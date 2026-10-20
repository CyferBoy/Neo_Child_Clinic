# Statistics Dashboard

> `feature/statistics/` — metrics, date windows, timezone handling, and the finance tables.
> Related: [[Due Vaccination]] · [[Inventory Management]] · [[Testing]]

## Layout

```
feature/statistics/
├── domain/       ClinicStatsManager, FinanceCalculator, ExpenseCalculator,
│                 StatisticsUtils, StatisticsDateUtils
└── presentation/ StatisticsViewModel, FullReportViewModel, FinanceDetailsViewModel,
                  MilestonePatientsViewModel, VaccineDetailViewModel,
                  OverviewTab, PatientsTab, VaccinationsTab, FinanceTab,
                  StatisticsTrendChart, BarLineChart, FinanceTable, FullReportScreen
```

5 navigation destinations (`app/NavigationStatisticsGraph.kt`).

## Timezone — the single most careful part of the codebase

✅ **The canonical timezone is IST**, declared once as a single source of truth:

```kotlin
// core/common/DateUtils.kt:21
internal const val IST_ZONE_ID = "Asia/Kolkata"
```

`DateUtils.parseDate` (`:57-112`) is the single parse entry point and the most heavily
documented function in the app. Three deliberate choices:

1. **All datetime formats are tried before any date-only format** (`:80-89`), because
   `SimpleDateFormat.parse` prefix-matches and would silently drop the time from
   `2026-08-22 03:48:08.105121+00` — documented at `:64-68`.
2. **Parsing is anchored to IST** (`:100`), so a device east of IST does not shift a
   date-only string back a day (`:94-99`).
3. **Results are cached** in a bounded `ConcurrentHashMap`, cap 5000, cleared wholesale at
   `:109`. It uses the non-throwing `parse(String, ParsePosition)` overload purely for cost
   (`:70-77`).

`StatisticsDateUtils.parseToISTLocalDate` (`:78-93`) handles the pull side: a string with
`T` **and** an offset/`Z` is parsed as an `Instant` and converted; otherwise it falls
through to date-only parsing. ✅ No double-shifting of date-only values.

⚠️ **But the notification layer does not follow this rule.**
`ReminderScheduler.kt:39`, `DailySummaryWorker.kt:29` and
`PersonalReminderNotificationWorker.kt:25` all use `Calendar.getInstance()` /
`LocalDate.now()` — **device local time**. On a device set outside IST, "08:00" is 08:00
device time and `lastSummarySentDate` flips at device midnight. The codebase solved this
deliberately for the widget (`WidgetWorker.kt:84-89`) and did not for reminders.

## Metrics

`feature/statistics/domain/ClinicStatsManager.kt:35-105` → `domain/model/ClinicStats.kt`:

| Field | Rule | Line |
|---|---|---|
| `todayVaccinations` | `dateGiven == todayStr` (string compare on `"d MMM yyyy"`) | `:55` |
| `monthlyVaccinations` | `monthOfIST(dateGiven) == currentMonth` | `:56-58` |
| `todayRevenue/Cash/Online` | `FinanceCalculator.calculateFinanceStats` bucketed by `resolveReportingDate` | `:65-73` |
| `monthlyRevenue` | same, current month | `:74-78` |
| `dueToday`, `overdue` | `DateClassifier.classify(nextDueDate)` | `:81-88` |
| `lowStockCount` | `isLowStock && !hasOutOfStock` | `:101` |
| `topVaccines` | top 5 by summed quantity, COMPLETED/EXTERNAL only | `:90`, `:107-123` |

The universal gate — `StatisticsUtils.isCountedVaccination` (`:43-44`):

```kotlin
vaccination.status == ReminderStatus.COMPLETED
    || vaccination.status == ReminderStatus.EXTERNAL
    || vaccination.source.equals("EXTERNAL", true)
```

`visitTypeStats` (`:150-161`) is the **deliberate exception**, documented at `:144-149`: it
counts all visits by `visit_type` regardless of status, because visits are recorded ACTIVE.

## The soft-delete question — resolved

> *"Exclude soft-deleted records from statistics."*

✅ **At HEAD, no statistics aggregate can be contaminated by a soft-deleted row.** Two
independent reasons:

1. **Locally, `reminders` is the only table that still has an `is_deleted` column.**
   `migration31_32` (`AppDatabase.kt:390-415`) hard-deletes then drops the trio from **18
   tables** — `reminders` is deliberately excluded (`:392-399`, explained at `:326-329`).
2. **Nothing in `app/src/main` ever writes `is_deleted = 1`.** Exhaustive grep: the only
   occurrence is the `DELETE FROM` inside the migration itself (`AppDatabase.kt:408`).

Every aggregate query is therefore filtered, by construction:

| Aggregate | Query | Soft-delete column present? |
|---|---|---|
| Upcoming / Due | `DueReminderDao.kt:75`, `:92`, `:101`, `:106` | ✅ filtered (`is_deleted = 0`) |
| Finance | `FinanceDao.kt:18` `SELECT * FROM finance_transactions` | ❌ dropped in 31→32 |
| Expenses | `ExpenseDao.kt:21` `SELECT * FROM expenses` | ❌ dropped in 27→28 |
| Patients | `PatientDao.kt:9` `SELECT * FROM patients` | ❌ dropped in 31→32 |
| Vaccinations | `VaccinationDao.kt:11` `SELECT * FROM patient_visits` | ❌ dropped in 31→32 |
| Inventory | `VaccineDao.kt:12`, `:35` | ❌ dropped in 31→32 |

