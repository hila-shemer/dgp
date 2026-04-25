import hashlib
from coincurve import PrivateKey
from dgp.engine import pbkdf2_raw
from dgp.wordlist import load_bip39_english

_N = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141
_B58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
_BECH32_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"


def _sha256(data: bytes) -> bytes:
    return hashlib.sha256(data).digest()


def _hash160(pubkey: bytes) -> bytes:
    h = hashlib.new('ripemd160')
    h.update(_sha256(pubkey))
    return h.digest()


def _base58check_encode(data: bytes) -> str:
    n = int.from_bytes(data, 'big')
    digits = []
    while n:
        n, r = divmod(n, 58)
        digits.append(_B58_ALPHABET[r])
    result = ''.join(reversed(digits))
    for byte in data:
        if byte == 0:
            result = '1' + result
        else:
            break
    return result


def _base58_decode(s: str) -> bytes:
    """Decode a base58-encoded string to bytes (no checksum verification)."""
    n = 0
    for c in s:
        idx = _B58_ALPHABET.index(c)
        n = n * 58 + idx
    # Determine leading zero bytes
    leading = 0
    for c in s:
        if c == '1':
            leading += 1
        else:
            break
    result = n.to_bytes((n.bit_length() + 7) // 8, 'big') if n else b''
    return b'\x00' * leading + result


def base58check_decode(s: str) -> bytes:
    """Decode a base58check string; raises ValueError on bad checksum."""
    raw = _base58_decode(s)
    payload, checksum = raw[:-4], raw[-4:]
    expected = _sha256(_sha256(payload))[:4]
    if checksum != expected:
        raise ValueError("Bad base58check checksum")
    return payload


def derive_secp256k1_priv(seed: str, name: str, account: str) -> bytes:
    raw = pbkdf2_raw(seed, account, name)[:32]
    n = int.from_bytes(raw, 'big')
    if n == 0 or n >= _N:
        raise ValueError("Derived private key is invalid (out of range for secp256k1)")
    return raw


def wif_compressed_mainnet(priv: bytes) -> str:
    payload = b'\x80' + priv + b'\x01'
    checksum = _sha256(_sha256(payload))[:4]
    return _base58check_encode(payload + checksum)


def bech32_p2wpkh_mainnet(pub_compressed: bytes) -> str:
    program = _hash160(pub_compressed)
    return _bech32_encode("bc", 0, program)


def derive_bip39_mnemonic(seed: str, name: str, account: str, words: int = 24) -> list:
    wordlist = load_bip39_english()
    entropy = pbkdf2_raw(seed, account, name)[:32]
    checksum = _sha256(entropy)[0]
    # 264-bit value: entropy (256 bits) shifted left 8, OR'd with checksum byte
    n = (int.from_bytes(entropy, 'big') << 8) | checksum
    result = []
    for i in range(24):
        idx = (n >> (264 - 11 * (i + 1))) & 0x7FF
        result.append(wordlist[idx])
    return result


# --- Bech32 (BIP-173) ---

def _bech32_polymod(values):
    GEN = [0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3]
    chk = 1
    for v in values:
        b = chk >> 25
        chk = (chk & 0x1ffffff) << 5 ^ v
        for i in range(5):
            chk ^= GEN[i] if ((b >> i) & 1) else 0
    return chk


def _bech32_hrp_expand(hrp):
    return [ord(x) >> 5 for x in hrp] + [0] + [ord(x) & 31 for x in hrp]


def _bech32_create_checksum(hrp, data):
    values = _bech32_hrp_expand(hrp) + list(data)
    polymod = _bech32_polymod(values + [0, 0, 0, 0, 0, 0]) ^ 1
    return [(polymod >> 5 * (5 - i)) & 31 for i in range(6)]


def _convertbits(data, frombits, tobits, pad=True):
    acc = 0
    bits = 0
    ret = []
    maxv = (1 << tobits) - 1
    max_acc = (1 << (frombits + tobits - 1)) - 1
    for value in data:
        if value < 0 or (value >> frombits):
            return None
        acc = ((acc << frombits) | value) & max_acc
        bits += frombits
        while bits >= tobits:
            bits -= tobits
            ret.append((acc >> bits) & maxv)
    if pad:
        if bits:
            ret.append((acc << (tobits - bits)) & maxv)
    elif bits >= frombits or ((acc << (tobits - bits)) & maxv):
        return None
    return ret


def _bech32_encode(hrp: str, witver: int, witprog: bytes) -> str:
    data = [witver] + _convertbits(witprog, 8, 5)
    checksum = _bech32_create_checksum(hrp, data)
    return hrp + '1' + ''.join([_BECH32_CHARSET[d] for d in data + checksum])


def bech32_decode(bech: str):
    """Decode a bech32 string. Returns (hrp, witver, witprog) or raises ValueError."""
    bech = bech.lower()
    if '1' not in bech:
        raise ValueError("No separator found")
    pos = bech.rfind('1')
    hrp = bech[:pos]
    data_chars = bech[pos + 1:]
    if len(data_chars) < 7:
        raise ValueError("Too short")
    decoded = []
    for c in data_chars:
        idx = _BECH32_CHARSET.find(c)
        if idx < 0:
            raise ValueError(f"Invalid character: {c}")
        decoded.append(idx)
    if _bech32_polymod(_bech32_hrp_expand(hrp) + decoded) != 1:
        raise ValueError("Invalid checksum")
    witver = decoded[0]
    witprog = _convertbits(decoded[1:-6], 5, 8, False)
    if witprog is None:
        raise ValueError("convertbits failed")
    return hrp, witver, bytes(witprog)
