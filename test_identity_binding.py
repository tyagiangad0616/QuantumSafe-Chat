#!/usr/bin/env python3
"""
A routing username bound to an account cannot be stolen
by another account or by a client that omits account_id.
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

    def types(self):
        return [
            decode_packet(raw[4:]).get("type")
            for raw in self.sent
        ]


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


def assert_true(condition, message):
    if not condition:
        print("FAIL:", message)
        sys.exit(1)


def join(sock, **payload):
    return server.handle_join(sock, payload)


def main():
    reset_server_state()

    owner = FakeSocket()
    thief = FakeSocket()
    legacy = FakeSocket()

    result = join(
        owner,
        username="Angad",
        account_id="qs_owner",
        device_id="android_1",
        phone_number="+919999999999",
        display_name="Angad",
        public_key="owner-key",
    )
    assert_true(result == "Angad", "account join must succeed")
    assert_true(
        server.username_bindings.get("Angad") == "qs_owner",
        "username must bind to the joining account",
    )
    assert_true("join_ack" in owner.types(), "owner must receive join_ack")

    stolen = join(
        thief,
        username="Angad",
        account_id="qs_thief",
        device_id="android_2",
        phone_number="+911111111111",
        display_name="Fake Angad",
        public_key="thief-key",
    )
    assert_true(stolen is None, "another account must not take the username")
    assert_true("error" in thief.types(), "username theft must return an error")
    assert_true(
        server.username_bindings.get("Angad") == "qs_owner",
        "original account binding must remain",
    )

    impersonated = join(
        legacy,
        username="Angad",
        public_key="legacy-key",
    )
    assert_true(
        impersonated is None,
        "a join without account_id must not steal a bound username",
    )
    assert_true("error" in legacy.types(), "legacy impersonation must return an error")

    reconnect = FakeSocket()
    again = join(
        reconnect,
        username="Angad",
        account_id="qs_owner",
        device_id="android_1",
        phone_number="+919999999999",
        display_name="Angad",
        public_key="owner-key-2",
    )
    assert_true(again == "Angad", "same account must be allowed to reconnect")
    assert_true("join_ack" in reconnect.types(), "reconnect must receive join_ack")

    python_peer = FakeSocket()
    peer = join(
        python_peer,
        username="Shriyaa",
        public_key="python-key",
    )
    assert_true(
        peer == "Shriyaa",
        "Python clients without account_id must still join unbound names",
    )

    print("SUCCESS: usernames bind to accounts")
    print("  - other account cannot steal a bound username")
    print("  - join without account_id cannot impersonate a bound user")
    print("  - same account can reconnect")
    print("  - Python peer without account_id still works for unbound names")


if __name__ == "__main__":
    main()
