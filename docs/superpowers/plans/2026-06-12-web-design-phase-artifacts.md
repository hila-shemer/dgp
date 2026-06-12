# Web Design-Phase Artifacts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the "DGP Web" design-phase artifact bundle (HTML cards) in a scratch dir and push it to a new claude.ai/design design-system project via DesignSync.

**Architecture:** Static, self-contained HTML preview cards staged in `/tmp/dgp-web-design/` (never committed). Each card's first line is a `<!-- @dsCard group="…" -->` marker. Content sources: this repo's Android code (tokens, screen inventory), git history at `62985ad^` (before-captures), and the `linux/dgp` Python engine (sample outputs). Push = `create_project` → `finalize_plan` → `write_files` → `list_files` verify.

**Tech Stack:** Hand-written HTML/CSS (no build step), Python 3.11 (`linux/dgp` engine), `curl`, DesignSync tool.

**Note on testing:** These are static content artifacts — no unit tests. Each task ends with a mechanical verification step (marker present, file renders, expected strings present) instead of TDD.

**Spec:** `docs/superpowers/specs/2026-06-12-web-design-phase-artifacts-design.md`

---

## Shared conventions (read first)

- Staging dir: `/tmp/dgp-web-design/` with subdirs `brief/ flows/ tokens/ wireframes/ data/ before/`.
- **First line of every file**, exactly: `<!-- @dsCard group="GROUP" -->` where GROUP ∈ `Brief | Flows | Tokens | Wireframes | Data | Before`.
- Every card is fully self-contained: inline `<style>`, no external requests, opens standalone in a browser.
- Wireframes are **grayscale only** (`#fff/#f4f4f4/#ddd/#999/#444/#111`) — structure, not visual design. Annotations in a distinct style (dashed border, small italic) so Claude Design can tell chrome from notes.
- Card skeleton used by all wireframe files:

```html
<!-- @dsCard group="Wireframes" -->
<!doctype html>
<html><head><meta charset="utf-8">
<title>DGP Web — {SCREEN NAME} (wireframe)</title>
<style>
  body { margin:0; font:14px/1.45 system-ui, sans-serif; background:#f4f4f4; color:#111; }
  .frame { width:1100px; margin:24px auto; background:#fff; border:1px solid #999; }
  .frame.mobile { width:390px; }
  .bar { display:flex; gap:12px; align-items:center; padding:12px 16px; border-bottom:1px solid #ddd; }
  .box { border:1px solid #999; background:#fff; padding:10px 12px; }
  .ghost { background:#f4f4f4; border:1px dashed #999; color:#444; }
  .row { display:flex; gap:12px; align-items:center; padding:10px 16px; border-bottom:1px solid #eee; }
  .strip { width:6px; align-self:stretch; background:#999; }
  .pill { font-size:11px; border:1px solid #999; border-radius:999px; padding:1px 8px; color:#444; }
  .btn { border:1px solid #444; background:#eee; padding:6px 14px; }
  .btn.primary { background:#444; color:#fff; }
  .mono { font-family:ui-monospace, monospace; }
  .note { border:1px dashed #b00; color:#b00; font-style:italic; font-size:12px; padding:6px 8px; margin:8px 16px; }
  h1 { font-size:16px; margin:0; } h2 { font-size:13px; margin:0; color:#444; text-transform:uppercase; letter-spacing:.06em; }
</style></head><body>
  <!-- screen content -->
</body></html>
```

- `.note` elements are designer-facing annotations (state rules, security constraints, responsive behavior). Use them liberally.

---

### Task 1: Scaffold staging dir + sample data card

**Files:**
- Create: `/tmp/dgp-web-design/data/sample-data.html`
- Create (scratch): `/tmp/dgp-web-design/gen_samples.py`

- [ ] **Step 1: Create the staging tree**

```bash
mkdir -p /tmp/dgp-web-design/{brief,flows,tokens,wireframes,data,before}
```

- [ ] **Step 2: Generate real engine outputs with a throwaway identity**

Write `/tmp/dgp-web-design/gen_samples.py`:

