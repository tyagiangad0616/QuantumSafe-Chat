#!/usr/bin/env python3
"""
Delivery ACK must come from the intended recipient.

A third client must not be able to mark another user's
message as delivered.
"""

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

    def packets(self):
        decoded = []
        for raw in self.sent:
            decoded.append(decode_packet(raw[4:]))
        return decoded

    def types(self):
        return [packet.get("type") for packet in self.packets()]


def reset_server_state():
    with server.state_lock:
        server.connected_clients.clear()
        server.public_keys.clear()
        server.offline_queues.clear()
        server.accounts.clear()
        server.devices.clear()
        server.pending_public_key_requests.clear()
        server.username_bindings.clear()
    with server.message_state_lock:
        server.message_records.clear()


def register(name, sock):
    server.register_client(name, "test-public-key-" + name, sock)


def send_chat(sender_sock, sender, recipient, message_id):
    server.handle_chat(
        sender_sock,
        sender,
        {
            "recipient": recipient,
            "message_id": message_id,
            "nonce": "dGVzdA==",
            "ciphertext": "dGVzdA==",
        },
    )


def assert_true(condition, message):
    if not condition:
        print("FAIL:", message)
        sys.exit(1)


def main():
    reset_server_state()

    angad = FakeSocket()
    shriyaa = FakeSocket()
    mallory = FakeSocket()

    register("Angad", angad)
    register("Shriyaa", shriyaa)
    register("Mallory", mallory)

    message_id = "ack-auth-test-001"
    send_chat(angad, "Angad", "Shriyaa", message_id)

    record = server.message_records.get(message_id)
    assert_true(record is not None, "chat must store a message record")
    assert_true(record["sender"] == "Angad", "record sender must be Angad")
    assert_true(
        record["recipient"] == "Shriyaa",
        "record recipient must be Shriyaa",
    )

    angad.sent.clear()
    mallory.sent.clear()

    server.handle_delivery_ack(
        mallory,
        "Mallory",
        {"message_id": message_id},
    )

    assert_true(
        "delivery_ack" not in angad.types(),
        "third-party ACK must not be forwarded to the sender",
    )
    assert_true(
        "error" in mallory.types(),
        "forged ACK must return an error to the claimant",
    )
    assert_true(
        server.message_records[message_id]["delivered"] is False,
        "forged ACK must not mark the message delivered",
    )

    shriyaa.sent.clear()
    angad.sent.clear()

    server.handle_delivery_ack(
        shriyaa,
        "Shriyaa",
        {"message_id": message_id},
    )

    assert_true(
        "delivery_ack" in angad.types(),
        "intended recipient ACK must reach the original sender",
    )
    assert_true(
        server.message_records[message_id]["delivered"] is True,
        "valid ACK must mark the message delivered",
    )

    angad.sent.clear()
    shriyaa.sent.clear()

    server.handle_delivery_ack(
        shriyaa,
        "Shriyaa",
        {"message_id": message_id},
    )

    assert_true(
        "delivery_ack" not in angad.types(),
        "duplicate ACK must not be forwarded again",
    )
    assert_true(
        "error" in shriyaa.types(),
        "duplicate ACK must be rejected",
    )

    expired_id = "ack-auth-test-expired"
    send_chat(angad, "Angad", "Shriyaa", expired_id)
    with server.message_state_lock:
        server.message_records[expired_id]["created_at"] -= (
            server.ACK_TTL_SECONDS + 1
        )

    angad.sent.clear()
    shriyaa.sent.clear()
    server.handle_delivery_ack(
        shriyaa,
        "Shriyaa",
        {"message_id": expired_id},
    )
    assert_true(
        "delivery_ack" not in angad.types(),
        "expired ACK must not be forwarded",
    )
    assert_true(
        "error" in shriyaa.types(),
        "expired ACK must be rejected",
    )

    print("SUCCESS: delivery acknowledgements are bound to the intended recipient")
    print("  - third-party ACK rejected")
    print("  - intended recipient ACK accepted")
    print("  - duplicate ACK rejected")
    print("  - expired ACK rejected")


if __name__ == "__main__":
    main()
