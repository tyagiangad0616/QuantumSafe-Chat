import os
import socket
import threading
import time
import traceback

from protocol.packet import (
    PacketTooLarge,
    create_packet,
    encode_packet,
    receive_packet,
)


# ============================================================
# QUANTUMSAFE CHAT SERVER
# ============================================================
#
# The server is an encrypted relay.
#
# It NEVER decrypts chat messages.
#
# Responsibilities:
#   - register users and their ML-KEM public keys
#   - route public-key requests
#   - route ML-KEM key-exchange packets
#   - route key-confirmation packets
#   - route encrypted chat packets
#   - queue packets for offline users
#   - route delivery acknowledgements
#
# Protocol:
#   4-byte big-endian payload length
#   + UTF-8 JSON packet
# ============================================================


HOST = os.environ.get("QUANTUMSAFE_HOST", "0.0.0.0")
PORT = int(os.environ.get("QUANTUMSAFE_PORT", "5050"))
BACKLOG = 50
MAX_CONNECTIONS = int(os.environ.get("QUANTUMSAFE_MAX_CONNECTIONS", "32"))
JOIN_TIMEOUT_SECONDS = 20.0
IDLE_TIMEOUT_SECONDS = 600.0
MAX_QUEUE_PER_USER = 100
QUEUE_TTL_SECONDS = 60 * 60


# ============================================================
# GLOBAL STATE
# ============================================================

# username -> socket
connected_clients = {}

# username -> ML-KEM public key
public_keys = {}

# username -> packets waiting for that user
offline_queues = {}

# ============================================================
# ACCOUNT / DEVICE REGISTRY
# ============================================================
#
# The existing username-based routing remains intact during
# migration. These registries introduce the new account/device
# model without breaking the currently working PQC protocol.
#
# account_id -> profile + device IDs
accounts = {}

# device_id -> account/device metadata
devices = {}

# username -> set of users waiting for this user's public key
pending_public_key_requests = {}

# username -> account_id that owns this routing name
username_bindings = {}

# message_id -> sender, intended recipient, timestamp, delivery state
message_records = {}

# Delivery ACKs older than this are rejected.
ACK_TTL_SECONDS = 24 * 60 * 60

state_lock = threading.RLock()
message_state_lock = threading.RLock()
socket_write_locks = {}
active_client_count = 0

shutdown_event = threading.Event()


# ============================================================
# LOGGING
# ============================================================

def log(message):
    print(message, flush=True)


# ============================================================
# SOCKET HELPERS
# ============================================================

def get_client(username):
    with state_lock:
        return connected_clients.get(username)


def is_online(username):
    with state_lock:
        return username in connected_clients


def write_lock_for(sock):
    with state_lock:
        lock = socket_write_locks.get(sock)
        if lock is None:
            lock = threading.Lock()
            socket_write_locks[sock] = lock
        return lock


def discard_write_lock(sock):
    with state_lock:
        socket_write_locks.pop(sock, None)


def send_packet(sock, packet):
    framed = encode_packet(packet)
    with write_lock_for(sock):
        sock.sendall(framed)


def safe_send(sock, packet):
    try:
        send_packet(sock, packet)
        return True

    except (
        BrokenPipeError,
        ConnectionResetError,
        ConnectionAbortedError,
        OSError,
    ):
        return False


def send_error(sock, message):
    safe_send(
        sock,
        create_packet(
            "error",
            {
                "message": message
            }
        )
    )


# ============================================================
# OFFLINE QUEUE
# ============================================================

def _prune_queue(queue):
    now = time.time()
    kept = []
    for item in queue:
        if not isinstance(item, dict) or "packet" not in item:
            kept.append({
                "queued_at": now,
                "packet": item
            })
            continue
        if now - item.get("queued_at", now) <= QUEUE_TTL_SECONDS:
            kept.append(item)
    return kept


def queue_packet(username, packet):
    with state_lock:

        queue = _prune_queue(
            offline_queues.setdefault(
                username,
                []
            )
        )

        if len(queue) >= MAX_QUEUE_PER_USER:
            queue = queue[-(MAX_QUEUE_PER_USER - 1):]

        queue.append({
            "queued_at": time.time(),
            "packet": packet
        })

        offline_queues[username] = queue

        return len(queue)


