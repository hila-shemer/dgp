# DGP Web App Implementation — Design

**Date:** 2026-06-14
**Status:** Approved (brainstorming complete; implementation plan to follow)
**Predecessor:** `2026-06-12-web-design-phase-artifacts-design.md` (the design-phase artifact bundle pushed to the "DGP Web" Claude Design project). This spec is the **implementation** phase that the predecessor deferred ("the web app rewrite itself — stack choice, backend, WebCrypto port — comes after the design phase").

## Goal

Build a full Android-parity DGP web application: a deterministic password manager whose derivation runs entirely client-side (WebCrypto), backed by a thin **zero-knowledge sync-hub server** that stores only an opaque encrypted config blob per account. The server is designed as the eventual universal sync point for all three clients (web, Android, Linux), but only the **web** client is built here.

DGP itself: `PBKDF2(seed+account, salt=service) → password`, recomputed on demand, never stored. Vault entries hold externally-assigned secrets encrypted under a seed-derived key. Identity is surfaced via the pride-flag fingerprint (two BIP-39 words + flag).

## Scope boundary

**In scope (this effort, one plan):**
- Full-parity web app (all screens below) — TypeScript SPA.
- The sync-hub server (FastAPI + SQLite).
- The web sync client (encrypt/PUT, GET/decrypt, conflict merge).
- The full authentication stack (see Authentication): seed-proof keypair + passphrase + TOTP 2FA + trusted-device management + new-device enrollment/recovery.
- A documented **sync API contract** (`web/shared/`) so Android and Linux can target it later.

**Out of scope (follow-on sibling plans, not this one):**
- The Android (Kotlin) sync client.
- The Linux (Python) sync client.
- Any change to the legacy Flask deployment beyond replacing it.
- Visual-design decisions (handled in the "DGP Web" Claude Design project); this spec is structure/architecture, not look-and-feel. **Note:** the new authentication model (below) supersedes the username/password login+register cards in the pushed design artifacts — those should be revisited in Claude Design.

## Decisions (made with user, 2026-06-14)

1. **Deployment:** single-tenant, self-hosted; replaces the live Flask app on `192.168.1.108:5000`. No public registration, no hostile-public hardening.
2. **Sync model:** server is the **universal sync hub** (architecture supports web + Android + Linux). Only the web client is built now; Android/Linux adopt the API later.
3. **First milestone size:** full parity in one plan (not a thin slice).
4. **Backend stack:** Python + FastAPI + SQLite.
5. **Frontend stack:** vanilla TypeScript + Vite (minimal dependency surface for code that handles the seed).
6. **Config blob encryption key:** **seed-derived** → truly zero-knowledge server (the seed never leaves the client; the password *does* reach the server at login, so a password-derived key would not be zero-knowledge).
7. **Auth — relationship to password:** keep an account passphrase **and** add seed-proof; full MFA stack built in plan 1.
8. **Auth — seed-proof mechanism:** asymmetric keypair; server stores only the public key.
9. **Auth — 2FA knowledge factor:** a **dedicated account passphrase** (not a remembered derived service password, which is short and would leak a real credential on DB compromise).

## Architecture

### Repo layout

Mirrors the existing `app/` (Android) and `linux/` (Python CLI):

```
web/
  client/    # vanilla TS SPA (Vite): full-parity UI + WebCrypto crypto module
  server/    # FastAPI + SQLite: auth + opaque encrypted-blob store
  shared/    # sync API contract (OpenAPI) + configblob/export byte-format specs
```

### Two layers: authenticate vs. unlock

Because the config blob is encrypted under a **seed-derived** key, authenticating to the server and *reading data* are distinct:

- **Login / sync layer** — proves identity to the server; grants the ability to fetch/replace the *encrypted* blob and manage the account. Achieved by either auth path (below).
- **Unlock / data layer** — decrypts the blob and enables derivation. **Always requires the seed.** No auth path can substitute for it; the server never holds the key.

State machine in the client: `LoggedOut → LoggedIn(token) → Unlocked(seed in memory)`. `Locked` (logged in, seed wiped) is distinct from `LoggedOut`. The seed lives only in a module-scoped variable, wiped on lock, never persisted.

### Zero-knowledge server

The server stores, per account: an auth record (passphrase hash + seed-derived public key + TOTP secret(s) + trusted-device tokens) and `config(version: int, blob: opaque)`. It never sees service names, types, notes, vault secrets, or the seed.

### Data flow

`login (either path) → GET /config → {version, blob} → unlock (enter seed) → decrypt blob → render services → derive passwords on demand (all in-browser) → edits re-encrypt the whole blob → PUT /config {expected_version, blob}`. On `409` version conflict: pull current, **merge service lists by entry UUID** (the data model has stable `id`s), re-encrypt, retry.

## Crypto module (`web/client/src/crypto/`)

