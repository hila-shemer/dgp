# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What is DGP?

DGP (Deterministically Generated Passwords) is a password manager that derives passwords from a seed + secret using PBKDF2. It has three implementations:

1. **Flask web app** (`dgp/`) — the primary application
2. **C CLI tool** (`dgp-simple.c`) — standalone command-line version using OpenSSL
3. **Android app** (`android/`) — Kotlin/Jetpack Compose port with biometric unlock

The core algorithm: `PBKDF2(seed + secret, service_name)` → output in various formats (hex, base58, alnum, xkcd wordlist).

**Important**: The web engine uses different PBKDF2 parameters (SHA256/8192 iterations) than simple.py, the C tool, and the Android app (all SHA1/42000 iterations). The latter three are mutually compatible; the web engine is not.

## Directory Structure

```
dgp/                          # Flask web application (primary)
│   factory.py                # App factory, WSGI middleware chain
│   auth.py                   # User auth (PBKDF2-SHA256, 260000 iterations)
│   security.py               # SecurityHeaders, HTTPSRedirect middleware
│   schema.sql                # SQLite schema (users, entries tables)
│   english.txt               # BIP-39 wordlist (2048 words)
│   seed                      # Master seed file (gitignored, create manually)
│   blueprints/
│       dgp.py                # Routes: /login /register /logout /add /gen /custom
│       engine.py             # Password generation (PBKDF2-SHA256, 8192 iterations)
│   templates/                # Jinja2 templates (layout, login, show_entries, register)
│   static/                   # CSS and static HTML
android/                      # Kotlin/Compose Android app
│   app/src/main/java/com/dgp/
│       MainActivity.kt       # Compose UI, seed entry, service list, generation
│       engine/
│           DgpEngine.kt      # Password generation (PBKDF2-SHA1, 42000 iterations)
│           TestVectors.kt    # 54 built-in test vectors
│       security/
│           BiometricHelper.kt  # AES-GCM seed storage via AndroidKeyStore + biometric
│           ConfigCrypto.kt     # AES-GCM encryption for service config and account
tests/
│   test_dgp.py               # Pytest tests for Flask app
│   basic.sh                  # Shell tests for C tool (via CTest)
dgp-simple.c                  # C CLI (PBKDF2-SHA1 via OpenSSL, 42000 iterations for gen)
genseed.c                     # Seed generation utility (C)
simple.py                     # Python CLI (PBKDF2-SHA1, 42000 iterations)
english.txt                   # BIP-39 wordlist (root; also in dgp/english.txt)
setup.py                      # Python package config
Makefile                      # C compilation
CMakeLists.txt                # CMake build + CTest integration
DEPLOYMENT.md                 # Production setup (Nginx, Gunicorn, systemd)
```

## Build & Run Commands

### Flask Web App
```bash
pip install --editable .
export FLASK_APP="dgp.factory:create_app()"
echo "my-master-seed" > dgp/seed   # create seed file (gitignored)
flask initdb                        # initialize SQLite database
flask run                           # runs on http://localhost:5000/
```

### Tests
```bash
pytest tests/                                       # run all Python tests
pytest tests/test_dgp.py::test_login_logout         # single test
```

### C CLI Tools
```bash
make dgp-simple       # requires libcrypto (OpenSSL)
make genseed
# Or via CMake:
cmake -B build && cmake --build build
ctest --test-dir build  # runs tests/basic.sh
```

### Standalone CLI (Python)
```bash
python simple.py test-vectors                          # print test vectors
python simple.py <seed> <account> <name> <type>        # generate a password
```

### Android
```bash
cd android && ./gradlew assembleDebug    # requires JDK 21
```

## Architecture

### Password Generation: Implementation Comparison

| Aspect | Web Engine | simple.py / C Tool | Android |
|---|---|---|---|
| File | `dgp/blueprints/engine.py` | `simple.py`, `dgp-simple.c` | `DgpEngine.kt` |
| Hash | SHA-256 | SHA-1 | SHA-1 |
| Iterations | 8192 | 42000 | 42000 |
| dklen | 32 bytes | 40 bytes | 40 bytes |
| Compatible? | No (standalone) | Yes (with each other) | Yes (with simple.py/C) |

**Warning**: Do not change these parameters without updating all compatible implementations. simple.py, the C tool, and the Android app use SHA1/42000 and produce identical output; the web engine uses different parameters and is not cross-compatible.

### Output Formats

All implementations support: `hex`, `hexlong`, `alnum`, `alnumlong`, `base58`, `base58long`, `xkcd`, `xkcdlong`.

- `hex` / `hexlong` — hex-encoded (8 or 16 chars)
- `alnum` / `alnumlong` — base58 encoded, guaranteed to contain uppercase + lowercase + digits (8 or 12 chars)
- `base58` / `base58long` — base58 encoded (8 or 12 chars, without the alnum guarantee)
- `xkcd` / `xkcdlong` — words from BIP-39 wordlist (4 or 8 words)

