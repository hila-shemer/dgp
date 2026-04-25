import base64
import pytest
from dgp.exportcrypto import encrypt_export, decrypt_export

# Single short pin to amortize PBKDF2 cost across tests via shared key derivation.
# Note: each call to encrypt_export/decrypt_export still runs PBKDF2 internally.
PIN = "x"
MSG = "export payload"


def test_round_trip():
    assert decrypt_export(encrypt_export(MSG, PIN), PIN) == MSG


def test_two_encryptions_distinct_both_decrypt():
    blob1 = encrypt_export(MSG, PIN)
    blob2 = encrypt_export(MSG, PIN)
    assert blob1 != blob2
    assert decrypt_export(blob1, PIN) == MSG
    assert decrypt_export(blob2, PIN) == MSG


def test_tamper_returns_none():
    blob = encrypt_export(MSG, PIN)
    b = bytearray(base64.b64decode(blob))
    b[13] ^= 1
    tampered = base64.b64encode(bytes(b)).decode()
    assert decrypt_export(tampered, PIN) is None


def test_wrong_pin_returns_none():
    blob = encrypt_export(MSG, PIN)
    assert decrypt_export(blob, "wrong") is None


def test_empty_plaintext():
    assert decrypt_export(encrypt_export("", PIN), PIN) == ""


def test_empty_pin():
    assert decrypt_export(encrypt_export("hello", ""), "") == "hello"