def pop_offline_queue(username):
    with state_lock:

        queued = _prune_queue(
            offline_queues.get(
                username,
                []
            )
        )

        offline_queues[username] = []

        return [
            item["packet"]
            for item in queued
        ]


def deliver_offline_queue(username, sock):
    queued = pop_offline_queue(username)

    if not queued:
        return

    log(
        f"[QUEUE] Delivering {len(queued)} "
        f"queued packet(s) to {username}"
    )

    failed = False
    remaining = []

    for index, packet in enumerate(queued):

        if failed:
            remaining.append(packet)
            continue

        if not safe_send(sock, packet):
            failed = True
            remaining.append(packet)

    if remaining:

        with state_lock:

            existing = offline_queues.setdefault(
                username,
                []
            )

            offline_queues[username] = (
                remaining + existing
            )

        log(
            f"[QUEUE] {len(remaining)} packet(s) "
            f"remain queued for {username}"
        )


# ============================================================
# PUBLIC KEY WAITING / RACE FIX
# ============================================================
#
# This fixes the exact startup race where:
#
#   1. Angad connects first.
#   2. Angad asks for Shriyaa's public key.
#   3. Shriyaa is not registered yet.
#   4. The old server returns "No public key found".
#   5. Shriyaa connects.
#   6. Both sides wait forever.
#
# Instead, the request is remembered and automatically
# fulfilled as soon as Shriyaa registers.
# ============================================================

def add_pending_public_key_request(
    target_username,
    requester_username
):
    with state_lock:

        requesters = (
            pending_public_key_requests
            .setdefault(
                target_username,
                set()
            )
        )

        requesters.add(
            requester_username
        )


def remove_pending_public_key_requests(
    target_username
):
    with state_lock:

        return pending_public_key_requests.pop(
            target_username,
            set()
        )


def send_public_key_to_requester(
    requester_username,
    target_username
):
    target_key = None
    requester_socket = None

    with state_lock:

        target_key = public_keys.get(
            target_username
        )

        requester_socket = (
            connected_clients.get(
                requester_username
            )
        )

    if target_key is None:
        return False

    if requester_socket is None:
        return False

    packet = create_packet(
        "public_key",
        {
            "username": target_username,
            "public_key": target_key
        }
    )

    if safe_send(
        requester_socket,
        packet
    ):

        log(
            f"[PUBLIC KEY] "
            f"Sent {target_username}'s key "
            f"to {requester_username}"
        )

        return True

    return False


def fulfill_pending_public_key_requests(
    target_username
):
    requesters = (
        remove_pending_public_key_requests(
            target_username
        )
    )

    if not requesters:
        return

    log(
        f"[PUBLIC KEY] "
        f"{target_username} is now registered; "
        f"fulfilling {len(requesters)} pending request(s)."
    )

    for requester in requesters:

        if requester == target_username:
            continue

        send_public_key_to_requester(
            requester,
            target_username
        )


# ============================================================
# USER REGISTRATION
# ============================================================

def register_client(
    username,
    public_key,
    sock
):
    old_socket = None

    with state_lock:

        old_socket = connected_clients.get(
            username
        )

        connected_clients[
            username
        ] = sock

        public_keys[
            username
        ] = public_key

        # A new ML-KEM public key invalidates ciphertext
        # queued against a previous session.
        offline_queues[username] = []

    if (
        old_socket is not None
        and old_socket is not sock
    ):

        log(
            f"[RECONNECT] "
            f"Replacing previous connection for "
            f"{username}"
        )

        try:
            old_socket.shutdown(
                socket.SHUT_RDWR
            )
        except OSError:
            pass

        try:
            old_socket.close()
        except OSError:
            pass

    fulfill_pending_public_key_requests(
        username
    )