Pure WebCrypto plus two tiny hand-rolled helpers. All constants must reproduce the Python engine exactly; the 103 test vectors are the gate.

| Unit | Specification |
|---|---|
| `derive.ts` | `deriveRaw(seed, account, service)` → 40 bytes via `subtle.deriveBits`, **PBKDF2-HMAC-SHA1**, **42,000** iterations, salt = `UTF8(service)`, password = `UTF8(seed + account)`. `generate(seed, account, service, type)` maps those bytes per type, reproducing `engine.py` byte-for-byte. |
| `base58.ts` | Hand-rolled (~30 LOC). Alphabet `123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz`. Drives `base58`/`base58long` and the `alnum`/`alnumlong` validity loop (output must contain ≥1 upper, ≥1 lower, ≥1 digit). |
| `wordlist.ts` + `english.txt` | BIP-39 2048-word list copied verbatim from `linux/dgp/data/english.txt`, bundled as an asset. Drives `xkcd` (4 words) / `xkcdlong` (6 words), each capitalized. |
| `vault.ts` | AES-256-GCM. Key = first 32 bytes of `PBKDF2-SHA1(seed+account, salt=name, 42,000, 40)`. Blob = `base64(iv₁₂ ‖ ciphertext+tag)`. Renaming an entry changes `name` → changes the key → the old secret is undecryptable (UI must warn before renaming a vault entry with a stored secret). |
| `exportcrypto.ts` | PIN-encrypted export blob, **wire-compatible with Android/Linux**: `PBKDF2-HMAC-SHA256(PIN, salt="dgp-export-v1", 600,000, 32)`, AES-256-GCM, `base64(iv₁₂ ‖ ct)`, JSON payload of services. |
| `configblob.ts` | The new sync format: `PBKDF2-HMAC-SHA256(seed+account, salt="dgp-config-v1", 600,000, 32)`, AES-256-GCM, `base64(iv₁₂ ‖ ct)`, plaintext = services JSON array. |
| `authkey.ts` | Seed-derived signing keypair: private key = `KDF(seed+account, salt="dgp-auth-v1")`, deterministically imported into WebCrypto (Ed25519 if available, else P-256; the deterministic-private-key import wrinkle is resolved in the crypto-port task — wrap raw scalar in a fixed PKCS8/JWK template or use one small audited lib). Only the **public key** ever leaves the client. |
| `flag.ts` | Pride-flag fingerprint (two BIP-39 words + flag index from seed+account) + nonce mining for "set as my flag", ported from the committed engine and `2026-06-09-pride-flag-identity-fingerprint-design.md`. Mining runs in a **Web Worker**. |

**Exact constants** (verbatim, for the port): derivation = PBKDF2-HMAC-SHA1 / 42,000 / 40-byte output / salt=service / password=seed+account. Export = PBKDF2-HMAC-SHA256 / 600,000 / salt `dgp-export-v1`. Config = PBKDF2-HMAC-SHA256 / 600,000 / salt `dgp-config-v1`. Auth = salt `dgp-auth-v1`. base58 alphabet as above. BIP-39 = 2048 words.

WebCrypto natively supports PBKDF2-SHA1 and AES-GCM, so no general-purpose crypto library is required.

## Authentication

Two login paths, OR'd at the top. Both grant only the **sync layer**; the seed is still required to unlock data.

| Path | Factors | Result | Steps |
|---|---|---|---|
| **Seed path** | Sign a server-issued nonce with the seed-derived keypair | Sync access **and** the user can unlock (they hold the seed) | One step (collapses login+unlock) |
| **2FA path** | Account passphrase (knowledge) **+** TOTP code from a registered authenticator (possession, RFC 6238) | Sync access only; seed still required to read data | Two steps |

- **Registration / account setup:** choose passphrase → client generates the seed-derived public key and registers it → enroll first authenticator (server generates TOTP secret → client renders the `otpauth://` QR → user scans → confirms a code → server stores the secret).
- **Trusted devices ("server remembers clients"):** after either path succeeds, the server mints a **revocable device token**; subsequent opens skip the challenge. Managed/revoked in Settings.
- **New-device enrollment / recovery:** authenticate via passphrase + TOTP, then register *this* device's seed-derived public key so the fast seed-path works here afterward.
- **Not chosen:** escrowing the seed server-side to let 2FA grant data access (would forfeit zero-knowledge).

## Sync server + API (`web/server/`)

FastAPI + SQLite, single-tenant, ~one module plus auth helpers.

**Schema (sketch):**
- `account(id, username, passphrase_hash, passphrase_salt, auth_pubkey)`
- `totp_secret(id, account_id, label, secret, confirmed)`
- `device_token(id, account_id, token_hash, label, created_at, last_seen)`
- `config(account_id, version INT, blob BLOB, updated_at)`

