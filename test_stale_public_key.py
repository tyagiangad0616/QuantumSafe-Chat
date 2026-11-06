#!/usr/bin/env python3
"""Disconnected users must not keep a stale ML-KEM public key on the relay."""

import sys

import server
from protocol.packet import decode_packet


class FakeSocket:
    def __init__(self):
        self.sent = []

    def sendall(self, data):
        self.sent.append(data)

    def shutdown(self, *_args):
        return None

    def close(self):
        return None

    def types(self):
        return [
            decode_packet(raw[4:]).get("type")
            for raw in self.sent
        ]

    def last_payload(self):
        return decode_packet(self.sent[-1][4:]).get("payload", {})


def reset():
    with server.state_lock:
        server.connected_clients.clear()
        server.public_keys.clear()
        server.offline_queues.clear()
        server.accounts.clear()
        server.devices.clear()
        server.pending_public_key_requests.clear()
        server.username_bindings.clear()
        server.socket_write_locks.clear()
    with server.message_state_lock:
        server.message_records.clear()


def assert_true(condition, message):
    if not condition:
        print("FAIL:", message)
        sys.exit(1)


print("========================================")
print("Stale public key test")
print("========================================")

reset()
alice = FakeSocket()
bob = FakeSocket()

server.register_client("Bob", "bob-key", bob)
server.register_client("Alice", "old-key", alice)
server.unregister_client("Alice", alice)

assert_true(
    "Alice" not in server.public_keys,
    "public key must be removed on disconnect"
)

server.handle_get_public_key(
    bob,
    "Bob",
    {"recipient": "Alice"}
)
assert_true(
    "public_key" not in bob.types(),
    "stale key must not be returned while Alice is offline"
)

server.register_client("Alice", "new-key", alice)
assert_true(
    "public_key" in bob.types(),
    "queued key request must be fulfilled after Alice rejoins"
)
assert_true(
    bob.last_payload().get("public_key") == "new-key",
    "requester must receive the new public key"
)

print("SUCCESS: stale public keys are invalidated")
