# Agentic DGP & Key Derivation — Design

- **Date:** 2026-06-17
- **Status:** Approved (brainstorming), pending implementation plan
- **Scope:** Linux CLI first (`linux/dgp/`); one engine-level primitive mirrored to Android for parity. No steganography, no Shamir splitting, no generic public/private-pair tooling in this spec — those are explicitly deferred.

## 1. Motivation & core concept

DGP derives everything from `seed + account` (key material) and a service name (salt) via
PBKDF2-HMAC-SHA1. This spec extends that in two directions:

1. **More output types** — reproducible SSH identities (client *and* host) and real,
   spendable BTC/Ethereum wallet keys, on top of the existing `btc-key` / `ssh` / `prng`.
2. **Subaccounts as capability tokens** — a one-way-derived, seed-grade secret you can hand
   to an AI subagent so it runs its *own* DGP, boxed into a disjoint derivation universe,
   with no path back to your master seed.

The unifying idea is **"seeds all the way down."** Your master seed is a long word phrase
(an `xkcdlonglong`). A subaccount cap-token is *the same object* — a long word phrase — only
sourced from `PBKDF2(seed, account, reserved-salt:label)` instead of from a CSPRNG. "Generate
a fresh master seed" and "mint a subaccount for an agent" share one renderer and are
indistinguishable in form. An agent can recursively mint its own subaccounts.

### Threat model for the agentic case

- An agent receives a **cap-token**, never the master seed. PBKDF2 is one-way, so the agent
  cannot recover the master seed or derive sibling subaccounts.
- A leaked cap-token compromises exactly **one** subaccount, never the whole identity. Handing
  an agent a cap-token is strictly safer than handing it the master seed.
- The agent runs inside a **Docker snapshot** under `~/proj/dgp`; egress is **reviewed
  patches/commits only**. So even though the cap-token sits in plaintext in the container
  (same filesystem-permission posture as the existing Linux port), the agent cannot exfiltrate
  it. We therefore do **not** build a capability-narrowing wrapper to hide the seed from the
  agent — it would add complexity for no benefit given patch-only egress + a scoped token.
- The master seed is **never read into** the agent's config dir.

## 2. Engine primitive: cap-token + `xkcdlonglong` (Android + Linux parity)

This is the only change to the shared engine, so it must land on both `DgpEngine.kt` and
`linux/dgp/engine.py` with shared test vectors (CLAUDE.md invariant: don't change the engine
on either side without re-greening both suites). It is **purely additive and
domain-separated**, so invariant #1 (password params) and the existing test vectors are
untouched — passwords still salt with the service name; cap-tokens salt with a reserved prefix.

### 2.1 Derivation

```
salt          = "dgp-subaccount:v1:" + label        # reserved namespace, mirrors dgp-flag-fp:v1
key material  = seed + account                       # current identity
bytes         = PBKDF2-HMAC-SHA1(key material, salt, iterations=42000, dklen=40)
cap_token     = render_xkcdlonglong(bytes)           # 24 words, seed-grade
```