def unregister_client(
    username,
    sock
):
    if username is None:
        return

    with state_lock:

        current = connected_clients.get(
            username
        )

        # Do not remove a newer connection if
        # an old socket disconnects later.
        if current is sock:

            connected_clients.pop(
                username,
                None
            )

            public_keys.pop(
                username,
                None
            )

            stale_device_ids = [
                device_id
                for device_id, info in devices.items()
                if info.get("socket") is sock
            ]

            for device_id in stale_device_ids:
                devices.pop(device_id, None)

            went_offline = True
        else:
            went_offline = False

    discard_write_lock(sock)

    log(
        f"[OFFLINE] {username}"
    )

    if went_offline:
        notify_peer_offline(username)


def notify_peer_offline(username):
    packet = create_packet(
        "peer_offline",
        {
            "username": username
        }
    )

    with state_lock:
        others = list(
            connected_clients.items()
        )

    for other_name, other_sock in others:
        if other_name == username:
            continue
        safe_send(other_sock, packet)


# ============================================================
# MESSAGE STATE
# ============================================================

def remember_message(
    message_id,
    sender,
    recipient
):
    with message_state_lock:
        message_records[message_id] = {
            "sender": sender,
            "recipient": recipient,
            "created_at": time.time(),
            "delivered": False,
        }


def accept_delivery_ack(
    message_id,
    claimant
):
    """
    Bind a delivery ACK to the intended recipient.

    Returns (accepted, record_copy, reason).
    reason is one of: accepted, unknown, unauthorized,
    expired, duplicate.
    """
    now = time.time()

    with message_state_lock:
        record = message_records.get(message_id)

        if record is None:
            return False, None, "unknown"

        if claimant != record["recipient"]:
            return False, dict(record), "unauthorized"

        if now - record["created_at"] > ACK_TTL_SECONDS:
            return False, dict(record), "expired"

        if record["delivered"]:
            return False, dict(record), "duplicate"

        record["delivered"] = True
        return True, dict(record), "accepted"


# ============================================================
# JOIN
# ============================================================

def handle_join(
    sock,
    payload
):
    username = str(
        payload.get(
            "username",
            ""
        )
    ).strip()

    account_id = str(
        payload.get(
            "account_id",
            ""
        )
    ).strip()

    device_id = str(
        payload.get(
            "device_id",
            ""
        )
    ).strip()

    phone_number = str(
        payload.get(
            "phone_number",
            ""
        )
    ).strip()

    display_name = str(
        payload.get(
            "display_name",
            username
        )
    ).strip()

    public_key = str(
        payload.get(
            "public_key",
            ""
        )
    ).strip()

    if not username:

        send_error(
            sock,
            "Username is required."
        )

        return None

    if not public_key:

        send_error(
            sock,
            "Public key is required."
        )

        return None

    with state_lock:
        bound_account = username_bindings.get(username)

        if bound_account:
            if not account_id or bound_account != account_id:
                send_error(
                    sock,
                    "Username is bound to another account."
                )
                return None
        elif account_id:
            username_bindings[username] = account_id

    if account_id and device_id:
        with state_lock:
            account = accounts.setdefault(
                account_id,
                {
                    "phone_number": phone_number,
                    "display_name": display_name or username,
                    "devices": set()
                }
            )

            if phone_number:
                account["phone_number"] = phone_number

            if display_name:
                account["display_name"] = display_name

            account["devices"].add(device_id)

            devices[device_id] = {
                "account_id": account_id,
                "username": username,
                "public_key": public_key,
                "socket": sock
            }

        log(
            f"[DEVICE REGISTERED] "
            f"{display_name or username} / {device_id}"
        )

    register_client(
        username,
        public_key,
        sock
    )

    with state_lock:
        online_count = len(
            connected_clients
        )

    log("")
    log(
        "============================================================"
    )
    log(
        f"[JOIN] {username}"
    )
    log(
        f"[ACCOUNT] {account_id or '(none)'}"
    )
    log(
        f"[DEVICE] {device_id or '(none)'}"
    )
    log(
        f"[PHONE] {phone_number or '(none)'}"
    )
    log(
        f"[ONLINE] {online_count} client(s)"
    )
    log(
        "============================================================"
    )

    if not safe_send(
        sock,
        create_packet(
            "join_ack",
            {
                "username": username
            }
        )
    ):
        return username

    # Send anything queued while this user was offline.
    deliver_offline_queue(
        username,
        sock
    )

    return username


