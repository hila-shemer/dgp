from __future__ import annotations
import base64
import os
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives import hashes

EXPORT_SALT = b"dgp-export-v1"
EXPORT_ITERATIONS = 600_000


def _derive_export_key(pin: str) -> bytes:
    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA256(),
        length=32,
        salt=EXPORT_SALT,
        iterations=EXPORT_ITERATIONS,
    )
    return kdf.derive(pin.encode("utf-8"))


def encrypt_export(plaintext: str, pin: str) -> str:
    key = _derive_export_key(pin)
    iv = os.urandom(12)
    ct = AESGCM(key).encrypt(iv, plaintext.encode("utf-8"), None)
    return base64.b64encode(iv + ct).decode("ascii")


def decrypt_export(blob: str, pin: str) -> str | None:
    try:
        key = _derive_export_key(pin)
        data = base64.b64decode(blob)
        iv, ct = data[:12], data[12:]
        pt = AESGCM(key).decrypt(iv, ct, None)
        return pt.decode("utf-8")
    except Exception:
        return None
