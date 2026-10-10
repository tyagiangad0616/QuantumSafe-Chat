import base64
import socket
import threading
import time
import uuid

from protocol.packet import (
    create_packet,
    encode_packet,
    receive_packet,
)

from crypto.kem import (
    generate_keypair,
    encapsulate,
    decapsulate,
)

from crypto.session import (
    derive_session_key,
    create_confirmation_tag,
    verify_confirmation_tag,
    encrypt_message,
    decrypt_message,
)


HOST = "127.0.0.1"
PORT = 5050


# ============================================================
# CONNECTION STATE
# ============================================================

client_socket = None

socket_lock = threading.Lock()

shutdown_requested = threading.Event()

receiver_stopped = threading.Event()


# ============================================================
# SESSION STATE
# ============================================================

session_key = None
shared_secret = None

session_ready = threading.Event()
session_lock = threading.Lock()


# ============================================================
# REPLAY PROTECTION
# ============================================================

processed_message_ids = set()

processed_message_lock = threading.Lock()


# ============================================================
# HELPER FUNCTIONS
# ============================================================

def get_socket():
    with socket_lock:
        return client_socket


def set_socket(sock):
    global client_socket

    with socket_lock:
        client_socket = sock


def close_current_socket(send_leave=True):

    sock = get_socket()

    if sock is None:
        return

    if send_leave:

        try:

            send_packet(
                sock,
                create_packet(
                    "leave",
                    {}
                )
            )

        except OSError:
            pass

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

    set_socket(None)


def send_packet(sock, packet):
    sock.sendall(
        encode_packet(packet)
    )


def send_current_packet(packet):

    sock = get_socket()

    if sock is None:

        raise ConnectionError(
            "Client is currently disconnected."
        )

    send_packet(
        sock,
        packet
    )


def set_session_material(
    secret,
    derived_key,
):

    global shared_secret
    global session_key

    with session_lock:

        shared_secret = secret

        session_key = derived_key


def get_session_key():

    with session_lock:

        return session_key


def get_shared_secret():

    with session_lock:

        return shared_secret


def is_message_processed(
    message_id,
):

    with processed_message_lock:

        return (
            message_id
            in processed_message_ids
        )


def mark_message_processed(
    message_id,
):

    with processed_message_lock:

        processed_message_ids.add(
            message_id
        )


# ============================================================
# RECEIVE LOOP
# ============================================================

