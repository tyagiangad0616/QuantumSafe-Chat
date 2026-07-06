import socket

server = socket.socket()
server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

server.bind(("127.0.0.1", 5050))
server.listen()

print("Server Started...")
print("Waiting For Client...")

client_socket, address = server.accept()

print("Client Connected:", address)

while True:

    message = client_socket.recv(1024).decode()

    if message.lower() == "exit":
        print("Client Disconnected")
        break

    print("Client:", message)

    reply = input("You: ")

    client_socket.send(reply.encode())

    if reply.lower() == "exit":
        break

client_socket.close()
server.close()