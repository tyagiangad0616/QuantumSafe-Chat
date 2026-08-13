import socket
import threading

from protocol.packet import create_packet, encode_packet, decode_packet


HOST = "127.0.0.1"
PORT = 5050

clients = {}


def send_packet(client_socket, packet):
    client_socket.send(encode_packet(packet))


def broadcast(packet, sender=None):
    data = encode_packet(packet)

    for client_socket in list(clients):
        if client_socket != sender:
            try:
                client_socket.send(data)
            except:
                clients.pop(client_socket, None)
                client_socket.close()


def handle_client(client_socket, address):
    print(f"[CONNECTED] {address}")

    try:
        while True:
            data = client_socket.recv(4096)

            if not data:
                break

            packet = decode_packet(data)
            packet_type = packet["type"]

            if packet_type == "join":
                username = packet["payload"]["username"]

                clients[client_socket] = username

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

            elif packet_type == "chat":
                username = clients.get(client_socket)

                if not username:
                    continue

                message = packet["payload"]["message"]

                if not message:
                    continue

                if message.lower() == "exit":
                    break

                packet["payload"]["username"] = username

                send_packet(
                    client_socket,
                    create_packet(
                        "message_ack",
                        {
                            "message_id": packet["id"],
                            "status": "received"
                        }
                    )
                )

                print(f"{username}: {message}")

                broadcast(packet, client_socket)

            elif packet_type == "leave":
                break

    except Exception as error:
        print(f"[ERROR] {address}: {error}")

    finally:
        username = clients.pop(client_socket, None)

        if username:
            print(f"[LEAVE] {username}")

            broadcast(
                create_packet(
                    "leave",
                    {
                        "username": username
                    }
                ),
                client_socket
            )

        client_socket.close()


server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

server.setsockopt(
    socket.SOL_SOCKET,
    socket.SO_REUSEADDR,
    1
)

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