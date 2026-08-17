import socket
import threading

from protocol.packet import create_packet, encode_packet, decode_packet

HOST = "127.0.0.1"
PORT = 5050

username_to_socket = {}
socket_to_username = {}
username_to_public_key = {}

pending_messages = {}

clients_lock = threading.Lock()


def send_packet(client_socket, packet):
    client_socket.sendall(encode_packet(packet))


def handle_client(client_socket, address):
    print(f"[CONNECTED] {address}")

    username = None

    try:
        while True:
            data = client_socket.recv(4096)

            if not data:
                break

            packet = decode_packet(data)
            packet_type = packet["type"]

            if packet_type == "join":
                username = packet["payload"]["username"]
                public_key = packet["payload"].get("public_key")

                with clients_lock:
                    if username in username_to_socket:
                        send_packet(
                            client_socket,
                            create_packet(
                                "error",
                                {
                                    "message": "Username already in use"
                                }
                            )
                        )
                        continue

                    username_to_socket[username] = client_socket
                    socket_to_username[client_socket] = username
                    username_to_public_key[username] = public_key

                print(f"[JOIN] {username}")

                send_packet(
                    client_socket,
                    create_packet(
                        "join_ack",
                        {
                            "username": username
                        }
                    )
                )

            elif packet_type == "get_public_key":
                recipient = packet["payload"].get("recipient")

                with clients_lock:
                    public_key = username_to_public_key.get(recipient)

                if public_key:
                    send_packet(
                        client_socket,
                        create_packet(
                            "public_key",
                            {
                                "username": recipient,
                                "public_key": public_key
                            }
                        )
                    )

                    print(
                        f"[KEY REQUEST] "
                        f"{username} requested {recipient}'s public key"
                    )

                else:
                    send_packet(
                        client_socket,
                        create_packet(
                            "error",
                            {
                                "message": f"{recipient} is not online"
                            }
                        )
                    )

            elif packet_type == "chat":
                username = socket_to_username.get(client_socket)

                if not username:
                    continue

                message = packet["payload"].get("message")
                recipient = packet["payload"].get("recipient")
                message_id = packet["payload"].get("message_id")

                if not message or not recipient or not message_id:
                    continue

                packet["payload"]["username"] = username

                with clients_lock:
                    pending_messages[message_id] = username
                    recipient_socket = username_to_socket.get(recipient)

                send_packet(
                    client_socket,
                    create_packet(
                        "message_ack",
                        {
                            "message_id": message_id,
                            "status": "received"
                        }
                    )
                )

                print(f"[MESSAGE] {username} -> {recipient}: {message}")

                if recipient_socket:
                    send_packet(recipient_socket, packet)
                else:
                    send_packet(
                        client_socket,
                        create_packet(
                            "error",
                            {
                                "message": f"{recipient} is not online"
                            }
                        )
                    )

            elif packet_type == "delivery_ack":
                message_id = packet["payload"].get("message_id")

                if not message_id:
                    continue

                with clients_lock:
                    sender_username = pending_messages.pop(message_id, None)
                    sender_socket = username_to_socket.get(sender_username)

                if sender_socket:
                    send_packet(
                        sender_socket,
                        create_packet(
                            "delivery_ack",
                            {
                                "message_id": message_id,
                                "status": "delivered"
                            }
                        )
                    )

                    print(
                        f"[DELIVERED] "
                        f"{message_id} -> {sender_username}"
                    )

            elif packet_type == "leave":
                break

    except Exception as error:
        print(f"[ERROR] {address}: {error}")

    finally:
        with clients_lock:
            username = socket_to_username.pop(client_socket, None)

            if username:
                username_to_socket.pop(username, None)
                username_to_public_key.pop(username, None)

        if username:
            print(f"[LEAVE] {username}")

        client_socket.close()


server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

server.bind((HOST, PORT))
server.listen()

print(f"Server running on {HOST}:{PORT}")

while True:
    client_socket, address = server.accept()

    thread = threading.Thread(
        target=handle_client,
        args=(client_socket, address),
        daemon=True
    )

    thread.start()