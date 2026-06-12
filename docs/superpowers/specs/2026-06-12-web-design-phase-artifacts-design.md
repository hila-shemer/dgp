# Web UI Design-Phase Artifacts — Design

**Date:** 2026-06-12
**Goal:** Seed a claude.ai/design design-system project ("DGP Web") with everything Claude Design needs to run the visual design phase for a redesigned DGP web app. This spec covers the artifact package only — not the web app implementation and not the visual design itself (that is Claude Design's job).

## Context

- The live web UI at 192.168.1.108:5000 is the legacy Flask app (removed from this repo in `62985ad`, source recoverable at `62985ad^`). It still wears the stock Flaskr tutorial stylesheet and has 4 screens: login, register, a single main page (search + `<select>` service list + add-entry form + custom-gen form + password panel), and a static FAQ.
- The Android app is far ahead of it: Editorial design language (`com.dgp.ui.theme`), unlock flow, services list with search/archive/drag-reorder, reveal sheet, vault entries, PIN-encrypted export/import, QR scan, test vectors, and the pride-flag identity fingerprint (spec: `2026-06-09-pride-flag-identity-fingerprint-design.md`).

## Decisions (made with user)

1. **Scope: Android-parity redesign.** The design covers the full Android feature set mapped to the web, not just a facelift of current functionality.
2. **Architecture assumption: hybrid.** Server account (username/password) is a sync shell storing only the encrypted service config; the seed is entered client-side, lives in memory only while unlocked, and all derivation happens in the browser (WebCrypto PBKDF2). Two credential layers appear in the design: account login and seed unlock.
3. **Delivery: DesignSync push** to a new claude.ai/design design-system project. No local artifact folder is the deliverable; the staging dir is scratch.
4. **Package shape: Option B** — brief + flows + Editorial token export + grayscale wireframes + realistic sample data + before-captures. Not brief-only (A), not a styled starter system (C).

## Package contents

Every pane-visible file is a self-contained HTML preview whose first line is `<!-- @dsCard group="…" -->`. Groups and files:

| Group | File(s) | Content |
|---|---|---|
| Brief | `brief/design-brief.html` | Product summary; hybrid architecture; security-UX constraints as design inputs (masked-by-default reveal, auto-clear countdown, sensitive clipboard, seed never persisted, vault as sole stored secret); audience; the "Claude treatment" ask; map of the other groups. |
| Flows | `flows/user-flows.html` | HTML flow diagram: register/login → unlock → services → reveal/edit; settings sub-flows (seed change, PIN export/import, QR scan-to-fill, test vectors); flag-identity flow. |
| Tokens | `tokens/editorial-tokens.html` | Swatches for EditorialColors light + dark (paper/ink/rule/accent/danger), the 9 per-type strip colors, type roles. Framed "brand anchor — evolve, don't ignore." |
| Wireframes | `wireframes/login-register.html`, `unlock.html`, `services.html`, `reveal.html`, `edit-entry.html`, `settings.html`, `export-import.html`, `flag-gallery.html` | Grayscale, structure-only, desktop ~1100px, responsive notes inline. `services` and `reveal` additionally get mobile-width variant cards as separate files (`services-mobile.html`, `reveal-mobile.html`) — daily-driver screens. The finalize_plan write set uses globs (`wireframes/*.html` etc.) so per-group file counts can flex without a plan mismatch. |
| Data | `data/sample-data.html` | Example output per entry type generated with the real Python engine (`linux/dgp`) using a throwaway seed; sample 12-service list with types and notes. |
| Before | `before/current-login.html`, `before/current-main.html` | Captures of the live Flaskr-era pages (markup + inlined style.css), lightly sandboxed, as motivation/contrast. |

## Build mechanics

1. Stage files in a scratch dir (not committed).
2. Sample outputs come from `python` + `linux/dgp` engine with a clearly-fake seed (e.g. `correct horse battery staple demo seed`) — never a real seed.
3. DesignSync: `create_project` ("DGP Web") → `finalize_plan` (writes = the paths above, localDir = staging dir) → `write_files`. Card registration is via `@dsCard` first-line markers; no `register_assets` needed.

## Error handling

- DesignSync calls can be rejected (auth scope, plan mismatch): surface the error and stop; nothing in the repo is affected.
- If the live server is unreachable for before-captures, fall back to templates at `62985ad^` (same markup source).

## Testing / acceptance

- Each HTML card opens standalone in a browser and renders legibly (manual spot-check before push).
- `list_files` after push shows exactly the planned paths.
- User confirms the cards appear in the Claude Design pane with the six groups.

## Out of scope

- The web app rewrite itself (stack choice, backend, WebCrypto port) — comes after the design phase.
- Any change to the legacy Flask deployment.
- Visual design decisions beyond the Editorial token anchor.
