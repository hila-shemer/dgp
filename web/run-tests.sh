#!/usr/bin/env bash
# Gate for web/index.html. Run it before believing anything the page tells you.
#
#   test-vectors.mjs  cuts the engine out of index.html and runs it against the
#                     74 golden vectors from linux/dgp/testvectors.py, plus
#                     reference vectors for the types those never touch.
#   test-ui.mjs       drives the page around the engine through a DOM shim:
#                     storage, export, import, lock, and the assertion that no
#                     seed or password ever reaches localStorage.
#
# Needs node with WebCrypto - v18 or newer. Nothing else, no install step.
set -euo pipefail
cd "$(dirname "$0")"
node test-vectors.mjs
node test-ui.mjs
