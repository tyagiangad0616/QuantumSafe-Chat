# Android client

Project folder: `android/` (package `com.example.quantumsafechat`).

The Android app talks to the same Python relay as `client.py`. Payloads on the wire are encrypted; the relay forwards ciphertext.

## Sources

| File | Role |
|------|------|
| `MainActivity.kt` | UI, chat list, settings |
| `QuantumSafeNetwork.kt` | TCP protocol |
| `QuantumSafeCrypto.kt` | ML-KEM-768 and AES-GCM |
| `QuantumSafeIdentity.kt` | Account / device helpers and demo OTP |

## Notes

- Phone verification in this build is a fixed demo code (`123456`), not SMS.
- SQLite on the device is not encrypted.
- Do not commit `local.properties`.
- Use JDK 21 in Android Studio. Do not put a machine-specific `org.gradle.java.home` in the shared `gradle.properties`.
