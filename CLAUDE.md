# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What is DGP?

DGP (Deterministically Generated Passwords) is an Android password manager that derives passwords deterministically from a **seed + account secret + service name** using PBKDF2. Given the same inputs it always produces the same password — no database of stored passwords is needed.

The repo was restructured in commit `62985ad` to be Android-only; prior Flask/C/Python implementations live in git history.

## Build & Test Commands

```bash
./gradlew assembleDebug                                        # debug APK
./gradlew assembleRelease                                      # release APK
./gradlew :app:test                                            # JVM unit tests
./gradlew :app:testDebugUnitTest --tests com.dgp.engine.DgpEngineTest   # single test class
./gradlew :app:connectedDebugAndroidTest                       # instrumentation tests (needs emulator/device)
```

**Requirements:** JDK 21 (path in `gradle.properties` → `org.gradle.java.home`), Android SDK 34 (path in `local.properties`, gitignored). AGP 8.7.3, Kotlin 1.9.25, Compose compiler 1.5.15, compose-bom 2024.10.01. Min SDK 26, target SDK 34.

Tests split:
- `app/src/test/` — JVM-only: `DgpEngineTest`, `ServiceParsingTest`.
- `app/src/androidTest/` — instrumentation: `MainActivityTest` (Compose UI), `ConfigCryptoInstrumentedTest`, `BiometricHelperInstrumentedTest`.

`app/build.gradle` adds `src/main/assets` to the test `resources.srcDirs` so `DgpEngineTest` can load the real BIP-39 word list from the JVM without Android assets infra.

CI (`.github/workflows/ci.yml`) runs both: JVM on every push, instrumentation on an API-30 x86_64 emulator with KVM acceleration.

## Architecture

### Password generation — `app/src/main/java/com/dgp/engine/DgpEngine.kt`

- **Algorithm:** PBKDF2-HMAC-SHA1, 42,000 iterations, 40-byte key.
- **Key material:** `seed + account` (concatenated). **Salt:** `service_name`.
- Signature: `generate(seed, name, entryType, secret, iterations = 42000)` — `name` is the service (salt), `secret` is the account. Non-obvious order; don't reshuffle.

Eight output formats share the same 40-byte key: `hex`/`hexlong`, `base58`/`base58long`, `alnum`/`alnumlong` (base58 window with at least one upper+lower+digit), `xkcd`/`xkcdlong` (4 or 6 BIP-39 words indexed via BigInteger). Base58 alphabet: `123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz`.

A ninth entry type `aeskey` returns the first 32 bytes of the PBKDF2 output as a hex-encoded 64-char string. It is never shown to the user — it exists as key material for **vault entries** (see below). `DgpEngine.deriveAesKey(seed, name, secret)` is a convenience wrapper that returns the raw 32-byte `ByteArray`.

### Vault entries

`DgpService` has an optional `encryptedSecret: String?` holding `Base64(IV ‖ AES-256-GCM ciphertext)`. Only set for entries with `type == "vault"`. The encryption key is derived via `DgpEngine.deriveAesKey(seed, service.name, account)` — same inputs as password gen, so renaming a vault service or changing the account/seed invalidates its secret. Editing a vault entry pre-decrypts the blob with the **current** name+account so the user sees the existing plaintext; saving always re-encrypts with the (possibly new) name/account, which naturally handles rename. `ConfigCrypto.encryptWithRawKey` / `decryptWithRawKey` do AES-GCM with a caller-supplied 32-byte key (no PBKDF2 upstream).

Vault entries are the **only** user-chosen secret material the app stores. They exist for legacy/externally-assigned passwords and OTP seeds that aren't deterministically derivable. See invariant #3 below.

### Security — `app/src/main/java/com/dgp/security/`

**BiometricHelper.kt** — seed encryption at rest. AES-256-GCM via Android Keystore, key requires biometric auth and is invalidated on new enrollment. Stores IV (12 B) ‖ ciphertext. Falls back gracefully on older devices.

**ConfigCrypto.kt** — service list + account encryption.
- Local storage: PBKDF2-HMAC-SHA256, **256 iterations**, 256-bit key, AES-256-GCM. Stored as `Base64(IV ‖ ciphertext)` in SharedPreferences. The low iteration count is intentional: encryption here is a speedbump on top of biometric-gated seed access, not the primary defense.
- Export/import-from-clipboard: PBKDF2-HMAC-SHA256, **600,000 iterations** (user-supplied PIN is the only secret).
- `decrypt()` returns `null` on failure; callers must handle.

### UI — `app/src/main/java/com/dgp/MainActivity.kt` (~1.1k lines)

Jetpack Compose + Material3. `MainActivity` extends `FragmentActivity` (required by `BiometricPrompt`). States: locked (seed entry / biometric unlock) → unlocked (service list, search, manual drag-reorder via `sh.calvin.reorderable`, archive toggle) → tap a service to generate → password display with clipboard copy. Settings cover seed change, PIN-encrypted config export (share-sheet) / import (from clipboard), plaintext-JSON file import, QR-code scanning (used to fill the seed field), and the test-vector runner.

Clipboard copy sets `ClipDescription.EXTRA_IS_SENSITIVE = true` (Android 13+). The account field is persisted encrypted with the seed and cleared on reboot and on biometric failure.

### Test vectors — `app/src/main/java/com/dgp/engine/TestVectors.kt`

50+ input/output pairs covering 64-byte (triggers pre-hashing in some impls) and 65-byte seeds, empty-account edge, varied service lengths, all 8 formats. Exposed in the app via the settings → test-vectors screen; also covered by `DgpEngineTest`.

## Non-obvious gotchas

- **FragmentActivity + Compose ActivityResult don't compose.** `rememberLauncherForActivityResult` hangs under `FragmentActivity` in this project, so the file-import path uses classic `startActivityForResult` with `REQUEST_IMPORT_FILE = 42`. Don't "modernize" this.
- **`androidx.tracing:tracing:1.2.0`** is a required `implementation` dep (not a test dep). Compose UI test 1.7+ calls `Trace.forceEnableAppTracing()`, which only exists in tracing ≥ 1.2.0. Without it, instrumentation tests throw `NoSuchMethodError` with the misleading message "No compose hierarchies found".
- **Services list is manually ordered**, not alphabetical — order is the JSON array order, persisted via `ConfigCrypto`.

## Invariants

1. **Never change the password-generation PBKDF2 parameters** (SHA1, 42,000 iterations, 40-byte key, `seed+account` as key material, service name as salt). Any change breaks every password anyone has ever derived.
2. **Test vectors are the source of truth** — any algorithm change must keep `TestVectors.kt` / `DgpEngineTest` green.
3. **Generated passwords are never stored** — always re-derived on demand. Only service configs, the encrypted account field, and (for `vault` entries) an encrypted user-supplied secret are persisted. Vault entries are the sole exception to "nothing secret is stored"; losing the config therefore loses those secrets.
4. **Seed lives in memory only while unlocked** — clear on lock and on reboot.
