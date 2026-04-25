# Decisions

Append-only log of resolved architectural and implementation choices.

---

## Phase 1

- **`Modifier.autoFocus()` uses `composed { … }` not `ModifierNodeElement`**: `composed` is deprecated in latest Compose but is stable in compose-bom 2024.10.01 (Compose 1.7.x). The `ModifierNodeElement` approach is more invasive and the plan does not require it.
- **100 ms default delay**: matches the review recommendation ("after a 100ms delay to ensure the transition has settled") and is the threshold below which `BasicTextField.assertIsFocused()` is unreliable on first frame in compose-bom 2024.10.01.
- **`FocusRequester` is owned internally via `remember` inside `composed`**: callers must not declare one; the whole point is to avoid that boilerplate.
- **No `keyboardController.show()` or `focusManager.clearFocus()` in the helper**: these are caller concerns and are explicitly being *removed* from `EditEntryScreen` in Phase 3.

## Phase 5

- **`RevealSheetContent` widened from `private` to `internal`** so `RevealSheetTest` can drive it via `setContent {}` without going through the full unlock flow.
- **`HapticFeedbackConstants.VIRTUAL_KEY` chosen** (API level 1) — avoids API-30+ constants (`CONFIRM`, `LONG_PRESS`) incompatible with min SDK 26.
- **`testTag = "reveal-target"` added to password Box**; used only by `RevealSheetTest`.
- **`assertDoesNotExist()` must NOT be imported explicitly** — it resolves as a member/extension on `SemanticsNodeInteraction` without an import. `MainActivityTest.kt` uses it the same way. Importing `androidx.compose.ui.test.assertDoesNotExist` causes "Unresolved reference" compile error.

## Phase 6

- **`data object` used for singleton subtypes** (None, ExportPin, ChangeSeed, TestVectors, Account) — Kotlin 1.9+ feature; project uses 1.9.25, so this is safe.
- **JVM test uses `ImportPin(null)` only** — avoids loading `android.net.Uri` class which is unavailable in the JVM test environment. As long as no non-null `Uri` is constructed, the class is never loaded and `NoClassDefFoundError` does not occur.
- **`InputField.kt` audit (Phase 4 carryover)** — no visibility toggle exists there; the file needs no changes.
