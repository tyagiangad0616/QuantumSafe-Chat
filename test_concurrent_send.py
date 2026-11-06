#!/usr/bin/env python3
"""Per-socket write locks must keep two send_packet calls from interleaving."""

import sys
import threading
import time

import server
from protocol.packet import create_packet, decode_packet, encode_packet


class RecordingSocket:
    def __init__(self):
        self.bytes = bytearray()
        self.pause = threading.Event()

    def sendall(self, data):
        # Yield in the middle of a frame so a missing lock would interleave.
        if self.bytes:
            time.sleep(0.01)
        self.bytes.extend(data)

    def shutdown(self, *_args):
        return None

    def close(self):
        return None


def reset():
    with server.state_lock:
        server.socket_write_locks.clear()


def assert_true(condition, message):
    if not condition:
        print("FAIL:", message)
        sys.exit(1)


print("========================================")
print("Concurrent socket write test")
print("========================================")

reset()
sock = RecordingSocket()
first = create_packet("chat", {"n": 1, "pad": "A" * 64})
second = create_packet("chat", {"n": 2, "pad": "B" * 64})

threads = [
    threading.Thread(target=server.send_packet, args=(sock, first)),
    threading.Thread(target=server.send_packet, args=(sock, second)),
]
for thread in threads:
    thread.start()
for thread in threads:
    thread.join()

raw = bytes(sock.bytes)
first_frame = encode_packet(first)
second_frame = encode_packet(second)

assert_true(
    raw in (first_frame + second_frame, second_frame + first_frame),
    "frames must be concatenated, not interleaved"
)

decoded = [
    decode_packet(raw[4:len(first_frame)]),
    decode_packet(raw[len(first_frame) + 4:]),
]
assert_true(
    {packet["payload"]["n"] for packet in decoded} == {1, 2},
    "both packets must remain intact"
)

print("SUCCESS: concurrent sends stayed framed")
