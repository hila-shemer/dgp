#!/usr/bin/env bash
# Narrated CLI demo of webdgp (DGP) — runs real commands in a fresh terminal.
#
#   ./demo.sh              stable: the 'dgp' on PATH                 (default)
#   DEMO_PAUSE=0 ./demo.sh no read-pauses (fast replay / self-test)
#
# Stable-only project: the Linux port installs a single 'dgp' CLI. No
# --staging/--next channels exist, so none are offered.
#
# Deliberately NO 'set -e': a demo command may exit non-zero on purpose and we
# want the real exit shown, not the script aborted. Fresh shell -> absolute paths.
set -uo pipefail

SRC=/home/hila/proj/webdgp            # repo root (stale ok)
LINUX=$SRC/linux                      # the Python port that ships the CLI
PAUSE=${DEMO_PAUSE:-3}

case ${1:-} in
  '') ;;
  *) echo "usage: $0   (stable-only; no channel flags)" >&2; exit 2 ;;
esac

# Stable: the installed 'dgp' on PATH. Fall back to "python3 -m dgp" run from
# the source tree in place if nothing is installed (stale is fine).
if TOOL=$(command -v dgp 2>/dev/null); then :; else TOOL="python3 -m dgp"; fi

say() { printf '# %s\n' "$*"; sleep "$(( PAUSE > 0 ? 1 : 0 ))"; }   # explanation line
run() { printf '\n$ %s\n' "$*"; eval "$*"; sleep "$PAUSE"; }          # show command, run for real, pause

# A throwaway seed + account so the demo never touches your real ~/.config/dgp.
# Every 'gen' below passes --type explicitly, so it derives in pure-function
# mode and never reads or writes the stored service list. Nothing is persisted.
SEED="correct horse battery staple"
ACCT="alice@example.com"

# --- overview: assume the viewer last saw this months ago; the name won't do
say "webdgp = DGP, 'Deterministically Generated Passwords'."
say "No password database. Each password is RE-DERIVED on demand from"
say "(seed + account) as the key and the service name as the salt, via"
say "PBKDF2-HMAC-SHA1 x42000. Same inputs -> same password, forever."
say "Primary target is an Android app; linux/ is a Python port of the SAME"
say "engine (algorithm + export format are wire-compatible). We demo that CLI."
say "Demoing: $TOOL   (stable, on PATH)"

# --- proof of life: cheap facts that work even if nothing is 'ready'
printf '\n'   # blank line before this section's narration
say "What can it do? (note: no --version; -h lists the subcommands)"
run "$TOOL -h 2>&1 | sed -n '1,15p'"

# --- headline: derive real passwords; show determinism is the whole point
printf '\n'   # blank line before this section's narration
say "Derive a github.com password (alnum) for our throwaway seed+account:"
run "$TOOL gen github.com --type alnum --seed \"\$SEED\" --account \"\$ACCT\""
say "Run the exact same inputs again -> byte-identical. That's the point:"
run "$TOOL gen github.com --type alnum --seed \"\$SEED\" --account \"\$ACCT\""
say "Same key material, different output format (xkcd = BIP-39 words):"
run "$TOOL gen github.com --type xkcd  --seed \"\$SEED\" --account \"\$ACCT\""
say "Change only the service name -> a completely different password:"
run "$TOOL gen gitlab.com --type alnum --seed \"\$SEED\" --account \"\$ACCT\""

# --- and the load-bearing claim: the algorithm matches Android exactly
printf '\n'   # blank line before this section's narration
say "74 built-in test vectors lock the algorithm to the Android engine:"
run "$TOOL test-vectors 2>&1 | tail -1"

printf '\n'   # blank line before the closing line
say "That's webdgp. Source: $SRC  (CLI: $LINUX)"
