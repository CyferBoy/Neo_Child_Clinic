# Technical Debt

> Duplication, dead code, and complexity — verified against call sites, not inferred.
> Related: [[Known Bugs]] · [[Testing]] · [[Architecture Decisions]]

## The one-line summary

> The codebase extracts pure logic beautifully and tests it — then wires it into
> untested orchestrators, where the bugs live.

Every dead-code item below was confirmed by **zero call sites**, not by inspection.

## Dead code — verified unreachable

### DAO methods (~24)

| DAO | Unused methods |
|---|---|
| `VaccinationDao` | `getAllVaccinationsPage`, `getVaccinationsForPatientPage`, `getDueCount` |
| `DueReminderDao` | `getAllDueReminders`, `getAllCompletedReminders`, `getAllDismissedReminders`, `getDueRemindersPage`, `getCompletedRemindersPage`, `getDismissedRemindersPage`, `getDueReminder` |
| `VaccineDao` | `getBatchesByVaccineSync`, **`getAuditCountForVaccine`**, **`getAuditCountExcludingCreation`** |
| `AuditLogDao` | `getLogsBefore`, `getLogsForModulePage` |
| `FinanceDao` | `getTransactionsInRangePage`, `getAmountSumInRange`, `getTransactionCountInRange` |
| `BorrowDao` | `getActiveBorrowsPage`, `getReturnedBorrowsPage` |
| `BorrowReturnDao` | `getAllReturnsPage` |
| `ConsultationDao` | `getAllConsultationsPage`, `getConsultationsForPatientPage`, `getConsultationsForPatientForPage` |
| `BackupDao` | `getHistorySnapshot`, `trimHistoryOlderThan`, `deleteHistory` |

The pagination layer in particular is **built but unwired** — every screen still reads the
unbounded `Flow` version. Each unused method carries a multi-line comment explaining its
future use.

⚠️ The two bolded `VaccineDao` methods are not merely unused — they are **wrong**: they query
`audit_logs.remarks` for a brand name, but the brand lives in `old_value`/`new_value`
(`InventoryRepositoryImpl.kt:165` writes `"Vaccine Definition: ${vaccine.brandName}"`).
They are dead-and-wrong, which also means the destructive-delete guard they were meant to
provide is **enforced nowhere**.

### Entities and columns

| Item | Evidence |
|---|---|
| `VaccineBatchEntity.reservedQuantity` | declared, never read or written |
| `Vaccine.doseSchedule` | `domain/model/Vaccine.kt:13`; two mapper lines, **no reader anywhere** — there is no clinical schedule engine in this app |
| `Vaccine.stock`, `Vaccine.isLowStock` | never populated |
| `Reminder` fields `vaccinationSource`, `reminderDate`, `source`, `lastReminderTime`, `notificationSent` | permanently `null` — all `@Transient` on `ReminderEntity.kt:61-65` |
| `inventory_deductions` as a table | **no sync descriptor** — deductions recorded here never reach the server |

### Functions

| Item | Evidence |
|---|---|
| `InventoryRepository.deductStock` — 50 lines of FEFO spread | zero callers. Its `getActiveBatchesByExpiry` query is also only reachable from it — and that query has the same `"d MMM yyyy"` string-sort defect |
| `ReminderRepository.getAuditTrail` | no consumer — and it maps audit fields to **semantically wrong targets** (`ReminderRepositoryImpl.kt:436-451`) |
| `SyncViewModel.processSync()` | no UI or other caller |
| `SyncViewModel.clearSynced()` → `clearSyncedItems()` → `SyncQueueDao.clearSynced()` | no UI caller; the whole path is unreachable |
| `SyncItem.toEntity()` | never called |
| WorkManager tags `"SYNC_JOB"`, `"IMMEDIATE_SYNC"` | set, never queried |
| `BackupProgress.TableDone` | handled in `BackupViewModel.kt:76`; nothing emits it |
| `NotificationHelper.cancelSummaryNotification()`, `cancelAll()` | no callers |
| `MIGRATIONS 23→24` | an intentional empty hop, kept for version contiguity |

## Duplication

### Four implementations of FEFO batch selection