def receive_messages(
    sock,
    username,
    kem,
    recipient,
):

    initiator = min(
        username,
        recipient
    )

    responder = max(
        username,
        recipient
    )

    receiver_stopped.clear()

    while (
        not shutdown_requested.is_set()
    ):

        try:

            packet = receive_packet(
                sock
            )

            if packet is None:

                break

            packet_type = packet["type"]

            payload = packet.get(
                "payload",
                {}
            )

            # ==================================================
            # JOIN ACK
            # ==================================================

            if packet_type == "join_ack":

                print(
                    f"\nConnected as "
                    f"{payload['username']}!"
                )

            # ==================================================
            # PUBLIC KEY
            # ==================================================

            elif packet_type == "public_key":

                key_owner = payload.get(
                    "username"
                )

                public_key = payload.get(
                    "public_key"
                )

                if (
                    key_owner != recipient
                    or not public_key
                ):

                    continue

                print(
                    f"\nReceived ML-KEM public key "
                    f"for {key_owner}"
                )

                print(
                    f"Public key length: "
                    f"{len(public_key)} characters"
                )

                if username != initiator:

                    print(
                        f"Waiting for "
                        f"{initiator} to "
                        f"complete key exchange..."
                    )

                    continue

                print(
                    f"Starting ML-KEM "
                    f"handshake with "
                    f"{recipient}..."
                )

                ciphertext, secret = encapsulate(
                    public_key
                )

                derived_key = (
                    derive_session_key(
                        secret,
                        initiator,
                        responder,
                    )
                )

                set_session_material(
                    secret,
                    derived_key,
                )

                print(
                    "ML-KEM encapsulation complete"
                )

                print(
                    "Session key derived"
                )

                send_current_packet(
                    create_packet(
                        "key_exchange",
                        {
                            "recipient":
                            recipient,

                            "ciphertext":
                            ciphertext
                        }
                    )
                )

                print(
                    "Key exchange ciphertext sent"
                )

            # ==================================================
            # KEY EXCHANGE
            # ==================================================

            elif packet_type == "key_exchange":

                sender = payload.get(
                    "sender"
                )

                ciphertext = payload.get(
                    "ciphertext"
                )

                if (
                    sender != initiator
                    or not ciphertext
                ):

                    continue

                print(
                    f"\nReceived ML-KEM "
                    f"ciphertext from {sender}"
                )

                secret = decapsulate(
                    kem,
                    ciphertext
                )

                derived_key = (
                    derive_session_key(
                        secret,
                        initiator,
                        responder,
                    )
                )

                set_session_material(
                    secret,
                    derived_key,
                )

                print(
                    "ML-KEM decapsulation completed"
                )

                print(
                    "Session key derived"
                )

                confirmation = (
                    create_confirmation_tag(
                        secret,
                        initiator,
                        responder,
                    )
                )

                send_current_packet(
                    create_packet(
                        "key_confirm",
                        {
                            "recipient":
                            sender,

                            "confirmation":
                            confirmation
                        }
                    )
                )

                print(
                    "Shared-secret confirmation sent"
                )

            # ==================================================
            # KEY CONFIRMATION
            # ==================================================

            elif packet_type == "key_confirm":

                sender = payload.get(
                    "sender"
                )

                confirmation = payload.get(
                    "confirmation"
                )

                if (
                    sender != responder
                    or not confirmation
                ):

                    continue

                secret = get_shared_secret()

                if secret is None:

                    print(
                        "\n[SECURITY ERROR] "
                        "Shared secret unavailable"
                    )

                    continue

                valid = verify_confirmation_tag(
                    secret,
                    initiator,
                    responder,
                    confirmation,
                )

                if not valid:

                    print(
                        "\n[SECURITY ERROR] "
                        "Shared-secret confirmation failed"
                    )

                    session_ready.clear()

                    continue

                print(
                    "\n✓ Shared-secret "
                    "confirmation verified"
                )

                send_current_packet(
                    create_packet(
                        "key_confirm_ack",
                        {
                            "recipient":
                            sender
                        }
                    )
                )

                session_ready.set()

                print(
                    "✓ PQC session established"
                )

            # ==================================================
            # KEY CONFIRM ACK
            # ==================================================

            elif packet_type == "key_confirm_ack":

                sender = payload.get(
                    "sender"
                )

                if sender != initiator:

                    continue

                session_ready.set()

                print(
                    "\n✓✓ PQC session confirmed"
                )

            # ==================================================
            # ENCRYPTED CHAT
            # ==================================================

            elif packet_type == "chat":

                sender = payload.get(
                    "username"
                )

                message_id = payload.get(
                    "message_id"
                )

                nonce_b64 = payload.get(
                    "nonce"
                )

                ciphertext_b64 = payload.get(
                    "ciphertext"
                )

                if not (
                    sender
                    and message_id
                    and nonce_b64
                    and ciphertext_b64
                ):

                    continue

                # ------------------------------------------------
                # REPLAY CHECK
                # ------------------------------------------------

                if is_message_processed(
                    message_id
                ):

                    print(
                        f"\n[SECURITY] "
                        f"Replay detected and rejected "
                        f"[{message_id[:8]}]"
                    )

                    print(
                        "Message was NOT displayed "
                        "and no delivery ACK was sent."
                    )

                    continue

                current_key = get_session_key()

                if current_key is None:

                    print(
                        "\n[SECURITY ERROR] "
                        "No secure session"
                    )

                    continue

                try:

                    nonce = base64.b64decode(
                        nonce_b64
                    )

                    ciphertext = base64.b64decode(
                        ciphertext_b64
                    )

                    message = decrypt_message(
                        current_key,
                        nonce,
                        ciphertext,
                        sender,
                        username,
                        message_id,
                    )

                except Exception:

                    print(
                        "\n[SECURITY ERROR] "
                        "Message authentication "
                        "or decryption failed"
                    )

                    continue

                mark_message_processed(
                    message_id
                )

                print(
                    f"\n{sender}: {message}"
                )

                # ACK only after successful
                # authentication/decryption.

                try:

                    send_packet(
                        sock,
                        create_packet(
                            "delivery_ack",
                            {
                                "message_id":
                                message_id
                            }
                        )
                    )

                except OSError:

                    # The client may have gone offline
                    # immediately after receiving it.
                    pass

            # ==================================================
            # MESSAGE ACK
            # ==================================================

            elif packet_type == "message_ack":

                message_id = payload.get(
                    "message_id"
                )

                status = payload.get(
                    "status"
                )

                if not message_id:
                    continue

                if status == "queued":

                    print(
                        f"\n✓ Message queued "
                        f"for offline recipient "
                        f"[{message_id[:8]}]"
                    )

                else:

                    print(
                        f"\n✓ Server received "
                        f"[{message_id[:8]}]"
                    )

            # ==================================================
            # DELIVERY ACK
            # ==================================================

            elif packet_type == "delivery_ack":

                message_id = payload.get(
                    "message_id"
                )

                if message_id:

                    print(
                        f"\n✓✓ Delivered "
                        f"[{message_id[:8]}]"
                    )

            # ==================================================
            # ERROR
            # ==================================================

            elif packet_type == "error":

                print(
                    f"\n[ERROR] "
                    f"{payload.get('message')}"
                )

        except (
            ConnectionResetError,
            BrokenPipeError,
            OSError,
        ):

            break

        except Exception as error:

            print(
                f"\n[ERROR] {error}"
            )

            break

    receiver_stopped.set()


