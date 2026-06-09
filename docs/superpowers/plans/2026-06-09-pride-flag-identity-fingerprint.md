# Pride-flag identity fingerprint — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show a deterministic pride-flag chip + two-word label derived from `seed + account` so the user can confirm at a glance that the active identity is correct; their reference identity is vanity-mined to land on the trans flag.

**Architecture:** A new, domain-separated derivation in `DgpEngine` (separate PBKDF2 salt — password path untouched) produces 32 fingerprint bytes → a two-BIP-39-word label and a flag index. A stored non-secret nonce biases the reference identity onto the trans flag (index 0). The flag swatch renders on the services-list header; the full chip + the "set as my flag" action render live in the account dialog. The nonce lives in a local pref, never in the export payload (preserves Linux wire-compat).

**Tech Stack:** Kotlin, Jetpack Compose / Material3, `PBKDF2WithHmacSHA1` + `SHA-256` (javax.crypto / java.security), JUnit4 (JVM unit tests).

**Spec:** `docs/superpowers/specs/2026-06-09-pride-flag-identity-fingerprint-design.md`

---

## File structure

- `app/src/main/java/com/dgp/engine/DgpEngine.kt` — **modify**: add fingerprint derivation, word, flag-index, and nonce-mining functions + `FlagFingerprint`/companion constants. Password path unchanged.
- `app/src/test/java/com/dgp/engine/DgpEngineTest.kt` — **modify**: add unit tests for the new functions.
- `app/src/main/java/com/dgp/ui/components/FlagFingerprint.kt` — **create**: `PrideFlags` gallery data + `FlagSwatch` and `FlagChip` composables.
- `app/src/main/java/com/dgp/MainActivity.kt` — **modify**: compute/cache `fpBytes` off-thread, hold `flagNonce`, load/clear the `flag_nonce` pref, register action, pass data into `ServicesScreen` and `AccountPromptDialog`.
- `app/src/main/java/com/dgp/ui/ServicesScreen.kt` — **modify**: render the flag swatch in the header avatar spot.

---

## Task 1: Engine — fingerprint derivation + mining (TDD)

**Files:**
- Modify: `app/src/main/java/com/dgp/engine/DgpEngine.kt`
- Test: `app/src/test/java/com/dgp/engine/DgpEngineTest.kt`

- [ ] **Step 1: Write the failing tests**

In `DgpEngineTest.kt`, add `assertFalse` + `assertArrayEquals` to the imports and keep the word list available. At the top of the class, after `private lateinit var engine: DgpEngine`, add:

```kotlin
    private lateinit var fpWordList: List<String>
```

In `setUp()`, after building `wordList`, capture it:

```kotlin
        fpWordList = wordList
```

Update imports block to include:

```kotlin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
```

Then add these tests at the end of the class (before the closing brace):

