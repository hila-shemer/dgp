# Problems

Blockers for human review. Append-only. Empty means "proceed".

---

## 2026-04-23 — Manual QA + instrumentation tests owed to human / review agent

Phase 10 landed the automated polish (motion tokens, dynamic-type clamp, eye-toggle a11y,
save flash + scroll). **Not done in the loop and required before ship:**

- **Manual screenshot QA** against `screenshots/02-primary-flow.png`,
  `screenshots/03-edit-reorder.png`, `screenshots/04-settings.png` —
  plan §10.3. Human eye; loop cannot compare pixel fidelity.
  Icons render as literal text in the PNGs; compare layout / spacing /
  colors against the PNG but icons against `src/DGP UI Redesign.html`.
- **Accessibility Scanner pass** per plan §10.2. Interactive on-device tool;
  requires human-driven UI navigation.
- **`./gradlew :app:connectedDebugAndroidTest`** against the attached emulator.
  Excluded from `run_tests.sh` per `DECISIONS.md` 2026-04-23 "Test Harness" —
  review-agent scope.

No blockers found during automated polish.

---

## Theme.Material3.DayNight.NoActionBar not available

**Attempted:** `Theme.Material3.DayNight.NoActionBar` as the parent for the platform XML theme, per plan §1.5.

**Why it failed:** `com.google.android.material:material` (which provides XML-based Material3 themes) is not a dependency — neither direct nor transitive. Only `androidx.compose.material3:material3` is present, which is Compose-only and ships no XML theme resources.

**Resolution:** Used `Theme.AppCompat.DayNight.NoActionBar` as the parent instead. `androidx.appcompat:appcompat` is a transitive dependency (pulled in by `androidx.compose.material3`). Provides the same functional properties for the shell theme: no action bar, dark-mode-responsive background.

**No further action needed** unless design explicitly requires Material3 XML component styling (inflated views), in which case `com.google.android.material:material` must be added to `app/build.gradle`.