### Flask App Structure

- `dgp/factory.py` — app factory with WSGI middleware chain (order: `ReverseProxied` → `HTTPSRedirect` → `SecurityHeaders`)
- `dgp/blueprints/dgp.py` — routes: `/login`, `/register`, `/logout`, `/add`, `/gen`, `/custom`
- `dgp/auth.py` — user auth with PBKDF2-SHA256 password hashing (260000 iterations); stored as `pbkdf2_sha256$iters$salt_hex$hash_hex`
- `dgp/schema.sql` — SQLite schema (`users` with id/username/email/password_hash/created_at/last_login; `entries` with id/user_id/name/type/note/created_at)
- `dgp/blueprints/engine.py` — password generation engine (PBKDF2-SHA256, 8192 iterations)
- `dgp/security.py` — adds security headers (CSP, HSTS, X-Frame-Options, X-Content-Type-Options); handles HTTPS redirect
- Seed is read from `dgp/seed` (plaintext file; must be created manually; gitignored)

### Android App Structure

- **UI**: Jetpack Compose with Material 3; coroutine-based state management
- **DgpEngine.kt**: Stateless password generator using PBKDF2WithHmacSHA1 via `javax.crypto.SecretKeyFactory`; uses `BigInteger` for base58 conversion; loads wordlist from `assets/english.txt`
- **BiometricHelper.kt**: Encrypts/decrypts seed using AES/GCM/NoPadding in AndroidKeyStore; biometric authentication required for every decrypt; key is invalidated if new biometrics are enrolled; stores `IV (12 bytes) + ciphertext` as Base64
- **ConfigCrypto.kt**: Encrypts service list and account info using PBKDF2-SHA256 (100000 iterations) + AES-GCM; format is `Base64(IV + ciphertext)`
- **TestVectors.kt**: 54 hard-coded test vectors run from the UI (long-press test icon in toolbar)
- **Account field**: Encrypted with seed via `ConfigCrypto`; cleared on device reboot or auth failure

### Key Files

- `english.txt` — BIP-39 wordlist (root dir); also present in `dgp/english.txt` and `android/app/src/main/assets/english.txt`
- `dgp/seed` — master seed file (gitignored, must be created manually)
- `dgp.db` — SQLite database (gitignored, created by `flask initdb`)

## Configuration

- `DGP_SETTINGS` env var points to a Python config file (loaded after built-in defaults)
- `HTTPS_REDIRECT` env var — enable HTTP→HTTPS redirect (set to `true` in production)
- `SESSION_COOKIE_SECURE` env var — require HTTPS for session cookies (set to `true` in production)
- See `DEPLOYMENT.md` for production setup (Nginx, Gunicorn, systemd)

### Production Config Example

```python
# production_config.py — pointed to by DGP_SETTINGS
HTTPS_REDIRECT = True
SESSION_COOKIE_SECURE = True
SESSION_COOKIE_HTTPONLY = True
SESSION_COOKIE_SAMESITE = 'Lax'
DEBUG = False
SECRET_KEY = 'your-random-secret-key'
```

## Testing

### Python (Flask)

Tests live in `tests/test_dgp.py`. They use a temporary SQLite database per test session. Key tests:

- `test_empty_db` — initial state
- `test_login_logout` — auth flow (default user: admin/default)
- `test_messages` — entry creation
- `test_user_registration` — register new user (testuser/testpass123)
- `test_user_login` — login as new user
- `test_duplicate_username` — validation

### C Tool

Tests run via CTest: `ctest --test-dir build`. Internally runs `tests/basic.sh`.

### Android

Built-in test vectors in `TestVectors.kt` can be run from the UI. No separate Android unit tests.

## Conventions

- **Never change PBKDF2 parameters** in `engine.py`, `simple.py`, `dgp-simple.c`, or `DgpEngine.kt` without understanding cross-compatibility implications.
- **Output type names** are a fixed set of 8 strings: `hex`, `hexlong`, `alnum`, `alnumlong`, `base58`, `base58long`, `xkcd`, `xkcdlong`. All implementations must support the same names.
- **Wordlist**: BIP-39 2048-word list. The file at the repo root is authoritative; copies in `dgp/` and `android/` must stay in sync.
- **Seed file**: Plaintext, one line, stored at `dgp/seed` (gitignored). Do not commit it.
- **Database**: SQLite, created by `flask initdb`. Schema in `dgp/schema.sql`. Never modify schema without updating `schema.sql`.
- **User entries are isolated**: Each user sees only their own service entries (`entries.user_id` FK).
- **Default credentials**: admin/default (hardcoded fallback in `dgp.py`; exists for backward compatibility — do not remove).
