import re

import pytest

from dgp.engine import (
    SUBACCOUNT_SALT_PREFIX,
    SUBACCOUNT_WORDS,
    derive_subaccount_seed,
    pbkdf2_raw,
)
from dgp.wordlist import load_bip39_english

WORDS = load_bip39_english()
SEED = "correct horse battery staple"


def _split_words(phrase: str) -> list[str]:
    # CamelCase phrase → list of lowercase words.
    return [w.lower() for w in re.findall(r"[A-Z][a-z]*", phrase)]


def test_is_deterministic():
    a = derive_subaccount_seed(SEED, "alice", "agent-bob")
    b = derive_subaccount_seed(SEED, "alice", "agent-bob")
    assert a == b


def test_has_exactly_24_words():
    phrase = derive_subaccount_seed(SEED, "", "agent-bob")
    # BIP-39 words are lowercase ascii; capitalise-first → exactly one capital/word.
    assert sum(1 for c in phrase if c.isupper()) == SUBACCOUNT_WORDS == 24
    assert len(_split_words(phrase)) == 24


def test_all_tokens_are_bip39_words():
    phrase = derive_subaccount_seed(SEED, "", "agent-bob")
    for w in _split_words(phrase):
        assert w in WORDS, f"{w!r} not in BIP-39 list"


def test_label_sensitive():
    assert derive_subaccount_seed(SEED, "", "a") != derive_subaccount_seed(SEED, "", "b")


def test_account_sensitive():
    assert derive_subaccount_seed(SEED, "", "x") != derive_subaccount_seed(SEED, "alice", "x")


def test_seed_sensitive():
    assert derive_subaccount_seed("s1", "", "x") != derive_subaccount_seed("s2", "", "x")


def test_empty_and_long_labels_do_not_throw():
    assert len(_split_words(derive_subaccount_seed(SEED, "", ""))) == 24
    assert len(_split_words(derive_subaccount_seed(SEED, "", "L" * 200))) == 24


def test_domain_separated_from_password_salt():
    # A subaccount under label "github" must not collide with a password whose
    # service is literally "github": different salts → different PBKDF2 output.
    plain = pbkdf2_raw(SEED, "alice", "github")
    capped = pbkdf2_raw(SEED, "alice", SUBACCOUNT_SALT_PREFIX + "github")
    assert plain != capped
