from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC

from dgp.wordlist import load_bip39_english

BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

_words: list[str] | None = None


def _get_words() -> list[str]:
    global _words
    if _words is None:
        _words = load_bip39_english()
    return _words


def pbkdf2_raw(seed: str, account: str, name: str, iters: int = 42000, dklen: int = 40) -> bytes:
    key_material = (seed + account).encode("utf-8")
    salt = name.encode("utf-8")
    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA1(),
        length=dklen,
        salt=salt,
        iterations=iters,
    )
    return kdf.derive(key_material)


def _to_base58(data: bytes) -> str:
    n = int.from_bytes(data, "big")
    result = []
    while n > 0:
        n, remainder = divmod(n, 58)
        result.append(BASE58_ALPHABET[remainder])
    return "".join(result)


def _grab_alnum(data: bytes, length: int) -> str:
    raw = _to_base58(data)
    for i in range(len(raw) - length + 1):
        candidate = raw[i : i + length]
        if (
            any(c.isascii() and c.isdigit() for c in candidate)
            and any(c.isascii() and c.islower() for c in candidate)
            and any(c.isascii() and c.isupper() for c in candidate)
        ):
            return candidate
    return raw[:length]


def _to_xkcd(data: bytes, count: int, words: list[str]) -> str:
    n = int.from_bytes(data, "big")
    result = []
    while n > 0 and len(result) < count:
        n, mod = divmod(n, 2048)
        word = words[mod]
        result.append(word[0].upper() + word[1:])
    return "".join(result)


def generate(seed: str, name: str, entry_type: str, secret: str, *, iterations: int = 42000) -> str:
    raw = pbkdf2_raw(seed, secret, name, iters=iterations)
    if entry_type == "hex":
        return raw[:4].hex()
    elif entry_type == "hexlong":
        return raw[:8].hex()
    elif entry_type == "alnum":
        return _grab_alnum(raw, 8)
    elif entry_type == "alnumlong":
        return _grab_alnum(raw, 12)
    elif entry_type == "base58":
        return _to_base58(raw)[:8]
    elif entry_type == "base58long":
        return _to_base58(raw)[:12]
    elif entry_type == "xkcd":
        return _to_xkcd(raw, 4, _get_words())
    elif entry_type == "xkcdlong":
        return _to_xkcd(raw, 6, _get_words())
    elif entry_type == "aeskey":
        return raw[:32].hex()
    else:
        return "unknown type"


def derive_aes_key(seed: str, name: str, secret: str, *, iterations: int = 42000) -> bytes:
    return pbkdf2_raw(seed, secret, name, iters=iterations)[:32]