```python
import sys
sys.path.insert(0, "/home/hila/proj/webdgp/linux")
from dgp.engine import generate

SEED = "demo seed for design mockups only - never a real identity"
SECRET = "demo@example.com"
for t in ["alnum", "alnumlong", "hex", "hexlong",
          "base58", "base58long", "xkcd", "xkcdlong"]:
    print(f"{t}\t{generate(SEED, 'github.com', t, SECRET)}")
```

Run: `python3 /tmp/dgp-web-design/gen_samples.py`
Expected: 8 lines, one `type<TAB>output` each (xkcd = 4 words dot/space-joined per engine, hexlong = long hex string). If import fails, run `pip install -e /home/hila/proj/webdgp/linux` first.

- [ ] **Step 3: Write `data/sample-data.html`**

`<!-- @dsCard group="Data" -->` + skeleton (group `Data`, frame width 1100px). Content:

1. **Format table** — one row per entry type: type name, the real generated output from Step 2 in `.mono`, and a charset/length note:
   - `alnum` / `alnumlong` — base58 charset window guaranteed ≥1 upper+lower+digit; short vs long
   - `hex` / `hexlong` — lowercase hex
   - `base58` / `base58long` — alphabet `123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz`
   - `xkcd` / `xkcdlong` — 4 vs 6 BIP-39 words (layout-relevant: wraps very differently from hex!)
   - `vault` — not generated; user-supplied stored secret, shown masked `••••••••` with note "decrypted client-side on reveal"
2. **Sample service list** (12 entries, use as canonical mockup content everywhere):

| name | type | note | flags |
|---|---|---|---|
| github.com | alnumlong | | |
| gmail | alnum | | |
| bank-leumi | xkcdlong | security answers in vault | |
| aws-root | hexlong | | |
| netflix | alnum | shared family account | |
| wifi-home | xkcd | | |
| luks-laptop | hexlong | | |
| btc-cold | base58long | | |
| work-vpn | alnum | rotates quarterly | |
| legacy-router | vault | ISP-assigned, can't change | |
| totp-github | vault | OTP seed | |
| myspace | alnum | | archived |

3. A `.note`: "All outputs derived from a throwaway demo seed. Identity for all mockups: account `demo@example.com`."

- [ ] **Step 4: Verify**

Run: `head -1 /tmp/dgp-web-design/data/sample-data.html && rg -c 'mono' /tmp/dgp-web-design/data/sample-data.html`
Expected: first line is the `@dsCard group="Data"` marker; count ≥ 8.

---

### Task 2: Before-captures from the live server / git history

**Files:**
- Create: `/tmp/dgp-web-design/before/current-login.html`
- Create: `/tmp/dgp-web-design/before/current-main.html`

- [ ] **Step 1: Capture login page + stylesheet**

```bash
curl -s -m 5 http://192.168.1.108:5000/login > /tmp/dgp-web-design/before/_login_raw.html
curl -s -m 5 http://192.168.1.108:5000/static/style.css > /tmp/dgp-web-design/before/_style.css
```

Fallback if unreachable: `git -C /home/hila/proj/webdgp show 62985ad^:dgp/templates/login.html` (render Jinja blocks by hand: title "Dgp", metanav with FAQ + "log in") and `git show 62985ad^:dgp/static/style.css`.

- [ ] **Step 2: Build `before/current-login.html`**

First line `<!-- @dsCard group="Before" -->`, then the captured login markup with `_style.css` inlined in a `<style>` tag and the `<link rel=stylesheet>` removed. Strip any `<script>` tags (sandboxing). Add at the bottom: `<p style="font:italic 12px sans-serif;color:#b00">BEFORE capture — live Flask app, stock Flaskr tutorial stylesheet.</p>`

- [ ] **Step 3: Build `before/current-main.html` (logged-in state, reconstructed)**