```kotlin
    // ── Flag fingerprint ──────────────────────────────────────────────────────

    @Test
    fun fingerprintBytes_areDeterministic_and32Bytes() {
        val a = engine.deriveFingerprintBytes("seed-abc", "alice@example.com")
        val b = engine.deriveFingerprintBytes("seed-abc", "alice@example.com")
        assertEquals(32, a.size)
        assertArrayEquals(a, b)
    }

    @Test
    fun fingerprintBytes_differ_whenIdentityDiffers() {
        val base = engine.deriveFingerprintBytes("seed-abc", "alice@example.com")
        assertFalse(base.contentEquals(engine.deriveFingerprintBytes("seed-abc", "alicz@example.com")))
        assertFalse(base.contentEquals(engine.deriveFingerprintBytes("seed-abd", "alice@example.com")))
    }

    @Test
    fun fingerprintBytes_domainSeparatedFromPasswordPath() {
        // Same seed/account, but the aeskey of a normally-named service uses the
        // service name as salt, so it must not equal the fingerprint bytes.
        val fp = engine.deriveFingerprintBytes("seed-abc", "alice@example.com")
        val aes = engine.deriveAesKey("seed-abc", "github", "alice@example.com")
        assertFalse(fp.contentEquals(aes))
    }

    @Test
    fun fingerprintWord_isTwoLowercaseBip39Words() {
        val fp = engine.deriveFingerprintBytes("seed-abc", "alice@example.com")
        val parts = engine.fingerprintWord(fp).split("-")
        assertEquals(2, parts.size)
        for (p in parts) {
            assertEquals(p.lowercase(), p)
            assertTrue("'$p' should be a BIP-39 word", p in fpWordList)
        }
    }

    @Test
    fun flagIndex_isAlwaysInRange() {
        val fp = engine.deriveFingerprintBytes("seed-abc", "alice@example.com")
        for (n in 0..64) {
            val i = engine.flagIndexFor(fp, n, DgpEngine.FLAG_COUNT)
            assertTrue("index $i out of range", i in 0 until DgpEngine.FLAG_COUNT)
        }
    }

    @Test
    fun mineFlagNonce_findsSmallestNonceMappingToTrans() {
        val fp = engine.deriveFingerprintBytes("seed-abc", "alice@example.com")
        val nonce = engine.mineFlagNonce(fp, DgpEngine.TRANS_FLAG_INDEX, DgpEngine.FLAG_COUNT)
        assertEquals(DgpEngine.TRANS_FLAG_INDEX, engine.flagIndexFor(fp, nonce, DgpEngine.FLAG_COUNT))
        for (n in 0 until nonce) {
            assertNotEquals(DgpEngine.TRANS_FLAG_INDEX, engine.flagIndexFor(fp, n, DgpEngine.FLAG_COUNT))
        }
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.dgp.engine.DgpEngineTest`
Expected: FAIL — compilation error, `deriveFingerprintBytes` / `fingerprintWord` / `flagIndexFor` / `mineFlagNonce` / `DgpEngine.FLAG_COUNT` unresolved.

- [ ] **Step 3: Implement the engine functions**

In `DgpEngine.kt`, add the import near the top (with the existing `java.security.spec.KeySpec` import):

```kotlin
import java.security.MessageDigest
```

Add a `companion object` and the new functions inside the `DgpEngine` class (e.g. just after `deriveAesKey`, before `binToHex`):

```kotlin
    companion object {
        /** Number of flags in the gallery (see PrideFlags). Load-bearing: the
         *  flag index maps into that ordered list. */
        const val FLAG_COUNT = 10
        /** Index of the trans flag — the vanity-mining target. */
        const val TRANS_FLAG_INDEX = 0

        // Reserved salt that domain-separates the visual fingerprint from every
        // password / aeskey derivation (those salt with the service name).
        private val FINGERPRINT_SALT = "dgp-flag-fp:v1".toByteArray()
    }

    /**
     * 32 bytes derived from seed+account under a reserved salt. This is the only
     * expensive step (PBKDF2-HMAC-SHA1, same iteration count as password gen).
     * Independent of the password path: callers display only the low-entropy
     * flag/word derived from these bytes, never the bytes themselves.
     */
    fun deriveFingerprintBytes(seed: String, account: String, iterations: Int = 42000): ByteArray {
        val keySpec: KeySpec = PBEKeySpec((seed + account).toCharArray(), FINGERPRINT_SALT, iterations, 32 * 8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return factory.generateSecret(keySpec).encoded
    }

    /** Two lowercase BIP-39 words joined by '-'. Nonce-independent, so the
     *  correct identity always shows the same word. */
    fun fingerprintWord(fpBytes: ByteArray): String {
        var intData = BigInteger(1, fpBytes)
        val wordBn = BigInteger.valueOf(2048)
        val words = mutableListOf<String>()
        repeat(2) {
            val (div, mod) = intData.divideAndRemainder(wordBn)
            words.add(wordList[mod.toInt()])
            intData = div
        }
        return words.joinToString("-")
    }

    /** Flag index = first 4 bytes of SHA-256(fpBytes ‖ nonce-LE) as an unsigned
     *  int, mod flagCount. Fast — so nonce mining is cheap. */
    fun flagIndexFor(fpBytes: ByteArray, nonce: Int, flagCount: Int = FLAG_COUNT): Int {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(fpBytes)
        md.update(byteArrayOf(
            (nonce and 0xFF).toByte(),
            ((nonce ushr 8) and 0xFF).toByte(),
            ((nonce ushr 16) and 0xFF).toByte(),
            ((nonce ushr 24) and 0xFF).toByte(),
        ))
        val h = md.digest()
        val v = ((h[0].toLong() and 0xFF) shl 24) or
            ((h[1].toLong() and 0xFF) shl 16) or
            ((h[2].toLong() and 0xFF) shl 8) or
            (h[3].toLong() and 0xFF)
        return (v % flagCount).toInt()
    }

    /** Smallest nonce >= 0 mapping fpBytes onto targetIndex (~flagCount tries on
     *  average; SHA-256 over the varying nonce is uniform, so it always finds one). */
    fun mineFlagNonce(fpBytes: ByteArray, targetIndex: Int = TRANS_FLAG_INDEX, flagCount: Int = FLAG_COUNT): Int {
        var nonce = 0
        while (flagIndexFor(fpBytes, nonce, flagCount) != targetIndex) nonce++
        return nonce
    }

    /** Convenience: flag index (for a stored nonce) + nonce-independent word. */
    fun fingerprintFor(fpBytes: ByteArray, nonce: Int, flagCount: Int = FLAG_COUNT): FlagFingerprint =
        FlagFingerprint(flagIndexFor(fpBytes, nonce, flagCount), fingerprintWord(fpBytes))
```

