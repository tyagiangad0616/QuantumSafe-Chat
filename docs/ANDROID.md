# Android client — QuantumSafe Chat

## Location

Android Studio project: **`android/`** (package `com.example.quantumsafechat`).

## What to highlight on GitHub / in your internship report

1. **Open the `android/` folder** in this repo — shows the full Compose UI and networking code, not just the Python backend.
2. **Screenshots** — add PNGs under `screenshots/android/` (chat list, active chat with ticks, secure-session indicator, settings). Reference them in the main `README.md`.
3. **Interoperability** — note in the report that Android and Python clients exchange **encrypted** traffic through the relay server; server logs should show forwarded ciphertext only.
4. **Honest scope** — describe as a **prototype**: SQLite on device, demo account verification, in-memory/ephemeral server queues per progress notes.

## Key source files

| File | Role |
|------|------|
| `app/src/main/java/.../MainActivity.kt` | Compose UI, navigation, chat list, messaging |
| `QuantumSafeNetwork.kt` | Socket protocol / server communication |
| `QuantumSafeCrypto.kt` | PQC + symmetric crypto integration on Android |
| `QuantumSafeIdentity.kt` | Account / device identity helpers |

## Building

- **minSdk / targetSdk:** see `android/app/build.gradle.kts`
- Do **not** commit `local.properties` (machine-specific SDK path)

## Related material

- Account-foundation experiments may also exist locally as `QuantumSafe_STEP1_ACCOUNT_FOUNDATION` (merge into `android/` when ready).
