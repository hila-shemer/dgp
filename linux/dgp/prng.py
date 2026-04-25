from cryptography.hazmat.primitives.ciphers import Cipher
from cryptography.hazmat.primitives.ciphers.algorithms import ChaCha20
from dgp.engine import pbkdf2_raw


def chacha20_zeros(seed: str, name: str, account: str, nbytes: int) -> bytes:
    if not (1 <= nbytes <= 2**32):
        raise ValueError(f"nbytes must be 1..2**32, got {nbytes}")
    key = pbkdf2_raw(seed, account, name)[:32]
    nonce = bytes(16)  # 16-byte nonce required by cryptography's ChaCha20
    cipher = Cipher(ChaCha20(key, nonce), mode=None)
    return cipher.encryptor().update(b"\x00" * nbytes)
