from protocol.packet import create_packet, encode_packet, decode_packet

packet = create_packet(
    "TEST",
    {
        "message": "Hello QuantumSafe!"
    }
)

print("Original packet:")
print(packet)

encoded = encode_packet(packet)

print("\nEncoded packet:")
print(encoded)

decoded = decode_packet(encoded)

print("\nDecoded packet:")
print(decoded)

if decoded == packet:
    print("\nSUCCESS: Packet system works!")
else:
    print("\nERROR: Packet system failed!")