#!/usr/bin/env bash
# Narrated CLI demo of dgp (Linux port). Runs real commands in a fresh terminal.
#
#   ./demo.sh              the installed `dgp` on PATH (default)
#   DEMO_PAUSE=0 ./demo.sh no read-pauses (fast replay / self-test)
#
# Stable-only: there's a single `dgp` console-script on PATH (pip install -e linux/),
# so no --staging/--next channels -- the working tree IS what's installed (-e).
#
# Deliberately NO 'set -e': a demo command may exit non-zero on purpose and we
# want the real exit shown on screen, not the script aborted. Fresh shell -> use
# absolute paths. This demo is READ-ONLY: every `dgp gen` call passes an explicit
# throwaway --seed, so it never reads or writes the user's real ~/.config/dgp.
set -uo pipefail

SRC=/home/hila/proj/dgp                 # repo root; Linux port lives in linux/
PAUSE=${DEMO_PAUSE:-3}

# Stable: the `dgp` console-script on PATH; fall back to `python -m dgp` from
# source if it isn't installed (stale source is fine for a demo).
if TOOL=$(command -v dgp); then :; else TOOL="python3 $SRC/linux -m dgp"; fi

say() { printf '# %s\n' "$*"; sleep "$(( PAUSE > 0 ? 1 : 0 ))"; }     # explanation line
run() { printf '\n$ %s\n' "$*"; eval "$*"; sleep "$PAUSE"; }          # show command, run for real, pause

# --- overview: assume the viewer last saw this months ago, name alone won't do
say "dgp -- Deterministically Generated Passwords. No password DATABASE: each"
say "password is RE-DERIVED on demand from seed + account + service via"
say "PBKDF2-HMAC-SHA1 (42,000 iters). Same inputs always -> same password."
say "This is the Linux CLI port of the dgp Android app; both share the engine."
say "Demoing: $TOOL"

# --- proof of life: cheap facts that work even if nothing's been built/configured
run "$TOOL --help 2>&1 | sed -n '1,12p'"

# --- headline 1: determinism. Same seed+service -> identical password, twice.
# Explicit --seed keeps this read-only (never touches ~/.config/dgp).
say "Headline: it's deterministic. A throwaway seed, generate github's password:"
run "$TOOL --seed 'correct horse' gen github.com --type alnum"
say "Run the exact same thing again -- byte-identical, nothing was stored:"
run "$TOOL --seed 'correct horse' gen github.com --type alnum"
say "Change ONLY the service name and the password is completely different:"
run "$TOOL --seed 'correct horse' gen gmail.com --type alnum"

# --- headline 2: other output formats off the same 40-byte key
say "Same key, different format -- xkcd gives memorable BIP-39 words:"
run "$TOOL --seed 'correct horse' gen github.com --type xkcd"

# --- headline 3: the parity proof that keeps Linux == Android
say "74 built-in vectors lock the algorithm to the Android engine, bit-for-bit:"
run "$TOOL test-vectors 2>&1 | tail -1"

say "That's dgp. Source: $SRC (Linux port under linux/)"
