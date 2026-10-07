import json
import struct


HEADER_SIZE = 4


def create_packet(packet_type, payload):
    return {
        "type": packet_type,
        "payload": payload
    }


def encode_packet(packet):
    """
    Convert a packet into a framed byte sequence.

    Format:
        [4-byte payload length][JSON payload]
    """
    payload = json.dumps(packet).encode("utf-8")

    header = struct.pack("!I", len(payload))

    return header + payload


def decode_packet(data):
    """
    Decode JSON payload bytes into a Python dictionary.
    """
    return json.loads(data.decode("utf-8"))


def receive_packet(sock):
    """
    Receive exactly one framed packet from a TCP socket.
    """

    header = _receive_exact(sock, HEADER_SIZE)

    if not header:
        return None

    payload_length = struct.unpack("!I", header)[0]

    if payload_length <= 0:
        raise ValueError("Invalid packet length")

    payload = _receive_exact(sock, payload_length)

    if payload is None:
        return None

    return decode_packet(payload)


def _receive_exact(sock, size):
    """
    Receive exactly 'size' bytes from the socket.
    """

    data = bytearray()

    while len(data) < size:
        chunk = sock.recv(size - len(data))

        if not chunk:
            return None

        data.extend(chunk)

    return bytes(data)