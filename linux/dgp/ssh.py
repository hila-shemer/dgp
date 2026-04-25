import os
import struct
import base64
from nacl.signing import SigningKey
from dgp.engine import pbkdf2_raw


def derive_ed25519(seed: str, name: str, account: str) -> SigningKey:
    raw = pbkdf2_raw(seed, account, name)[:32]
    return SigningKey(raw)


def serialize_openssh_public(verify_key, comment: str = "") -> str:
    vk_bytes = bytes(verify_key)
    pubkey_blob = (
        struct.pack(">I", 11) + b"ssh-ed25519"
        + struct.pack(">I", 32) + vk_bytes
    )
    encoded = base64.b64encode(pubkey_blob).decode("ascii")
    parts = ["ssh-ed25519", encoded]
    if comment:
        parts.append(comment)
    return " ".join(parts)


def serialize_openssh_private(signing_key, comment: str = "") -> bytes:
    vk_bytes = bytes(signing_key.verify_key)
    sk_seed = bytes(signing_key)

    pubkey_blob = (
        struct.pack(">I", 11) + b"ssh-ed25519"
        + struct.pack(">I", 32) + vk_bytes
    )

    comment_bytes = comment.encode("utf-8")
    checkint = os.urandom(4)

    priv_body = (
        checkint + checkint
        + struct.pack(">I", 11) + b"ssh-ed25519"
        + struct.pack(">I", 32) + vk_bytes
        + struct.pack(">I", 64) + sk_seed + vk_bytes
        + struct.pack(">I", len(comment_bytes)) + comment_bytes
    )

    pad_needed = (8 - len(priv_body) % 8) % 8
    priv_body += bytes(range(1, pad_needed + 1))

    key_blob = (
        b"openssh-key-v1\x00"
        + struct.pack(">I", 4) + b"none"
        + struct.pack(">I", 4) + b"none"
        + struct.pack(">I", 0)
        + struct.pack(">I", 1)
        + struct.pack(">I", len(pubkey_blob)) + pubkey_blob
        + struct.pack(">I", len(priv_body)) + priv_body
    )

    b64 = base64.b64encode(key_blob).decode("ascii")
    lines = ["-----BEGIN OPENSSH PRIVATE KEY-----"]
    for i in range(0, len(b64), 70):
        lines.append(b64[i:i + 70])
    lines.append("-----END OPENSSH PRIVATE KEY-----")
    lines.append("")
    return "\n".join(lines).encode("utf-8")
