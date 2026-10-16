# Testing

> 43 test files. 252 unit tests passing. Where the coverage actually is — and where it is not.
> Related: [[Known Bugs]] · [[Room Database]] · [[Error Handling]]

## The headline number

| Metric | Value |
|---|---|
| Unit test files (`app/src/test`) | **38** |
| Instrumented test files (`app/src/androidTest`) | **5** |
| Total test lines | ~4,470 (vs ~47,230 in main — **9.5%**) |
| Unit tests passing | **252, 0 failures, 0 errors** |
| Last recorded run | `app/build/test-results/testDebugUnitTest/`, **2026-10-16 14:33** |
| Instrumented tests | **no recorded result** — they need a device |

🔍 The 252 figure is the last recorded result, not a run performed for this vault.

## The pattern that explains everything

> **Every extracted pure function is tested. Every orchestrator is not.**

The 38 unit test files cover pure/domain logic almost exclusively: `MemoryCache`,
`PatientCache`, `DateClassifier`, `InventoryUtils`, `InventoryStockMovement`,
`VaccinationEditEngine.inventoryDiff`, `EditReconciler`, `ReminderDueListProcessor`'s
neighbours, `FinanceCalculator`, `ExpenseCalculator`, `StatisticsUtils`, `BackupCrypto`,
`BackupValidator`.

🔍 **Zero `*ViewModelTest.kt` files exist.** No coverage of `stateIn` wiring, no coverage of
an error path, no coverage of a `combine` chain.

## The 4 instrumented tests matter more than their count suggests

**They are not run by `./gradlew test`.** `ExpenseMigrationTest.kt:28-30` says so outright:

> *"It is currently @Ignore'd: only 33.json was ever exported… androidTest is not part of the
> default test run, so this reported green without ever executing."*

That last clause is the finding: **the default `test` task runs exactly one
local-persistence test** — `AppDatabaseMigrationTest`, which asserts migration contiguity
and nothing else. All DAO SQL correctness is invisible to CI.

| File | Covers | Notable assertions |
|---|---|---|
| `ExpenseDaoTest.kt` | `ExpenseDao` | delete removes **physically**; category+method+date filters; amount/date sort; limit/offset paging |
| `PatientVisitPriceQueryTest.kt` | unpaid-patient + batch-item queries | flags when any visit unpaid; **not** flagged with zero visits; flags on exactly 0 but not a small positive; **re-emits when a price changes** |
| `UpcomingVaccinationDaoTest.kt` | `getUpcomingVaccinationsByType` / `ByVaccineId` (15 tests) | brand match **exact not substring**; `softDeletedRemindersAreExcluded`; legacy NULL/empty `nxt_vaccine_id` excluded; **type card count == sum of brand counts** |
| `DateGivenIndexMigrationTest.kt` | migration 32→33 | adds the index without touching data; idempotent |
| `ExpenseMigrationTest.kt` | migration 22→23 | `@Ignore`d — **never executed** |

`UpcomingVaccinationDaoTest` is genuinely good work: its `EXPLAIN QUERY PLAN` assertions and
the "card count == detail count" invariant are exactly the tests this codebase needs more of.

## DAO coverage: 4 of 21

| Covered | Uncovered |
|---|---|
| `PatientDao`, `VaccinationDao`, `VaccinationItemDao` (via `PatientVisitPriceQueryTest`) | `VaccineDao`, `SyncQueueDao`, `DueReminderDao` (7 of 16 queries), `WasteDao`, `WidgetDueDao`, `AuditLogDao`, `FinanceDao`, `BorrowDao`, `BorrowReturnDao`, `PatientNotesDao`, `PatientTodoDao`, `PersonalReminderDao`, `ProfileDao`, `InventoryDeductionDao`, `ConsultationDao`, `DoctorAvailabilityDao`, `BackupDao` |

`BackupDao` is a 272-line DAO over 20 tables with **zero** tests.

## The 12 untested critical paths, ranked

