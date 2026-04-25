# DGP — Deterministically Generated Passwords (Linux)

A Linux port of the DGP Android password manager. Derives passwords
deterministically from **seed + account + service name** using PBKDF2-HMAC-SHA1
(42,000 iterations, 40-byte key). No password database is stored.

## Install

```bash
cd /path/to/dgp/linux
pip install -e .
# Entry points (dgp, dgp-gui) are installed to ~/.local/bin
# Ensure ~/.local/bin is on your PATH
```

For a wheel build:

```bash
pip wheel -w dist .
```

## CLI usage

```
dgp gen <service> [--type TYPE] --seed-file FILE
dgp test-vectors [--all]
dgp config list [--archived] [--json]
dgp config add <name> [--type T] [--comment C] [--pin] [--tag X] [--vault]
dgp config remove <name>
dgp config edit <name>
dgp config export [--out PATH] [--pin PIN]
dgp config import <file> [--plaintext] [--pin PIN]
dgp ssh <service> [--out PATH]
dgp btc-key <service>
dgp btc-mnemonic <service>
dgp prng <service> --bytes N [--out PATH]
```

`--type` choices: `alnum`, `alnumlong`, `hex`, `hexlong`, `base58`, `base58long`,
`xkcd`, `xkcdlong`. Vault entries are read via `dgp config edit`, not `dgp gen`.

## GUI launch

```bash
dgp-gui [--seed-file PATH] [--account-file PATH]
```

Desktop integration:

```bash
cp linux/packaging/dgp-gui.desktop ~/.local/share/applications/
```

## Config layout

| File | Purpose | Mode |
|------|---------|------|
| `~/.config/dgp/seed` | Master seed (plaintext at rest) | 0600 |
| `~/.config/dgp/account` | Default account string | 0600 |
| `~/.config/dgp/services.json` | Service list (plaintext JSON) | 0600 |

Parent directory `~/.config/dgp/` is created with mode `0700`.

## Security model

- Filesystem-permission-based; no keychain, biometric, or memory-encryption.
- Export PIN is the only secret that can leave the host (in the encrypted export blob).
- Vault entries are the only stored secrets; losing `services.json` loses those secrets.
- No alternate unlock path if the seed file is lost.
- Clipboard auto-clear is not implemented.
- Memory hygiene: `bytearray` key material can be zeroed via `ctypes.memset`; Python
  `str` objects cannot be zeroed in CPython. Document as best-effort.

## Android compatibility

- All 74 PBKDF2-based test vectors match the Android engine exactly.
- PIN-encrypted export/import round-trips with Android `ConfigCrypto` wire format.
- Java cross-language fixture (`tests/fixtures/AndroidExportFixture.java`) proves
  wire compatibility; run: `pytest tests/test_android_compat.py` (requires JDK).
- Non-ASCII seeds/accounts: behavior is pinned by one test vector; no parity with
  Android guaranteed beyond 7-bit ASCII.

## Known limitations

- English BIP-39 wordlist only (2048 words).
- No clipboard auto-clear timer.
- Wayland sensitive-clipboard hint (`application/x-kde-passwordManagerHint`) depends
  on compositor support; KDE Plasma honors it, others ignore it harmlessly.
- Non-ASCII seed/account parity with Android is not guaranteed.
- No biometric unlock, QR-code scanning, cloud sync, or Flatpak/RPM packaging.

## Manual GUI smoke checklist

Walk through to verify the GUI after install:

1. Launch `dgp-gui` — window appears with empty list and seed-unlock row.
2. Enter seed in unlock field; click **Unlock** — service list populates.
3. Click a service row — password appears masked in the right panel.
4. Click **Show** — password becomes visible; value matches `dgp gen <service>`.
5. Click **Copy** — paste elsewhere to confirm the correct password is on clipboard.
6. Type in the search box — list filters live as you type; clear to restore.
7. Drag a row to a new position; quit and relaunch — order persists in `services.json`.
8. **Settings → Export**: enter PIN, click "Export to file"; save blob.
   **Settings → Import**: clear services, import the blob with same PIN — entries match.
9. **Settings → Test Vectors**: click "Run vectors" — all 74 rows show green.
10. For a vault entry: `dgp config add vaulttest --vault` (CLI), then open GUI
    and click the entry — VaultDialog appears with the decrypted secret text.
