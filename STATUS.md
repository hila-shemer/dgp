TASK_DONE

**Current phase:** Phase 7 — MainActivity modal refactor
**Last green commit:** d9c791c

## Overall progress

- [x] Phase 1 — `AutoFocus.kt` helper (no call sites yet)
- [x] Phase 2 — UnlockScreen focus + IME
- [x] Phase 3 — EditEntryScreen focus race fix
- [x] Phase 4 — Visibility toggle touch target (IconButton)
- [x] Phase 5 — RevealSheet press-and-hold + haptics
- [x] Phase 6 — ActiveModal sealed class + JVM tests
- [ ] Phase 7 — MainActivity modal refactor
- [ ] Phase 8 — End-to-end verification + patch export

## Completed

- Phase 1: Created `app/src/main/java/com/dgp/ui/components/AutoFocus.kt` with `Modifier.autoFocus(delayMillis: Long = 100)` using `composed { … }`. No existing files modified.
- Phase 2: Edited `UnlockScreen.kt` — added `.autoFocus()` to seed field modifier chain, changed `windowInsetsPadding` to include `WindowInsets.ime` via `.union()`. Added two new instrumentation tests to `MainActivityTest.kt`. All JVM + instrumentation tests pass.
- Phase 3: Removed the `clearFocus(force=true)` + `requestFocus()` + `keyboardController.show()` triple (and orphan comment + its `LaunchedEffect(Unit)`) from `EditEntryScreen.kt`. Replaced `.focusRequester(focusRequester)` with `.autoFocus()` on the name field. Removed 4 dead imports, added `autoFocus` import. All 174 JVM + 65 instrumentation tests pass.
- Phase 4: Wrapped seed-visibility toggle in `IconButton` in `UnlockScreen.kt`. Added `import androidx.compose.material3.IconButton` and `import androidx.compose.ui.semantics.contentDescription`. `contentDescription` moved to `IconButton`'s `Modifier.semantics { }` block; inner `Icon` is now decorative (`contentDescription = null`). Added `unlockScreen_visibilityToggle_meetsMinTouchTarget` test to `MainActivityTest.kt`. All 240 tests pass (174 JVM + 66 instrumentation).
- Phase 5: Added 150ms press-and-hold gate with haptics to RevealSheet. Changed `RevealSheetContent` from `private` to `internal`. Added `testTag="reveal-target"` to password Box. Created `RevealSheetTest.kt` with 2 instrumentation tests. All 242 tests pass (174 JVM + 68 instrumentation).
- Phase 6: Created `app/src/main/java/com/dgp/ui/ActiveModal.kt` with all 9 sealed subtypes. Created `app/src/test/java/com/dgp/ui/ActiveModalTest.kt` with 2 passing JVM tests. Both new tests confirmed passing via JUnit XML. `ActiveModal` not referenced in `MainActivity.kt`.

## In Progress

- (nothing in-flight)

## Next

- Phase 7: MainActivity modal refactor — replace the independent `mutableStateOf` modal flags in `MainActivity.kt` with a single `var activeModal by remember { mutableStateOf<ActiveModal>(ActiveModal.None) }`. Wire up all nine subtypes.

## Notes

- `WindowInsets.union()` requires explicit import `import androidx.compose.foundation.layout.union` (see PROBLEMS.md).
- `contentDescription` in `Modifier.semantics { }` requires `import androidx.compose.ui.semantics.contentDescription`.
- Environment: JDK 25 at `/usr/lib/jvm/java-25-openjdk-amd64`. `run_tests.sh` overrides gradle.properties JDK path.
- `assertTouchWidthIsEqualTo` / `assertTouchHeightIsEqualTo` resolve fine with compose-bom 2024.10.01.
- `assertDoesNotExist()` is a member/extension on `SemanticsNodeInteraction` — do NOT import it explicitly; it resolves without an import (matches how MainActivityTest.kt uses it).
- Existing JVM tests use `org.junit.Assert.*` assertion style (see `ServiceParsingTest.kt`).
- Gradle test summary may report pre-Phase-6 count — trust JUnit XML in `build/test-results/` as source of truth. ActiveModalTest confirmed 2/2 passing, 0 failures.
