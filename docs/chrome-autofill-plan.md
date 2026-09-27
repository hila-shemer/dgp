# Chrome autofill, Android autofill, Import from Chrome: implementation plan

The review copy with Hila's answers is the Claude Doc
"dgp: Chrome, autofill and import design" (claude.ai artifact 7b4958e9). This file is
what the code is built from. Where it and the doc disagree, her comments in the doc win.

## Answers this plan builds on (2026-09-27)

- Autopaste = Android autofill. No keyboard (IME), no Credential Manager provider.
- The phone should stay unlocked most of the time. The unlocked seed lives in a
  process-wide session, so a fill reuses it. It still clears on Lock, on reboot, and
  whenever Android kills the process.
- Entries get an explicit `sites` list, because service names are arbitrary.
- Linux extension: unpacked, developer mode, fixed key.
- Chrome import: every Chrome password DGP did not generate becomes a `vault` entry.
- The account is one global value, and fills use it.
- Unanswered, so the conservative default: the Chrome username goes into `comment`,
  and the web page gets no Chrome import (it only keeps `sites` on round-trip).

## Wire format: `sites`

An optional JSON array of strings on each service object, serialized right after
`tags` and omitted when empty:

    {"id": "...", "name": "gh", "type": "alnum", ..., "tags": [...], "sites": ["github.com", "com.github.android"]}

Each element is a web host (`github.com`, `accounts.example.co.uk`) or an Android
package name (`com.github.android`). Values are stored lowercased and trimmed. Every
parser (Android, Linux, web) must keep the field on a parse/serialize round trip.
Older builds drop it, and that is the only compatibility cost.

## Matching

Both engines implement the same rules. `linux/tests/fixtures/sitematch-cases.json` is
the shared case table, and both test suites run it.

`normalize_host(h)`: lowercase, strip scheme and path when given a URL, strip a port,
strip a trailing dot, strip one leading `www.`.

`registrable(h)`: the last two labels, or the last three when the TLD is two letters
and the second-to-last label is one of `co com net org ac gov edu ltd plc`
(`a.b.co.il` gives `b.co.il`). There is no public-suffix list, on purpose.

A request is either `web(host)` or `app(package)`.

1. **Site match.** For `web(H)`, with H normalized, an entry matches when some site `s`
   in its `sites` satisfies `H == s` or `H` ends with `"." + s`. For `app(P)`, it
   matches when some `s == P`.
2. **Name fallback.** Only entries with an EMPTY `sites` list take part. Name `n` is
   lowercased with spaces removed. For `web(H)`, it matches only when
   `n == registrable(H)`. A bare first label (`google`) would be offered on
   `google.evil`. Apps get no name fallback, because any app can put a `google`
   label in its package name. (Changed after the security review, 2026-09-27.)
3. Archived entries never match.
4. The result lists site matches first, then name matches, each in list order.

The fill UI always offers "search all", so a miss costs one extra tap.

## Android (`app/`)

- **Who is asking.** A fill request's `webDomain` is trusted only when the requesting
  package is a known browser (`FillPolicy.BROWSERS`). Any other app can set any
  webDomain in its own view tree, so its request is matched as `app(package)`. A
  browser request with no webDomain, or on plain `http` (localhost aside), gets no
  suggestions. The Linux popup and native host likewise refuse non-https origins.
  Below API 28 nothing is offered: webScheme doesn't exist there, and the
  requester's activityComponent may be forged.

- `DgpService.sites: List<String>`, handled in parse and serialize, plus an editable
  "sites" field in EditEntryScreen (comma- or newline-separated).
- `com.dgp.engine.SiteMatcher`: the matching rules above, pure Kotlin, JVM-tested.
- `com.dgp.session.DgpSession`: a process-wide object holding `seed`, `account` and
  the decrypted service list. MainActivity writes it on unlock and clears it on Lock
  and on the existing clear paths. Nothing persists it.
- `com.dgp.autofill.DgpAutofillService`: parses the structure for password (and
  username) fields plus `webDomain` or package name.
  - When `DgpSession` is unlocked: one dataset per match, each needing the picker's
    authentication. The presentation shows the service name only, never the password.
  - When locked: a single "Unlock DGP" dataset.
  - Both go through `AutofillPickerActivity` (FLAG_SECURE). It unlocks with the
    fingerprint if needed, lists the matches plus a search box, derives the chosen
    password (vault entries decrypt), and returns the Dataset through
    `EXTRA_AUTHENTICATION_RESULT`. No SaveInfo, so DGP never offers to save.
- Settings screen: an "Autofill service" row that opens
  `Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE`, plus "Import from Chrome (CSV)".
- The file picker uses the existing `startActivityForResult` pattern with a new small
  request code. Do not use `rememberLauncherForActivityResult`.

## Linux (`linux/`)

- `DgpService.sites`, `dgp/sitematch.py`, and a `--site` option on the add/edit CLI.
- `dgp native-host`: Chrome native messaging (4-byte native-endian length + JSON on
  stdio). Ops:
  - `{"op":"match","origin":URL}` gives `{"ok":true,"services":[{"id","name","type"}]}`.
  - `{"op":"list"}` returns the same shape for every unarchived entry.
  - `{"op":"get","id":ID}` gives `{"ok":true,"password":P}`.
  - Errors are `{"ok":false,"error":"..."}` and never contain a password. The host
    writes nothing to stderr.
- `dgp chrome-install [--extension-id ID]`: writes the host manifest
  `io.github.hilashemer.dgp.json` for google-chrome and chromium, pointing at a
  launcher script, with `allowed_origins` = the fixed extension ID.
- `linux/chrome-extension/`: a Manifest V3 extension with a fixed `key` and
  permissions `nativeMessaging activeTab scripting`. The popup matches the active tab's
  origin, shows the matches plus search, and on click gets the password and fills the
  top frame's focused (or first) password field. Before filling, it re-checks that the
  tab's origin has not changed.
- `dgp import-chrome FILE [--dry-run]` (see below).

## Import from Chrome (both platforms)

The input is the Google Password Manager CSV. Columns are read by header name, and only
`url` is required (`name`, `username`, `password` and `note` are optional). For each row:

- The site is `normalize_host(url)`. `android://<hash>@<package>/` rows give the package.
- If ANY existing entry's derived password (with the current seed and account) equals
  the CSV password (entries matching the site are tried first), the site is added to
  that entry's `sites` if missing. The password was generated by DGP,
  so no vault entry is made.
- If an existing vault entry with this site in `sites` decrypts to the same password,
  the row is skipped, which makes a re-run of the import harmless.
- Otherwise a new `vault` entry is created:
  - name = the site, or `site (username)` when that name is already taken;
  - `sites` = [site], `comment` = username, `tags` = ["chrome-import"];
  - `encryptedSecret` = vault-encrypt(password, deriveAesKey(seed, name, account)).
- Rows with an empty password are skipped.
- The preview reports how many rows were already generated, how many vault entries are
  new, and how many rows were skipped. The CSV itself is never copied or logged.

## Invariants kept

The PBKDF2 parameters and test vectors are unchanged. No derived password, seed or CSV
password is logged anywhere. The seed stays in the process that already holds it: the
DGP app process on Android, the `dgp` Python process on Linux.
