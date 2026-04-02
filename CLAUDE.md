# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What is DGP?

DGP (Deterministically Generated Passwords) is an Android password manager that derives passwords deterministically from a **seed + account secret + service name** using PBKDF2. Given the same inputs, it always produces the same password — no database of stored passwords is needed.

The repository was restructured in commit `62985ad` to be **Android-only**. All Python (Flask web app, `simple.py`), C CLI (`dgp-simple.c`), and related tooling were removed. The Android app, previously in `android/`, now lives at the repository root.

Historical implementations exist in git history:
- **Flask web app** — `dgp/blueprints/engine.py`, SHA256/8192 iterations
- **C CLI tool** — `dgp-simple.c`, SHA256/260000 iterations
- **Standalone Python** — `simple.py`, SHA1/42000 iterations

## Repository Structure

```
dgp/
├── app/
│   ├── build.gradle                        # App-level Gradle config
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   └── english.txt                 # BIP-39 2048-word list (for xkcd format)
│       └── java/com/dgp/
│           ├── MainActivity.kt             # Jetpack Compose UI (795 lines)
│           ├── engine/
│           │   ├── DgpEngine.kt            # Password generation algorithm
│           │   └── TestVectors.kt          # 50+ hardcoded test vectors
│           └── security/
│               ├── BiometricHelper.kt      # Android Keystore + biometric encryption
│               └── ConfigCrypto.kt         # PBKDF2+AES-GCM config encryption
├── gradle/wrapper/                         # Gradle wrapper (intentionally checked in)
├── build.gradle                            # Root Gradle build script
├── settings.gradle                         # rootProject.name = "DGP", includes :app
├── gradle.properties                       # AndroidX, Jetifier, JVM args, JDK path (checked in)
├── local.properties                        # Android SDK path (gitignored, machine-specific)
├── gradlew / gradlew.bat
└── CLAUDE.md
```

## Build & Run Commands

```bash
./gradlew assembleDebug      # build debug APK (requires JDK 21)
./gradlew assembleRelease    # build release APK
./gradlew build              # full build including tests
```

**Requirements:** JDK 21, Android SDK (path set in `local.properties`), Android SDK 34.

The Gradle wrapper (`gradle/wrapper/`) is intentionally checked into version control — standard Android practice that allows any developer to build without installing Gradle separately. `gradle.properties` is also checked in; it configures project-wide settings (AndroidX, Jetifier, JVM memory, JDK path). The only machine-specific file is `local.properties` (Android SDK path), which is gitignored.

## Architecture

### Password Generation Algorithm (`app/src/main/java/com/dgp/engine/DgpEngine.kt`)

- **Algorithm:** PBKDF2-HMAC-SHA1
- **Iterations:** 42,000
- **Key length:** 40 bytes
- **Key material:** `seed + account` (concatenated)
- **Salt:** `service_name`

Output formats (all derived from the same 40-byte key):

| Format | Description |
|--------|-------------|
| `hex` | First 16 bytes as hex (32 chars) |
| `hexlong` | All 40 bytes as hex (80 chars) |
| `base58` | Base58-encoded, 20 bytes |
| `base58long` | Base58-encoded, 40 bytes |
| `alnum` | Base58 subset enforcing uppercase + lowercase + digit |
| `alnumlong` | Long alnum variant |
| `xkcd` | 4 words from BIP-39 list |
| `xkcdlong` | 6 words from BIP-39 list |

Base58 alphabet: `123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz`

`alnum` format: generates multiple base58 passwords until one satisfies uppercase + lowercase + digit requirements.

`xkcd` format: uses BigInteger arithmetic to index into `english.txt` (2048 words, BIP-39).

### Security Architecture (`app/src/main/java/com/dgp/security/`)

**BiometricHelper.kt** — Seed encryption at rest:
- AES-256-GCM via Android Keystore with biometric requirement
- Key invalidated on new biometric enrollment
- Stores: IV (12 bytes) + ciphertext
- Falls back gracefully on older devices

**ConfigCrypto.kt** — Service config + account encryption:
- PBKDF2-HMAC-SHA256, 100,000 iterations, key derived from seed
- AES-256-GCM encryption
- Stores Base64(IV + ciphertext) in SharedPreferences
- Account field cleared on reboot and biometric failure

### UI (`app/src/main/java/com/dgp/MainActivity.kt`)

Jetpack Compose, Material3. Key UI states:
- **Locked** — seed entry dialog (manual or biometric unlock)
- **Unlocked** — service list with search, add/edit/delete
- **Generate** — account prompt → password display with clipboard copy
- **Settings** — seed management (change, export QR, import via camera QR scan)
- **Test vectors** — runs all 50+ test vectors with pass/fail UI

External dependency: `com.google.android.gms:play-services-code-scanner` for QR code import.

Sensitive data handling:
- `ClipboardManager` uses `EXTRA_IS_SENSITIVE = true` (Android 13+)
- Account field persisted encrypted with seed, cleared on reboot

### Test Vectors (`app/src/main/java/com/dgp/engine/TestVectors.kt`)

50+ hardcoded input/output pairs covering:
- 64-byte and 65-byte seeds (64-byte triggers pre-hashing in some implementations)
- Empty account string
- Various service name lengths
- All 8 output formats

Run from the app UI via the test button in settings.

## Key Conventions

1. **Never change PBKDF2 parameters** (SHA1, 42000 iterations) — breaks compatibility with all existing stored passwords.
2. **Test vectors are the source of truth** — any algorithm change must pass all vectors in `TestVectors.kt`.
3. **Generated passwords are never stored** — always re-derived on demand from seed + account + service. Other data (service configs, account field) may be stored encrypted.
4. **Seed security** — seed must remain in memory only while unlocked; always clear on lock/reboot.
5. **Error handling in crypto** — `ConfigCrypto.decrypt()` returns `null` on failure; callers must handle gracefully.

## Dependencies (app/build.gradle)

- `androidx.core:core-ktx:1.12.0`
- `androidx.lifecycle:lifecycle-runtime-ktx:2.7.0`
- `androidx.activity:activity-compose:1.8.2`
- `androidx.compose:compose-bom:2023.10.01` (UI, Material3)
- `androidx.biometric:biometric:1.1.0`
- `androidx.compose.material:material-icons-extended`
- `com.google.android.gms:play-services-code-scanner:16.1.0`

Min SDK: 26 (Android 8.0), Target SDK: 34, Kotlin 1.9.22, Compose compiler 1.5.8.
