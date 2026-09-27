import io
import json
import os
import struct
import subprocess
import sys

import pytest

from dgp import engine, nativehost, store
from dgp.service import DgpService
from dgp.vault import encrypt_vault

SEED = "test-seed-not-real"
ACCOUNT = "acct"


def _frame(obj) -> bytes:
    body = json.dumps(obj).encode("utf-8")
    return struct.pack("=I", len(body)) + body


def _unframe(data: bytes) -> list[dict]:
    out, i = [], 0
    while i < len(data):
        (n,) = struct.unpack("=I", data[i:i + 4])
        out.append(json.loads(data[i + 4:i + 4 + n]))
        i += 4 + n
    return out


@pytest.fixture
def cfg(tmp_path, monkeypatch):
    monkeypatch.setenv("DGP_CONFIG_DIR", str(tmp_path))
    store.write_text_file(store.seed_path(), SEED + "\n")
    store.write_text_file(store.account_path(), ACCOUNT + "\n")
    services = [
        DgpService(id="gh", name="my code", type="alnum", sites=["github.com"]),
        DgpService(id="goo", name="google", type="xkcd"),
        DgpService(id="v", name="bank", type="vault", sites=["bank.example"],
                   encrypted_secret=encrypt_vault("vault-secret-1", SEED, "bank", ACCOUNT)),
        # Encrypted under another name, so it cannot decrypt as "broken".
        DgpService(id="bad", name="broken", type="vault",
                   encrypted_secret=encrypt_vault("vault-secret-2", SEED, "other", ACCOUNT)),
        DgpService(id="old", name="github", archived=True),
    ]
    store.write_services(services)
    return tmp_path


def _serve(requests) -> list[dict]:
    out = io.BytesIO()
    nativehost.serve(io.BytesIO(b"".join(_frame(r) for r in requests)), out)
    return _unframe(out.getvalue())


def _all_secrets():
    return [engine.generate(SEED, "my code", "alnum", ACCOUNT),
            engine.generate(SEED, "google", "xkcd", ACCOUNT),
            engine.generate(SEED, "github", "alnum", ACCOUNT),
            "vault-secret-1", "vault-secret-2", SEED]


def test_match_list_get(cfg):
    r = _serve([
        {"op": "match", "origin": "https://gist.github.com/x"},
        {"op": "list"},
        {"op": "get", "id": "gh"},
        {"op": "get", "id": "v"},
    ])
    assert r[0] == {"ok": True, "services": [{"id": "gh", "name": "my code", "type": "alnum"}]}
    assert [s["id"] for s in r[1]["services"]] == ["gh", "goo", "v", "bad"]
    assert r[2] == {"ok": True, "password": engine.generate(SEED, "my code", "alnum", ACCOUNT)}
    assert r[3] == {"ok": True, "password": "vault-secret-1"}


def test_errors_hold_no_password(cfg, monkeypatch):
    replies = _serve([
        {"op": "get", "id": "nope"},
        {"op": "get", "id": "old"},     # archived
        {"op": "get", "id": "bad"},     # vault that does not decrypt
        {"op": "match"},
        {"op": "frobnicate"},
        [1, 2],
    ])
    secrets = _all_secrets()
    # A crash mid-derivation must not leak the exception text either.
    leak = secrets[0]

    def boom(*a, **k):
        raise RuntimeError(f"oops {leak} {SEED}")
    monkeypatch.setattr(nativehost.engine, "generate", boom)
    replies += _serve([{"op": "get", "id": "gh"}])

    assert len(replies) == 7
    for rep in replies:
        assert rep["ok"] is False and set(rep) == {"ok", "error"}
        for secret in secrets:
            assert secret not in rep["error"]
    assert replies[-1]["error"] == "internal error"


def test_malformed_frame_then_continues(cfg):
    inp = io.BytesIO(struct.pack("=I", 3) + b"{x}" + _frame({"op": "list"}))
    out = io.BytesIO()
    nativehost.serve(inp, out)
    r = _unframe(out.getvalue())
    assert r[0] == {"ok": False, "error": "malformed request"}
    assert r[1]["ok"] is True


def test_no_seed(tmp_path, monkeypatch):
    monkeypatch.setenv("DGP_CONFIG_DIR", str(tmp_path))
    store.write_services([DgpService(id="a", name="a")])
    r = _serve([{"op": "get", "id": "a"}])
    assert r[0]["ok"] is False and "no seed" in r[0]["error"]


def test_subprocess_stderr_empty(cfg):
    env = {**os.environ, "DGP_CONFIG_DIR": str(cfg)}
    reqs = [{"op": "match", "origin": "https://github.com/"},
            {"op": "get", "id": "gh"}, {"op": "get", "id": "bad"}]
    p = subprocess.run([sys.executable, "-m", "dgp", "native-host", "chrome-extension://x/"],
                       input=b"".join(_frame(r) for r in reqs) + b"\x05\x00",  # torn frame at EOF
                       capture_output=True, env=env, timeout=60)
    assert p.returncode == 0
    assert p.stderr == b""
    r = _unframe(p.stdout)
    assert [x["ok"] for x in r] == [True, True, False]
    assert r[1]["password"] == engine.generate(SEED, "my code", "alnum", ACCOUNT)
