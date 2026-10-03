# Neo Child Clinic — Design System

**Layer:** UI / presentation only. Nothing here touches database, Supabase, auth, RLS,
repositories, use cases, sync, or business logic.

**Product type:** internal clinical back-office tool (pediatric vaccination management).
**Users:** clinic staff — reception, doctor, admin — on a phone, all day, often one-handed.
**Density:** high (data-dense). A doctor scanning 40 patients wants rows, not whitespace.

**Style: Data-Dense Dashboard.** Calm, neutral, information-first. Explicitly **not**
Neumorphism (rejected: `accessibility: risk:high`, and embossed shapes destroy figure/ground
separation on dense tables). Not Material You (rejected: an app-wide brand blue is how a
clinic reads as *itself*; wallpaper-derived color makes two devices look like two products).

Every contrast figure below is **measured**, not asserted. Source of truth for the numbers is
`design-system/neo-child-clinic/contrast_check.py`.

### Scope decisions (owner-confirmed)

| Decision | Outcome |
|---|---|
| **Dashboard card layout + light-mode colours are approved as-is.** | `DashboardComponents.kt` / `TodayPatientsCard.kt` keep their `softX`/`textX` pastel pairs and current geometry. Two proposals were withdrawn: recolouring the six tiles to a neutral+status scheme, and escalating the inventory tile's container on stock-out. Do not raise them again without asking. |
| The inventory tile's pink-on-orange alert state stays. | Measured 5.54:1, so it passes AA. It is a *semantic* weakness (the container never changes, so 0 and 5 out-of-stock look alike) — not an accessibility defect. Left alone by choice. |
| Drawer primary-container changes purple → blue. | Unavoidable: `primaryContainer` was never declared, so `AppDrawer` rendered M3 baseline purple against an all-blue app. Filling the role in fixes it. |
| Drawer-only navigation stays. | 51 destinations; no bottom bar. Existing IA preserved per scope. |
| Spacing + elevation tokens deferred. | No consumer until Phase 4 migrates screens. Creating them now would be speculative - 936 `.dp` literals get replaced at migration time, not before. |

---

## 1. Color

### 1.1 Two layers, one rule

| Layer | Provided by | Use for |
|---|---|---|
| **Role layer** | `MaterialTheme.colorScheme` | Everything role-based: surfaces, on-surface text, outlines, error, primary containers |
| **Accent layer** | `LocalCustomColors` | *Semantic badges only* — 7 container/content pairs for status chips, KPI tiles, chart legend swatches |

**Rule:** `LocalCustomColors` must **never** supply a background, a surface, or an app-bar
color. Only `colorScheme` does. (Today `bgOffWhite` and `iconColor` break this rule — see §13.)

### 1.2 Role palette

Light scheme — full M3 role coverage. **Bold = changed from today's app.**

| Role | Value | Note |
|---|---|---|
| `primary` | `#0059B8` | **was `#007BFF`** — failed AA (see §1.4) |
| `onPrimary` | `#FFFFFF` | 6.73:1 on primary |
| `primaryContainer` | `#D6E4F0` | = `softBlue` today, promoted to a role |
| `onPrimaryContainer` | `#1E3A5F` | = `textBlue` today, promoted to a role |
| `secondary` | `#00695C` | teal, from `textCyan` |
| `onSecondary` | `#FFFFFF` | |
| `secondaryContainer` | `#D9F2F0` | = `softCyan` |
| `onSecondaryContainer` | `#00695C` | |
| `tertiary` | `#6A1B9A` | from `textPurple` |
| `onTertiary` | `#FFFFFF` | |
| `tertiaryContainer` | `#F2E4F6` | = `softPurple` |
| `onTertiaryContainer` | `#4A148C` | |
| `error` | `#C62828` | **was undefined** — silently fell back to M3 baseline red |
| `onError` | `#FFFFFF` | |
| `errorContainer` | `#FCE4E4` | = `softPink` |
| `onErrorContainer` | `#B71C1C` | = `textPink` |
| `background` / `surface` | `#FBF8F5` | **was `#FFFBFE`** — absorbs today's `bgOffWhite` |
| `onBackground` / `onSurface` | `#1A1C1E` | |
| `surfaceVariant` | `#EBEBEB` | = `softGrey` |
| `onSurfaceVariant` | `#424242` | = `textGrey` |
| `outline` | `#8A8A8A` | 3.26:1 — meets the 3:1 non-text floor |
| `outlineVariant` | `#DEDEDE` | decorative dividers only — exempt from 3:1 |
| `scrim` | `#000000` | |