# ============================================================
# GET PUBLIC KEY
# ============================================================

def handle_get_public_key(
    sock,
    requester,
    payload
):
    recipient = str(
        payload.get(
            "recipient",
            ""
        )
    ).strip()

    if not recipient:

        send_error(
            sock,
            "Recipient is required."
        )

        return

    with state_lock:

        key = public_keys.get(
            recipient
        )

        recipient_online = (
            recipient
            in connected_clients
        )

    # --------------------------------------------------------
    # KEY AVAILABLE
    # --------------------------------------------------------

    if key is not None:

        log(
            f"[PUBLIC KEY] "
            f"{requester} requested "
            f"{recipient}'s key."
        )

        if not safe_send(
            sock,
            create_packet(
                "public_key",
                {
                    "username": recipient,
                    "public_key": key
                }
            )
        ):
            log(
                f"[PUBLIC KEY] "
                f"Failed sending {recipient}'s key "
                f"to {requester}"
            )

        return

    # --------------------------------------------------------
    # KEY NOT AVAILABLE YET
    # --------------------------------------------------------
    #
    # DO NOT send an error to the Android UI.
    #
    # The target may simply be connecting a moment later.
    # Remember the request and automatically answer it when
    # the target registers.
    # --------------------------------------------------------

    add_pending_public_key_request(
        recipient,
        requester
    )

    if recipient_online:

        log(
            f"[PUBLIC KEY] "
            f"{requester} requested {recipient}'s key, "
            f"but the key is not ready yet."
        )

    else:

        log(
            f"[PUBLIC KEY] "
            f"{requester} requested {recipient}'s key "
            f"while {recipient} is offline; "
            f"request queued."
        )


# ============================================================
# ROUTE GENERIC PEER PACKET
# ============================================================

def route_to_recipient(
    packet_type,
    sender,
    payload,
    recipient,
    offline_queue=True
):
    forwarded_payload = dict(
        payload
    )

    # Different parts of the existing project use
    # different sender field names:
    #
    #   key exchange -> sender
    #   chat         -> username
    #
    # Keep both so Android and Python clients remain
    # compatible.
    forwarded_payload[
        "sender"
    ] = sender

    forwarded_payload[
        "username"
    ] = sender

    packet = create_packet(
        packet_type,
        forwarded_payload
    )

    destination = get_client(
        recipient
    )

    if destination is not None:

        if safe_send(
            destination,
            packet
        ):

            log(
                f"[FORWARDED] "
                f"{packet_type}: "
                f"{sender} -> {recipient}"
            )

            return True

        log(
            f"[FORWARD FAILED] "
            f"{packet_type}: "
            f"{sender} -> {recipient}"
        )

    if offline_queue:

        queue_size = queue_packet(
            recipient,
            packet
        )

        log(
            f"[QUEUED] "
            f"{packet_type}: "
            f"{sender} -> {recipient} "
            f"(queue={queue_size})"
        )

    return False


# ============================================================
# KEY EXCHANGE
# ============================================================

def handle_key_exchange(
    sock,
    sender,
    payload
):
    recipient = str(
        payload.get(
            "recipient",
            ""
        )
    ).strip()

    ciphertext = payload.get(
        "ciphertext"
    )

    if not recipient:

        send_error(
            sock,
            "Key exchange recipient is required."
        )

        return

    if not ciphertext:

        send_error(
            sock,
            "Key exchange ciphertext is required."
        )

        return

    route_to_recipient(
        "key_exchange",
        sender,
        payload,
        recipient
    )


# ============================================================
# KEY CONFIRM
# ============================================================

def handle_key_confirm(
    sock,
    sender,
    payload
):
    recipient = str(
        payload.get(
            "recipient",
            ""
        )
    ).strip()

    confirmation = payload.get(
        "confirmation"
    )

    if not recipient:

        send_error(
            sock,
            "Key confirmation recipient is required."
        )

        return

    if not confirmation:

        send_error(
            sock,
            "Key confirmation is required."
        )

        return

    route_to_recipient(
        "key_confirm",
        sender,
        payload,
        recipient
    )


