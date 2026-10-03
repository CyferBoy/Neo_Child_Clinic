# Neo Child Clinic — UI Change Proposal

Companion to `MASTER.md`. Nothing here is implemented yet. Scope is **UI/presentation only**:
no database, Supabase, auth, RLS, repository, use case, sync, or business-logic changes.

---

## Sequencing

The phases are ordered so that **each one is shippable and visually verifiable on its own**.
Nothing in Phase 1 touches a screen.

| Phase | What | Blast radius | Risk |
|---|---|---|---|
| **1** | Token layer only | new files + `Theme.kt` | none — additive |
| **2** | Fix the 6 WCAG failures | a handful of lines | low, visibly better |
| **3** | Shared components | `core/ui/` only | none — additive |
| **4** | Migrate screens to tokens | 128 presentation files | medium, mechanical |
| **5** | Remove the legacy paths | deletions | medium |

Phase 4 is the bulk of the work and is deliberately last: it's the phase where the token layer
gets proven before 255 composables depend on it.

---

## Phase 1 — Token layer (additive, no screen touched)

New files under `core/designsystem/`:

```
Spacing.kt     10 spacing tokens + derived constants (§3)
Shapes.kt      5 corner radii (§4)
Elevation.kt   5 shadow levels (§5)
Typography.kt  all 16 M3 roles + tabular figures (§2)
Palette.kt     accent layer: 7 semantic pairs (§1.3)
```

Modify `core/designsystem/`:

- `Color.kt` — **delete** `PurpleGrey40`, `Pink40`, `SuccessGreen`, `ErrorRed`, and the
  `Dark*Container`/`Dark*On*Container` name-soup (11 values), which move into `Palette.kt`
  under semantic names. Keep the 9 live `Chart*` values, darkening `ChartRevenue` → `#B45309`
  and `ChartCash` → `#1B5E20`.
- `Theme.kt` — expand both schemes to full role coverage (§1.2); set `background`/`surface`
  from the old `bgOffWhite`; define `error` explicitly; **delete** `dynamicColor`
  (dead parameter, no caller, no settings toggle).
- `Type.kt` — replaced by `Typography.kt`.

`CustomColors` keeps its 7 container/content pairs, loses `bgOffWhite` and `iconColor`.

### The one behavioural break to accept up front

Setting `colorScheme.background`/`surface` to `#FBF8F5` while screens still pass
`Scaffold(containerColor = Color.Transparent)` inside a `Surface(color = customColors.bgOffWhite)`
wrapper means the two now agree — but only if `bgOffWhite` is deleted in the same commit.
Leaving one without the other produces a mismatched background. **They ship together.**

---

## Phase 2 — Fix the measured accessibility failures

Six changes, all small, all provable with `contrast_check.py`.

| # | File:line | Change |
|---|---|---|
| 1 | `Color.kt:8` | `ClinicBlue #007BFF` → `#0059B8`. Fixes every primary button label **and** every `StandardTextField` label in one line. |
| 2 | `Theme.kt` | Add `error = #C62828` / `onError` / `errorContainer` / `onErrorContainer` to both schemes. |
| 3 | `StatisticsSummaryCard.kt:23-24,198` | `SuccessGreen`/`ErrorRed` → `CustomColors.success.content` / `colorScheme.error`. |
| 4 | `TodayPatientDateSelector.kt:19,117` | `SuccessGreen` fill → `CustomColors.success.content` (2.71:1 → 7.68:1). |
| 5 | `Color.kt:16,18` | `ChartRevenue` `#FF9800`→`#B45309`; `ChartCash` `#4CAF50`→`#1B5E20`. |
| 6 | `res/values/themes.xml` | Parent `android:Theme.Material.Light.NoActionBar` → a `DayNight` variant; add `values-night/`. Kills the white pre-Compose flash in dark mode. |

**No behavior, no data flow, no navigation change.** This phase alone fixes the app's largest
accessibility defect.

---

## Phase 3 — Shared components (`core/ui/`)

Extend what exists rather than replacing it.