Dark scheme — **full parity**. Today only 9 of 27 roles are defined; the rest silently fall
through to M3's purple baseline, which is why dark mode looks unfinished.

| Role | Value |
|---|---|
| `primary` | `#92CCFF` |
| `onPrimary` | `#003355` |
| `primaryContainer` / `onPrimaryContainer` | `#004977` / `#C2E8FF` |
| `secondary` / `onSecondary` | `#80CBC4` / `#00352F` |
| `secondaryContainer` / `onSecondaryContainer` | `#004D40` / `#80CBC4` |
| `tertiary` / `onTertiary` | `#E0BBE4` / `#381E40` |
| `tertiaryContainer` / `onTertiaryContainer` | `#553F5F` / `#F2DAFF` |
| `error` / `onError` | `#FFB4AB` / `#690005` |
| `errorContainer` / `onErrorContainer` | `#93000A` / `#FFDAD6` |
| `background` / `surface` | `#121212` (absorbs dark `bgOffWhite`) |
| `onBackground` / `onSurface` | `#E2E2E6` |
| `surfaceVariant` | `#2C2C2C` |
| `onSurfaceVariant` | `#C4C6C7` |
| `outline` | `#8E8E8E` |
| `outlineVariant` | `#3A3A3A` |

### 1.3 Accent layer — 7 semantic pairs

These already work. Measured 5.10:1–9.72:1 in light, 5.27:1–10.81:1 in dark. **Keep the
values; only drop `bgOffWhite` and `iconColor`.**

| Semantic | container (light) | content (light) | container (dark) | content (dark) |
|---|---|---|---|---|
| info | `#D6E4F0` | `#1E3A5F` | `#004977` | `#C2E8FF` |
| success | `#DCF0E2` | `#1B5E20` | `#005231` | `#8FF7BF` |
| warning | `#FFE8D1` | `#9C4D04` | `#723600` | `#FFDDB1` |
| accent | `#F2E4F6` | `#4A148C` | `#553F5F` | `#F2DAFF` |
| teal | `#D9F2F0` | `#00695C` | `#004D40` | `#80CBC4` |
| neutral | `#EBEBEB` | `#424242` | `#2C2C2C` | `#E2E2E6` |
| danger | `#FCE4E4` | `#B71C1C` | `#632E2E` | `#FFDAD6` |

### 1.4 Measured WCAG failures this palette fixes

| Element | Today | Measured | Required | Fix |
|---|---|---|---|---|
| White label on primary button | `#007BFF` | **3.98:1** | 4.5:1 | primary → `#0059B8` (**6.73:1**) |
| Primary as text (field labels, links) | `#007BFF` | **3.88:1** | 4.5:1 | same change (**6.56:1**) |
| Success delta text (`StatisticsSummaryCard.kt:198`) | `#4CAF50` | **2.71:1** | 4.5:1 | use `success.content` `#1B5E20` (**7.68:1**) |
| Error delta text (`StatisticsSummaryCard.kt:198`) | `#F44336` | **3.59:1** | 4.5:1 | `error` → `#C62828` (**5.48:1**) |

Chart series (non-text, 3:1 floor) — two are below it:

| Series | Today | Measured | Fix |
|---|---|---|---|
| `ChartRevenue` | `#FF9800` | **2.10:1** | `#B45309` |
| `ChartCash` | `#4CAF50` | **2.71:1** | `#1B5E20` |

All other chart series already clear 3:1.

---

## 2. Typography

`FontFamily.Default` (Roboto). Bundling a custom face adds a font pipeline and APK weight for
marginal gain — not doing it unless the clinic asks for a brand face.

Today `Type.kt` defines **1 of 16** M3 roles; the other 15 fall back to M3 defaults, and 27
hardcoded `.sp` values scatter on top of that. Define all 16 once, then ban inline `.sp`.

| Role | Size / line | Weight | Tracking |
|---|---|---|---|
| `displayLarge` | 57/64 | Normal | -0.25 |
| `displayMedium` | 45/52 | Normal | 0 |
| `displaySmall` | 36/44 | Normal | 0 |
| `headlineLarge` | 32/40 | Normal | 0 |
| `headlineMedium` | 28/36 | Normal | 0 |
| `headlineSmall` | 24/32 | SemiBold | 0 |
| `titleLarge` | 22/28 | SemiBold | 0 |
| `titleMedium` | 16/24 | SemiBold | 0.15 |
| `titleSmall` | 14/20 | SemiBold | 0.1 |
| `bodyLarge` | 16/24 | Normal | 0.5 → **0** |
| `bodyMedium` | 14/20 | Normal | 0.25 |
| `bodySmall` | 12/16 | Normal | 0.4 |
| `labelLarge` | 14/20 | Medium | 0.1 |
| `labelMedium` | 12/16 | Medium | 0.5 |
| `labelSmall` | 11/16 | Medium | 0.5 |

