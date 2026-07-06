import socket
import threading

def receive_messages():

    while True:

        try:
            message = client.recv(1024).decode()

            if message:
                print("\nServer:", message)

        except:
            print("\nConnection Closed")
            break

client = socket.socket()

client.connect(("127.0.0.1", 5050))

print("Connected To Server!")

receiver_thread = threading.Thread(target=receive_messages)
receiver_thread.start()

while True:

    message = input("You: ")

    client.send(message.encode())

    if message.lower() == "exit":
        break

client.close()