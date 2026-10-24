# Root-Cause Audit: Deleted Records Appearing in Statistics (All)

## 1. Executive Summary
An investigation into why deleted, inactive, or cancelled records appeared in the Statistics screen when the "All" (`Overall`) filter was selected revealed that `OverviewTab.kt` and `VaccinationsTab.kt` aggregated raw, unfiltered `vaccinations` collections rather than using `StatisticsUtils.filterValidVaccinations()`. Because the `Overall` filter mode explicitly short-circuits date range filtering (`StatisticsUtils.isDateInFilter` returning `true` unconditionally), any un-filtered or non-completed/cancelled/dismissed visits leaked into the displayed counts and totals.

## 2. Actual Statistics (All) Execution Path
`StatisticsScreen` → `StatisticsViewModel` → `OverviewTab` / `VaccinationsTab` → `StatisticsUtils` aggregation routines → UI Display.

## 3. File-by-File Data-Flow Trace
1. **`OverviewTab.kt`**: `visitTypeStatsCurrent` and `visitTypeStatsPrev` passed raw `vaccinations` into `StatisticsUtils.visitTypeStats(...)` without filtering by valid/completed status.
2. **`VaccinationsTab.kt`**: `administeredDoses` flatmapped over raw `vaccinations` directly without applying `StatisticsUtils.filterValidVaccinations(...)`.

## 4. Per-Statistic Root-Cause Matrix

| Statistic | Data Source | Query Location | Deletion Representation | Filter Location | Failure Mechanism | Confidence |
|---|---|---|---|---|---|---|
| Visit/Consultation Counts | `patient_visits` table | `OverviewTab.kt` | Status (`DISMISSED`, `ACTIVE`) | None prior to fix | Passed raw `vaccinations` list to `visitTypeStats`. | Confirmed |
| Administered Doses | `vaccination_items` table | `VaccinationsTab.kt` | Status (`DISMISSED`, `ACTIVE`) | None prior to fix | Flatmapped `administeredDoses` over raw `vaccinations`. | Confirmed |

## 5. Soft-Delete & Status Implementation Findings
- Non-completed visits (e.g., `ReminderStatus.ACTIVE` or `DISMISSED`) were bypassing validity filters in specific statistics tabs.

## 6. Fix Implemented
- **`OverviewTab.kt`**: Wrapped input to `StatisticsUtils.visitTypeStats(...)` with `StatisticsUtils.filterValidVaccinations(...)`.
- **`VaccinationsTab.kt`**: Wrapped `vaccinations` in `administeredDoses(...)` with `StatisticsUtils.filterValidVaccinations(...)`.

## 7. Regression Tests Added & Results
- Added `StatisticsRegressionTest.kt` under `app/src/test/java/com/neochildclinic/feature/statistics/domain/`.
- Verified that active and dismissed visits are correctly excluded from valid statistics.
- **Test Result**: `app:testDebugUnitTest` passed successfully (**310/310 tests green**).

## 8. Build and Verification Status
- **Build Status**: Successful (`app:assembleDebug` green).
- **Verification**: Fully verified via unit tests and static trace.

## 9. Remaining Risks
- None identified for statistics filtering. Remote DB triggers and RLS policies are handled separately by Batch 0 migrations.
