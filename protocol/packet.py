import json


def create_packet(packet_type, payload):
    return {
        "type": packet_type,
        "payload": payload
    }


def encode_packet(packet):
    return json.dumps(packet).encode()


def decode_packet(data):
    return json.loads(data.decode())