The main page requires login, so reconstruct from `git show 62985ad^:dgp/templates/show_entries.html` + `layout.html`: render the logged-in branch statically with style.css + layout's inline `<style>` inlined, all `<script>` removed, and these concrete values:
- password-display visible (`class="password-display show"`) with `password-info` = "Password for: github.com" and a `.mono` fake `mTk3v9XcQpL2RfWd8HsB4n` as `password-text`
- `<select size="10">` filled with the 12 sample services from Task 1 as `name (type)` options
- both the add-entry and gen-custom forms as-is
- same BEFORE caption as Step 2

- [ ] **Step 4: Verify**

Run: `head -1 /tmp/dgp-web-design/before/*.html && rg -c '<script' /tmp/dgp-web-design/before/current-*.html; rm -f /tmp/dgp-web-design/before/_login_raw.html /tmp/dgp-web-design/before/_style.css`
Expected: both files start with the Before marker; rg reports no `<script>` matches (exit 1).

---

### Task 3: Editorial tokens card

**Files:**
- Create: `/tmp/dgp-web-design/tokens/editorial-tokens.html`

- [ ] **Step 1: Write the card**

First line `<!-- @dsCard group="Tokens" -->`. Two columns (light / dark), each a paper-colored panel showing labeled swatches with hex values (values from `app/src/main/java/com/dgp/ui/theme/Colors.kt`, already verified):

Light: paper `#F5F3EE`, paperElev `#FFFFFF`, ink `#1C1A15`, inkMuted `#6B6659`, inkFaint `#A39D8E`, rule `#E3DFD4`, ruleStrong `#CFC9BC`, accent `#7FA650`, accentSoft `#E8F0D8`, danger `#B5412A`.
Dark: paper `#121110`, paperElev `#1C1A18`, ink `#E8E4D8`, inkMuted `#8F897C`, inkFaint `#5C574E`, rule `#2A2824`, ruleStrong `#3A3832`, accent `#B8D878`, accentSoft `#2E3A1F`, danger `#E06A50`.

Below: **type-strip row** — 9 swatches (light/dark pairs): alnum `#C4A95A`/`#D9BC63`, alnumlong `#9B8A4E`/`#C2AE63`, hex `#4A9C8B`/`#6FB8A8`, hexlong `#3E8477`/`#5AA396`, base58 `#8C6CB8`/`#B094D4`, base58long `#75579C`/`#9A7DC2`, xkcd `#5E7CC4`/`#8098D4`, xkcdlong `#4A66A8`/`#6D84BE`, vault `#B8603C`/`#D98060`. Caption: "every service row carries its type's strip color — this coding must survive the redesign."

Type roles section (text, not fonts): editorial flavor — section headings with `## ` prefix glyph, lowercase UI labels (e.g. "fixed by protocol — never editable"), monospace for all derived material.

Header note (verbatim): *"Brand anchor from the Android app's Editorial theme — evolve it for the web, don't ignore it."*

- [ ] **Step 2: Verify**

Run: `head -1 /tmp/dgp-web-design/tokens/editorial-tokens.html && rg -c '#7FA650|#B8D878|#B8603C' /tmp/dgp-web-design/tokens/editorial-tokens.html`
Expected: Tokens marker; count ≥ 3.

---

### Task 4: Brief + flows cards

**Files:**
- Create: `/tmp/dgp-web-design/brief/design-brief.html`
- Create: `/tmp/dgp-web-design/flows/user-flows.html`

- [ ] **Step 1: Write `brief/design-brief.html`** (group `Brief`, prose card, width 900px)

