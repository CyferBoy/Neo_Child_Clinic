# Testing

> 49 test files. 277 unit tests passing. Where the coverage actually is — and where it is not.
> Related: [[Known Bugs]] · [[Room Database]] · [[Error Handling]]

## The headline number

| Metric | Value |
|---|---|
| Unit test files (`app/src/test`) | **41** |
| Instrumented test files (`app/src/androidTest`) | **8** (56 `@Test`, 1 `@Ignore`d) |
| Total test lines | ~5,642 (vs ~47,749 in main — **11.8%**) |
| Unit tests passing | **277, 0 failures, 0 errors** |
| Last recorded run | `./gradlew :app:testDebugUnitTest`, **2026-10-18** |
| Instrumented tests | 🔍 **green in CI 2026-10-18** on API 34 (CI-recorded, not reproduced here) — see below |

✅ The 277 figure and the instrumented result are both from runs performed while writing this
vault, not carried over.

| Lines | Count |
|---|---|
| `app/src/main` | 47,749 |
| `app/src/test` | 4,118 |
| `app/src/androidTest` | 1,524 |

## The pattern that explains everything

> **Every extracted pure function is tested. Every orchestrator is not.**

The 41 unit test files cover pure/domain logic almost exclusively: `MemoryCache`,
`PatientCache`, `DateClassifier`, `InventoryUtils`, `InventoryStockMovement`,
`VaccinationEditEngine.inventoryDiff`, `EditReconciler`, `ReminderDueListProcessor`'s
neighbours, `FinanceCalculator`, `ExpenseCalculator`, `StatisticsUtils`, `BackupCrypto`,
`BackupValidator`.

🔍 **Zero `*ViewModelTest.kt` files exist.** No coverage of `stateIn` wiring, no coverage of
an error path, no coverage of a `combine` chain.

## The 8 instrumented tests matter more than their count suggests

**They are not run by `./gradlew test`** — they need a device. That used to mean DAO SQL
correctness was invisible to CI, and for a long time it was: `ExpenseMigrationTest.kt` says the
quiet part out loud in its own KDoc —

> *"It is currently @Ignore'd: only 33.json was ever exported… androidTest is not part of the
> default test run, so this reported green without ever executing."*

✅ **That is no longer true.** `.github/workflows/android-instrumented.yml` runs
`connectedDebugAndroidTest` on an API 34 emulator for every push and pull request. Its first real
execution is the most useful thing to have happened to this suite: 17 cases failed on a foreign-key
violation they had never been able to reach, and once their fixtures were seeded, **12 more failed
on a real product bug** — `getUpcomingVaccinationsByVaccineId` was matching a JSON column as
comma-separated text and returned nothing for every brand ([[Quality/Bug Register]] NCC-041).

The job is **advisory**: it is a separate workflow, so a device-level failure never blocks the main
build. Make it blocking through branch protection when it has a longer green history.

| File | Covers | Notable assertions |
|---|---|---|
| `ExpenseDaoTest.kt` | `ExpenseDao` (8) | delete removes **physically**; category+method+date filters; amount/date sort; limit/offset paging |
| `PatientVisitPriceQueryTest.kt` | unpaid-patient + batch-item queries (9) | flags when any visit unpaid; **not** flagged with zero visits; flags on exactly 0 but not a small positive; **re-emits when a price changes** |
| `UpcomingVaccinationDaoTest.kt` | `getUpcomingVaccinationsByType` / `ByVaccineId` (16) | brand match **exact not substring**; `_` in an id stays literal; `softDeletedRemindersAreExcluded`; legacy NULL/empty `nxt_vaccine_id` excluded; **type card count == sum of brand counts** |
| `ReminderSoftDeleteFilterTest.kt` | soft-delete filtering across `DueReminderDao` (12) | every pre-existing filter still excludes deleted rows, plus the new ones |
| `BatchPullGuardEquivalenceTest.kt` | batched pull guards — `SyncQueueDao.unsyncedIdSet`, `PatientDao.getAllPatientsSnapshot` (2) | the bulk query answers exactly what the per-row form did, per status and per entity name; a missing entity name returns empty; the snapshot lookups agree with per-row reads |
| `PullUpsertPreservesChildrenTest.kt` | `@Upsert` write paths used by pulls and edits (6) | a parent upsert leaves visits, items, consultations, reminders, borrows and returns intact; `REPLACE` still cascades (the hazard the upserts exist for); a `clinicId` collision never deletes the other patient |
| `DateGivenIndexMigrationTest.kt` | migration 32→33 (2) | adds the index without touching data; idempotent; `EXPLAIN QUERY PLAN` loses its temp B-tree |
| `ExpenseMigrationTest.kt` | migration 22→23 (1) | `@Ignore`d — **never executed** |

