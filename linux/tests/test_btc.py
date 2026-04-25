import hashlib
import pytest

from dgp.btc import (
    derive_secp256k1_priv,
    wif_compressed_mainnet,
    bech32_p2wpkh_mainnet,
    derive_bip39_mnemonic,
    base58check_decode,
    bech32_decode,
    _sha256,
)
from coincurve import PrivateKey

# ── Vector tests ──────────────────────────────────────────────────────────────

VECTORS = [
    (
        "myseed", "github", "myaccount",
        "KxY4BWHyP13685Sviqf4rdy2niT9QBkAJ976XdZnALUVWnAaAsqn",
        "bc1q8gkhaql4yc3lq4kn9rrw608m2l62u0s8t23yyu",
    ),
    (
        "anotherseed", "service.example.com", "",
        "KwpdKhVzX2Y21HYMN2ByhVCz3Y3QdH9bqUFf832R9WDvd7BKqUn9",
        "bc1q06cry0352evnjh5ftw5rwyqwtg583zdjxa70z9",
    ),
    (
        "btcseed", "wallet", "user1",
        "KySr57gSipuvJ8d7DitKAKadvrqA5pnmi1idDzMiTFQBmiJfgTyu",
        "bc1qsmeflgne3tzsljhwp4rvvw6qxap0cnkz0h4crr",
    ),
]


@pytest.mark.parametrize("seed,name,account,exp_wif,exp_addr", VECTORS)
def test_btc_vector_wif(seed, name, account, exp_wif, exp_addr):
    priv = derive_secp256k1_priv(seed, name, account)
    assert wif_compressed_mainnet(priv) == exp_wif


@pytest.mark.parametrize("seed,name,account,exp_wif,exp_addr", VECTORS)
def test_btc_vector_addr(seed, name, account, exp_wif, exp_addr):
    priv = derive_secp256k1_priv(seed, name, account)
    pub = PrivateKey(priv).public_key.format(compressed=True)
    assert bech32_p2wpkh_mainnet(pub) == exp_addr


@pytest.mark.parametrize("seed,name,account,exp_wif,exp_addr", VECTORS)
def test_btc_deterministic(seed, name, account, exp_wif, exp_addr):
    priv1 = derive_secp256k1_priv(seed, name, account)
    priv2 = derive_secp256k1_priv(seed, name, account)
    assert priv1 == priv2


# ── WIF round-trip ────────────────────────────────────────────────────────────

def test_wif_round_trip():
    priv = derive_secp256k1_priv("myseed", "github", "myaccount")
    wif = wif_compressed_mainnet(priv)

    # Decode base58check
    payload = base58check_decode(wif)
    # Verify structure: 0x80 prefix + 32 bytes + 0x01 compressed flag
    assert payload[0:1] == b'\x80'
    assert payload[-1:] == b'\x01'
    recovered = payload[1:-1]
    assert len(recovered) == 32
    assert recovered == priv


# ── Bech32 sanity (BIP-173 reference decode) ──────────────────────────────────

def test_bech32_decode_bip173_reference():
    # BIP-173 test vector: BC1QW508D6QEJXTDG4Y5R3ZARVARY0C5XW7KV8F3T4
    # Witness program derived by decoding — verified by round-trip encode.
    hrp, witver, prog = bech32_decode("BC1QW508D6QEJXTDG4Y5R3ZARVARY0C5XW7KV8F3T4")
    assert hrp == "bc"
    assert witver == 0
    assert len(prog) == 20
    assert prog == bytes.fromhex("751e76e8199196d454941c45d1b3a323f1433bd6")


# ── BIP-39 mnemonic vectors ───────────────────────────────────────────────────

MNEMONIC_VECTORS = [
    (
        "myseed", "github", "myaccount",
        "chef crash spoon warm thank glue broken lake capable fade avoid must "
        "black vendor orange atom tomato matter benefit muscle manual trash gorilla rubber",
    ),
    (
        "anotherseed", "service.example.com", "",
        "ball raw snack salon spot truth scene parrot carpet sibling goat piece "
        "cat bring smooth double family large engine throw alone rural session board",
    ),
    (
        "btcseed", "wallet", "user1",
        "draw interest candy output shock swear claw accident fix news joke sister "
        "alarm web tackle camp already fatigue crowd crisp enforce welcome tooth knock",
    ),
]


@pytest.mark.parametrize("seed,name,account,exp_words", MNEMONIC_VECTORS)
def test_bip39_mnemonic_vector(seed, name, account, exp_words):
    words = derive_bip39_mnemonic(seed, name, account)
    assert len(words) == 24
    assert " ".join(words) == exp_words


@pytest.mark.parametrize("seed,name,account,exp_words", MNEMONIC_VECTORS)
def test_bip39_mnemonic_deterministic(seed, name, account, exp_words):
    words1 = derive_bip39_mnemonic(seed, name, account)
    words2 = derive_bip39_mnemonic(seed, name, account)
    assert words1 == words2