# ============================================================
# KEY CONFIRM ACK
# ============================================================

def handle_key_confirm_ack(
    sock,
    sender,
    payload
):
    recipient = str(
        payload.get(
            "recipient",
            ""
        )
    ).strip()

    if not recipient:

        send_error(
            sock,
            "Key confirmation ACK recipient is required."
        )

        return

    route_to_recipient(
        "key_confirm_ack",
        sender,
        payload,
        recipient
    )


# ============================================================
# ENCRYPTED CHAT
# ============================================================

def handle_chat(
    sock,
    sender,
    payload
):
    recipient = str(
        payload.get(
            "recipient",
            ""
        )
    ).strip()

    message_id = str(
        payload.get(
            "message_id",
            ""
        )
    ).strip()

    nonce = payload.get(
        "nonce"
    )

    ciphertext = payload.get(
        "ciphertext"
    )

    if not recipient:

        send_error(
            sock,
            "Chat recipient is required."
        )

        return

    if not message_id:

        send_error(
            sock,
            "Message ID is required."
        )

        return

    if not nonce:

        send_error(
            sock,
            "Message nonce is required."
        )

        return

    if not ciphertext:

        send_error(
            sock,
            "Encrypted ciphertext is required."
        )

        return

    # Bind this ciphertext to sender AND intended recipient
    # so a third party cannot forge a delivery ACK.
    remember_message(
        message_id,
        sender,
        recipient
    )

    short_id = message_id[:8]

    log("")
    log(
        "------------------------------------------------------------"
    )

    log(
        f"[CHAT] "
        f"{sender} -> {recipient}"
    )

    log(
        f"[MESSAGE ID] "
        f"{short_id}"
    )

    log(
        "[SECURITY] "
        "Encrypted payload; server does not decrypt plaintext."
    )

    forwarded = route_to_recipient(
        "chat",
        sender,
        payload,
        recipient,
        offline_queue=True
    )

    # --------------------------------------------------------
    # SERVER ACK
    # --------------------------------------------------------

    if forwarded:

        status = "sent"

        log(
            f"[ACK] "
            f"Server received [{short_id}]"
        )

    else:

        status = "queued"

        log(
            f"[ACK] "
            f"Queued [{short_id}] for {recipient}"
        )

    safe_send(
        sock,
        create_packet(
            "message_ack",
            {
                "message_id": message_id,
                "status": status
            }
        )
    )


# ============================================================
# DELIVERY ACK
# ============================================================

def handle_delivery_ack(
    sock,
    recipient_user,
    payload
):
    message_id = str(
        payload.get(
            "message_id",
            ""
        )
    ).strip()

    if not message_id:

        send_error(
            sock,
            "Delivery ACK requires a message ID."
        )

        return

    accepted, record, reason = accept_delivery_ack(
        message_id,
        recipient_user
    )

    short_id = message_id[:8]

    if not accepted:
        log(
            f"[DELIVERY ACK REJECTED] "
            f"{reason} [{short_id}] "
            f"from {recipient_user}"
        )

        send_error(
            sock,
            "Delivery acknowledgement rejected."
        )

        return

    original_sender = record["sender"]

    packet = create_packet(
        "delivery_ack",
        {
            "message_id": message_id,
            "sender": recipient_user,
            "username": recipient_user
        }
    )

    destination = get_client(
        original_sender
    )

    if destination is not None:

        if safe_send(
            destination,
            packet
        ):

            log(
                f"[DELIVERED] "
                f"{recipient_user} -> "
                f"{original_sender} "
                f"[{short_id}]"
            )

            return

    # The original sender is offline.
    # Queue the delivery ACK so it can be shown when
    # they reconnect.
    queue_size = queue_packet(
        original_sender,
        packet
    )

    log(
        f"[DELIVERY ACK QUEUED] "
        f"{original_sender} offline "
        f"[{short_id}] "
        f"(queue={queue_size})"
    )


# ============================================================
# LEAVE
# ============================================================

def handle_leave(
    username
):
    if username:

        log(
            f"[LEAVE] {username}"
        )


# ============================================================
# PACKET DISPATCH
# ============================================================