`bodyLarge` drops `letterSpacing = 0.5.sp` → `0`. 0.5sp is a display-tracking value; on 16sp
running clinical text it reads as loose and hurts scan speed on dense lists.

**Numeric alignment:** currency and counts use `FontFeatureSettings = "tnum"` so digits share a
column width in tables. Matters for `FinanceTab`, `FullReportScreen`, `ExpenseListScreen`.

---

## 3. Spacing

No `Spacing.kt` exists; 936 hardcoded `.dp` literals. One 4dp-based scale:

| Token | dp | Use |
|---|---|---|
| `space2` | 2 | hairline nudge, badge inset |
| `space4` | 4 | icon-to-label inside a chip |
| `space8` | 8 | gap between list items, chip internal padding |
| `space12` | 12 | card internal padding (dense), gap in a row |
| `space16` | 16 | **screen gutter**, card padding, field gap |
| `space20` | 20 | field gap in forms |
| `space24` | 24 | gap between sections |
| `space32` | 32 | gap above a major section |
| `space40` | 40 | large section break |
| `space48` | 48 | above a screen title |

Derived constants:

```
screenPadding   = 16.dp
cardPadding     = 16.dp      // dense card: 12.dp
listItemGap     = 8.dp
sectionGap      = 24.dp
buttonHeight    = 48.dp      // primary/tonal
buttonHeightSm  = 40.dp      // in-card, in-dialog
touchTarget     = 48.dp      // minimum, all interactive
iconSm/Md/Lg    = 16/24/32.dp
topBarHeight    = 64.dp
bottomBarHeight = 80.dp      // incl. content inset
```

Touch target floor is **48dp** (Android/Material), not iOS's 44pt.

---

## 4. Shapes

72 inline `RoundedCornerShape(n.dp)` values; only 8 files read `MaterialTheme.shapes`.
One set, in `Shapes.kt`:

