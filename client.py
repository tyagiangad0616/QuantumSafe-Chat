import socket
import threading

from protocol.packet import create_packet, encode_packet, decode_packet


HOST = "127.0.0.1"
PORT = 5050


client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
client.connect((HOST, PORT))


username = input("Enter Username: ")


join_packet = create_packet(
    "join",
    {
        "username": username
    }
)

client.send(encode_packet(join_packet))


def receive_messages():
    while True:
        try:
            data = client.recv(4096)

            if not data:
                break

            packet = decode_packet(data)
            packet_type = packet["type"]

            if packet_type == "join_ack":
                print("\nConnected to QuantumSafe Chat!")

            elif packet_type == "message_ack":
                print("\n✓ Message sent")

            elif packet_type == "chat":
                sender = packet["payload"]["username"]
                message = packet["payload"]["message"]

                print(f"\n{sender}: {message}")

            elif packet_type == "leave":
                sender = packet["payload"]["username"]

                print(f"\n{sender} left the chat.")

        except Exception:
            break

    print("\nDisconnected from server.")


receiver_thread = threading.Thread(
    target=receive_messages,
    daemon=True
)

receiver_thread.start()


while True:
    message = input("You: ")

    if message.lower() == "exit":
        packet = create_packet(
            "leave",
            {}
        )

        client.send(encode_packet(packet))
        break

    packet = create_packet(
        "chat",
        {
            "message": message
        }
    )

    client.send(encode_packet(packet))


client.close()