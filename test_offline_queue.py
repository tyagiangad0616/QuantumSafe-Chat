#!/usr/bin/env python3
"""Offline queues expire, cap, and drop when a user registers a new key."""

import sys
import time

import server


def reset():
    with server.state_lock:
        server.connected_clients.clear()
        server.public_keys.clear()
        server.offline_queues.clear()
        server.socket_write_locks.clear()


def assert_true(condition, message):
    if not condition:
        print("FAIL:", message)
        sys.exit(1)


print("========================================")
print("Offline queue policy test")
print("========================================")

reset()
original_ttl = server.QUEUE_TTL_SECONDS
original_max = server.MAX_QUEUE_PER_USER
server.QUEUE_TTL_SECONDS = 1
server.MAX_QUEUE_PER_USER = 3

try:
    for index in range(5):
        server.queue_packet("Alice", {"type": "chat", "n": index})

    queued = server.offline_queues["Alice"]
    assert_true(len(queued) == 3, "queue must cap at MAX_QUEUE_PER_USER")
    assert_true(
        [item["packet"]["n"] for item in queued] == [2, 3, 4],
        "oldest packets must be dropped first"
    )

    time.sleep(1.1)
    remaining = server.pop_offline_queue("Alice")
    assert_true(remaining == [], "expired packets must not be delivered")

    server.queue_packet("Alice", {"type": "chat", "n": 9})
    alice = type("Sock", (), {"sendall": lambda *a, **k: None, "shutdown": lambda *a, **k: None, "close": lambda *a, **k: None})()
    server.register_client("Alice", "key", alice)
    assert_true(
        server.offline_queues.get("Alice") == [],
        "new public key must drop previously queued ciphertext"
    )
    print("SUCCESS: offline queue limits and key rotation hold")
finally:
    server.QUEUE_TTL_SECONDS = original_ttl
    server.MAX_QUEUE_PER_USER = original_max
    reset()
