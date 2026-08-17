import socket
import threading
import uuid
import base64

from protocol.packet import create_packet, encode_packet, decode_packet
from crypto.kem import generate_keypair

HOST = "127.0.0.1"
PORT = 5050


def send_packet(client_socket, packet):
    client_socket.sendall(encode_packet(packet))


def receive_messages(client_socket):
    while True:
        try:
            data = client_socket.recv(4096)

            if not data:
                break

            packet = decode_packet(data)
            packet_type = packet["type"]
            payload = packet["payload"]

            if packet_type == "join_ack":
                print(f"\nConnected as {payload['username']}!")

            elif packet_type == "chat":
                username = payload["username"]
                message = payload["message"]
                message_id = payload["message_id"]

                print(f"\n{username}: {message}")

                send_packet(
                    client_socket,
                    create_packet(
                        "delivery_ack",
                        {
                            "message_id": message_id
                        }
                    )
                )

            elif packet_type == "message_ack":
                print(
                    f"\n✓ Server received "
                    f"[{payload['message_id'][:8]}]"
                )

            elif packet_type == "delivery_ack":
                print(
                    f"\n✓✓ Delivered "
                    f"[{payload['message_id'][:8]}]"
                )

            elif packet_type == "error":
                print(f"\n[ERROR] {payload['message']}")

        except Exception as error:
            print(f"\n[ERROR] {error}")
            break


client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
client.connect((HOST, PORT))

username = input("Enter Username: ").strip()

print("Generating ML-KEM-768 keypair...")
kem, public_key = generate_keypair()

public_key_b64 = base64.b64encode(public_key).decode("utf-8")

print(f"PQC identity created for {username}")

send_packet(
    client,
    create_packet(
        "join",
        {
            "username": username,
            "public_key": public_key_b64
        }
    )
)

thread = threading.Thread(
    target=receive_messages,
    args=(client,),
    daemon=True
)

thread.start()

recipient = input("Enter Recipient Username: ").strip()

try:
    while True:
        message = input("You: ").strip()

        if message.lower() == "exit":
            send_packet(
                client,
                create_packet(
                    "leave",
                    {}
                )
            )
            break

        if not message:
            continue

        message_id = str(uuid.uuid4())

        send_packet(
            client,
            create_packet(
                "chat",
                {
                    "recipient": recipient,
                    "message": message,
                    "message_id": message_id
                }
            )
        )

finally:
    client.close()
    print("Disconnected from server.")