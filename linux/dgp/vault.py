from __future__ import annotations
import base64
import os
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from dgp import engine


def encrypt_with_raw_key(plaintext: str, key: bytes) -> str:
    assert len(key) == 32
    iv = os.urandom(12)
    ct = AESGCM(key).encrypt(iv, plaintext.encode("utf-8"), None)
    return base64.b64encode(iv + ct).decode("ascii")


def decrypt_with_raw_key(blob: str, key: bytes) -> str | None:
    try:
        data = base64.b64decode(blob)
        iv, ct = data[:12], data[12:]
        pt = AESGCM(key).decrypt(iv, ct, None)
        return pt.decode("utf-8")
    except Exception:
        return None


def encrypt_vault(plaintext: str, seed: str, name: str, account: str) -> str:
    key = engine.derive_aes_key(seed, name, account)
    return encrypt_with_raw_key(plaintext, key)


def decrypt_vault(blob: str, seed: str, name: str, account: str) -> str | None:
    key = engine.derive_aes_key(seed, name, account)
    return decrypt_with_raw_key(blob, key)
