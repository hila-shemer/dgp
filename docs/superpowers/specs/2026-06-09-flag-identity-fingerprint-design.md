# Flag identity fingerprint — design

**Status:** approved (brainstorm), pending implementation plan
**Date:** 2026-06-09
**Scope:** Android app only. Not part of the Android↔Linux password-compat contract; `linux/` is untouched.

## Problem

The user wants to confirm, at a glance, that the active identity — `seed + account` — is
the one they intend, before trusting any generated password. Today nothing visually
distinguishes a correct identity from one with a mistyped seed or account.

## Solution overview

Show a small **flag chip + two-word label** derived deterministically from
`seed + account`. The user's reference identity is vanity-mined to land on a fixed
**reference flag** (index 0 of the gallery); any other identity lands on a *different*
flag and shows a different word. The flag is the instant recognition signal; the word is
a high-entropy backstop for the rare case where a wrong identity collides on the same flag.

This is a *recognition* aid, not an authentication mechanism. The app does not store a
secret verifier — it stores only a public nonce (see §4).

## 1. Flag gallery (10)

A fixed, ordered list of ten striped flags; index 0 is the reference flag (the vanity
target). All render as clean horizontal colored stripes and are mutually distinguishable
at chip size.

## 2. Derivation (new, domain-separated)

The password-generation path in `DgpEngine` is **not modified** (invariant #1). A new,
independent derivation is added:

- `fpBytes = PBKDF2-HMAC-SHA1(password = seed + account, salt = "dgp-flag-fp:v1",
  iterations = 42000, length = 32 bytes)`.
  - The distinct salt domain-separates the fingerprint from every password/`aeskey`
    derivation, so the on-screen fingerprint cannot be reversed into password material
    and vice-versa.
  - This is the only expensive step. Cache `fpBytes` in `MainActivity` state keyed by the
    current `(seed, account)`; recompute only when either changes.
- `word = two BIP-39 words` indexed from `fpBytes` (reuses the wordlist already loaded
  from assets; ~2048² ≈ 22 bits). **Independent of the nonce**, so the correct identity
  always shows the same word.
- `flagIndex = SHA-256(fpBytes ‖ nonceBytes)[0] mod 10` — a **fast** hash, so mining the
  nonce is instant once `fpBytes` is in hand.

`REFERENCE_INDEX = 0`.

## 3. Vanity mining

The "set as my flag" action loops `nonce = 0, 1, 2, …` recomputing `flagIndex` until it
equals `REFERENCE_INDEX` (~10 iterations on average, all fast SHA-256 over the
already-derived `fpBytes`). The resulting nonce is stored.

## 4. What is stored (and why it's safe)

- A single integer **`flag_nonce`** is stored as its **own local SharedPreferences key**,
  separate from the encrypted config.
- **It is NOT added to the export/import payload.** That payload is a plain JSON *array* of
  services (`serializeServices`), and its plaintext shape is wire-compatible with the Linux
  port's `ConfigCrypto` (a hard invariant). Wrapping it to add a field would break Linux
  import, so the nonce stays out of it. Consequence: after a reinstall, or on a second
  device, the user re-taps "set as my flag" once to re-mine the nonce (cheap — ~10 fast
  iterations). The flag is per-device local state, not synced.
- **The nonce is non-secret.** It only biases which flag the mapping selects. Recovering
  `seed + account` from the nonce still requires breaking the slow KDF, so storing it (even
  in plaintext) adds no attack surface. Invariant #3 ("nothing *secret* is stored") is
  preserved — the nonce is not secret.
- `flag_nonce` is cleared on **config reset** and on **seed change** (the reference identity
  no longer holds), which makes the "set as my flag" affordance reappear for re-registration.

## 5. UX & placement

- **Services-list header:** the account-letter avatar (`ServicesScreen.kt:139`) is
  replaced/augmented by the flag chip + word, visible the whole time the app is unlocked.
  The header tap keeps its existing behaviour (opens the account dialog); the header chip is
  display-only. To keep the 28 dp header element legible, it shows the **flag swatch** (the
  full flag + word chip lives in the account dialog).
- **Set/Change Account dialog:** the chip (flag + word) renders live as the account is typed
  — debounced and computed off the main thread, since each render is a full PBKDF2. This is
  also where the **"set as my flag" / "✓ this is correct"** action lives (the user chose this
  placement, not Settings). The action goes through a confirm step to avoid blessing a typo.
- **Before a reference is mined** (`flag_nonce` unset): show the raw `nonce = 0` flag for the
  current identity, plus the "set as my flag" affordance. After mining, the correct identity
  shows the reference flag.

## 6. Edge cases

- **Seed or account legitimately changes** → the flag is no longer the reference flag (it is
  now a genuinely different identity). The user re-taps "set as my flag" if the new identity
  should become the reference. This is intended behaviour — the changed flag tells them the
  identity changed.
- **~1-in-10 flag collision** for a wrong identity → the two-word label still differs and
  catches it.

## 7. Testing

- `DgpEngineTest` (JVM) additions:
  - `fpBytes`/word/flagIndex are deterministic for fixed inputs (lock down with concrete
    expected values).
  - Vanity mining terminates and the resulting nonce maps the reference identity to
    `REFERENCE_INDEX`.
  - Domain separation: fingerprint output differs from every password-type output for the
    same `(seed, name, secret)`.
- Manual device check: device is already connected. **Signing constraint:** the device runs
  the Play early-access *release* build (`io.github.hilashemer.dgp`), so a debug APK cannot
  update it in place (signature mismatch), and a local release APK can only update in place
  if its signature matches (i.e. Play App Signing is off and the local keystore is the app
  signing key). **Chosen path:** in-place local release update — `./gradlew assembleRelease`
  (signing creds confirmed present in `local.properties`) then `adb install -r` over the
  installed app, preserving the real config so the fingerprint can be exercised against the
  real identity. If the install is rejected for signature mismatch (Play App Signing on),
  fall back to a side-by-side debug variant (`applicationIdSuffix ".debug"`) and import the
  config. Either way: confirm the chip renders on the list header and live in the account
  dialog, mine the nonce, and verify a mistyped account changes the flag/word. Shipping to
  the early-access track itself is a separate Play Console release upload, out of local scope.

## 8. Files expected to change

- `app/src/main/java/com/dgp/engine/DgpEngine.kt` — fingerprint derivation + mining helper.
- `app/src/main/java/com/dgp/ui/components/` — new flag-chip composable + the 10-flag
  gallery definition.
- `app/src/main/java/com/dgp/MainActivity.kt` — cache `fpBytes`, hold `flagNonce`, wire the
  "set as my flag" action, persist/restore/export the nonce.
- `app/src/main/java/com/dgp/ui/ServicesScreen.kt` — render the chip in the header.
- The account Set/Change dialog — live chip while typing.
- Config (de)serialization + export/import — carry `flagNonce`.

## Invariants preserved

- #1 password PBKDF2 params unchanged (fingerprint is a separate derivation).
- #2 `TestVectors.kt` / `DgpEngineTest` stay green; new tests added alongside.
- #3 nothing *secret* stored — `flagNonce` is non-secret.
- #4 seed lives in memory only while unlocked — `fpBytes` cache clears on lock with it.
