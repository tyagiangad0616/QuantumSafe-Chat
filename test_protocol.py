import struct
import sys

from protocol.packet import (
    create_packet,
    decode_packet,
    encode_packet,
)


packet = create_packet(
    "TEST",
    {
        "message": "Hello QuantumSafe!"
    }
)

encoded = encode_packet(packet)
payload_length = struct.unpack("!I", encoded[:4])[0]
payload = encoded[4:]
decoded = decode_packet(payload)

if decoded != packet or payload_length != len(payload):
    print("ERROR: Packet system failed!")
    sys.exit(1)

print("SUCCESS: Framed packet system works!")
