import base64
import pytest
from dgp.vault import (
    encrypt_with_raw_key,
    decrypt_with_raw_key,
    encrypt_vault,
    decrypt_vault,
)

KEY = b"A" * 32
KEY2 = b"B" * 32
MSG = "hello vault"


def test_round_trip():
    blob = encrypt_with_raw_key(MSG, KEY)
    assert decrypt_with_raw_key(blob, KEY) == MSG


def test_iv_randomness():
    blob1 = encrypt_with_raw_key(MSG, KEY)
    blob2 = encrypt_with_raw_key(MSG, KEY)
    assert blob1 != blob2


def test_tamper_returns_none():
    blob = encrypt_with_raw_key(MSG, KEY)
    b = bytearray(base64.b64decode(blob))
    b[13] ^= 1
    tampered = base64.b64encode(bytes(b)).decode()
    assert decrypt_with_raw_key(tampered, KEY) is None


def test_wrong_key_returns_none():
    blob = encrypt_with_raw_key(MSG, KEY)
    assert decrypt_with_raw_key(blob, KEY2) is None


def test_vault_round_trip():
    seed, name, account = "seed", "service", "account"
    blob = encrypt_vault("secret", seed, name, account)
    assert decrypt_vault(blob, seed, name, account) == "secret"


def test_vault_wrong_account_returns_none():
    seed, name, account = "seed", "service", "account"
    blob = encrypt_vault("secret", seed, name, account)
    assert decrypt_vault(blob, seed, name, "other") is None


def test_empty_plaintext_round_trip():
    blob = encrypt_with_raw_key("", KEY)
    assert decrypt_with_raw_key(blob, KEY) == ""