`render_xkcdlonglong` extends the existing `xkcd`/`xkcdlong` family (BIP-39 English wordlist,
2048 words, capitalize-first, concatenate — `CorrectHorseBatteryStaple` style) to **exactly 24
words** (~264 bits, matching a real seed's shape).

To be byte-for-byte reproducible across Kotlin and Python, the word selection is the existing
`getXkcd` algorithm (repeated `divmod(n, 2048)`, low-order word first), but **always emits
exactly 24 words**: if the integer is exhausted early, remaining words use `wordList[0]`. 40
bytes (320 bits) comfortably exceeds the 264 bits needed, so early exhaustion is a
defensive edge case only.

### 2.2 New API surface

- Linux `engine.py`: `derive_subaccount_seed(seed, account, label) -> str`.
- Android `DgpEngine.kt`: `deriveSubaccountSeed(seed, account, label): String`.
- A shared, language-neutral **test-vector fixture** (JSON: `seed, account, label ->
  cap_token`) consumed by `DgpEngineTest` and `pytest`. Add cases to `TestVectors.kt` /
  `testvectors.py` covering empty account, empty label, and long labels.

### 2.3 Optional symmetry (low cost, recommended)

A `dgp seed new` command that renders 24 words from a CSPRNG using the **same** renderer — so
fresh master seeds and cap-tokens are produced by one code path.

### 2.4 Collision note

A service literally named `dgp-subaccount:v1:<label>` would, if its password were derived,
collide with a cap-token's bytes — the same accepted, documented risk class as the existing
`dgp-flag-fp:v1` fingerprint salt. We accept it and note it; labels are used verbatim in the
salt.

## 3. Agentic DGP provisioning (Linux CLI)

The CLI already resolves config via `DGP_CONFIG_DIR` (`store.py`), so "a copy that can be
derive-seeded" is a second config dir, **not** a forked codebase.

### 3.1 Commands (new `subaccount` subcommand group)

- `dgp subaccount mint <label> [--qr]` — print the cap-token for `<label>` under the current
  identity. `--qr` renders a terminal QR for transfer.
- `dgp subaccount provision <label> --home <dir> [--account <acct>] [--services name:type,...]`
  — write a ready-to-use agent config dir:
  - `<dir>/seed`   = cap-token (mode 0600)
  - `<dir>/account` = `<acct>` or empty (default empty)
  - `<dir>/services.json` = the delegated catalog (see §5); empty unless `--services` given.

  The master seed is **never** read into `<dir>`.

### 3.2 Agent usage

The agent runs the unmodified CLI against its own dir:

```
DGP_CONFIG_DIR=<dir> dgp gen <service> --type alnumlong
DGP_CONFIG_DIR=<dir> dgp ssh <service>
DGP_CONFIG_DIR=<dir> dgp wallet eth-address
```

Full derivation powers, scoped to its sub-universe, no path back to the master.

### 3.3 Sandbox recipe

A short doc (`docs/agentic-dgp.md`, written during implementation) showing how to place a
provisioned `<dir>` into a Docker snapshot under `~/proj/dgp`, with the master config dir left
on the host. References the existing container-sandbox pattern.

## 4. Native identity keys (Linux CLI)

Per-service direct keys (no HD tree) — matches the existing `dgp ssh` approach and the
reproducible "this is me" goal. Works identically for an agent under its cap-token (the agent
gets its *own* reproducible identity).

- **SSH client key** — `dgp ssh <service>`. Already implemented; keep as-is. Salt = service
  name.
- **SSH host key** — `dgp ssh --host <hostname>`: derive Ed25519 from a namespaced salt
  `ssh-host:<hostname>` (so a host key and a client key for the same string never collide),
  serialize as an OpenSSH key pair (the existing `serialize_openssh_private/public` already
  produce the right format for `ssh_host_ed25519_key` + `.pub`). Reproducible host identity
  from seed + hostname: re-flash a box, re-derive the same host key, no client SSH warnings.

## 5. HD money wallet (Linux CLI) — heaviest piece, phased last

A subaccount (or the master) → a BIP-39 mnemonic sub-seed → BIP-32 master `xprv`/`xpub` →
standard BIP-44-family paths, so everything round-trips into Metamask / Electrum / Ledger.
This is the literal "wallet master keys."

### 5.1 Bitcoin

Two single-sig address types (legacy `1…` P2PKH is **out** per the latest decision):

| Type | Looks like | Purpose path | Encoding |
|---|---|---|---|
| P2SH-P2WPKH (wrapped segwit) | `3…` | `m/49'/0'/0'/{0,1}/i` | `base58check(0x05 ‖ hash160(0x0014 ‖ hash160(pub)))` |
| P2WPKH (native segwit) | `bc1…` | `m/84'/0'/0'/{0,1}/i` | bech32 (default) |

`{0,1}` = receive / change chains. All primitives (`hash160`, `_base58check_encode`, bech32)
already exist in `btc.py`; P2SH-P2WPKH only adds the redeem-script wrap.

- `dgp wallet btc-address --type {p2sh-segwit,bech32} [--change] [--index i]` (default
  `bech32`, index 0, receive chain).
- `dgp wallet xpub [--type ...]` — account-level extended pubkey for watch-only. Default
  `xpub` version bytes; note that `ypub` (BIP-49) / `zpub` (BIP-84) are the same key with
  different version bytes, emitted to match `--type` when requested.
- `dgp wallet mnemonic` — the standard BIP-39 phrase that restores the whole wallet in any
  standard wallet.
- `dgp wallet xprv` — the BIP-32 master private extended key.

Requires BIP-32 CKD (secp256k1 scalar add via `coincurve`, already a dependency) and a
standards-correct BIP-39 mnemonic (entropy + SHA-256 checksum + PBKDF2-HMAC-SHA512 seed
stretch — distinct from DGP's own xkcd encoding; the existing `derive_bip39_mnemonic` is
DGP-custom and is **not** reused here).

### 5.2 Ethereum

- Path `m/44'/60'/0'/0/i` → uncompressed secp256k1 pubkey → `keccak256(pubkey[1:])[12:]` →
  `0x…` address, with **EIP-55 mixed-case checksum**.
- **ERC-20 aware** = the single `0x…` address receives USDC and every other token; we present
  the one address and note that tokens live at the same address (no per-token derivation).
- `dgp wallet eth-address [--index i]`, `dgp wallet eth-key [--index i]` (private key, for
  import).

### 5.3 Keccak-256

Ethereum needs **Keccak-256**, *not* NIST `sha3_256` (different padding — Python's
`hashlib.sha3_256` gives wrong results). Per the minimal-deps preference, **vendor a compact
(~30-line) Keccak-256** in `linux/dgp/keccak.py` rather than add `pycryptodome` / `eth-hash`.
Test against known Ethereum address vectors.

## 6. Metadata model (catalog, never private keys)

Extend `DgpService` / `services.json` with derivation-descriptor entry types so a config dir
(human's or agent's) is a catalog of *what to derive*, plus cached public material for display
and watch-only — never secrets (invariant #3).

- New entry types: `sshkey` (role: client/host, comment), `wallet` (chain, address-type,
  account/index/chain path), `identity`.
- Each entry stores: `name`, `type`, params (path / index / chain / hostname), and an
  **optional cached public output** (pubkey / address / xpub).
- Private keys and addresses are **re-derived on demand** from the (cap-token) seed; cached
  public values exist only so `dgp config list` can show a catalog without re-deriving.
- The provisioned agent catalog (§3.1) is exactly a `services.json` of these entries with no
  secrets.

## 7. Sequencing

1. **Engine primitive** — `derive_subaccount_seed` + `xkcdlonglong` + shared parity vectors.
   Land on Linux now; port to `DgpEngine.kt` alongside or immediately after (small: one
   function + the renderer + adopting the shared vectors) **before any Android surface uses
   subaccounts**, to honor the parity invariant.
2. **Agentic provisioning MVP** — `subaccount mint`/`provision`, metadata catalog types,
   threat-model + sandbox doc.
3. **SSH host keys** — small extension to `ssh.py`.
4. **HD wallet** — BTC (`3…` + `bc1…`) + ETH (vendored Keccak-256). Largest; may warrant its
   own sub-spec at implementation time.

## 8. Out of scope (deferred to future specs)

- Steganography (hiding encrypted metadata in a carrier).
- Shamir secret-splitting of the seed.
- Generic public/private-pair tooling beyond SSH/wallet.
- Android UI for any of the new surfaces (engine primitive parity excepted).
- Direct import of the new entry types into the Android app.

## 9. Risks & open questions

- **BTC address types** — settled as `3…` + `bc1…` (legacy `1…` dropped). The earlier
  "all three" selection was superseded; easy to re-add P2PKH (`m/44'`, `base58check(0x00 ‖
  hash160(pub))`) if wanted.
- **Android parity timing** — the engine primitive must reach Android before any Android
  feature depends on subaccounts; until then there is a parity gap for the *new* primitive
  only (existing engine unchanged).
- **`xpub` flavor** — defaulting to `xpub`; `ypub`/`zpub` emitted on request. Confirm this is
  enough for the target wallets.
- **Cap-token length** — fixed at 24 words; revisit only if transcription (vs. copy/QR)
  becomes a real workflow.

## 10. Test strategy

- Shared cap-token vectors green on both `DgpEngineTest` (JVM) and `pytest`.
- Keccak-256 vectors + known Ethereum address vectors.
- BIP-32/39/44/49/84 derivation checked against published test vectors (BIP-39 mnemonic →
  address) for at least one path per chain/type.
- `run_tests.sh` stays green end to end.
