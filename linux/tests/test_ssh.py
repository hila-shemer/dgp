import os
import shutil
import stat
import tempfile

import pytest

from dgp.ssh import derive_ed25519, serialize_openssh_private, serialize_openssh_public

# ── Vector tests ──────────────────────────────────────────────────────────────

VECTORS = [
    (
        "myseed", "github", "myaccount",
        "27464f4a7b9dfcc7c723e521ea384049016fe466f072e4112854c8c877ced92d",
        "e00c4995808aaefb1bf2de4d687f7dd857cb998fdd1b45eb40c5a90ce83aeb2a",
    ),
    (
        "anotherseed", "service.example.com", "",
        "11f653345f4d2dd3b0250322d8f59052323c38733a0d528fa529f0a06f7b3110",
        "8c5ecda068fec1fa5ed9bd78fa647be86ac0dca229096437443cefacfb965df9",
    ),
    (
        "seed_with_64_bytes_exactly_padding_needed_here_pad_padding_0000000",
        "ssh.example", "user@host",
        "f0a1799c3e5845f5351c71e97e885a8f8d760d748246ade8546d7333d995522c",
        "c2cec99d8872e6eb0e7885d7d5990978d5e902fe04e58f14f9a15f17b2d7840a",
    ),
]


@pytest.mark.parametrize("seed,name,account,exp_priv,exp_pub", VECTORS)
def test_derive_ed25519_private(seed, name, account, exp_priv, exp_pub):
    k = derive_ed25519(seed, name, account)
    assert bytes(k).hex() == exp_priv


@pytest.mark.parametrize("seed,name,account,exp_priv,exp_pub", VECTORS)
def test_derive_ed25519_public(seed, name, account, exp_priv, exp_pub):
    k = derive_ed25519(seed, name, account)
    assert bytes(k.verify_key).hex() == exp_pub


@pytest.mark.parametrize("seed,name,account,exp_priv,exp_pub", VECTORS)
def test_derive_ed25519_deterministic(seed, name, account, exp_priv, exp_pub):
    k1 = derive_ed25519(seed, name, account)
    k2 = derive_ed25519(seed, name, account)
    assert bytes(k1) == bytes(k2)
    assert bytes(k1.verify_key) == bytes(k2.verify_key)


# ── OpenSSH round-trip ────────────────────────────────────────────────────────

def test_openssh_private_pem_header():
    k = derive_ed25519("myseed", "github", "myaccount")
    pem = serialize_openssh_private(k, "test-comment")
    assert pem.startswith(b"-----BEGIN OPENSSH PRIVATE KEY-----")
    assert b"-----END OPENSSH PRIVATE KEY-----" in pem


def test_openssh_public_format():
    k = derive_ed25519("myseed", "github", "myaccount")
    pub = serialize_openssh_public(k.verify_key, "mycomment")
    assert pub.startswith("ssh-ed25519 ")
    parts = pub.split()
    assert len(parts) == 3
    assert parts[2] == "mycomment"


@pytest.mark.skipif(shutil.which("ssh-keygen") is None, reason="ssh-keygen not available")
def test_openssh_roundtrip_via_ssh_keygen(tmp_path):
    k = derive_ed25519("myseed", "roundtrip", "account123")
    comment = "dgp:roundtrip"
    pem = serialize_openssh_private(k, comment)
    pub_expected = serialize_openssh_public(k.verify_key, comment)

    key_file = tmp_path / "id_ed25519"
    key_file.write_bytes(pem)
    key_file.chmod(0o600)

    import subprocess
    result = subprocess.run(
        ["ssh-keygen", "-y", "-f", str(key_file)],
        capture_output=True, text=True
    )
    assert result.returncode == 0, f"ssh-keygen failed: {result.stderr}"
    keygen_pub = result.stdout.strip()
    assert keygen_pub.startswith("ssh-ed25519 ")
    # Compare base64 portion (strip comment which ssh-keygen may differ on)
    assert keygen_pub.split()[1] == pub_expected.split()[1]