`AddVaccinationLogic.kt:12-15` · `BorrowBatchSelection.kt:38-40` ·
`InventoryRepositoryImpl.kt:384-409` · SQL `VaccineDao.kt:32`.

Plus **five** variants of the "usable batch" predicate, using **two** different expiry
predicates (`isExpired` vs `isExpiredAsOf`):
`InventoryRepositoryImpl.kt:72`, `AddVaccinationLogic.kt:14`, `VaccineRowCard.kt:87`,
`BorrowBatchSelection.kt:29`, `WasteViewModel.kt:52`.

### Twelve copies of the same `refresh()` boilerplate

Byte-identical modulo the repository call:

```kotlin
if (_isRefreshing.value) return
viewModelScope.launch {
    _isRefreshing.value = true
    try { x.refresh() } catch (_: Exception) {}      // or { }
    _isRefreshing.value = false
}
```

`BorrowedViewModel.kt:158`, `CompletedDismissedViewModel.kt:63`, `DueViewModel.kt:99`,
`VaccineInventoryViewModel.kt:63`, `WasteViewModel.kt:74`, `WeeklyDoctorSlotsViewModel.kt:49`,
`FullReportViewModel.kt:107`, `FinanceDetailsViewModel.kt:59`, `MilestonePatientsViewModel.kt:34`,
`VaccineDetailViewModel.kt:108`, `PersonalReminderViewModel.kt:101`, `AdminViewModel.kt:50`.

### Three ad-hoc workarounds for the 5-argument `combine` limit

| Shape | Where |
|---|---|
| `private data class RefreshState<A,B,C,D,E>` | `PatientListViewModel.kt:221` |
| `private data class Quad<A,B,C,D>` | `VaccineInventoryViewModel.kt:111` |
| raw `values[0] as List<T>` casts | `BorrowedViewModel.kt:86`, `PersonalReminderViewModel.kt:65`, `StatisticsViewModel.kt:71`, `FullReportViewModel.kt:77`, `CompletedDismissedViewModel.kt:34` |

Each carries its own `@Suppress("UNCHECKED_CAST")` block. ✅ Fix: kotlinx.coroutines already
ships `combine(vararg)` overloads and a `combine` for up to 5 flows.

### Session-status fallback logic in two files

`app/Navigation.kt:50-60` and `app/MainActivity.kt:202-204` — the same decision, written
twice.

### Three return-label conventions for the same call

`SessionManager.getCurrentUserEmail()` → `"Unknown"` (`SessionManager.kt:42`);
`DueViewModel.kt:50` maps it to `"Unknown Staff"`;
`WeeklyDoctorSlotsViewModel.kt:213` maps it to `null`.

### Duplicate ViewModel instances causing duplicate work

| ViewModel | Instantiated | Consequence |
|---|---|---|
| `AdminViewModel` | 4 screens (`ManageStaffScreen.kt:37`, `StaffDetailsScreen.kt:34`, `EditStaffScreen.kt:28`, `AddStaffScreen.kt:32`) | **4 independent `init { fetchStaff(); observeStaff() }` = 4 remote refreshes per session** |
| `DashboardViewModel` | 2 (`DashboardScreen.kt:58`, `NavigationPatientGraph.kt:149`) | **duplicate full 15-table sync** on every Dashboard → Today's Patients navigation |
| `NotificationSettingsViewModel` | 3 unrelated screens | 3 instances of what is really one app-level preference |

### `Routes` duplicated as literals

`DashboardScreen.kt:218-233` re-declares 10 route strings instead of referencing
`Routes.*` — and **4 of the 10 branches are dead**, because `AppDrawer.kt` only ever emits 6
routes (`"patient_list"`, `"due"`, `"vaccine_inventory"`, `"statistics"` are never sent).

### `PatientUtils` is a pass-through facade with mutual recursion

`core/common/PatientUtils.kt:11-57` — 19 one-line delegations to `AgeUtils`/`DateUtils`, and
`AgeUtils`/`DateClassifier` **call back into** `PatientUtils.parseDate`
(`AgeUtils.kt:26,69,86,109`; `DateClassifier.kt:26,62,79-80`). The facade exists purely for
call-site compatibility (comment at `:7-10`).

## Complexity