def handle_packet(
    sock,
    username,
    packet
):
    if not isinstance(
        packet,
        dict
    ):

        send_error(
            sock,
            "Invalid packet format."
        )

        return username

    packet_type = packet.get(
        "type"
    )

    payload = packet.get(
        "payload",
        {}
    )

    if not isinstance(
        payload,
        dict
    ):

        payload = {}

    if not packet_type:

        send_error(
            sock,
            "Packet type is missing."
        )

        return username

    # ========================================================
    # JOIN
    # ========================================================

    if packet_type == "join":

        if username is not None:

            send_error(
                sock,
                "Already joined."
            )

            return username

        return handle_join(
            sock,
            payload
        )

    # Everything below requires a successful JOIN.

    if username is None:

        send_error(
            sock,
            "You must send a join packet first."
        )

        return username

    # ========================================================
    # PUBLIC KEY
    # ========================================================

    if packet_type == "get_public_key":

        handle_get_public_key(
            sock,
            username,
            payload
        )

    # ========================================================
    # KEY EXCHANGE
    # ========================================================

    elif packet_type == "key_exchange":

        handle_key_exchange(
            sock,
            username,
            payload
        )

    # ========================================================
    # KEY CONFIRM
    # ========================================================

    elif packet_type == "key_confirm":

        handle_key_confirm(
            sock,
            username,
            payload
        )

    # ========================================================
    # KEY CONFIRM ACK
    # ========================================================

    elif packet_type == "key_confirm_ack":

        handle_key_confirm_ack(
            sock,
            username,
            payload
        )

    # ========================================================
    # CHAT
    # ========================================================

    elif packet_type == "chat":

        handle_chat(
            sock,
            username,
            payload
        )

    # ========================================================
    # DELIVERY ACK
    # ========================================================

    elif packet_type == "delivery_ack":

        handle_delivery_ack(
            sock,
            username,
            payload
        )

    # ========================================================
    # LEAVE
    # ========================================================

    elif packet_type == "leave":

        handle_leave(
            username
        )

    # ========================================================
    # UNKNOWN
    # ========================================================

    else:

        log(
            f"[UNKNOWN] "
            f"{username} -> {packet_type}"
        )

        send_error(
            sock,
            f"Unknown packet type: {packet_type}"
        )

    return username


# ============================================================
# CLIENT THREAD
# ============================================================

def handle_client(
    sock,
    address
):
    global active_client_count

    username = None

    log("")
    log(
        f"[CONNECTION] "
        f"New client from {address}"
    )

    try:
        sock.setsockopt(
            socket.SOL_SOCKET,
            socket.SO_KEEPALIVE,
            1
        )
        sock.settimeout(JOIN_TIMEOUT_SECONDS)

        while not shutdown_event.is_set():

            packet = receive_packet(
                sock
            )

            if packet is None:
                break

            if username is None:
                sock.settimeout(IDLE_TIMEOUT_SECONDS)

            packet_type = packet.get(
                "type",
                "unknown"
            )

            log(
                f"[PACKET] "
                f"{username or address} "
                f"-> {packet_type}"
            )

            username = handle_packet(
                sock,
                username,
                packet
            )

    except PacketTooLarge as error:

        log(
            f"[REJECT] "
            f"{username or address}: {error}"
        )

    except socket.timeout:

        log(
            f"[TIMEOUT] "
            f"{username or address}"
        )

    except (
        BrokenPipeError,
        ConnectionResetError,
        ConnectionAbortedError,
        EOFError,
        OSError,
        ValueError,
    ) as error:

        log(
            f"[DISCONNECT] "
            f"{username or address}: "
            f"{error}"
        )

    except Exception as error:

        log(
            f"[ERROR] "
            f"{username or address}: "
            f"{error}"
        )

        traceback.print_exc()

    finally:
        unregister_client(
            username,
            sock
        )
        discard_write_lock(sock)

        with state_lock:
            if active_client_count > 0:
                active_client_count -= 1

        try:
            sock.shutdown(
                socket.SHUT_RDWR
            )
        except OSError:
            pass

        try:
            sock.close()
        except OSError:
            pass


