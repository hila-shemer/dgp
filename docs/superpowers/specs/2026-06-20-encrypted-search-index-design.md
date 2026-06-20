# Encrypted search index — design

Status: approved design, pre-implementation.
Date: 2026-06-20.

## Problem

Search today (`ServicesScreen.kt`) matches metadata only - `name`, `comment`, `tags`.
You can't find an entry by what it actually produces: a deterministic password, or a
vault entry's stored secret. The headline use case is recalling a vault secret by its
content ("which entry holds that OTP seed / legacy password"), with reverse-lookup of a
generated password as a bonus.

The reason it isn't already there: deriving a value costs a PBKDF2-HMAC-SHA1 pass at
42,000 iterations, and that's true for both paths - generated passwords via
`engine.generate`, and vault secrets via `engine.deriveAesKey` before the AES-GCM
decrypt. At ~80 entries, deriving everything on each keystroke, or even once per search,
is multi-second. So content search needs a cache, and the cache has to be encrypted at
rest because it now holds derived secrets.

## What this changes about the threat model

Invariant #3 currently reads: generated passwords are never stored, always re-derived on
demand. This design amends it:

> Generated password **strings** (never the raw 40-byte PBKDF2 key) may be cached in the
> seed-encrypted, lock-cleared `pw_index` search index. The index is wiped from memory on
> lock and is unreadable at rest without the seed.

Two deliberate bounds on the blast radius:

- **Strings only, never the 40-byte key.** A leaked index yields the password in its one
  stored format; it cannot be re-expanded into the other seven formats or the AES key,
  because the key material isn't there.
- **Seed-encrypted, same as the services list.** At rest the blob is ciphertext. The seed
  lives in memory only while unlocked (Invariant #4), so a decrypt failure is also how we
  detect a seed change - it invalidates for free.

Vault plaintext is a smaller delta than it looks: vault secrets are *already* persisted
(`encryptedSecret`). Generated passwords are the genuinely new at-rest artifact, and the
two bounds above are what make that acceptable.

`CLAUDE.md` Invariant #3 and the architecture notes get updated as part of the work.

## Components

One new module; everything else is a small wiring change.

- **`com.dgp.index.PasswordIndex`** (new) - owns the cache: load/decrypt, staleness check,
  persist/encrypt, per-entry key computation. It does not know about PBKDF2. It's handed a
  `derive: (DgpService) -> String` lambda - which `MainActivity` already has as
  `generateForService` - plus `ConfigCrypto` and the prefs handle. Keeps the crypto path
  and the cache decoupled, and keeps `MainActivity` from growing another responsibility.
- **`MainActivity`** - holds the in-memory `pwIndex: Map<String, String>` (id to value) and
  a build-progress count; kicks the background build after unlock; wires both into
  `ServicesScreen`. Clears the map at every point `masterSeed`/`account` are already cleared
  (lock, reboot, biometric failure - the existing teardown sites around lines 469, 747, 784).
- **`ServicesScreen`** - extends the existing `filtered` predicate to also match the value
  map, and renders the inline reveal on a value match.

## Storage format and encryption

One SharedPreferences blob, key `pw_index`, encrypted with `ConfigCrypto.encrypt(json, seed)`
- the same primitive the services list uses (PBKDF2-HMAC-SHA256, 256 iterations, AES-256-GCM,
`Base64(IV ‖ ciphertext)`):

```json
{ "version": 1,
  "accountFp": "<sha256(account)>",
  "entries": {
    "<serviceId>": { "k": "<sha256(name|type|encryptedSecret)>", "v": "<derived value or vault plaintext>" }
  } }
```

`v` is the formatted password string (or the vault plaintext), never the 40-byte key.
`k` is the per-entry staleness key. `accountFp` is the index-wide staleness key.

## Data flow and invalidation

- **Unlock.** Decrypt `pw_index`. If `accountFp` matches the current account, load `v`s into
  the in-memory map - instant. If stale or missing, a background coroutine (`Dispatchers.Default`)
  derives all values, fills the map incrementally, then persists the re-encrypted blob.
- **Per-entry staleness.** Each entry carries `k = sha256(name|type|encryptedSecret)`. On
  add/edit/delete only the touched entry is re-derived; a rename, a type change, or a vault
  secret edit flips `k` and recomputes just that one. The derive inputs are exactly
  `(seed, name, type, account)` (and the secret blob for vault), so `k` covers everything
  except seed and account, which the blob encryption and `accountFp` cover respectively.
- **Account change while unlocked.** `accountFp` mismatch triggers a full background rebuild.
- **Lock / reboot / biometric failure.** Wipe the in-memory map and cancel any running build.
  The on-disk blob persists for the next unlock - it's ciphertext without the seed.

## Search and match display

- Metadata match (name/comment/tags) is unchanged, gated at >= 1 char.
- **Value match runs on the alphanumeric projection.** Strip every non-alphanumeric character
  (spaces and punctuation alike) from both the query and the candidate value, lowercase, then
  substring-match. The gate trips when that projection reaches **5 alphanumeric characters**.
  Rationale: inline reveal paints secrets on screen, so a short query must not splatter dozens
  of values. Side effects, both wanted: spaces drop out, so a spaceless query `correcthorse`
  matches an `xkcd` value `correct horse battery`; and `hex`/`base58`/`alnum` values match
  naturally. Cost: you can't search for a literal symbol - acceptable for content recall.
- On a value match the entry shows the value inline (monospace, sensitivity-flagged,
  tap-to-copy as today), auto-hidden when the query clears.
- While the index is still building, metadata search is fully live and value search matches
  whatever's already derived; a subtle `indexing N/total` hint shows progress.
- Settings gains a **Clear search index** action that wipes the blob - cheap hygiene for a new
  at-rest artifact.

## Testing

- **JVM unit tests** (`PasswordIndexTest`): encrypt/serialize round-trip; account change
  invalidates the whole index; a rename invalidates only that entry; a partial (mid-build) map
  is searchable. Plus a predicate test: metadata match, value match gated by the 5-alphanumeric
  projection, vault-plaintext match.
- **Engine untouched.** The index calls the existing derive path - no PBKDF2 change - so
  `DgpEngineTest` and `TestVectors` stay green. This is non-negotiable per Invariant #1/#2.
- Inline-reveal UI is instrumentation-only and optional; the load-bearing logic (index +
  predicate) is all JVM-testable.

## Deliberately out of scope

- No lazy/on-demand build mode - eager-after-unlock only.
- No per-format index; one stored format per entry, matching its current `type`.
- No change to the export/import wire format. The cache is local-only and never travels with
  a config export, so the Linux port's compatibility contract is untouched.