| # | Path | Risk if wrong |
|---|---|---|
| 1 | **`BackupRestorer`** — 318 lines, the most destructive code in the app | A wrong FK ordering fails mid-transaction; a wrong `appliedIds` set re-queues rows never written |
| 2 | **`ClinicalVaccinationService.recordVaccination` / `deductInventoryForNewVaccination`** | The entire create-time stock path: COMPLETED/FAILED ledger distinction, PARTIAL status, the narrow-catch contract. **Where two live bugs sit.** |
| 3 | **`VaccinationEditEngine.applyInventoryDiff` + `reconcileInventoryDeductions`** | Only the pure `inventoryDiff` is tested. The reversal cap, `allowExpired`, shortfall capture, and the 5-branch status rule are not. |
| 4 | **`ReminderDueListProcessor.process()` / `.stats()`** | The most load-bearing pure function in the reminder subsystem. **Zero tests.** |
| 5 | **`InventoryRepositoryImpl` stock mutations** (`deductStockFromBatch`, `addStockToBatch`, `reverseDeduction`, `updateBatch`, `addBatch`) | Every stock write in the app. Only the two pure helpers they call are tested. |
| 6 | **`VaccinationRepositoryImpl.deleteVaccination`** | The 6-step transactional delete, the idempotency latch, the child-before-parent enqueue order |
| 7 | **`SyncRepositoryImpl.processNextItems` / `processGroup` / `classifyAndRecordError` / session gate** | Zero tests. The suite only tests the pure helpers **extracted** from them — which is exactly why the `SingleFlight` wrapper bug survives. |
| 8 | **`BorrowRepositoryImpl`** (`saveBorrowedItem`, `submitReturn`, `deleteBorrowedItem`) | The quantity-edit diff, the outstanding cap, the cross-batch release |
| 9 | **`WasteRepositoryImpl`** (all three mutators) | restore-then-deduct, `wastedQuantity` unwind |
| 10 | **`DailySummaryWorker`** | The once-per-day guard, the "only mark when non-empty" rule, the low-stock notify/clear machine |
| 11 | **`PersonalReminderNotificationWorker` + `ReminderScheduler`** | The undated/today/overdue/future matrix; the "past today's time → +1 day" rollover |
| 12 | **`SafetyBackupStore` + `BackupCollector`** | Stage/commit/discard atomicity; the FCM-token strip — the only privacy guarantee in the export |

Also untested: `ClinicStatsManager.getClinicStats()` (including the `visitDatesById`
reporting-date bridge its own comment calls the Finance tab's single source of truth),
`FullReportViewModel.computeMonthly`/`computeDaily`, `StatisticsDateUtils` directly,
`BackupViewModel`'s 6 unguarded launches, `CloudBackupApi`'s status mapping, and both
notification-id computations.

## Anti-vacuous test design — one test does this right

`core/sync/SyncEntityRegistryTest.kt` scans the sources to assert every `enqueue(entityName)`
exists in the registry — **and asserts that the scan actually scanned something.** Without
that second assertion the test would pass vacuously if the glob broke.

This is the pattern to copy for any source-scanning test. It is also why
`SyncOrderingTest` can drift silently — see below.

## Tests that will not catch the bugs they appear to

| Test | Why it misses |
|---|---|
| `SingleFlightResultTest.kt` | exercises `flight.run { }`'s return value, **not** the `SyncRepositoryImpl.kt:103-107` wrapper that discards it |
| `SyncOrderingTest.kt` | asserts against a **hand-copied** priority table (`:17-26`) that already disagrees with `SYNC_ENTITY_REGISTRY` — `TRANSACTION`, `AUDIT_LOG`, `PROFILE`, `STAFF` are absent from the test's `when`. It does not assert against the registry, so the two can drift silently. |
| `VaccinationEditEngineTest.kt` | 3 tests covering only the pure `inventoryDiff`; `applyInventoryDiff` and `reconcileInventoryDeductions` are untested |
| `ExpenseMigrationTest.kt` | `@Ignore`d — never executed |
| `DateGivenIndexMigrationTest.kt` | re-implements migration 32→33 with a hand-built 5-column `patient_visits` fixture, so `MigrationTestHelper.runMigrationsAndValidate` semantics are not exercised |

## Schema-migration coverage

✅ Only **one** schema file has ever been exported:
`app/schemas/…/AppDatabase/33.json`. The KSP wiring is correct
(`app/build.gradle.kts:29-31`) and it is packaged into the androidTest APK (`:74-80`), so the
reasoning is right — the migrations just were never run with it.

| Migration | Tested? |
|---|---|
| 17→18 … 21→22, 24→25 … 30→31 | **no** |
| 22→23 | `@Ignore`d, and it re-declares the SQL rather than referencing the real object |
| 31→32 (destructive — mass DELETE + 54 column drops) | **no** |
| 32→33 | re-implemented, not the real object |

`AppDatabaseMigrationTest` asserts **contiguity only** — it never checks that `MIGRATIONS`
matches the `@Database(version =)` annotation, nor that entity↔schema identity holds.

## 🔍 Not checked

- The instrumented suite was never run here; its 5 files have no recorded result.
- `supabase/tests/rls_role_test.sql` and `verify_security.sql` exist but were not executed —
  they need a live database.
- No coverage measurement (JaCoCo) is configured.

## Related

- [[Known Bugs]] — where most of these gaps hide
- [[Error Handling]] — why untested error paths matter most here
- [[Room Database]] — 17 of 21 DAOs untested
- [[Technical Debt]] — the dead code that tests would have caught