# ============================================================
# SERVER STATUS
# ============================================================

def print_server_status():
    with state_lock:

        online = list(
            connected_clients.keys()
        )

        queues = {
            username: len(queue)
            for username, queue
            in offline_queues.items()
            if queue
        }

        pending_keys = {
            username: list(requesters)
            for username, requesters
            in pending_public_key_requests.items()
            if requesters
        }

    log("")
    log(
        "================ SERVER STATUS ================"
    )

    log(
        f"Online users: "
        f"{online if online else 'none'}"
    )

    with state_lock:
        account_summary = {
            account_id: sorted(info.get("devices", set()))
            for account_id, info in accounts.items()
        }

    log(
        f"Accounts/devices: "
        f"{account_summary if account_summary else 'none'}"
    )

    log(
        f"Offline queues: "
        f"{queues if queues else 'none'}"
    )

    log(
        f"Pending public-key requests: "
        f"{pending_keys if pending_keys else 'none'}"
    )

    log(
        "==============================================="
    )


# ============================================================
# START SERVER
# ============================================================

def start_server():
    global active_client_count

    log("")
    log(
        "============================================================"
    )
    log(
        "                 QUANTUMSAFE CHAT SERVER"
    )
    log(
        "============================================================"
    )
    log(
        f"Listening on {HOST}:{PORT}"
    )
    log(
        "Protocol: 4-byte big-endian length + UTF-8 JSON"
    )
    log(
        "PQC: ML-KEM-768"
    )
    log(
        "Message encryption: AES-256-GCM"
    )
    log(
        "Mode: encrypted relay"
    )
    log(
        "Plaintext decryption: DISABLED"
    )
    log(
        "============================================================"
    )

    server_socket = socket.socket(
        socket.AF_INET,
        socket.SOCK_STREAM
    )

    server_socket.setsockopt(
        socket.SOL_SOCKET,
        socket.SO_REUSEADDR,
        1
    )

    try:
        server_socket.bind(
            (
                HOST,
                PORT
            )
        )
    except OSError as error:
        if error.errno == 48:
            log(
                f"[FATAL] Port {PORT} is already in use."
            )
            log(
                "Another server.py is probably still running."
            )
            log(
                f"Free it with: lsof -iTCP:{PORT} -sTCP:LISTEN"
            )
            log(
                "Then kill the PID shown, or close the other terminal."
            )
        raise SystemExit(
            f"Could not bind to {HOST}:{PORT}: {error}"
        ) from error

    server_socket.listen(
        BACKLOG
    )

    server_socket.settimeout(
        1.0
    )

    log(
        "[SERVER] Ready."
    )

    try:

        while not shutdown_event.is_set():

            try:

                client_socket, address = (
                    server_socket.accept()
                )

            except socket.timeout:

                continue

            except OSError:

                if shutdown_event.is_set():
                    break

                raise

            with state_lock:
                if active_client_count >= MAX_CONNECTIONS:
                    log(
                        f"[REJECT] Connection cap "
                        f"{MAX_CONNECTIONS} reached; "
                        f"closing {address}"
                    )
                    try:
                        client_socket.close()
                    except OSError:
                        pass
                    continue
                active_client_count += 1

            thread = threading.Thread(
                target=handle_client,
                args=(
                    client_socket,
                    address
                ),
                daemon=True
            )

            thread.start()

            print_server_status()

    except KeyboardInterrupt:

        log(
            "\n[SERVER] Ctrl+C received."
        )

    finally:

        shutdown_event.set()

        log(
            "[SERVER] Shutting down..."
        )

        with state_lock:

            clients = list(
                connected_clients.items()
            )

            connected_clients.clear()

        for username, sock in clients:

            log(
                f"[SERVER] Closing {username}"
            )

            try:
                sock.shutdown(
                    socket.SHUT_RDWR
                )
            except OSError:
                pass

            try:
                sock.close()
            except OSError:
                pass

        try:
            server_socket.close()
        except OSError:
            pass

        log(
            "[SERVER] Stopped."
        )


# ============================================================
# MAIN
# ============================================================

if __name__ == "__main__":
    start_server()
