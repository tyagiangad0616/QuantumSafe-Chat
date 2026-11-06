#!/usr/bin/env python3
"""Oversized length-prefixed frames must be rejected without reading the body."""

import struct
import sys

from protocol.packet import (
    MAX_PAYLOAD_BYTES,
    PacketTooLarge,
    create_packet,
    encode_packet,
    receive_packet,
)


class MockSock:
    def __init__(self, data):
        self.data = data

    def recv(self, size):
        chunk = self.data[:size]
        self.data = self.data[size:]
        return chunk


def assert_true(condition, message):
    if not condition:
        print("FAIL:", message)
        sys.exit(1)


print("========================================")
print("Packet size limit test")
print("========================================")

packet = create_packet("ping", {"ok": True})
framed = encode_packet(packet)
decoded = receive_packet(MockSock(framed))
assert_true(decoded == packet, "valid packet must round-trip")
print("1. Valid packet accepted")

too_big = struct.pack("!I", MAX_PAYLOAD_BYTES + 1)
try:
    receive_packet(MockSock(too_big))
    print("FAIL: oversized header was accepted")
    sys.exit(1)
except PacketTooLarge:
    print("2. Oversized header rejected before body read")

try:
    encode_packet({
        "type": "chat",
        "payload": {"blob": "x" * (MAX_PAYLOAD_BYTES + 32)}
    })
    print("FAIL: oversized encode was accepted")
    sys.exit(1)
except PacketTooLarge:
    print("3. Oversized encode rejected")

print("SUCCESS: packet size limits hold")
