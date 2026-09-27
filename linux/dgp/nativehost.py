"""Chrome native messaging host for the DGP extension.

Framing: each message is a 4-byte native-endian uint32 length followed by that
many bytes of UTF-8 JSON, on stdin (requests) and stdout (replies). The loop
runs until stdin hits EOF. Chrome treats anything on stdout that is not a
framed reply as a protocol error, and stderr may land in Chrome's log, so this
module never prints; error replies carry fixed strings, never a password or an
exception's text.

Seed, account and services are re-read from the store on every request, so a
`dgp config` change shows up without restarting Chrome.
"""
from __future__ import annotations

import json
import struct
from typing import BinaryIO

from dgp import engine, store, vault
from dgp.cli import USER_VISIBLE_TYPES
from dgp.service import DgpService
from dgp.sitematch import match

# Chrome caps host->extension messages at 1 MiB; requests are tiny, so refuse
# anything bigger rather than buffering it.
MAX_REQUEST = 1024 * 1024

_LEN = struct.Struct("=I")


class _Refused(Exception):
    """An error whose message is safe to send back (a fixed string)."""


def read_message(inp: BinaryIO):
    """Return the next decoded request, or None at EOF. Raises _Refused on bad input."""
    head = inp.read(4)
    if len(head) < 4:
        return None
    (n,) = _LEN.unpack(head)
    if n > MAX_REQUEST:
        # Drain so the stream stays in sync, then refuse.
        while n > 0:
            chunk = inp.read(min(n, 65536))
            if not chunk:
                return None
            n -= len(chunk)
        raise _Refused("request too large")
    body = inp.read(n)
    if len(body) < n:
        return None
    try:
        msg = json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, ValueError):
        raise _Refused("malformed request")
    if not isinstance(msg, dict):
        raise _Refused("malformed request")
    return msg


def write_message(out: BinaryIO, obj: dict) -> None:
    body = json.dumps(obj, separators=(",", ":")).encode("utf-8")
    out.write(_LEN.pack(len(body)))
    out.write(body)
    out.flush()


def _summary(s: DgpService) -> dict:
    return {"id": s.id, "name": s.name, "type": s.type}


def _read_seed() -> str:
    try:
        seed = store.read_text_file(store.seed_path()).rstrip()
    except FileNotFoundError:
        seed = ""
    if not seed:
        raise _Refused("no seed configured; run dgp-gui or write ~/.config/dgp/seed")
    return seed


def _read_account() -> str:
    try:
        return store.read_text_file(store.account_path()).rstrip()
    except FileNotFoundError:
        return ""


def _password_for(s: DgpService) -> str:
    seed = _read_seed()
    account = _read_account()
    if s.type == "vault":
        if not s.encrypted_secret:
            raise _Refused("vault entry has no secret")
        pw = vault.decrypt_vault(s.encrypted_secret, seed, s.name, account)
        if pw is None:
            raise _Refused("vault entry did not decrypt with the current seed and account")
        return pw
    if s.type not in USER_VISIBLE_TYPES:
        raise _Refused("unsupported entry type")
    return engine.generate(seed, s.name, s.type, account)


def handle(req: dict) -> dict:
    op = req.get("op")
    services = store.read_services()
    if op == "list":
        return {"ok": True, "services": [_summary(s) for s in services if not s.archived]}
    if op == "match":
        origin = req.get("origin")
        if not isinstance(origin, str):
            raise _Refused("match needs an origin string")
        return {"ok": True, "services": [_summary(s) for s in match(services, "web", origin)]}
    if op == "get":
        sid = req.get("id")
        s = next((x for x in services if x.id == sid and not x.archived), None)
        if s is None:
            raise _Refused("no such entry")
        return {"ok": True, "password": _password_for(s)}
    raise _Refused("unknown op")


def _reply_for(req) -> dict:
    try:
        return handle(req)
    except _Refused as e:
        return {"ok": False, "error": str(e)}
    except Exception:
        # Deliberately drop the exception text: it could echo secret material.
        return {"ok": False, "error": "internal error"}


def serve(inp: BinaryIO, out: BinaryIO) -> None:
    while True:
        try:
            req = read_message(inp)
        except _Refused as e:
            write_message(out, {"ok": False, "error": str(e)})
            continue
        if req is None:
            return
        write_message(out, _reply_for(req))