`UpcomingVaccinationDaoTest` is genuinely good work: its plan and "card count == detail count"
invariants are exactly the tests this codebase needs more of. They also proved their worth
immediately — see [[Quality/Bug Register]] (“NCC-041 — Brand drill-down returned nothing for every brand”).

⚠️ **A test can run, pass, and verify nothing.** `DateGivenIndexMigrationTest` asserted with
Kotlin's `assert(...)`, which is a no-op unless the JVM runs with `-ea` — impossible for a device
test. Seven assertions, including the two the class exists for, could not fail. They are JUnit
assertions now and the workflow rejects a bare `assert` in `androidTest` outright. See
[[Quality/Bug Register]] (“NCC-042 — seven instrumented assertions that could not fail”). Anything in this table is worth a second look for the same pattern.

## DAO coverage: 5 of 21, up from 3

| Covered | Uncovered |
|---|---|
| `ExpenseDao` (`ExpenseDaoTest`) · `PatientDao`, `VaccinationDao`, `VaccinationItemDao` (`PatientVisitPriceQueryTest`) · `DueReminderDao` — the upcoming and soft-delete queries only (`UpcomingVaccinationDaoTest`, `ReminderSoftDeleteFilterTest`) | `VaccineDao`, `SyncQueueDao`, `WasteDao`, `WidgetDueDao`, `AuditLogDao`, `FinanceDao`, `BorrowDao`, `BorrowReturnDao`, `PatientNotesDao`, `PatientTodoDao`, `PersonalReminderDao`, `ProfileDao`, `InventoryDeductionDao`, `ConsultationDao`, `DoctorAvailabilityDao`, `BackupDao` |

✅ `DueReminderDao` was listed here as uncovered, one of "7 of 16 queries" with no test at all.
It now has 28 instrumented cases behind it, and the two that mattered were red on the first run
that could execute them.

`BackupDao` is a 272-line DAO over 23 tables with **zero** tests of its own — its
`getAllRemindersForBackup` query is exercised incidentally by `ReminderSoftDeleteFilterTest.kt:196`
and `:226`.

## The 12 untested critical paths, ranked