⚠️ **The original concern does not reproduce — but only because soft-delete was almost
entirely removed, not because the queries got filters right.** The residual risk is a
row soft-deleted by a pre-v31 client: `migration31_32` hard-deletes those on 18 tables but
**not** on `reminders`, where a legacy `is_deleted = 1` row would be hidden by the filtered
queries yet still visible to the nine unfiltered ones. See [[Known Bugs]] and
[[Room Database]].

## Financial year

`StatisticsUtils.fyQuarters` (`:16-21`): `Q1 = Apr–Jun` (months 3,4,5) … `Q4 = Jan–Mar`
(0,1,2) on 0-based `Calendar` months. `isDateInFilter` (`:77-92`):
`recordFY = if (m >= Calendar.APRIL) y else y - 1`.

`FinanceCalculator.getMonthlyGroupedData` (`:173-198`) builds the FY window as a `Calendar`
starting 1 April, offset by `((selectedMonth - APRIL) + 12) % 12`.

✅ `StatisticsUtilsFilterTest` pins a real regression: a mangled `"FY veral"` string is
rejected — the residue of a `takeLast(5)` bug.

## The income double-count rule

`FinanceCalculator.kt:307-318` states it as a hard invariant:

> *"Every screen that sums income must go through here — do not re-implement the sum."*

`FinanceCalculator.effectiveIncome()` collapses duplicate `VACCINATION` income per visit,
because a visit can legitimately carry more than one income row (`:310-312`).
`FinanceCalculatorTest` pins it.

✅ **Was re-implemented anyway (NCC-008).** `FullReportViewModel.computeDaily` summed raw
transactions:

```kotlin
transactions.filter { it.type.equals("INCOME", true) }.forEach { tx -> … }
```

…so drilling Full Report into a single month **over-reported** revenue, cash, online and net
profit while the same screen showed the correct figure one level up. `computeDaily` now goes
through `FinanceCalculator.effectiveIncome` too (`:298`), as `computeMonthly` already did
(`:212`) — the inline comment records the rule.

## `resolveReportingDate`

Finance rows are bucketed by a three-level precedence, pinned by `FinanceCalculatorTest`:

```
transaction.transaction_date  →  the parent visit's dateGiven  →  the row's created timestamp
```

`ClinicStatsManager.kt:64-69` builds the `visitDatesById` bridge that its own comment calls
the Finance tab's single source of truth. ⚠️ `ClinicStatsManager.getClinicStats()` has **zero
tests**.

## Upcoming-vaccine grouping

`UpcomingVaccineGroupingTest.kt` pins the whole contract, including two subtle ones:

- Brands are keyed by **catalog id, not display name**.
- The total of brand counts **equals** what
  `getUpcomingVaccinationsByVaccineId` would return — i.e. card count == detail count.

The SQL side matches the JSON text exactly (`DueReminderDao.kt:121-122`:
`instr(COALESCE(nxt_vaccine_id, ''), '"' || :vaccineId || '"') > 0`). The older delimited
`LIKE` match could never hit JSON-quoted ids and returned 0 rows for every brand — see
[[Quality/Bug Register]] NCC-041.

⚠️ But `VaccineDetailViewModel.kt:91-95` **re-filters in Kotlin**, dropping any row whose
`cleanVaccineName(reminder.vaccineName)` does not case-insensitively equal the catalog
`brandName` — and that re-filter has **zero tests**. Rows saved before
`ReminderRepositoryImpl.kt:173-177` began normalizing `vaccineName` to the catalog brand
would be counted on the card and hidden in the list.

## Confirmed findings

| # | Finding | Severity | Evidence |
|---|---|---|---|
| 1 | `FullReportViewModel.computeDaily` double-counted duplicate vaccination income | **High** | Was `FullReportViewModel.kt:282`; now `effectiveIncome` at `:298` (NCC-008) |
| 2 | Three statistics ViewModels had no `.catch` on terminal flows — `isLoading` could never return to false | Medium | Was `FinanceDetailsViewModel.kt:50-57`, `FullReportViewModel.kt:66-94`, `StatisticsViewModel.kt:55-95`; now `try`/`finally` `:50-61`, `.catch` `:99-106`, `.catch` `:100` (NCC-016) |
| 3 | `VaccineDetailViewModel` re-filter discards the error message and has no test | Medium | `:99-104`, `:91-95` |
| 4 | Reminder scheduling uses device time; all statistics use IST | Medium | `ReminderScheduler.kt:39` |
| 5 | `AgeUtils` milestone table labels `"16–17 Months"` **twice** with different dates | Cosmetic | `core/common/AgeUtils.kt:42-43` |
| 6 | `StatisticsDateUtils.todayIST()` KDoc says "time zeroed"; nothing is zeroed | Low | `StatisticsDateUtils.kt:25-30` |
| 7 | `getNextAgeMilestone` looks only 2 months ahead | Low | `AgeUtils.kt:24` |

Ranked in [[Known Bugs]].

## Related

- [[Due Vaccination]] — `dueToday`/`overdue` share `DateClassifier`
- [[Inventory Management]] — `lowStockCount` and the `<=` boundary
- [[Room Database]] — why the soft-delete concern does not reproduce
- [[Testing]] — what is and is not covered in this feature
