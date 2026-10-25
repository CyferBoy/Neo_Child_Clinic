# Test Coverage and Evidence Plan

## 1. Environment and execution status

| Item | Value |
| --- | --- |
| Java | OpenJDK 25.0.3 (Termux) |
| Android SDK | **not installed** (`ANDROID_HOME` empty, no `local.properties`) |
| Emulator/device | none attached |
| Gradle test run | **not executed** |
| Supabase CLI / Docker | not available |
| Test files | 41 local unit, 8 instrumentation |
| Exported Room schemas | **only v33** |

No test result in this audit is a pass. “Verified” below means source inspection or a standalone arithmetic simulation, not a project test.

## 2. Existing tests

### Unit (`app/src/test`, 41 files)

Representative coverage:

| Area | Tests |
| --- | --- |
| Sync ordering/grouping/failure | `SyncOrderingTest`, `SyncGroupFailureTest`, `SyncEntityRegistryTest`, `SyncDescriptorRemoteColumnsTest`, `SyncErrorClassificationTest`, `SingleFlightTest`, `SingleFlightResultTest`, `CloudRefreshTest`, `RefreshStepTest` |
| Vaccination edit/create/delete | `VaccinationEditEngineTest`, `RecordVaccinationTransactionTest`, `VaccinationDeletionTest`, `EditReconcilerTest`, `AddVaccinationLogicTest`, `VaccinationNextDueDateTest` |
| Inventory | `InventoryStockMovementTest`, `InventorySortKeysTest`, `InventoryUtilsExpiryTest` |
| Borrow | `BorrowBatchSelectionTest` |
| Finance/statistics | `FinanceCalculatorTest`, `ExpenseCalculatorTest`, `ExpenseValidatorTest`, `StatisticsUtilsFilterTest`, `VisitTypeStatsTest` |
| Dates/age | `DateClassifierTest`, `PatientUtilsAgeMilestoneTest`, `PatientUtilsTest` |
| Cache/security | `PatientCacheTest`, `MemoryCacheTest`, `BiometricLockManagerTest`, `CurrentUserProviderTest` |
| DB/backup | `AppDatabaseMigrationTest` (chain contiguity only), `BackupCryptoTest`, `BackupValidatorTest`, `ExpenseEntityMappingTest` |
| UI state | route tests, `PatientHistoryUiStateTest`, `TodaySlotFilterTest`, `UpcomingVaccineGroupingTest` |

### Instrumentation (`app/src/androidTest`, 8 files)

| Test | What it actually covers | Gap |
| --- | --- | --- |
| `PullUpsertPreservesChildrenTest` | `@Upsert` does not REPLACE/cascade children | good |
| `BatchPullGuardEquivalenceTest` | pull guard equivalence | good |
| `ReminderSoftDeleteFilterTest` | all reminder reads filter soft-deleted rows (NCC-006) | good |
| `ExpenseDaoTest` | expense queries | good |
| `PatientVisitPriceQueryTest` | price query | good |
| `UpcomingVaccinationDaoTest` | upcoming query | good |
| `DateGivenIndexMigrationTest` | manually built v32→v33 index | not full schema; not `MigrationTestHelper` |
| `ExpenseMigrationTest` | legacy expense migration | **`@Ignore`d** because schema pair is missing |

## 3. Risk → test coverage map

| Risk | Existing coverage | Verdict |
| --- | --- | --- |
| Stock retry/idempotency | none (JVM tests cannot run Postgres triggers) | **uncovered, highest priority** |
| Vaccination edit double-deduct | `VaccinationEditEngineTest`, `VaccinationDeletionTest` | covered on JVM arithmetic, not DB integration |
| Finance heal overwrite | none | uncovered |
| Patient-delete reminder serverId | none | uncovered |
| Delete convergence / tombstones | none | uncovered |
| Queue dedupe race | none | uncovered |
| Borrow partial-return status | none (batch selection only) | uncovered |
| Timezone behavior | `DateClassifierTest`, `PatientUtilsTest`, but no finance/worker TZ matrix | partial |
| RLS / role matrix | none in repo | uncovered (SQL tests absent/not executed) |
| Pull upsert preserving children | instrumentation test | covered |
| Reminder soft-delete filters | instrumentation test | covered |
| Migration chain | contiguity test | partially covered; actual data migrations untested |
| Receipt numbering | none | server-only, untested here |
| Conflict ordering | `SyncOrderingTest` | covered |

## 4. Evidence ledger (what was actually executed)

| ID | Claim | Method | Command / artifact | Result |
| --- | --- | --- | --- | --- |
| E-01 | Repo is clean at the audited commit | git | `git status --porcelain`, `git rev-parse HEAD` | clean; `ca4bed4...` |
| E-02 | 25 Room entities, v33, index set | Python JSON extraction | scratch script over `33.json` | confirmed |
| E-03 | 21 DAOs / 230 queries | shell count | `ls .../dao/*.kt`, `grep -c @Query` | confirmed |
| E-04 | Stock UPDATE arithmetic corrupts stock | standalone Python simulation of the SQL branches | inline script (no DB) | 8 cases confirmed corrupt; DELETE correct |
| E-05 | Skills are valid and discoverable | Python frontmatter parse | `.opencode/skills/*/SKILL.md` | 6/6 valid |
| E-06 | No Android SDK | environment inspection | `echo $ANDROID_HOME`, `which java` | no SDK; Gradle tests not run |
| E-07 | Pull paths are upsert-only | source inspection | refresh methods across repositories | confirmed |
| E-08 | Patient delete uses local reminder id | source inspection | `PatientRepositoryImpl.kt:215-223`; `ReminderEntity.kt:145,168-170` | confirmed |
| E-09 | RLS broad CRUD + carve-outs | migration inspection | `20260929...:40-88`; `20261013...` | confirmed as written |
| E-10 | Stock history is remote-only | source inspection | `StockHistoryViewModel.kt:129-138,167-176` | confirmed |