| # | Path | Risk if wrong |
|---|---|---|
| 1 | **`BackupRestorer`** — 318 lines, the most destructive code in the app | A wrong FK ordering fails mid-transaction; a wrong `appliedIds` set re-queues rows never written |
| 2 | **`ClinicalVaccinationService.recordVaccination` / `deductInventoryForNewVaccination`** | The entire create-time stock path: COMPLETED/FAILED ledger distinction, PARTIAL status, the narrow-catch contract. **Where two live bugs sit.** |
| 3 | **`VaccinationEditEngine.applyInventoryDiff` + `reconcileInventoryDeductions`** | Only the pure `inventoryDiff` and `deductionStatus` are tested. The reversal cap, `allowExpired` and shortfall capture are not. |
| 4 | **`ReminderDueListProcessor.process()` / `.stats()`** | The most load-bearing pure function in the reminder subsystem. **Zero tests.** |
| 5 | **`InventoryRepositoryImpl` stock mutations** (`deductStockFromBatch`, `addStockToBatch`, `reverseDeduction`, `updateBatch`, `addBatch`) | Every stock write in the app. Only the two pure helpers they call are tested. |
| 6 | **`VaccinationRepositoryImpl.deleteVaccination`** | The 6-step transactional delete, the idempotency latch, the child-before-parent enqueue order |
| 7 | **`SyncRepositoryImpl.processNextItems` / `processGroup` / `classifyAndRecordError` / session gate** | Zero tests. The suite only tests the pure helpers **extracted** from them — which is exactly how the `SingleFlight` wrapper bug (`NCC-009`, since fixed) slipped through. |
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
| `SingleFlightResultTest.kt` | ✅ **Resolved** — asserts the shared result (`:47`, `:68`), which the wrapper now returns (`SyncRepositoryImpl.kt:103-104`); the wrapper itself still has no regression test |
| `SyncOrderingTest.kt` | asserts against a **hand-copied** priority table (`:17-26`) that already disagrees with `SYNC_ENTITY_REGISTRY` — `TRANSACTION`, `PROFILE`, `STAFF` are absent from the test's `when` (it does include `AUDIT_LOG`, `SyncOrderingTest.kt:23`). It does not assert against the registry, so the two can drift silently. |
| `VaccinationEditEngineTest.kt` | 8 tests covering only the pure `inventoryDiff` and `deductionStatus`; `applyInventoryDiff` and `reconcileInventoryDeductions` are untested |
| `ExpenseMigrationTest.kt` | `@Ignore`d — never executed |
| `DateGivenIndexMigrationTest.kt` | re-implements migration 32→33 with a hand-built 5-column `patient_visits` fixture, so `MigrationTestHelper.runMigrationsAndValidate` semantics are not exercised. Its assertions were also no-ops until 2026-10-18 — see [[Quality/Bug Register]] (“NCC-042 — seven instrumented assertions that could not fail”) |

## Schema-migration coverage

✅ Only **one** schema file has ever been exported:
`app/schemas/…/AppDatabase/33.json`. The KSP wiring is correct
(`app/build.gradle.kts:29-31`) and it is packaged into the androidTest APK (`:82-86`), so the
reasoning is right — the migrations just were never run with it.

| Migration | Tested? |
|---|---|
| 17→18 … 21→22, 24→25 … 30→31 | **no** |
| 22→23 | `@Ignore`d, and it re-declares the SQL rather than referencing the real object |
| 31→32 (destructive — mass DELETE + 54 column drops) | **no** |
| 32→33 | re-implemented, not the real object |

`AppDatabaseMigrationTest` checks more than contiguity: the floor is 17 (`:17-21`), the chain
is contiguous (`:23-29`), it ends at `DB_VERSION` (`:31-35`, a hand-maintained mirror of the
annotation — `AppDatabase.kt:79`) and start versions are unique (`:37-41`). It still never
checks that `MIGRATIONS` matches the `@Database(version =)` annotation (`AppDatabase.kt:42`),
nor that entity↔schema identity holds.

## 🔍 Not checked

- The instrumented suite runs in CI, but **not here** — this environment has no usable emulator,
  so every androidTest assertion above is reported from CI, not reproduced locally. No androidTest
  result XML is in the tree — `app/build/` is absent, and build outputs are not committed.
- `supabase/tests/rls_role_test.sql` and `verify_security.sql` exist but were not executed —
  they need a live database.
- No coverage measurement (JaCoCo) is configured, so every percentage here is lines, not branches.
- The 2026-10-18 instrumented run was **green** (`BUILD SUCCESSFUL`, Gradle exited 0) while its
  JUnit XML reported `tests=48 failures=1 errors=0 skipped=0`. The single failure is the
  `@Ignore`d `ExpenseMigrationTest` case, whose body cannot execute, so no assertion is being
  masked. The runner counted an ignored test as a failure rather than a skip, and that node
  carries an empty stack trace; the mechanism is not established, because the raw `am instrument`
  output is not captured. **Treat Gradle's exit code as the gate, not the XML's `failures`
  counter.**

## Related

- [[Known Bugs]] — where most of these gaps hide
- [[Error Handling]] — why untested error paths matter most here
- [[Room Database]] — 16 of 21 DAOs untested
- [[Technical Debt]] — the dead code that tests would have caught
