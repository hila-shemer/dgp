#!/usr/bin/env bash
# Narrated CLI demo of dgp-mobile. Runs real commands in a fresh terminal.
#
#   ./demo.sh              the project as it sits in source (default, the only channel)
#   DEMO_PAUSE=0 ./demo.sh no read-pauses (fast replay / self-test)
#
# Stable-only repo: there is no installed binary and no staging/next channel,
# so we demo straight from source. The shipping product is an *Android app*
# (needs the SDK + a minutes-long Gradle build -> out of scope for a 30s demo),
# and the bundled simple.py reference is Python 2 + werkzeug (won't run on a
# modern interpreter). So the headline faithfully re-runs the project's own
# algorithm with the Python 3 standard library and reproduces ITS OWN published
# test vectors -- honest output, same PBKDF2 the app and simple.py compute.
#
# Deliberately NO 'set -e': a demo command may exit non-zero on purpose.
# A fresh shell starts here -> use absolute paths.
set -uo pipefail

SRC=/home/hila/proj/dgp-mobile         # source dir; assumed not to move (stale ok)
PAUSE=${DEMO_PAUSE:-3}

case ${1:-} in
  '') ;;
  *) echo "usage: $0   (stable-only, no channel flags)" >&2; exit 2 ;;
esac

say() { printf '# %s\n' "$*"; sleep "$(( PAUSE > 0 ? 1 : 0 ))"; }   # explanation line
run() { printf '$ %s\n' "$*"; eval "$*"; sleep "$PAUSE"; }          # show command, run for real, pause
sec() { printf '\n'; }                                              # one blank line at a section boundary

# --- overview: assume the viewer last saw this months ago, name alone won't do
say "dgp-mobile (HashDroid) -- a DETERMINISTIC password manager that stores nothing."
say "You type one seed + an account name; it derives the site password by PBKDF2."
say "Same inputs always give the same password, so there is no vault to sync or leak."
say "Ships as an Android app; simple.py is the portable reference implementation."
say "Demoing: the algorithm from source at $SRC (no build, no binary on PATH)."

# --- proof of life: cheap facts that work even if nothing is built
sec
say "What's in the repo -- an Android app plus a reference script and wordlist:"
run "ls $SRC"

# --- the headline: run the project's real algorithm and reproduce ITS test vectors
# simple.py is Python 2 (print statements, werkzeug.pbkdf2) so it won't execute on
# Python 3.x; the Android app needs the SDK. Both compute the SAME thing: PBKDF2-
# HMAC-SHA1, 42000 iterations, password = seed+account, salt = name, 40-byte key,
# then base58/alnum-encode. We re-run exactly that with the stdlib and reproduce
# the vectors simple.py hardcodes at lines 124-126 (a / "" / aa).
sec
say "Now watch the real derivation -- a faithful stdlib port of the project's PBKDF2:"
run "python3 $SRC/demo_gen.py"

sec
say "Re-run it: same seed in, same passwords out -- deterministic, nothing stored."
run "python3 $SRC/demo_gen.py"

sec
say "That's dgp-mobile. Source: $SRC (app/ is the shipping Android build)."