Not executed: Gradle unit/instrumentation tests, Room migration helper tests, Supabase SQL/policy tests, `EXPLAIN`/benchmarks, live API calls.

## 5. Minimum regression suite to add

### T-01 — Trigger arithmetic (highest priority)

Environment: disposable Postgres/Supabase project.

```sql
-- setup
insert into vaccine_batches (id, remaining_quantity, ...) values ('b1', 0, ...);

-- 1. purchase +10 (INSERT)
insert into inventory_transactions (id, batch_id, quantity, transaction_type, ...)
values ('t1', 'b1', 10, 'PURCHASE', ...);
-- expect b1.remaining = 10

-- 2. retry the same upsert (UPDATE)
insert into inventory_transactions (...) values (...) on conflict (id) do update set quantity = excluded.quantity;
-- expect b1.remaining = 10, not 0

-- 3. edit -2 -> -3
-- expect a delta of -1, not -3 or +1

-- 4. move a row from b1 to b2
-- expect b1 += old, b2 += new

-- 5. delete
-- expect undo of old exactly once
```

Assertions must run in one transaction and roll back afterward.

**Status (2026-10-22):** implemented as `supabase/tests/stock_trigger_arithmetic.sql` (single transaction, rollback, 17 assertions including the negative guard). On PostgreSQL 18.2 the fixed migration passes; the old function fails at the first purchase retry with `expected 10, got 0`. The test has not been run against Supabase. See `16_Phase1_Stock_Trigger_Remediation.md`.

### T-02 — Finance pull vs pending edit

Room in-memory + fake PostgREST:

- local finance row `isSynced=false`, queue row PENDING;
- remote row same id with a different `transactionDate`;
- run `refreshTransactions()`;
- assert local amount/date unchanged and no UPDATE enqueued.

### T-03 — Patient-delete reminder identity

- Pull a remote reminder (`id=local-uuid`, `serverId=remote-uuid`);
- call `deletePatient`;
- assert the queued `REMINDERS` DELETE uses `remote-uuid`.

### T-04 — Borrow return status

- borrow 10, one prior return 1, stale dialog passes `remainingQuantity=10`;
- return 9;
- assert `isReturned`/status reflects outstanding 0, not the caller's stale parameter.

### T-05 — Delete convergence

- local row exists; remote row absent; no pending queue;
- run the refresh step;
- assert local row is removed or tombstoned.

### T-06 — Queue dedupe concurrency

- launch two concurrent `enqueue` calls for the same entity/operation;
- assert one PENDING row (requires a unique index or serialized transaction).

### T-07 — RLS role matrix

For each role (`admin`, `doctor`, `receptionist`, `nurse`, `inventory_manager`, inactive):

- SELECT/INSERT/UPDATE/DELETE each table category;
- assert allowed/denied matches the documented policy.

### T-08 — Timezone matrix

Run finance date resolution, dashboard “today”, stock-history filter and notification workers with device zones `Asia/Kolkata`, `UTC`, `America/New_York`, `Pacific/Kiritimati`; assert the clinic date is unchanged.

### T-09 — Clinic-id collision

- Seed two devices with the same local max clinic id;
- create a patient on each while offline;
- sync both;
- assert the second is retried with a new id (target) or fails without corrupting the first.

### T-10 — Patient merge preserves history

- Create a duplicate with a consultation + finance row + note + vaccination + reminder;
- merge into a master;
- assert every child now belongs to the master, none were deleted, and the duplicate is gone;
- assert the sync group orders moved-row UPDATEs before the duplicate DELETE.


## 6. Execution plan

1. **Static** — already done in this audit.
2. **JVM** — `./gradlew testDebugUnitTest` on a machine with the Android SDK; add T-02/T-03/T-04/T-06 as JVM tests with fakes/in-memory Room (Robolectric if needed).
3. **Instrumentation** — `./gradlew connectedDebugAndroidTest` for Room DAO/migration tests; add T-05.
4. **SQL** — apply migrations to a disposable Postgres/Supabase; run T-01 and T-07; never against production.
5. **Manual** — two-device convergence scenarios with network toggling; verify pending count, failed items and stock totals.

## 7. Residual test risk

- Only v33 schema is exported; v31/v32 migrations cannot be validated with `MigrationTestHelper` without reconstructing schemas or adding the missing JSON files.
- Server trigger/policy tests do not exist in CI; `supabase/tests` was not executed here.
- No test asserts the finance heal, reminder identity, tombstone reconciliation, or role matrix.
