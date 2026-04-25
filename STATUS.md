DONE

**Current phase:** Phase 8 — End-to-end verification + patch export (COMPLETE)
**Last green commit:** d66a1ed

## Overall progress

- [x] Phase 1 — `AutoFocus.kt` helper (no call sites yet)
- [x] Phase 2 — UnlockScreen focus + IME
- [x] Phase 3 — EditEntryScreen focus race fix
- [x] Phase 4 — Visibility toggle touch target (IconButton)
- [x] Phase 5 — RevealSheet press-and-hold + haptics
- [x] Phase 6 — ActiveModal sealed class + JVM tests
- [x] Phase 7 — MainActivity modal refactor
- [x] Phase 8 — End-to-end verification + patch export

## Completed

- Phase 1: Created `app/src/main/java/com/dgp/ui/components/AutoFocus.kt` with `Modifier.autoFocus(delayMillis: Long = 100)` using `composed { … }`. No existing files modified.
- Phase 2: Edited `UnlockScreen.kt` — added `.autoFocus()` to seed field modifier chain, changed `windowInsetsPadding` to include `WindowInsets.ime` via `.union()`. Added two new instrumentation tests to `MainActivityTest.kt`. All JVM + instrumentation tests pass.
- Phase 3: Removed the `clearFocus(force=true)` + `requestFocus()` + `keyboardController.show()` triple (and orphan comment + its `LaunchedEffect(Unit)`) from `EditEntryScreen.kt`. Replaced `.focusRequester(focusRequester)` with `.autoFocus()` on the name field. Removed 4 dead imports, added `autoFocus` import. All JVM + instrumentation tests pass.
- Phase 4: Wrapped seed-visibility toggle in `IconButton` in `UnlockScreen.kt`. Added `import androidx.compose.material3.IconButton` and `import androidx.compose.ui.semantics.contentDescription`. `contentDescription` moved to `IconButton`'s `Modifier.semantics { }` block; inner `Icon` is now decorative (`contentDescription = null`). Added `unlockScreen_visibilityToggle_meetsMinTouchTarget` test to `MainActivityTest.kt`.
- Phase 5: Added 150ms press-and-hold gate with haptics to RevealSheet. Changed `RevealSheetContent` from `private` to `internal`. Added `testTag="reveal-target"` to password Box. Created `RevealSheetTest.kt` with 2 instrumentation tests.
- Phase 6: Created `app/src/main/java/com/dgp/ui/ActiveModal.kt` with all 9 sealed subtypes. Created `app/src/test/java/com/dgp/ui/ActiveModalTest.kt` with 2 passing JVM tests.
- Phase 7: Replaced 10 independent modal `mutableStateOf` flags in `MainActivity.kt` with a single `var activeModal: ActiveModal`. Removed `showExportPinDialog`, `showImportPinDialog`, `importEncryptedFileUri`, `showAccountPrompt`, `showAddDialog`, `addDialogInitialName`, `editingService`, `showSeedSettings`, `showTestVectors`, `revealingService`. Replaced seven `if`/`?.let` modal display blocks with one `when (val m = activeModal)`. All 242 tests pass (JVM + instrumentation). Commit: d66a1ed.

## In Progress

- (nothing in-flight)

## Next

- Phase 8: End-to-end verification + patch export.

## Notes

- `WindowInsets.union()` requires explicit import `import androidx.compose.foundation.layout.union` (see PROBLEMS.md).
- `contentDescription` in `Modifier.semantics { }` requires `import androidx.compose.ui.semantics.contentDescription`.
- Environment: JDK 25 at `/usr/lib/jvm/java-25-openjdk-amd64`. `run_tests.sh` overrides gradle.properties JDK path.
- `assertTouchWidthIsEqualTo` / `assertTouchHeightIsEqualTo` resolve fine with compose-bom 2024.10.01.
- `assertDoesNotExist()` is a member/extension on `SemanticsNodeInteraction` — do NOT import it explicitly.
- Existing JVM tests use `org.junit.Assert.*` assertion style (see `ServiceParsingTest.kt`).
- **Real test counts** (verified via JUnit XML): 70 JVM tests across 4 suites + 68 instrumentation tests = 138 green. Gradle stdout may show inflated numbers.