# ============================================================
# CONNECT / RECONNECT
# ============================================================

def connect_to_server(
    username,
    public_key,
    kem,
    recipient,
):

    global client_socket

    old_socket = get_socket()

    if old_socket is not None:

        close_current_socket(
            send_leave=False
        )

        time.sleep(
            0.2
        )

    new_socket = socket.socket(
        socket.AF_INET,
        socket.SOCK_STREAM
    )

    new_socket.connect(
        (HOST, PORT)
    )

    set_socket(
        new_socket
    )

    send_packet(
        new_socket,
        create_packet(
            "join",
            {
                "username":
                username,

                "public_key":
                public_key
            }
        )
    )

    receiver_thread = threading.Thread(
        target=receive_messages,
        args=(
            new_socket,
            username,
            kem,
            recipient,
        ),
        daemon=True,
    )

    receiver_thread.start()

    return new_socket


# ============================================================
# USER SETUP
# ============================================================

print(
    "Connecting to QuantumSafe-Chat..."
)


username = input(
    "Enter Username: "
).strip()


print(
    "Generating ML-KEM-768 keypair..."
)

kem, public_key = generate_keypair()

print(
    f"PQC identity created for "
    f"{username}"
)


recipient = input(
    "Enter Recipient Username: "
).strip()


initiator = min(
    username,
    recipient
)

responder = max(
    username,
    recipient
)

print(
    f"PQC initiator: {initiator}"
)

print(
    f"PQC responder: {responder}"
)


# ============================================================
# FIRST CONNECTION
# ============================================================

connect_to_server(
    username,
    public_key,
    kem,
    recipient,
)


# ============================================================
# REQUEST PUBLIC KEY
# ============================================================

time.sleep(
    0.2
)

try:

    send_current_packet(
        create_packet(
            "get_public_key",
            {
                "recipient":
                recipient
            }
        )
    )

    print(
        f"Requested "
        f"{recipient}'s public key..."
    )

except ConnectionError:

    print(
        "\n[ERROR] "
        "Could not request public key."
    )


def retry_public_key_requests():

    while (
        not shutdown_requested.is_set()
        and not session_ready.is_set()
    ):

        time.sleep(3)

        if session_ready.is_set():
            break

        try:

            send_current_packet(
                create_packet(
                    "get_public_key",
                    {
                        "recipient":
                        recipient
                    }
                )
            )

        except (
            ConnectionError,
            OSError,
        ):
            pass


