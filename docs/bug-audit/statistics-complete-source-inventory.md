# Statistics — Complete Source Inventory

Audit date: 2026-10-24 · Scope: every file that contributes data to any Statistics metric.
Method: two independent repository-wide traversals (data layer, presentation/domain layer),
reconciled and then **spot-verified line-by-line by the auditor** against the working tree.

## 1. Trust-boundary facts (established before any finding)

| Fact | Evidence |
|---|---|
| Locally, **only `reminders` is soft-deleted** (`is_deleted`, `deleted_at`, `deleted_by`). Every other business table had the soft-delete trio hard-deleted-and-dropped by migration 31→32. | `AppDatabase.kt:385-415` (18-table list + `DELETE … WHERE is_deleted = 1` before `DROP COLUMN`) |
| All local deletes are **hard `DELETE`** with cascade (a deleted visit also removes its items, reminders and its income rows). | `VaccinationDao.kt:69-73`, `VaccinationRepositoryImpl.kt:406-504`, `PatientRepositoryImpl.kt:223-249` |
| Remotely, all 18 business tables carry `is_deleted`; the app **filters `eq("is_deleted", false)` on every pull path**. | `supabase/migrations/20260925_business_soft_delete_columns.sql:30-56`; `PatientRemoteDataSourceImpl.kt:19,26`, `VaccinationRepositoryImpl.kt:97,164`, `ReminderRepositoryImpl.kt:466`, `FinanceRepositoryImpl.kt:385` |
| `DISMISSED` is a **reminder** status, never a visit status; cancelled visits are deleted outright. Visit statuses in practice: `COMPLETED` (administered), `ACTIVE` (consultation visits always; vaccination visits until administered), `EXTERNAL`. | `ReminderStatus.kt:6-11`, `Visit.kt:43`, `ClinicalVaccinationService.kt:206-220`, `AddVaccinationViewModel.kt:646` |
| The one status gate on visits: `StatisticsUtils.isCountedVaccination` = `COMPLETED || EXTERNAL || source == "EXTERNAL"`. | `StatisticsUtils.kt:43-47` |
| Realtime carries **no row payload** — it only triggers re-pull through the filtered refresh paths. `StatisticsViewModel` has no realtime subscription at all. | `RealtimeChangeSubscriptions.kt:39-65` |

**Consequence:** a locally *deleted* record cannot exist in Room, and a *remotely* deleted record
is excluded at every pull. The remaining risk surface is therefore **status/lifecycle gating**,
not deletion — which is what the findings below cover.

## 2. Inventory — presentation layer (`feature/statistics/presentation`)

| # | File | Role | Deletion/status handling | Notes |
|---|---|---|---|---|
| 1 | `StatisticsScreen.kt` | Tab host, filter state (Overall/FY/Quarter/Month) | none needed | pass-through |
| 2 | `StatisticsViewModel.kt` | `combine` of 6 repo flows → `StatisticsUiState` | uses DAO-filtered `getUpcomingVaccinations()` (`:63-67`), not `getAllReminders()` | `.catch` guards flow death (NCC-016) |
| 3 | `OverviewTab.kt` | Overview cards + 2 charts | **fixed (NCC-043)**: visit-type cards + consultation series + effective-reg-dates now use raw visits; vaccination/dose/finance metrics stay status-gated | see §4 |
| 4 | `PatientsTab.kt` | Patient counts, gender/age breakdowns | hard-delete table; effective dates from raw `allVisits` (`:36-38`) | clean |
| 5 | `VaccinationsTab.kt` | Administered doses per vaccine, Upcoming Need | `administeredDoses` gates the parent visit (`:36-41`); comments corrected (NCC-043-docs) | clean |
| 6 | `FinanceTab.kt` | Revenue/COGS/expenses/net profit | `visitDatesById` from raw visits **for dating only** (`:45`); counting rules inside `FinanceCalculator` | clean |
| 7 | `FullReportViewModel.kt` | Daily/monthly report series | **fixed (NCC-043)**: threads raw `allVisits` for consultation series + effective-reg-dates | see §4 |
| 8 | `FullReportScreen.kt` | Renders report state | none | pass-through |
| 9 | `VaccineDetailViewModel.kt` / `VaccineDetailScreen.kt` | Brand/type drill-down | SQL-filtered source (`getUpcomingVaccinationsByVaccineId/Type`, `DueReminderDao.kt:99-126`) | clean |
| 10 | `FinanceDetailsViewModel.kt` / `MonthlyFinanceDetailsScreen.kt` | Month drill-down | COGS matched against valid vaccinations internally (`FinanceCalculator.kt:79`) | date-resolution & expense-scope mismatch vs FinanceTab — recorded, not fixed |
| 11 | `FinanceTable.kt` | Monthly finance table | filtering pre-aggregation inside `FinanceCalculator.getMonthlyGroupedData` | clean |
| 12 | `MilestonePatientsViewModel.kt` / `MilestonePatientsScreen.kt` | Age-milestone patient lists | hard-delete patients | device-zone vs IST drift — recorded, not fixed |
| 13 | `StatisticsSummaryCard.kt`, `StatisticsTrendChart.kt`, `BarLineChart.kt` | presentational | none | clean |

