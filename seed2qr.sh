#!/bin/sh
# Generate a QR code from a seed file for scanning into the DGP Android app.
# Usage: ./seed2qr.sh [seed_file]
# Defaults to ./seed if no argument given.
# Requires: qrencode (dnf install qrencode)

SEED_FILE="${1:-./seed}"

if [ ! -f "$SEED_FILE" ]; then
    echo "Error: seed file '$SEED_FILE' not found" >&2
    exit 1
fi

qrencode -r "$SEED_FILE" -t UTF8 -o -
