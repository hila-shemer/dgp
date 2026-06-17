# Agentic DGP — Phase 1: Cap-Token Primitive Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a one-way `deriveSubaccountSeed(seed, account, label)` primitive that renders a 24-word `xkcdlonglong` cap-token, to both the Linux (Python) and Android (Kotlin) engines, proven byte-for-byte identical by shared golden vectors.

**Architecture:** Reuse the existing PBKDF2-HMAC-SHA1 path (42 000 iters, 40-byte output) with a reserved, label-varying salt (`dgp-subaccount:v1:<label>`) that domain-separates cap-tokens from password derivation (which salts with the service name) — exactly mirroring the existing `dgp-flag-fp:v1` fingerprint pattern. The 40 output bytes render to a fixed-length 24-word CamelCase phrase via a low-order-word-first divmod-by-2048 loop, identical in both languages. Purely additive: existing password test vectors are untouched (invariant #1).

**Tech Stack:** Python 3.11+ (`cryptography` PBKDF2, `pytest`); Kotlin (`javax.crypto` PBKDF2, JUnit4). Spec: `docs/superpowers/specs/2026-06-17-agentic-dgp-key-derivation-design.md` §2.

**Repo policy reminder:** every real commit message must end with the trailer
`Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>` (the `-m` subjects below omit it for brevity — add it when committing).

---

## File structure

- **Modify** `linux/dgp/engine.py` — add `SUBACCOUNT_SALT_PREFIX`, `SUBACCOUNT_WORDS`, `_render_word_phrase()`, `derive_subaccount_seed()`. (Engine module; one clear responsibility — derivation.)
- **Create** `linux/tests/test_subaccount.py` — property tests + golden-vector parity test (Python side).
- **Modify** `app/src/main/java/com/dgp/engine/DgpEngine.kt` — add companion `SUBACCOUNT_SALT_PREFIX` / `SUBACCOUNT_WORDS`, instance `deriveSubaccountSeed()`, private `renderWordPhrase()`.
- **Modify** `app/src/test/java/com/dgp/engine/DgpEngineTest.kt` — add a "── Subaccount cap-token ──" section: property tests + the SAME golden vectors (the cross-language parity anchor).

CLI commands (`dgp subaccount mint/provision`) are **Phase 2**, not here. This phase is engine + tests only.

---

## Task 1: Python cap-token primitive (property-tested)

**Files:**
- Modify: `linux/dgp/engine.py`
- Test: `linux/tests/test_subaccount.py` (create)

- [ ] **Step 1: Write the failing property tests**

Create `linux/tests/test_subaccount.py`:

```python
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd linux && pytest tests/test_subaccount.py -q`
Expected: FAIL — `ImportError: cannot import name 'derive_subaccount_seed' from 'dgp.engine'`.

- [ ] **Step 3: Implement the primitive**

In `linux/dgp/engine.py`, add after the existing `_to_xkcd` function (and before `generate`):

```python
SUBACCOUNT_SALT_PREFIX = "dgp-subaccount:v1:"
SUBACCOUNT_WORDS = 24


def _render_word_phrase(data: bytes, count: int, words: list[str]) -> str:
    """Fixed-length CamelCase word phrase: exactly `count` words, low-order word
    first (divmod by 2048). Unlike _to_xkcd this never stops early — once the
    integer is exhausted, remaining positions yield words[0] — so the output is
    always `count` words and matches the Kotlin renderer byte-for-byte."""
    n = int.from_bytes(data, "big")
    result = []
    for _ in range(count):
        n, mod = divmod(n, 2048)
        word = words[mod]
        result.append(word[0].upper() + word[1:])
    return "".join(result)


def derive_subaccount_seed(seed: str, account: str, label: str) -> str:
    """One-way 24-word cap-token for `label` under the (seed, account) identity.
    Domain-separated from password derivation via a reserved salt prefix; PBKDF2
    is one-way, so the holder of a cap-token cannot recover seed/account."""
    raw = pbkdf2_raw(seed, account, SUBACCOUNT_SALT_PREFIX + label)
    return _render_word_phrase(raw, SUBACCOUNT_WORDS, _get_words())
```

(`pbkdf2_raw` defaults to `iters=42000, dklen=40`, so `raw` is 40 bytes — ample for 24 words ≈ 264 bits.)

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd linux && pytest tests/test_subaccount.py -q`
Expected: PASS (8 passed).

- [ ] **Step 5: Commit**

```bash
git add linux/dgp/engine.py linux/tests/test_subaccount.py
git commit -m "feat(engine): one-way subaccount cap-token (24-word xkcdlonglong)"
```

---

## Task 2: Lock the Python golden vectors

These three strings become the cross-language parity anchor — Kotlin must reproduce them exactly in Task 4.

**Files:**
- Test: `linux/tests/test_subaccount.py`

- [ ] **Step 1: Generate the golden values**

Run (requires `pip install -e linux` already done):

```bash
cd linux && python -c "
from dgp.engine import derive_subaccount_seed as d
S = 'correct horse battery staple'
print('case1', d(S, '', 'agent-bob'))
print('case2', d(S, 'alice@example.com', 'agent-bob'))
print('case3', d(S, '', 'agent-alice'))
"
```

Record the three printed phrases verbatim — call them `<CASE1>`, `<CASE2>`, `<CASE3>`. (They are deterministic; the same three values must be pasted into both this task and Task 4.)

- [ ] **Step 2: Add the golden-vector test**

Append to `linux/tests/test_subaccount.py`, substituting the recorded values:

```python
# Golden cap-token vectors. Generated once from the Python reference and locked;
# the Kotlin engine must reproduce these exactly (see DgpEngineTest). Editing the
# derivation must re-green BOTH suites or it is a parity break.
GOLDEN = [
    ("correct horse battery staple", "",                  "agent-bob",   "<CASE1>"),
    ("correct horse battery staple", "alice@example.com", "agent-bob",   "<CASE2>"),
    ("correct horse battery staple", "",                  "agent-alice", "<CASE3>"),
]


@pytest.mark.parametrize("seed,account,label,expected", GOLDEN)
def test_golden_vectors(seed, account, label, expected):
    assert derive_subaccount_seed(seed, account, label) == expected
```

- [ ] **Step 3: Run to verify it passes**

Run: `cd linux && pytest tests/test_subaccount.py -q`
Expected: PASS (11 passed).

- [ ] **Step 4: Commit**

```bash
git add linux/tests/test_subaccount.py
git commit -m "test(engine): lock golden cap-token vectors (Python reference)"
```

---

## Task 3: Kotlin cap-token primitive (property-tested)

**Files:**
- Modify: `app/src/main/java/com/dgp/engine/DgpEngine.kt`
- Test: `app/src/test/java/com/dgp/engine/DgpEngineTest.kt`

- [ ] **Step 1: Write the failing property tests**

In `DgpEngineTest.kt`, add this section just before the closing brace of the class (after the flag-fingerprint tests):

```kotlin
    // ── Subaccount cap-token ──────────────────────────────────────────────────

    private fun splitWords(phrase: String): List<String> =
        Regex("[A-Z][a-z]*").findAll(phrase).map { it.value.lowercase() }.toList()

    @Test
    fun subaccount_isDeterministic() {
        val a = engine.deriveSubaccountSeed("correct horse", "alice", "agent-bob")
        val b = engine.deriveSubaccountSeed("correct horse", "alice", "agent-bob")
        assertEquals(a, b)
    }

    @Test
    fun subaccount_has24Bip39Words() {
        val phrase = engine.deriveSubaccountSeed("correct horse", "", "agent-bob")
        assertEquals(DgpEngine.SUBACCOUNT_WORDS, phrase.count { it.isUpperCase() })
        assertEquals(24, DgpEngine.SUBACCOUNT_WORDS)
        val words = splitWords(phrase)
        assertEquals(24, words.size)
        for (w in words) assertTrue("'$w' should be a BIP-39 word", w in fpWordList)
    }

    @Test
    fun subaccount_sensitiveToSeedAccountLabel() {
        val base = engine.deriveSubaccountSeed("correct horse", "", "x")
        assertNotEquals(base, engine.deriveSubaccountSeed("other seed", "", "x"))
        assertNotEquals(base, engine.deriveSubaccountSeed("correct horse", "alice", "x"))
        assertNotEquals(base, engine.deriveSubaccountSeed("correct horse", "", "y"))
    }

    @Test
    fun subaccount_emptyAndLongLabels_doNotThrow() {
        assertEquals(24, splitWords(engine.deriveSubaccountSeed("s", "", "")).size)
        assertEquals(24, splitWords(engine.deriveSubaccountSeed("s", "", "L".repeat(200))).size)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.dgp.engine.DgpEngineTest`
Expected: FAIL — compilation error: unresolved reference `deriveSubaccountSeed` / `SUBACCOUNT_WORDS`.

- [ ] **Step 3: Implement the primitive**

In `DgpEngine.kt`, add to the `companion object` (next to `FINGERPRINT_SALT`):

```kotlin
        /** Reserved salt prefix domain-separating subaccount cap-tokens from every
         *  password derivation (which salt with the service name). */
        private const val SUBACCOUNT_SALT_PREFIX = "dgp-subaccount:v1:"

        /** Cap-token length in BIP-39 words (~264 bits — seed-grade). */
        const val SUBACCOUNT_WORDS = 24
```

Then add these to the class body (e.g. just after `deriveFingerprintBytes`):

```kotlin
    /**
     * One-way 24-word cap-token for [label] under the (seed, account) identity.
     * Domain-separated from password derivation via a reserved salt prefix; PBKDF2
     * is one-way, so a cap-token holder cannot recover seed/account. The output is
     * a seed-grade word phrase an agent can use as its own DGP seed.
     */
    fun deriveSubaccountSeed(seed: String, account: String, label: String): String {
        val salt = (SUBACCOUNT_SALT_PREFIX + label).toByteArray()
        val keySpec: KeySpec = PBEKeySpec((seed + account).toCharArray(), salt, 42000, 40 * 8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val raw = factory.generateSecret(keySpec).encoded
        return renderWordPhrase(raw, SUBACCOUNT_WORDS)
    }

    /** Fixed-length CamelCase phrase: exactly [count] words, low-order word first
     *  (divmod by 2048). Never stops early — matches Python `_render_word_phrase`
     *  byte-for-byte, so cap-tokens are identical across engines. */
    private fun renderWordPhrase(data: ByteArray, count: Int): String {
        var intData = BigInteger(1, data)
        val wordBn = BigInteger.valueOf(2048)
        val sb = StringBuilder()
        repeat(count) {
            val (div, mod) = intData.divideAndRemainder(wordBn)
            val word = wordList[mod.toInt()]
            sb.append(word.replaceFirstChar { it.uppercase() })
            intData = div
        }
        return sb.toString()
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests com.dgp.engine.DgpEngineTest`
Expected: PASS (existing tests + the 4 new subaccount property tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dgp/engine/DgpEngine.kt app/src/test/java/com/dgp/engine/DgpEngineTest.kt
git commit -m "feat(engine): mirror subaccount cap-token to Kotlin engine"
```

---

## Task 4: Kotlin golden vectors (parity anchor)

Prove byte-for-byte parity: paste the **exact** `<CASE1>`/`<CASE2>`/`<CASE3>` strings recorded in Task 2.

**Files:**
- Test: `app/src/test/java/com/dgp/engine/DgpEngineTest.kt`

- [ ] **Step 1: Add the golden-vector test**

In the "── Subaccount cap-token ──" section of `DgpEngineTest.kt`, add (substituting the Task 2 values):

```kotlin
    @Test
    fun subaccount_goldenVectors_matchPythonReference() {
        // These three strings are generated by the Python reference (test_subaccount.py
        // GOLDEN) and pasted here verbatim. If this fails, the Kotlin and Python
        // engines have diverged — a parity break, not a test bug.
        assertEquals("<CASE1>", engine.deriveSubaccountSeed("correct horse battery staple", "", "agent-bob"))
        assertEquals("<CASE2>", engine.deriveSubaccountSeed("correct horse battery staple", "alice@example.com", "agent-bob"))
        assertEquals("<CASE3>", engine.deriveSubaccountSeed("correct horse battery staple", "", "agent-alice"))
    }
```

- [ ] **Step 2: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.dgp.engine.DgpEngineTest`
Expected: PASS. If it fails, the renderers diverged — recheck `renderWordPhrase` vs `_render_word_phrase` (endianness: `BigInteger(1, data)` == `int.from_bytes(data, "big")`; capitalise-first; exactly 24 iterations).

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/dgp/engine/DgpEngineTest.kt
git commit -m "test(engine): assert Kotlin cap-token parity with Python golden vectors"
```

---

## Task 5: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Run the whole Python suite**

Run: `cd linux && pytest -q`
Expected: PASS (all existing tests + the new `test_subaccount.py`). Confirms the additive change broke no existing password/vault/export vectors.

- [ ] **Step 2: Run the whole JVM suite**

Run: `./gradlew :app:test`
Expected: PASS (existing `DgpEngineTest` vectors still green + new subaccount tests).

- [ ] **Step 3: Run the combined runner**

Run: `./run_tests.sh`
Expected: Python + JVM green (Android instrumentation may skip if no device — that's fine; Phase 1 adds no instrumentation tests).

- [ ] **Step 4: Tick the spec**

No code change. Confirm Phase 1 of the spec (§7 item 1) is satisfied: `deriveSubaccountSeed` exists on both engines, domain-separated, with shared golden vectors. Phase 2 (CLI `subaccount mint/provision` + metadata catalog) is the next plan.

---

## Self-review notes

- **Spec coverage (§2):** §2.1 derivation (reserved salt, seed+account material, 40-byte PBKDF2) → Task 1/3 impl. §2.2 API (`derive_subaccount_seed` / `deriveSubaccountSeed`) → Task 1/3. §2.2 shared vectors covering empty account + empty label + long labels → Task 2 golden (empty account) + Task 1/3 property tests (empty + 200-char labels). §2.4 collision/domain-separation note → `test_domain_separated_from_password_salt`. §2.3 `dgp seed new` is explicitly optional and deferred to Phase 2 (not in scope here).
- **Invariant #1:** change is additive (new function, reserved salt); Task 5 Step 1/2 re-run the existing vector suites to prove passwords are unchanged.
- **Type consistency:** `SUBACCOUNT_WORDS = 24` and `SUBACCOUNT_SALT_PREFIX = "dgp-subaccount:v1:"` are identical strings/values in both engines; renderer named `_render_word_phrase` (Python) / `renderWordPhrase` (Kotlin); golden `<CASE1..3>` are the single shared source of truth.
- **No magic before reference:** golden constants are generated by the Python impl (Task 2 Step 1) and then locked into both suites — the standard way to anchor a deterministic cross-language output.