**Endpoints:**
- `POST /auth/register` — username + passphrase + seed-derived public key (gated; single-tenant).
- `POST /auth/login/challenge` → nonce; `POST /auth/login/seed` — signed nonce → bearer token.
- `POST /auth/login/password` — passphrase + TOTP code → bearer token.
- `POST /auth/totp/enroll` / `POST /auth/totp/confirm`.
- `GET /devices` / `DELETE /devices/{id}` — trusted-device management.
- `GET /config` → `{version, blob}`.
- `PUT /config {expected_version, blob}` → `200 {version+1}` on match, else `409 {current_version, blob}`.

**Sync semantics:** optimistic concurrency via the version counter; client merges on `409` by entry UUID, re-encrypts, retries. Server never decrypts. Bearer-token auth works for all three future clients.

**Contract (`web/shared/`):** OpenAPI document + the `configblob` and `export` byte-format specs, as the authoritative target for the later Android/Linux sync clients.

## Frontend app (`web/client/`)

**Primitives (hand-rolled, minimal-deps):** a ~50-LOC observable store (pub/sub), a hash-based router, and `render→DOM` component functions.

**Screens:**

| Group | Screens |
|---|---|
| Auth | Login (seed entry *or* passphrase + TOTP), Register / account setup, New-device enrollment / recovery |
| Core | Unlock (seed entry, QR scan, flag chip), Services list (search, archive filter, drag-reorder, per-type strips, header flag chip, +add), Reveal (hold-to-show, copy, auto-clear countdown, vault decrypt) |
| Manage | Edit entry (type grid, live derive preview, vault secret, rename warning), Settings (identity, theme auto/light/dark, trusted-device management, test vectors, danger zone), Export/Import (PIN dialog ↔ encrypted blob), Flag gallery (set-as-my-flag with worker mining), Test-vectors view (runs all 103 live) |

**Tricky UX, handled explicitly:**
- **Hold-to-reveal + auto-clear countdown:** pointer events + timer; copy via the async Clipboard API. Browser limitation: the clipboard can only be overwritten while the page is open, so "auto-clear after N seconds" is best-effort in-page, not an OS-level guarantee. Asserted by an in-page test, not papered over with a caveat.
- **Drag-reorder:** manual ordering persisted in the config (never alphabetical).
- **Flag nonce mining:** Web Worker; UI never blocks.
- **QR:** *scan* the seed via `BarcodeDetector` + `getUserMedia` (manual-entry fallback); *generate* the TOTP enrollment QR (`otpauth://`) via a tiny hand-rolled encoder or one small audited lib (crypto-adjacent, acceptable).

**Theme:** Editorial light/dark tokens from `app/.../ui/theme/Colors.kt` as the brand anchor.

## Testing

Per the user's "tests over caveats" preference:

- **Crypto gate:** all 103 vectors from `linux/dgp/testvectors.py` ported to a Vitest suite — `generate()` must match every one. Export gets a **cross-language** fixture: TS decrypts a Python-produced blob, and Python decrypts a TS-produced blob.
- **Auth:** keypair sign/verify; TOTP against RFC-6238 published test vectors; full challenge-response and `409`→merge-by-UUID→retry unit tests.
- **Server:** pytest + httpx over every endpoint (both login paths, config get/put, version conflict, device tokens, TOTP enroll/confirm).
- **UI:** Vitest + happy-dom for the store and components; Playwright smoke for `login → unlock → reveal`.
- Wire into the repo's existing `run_tests.sh` convention.

## Deployment

FastAPI serves both the JSON API and the built static client from the `.108` box (single origin → simplest CORS story), replacing the legacy Flask app.

## Error handling

- **Wrong seed:** decryption of the config blob (AES-GCM) fails authentication → surface "wrong seed or account"; the flag fingerprint chip is the early human-visible signal (mismatch = wrong seed/account).
- **Version conflict (`409`):** automatic pull-merge-retry; only surface to the user if merge is impossible.
- **TOTP / passphrase failure:** generic auth-failure message, rate-limited server-side.
- **Vault decrypt failure** (e.g., after a rename): explicit "secret unrecoverable" state.
- **Network/server unreachable:** the app remains usable read-only on the in-memory decrypted config; writes queue and sync on reconnect.

## Acceptance criteria

1. All 103 derivation test vectors pass in the TS suite.
2. An export blob produced by the web app imports cleanly in the Python CLI, and vice versa.
3. Both login paths work; TOTP enrollment + verification round-trips against a standard authenticator app.
4. Two browsers editing the same account converge correctly via the `409` merge path.
5. Every parity screen is implemented and functional against the real WebCrypto engine.
6. The seed never appears in network traffic, `localStorage`, or server storage.

## Follow-on work (explicitly deferred)

- Android (Kotlin) sync client targeting the `web/shared/` API contract.
- Linux (Python) sync client targeting the same contract.
- Revisit the login/register cards in the "DGP Web" Claude Design project to reflect the seed-proof + 2FA model.