threading.Thread(
    target=retry_public_key_requests,
    daemon=True,
).start()


# ============================================================
# CHAT LOOP
# ============================================================

try:

    while True:

        message = input(
            "You: "
        ).strip()

        # ------------------------------------------------------
        # EXIT
        # ------------------------------------------------------

        if message.lower() == "/exit":

            shutdown_requested.set()

            close_current_socket(
                send_leave=True
            )

            break

        if message.lower() == "exit":

            shutdown_requested.set()

            close_current_socket(
                send_leave=True
            )

            break

        # ------------------------------------------------------
        # DISCONNECT
        # ------------------------------------------------------

        if message.lower() == "/disconnect":

            print(
                "\n[CLIENT] "
                "Disconnecting from server..."
            )

            close_current_socket(
                send_leave=True
            )

            print(
                "[CLIENT] "
                "Disconnected. "
                "PQC session remains in memory."
            )

            continue

        # ------------------------------------------------------
        # RECONNECT
        # ------------------------------------------------------

        if message.lower() == "/reconnect":

            if shutdown_requested.is_set():

                print(
                    "\n[ERROR] "
                    "Client is shutting down."
                )

                continue

            print(
                "\n[CLIENT] "
                "Reconnecting..."
            )

            try:

                connect_to_server(
                    username,
                    public_key,
                    kem,
                    recipient,
                )

                print(
                    "[CLIENT] "
                    "Reconnected successfully."
                )

                if session_ready.is_set():

                    print(
                        "✓ Existing PQC session "
                        "reused."
                    )

                else:

                    print(
                        "[CLIENT] "
                        "No existing PQC session."
                    )

            except Exception as error:

                print(
                    f"[ERROR] "
                    f"Reconnect failed: {error}"
                )

            continue

        # ------------------------------------------------------
        # REPLAY TEST
        # ------------------------------------------------------

        if message.lower() == "/replay":

            print(
                "\n[ERROR] "
                "Replay command is not included "
                "in this reliability build."
            )

            print(
                "Use the previous replay-test "
                "version when testing replay protection."
            )

            continue

        # ------------------------------------------------------
        # EMPTY
        # ------------------------------------------------------

        if not message:

            continue

        # ------------------------------------------------------
        # SECURE SESSION
        # ------------------------------------------------------

        if not session_ready.wait(
            timeout=60
        ):

            print(
                "\n[ERROR] "
                "Secure PQC session was "
                "not established."
            )

            print(
                f"Ensure {recipient} is connected "
                f"(python client.py or Android app) "
                f"with recipient set to {username}."
            )

            print(
                f"PQC initiator is {initiator}; "
                f"both peers must be online for "
                f"ML-KEM handshake."
            )

            continue

        current_key = get_session_key()

        if current_key is None:

            print(
                "\n[ERROR] "
                "Session key unavailable."
            )

            continue

        # ------------------------------------------------------
        # CREATE ENCRYPTED MESSAGE
        # ------------------------------------------------------

        message_id = str(
            uuid.uuid4()
        )

        nonce, ciphertext = encrypt_message(
            current_key,
            message,
            username,
            recipient,
            message_id,
        )

        nonce_b64 = base64.b64encode(
            nonce
        ).decode(
            "utf-8"
        )

        ciphertext_b64 = base64.b64encode(
            ciphertext
        ).decode(
            "utf-8"
        )

        encrypted_packet = create_packet(
            "chat",
            {
                "recipient":
                recipient,

                "message_id":
                message_id,

                "nonce":
                nonce_b64,

                "ciphertext":
                ciphertext_b64
            }
        )

        try:

            send_current_packet(
                encrypted_packet
            )

        except (
            ConnectionError,
            OSError,
        ):

            print(
                "\n[ERROR] "
                "Currently disconnected. "
                "Use /reconnect."
            )

finally:

    if not shutdown_requested.is_set():

        shutdown_requested.set()

        close_current_socket(
            send_leave=False
        )

    print(
        "Disconnected from server."
    )