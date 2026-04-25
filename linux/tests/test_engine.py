from unittest.mock import patch

from dgp.engine import BASE58_ALPHABET, _grab_alnum, _to_base58, _to_xkcd, derive_aes_key, generate
from dgp.wordlist import load_bip39_english


def test_base58_lsb_first():
    # n=58: divmod(58,58)=(1,0) → BASE58[0]='1', divmod(1,58)=(0,1) → BASE58[1]='2'
    # LSB-first means first char represents 58^0 digit (0), second char the 58^1 digit (1)
    result = _to_base58(bytes([58]))
    assert result == "12"
    assert result[0] == BASE58_ALPHABET[0]  # LSB digit (0) first


def test_grab_alnum_ascii_only():
    # '²' (U+00B2) passes str.isdigit() but fails c.isascii() and c.isdigit()
    # Patching _to_base58 to return a string with a non-ASCII digit in the first window:
    #   "aA²aA3bcdef" — window "aA²" has upper+lower+unicode-digit but no ASCII digit
    #   window "aA3" is the first qualifying window under ASCII-only classification
    crafted = "aA²aA3bcdef"
    with patch("dgp.engine._to_base58", return_value=crafted):
        result = _grab_alnum(b"dummy", 3)
    assert result == "aA3"  # would be "aA²" if Unicode-aware isdigit() were used


def test_xkcd_early_zero_termination():
    # bytes([1]) → n=1; divmod(1,2048)=(0,1) → words[1]="ability", n=0 → loop exits
    # count=4 but only 1 word is emitted because BigInt hit zero
    words = load_bip39_english()
    result = _to_xkcd(bytes([1]), 4, words)
    expected = words[1][0].upper() + words[1][1:]  # "Ability"
    assert result == expected


def test_generate_first_vector():
    assert generate("a", "aa", "alnum", "") == "oxToKKV2"


def test_derive_aes_key_length():
    assert len(derive_aes_key("a", "aa", "")) == 32