## 3. Inventory — domain + data layer

| # | File | Role | Deletion/status handling |
|---|---|---|---|
| 14 | `domain/StatisticsUtils.kt` | status gate (`:43-47`), date filters, `visitTypeStats` (`:150-161`), **new** `visitTypeStatsForFilter` | single source of the counting rules |
| 15 | `domain/StatisticsDateUtils.kt` | IST dates, `computeEffectiveRegistrationDates(patients, allVisits)` (`:132-150`) | parameter contract demands the raw visit list |
| 16 | `domain/FinanceCalculator.kt` | revenue dedup (`effectiveIncome`), snapshot COGS, profit completeness gate | COGS gated by `filterValidVaccinations` (`:79`); unmatched income ⇒ profit "Unavailable" |
| 17 | `domain/ExpenseCalculator.kt` | expense windows | hard-delete table, no predicate needed |
| 18 | `domain/ClinicStatsManager.kt` | dashboard metrics (consumer: `DailySummaryWorker` only) | **fixed**: inline duplicate status rule replaced by `StatisticsUtils::isCountedVaccination` |
| 19 | `VaccinationDao.kt` | `getAllVaccinationsWithItems()` (`:33-35`) | **no status filter** — status is a domain-layer rule, deliberately |
| 20 | `PatientDao.kt` | `getAllPatients()` | no filter — no soft-delete column (`:58-61` comment) |
| 21 | `DueReminderDao.kt` | upcoming/due/dismissed queries | **SQL-level** `is_deleted = 0` + status on every read (`:22-134`) |
| 22 | `FinanceDao.kt` / `ExpenseDao.kt` | ledger flows | no soft-delete column locally |
| 23 | `VaccineDao.kt` | inventory + `getVaccinationCountForVaccine` (`:118-119`) | no status predicate — **suspected**: counts pending visits; recorded, unverified, not fixed |
| 24 | Repo impls: `PatientRepositoryImpl`, `VaccinationRepositoryImpl`, `ReminderRepositoryImpl` (`:428-429`, `:97-118`), `FinanceRepositoryImpl`, `ExpenseRepositoryImpl`, `InventoryRepositoryImpl` | Room flows + remote refresh | every remote pull filters `is_deleted = false`; local prune via `RemotePrune` (`core/sync/RemotePrune.kt:29-58`). **NCC-044 (fixed)**: `refreshTransactions`' import guard let `local == null` bypass the pending set, re-importing freshly deleted income rows — now `financeImportAllowed(...)` gates both branches |
| 25 | `core/sync/SyncUploader.kt`, `RealtimeChangeSubscriptions.kt` | sync | **residual risk**: `fetchRemoteConflictData` (`:422`) is the one business-table read with no `is_deleted` filter; `RemoteReminder.toLocal()` hard-codes `isDeleted = false` |

## 4. The defect this audit confirmed and fixed — NCC-043

Commit `8b3c415` (the previous statistics fix under audit) wrapped the **visit-type** input in
`filterValidVaccinations`:

```
visitTypeStats(filterValidVaccinations(vaccinations).filter { isDateInFilter(...) })   // pre-fix
```

Consultation visits are persisted `ACTIVE` by design (`Visit.kt:43`), so `isCountedVaccination`
excluded **100% of them** → "Consulted Patients" and "Total Consultation" cards and the
consultation chart series were permanently `0`, and `computeEffectiveRegistrationDates`
received only valid visits (Overview/FullReport) while `PatientsTab` passed raw — so a
consultation-first patient bucketed into a different month per tab.

This violated the helper's own contract (`StatisticsUtils.kt`, `visitTypeStats` KDoc) and the
original design in commit `a4937b0`, which fed raw visits. Fix: `visitTypeStatsForFilter`
applies **only the date filter**; vaccination-type metrics keep the status gate (a pending
vaccination visit must never count as administered).
