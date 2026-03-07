# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What is DGP?

DGP (Deterministically Generated Passwords) is a password manager that derives passwords from a seed + secret using PBKDF2. It has three implementations:

1. **Flask web app** (`dgp/`) — the primary application
2. **C CLI tool** (`dgp-simple.c`) — standalone command-line version using OpenSSL
3. **Android app** (`android/`) — Kotlin port with biometric unlock

All implementations must produce identical output for the same inputs. The core algorithm: `PBKDF2(seed + secret, service_name)` → output in various formats (hex, base58, alnum, xkcd wordlist).

## Build & Run Commands

### Flask Web App
```bash
pip install --editable .
export FLASK_APP="dgp.factory:create_app()"
flask initdb          # initialize SQLite database
flask run             # runs on http://localhost:5000/
```

### Tests
```bash
pytest tests/                    # run all Python tests
pytest tests/test_dgp.py::test_login_logout  # single test
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

### Password Generation Pipeline
- **Web app engine**: `dgp/blueprints/engine.py` — uses `hashlib.pbkdf2_hmac('sha256', ..., iterations=8192)`
- **Standalone Python**: `simple.py` — uses PBKDF2 with SHA1, 42000 iterations (different params than web engine!)
- **C implementation**: `dgp-simple.c` — uses OpenSSL EVP, SHA256, 260000 iterations
- **Android/Kotlin**: `android/.../DgpEngine.kt` — uses PBKDF2WithHmacSHA1, 42000 iterations

**Warning**: The implementations use different hash functions and iteration counts. `simple.py` and the Android app use SHA1/42000 iterations. The web engine uses SHA256/8192 iterations. The C tool uses SHA256/260000 iterations. Be careful when modifying generation logic.

### Output Formats
All implementations support: `hex`, `hexlong`, `alnum`, `alnumlong`, `base58`, `base58long`, `xkcd`, `xkcdlong`. The `alnum` format uses base58 encoding and ensures the result contains uppercase, lowercase, and digits. The `xkcd` format uses `english.txt` (BIP-39 2048-word list).

### Flask App Structure
- `dgp/factory.py` — app factory with WSGI middleware chain (ReverseProxied → HTTPSRedirect → SecurityHeaders)
- `dgp/blueprints/dgp.py` — routes: `/login`, `/register`, `/logout`, `/add`, `/gen`, `/custom`
- `dgp/auth.py` — user auth with PBKDF2-SHA256 password hashing (260000 iterations)
- `dgp/schema.sql` — SQLite schema (users, entries tables)
- `dgp/blueprints/engine.py` — password generation engine
- Seed is read from a file at `dgp/seed`

### Key Files
- `english.txt` — BIP-39 wordlist (root dir), also symlinked/copied in `dgp/english.txt`
- `seed` — user's seed file (gitignored, must be created manually)
- `dgp.db` — SQLite database (gitignored, created by `flask initdb`)

## Configuration
- `DGP_SETTINGS` env var points to a Python config file
- `HTTPS_REDIRECT` and `SESSION_COOKIE_SECURE` env vars for production
- See `DEPLOYMENT.md` for production setup (Nginx, Gunicorn, systemd)