| File | Lines | Concern |
|---|---|---|
| `feature/inventory/data/InventoryRepositoryImpl.kt` | 660 | 19 `override`s; `addStockBatch` is ~10 validation branches interleaved with writes |
| `feature/vaccination/presentation/AddVaccinationViewModel.kt` | 707 | `saveVaccination` alone is 220 lines |
| `core/sync/SyncUploader.kt` | 674 | a 22-entry static registry + per-entity conflict rules + 6 hand-rolled PostgREST call shapes, all in `core/` |
| `feature/dashboard/presentation/DashboardViewModel.kt` | 439 | a 4-level nested `combine`; 13 injected deps |
| `core/security/AuthViewModel.kt` | 369 | login + profile + device registration + the sign-out data-erase gate |
| `data/local/dao/BackupDao.kt` | 272 | a second DAO over 20 tables, re-validated by Room on every schema change |
| `data/backup/BackupRestorer.kt` | 318 | the most destructive code in the app, with **zero tests** |

### `AuditLogger`'s dual API is the biggest footgun in the codebase

`log(...)` (fire-and-forget, escapes transactions) and `recordLog(...)` (suspend, joins
transactions) are used interchangeably at **20 call sites**; 5 of the `log()` calls are
*inside* live transactions. **Collapsing to `recordLog` would remove an entire class of bug.**

## Stale documentation inside the code

| Claim | Reality |
|---|---|
| `AuditLogDao.kt:30-33` — "indexed on … entityType+entityId" | **No composite index exists** — 6 single-column indices only |
| `DueReminderDao.kt:88` — "UpcomingVaccinationRulesTest asserts they agree with the Kotlin rules" | **No such test exists** — grep returns only this comment |
| `ExpenseDaoTest.kt:21-22` — coverage includes "soft-deleting an expense" | `expenses` has no soft-delete; dropped in 27→28 |
| `ExpenseMigrationTest.kt:18-23` — migrations are local vals in `getInstance()`; `MigrationTest.kt` references a non-existent constant | **Both stale** — migrations are companion `internal val`s; no `MigrationTest.kt` exists |
| `DatabaseModule.kt:139-142` — "SyncRepositoryImpl takes AppDatabase" | it takes `SyncQueueDao` |
| `20260929000000_security_baseline.sql:35` — "keep this list explicit so adding a new remote table cannot silently inherit access" | That list governs **policies**, not **grants** — and no migration issues a **table** `GRANT` (four `grant execute on function` statements do exist) |
| `StatisticsDateUtils.kt:25-30` — "time zeroed" | Nothing is zeroed |

## ⚠️ A comment-density problem

Comment-to-code ratio is often 3:1. The comments are accurate and genuinely useful — but in
several places a comment **asserts the opposite of what the code does**, which is worse than
no comment:

| Comment | Code |
|---|---|
| `SingleFlight.kt:14-18` — claims the shared-result bug is fixed | `SyncRepositoryImpl.kt:103-107` still discards it |
| `VaccinationEditEngine.kt:180-183` — "a FAILED batch never moves stock in **either direction**" | only the negative direction is capped — see [[Known Bugs]] §1.1 |
| `20261013140000:9-10` — "the client's absolute sync … clamps at 0" | the uploader omits the column entirely |
| `20261016:16-17` — "the trigger keeps working (it runs as the table owner)" | the `force` on the next line inverts this |

When you change behaviour, **update the comment in the same diff**.

## What is genuinely good

Worth preserving through any refactor:

- **Pure domain engines** with no Android imports — 7 of them, all tested.
- **`DateUtils.parseDate`** — format ordering, IST anchoring, and bounded caching are all
  deliberate and documented.
- **`BackupCrypto`** — iteration-count bounds, unsigned length rebasing, typed errors.
- **`MemoryCache`** — monotonic clock, lock-guarded, bounded, 10 tests.
- **`CloudRefresh`** — always rethrows session failures even when asked to swallow.
- **The sign-out data-erase gate** in `AuthViewModel.kt:296-345`.
- **`reminders` is append-only server-side** (`audit_logs` policies are select+insert only).

## Related

- [[Known Bugs]] — what this debt has already cost
- [[Testing]] — the coverage gap this debt created
- [[Architecture Decisions]] — the reasoning behind the structure
- [[Feature Roadmap]] — the suggested order of work
