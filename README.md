# QuantumSafe Chat

A small prototype of encrypted chat over a local TCP relay. It uses ML-KEM-768 for key establishment and AES-256-GCM for message payloads. This is a learning build, not a production messenger.

Assigned work during an internship at CyberPeace Foundation (Research Department).

**Relay:** Python (`server.py`) on port 5050  
**Peers:** Python `client.py` and an Android app in `android/`

---

## Layout

| Path | Description |
|------|-------------|
| `server.py`, `client.py` | Relay and a terminal peer |
| `pqc.py`, `crypto/` | ML-KEM helpers and session crypto |
| `protocol/` | Length-prefixed JSON packets |
| `test_*.py` | Protocol and crypto checks |
| `docs/` | Notes and an early plan |
| `screenshots/` | Optional captures |
| `android/` | Kotlin / Compose client |

---

## What it does

- Encrypted chat between two peers through a relay that does not decrypt payloads
- Per-contact sessions on Android
- Delivery acknowledgements bound to the intended recipient
- Account ids derived from a phone number, with a **demo OTP** (`123456`) so the flow can be shown without SMS
- Local SQLite history on Android (plaintext; Android backup is off)

Limitations that are intentional for this prototype: no real SMS, no encrypted local database, no Play Store packaging.

---

## Run locally

Python peers need [liboqs](https://github.com/open-quantum-safe/liboqs) / `liboqs-python`. The relay itself does not import `oqs`.

```bash
cd QuantumSafe-Chat
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt
python server.py            # terminal 1
python client.py            # terminal 2
```

Optional environment variables: `QUANTUMSAFE_HOST`, `QUANTUMSAFE_PORT`, `QUANTUMSAFE_MAX_CONNECTIONS`.

Checks that do not need liboqs:

```bash
python3 test_packet_limits.py
python3 test_concurrent_send.py
python3 test_stale_public_key.py
python3 test_offline_queue.py
python3 test_identity_binding.py
python3 test_delivery_ack.py
python3 test_protocol.py
```

### Android

1. Open `android/` in Android Studio (JDK 21 / JBR 21).
2. Start `server.py` on the host.
3. Emulator: the app tries `10.0.2.2` then `127.0.0.1`. Physical device: `adb reverse tcp:5050 tcp:5050`, or set the host LAN IP under Settings.

---