Add this data class at the bottom of `DgpEngine.kt`, after the class closing brace:

```kotlin
/** A rendered identity fingerprint: which flag to show + the two-word label. */
data class FlagFingerprint(val flagIndex: Int, val word: String)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests com.dgp.engine.DgpEngineTest`
Expected: PASS (all existing vectors + the 6 new tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dgp/engine/DgpEngine.kt app/src/test/java/com/dgp/engine/DgpEngineTest.kt
git commit -m "feat(engine): add domain-separated flag-fingerprint derivation + nonce mining"
```

---

## Task 2: Pride-flag gallery + chip composables

**Files:**
- Create: `app/src/main/java/com/dgp/ui/components/FlagFingerprint.kt`

- [ ] **Step 1: Create the gallery + composables**

```kotlin
package com.dgp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dgp.ui.theme.editorial
import com.dgp.ui.theme.editorialType

/**
 * The pride-flag gallery. Order is LOAD-BEARING: DgpEngine.flagIndexFor maps into
 * this list, and index 0 (trans) is the vanity-mining target (DgpEngine.TRANS_FLAG_INDEX).
 * Keep the size in sync with DgpEngine.FLAG_COUNT.
 */
object PrideFlags {
    data class Flag(val name: String, val stripes: List<Long>)

    val flags: List<Flag> = listOf(
        Flag("trans", listOf(0xFF5BCEFA, 0xFFF5A9B8, 0xFFFFFFFF, 0xFFF5A9B8, 0xFF5BCEFA)),
        Flag("rainbow", listOf(0xFFE40303, 0xFFFF8C00, 0xFFFFED00, 0xFF008026, 0xFF004DFF, 0xFF750787)),
        Flag("bi", listOf(0xFFD60270, 0xFFD60270, 0xFF9B4F96, 0xFF0038A8, 0xFF0038A8)),
        Flag("pan", listOf(0xFFFF218C, 0xFFFFD800, 0xFF21B1FF)),
        Flag("lesbian", listOf(0xFFD52D00, 0xFFFF9A56, 0xFFFFFFFF, 0xFFD362A4, 0xFFA30262)),
        Flag("nonbinary", listOf(0xFFFCF434, 0xFFFFFFFF, 0xFF9C59D1, 0xFF2C2C2C)),
        Flag("ace", listOf(0xFF000000, 0xFFA3A3A3, 0xFFFFFFFF, 0xFF800080)),
        Flag("genderfluid", listOf(0xFFFF75A2, 0xFFFFFFFF, 0xFFBE18D6, 0xFF000000, 0xFF333EBD)),
        Flag("agender", listOf(0xFF000000, 0xFFBCC4C7, 0xFFFFFFFF, 0xFFB7F684, 0xFFFFFFFF, 0xFFBCC4C7, 0xFF000000)),
        Flag("genderqueer", listOf(0xFFB57EDC, 0xFFFFFFFF, 0xFF4A8123)),
    )
}

/** Equal-height horizontal stripes for one flag. Caller sets size via [modifier]. */
@Composable
fun FlagSwatch(flagIndex: Int, modifier: Modifier = Modifier, cornerDp: Int = 6) {
    val flag = PrideFlags.flags[flagIndex.coerceIn(0, PrideFlags.flags.size - 1)]
    Column(modifier.clip(RoundedCornerShape(cornerDp.dp))) {
        flag.stripes.forEach { c ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(c)),
            )
        }
    }
}

