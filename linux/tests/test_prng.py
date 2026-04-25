import pytest

from dgp.prng import chacha20_zeros

# ── Vector tests ──────────────────────────────────────────────────────────────

VECTORS = [
    ("myseed", "github", "myaccount", 16,
     "353ad6aacfd7a5191e36243c05e3962c"),
    ("anotherseed", "service.example.com", "", 32,
     "44b55b0475c45f39f58c3d826402b6b13dab8f0fab0e0aaaba1195f146a4242a"),
    ("prng_seed", "random_service", "user", 8,
     "b731aea3e9ff3b4b"),
]


@pytest.mark.parametrize("seed,name,account,n,exp_hex", VECTORS)
def test_prng_vector(seed, name, account, n, exp_hex):
    result = chacha20_zeros(seed, name, account, n)
    assert result.hex() == exp_hex


@pytest.mark.parametrize("seed,name,account,n,exp_hex", VECTORS)
def test_prng_deterministic(seed, name, account, n, exp_hex):
    r1 = chacha20_zeros(seed, name, account, n)
    r2 = chacha20_zeros(seed, name, account, n)
    assert r1 == r2


# ── Edge cases ────────────────────────────────────────────────────────────────

def test_prng_zero_raises():
    with pytest.raises(ValueError):
        chacha20_zeros("s", "n", "a", 0)


def test_prng_too_large_raises():
    with pytest.raises(ValueError):
        chacha20_zeros("s", "n", "a", 2**32 + 1)


def test_prng_one_byte():
    result = chacha20_zeros("s", "n", "a", 1)
    assert len(result) == 1


def test_prng_large_accepted():
    # 2**32 bytes is technically valid but impractical; use 64 bytes to verify
    # the function accepts valid sizes without error
    result = chacha20_zeros("s", "n", "a", 64)
    assert len(result) == 64