| Token | Radius | Use |
|---|---|---|
| `extraSmall` | 4 | badge, chip, table cell |
| `small` | 8 | list row, text field, icon button |
| `medium` | 12 | **card, button** (matches today's `StandardButton`) |
| `large` | 16 | dialog, dropdown menu |
| `extraLarge` | 28 | bottom sheet, modal |

---

## 5. Elevation

| Level | dp | Use |
|---|---|---|
| `level0` | 0 | list rows, table cells — separate with `surfaceContainer`, not shadow |
| `level1` | 1–2 | cards |
| `level2` | 3 | dropdown menu, FAB |
| `level3` | 6 | dialog |
| `level4` | 8 | modal bottom sheet |

Rule: **flat by default.** A dense list of cards each carrying a shadow reads as noise. Shadow
marks *layers* (what floats above what), not decoration. Where the app today separates rows by
`copy(alpha = 0.6f)` tint swaps, switch to `surfaceContainer` elevation — tint-by-alpha on a
list makes it impossible to tell "selected" from "alternate row".

---

## 6. Cards

- `AppCard` — `Card(colors = surfaceContainerLow, shape = medium, elevation = level1)`, padding `space16` (or `space12` in dense/statistics contexts).
- No border by default; use elevation. Border only when the card sits on a same-tone surface.
- KPI tile — `AppCard` + accent container icon chip (48dp, `medium` radius) + `titleMedium` label + `headlineSmall` value + `labelMedium` delta (signed, `success.content`/`error`). **Component only** — the dashboard's existing tile colours are approved and stay; this just stops the six tiles being re-declared by hand.
- List row — flat, `small` radius on the group container only, `space16` horizontal padding, 64–72dp min height, trailing action at `space8`.

---

## 7. Buttons

Today `StandardButton` is the *only* button component, so every other button in the app is a
raw `Button`/`OutlinedButton`/`TextButton` with hand-passed colors — that is where the visual
drift comes from. One component, five variants.

| Variant | Container | Content | Use |
|---|---|---|---|
| `Primary` | `primary` | `onPrimary` | one per screen; the commit action |
| `Tonal` | `secondaryContainer` | `onSecondaryContainer` | secondary action next to Primary |
| `Outlined` | transparent + `outline` | `primary` | tertiary; cancel, dismiss |
| `Text` | transparent | `primary` | inline/low-emphasis |
| `Danger` | `errorContainer` | `onErrorContainer` | destructive confirm — **never** a bare red `TextButton` |

Sizing: `height 48.dp` (`buttonHeight`), `shape medium`, label `labelLarge`. `FilledTonalButton`
and `OutlinedButton` get `40.dp` in-card / in-dialog. Loading swaps label for a 20dp
`CircularProgressIndicator` and sets `enabled = false` — matching `StandardButton`'s existing
behavior, which is correct.

Icon-only buttons: `IconButton` with a 24dp icon inside a 48dp target, `contentDescription`
required unless decorative.

---

## 8. Segmented controls

6 call sites build `SingleChoiceSegmentedButtonRow` ad-hoc (`PatientInfoComponents.kt:172`,
`WeeklyDoctorSlotsScreen.kt:137`, `BorrowedScreen.kt:203`, `TodayPatientsScreen.kt:162,183`,
`VaccinationsTab.kt:225`, `FullReportScreen.kt:135`). Extract one `SegmentedControl<T>`:

```kotlin
SegmentedControl(
    options = listOf(...),
    selected = state,
    onSelect = { ... },          // must also be reachable by keyboard/screen reader
    modifier = Modifier.fillMaxWidth(),
)
```

- 2–4 options, single row. Never wrap.
- Height 40dp, label `labelLarge`, shape `small` (segmented controls are compact by nature).
- Selected segment: `secondaryContainer` + `onSecondaryContainer`, with the M3 checkmark.
- **Tab rows are not segmented controls.** `TabRow` (`BorrowedScreen.kt:163`,
  `CompletedDismissedScreen.kt:52`) and `PrimaryScrollableTabRow`
  (`ReminderComponents.kt:17`) mean "these are *different screens*" — keep them separate.
  Rule of thumb: ≤4 short options that filter one list → segmented. Different datasets → tabs.

---

## 9. Dialogs

`DeleteConfirmationDialog` (`core/ui/Dialogs.kt:23`) is the one good component — keep its
`isDeleting` double-fire guard, that's a real correctness property.

| Type | Use |
|---|---|
| `ConfirmDialog` | base — title, message, confirm, dismiss, `isBusy` guard |
| `DeleteConfirmationDialog` | destructive; confirm button = `Danger` |
| `AlertDialog` (M3) | everything else |

Rules: `shape large`; max width 560dp; confirm button carries `Danger` for destructive actions
instead of red text; a dialog must never be the only place an error appears.

---

## 10. Navigation

51 destinations, **drawer-only** (`AppDrawer.kt`), one `NavHost` (`Navigation.kt:83`).

IA is kept as-is per scope. Changes are consistency-only:

- **Route constants must be built, not concatenated.** 13+ sites hand-write route strings
  (`"patient_details/$patientId"` etc.). Add a `Routes.patientDetails(id)` builder and make the
  constants the single source.
- `NavigationReminderGraph.kt:30` registers a raw literal `"completed_dismissed?tab={tab}"` that
  exists in no constant — give it one.
- `NavigationDashboardGraph.kt:64` navigates to `"today_patients"` while the route is declared
  `"today_patients?tab={tab}&highlightId={highlightId}"`. It works only because both args are
  nullable with defaults. Fragile, not broken.
- `AccessDeniedScreen` (`Navigation.kt:96`) and `StatisticsAccessDeniedScreen`
  (`StatisticsScreen.kt:76`) are two copies of one screen — fold into `core/ui`.
- Drawer items get `Icons` + label + selected state; group into sections (Clinic / Records /
  Inventory / Reports / Settings).

---

## 11. Loading, empty, error

Three states, three components, used everywhere. Today they are ~10 ad-hoc `Text(error)`
renders with no recovery path.

| State | Component | Rule |
|---|---|---|
| **Loading (first paint)** | `SkeletonBox` / `SkeletonListItem` from `core/ui/Skeleton.kt` | Match the real layout's shape and count. Already good — 19 screens use it. Keep. |
| **Loading (in-flight action)** | 20dp `CircularProgressIndicator` in the triggering control | Button disables itself; don't block the screen. |
| **Loading (refresh)** | `LinearProgressIndicator` | Only when existing content stays on screen. |
| **Empty** | `EmptyState(icon, title, message, action)` | **Always** offer the action that creates content. "No patients yet — Add patient". Never a bare sentence. |
| **Error** | `ErrorState(message, onRetry)` | **Always** a retry path. UX rule: no recovery path = no error state. |

Notes:
- Two competing `EmptyState` definitions exist: `core/ui/Buttons.kt:18` and
  `PersonalReminderScreen.kt:127`. Delete the second, extend the first.
- Snackbar is the transient-feedback channel — already used in 4 screens
  (`DashboardScreen`, `StatisticsScreen`, `SyncScreen`, `AddVaccinationScreen`).
  `MessageEffect`/`Toast` (`core/ui/Buttons.kt:25`) is the legacy path — retire it.
- `SnackbarHost` should be provided once, at the NavHost level, not per screen.

---

## 12. Accessibility

| Rule | Status today | Action |
|---|---|---|
| Text contrast ≥4.5:1 both themes | **3 measured failures** (§1.4) | apply §1.2/§1.4 |
| Non-text (chart series, borders) ≥3:1 | 2 failures (`ChartRevenue`, `ChartCash`) | darken per §1.4 |
| Touch target ≥48dp | 51 `IconButton` sites, ~97 sub-48dp size/height calls | audit; M3 `IconButton` already reserves 48dp — verify the *icon* size isn't what's constrained |
| Decorative icon hidden from AT | 46 `contentDescription = null` | valid **only** where the icon sits beside visible text; audit the rest |
| Icon-only control has a name | not guaranteed | every icon-only button gets a real `contentDescription` |
| State announced | segmented/tabs not verified | `Modifier.semantics { selected = … ; role = Role.Tab }` |
| Dark mode independent contrast | only 9/27 roles defined | full dark parity per §1.2 |
| Dynamic text | 27 hardcoded `.sp` | route all through `Typography` so scale-to-font-size works |
| Reduced motion | shimmer only | gate `Skeleton.kt` infinite transition on `!LocalAccessibilityManager`/duration scale |
| `FLAG_SECURE` | already set (`MainActivity.kt:85`) | keep — correct for PHI |
| `values-night/` | missing | add; XML parent theme is currently always-light `android:Theme.Material.Light.NoActionBar` |

---

## 13. Known inconsistencies, ranked

Full change proposal lives in `../neo-child-clinic-CHANGES.md`.

### P0 — correctness / accessibility
1. `primary #007BFF` fails AA as button text (3.98:1) **and** as label text (3.88:1). Affects every primary button and every field label.
2. `SuccessGreen #4CAF50` (2.71:1) and `ErrorRed #F44336` (3.59:1) used as delta text in `StatisticsSummaryCard.kt:198`.
3. `colorScheme.error` is used in ~10 files but **never defined** in either scheme — works only because M3's baseline red happens to be legible.
4. `CustomColors.bgOffWhite` (`#FBF8F5`/`#121212`) contradicts `colorScheme.background`/`surface` (`#1A1C1E`) → two backgrounds stack; this is why most screens wrap `Scaffold(containerColor = Color.Transparent)`.
5. Dark scheme defines 9 of 27 roles; the rest silently render as M3 baseline purple.
6. `ChartRevenue` 2.10:1, `ChartCash` 2.71:1 — below the 3:1 non-text floor.

### P1 — consistency
7. No spacing tokens: 936 `.dp` literals across 92 files.
8. No shape tokens: 72 inline `RoundedCornerShape`; 8 files use `MaterialTheme.shapes`.
9. `Typography` defines 1 of 16 roles; 27 inline `.sp`.
10. One button component, but raw `Button`/`OutlinedButton`/`TextButton` used everywhere else with hand-passed colors.
11. `SegmentedControl` built ad-hoc in 6 places.
12. `EmptyState` defined twice; neither has an icon or an action.
13. No `ErrorState` — errors are red `Text` with no retry, ~10 sites.
14. `AccesDeniedScreen` duplicated.
15. Off-token hex: 139 literal `Color(0x…)` in 23 files, on top of the token layer.

### P2 — hygiene
16. `dynamicColor` parameter defaults `false`, no caller sets it, no settings toggle → dead path. **Delete it** (decision: brand blue stays).
17. `themeMode` is a raw `String` with 3 magic literals compared in 2 files → make it an enum.
18. Route strings hand-concatenated in 13+ places; one route registered as a raw literal.
19. `res/values/strings.xml` has 1 entry; `stringResource` never called → no localization path. Out of scope for a design-system pass, but worth logging.
20. Zero UI tests; 7 previews for 51 destinations, none dark-mode annotated.
