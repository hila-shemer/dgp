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

# Strip trailing newline so the QR content matches what the app stores
SEED=$(tr -d '\n' < "$SEED_FILE")

# Show fingerprint (matches the app's seed fingerprint display)
printf 'SHA-256: %s\n' "$(printf '%s' "$SEED" | sha256sum | cut -c1-16)"

printf '%s' "$SEED" | qrencode -t UTF8 -o -
