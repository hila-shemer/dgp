"""Chrome integration commands: native-host and chrome-install."""
from __future__ import annotations

import argparse
import json
import os
import re
import shlex
import sys
from pathlib import Path


HOST_NAME = "io.github.hilashemer.dgp"
# ID of linux/chrome-extension, fixed by the "key" in its manifest.json:
# sha256 of the DER public key, first 32 hex digits mapped 0-9a-f -> a-p.
# tests/test_chrome.py checks the two stay in step.
EXTENSION_ID = "iagcagolicncaaanhhmdhfohinomcnhi"

_BROWSER_DIRS = ["google-chrome", "chromium"]


def register(subparsers: argparse._SubParsersAction) -> None:
    p = subparsers.add_parser("native-host", help="Chrome native messaging host (run by Chrome)")
    # Chrome passes the caller origin (and on some platforms more flags); ignore them.
    p.add_argument("chrome_args", nargs="*", help=argparse.SUPPRESS)
    p.set_defaults(func=_cmd_native_host)

    p = subparsers.add_parser("chrome-install",
                              help="Register the native host with Chrome and Chromium")
    p.add_argument("--extension-id", default=EXTENSION_ID, metavar="ID",
                   help=f"Allowed extension ID (default: {EXTENSION_ID})")
    p.add_argument("--dry-run", action="store_true", help="Show what would be written")
    p.set_defaults(func=_cmd_chrome_install)


def _cmd_native_host(args: argparse.Namespace) -> int:
    from dgp import nativehost
    # Nothing may reach stderr, not even an interpreter warning.
    devnull = os.open(os.devnull, os.O_WRONLY)
    os.dup2(devnull, 2)
    sys.stderr = open(os.devnull, "w")
    nativehost.serve(sys.stdin.buffer, sys.stdout.buffer)
    return 0


def launcher_path() -> Path:
    return Path.home() / ".local" / "share" / "dgp" / "native-host"


def manifest_paths() -> list[Path]:
    return [Path.home() / ".config" / b / "NativeMessagingHosts" / f"{HOST_NAME}.json"
            for b in _BROWSER_DIRS]


def _cmd_chrome_install(args: argparse.Namespace) -> int:
    ext_id = args.extension_id
    if not re.fullmatch(r"[a-p]{32}", ext_id):
        print(f"Error: '{ext_id}' is not a Chrome extension ID (32 letters a-p).",
              file=sys.stderr)
        return 1
    launcher = launcher_path()
    script = ("#!/bin/sh\n"
              "# Started by Chrome for the DGP extension; written by `dgp chrome-install`.\n"
              f"exec {shlex.quote(sys.executable)} -m dgp native-host\n")
    manifest = json.dumps({
        "name": HOST_NAME,
        "description": "DGP password host",
        "path": str(launcher),
        "type": "stdio",
        "allowed_origins": [f"chrome-extension://{ext_id}/"],
    }, indent=2) + "\n"

    if args.dry_run:
        print(f"would write {launcher}:\n{script}")
        for m in manifest_paths():
            print(f"would write {m}")
        print(manifest, end="")
        return 0

    launcher.parent.mkdir(parents=True, exist_ok=True)
    launcher.write_text(script, encoding="utf-8")
    launcher.chmod(0o755)
    print(f"wrote {launcher}")
    for m in manifest_paths():
        m.parent.mkdir(parents=True, exist_ok=True)
        m.write_text(manifest, encoding="utf-8")
        print(f"wrote {m}")
    return 0

