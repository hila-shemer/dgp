import os
import stat
import subprocess
import sys

import pytest


def _run(tmp_path, *args, extra_env=None):
    env = {**os.environ, "DGP_CONFIG_DIR": str(tmp_path)}
    if extra_env:
        env.update(extra_env)
    return subprocess.run(
        [sys.executable, "-m", "dgp", *args],
        env=env, capture_output=True
    )


# ── prng ──────────────────────────────────────────────────────────────────────

def test_prng_16_bytes(tmp_path):
    r = _run(tmp_path, "prng", "s", "--bytes", "16", "--seed", "a", "--account", "")
    assert r.returncode == 0
    assert len(r.stdout) == 16


def test_prng_zero_bytes_fails(tmp_path):
    r = _run(tmp_path, "prng", "s", "--bytes", "0", "--seed", "a", "--account", "")
    assert r.returncode != 0


def test_prng_out_file(tmp_path):
    out = str(tmp_path / "out.bin")
    r = _run(tmp_path, "prng", "s", "--bytes", "32", "--seed", "a", "--account", "",
             "--out", out)
    assert r.returncode == 0
    assert os.path.exists(out)
    assert os.path.getsize(out) == 32
    assert stat.S_IMODE(os.stat(out).st_mode) == 0o600


# ── ssh ───────────────────────────────────────────────────────────────────────

def test_ssh_to_stdout(tmp_path):
    r = _run(tmp_path, "ssh", "s", "--seed", "a", "--account", "")
    assert r.returncode == 0
    assert r.stdout.startswith(b"-----BEGIN OPENSSH PRIVATE KEY-----")
    assert r.stderr.startswith(b"ssh-ed25519 ")


def test_ssh_out_file(tmp_path):
    key_path = str(tmp_path / "id")
    r = _run(tmp_path, "ssh", "s", "--seed", "a", "--account", "", "--out", key_path)
    assert r.returncode == 0
    priv_path = key_path
    pub_path = key_path + ".pub"
    assert os.path.exists(priv_path)
    assert os.path.exists(pub_path)
    assert stat.S_IMODE(os.stat(priv_path).st_mode) == 0o600
    assert stat.S_IMODE(os.stat(pub_path).st_mode) == 0o644
    pub_content = open(pub_path).read().strip()
    assert pub_content.startswith("ssh-ed25519 ")


# ── btc-key ───────────────────────────────────────────────────────────────────

def test_btc_key_output(tmp_path):
    r = _run(tmp_path, "btc-key", "s", "--seed", "a", "--account", "")
    assert r.returncode == 0
    lines = r.stdout.decode("utf-8").strip().splitlines()
    assert len(lines) == 2
    # WIF mainnet compressed starts with K or L (or 5 for uncompressed, but we use compressed)
    assert lines[0][0] in ("5", "K", "L"), f"Unexpected WIF prefix: {lines[0][0]!r}"
    assert lines[1].startswith("bc1")


# ── btc-mnemonic ──────────────────────────────────────────────────────────────

def test_btc_mnemonic_24_words(tmp_path):
    r = _run(tmp_path, "btc-mnemonic", "s", "--seed", "a", "--account", "")
    assert r.returncode == 0
    words = r.stdout.decode("utf-8").split()
    assert len(words) == 24