/** Flag swatch + two-word label — the full fingerprint chip used in the account dialog. */
@Composable
fun FlagChip(flagIndex: Int, word: String, modifier: Modifier = Modifier) {
    val editorial = MaterialTheme.editorial
    val type = MaterialTheme.editorialType
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FlagSwatch(flagIndex, Modifier.size(width = 40.dp, height = 28.dp))
        Spacer(Modifier.width(10.dp))
        Text(text = word, style = type.chipLabel, color = editorial.ink)
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dgp/ui/components/FlagFingerprint.kt
git commit -m "feat(ui): add pride-flag gallery + FlagSwatch/FlagChip composables"
```

---

## Task 3: Render the flag swatch on the services-list header

**Files:**
- Modify: `app/src/main/java/com/dgp/ui/ServicesScreen.kt`

- [ ] **Step 1: Add a `flagIndex` parameter**

In `ServicesScreen.kt`, add an import:

```kotlin
import com.dgp.ui.components.FlagSwatch
```

In the `ServicesScreen(...)` parameter list (after `account: String,`), add:

```kotlin
    flagIndex: Int? = null,
```

- [ ] **Step 2: Render the swatch in the avatar Box**

Replace the avatar `Box` body (the block at ServicesScreen.kt:141-153 that draws `Text(text = avatarLetter, ...)`). Keep the same `Box` modifiers (size/clickable/semantics) and swap the *content*:

```kotlin
            val avatarLetter = account.firstOrNull { !it.isWhitespace() }
                ?.uppercaseChar()?.toString() ?: "•"
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(editorial.accent, CircleShape)
                    .clickable(onClick = onOpenAccount)
                    .semantics {
                        contentDescription =
                            if (account.isEmpty()) "Set Account" else "Clear Account"
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (flagIndex != null && account.isNotEmpty()) {
                    FlagSwatch(
                        flagIndex = flagIndex,
                        modifier = Modifier.size(width = 26.dp, height = 18.dp),
                        cornerDp = 4,
                    )
                } else {
                    Text(text = avatarLetter, style = type.chipLabel, color = editorial.accentInk)
                }
            }
```

- [ ] **Step 3: Update the two `@Preview` callers if they exist**

Run: `git grep -n "ServicesScreen(" app/src/main/java/com/dgp/ui/ServicesScreen.kt`
For any `@Preview` composable in this file that calls `ServicesScreen(`, leave `flagIndex` unset (it defaults to `null`). No change needed unless a preview fails to compile.

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (the new param has a default, so the MainActivity call site still compiles until Task 4 wires it).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dgp/ui/ServicesScreen.kt
git commit -m "feat(ui): render flag swatch in the services-list header avatar"
```

---

## Task 4: Wire fingerprint state into MainActivity

**Files:**
- Modify: `app/src/main/java/com/dgp/MainActivity.kt`

- [ ] **Step 1: Add fingerprint state + recompute effect**

In `DgpApp`, just after `var services by remember { mutableStateOf(listOf<DgpService>()) }` (≈ line 268), add:

```kotlin
    // Visual flag fingerprint of seed+account. fpBytes is the one expensive
    // derivation; recompute off the main thread whenever seed or account changes.
    var fpBytes by remember { mutableStateOf<ByteArray?>(null) }
    var flagNonce by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(masterSeed, account, isSeeded) {
        fpBytes = if (isSeeded && masterSeed.isNotEmpty()) {
            withContext(Dispatchers.Default) { engine.deriveFingerprintBytes(masterSeed, account) }
        } else {
            null
        }
    }

    val headerFlagIndex: Int? = fpBytes
        ?.takeIf { account.isNotEmpty() }
        ?.let { engine.flagIndexFor(it, flagNonce ?: 0) }
```

- [ ] **Step 2: Load the nonce on unlock**

In `unlockWithSeed(...)`, inside the `if (loadServices(seed)) { ... }` block, after the account-decrypt lines (after the `if (!decryptedAccount.isNullOrEmpty()) { account = ... } else { ... }` block), add:

```kotlin
            flagNonce = prefs.getInt("flag_nonce", -1).takeIf { it >= 0 }
```

- [ ] **Step 3: Clear the nonce on lock, seed change, and reset**

(a) In **both** lock handlers — the Settings `onLock` (≈ line 726-728) and the ServicesScreen `onLock` (≈ line 760-762) — next to the existing `account = ""` line, add:

```kotlin
                    flagNonce = null
                    fpBytes = null
```

(b) In the `onResetConfig` handler in the `UnlockScreen(...)` call (the `prefs.edit().remove(...)` chain ≈ line 639-644), add a remove:

```kotlin
                        .remove("flag_nonce")
```

(c) In the `ChangeSeed` modal's `onSave` (≈ line 887-895), after `masterSeed = newSeed`, add:

```kotlin
                    prefs.edit().remove("flag_nonce").apply()
                    flagNonce = null
```

- [ ] **Step 4: Pass the flag index to `ServicesScreen`**

In the `ServicesScreen(...)` call (≈ line 736), add the argument right after `account = account,`:

```kotlin
                flagIndex = headerFlagIndex,
```

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/dgp/MainActivity.kt
git commit -m "feat: compute+cache flag fingerprint, load/clear flag_nonce, feed header"
```

---

## Task 5: Live chip + "set as my flag" in the account dialog

**Files:**
- Modify: `app/src/main/java/com/dgp/MainActivity.kt`

- [ ] **Step 1: Extend `AccountPromptDialog`**

Replace the `AccountPromptDialog(...)` definition — **including its `@Composable` annotation line** (≈ line 958-998) — with this version. It adds `seed`, `engine`, `nonce`, `registered`, an `initial` value, and `onSetAsFlag`, and renders the live chip + a "make this my trans flag" action. The live derivation is debounced and off-thread.

```kotlin
@Composable
fun AccountPromptDialog(
    seed: String,
    engine: DgpEngine,
    nonce: Int?,
    registered: Boolean,
    initial: String = "",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onSetAsFlag: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    var visible by remember { mutableStateOf(false) }
    var fp by remember { mutableStateOf<FlagFingerprint?>(null) }

    // Debounced, off-main-thread live fingerprint of the typed account.
    LaunchedEffect(value) {
        if (value.isEmpty()) { fp = null; return@LaunchedEffect }
        fp = null
        delay(250)
        val bytes = withContext(Dispatchers.Default) { engine.deriveFingerprintBytes(seed, value) }
        fp = engine.fingerprintFor(bytes, nonce ?: 0)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Account") },
        text = {
            Column {
                TextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Account") },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { visible = !visible },
                            modifier = Modifier.semantics {
                                contentDescription = if (visible) "Hide password" else "Show password"
                            },
                        ) {
                            Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null)
                        }
                    },
                    singleLine = true,
                )
                Spacer(Modifier.height(16.dp))
                when {
                    value.isEmpty() -> Text("Type your account to see its flag.")
                    fp == null -> Text("Deriving flag…")
                    else -> {
                        FlagChip(flagIndex = fp!!.flagIndex, word = fp!!.word)
                        Spacer(Modifier.height(8.dp))
                        if (!registered || fp!!.flagIndex != DgpEngine.TRANS_FLAG_INDEX) {
                            TextButton(onClick = { if (value.isNotEmpty()) onSetAsFlag(value) }) {
                                Text("This is correct — make it my 🏳️‍⚧️ flag")
                            }
                        } else {
                            Text("✓ shows your trans flag")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (value.isNotEmpty()) onSave(value) },
                enabled = value.isNotEmpty(),
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Skip") }
        },
    )
}
```

Add any missing imports at the top of `MainActivity.kt`:

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.dgp.engine.FlagFingerprint
import com.dgp.ui.components.FlagChip
```

(Note: `Modifier`, `Text`, `Button`, `TextButton`, `Icon`, `IconButton`, `AlertDialog`, `TextField`, `Column`-friends — confirm each is imported; add the ones the compiler flags.)

- [ ] **Step 2: Update the call site**

Replace the `AccountPromptDialog(...)` call inside `is ActiveModal.Account ->` (≈ line 782-794) with:

```kotlin
        is ActiveModal.Account -> {
            if (isSeeded) {
                AccountPromptDialog(
                    seed = masterSeed,
                    engine = engine,
                    nonce = flagNonce,
                    registered = flagNonce != null,
                    initial = account,
                    onDismiss = { activeModal = ActiveModal.None },
                    onSave = { newAccount ->
                        account = newAccount
                        if (masterSeed.isNotEmpty()) {
                            prefs.edit().putString("account_encrypted", ConfigCrypto.encrypt(newAccount, masterSeed)).apply()
                        }
                        activeModal = ActiveModal.None
                    },
                    onSetAsFlag = { newAccount ->
                        account = newAccount
                        if (masterSeed.isNotEmpty()) {
                            prefs.edit().putString("account_encrypted", ConfigCrypto.encrypt(newAccount, masterSeed)).apply()
                        }
                        scope.launch {
                            val bytes = withContext(Dispatchers.Default) { engine.deriveFingerprintBytes(masterSeed, newAccount) }
                            fpBytes = bytes
                            val n = engine.mineFlagNonce(bytes)
                            flagNonce = n
                            prefs.edit().putInt("flag_nonce", n).apply()
                        }
                        activeModal = ActiveModal.None
                    },
                )
            }
        }
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run the full JVM test suite (no regressions)**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dgp/MainActivity.kt
git commit -m "feat(ui): live flag chip + 'set as my flag' in the account dialog"
```

---

## Task 6: Build release APK, install in place, verify on device

**Files:** none (build + manual verification)

- [ ] **Step 1: Build the release APK**

Run: `./gradlew assembleRelease`
Expected: BUILD SUCCESSFUL; APK at `app/build/outputs/apk/release/app-release.apk`.

- [ ] **Step 2: Install in place over the early-access app**

Run: `adb -s 3C011FDJG0058S install -r app/build/outputs/apk/release/app-release.apk`
Expected: `Success`.

If it fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` / signature mismatch (Play App Signing is on), fall back: add `applicationIdSuffix ".debug"` under a `debug { }` block in `app/build.gradle` `buildTypes`, then `./gradlew installDebug` to install side-by-side, and import the config (or set a test identity) before verifying. **Do not** uninstall the release app without confirming with the user.

- [ ] **Step 3: Manual verification checklist**

- [ ] Unlock with the real seed and account → the header avatar shows a flag swatch.
- [ ] Open the account dialog → the live chip (flag + two-word label) renders; tap "make it my 🏳️‍⚧️ flag".
- [ ] After registering, the header swatch and the dialog chip both show the **trans** flag, and the dialog shows "✓ shows your trans flag".
- [ ] Re-open the account dialog and type a wrong account → the chip changes to a different flag and a different word.
- [ ] Restore the correct account → trans flag returns.
- [ ] Lock and unlock → the registered trans flag is still shown (nonce persisted).

- [ ] **Step 4: Commit any fallback build change (only if Step 2 fell back)**

```bash
git add app/build.gradle
git commit -m "build: add .debug applicationIdSuffix for side-by-side test installs"
```

---

## Notes & caveats

- **Header shows the swatch only; the word lives in the account dialog.** The 28 dp header element is too small for the word. If you want the two-word label visible on the list header too, that's a small follow-up (a `FlagChip` in the header row) — raise it with the user.
- **Mining loop** in `mineFlagNonce` is unbounded by construction but terminates in ~`FLAG_COUNT` iterations on average (uniform SHA-256). No cap needed.
- **`onOpenAccount` clears a set account** before opening the dialog (existing behavior, MainActivity.kt:766). The dialog is seeded with `initial = account`, but after a clear that's empty — unchanged from today's UX.
- **Linux port untouched** — fingerprint is Android-only and not part of the password/export compat contract. `flag_nonce` is never written into the export payload.
```