Sections (each 1-2 short paragraphs, no filler):
1. **What DGP is** — deterministic password manager: PBKDF2(seed+account, service) → password, recomputed on demand, never stored. Android app is mature; web app is a 2017-era Flask leftover being redesigned to parity.
2. **Architecture the design must assume (hybrid)** — server account (username/password) is only a sync shell storing the *encrypted* service config; the seed is typed/scanned client-side, lives in browser memory only while unlocked; all derivation client-side (WebCrypto). Two distinct credential moments: *log in* (account) then *unlock* (seed). Logout ≠ lock.
3. **Security constraints that are design inputs** — passwords masked by default, hold/press-to-reveal; auto-clear countdown after generation (60s today — make it visible); clipboard copy flagged sensitive with user feedback; seed never rendered after unlock; vault secrets are the only stored secrets (badge them differently); renaming a vault entry invalidates its secret (the edit screen must warn).
4. **The ask** — "the Claude treatment": a distinctive, confident web visual language. Editorial tokens (see Tokens group) are the brand anchor. Light + dark from day one. Desktop-first, fully usable at 390px.
5. **Personality cue** — the pride-flag identity fingerprint (chip + two BIP-39 words derived from seed+account; user's true identity vanity-mined to the trans flag). It's the app's joy moment — give it room.
6. **Map** — one line per group: Flows, Tokens, Wireframes (structure is agreed — restyle, don't rearrange without reason), Data (use as mockup content), Before (what we're escaping).

- [ ] **Step 2: Write `flows/user-flows.html`** (group `Flows`, width 1100px)

CSS boxes-and-arrows (flex rows, `→` glyphs). Three lanes:
1. **Main lane:** `register → login → unlock (seed + account) → services list → [tap service] → reveal (hold-to-show, copy, countdown) | [edit] → edit entry → back`. Branch from services: `+ add` → edit entry (new); search; archive toggle; drag-reorder.
2. **Settings lane:** `settings → {change seed (re-unlock), export (PIN dialog → encrypted blob → share/copy), import (paste blob + PIN | plaintext JSON file), test vectors (pass/fail table), theme (auto/light/dark), flag identity → gallery, lock & quit, clear all (danger confirm)}`.
3. **Flag lane:** `unlock → fingerprint computed (cached per seed+account) → chip+word in header → [matches my flag?] yes: proceed / no: STOP — wrong seed or account`. Plus `set as my flag` (nonce mining, instant) from the account dialog.
Annotate lane 3: chip is a *recognition* aid, not authentication; nonce is public.

- [ ] **Step 3: Verify**

Run: `head -1 /tmp/dgp-web-design/brief/design-brief.html /tmp/dgp-web-design/flows/user-flows.html`
Expected: Brief / Flows markers respectively.

---

### Task 5: Wireframes — auth + unlock + services

**Files:**
- Create: `/tmp/dgp-web-design/wireframes/login-register.html`
- Create: `/tmp/dgp-web-design/wireframes/unlock.html`
- Create: `/tmp/dgp-web-design/wireframes/services.html`
- Create: `/tmp/dgp-web-design/wireframes/services-mobile.html`

All use the shared skeleton from "Shared conventions". Required content per card:

- [ ] **Step 1: `login-register.html`** — two `.frame`s side by side (login: username, password, submit, "create account" link; register: username, email *(optional)*, password ×2 with min-8 hint, submit). `.note`: "account = sync shell only; no secrets behind this password — tone should be lighter than the unlock step."

- [ ] **Step 2: `unlock.html`** — centered single column: masked seed textarea with visibility toggle + "scan QR" button; account field (persisted, prefilled `demo@example.com`); live flag chip placeholder next to account (`.ghost` box: "flag chip + two words — updates as identity changes"); primary Unlock button. `.note`s: "seed never leaves the browser; field cleared on lock", "flag chip is the typo detector — see Flows lane 3."

- [ ] **Step 3: `services.html`** (the fully-worked example — build exactly this, then match its idiom in every later wireframe)

```html
<!-- @dsCard group="Wireframes" -->
<!doctype html>
<html><head><meta charset="utf-8">
<title>DGP Web — Services (wireframe)</title>
<style>/* shared skeleton CSS from "Shared conventions" — paste verbatim */</style>
</head><body>
<div class="frame">
  <div class="bar">
    <div class="box ghost" style="width:34px;height:34px;border-radius:50%">flag</div>
    <h1>DGP</h1>
    <span class="pill mono">ocean · velvet</span>
    <input class="box" style="flex:1" placeholder="search services…">
    <button class="btn">archive</button>
    <button class="btn">settings</button>
    <button class="btn primary">+ add</button>
  </div>
  <div class="row"><div class="strip"></div><b style="flex:1">github.com</b><span class="pill">alnumlong</span><span class="ghost box" style="padding:2px 6px">⠿</span></div>
  <div class="row"><div class="strip"></div><b style="flex:1">gmail</b><span class="pill">alnum</span><span class="ghost box" style="padding:2px 6px">⠿</span></div>
  <div class="row"><div class="strip"></div><b style="flex:1">bank-leumi</b><span class="pill">xkcdlong</span><span class="pill">note</span><span class="ghost box" style="padding:2px 6px">⠿</span></div>
  <div class="row"><div class="strip"></div><b style="flex:1">legacy-router</b><span class="pill">vault</span><span class="pill">note</span><span class="ghost box" style="padding:2px 6px">⠿</span></div>
  <!-- …remaining 7 active sample services, same row idiom… -->
  <div class="bar"><h2>archived (1)</h2></div>
  <div class="row" style="opacity:.5"><div class="strip"></div><b style="flex:1">myspace</b><span class="pill">alnum</span></div>
  <div class="note">Each .strip carries the entry type's color (see Tokens). Row click → reveal. ⠿ = drag-reorder handle, in-place (no separate mode — improves on Android). Header chip shows flag + two-word fingerprint; "ocean · velvet" is demo content. List order is manual, never alphabetical.</div>
  <div class="note">Empty state (not drawn): centered "no services yet" + primary add button.</div>
</div>
</body></html>
```

- [ ] **Step 4: `services-mobile.html`** — same content in `.frame.mobile`: search collapses to icon, pills shrink, `+ add` becomes a floating bottom-right button. `.note`: "row height ≥ 44px touch target."

- [ ] **Step 5: Verify**

Run: `head -1 /tmp/dgp-web-design/wireframes/*.html | rg -c 'Wireframes'`
Expected: 4 (so far).

---

### Task 6: Wireframes — reveal, edit, settings, export/import, flag gallery

**Files:**
- Create: `/tmp/dgp-web-design/wireframes/reveal.html`, `reveal-mobile.html`, `edit-entry.html`, `settings.html`, `export-import.html`, `flag-gallery.html`

Same skeleton + idiom as Task 5 Step 3. Required content:

- [ ] **Step 1: `reveal.html`** — modal panel over a dimmed services list: service name + type pill + note line; password area with three drawn states side by side: (a) masked `••••••••••••` + "hold to reveal" button, (b) revealed `.mono` (use the real Task-1 output for github.com/alnumlong), (c) vault variant: "decrypting…" → plaintext, plus decrypt-failure error text. Copy button + "copied ✓" feedback + visible auto-clear countdown ("clears in 0:47"). `.note`: "countdown is a constraint, not decoration; copy uses sensitive-clipboard."

- [ ] **Step 2: `reveal-mobile.html`** — same as bottom sheet in `.frame.mobile`, masked state only. `.note`: "sheet drag vs hold-to-reveal must not conflict (Android lesson: separate the reveal control from the scrollable surface)."

- [ ] **Step 3: `edit-entry.html`** — form: name field; **type tile grid** — 9 tiles (8 formats + vault, each tile = label + one-line description, e.g. vault: "store externally-assigned secret"); **live derive preview** `.mono` box ("updates debounced as you type — hidden for vault/blank name"); note field; vault-only section: secret field + visibility toggle; Save / Cancel / Delete. Two `.note`s: "renaming a vault entry re-encrypts under the new name — warn before saving a rename of a vault entry with existing secret"; "duplicate names rejected."

- [ ] **Step 4: `settings.html`** — sectioned list: **identity** (change seed → re-unlock; account; flag identity row with chip → gallery), **config** (export → PIN dialog; import from clipboard; import JSON file), **app** (theme auto/light/dark pills; test vectors → pass/fail count; algorithm row, disabled, labeled lowercase "fixed by protocol — never editable"), **danger** (lock & quit; clear all entries — red, confirm dialog drawn inline: "vault secrets cannot be recovered after this").

- [ ] **Step 5: `export-import.html`** — left: export flow (PIN entry dialog with 2 fields PIN+confirm, then encrypted-blob `.mono` box ~6 lines base64 + copy/share buttons + "600k PBKDF2 iterations — PIN is the only secret" note). Right: import flow (paste box, PIN field, then result states: "imported 12 services ✓" / "wrong PIN or corrupted blob" error). `.note`: "blob is wire-compatible with Android + Linux exports."

- [ ] **Step 6: `flag-gallery.html`** — grid of 10 flags as grayscale stripe blocks (3-6 horizontal stripes each) labeled: trans *(index 0 — vanity target)*, rainbow, bi, pan, lesbian, nonbinary, ace, genderfluid, agender, genderqueer. Current-identity card: chip + `.mono` two words + "set as my flag" primary button. `.note`s: "real stripe colors ship in the visual phase — grayscale here on purpose; flags must stay distinguishable at 20px chip size"; "'set as my flag' mines a public nonce so this identity lands on trans — instant, no progress UI needed."

- [ ] **Step 7: Verify all ten wireframes**

Run: `for f in /tmp/dgp-web-design/wireframes/*.html; do head -1 "$f" | rg -q '@dsCard group="Wireframes"' || echo "BAD: $f"; done; ls /tmp/dgp-web-design/wireframes | wc -l`
Expected: no BAD lines; count = 10.

---

### Task 7: Local render check + DesignSync push

**Files:** none created in repo; pushes `/tmp/dgp-web-design/**` to claude.ai/design.

- [ ] **Step 1: Structural sweep of the whole bundle**

```bash
for f in $(find /tmp/dgp-web-design -name '*.html'); do
  head -1 "$f" | rg -q '^<!-- @dsCard group="(Brief|Flows|Tokens|Wireframes|Data|Before)" -->' || echo "BAD MARKER: $f"
  rg -q '</html>' "$f" || echo "UNCLOSED: $f"
done; find /tmp/dgp-web-design -name '*.html' | wc -l
```

Expected: no BAD/UNCLOSED lines; 17 files (1 brief, 1 flows, 1 tokens, 10 wireframes, 1 data, 2 before). Delete `gen_samples.py` is NOT needed — it's `.py`, excluded by the plan globs.

- [ ] **Step 2: Spot-render the two highest-risk cards**

Read `/tmp/dgp-web-design/wireframes/services.html` and `/tmp/dgp-web-design/tokens/editorial-tokens.html` and confirm: no truncated tags, sample data names present, hex values present. (User can also `xdg-open` any card.)

- [ ] **Step 3: DesignSync — check for an existing project, then create**

Call `DesignSync {method: list_projects}`. If a project named "DGP Web" already exists and is writable, reuse its projectId (confirm with user before writing into it). Otherwise call `DesignSync {method: create_project, name: "DGP Web"}` and record the returned `projectId`.

- [ ] **Step 4: Finalize plan**

```
DesignSync {
  method: finalize_plan,
  projectId: <from step 3>,
  localDir: "/tmp/dgp-web-design",
  writes: ["brief/*.html", "flows/*.html", "tokens/*.html",
           "wireframes/*.html", "data/*.html", "before/*.html"]
}
```

Record the returned `planId`.

- [ ] **Step 5: Write files**

One `write_files` call with the `planId` and all 17 files as `{path, localPath}` pairs (path = project-relative, localPath = same relative path under localDir). No `register_assets` — cards come from the `@dsCard` markers.

- [ ] **Step 6: Verify push**

Call `DesignSync {method: list_files, projectId: …}`. Expected: exactly the 17 planned paths. Report the project name + path list to the user and ask them to confirm the six groups render in the Claude Design pane (spec acceptance #3).

---

## Self-review notes (done at write time)

- Spec coverage: brief ✓ (T4), flows ✓ (T4), tokens ✓ (T3), 8 wireframes + 2 mobile variants ✓ (T5/T6), engine-generated data ✓ (T1), before-captures with fallback ✓ (T2), push mechanics + list_files acceptance ✓ (T7), throwaway-seed rule ✓ (T1), glob-based plan set per spec ✓ (T7.4).
- No repo files are created or modified by tasks 1-7; the only repo artifact is this plan + the already-committed spec.
- File count consistency: 17 everywhere (T7 sweep matches per-task creations).