| Component | Action |
|---|---|
| `StandardButton` | Keep. Add a `ButtonVariant` (Primary/Tonal/Outlined/Text/Danger) so screens stop hand-passing `containerColor`. Migrate raw `Button`/`OutlinedButton`/`TextButton` call sites onto it. |
| `EmptyState` | Extend `core/ui/Buttons.kt:18` to take `icon`, `title`, `message`, `onAction`. **Delete** the duplicate private `EmptyState` at `PersonalReminderScreen.kt:127`. |
| `ErrorState` | **New.** `message` + `onRetry`. Replace ~10 bare `Text(uiState.error, color = error)` renders (`Dialogs.kt:105,159`, `FullAuditLogScreen.kt:57`, `AccessDeniedScreen`…). |
| `SegmentedControl<T>` | **New.** Absorb the 6 ad-hoc `SingleChoiceSegmentedButtonRow` sites. |
| `AppCard` / `KpiTile` | **New.** `KpiTile` absorbs the 6 near-identical dashboard tiles in `DashboardComponents.kt`. |
| `ConfirmDialog` | Extract the base from `DeleteConfirmationDialog`; the `isDeleting` double-fire guard is a real correctness property — keep it. |
| `MessageEffect` (Toast) | **Deprecate.** Snackbar is already the pattern in 4 screens; provide `SnackbarHost` once at the NavHost and retire the Toast path. |
| `Skeleton.kt` | Keep — it's the best thing in `core/ui/`. Gate the shimmer on the system duration scale for reduced-motion. |
| `AccessDeniedScreen` | Fold `Navigation.kt:96` and `StatisticsScreen.kt:76` into one. |

---

## Phase 4 — Migrate screens (mechanical, no logic changes)

Per feature, in dependency order:

```
auth → patient → vaccination → consultation → reminder → inventory
     → borrowed/waste → finance → statistics → staff → settings → dashboard
```

Screens last, because they consume everything above.

Substitutions, applied uniformly:

| Pattern today | Becomes |
|---|---|
| `modifier.padding(16.dp)` | `Modifier.padding(MaterialTheme.spacing.screenPadding)` |
| `RoundedCornerShape(12.dp)` | `MaterialTheme.shapes.medium` |
| `Color.White` / `Color.Black` (71 refs) | the correct `colorScheme` role |
| `Scaffold(containerColor = Color.Transparent)` + `Surface(bgOffWhite)` | a plain `Scaffold` on `colorScheme.background` |
| `customColors.textBlue.copy(alpha = 0.6f)` | `onSurfaceVariant` |
| inline `.sp` (27 refs) | `MaterialTheme.typography.*` |
| raw `Color(0x…)` (139 refs, 23 files) | a semantic token |

Duplicate components collapsed as they're encountered:

`PatientSummaryCard` (`AddConsultationScreen.kt:218` / `AddVaccinationScreen.kt:321`) ·
`DetailRow` (`ExpenseListScreen.kt:315` / `PersonalReminderDetailsSheet.kt:241`) ·
`InfoRow`/`InfoSection` (`StaffDetailsScreen.kt:270,250` / `ProfileScreen.kt:213,193`) ·
`SectionHeader` (`AddConsultationScreen.kt:233` / `PatientInfoComponents.kt:296`)

---

## Phase 5 — Cleanup

- Delete `SelectDropdown<T>` (`core/ui/Dropdowns.kt:105`) — declared, never consumed.
- Delete `Typography.bodyLarge` override's `letterSpacing = 0.5.sp`.
- `themeMode`: raw `String` + 3 magic literals in 2 files (`MainActivity.kt:92-96`,
  `SettingsScreen.kt:50-54`) → enum.
- Route builders: replace 13+ hand-written interpolations (`"patient_details/$id"`) with
  `Routes.patientDetails(id)`. Add the missing `Routes.COMPLETED_DISMISSED` constant for the raw
  literal at `NavigationReminderGraph.kt:30`.
- String extraction is **out of scope** (1 entry in `strings.xml`, 0 calls to `stringResource`) —
  worth a separate ticket; it is a real localization blocker but not a design-system fix.

---

## Explicitly not doing

| Item | Why |
|---|---|
| Material You / dynamic color | Already dead code. Deleting it is cleaner than wiring a toggle. Brand blue is why the clinic reads as itself. |
| Bundling a custom font (e.g. Atkinson Hyperlegible) | Real a11y benefit, but needs a font pipeline + APK weight. Ask first. |
| Adding a bottom navigation bar | 51 destinations, drawer-only is defensible for this IA. User asked to keep the existing IA. Flagging, not doing. |
| Sealed `UiState` hierarchy | `SlotsUiState` (`core/ui/Dropdowns.kt:160`) is the right pattern, but converting 26 ViewModels is a state-management change, not presentation. Separate ticket. |
| Snackbar replacing every Toast | Retire `MessageEffect`, migrate incrementally in Phase 4. |

---

## Verification

Per phase:

```bash
python design-system/neo-child-clinic/contrast_check.py   # must exit 0
./gradlew :app:assembleDebug                               # must compile
```

Then by hand, in dark **and** light:

- [ ] Every screen: primary button label, field label, error text readable (AA)
- [ ] Statistics: all 9 chart series distinguishable; revenue/cash deltas readable
- [ ] Every list: empty state offers an action; error state offers retry
- [ ] Pull-to-refresh on all 13 list screens
- [ ] Font scale at max: no clipped text, no overlap
- [ ] Back gesture from every detail screen returns to the drawer
