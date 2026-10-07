import struct

from protocol.packet import (
    create_packet,
    encode_packet,
    decode_packet
)


packet = create_packet(
    "TEST",
    {
        "message": "Hello QuantumSafe!"
    }
)


print("Original packet:")
print(packet)


encoded = encode_packet(
    packet
)


print("\nEncoded packet:")
print(encoded)


# First 4 bytes contain payload length

payload_length = struct.unpack(
    "!I",
    encoded[:4]
)[0]


payload = encoded[4:]


print("\nFrame length:")
print(payload_length)


print("\nDecoded packet:")

decoded = decode_packet(
    payload
)

print(decoded)


if decoded == packet:

    print(
        "\nSUCCESS: "
        "Framed packet system works!"
    )

else:

    print(
        "\nERROR: "
        "Packet system failed!"
    )