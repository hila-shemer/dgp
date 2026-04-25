# UI/UX Review & Refinement Strategy

This document outlines "surprising" behaviors identified during the UI code review of the DGP Android application. It provides technical strategies for remediation and a comprehensive testing plan to ensure a polished, professional experience.

---

## 1. Focus Management & Keyboard UX

### **The Issue: Inconsistent Auto-Focus**
*   **Location:** `UnlockScreen.kt`, `EditEntryScreen.kt`.
*   **Behavior:** On the `UnlockScreen`, the user is presented with a seed input, but the keyboard does not open automatically. This requires an extra tap on every app launch. In `EditEntryScreen`, `focusManager.clearFocus()` and `focusRequester.requestFocus()` are called in the same `LaunchedEffect` block, which can cause race conditions in the Compose focus system.
*   **Impact:** Friction in the critical "Time-to-Unlock" path and potential keyboard flickering.

### **Proposed Fix**
1.  **UnlockScreen:** Add a `FocusRequester` and a `LaunchedEffect(Unit)` that calls `requestFocus()` after a 100ms delay (to ensure the transition has settled).
2.  **Consolidation:** Create a `Modifier.autoFocus()` extension that handles the `FocusRequester` and `LaunchedEffect` boilerplate consistently across the app.
3.  **Keyboard Strategy:** Use `WindowInsets.ime` to detect keyboard visibility and ensure the "Unlock" button is not obscured by the keyboard on smaller devices.

### **Verification Strategy**
*   **Test Type:** Compose Instrumentation Test.
*   **Test Case:** `test_unlockScreen_requestsFocusOnLaunch`.
*   **Assertion:** `composeTestRule.onNodeWithTag("seed-input").assertIsFocused()`.
*   **Assertion:** `composeTestRule.onNodeWithTag("unlock-button").assertIsDisplayed()` (to verify it's not obscured or pushed off-screen).

---

## 2. Gestural Ambiguity & Interaction

### **The Issue: "Press-and-Hold" vs. Bottom Sheet Scroll**
*   **Location:** `RevealSheet.kt`.
*   **Behavior:** The password reveal box uses `detectTapGestures(onPress = ...)`. Since this box is located inside a `ModalBottomSheet`, there is a conflict between the user's intent to scroll the sheet and their intent to reveal the password.
*   **Impact:** Users may accidentally trigger a "reveal" while trying to drag the sheet up, or the sheet may feel "stuck" when swiping over the password area.

### **Proposed Fix**
1.  **Delay Trigger:** Implement a slight delay (e.g., 150ms) in the `onPress` logic to ensure the gesture isn't a scroll.
2.  **Haptic Feedback:** Use `LocalView.current.performHapticFeedback` to trigger a light vibration when the reveal begins and ends, providing physical confirmation to the user.
3.  **Alternative:** Replace the press-and-hold with a "Hold to Reveal" button that has a larger, more explicit touch target separate from the primary scrollable surface.

### **Verification Strategy**
*   **Test Type:** Compose Instrumentation Test.
*   **Test Case:** `test_revealSheet_pressAndHold_triggersReveal`.
*   **Action:** `performTouchInput { down(center); advanceEventTime(200); up() }`.
*   **Assertion:** Check if the masked text changes to the real password during the `down` state.

---

## 3. Navigation & Screen Transitions

### **The Issue: Disorienting "Hard Cuts"**
*   **Location:** `MainActivity.kt` (switching between `ServicesScreen` and `ReorderScreen`).
*   **Behavior:** Entering "Reorder" mode immediately replaces the entire screen content. There is no visual continuity between the row the user long-pressed and its new state in the reorder list.
*   **Impact:** Disorienting for users with many services; they "lose their place" in the list.

### **Proposed Fix**
1.  **Shared Element Transitions:** Use `AnimatedContent` or the new `SharedTransitionLayout` (available in Compose 1.7+) to animate the transition between the main list and the reorder mode.
2.  **In-Place Reordering:** Instead of a full-screen switch, transform the existing `ServicesScreen` into a reorderable state using the same `LazyColumn`. This maintains scroll position and context.

### **Verification Strategy**
*   **Test Type:** Visual/Manual Regression.
*   **Test Case:** Verify that the scroll position of the `ServicesScreen` is maintained when entering and exiting `ReorderScreen`.

---

## 4. Accessibility & Touch Targets

### **The Issue: Small Touch Targets for Critical Toggles**
*   **Location:** `InputField.kt`, `UnlockScreen.kt`.
*   **Behavior:** Visibility toggles (the "eye" icons) are implemented using `Modifier.clickable` directly on the `Icon` (20dp–24dp).
*   **Impact:** Users with limited motor control or large fingers may find these toggles difficult to hit, leading to frustration when trying to view a password.

### **Proposed Fix**
1.  **Standard Components:** Replace all `Modifier.clickable` on icons with standard `IconButton`.
2.  **Min Target Size:** Ensure all interactive elements meet the Android accessibility guideline of **48dp x 48dp**. Use `Modifier.minimumInteractiveComponentSize()` where appropriate.

### **Verification Strategy**
*   **Test Type:** Automated Accessibility Scanner (e.g., Accessibility Test Framework for Android).
*   **Assertion:** `onNodeWithContentDescription("Show seed").assertTouchBoundsInRoot().assertWidthIsAtLeast(48.dp)`.

---

## 5. State Orchestration (Dialog Stacking)

### **The Issue: Overlapping Dialog Contexts**
*   **Location:** `MainActivity.kt`.
*   **Behavior:** Multiple state variables (`showExportPinDialog`, `showImportPinDialog`, `editingService`, `revealingService`) can technically be true at the same time.
*   **Impact:** Potential for "Dialog Soup" where multiple modal elements overlap, or a sheet appears over a dialog, causing confusion and potential state deadlocks.

### **Proposed Fix**
1.  **UI State Sealed Class:** Refactor the "active modal" state into a single sealed class:
    ```kotlin
    sealed class ActiveModal {
        object None : ActiveModal()
        data class Editing(val service: DgpService) : ActiveModal()
        data class Revealing(val service: DgpService) : ActiveModal()
        object ExportPin : ActiveModal()
        // ...
    }
    ```
2.  **Single Source of Truth:** This ensures that only one modal context can be active at a time, making the UI logic deterministic and easier to debug.

### **Verification Strategy**
*   **Test Type:** Unit Test.
*   **Test Case:** `test_uiState_precludesMultipleModals`.
*   **Action:** Attempt to set the state to `Revealing` while it is already `Editing` and verify that the transition is handled gracefully by the state machine.
