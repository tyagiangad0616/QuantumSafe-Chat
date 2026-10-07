# QuantumSafe Chat

Post-quantum secure messaging prototype developed during a CyberPeace Foundation internship (Research Department). Combines a **Python relay server + test client** with an **Android (Kotlin / Jetpack Compose)** client.

**Stack:** ML-KEM-768 · HKDF-SHA256 · AES-256-GCM · HMAC-SHA256 · TCP port **5050**

> **Status:** Working **prototype** for learning and demonstration — not production-hardened messaging.

---

## Repository layout

| Path | Description |
|------|-------------|
| `server.py`, `client.py` | Relay server and Python peer client |
| `pqc.py`, `crypto/` | ML-KEM-768 and session crypto helpers |
| `protocol/` | QSP packet encoding/decoding and acknowledgements |
| `test_*.py` | Protocol, PQC, replay, wrong-key, and tampering tests |
| `docs/` | Project plan and documentation |
| `screenshots/` | Evidence placeholders (add session/UI captures here) |
| `android/` | **Android Studio project** — Jetpack Compose UI, SQLite, multi-contact chat |

---

## Features (current)

### Python backend / protocol

- TCP client–server chat on `127.0.0.1:5050`
- Bidirectional messaging, threading, join/username flow
- QSP messaging with delivery acknowledgements
- ML-KEM-768 key establishment integrated into chat protocol
- Security-oriented tests (replay, wrong key, metadata tampering)

### Android client (`android/`)

- Kotlin + **Jetpack Compose** WhatsApp-style UI
- Encrypted messaging interoperable with the Python client
- Chat list, contacts, search, message bubbles, delivery ticks
- **SQLite** persistence across app restarts
- Multi-contact / per-peer sessions
- Account/device identity foundation (demo verification flow)

---

## Quick start (local)

### Python (requires [liboqs](https://github.com/open-quantum-safe/liboqs) / `oqs` Python bindings)

```bash
cd QuantumSafe-Chat
python3 -m venv venv && source venv/bin/activate
pip install liboqs-python   # or your environment’s oqs package
python server.py            # terminal 1
python client.py            # terminal 2
```

### Android

1. Open **`android/`** in Android Studio.
2. Ensure the Python server is reachable from the device/emulator (use host machine IP instead of `127.0.0.1` on a physical device).
3. Run the **app** configuration on a device or emulator.

---

## Internship context

- **Organization:** CyberPeace Foundation — Research Department  
- **Period:** 8 June 2026 – 8 August 2026 (onsite through 10 July 2026)  
- **Mentor / reporting:** Mr. Abhishek Sanjeev  

See `docs/project_plan.md` for early milestones. Add screenshots under `screenshots/` for report annexures.

---

## License

[Add license if required by your institution or mentor.]
