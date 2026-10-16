# Known Bugs

> Verified defects, ranked. Each is **Confirmed** (provable from the cited code) or
> **Suspect** (a real smell whose impact could not be proven from source alone).
> Companions: [[Testing]] · [[Technical Debt]] · [[Architecture Decisions]]

Nothing here is fixed. This page is the backlog.

## Severity 1 — data loss or wrong clinical data

### 1.1 ✅ Stock ledger says `FAILED` while a dose actually left the shelf

`feature/vaccination/domain/VaccinationEditEngine.kt:192-199`.

```kotlin
val status = when {
    shortfall != null                          -> "FAILED"
    exact != null                              -> exact.status
    forVaccine.isEmpty()                       -> "COMPLETED"
    forVaccine.none { it.status == "COMPLETED" } -> "FAILED"   // ← wrong
    else                                       -> "COMPLETED"
}
```

`applyInventoryDiff` deducts on `delta > 0` **without consulting the ledger** (`:132-148`);
the ledger is only read for the *reversal cap* (`:157`).

**Reproduction:**

1. Batch `b1` has 0 doses. Record a vaccination `b1 × 1` →
   `InsufficientStockException` → ledger row `b1, qty 1, **FAILED**`.
2. Staff buys 5 doses into `b1`.
3. Edit the visit to `b1 × 2`. `inventoryDiff` = `{b1: +1}`.
4. `deductStockFromBatch(b1, 1)` **succeeds**. `usedQuantity += 1`. **A real dose left.**
5. `reconcileInventoryDeductions`: `forVaccine = [the FAILED row]`, `exact` (qty 2) is null,
   `shortfall` is null, `none { COMPLETED }` → **`"FAILED"`**.
6. `deleteVaccination` → `getCompletedForVaccination` returns nothing →
   **no reversal. The dose is gone and the ledger actively lies.**

The comment at `:180-183` claims this rule *"matches the reversal cap … so a FAILED batch
never moves stock in either direction"*. That is true for the **negative** direction only.

**Fix:** cap the *positive* delta by the ledger too, or set `COMPLETED` whenever
`applyInventoryDiff` actually deducted.

### 1.2 ✅ One unusable batch line destroys the entire vaccination record

`ClinicalVaccinationService.kt:120` catches **only** `InsufficientStockException`. But
`deductStockFromBatch` throws two plain `IllegalStateException`s that escape it:

```kotlin
// InventoryRepositoryImpl.kt:435
val batch = vaccineDao.getBatchById(batchId) ?: throw IllegalStateException("Batch not found")
// InventoryRepositoryImpl.kt:445
if (expired) throw IllegalStateException("Cannot deduct stock from a batch that had already expired …")
```

Either propagates out of `transactionRunner.run` (`ClinicalVaccinationService.kt:42`) and
**rolls back the visit, its items, the finance income row and the inventory status** —
exactly what `InsufficientStockException.kt:7-11` says must not happen.

Reachability is low (the dropdown filters expired batches at `VaccineRowCard.kt:87`; a
deleted batch is guarded at `InventoryRepositoryImpl.kt:324-333`) but non-zero: a batch
deleted after the screen loaded, or a record restored from a backup whose batch row is
missing.

**Fix:** move both throws behind the recovery path, or catch `IllegalStateException` in the
same branch as `InsufficientStockException`.

### 1.3 ✅ A reminder failure after commit causes a duplicate visit on retry

`AddVaccinationViewModel.kt:685-698` commits the visit in `recordVaccination`'s transaction,
**then** writes reminders outside it:

```kotlin
clinicalService.recordVaccination(vaccination, user, isNew = true)   // :685  COMMITTED
reminderSpecs.forEach { spec ->
    reminderRepository.saveNextVaccination(...)                      // :688  separate transaction
}
} catch (e: Exception) {
    _uiState.update { it.copy(isLoading = false, errorMessage = e.message)) }   // :702-704
```

If any `saveNextVaccination` throws, the visit **and its stock deduction are already
committed**, but the UI shows an error. Staff taps Save again — and
`vaccinationId` is regenerated per attempt (`:554`) while item ids come from **stable UI row
ids**, so `EditReconciler` takes the "added" branch and **reuses that id verbatim**
(`EditReconciler.kt:50-53`). `vaccination_items` uses `OnConflictStrategy.REPLACE`
(`VaccinationItemDao.kt:12-13`), so visit #2's item **REPLACES** visit #1's row.

Result: visit #1 left with zero items but a live stock deduction, **plus** a second
deduction for visit #2.

**Fix:** move the reminder loop inside the same transaction.

### 1.4 ✅ Soft-delete filtering applied to 7 of 16 reminder queries

`data/local/dao/DueReminderDao.kt`. The file's own comment at `:10-13` states the rule and
then breaks it.

| Filtered (`is_deleted = 0`) | **Unfiltered** |
|---|---|
| `getAllDueReminders` `:15`, `getAllCompletedReminders` `:18`, `getAllDismissedReminders` `:21`, `getAllReminders` `:75`, `getUpcomingVaccinationsByType` `:92`, `…ByVaccineId` `:101`, `getUpcomingVaccinations` `:106` | `getDueRemindersPage` `:27`, `getCompletedRemindersPage` `:30`, `getDismissedRemindersPage` `:33`, `getDueReminder` `:36`, `getRemindersByVisitId` `:39`, `getReminderById` `:42`, `getReminderByUniqueEvent` `:45`, `getReminderByStableId` `:48`, `getDueRemindersForPatient` `:109`, and `BackupDao.getAllRemindersForBackup` |

**This is live, not hypothetical.** `migration31_32` (`AppDatabase.kt:390-415`)
hard-deletes `is_deleted = 1` rows on 18 tables but **deliberately excludes `reminders`**, so
a device upgraded from an old build carries live soft-deleted reminder rows into HEAD.

- `getRemindersByVisitId` feeds `PatientVaccinationCardEntity` via `@Relation` — and
  **`@Relation` cannot filter**, so soft-deleted reminders appear on patient history
  regardless of any predicate.
- `getDueRemindersPage`/… are the paged twins of the filtered `getAll*` queries — the exact
  "card says 12, drill-down opens 14" divergence the `:78-91` comment says the pagination
  pass was designed to prevent.
- `BackupDao.getAllRemindersForBackup` exports them into the `.nccb` payload, resurrecting
  them on the restoring device.

**No test covers any of the 9 unfiltered queries.**

### 1.5 ✅ The Postgres stock trigger corrupts stock on any retried upsert

`supabase/migrations/20261013140000_stock_trigger_negative_guard.sql:56-61`:

```sql
ELSE
    UPDATE public.vaccine_batches
       SET remaining_quantity = remaining_quantity - OLD.quantity
     WHERE id = OLD.batch_id;
```

`20261016:140` widened the trigger to `after insert **or update** or delete`, and the
client uploads transaction rows with `upsert` (`SyncUploader.kt:532`). A retried upsert of an
existing `inventory_transactions.id` — which `SyncUploader.kt:477` explicitly acknowledges
happens — takes the conflict-`UPDATE` path:

- `VACCINATION` row with `quantity = -2` → `-(-2)` = **+2 doses invented**
- `PURCHASE` row with `quantity = +10` → **10 doses silently destroyed**

Correct form is `- OLD.quantity + NEW.quantity`. Note the `DELETE` branch (`-OLD.quantity`)
is defensible — deleting a ledger row does undo the movement.

### 1.6 ✅ Two stock triggers may both be installed — every movement applied twice

`20261016:134-138` gates the install on the absence of
`inventory_transactions_update_batch_stock`. The **legacy** trigger is named
`tr_update_batch_stock` (`.artifacts/…/scratch/stock_triggers.sql:23`) and is **never dropped
by any migration**. `20261013140000:4-6` asserts the function *is* live in production.

**Fix:** add `drop trigger if exists tr_update_batch_stock on public.inventory_transactions;`
immediately before the install block. 🔍 Verify against the live DB first.

## Severity 2 — user-visible wrong behaviour

### 2.1 ✅ The Postgres trigger can silently leave every batch at zero stock

`SyncUploader.kt:608-619` unconditionally sends `remaining_quantity = 0` on create and
**omits** the column on update. The trigger function body is wrapped in an existence check
(`20261013140000:13-21`) and the install is gated the same way (`20261016:127-144`).

On any database where the trigger was never installed, **every batch is created with zero
stock forever.** The migration comment at `:9-10` claims "the client's absolute
`vaccine_batches` sync … clamps at 0" — that fallback no longer exists. 🔍 Verify
`update_batch_stock` is present in production.

### 2.2 ✅ `FullReportViewModel.computeDaily` double-counts duplicate vaccination income

`FullReportViewModel.kt:282` sums raw income rows, while `computeMonthly` in the **same
class** (`:196-198`) does it correctly:

```kotlin
FinanceCalculator.effectiveIncome(transactions.filter { it.type.equals("INCOME", true) })
```

`FinanceCalculator.kt:307-318` states the invariant: *"Every screen that sums income must go
through here — do not re-implement the sum."*

Drilling Full Report into a single month **over-reports** revenue, cash, online and net
profit, while the same screen shows the correct figure one level up.

### 2.3 ✅ A `SingleFlight` waiter always reports "nothing failed"

`core/sync/SyncRepositoryImpl.kt:103-107`:

```kotlin
override suspend fun processNextItems(): Boolean {
    var anyFailed = false
    flight.run { anyFailed = processNextItemsInternal() }
    return anyFailed
}
```

`SingleFlight.run` only executes the block for the **owner**; a waiter does
`return running.await() as T` (`SingleFlight.kt:31-33`) and never assigns `anyFailed` →
**always `false`**. A waiter then takes `SyncWorker.kt:37-40`'s success branch → resets
`failure_count` and returns `Result.success()`, cancelling the "Some changes could not be
synced" alert (threshold ≥5).

Compare the correct usage at `RefreshDataUseCase.kt:49`:
`= flight.run { runRefresh() }`.

**This is exactly the bug the class doc claims was fixed** (`SingleFlight.kt:14-18`) and that
`SingleFlightResultTest.kt` claims to cover — but the test exercises the return value, not
this wrapper.

### 2.4 ✅ Enabling or changing notification time has no effect until app restart

`ReminderScheduler`'s two schedule methods have **exactly two** call sites, both in
`NeoChildApp.onCreate` (`:111-112`). `NotificationSettingsViewModel.updateSettings` (`:27-31`)
writes DataStore and never reschedules. Staff change the reminder time; it takes effect the
next day, after a restart.

### 2.5 ✅ Enabling Automatic Backup does not schedule until app restart

`BackupAutoScheduler.schedule()` has **zero** call sites —
`grep` finds only the class declaration, a KDoc mention, and
`NeoChildApp.kt:124` (`rearmIfEnabled`). `BackupViewModel.kt:202-215` writes DataStore and
never touches the scheduler.

### 2.6 ✅ The lock screen is an overlay, not a gate

`app/MainActivity.kt:106-114`:

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    AppNavigation(navController, appUpdateViewModel)   // :107  ALWAYS composed
    if (isLocked) {                                       // :109
        LockScreen(onAuthenticate = …, onPasswordAuthenticate = …)
    }
}
```

`AppNavigation` → `NavHost` composes the entire 51-destination graph and constructs every
screen ViewModel **while locked**. `DashboardViewModel.init` runs, Room flows collect,
patient data is in memory. `LockScreen`'s root is an opaque `Surface`, so it is *visually*
covered — but no code path is prevented.

Compounding it, the re-arm is not awaited before UI renders
(`NeoChildApp.kt:93-98`, off the main thread, `_isAppLocked` starts `false`), and
`checkAppLock()` first waits up to **4 s** for the session status
(`MainActivity.kt:194-225`).

### 2.7 ✅ Two declared error fields can never be set — so the errors are invisible

| Field | Declared | Writers | Consequence |
|---|---|---|---|
| `DashboardViewModel.errorMessage` | `:47` | **0** | `DashboardScreen.kt:199-201` is dead code |
| `BorrowedViewModel.actionError` | `:59` | **0** | `submitReturn`, `deleteBorrowedItem`, `saveBorrowedItem` fail **completely silently**; `BorrowedScreen.kt` has zero error references |

### 2.8 ✅ `savePatient` swallows its own validation error

`PatientViewModel.kt:198-204` throws `IllegalArgumentException` for a blank name or DOB and
then catches it in the same `try` with an empty body. The caller passes only `onComplete`
(`AddPatientScreen.kt:136`). **Zero feedback, no navigation.**

### 2.9 ✅ `isLoading` can never return to false in 5 places

| Location | Mechanism |
|---|---|
| `FinanceDetailsViewModel.kt:50-57` | `.first()` on Room flows, no `try`/`finally` |
| `FullReportViewModel.kt:66-94` | `combine(…).collect { }`, no `.catch` |
| `StatisticsViewModel.kt:55-95` | same |
| `ProfileViewModel.kt:78-85`, `:101-106` | `?: return@launch` after setting `isLoading = true` |

A Save button stays disabled **forever**, with no error.

### 2.10 ✅ Inventory sorts compare `"d MMM yyyy"` lexicographically

`Constants.DATE_FORMAT` is **not zero-padded**, so
`"16 Oct 2026".compareTo("9 Oct 2026") < 0` — the later expiry sorts **first**.

| Site | Impact |
|---|---|
| `InventoryRepositoryImpl.kt:121` | `InventorySort.EXPIRY` is wrong for most of the year |
| `InventoryRepositoryImpl.kt:123-124` | same on `purchaseDate` |
| `InventoryRepositoryImpl.kt:81` | `maxByOrNull { it.purchaseDate }` picks the **oldest** batch for the MRP/net-rate fallback |
| `AddBatchViewModel.kt:56` | same |

✅ The app already documents this hazard and fixes it in
`domain/model/Vaccination.kt:60-61`. These four sites were missed.

### 2.11 ✅ `addStockBatch` is not atomic

`InventoryRepositoryImpl.kt:233-263` — ~10 validation branches interleaved with writes in one
loop, **no `transactionRunner.run`**. A 5-batch submission failing on batch 3 leaves batches
1-2 committed with their `PURCHASE` transactions and sync enqueues, while the UI reports
total failure. Retrying mints fresh `UUID.randomUUID()` batch ids
(`AddStockViewModel.kt:210`) → duplicate stock.

## Severity 3 — sync, integrity, and correctness risks

### 3.1 ✅ Unbounded WorkManager chain growth

`SyncManagerImpl.kt:32-36` uses `ExistingWorkPolicy.APPEND_OR_REPLACE`, and
`scheduleSync()` fires **per queued row** (`SyncRepositoryImpl.kt:84`) — including from
inside a Room transaction. One `deletePatient` enqueues dozens of rows (`:206-239`) in one
transaction. Offline those requests accumulate; on reconnect the whole chain runs
sequentially, each run draining the same now-empty queue.

### 3.2 ✅ The uploader's conflict-resolution branch is unreachable

`SyncUploader.kt:514-516` requires `!isUnsynced(...)`, but every item reaching it is already
`SYNCING` (committed at `SyncRepositoryImpl.kt:284-288` **before** the upload at `:300`).
So `descriptor.download(...)` at `:518` and the whole timestamp block (`:496-521`) are dead
code, and `fetchRemoteConflictData`'s batch read is fetched then ignored. See
[[Offline-First Architecture]].

### 3.3 ✅ Lost-update race on every batch mutation

`InventoryRepositoryImpl.kt:435` reads `getBatchById`, `:452` writes `updateBatch` — a
full-row `@Update` with **no `@Version` column** on `VaccineBatchEntity` and no
transaction. Two concurrent deductions both read the same `remainingQuantity`; the second
overwrites the first. Same shape at `:531-538`, `:478`, `:565-583`, `:613-619`.

### 3.4 ✅ `AuditLogger.log()` writes audit rows outside the caller's transaction

`AuditLogger.kt:40-42` fires `recordLog` on a separate `Dispatchers.IO` coroutine. It is
called from inside live transactions in 5 places — `WasteRepositoryImpl.kt:64`, `:102`;
`MergePatientsUseCase.kt:47`; `BorrowRepositoryImpl.kt:150`, `:185`, `:287` — so those audit
rows **survive a rollback**. And `recordLog` itself swallows everything (`:88-90`), so a
committed `isSynced = false` audit row can end up with no queue row and never upload.

### 3.5 ✅ `SyncQueueDao` orders a TEXT priority column backwards

`SyncQueueDao.kt:12` — `ORDER BY priority DESC`. `SyncPriority` is stored as its name
(`LOW`, `MEDIUM`, `HIGH`), so lexicographic DESC gives `MEDIUM, LOW, HIGH` — **`HIGH`
sorts last**. No behavioural impact, because `orderPendingIntoGroups` re-sorts
deterministically, but it makes the column actively misleading.

### 3.6 ✅ `reminders` pull never applies tombstones

`ReminderRepositoryImpl.refreshReminders()` (`:459-504`) and
`PersonalReminderRepositoryImpl.refresh()` (`:176-183`) only insert/overwrite. A row deleted
on device B stays on device A — and the queue-aware guard preserves it **indefinitely** if it
is locally unsynced.

### 3.7 ✅ A realtime subscription dies permanently on first error

`RealtimeChangeSubscriptions.kt:52-54` puts `.catch { }` on each table flow. The first error
terminates that subscription with **no resubscribe**; the screen keeps a live-looking
ViewModel with a dead channel.

## Severity 4 — security

| # | Finding | Evidence |
|---|---|---|
| 4.1 | ✅ **Every active staff member can `select *` from `profiles`** — email, phone, role, employee_id, **fcm_token**, and the soft-delete audit fields. Introduced by the migration named "security baseline". | `20260929000000_security_baseline.sql:119-121` |
| 4.2 | ⚠️ **Legacy policies from unversioned scratch SQL may still exist.** `WITH CHECK (true)` on `audit_logs` → any authenticated user including a deactivated one can forge audit entries. `FOR ALL` on `profiles` gated by `get_my_role()` (which reads `profiles.role`) **defeats the whole `20261001_app_metadata_roles.sql` migration**. | `.artifacts/…/scratch/missing_tables.sql:95-100`, `full_project_schema.sql.artifact.md:329-345` |
| 4.3 | ⚠️ `todo_notification_events` had **no RLS and no revocation** from its creation until `20261016`, and receives `patient_id`, patient `name`, `doctor_id`. | `20261016:13-14` says so explicitly |
| 4.4 | ⚠️ `FORCE ROW LEVEL SECURITY` on `todo_notification_events` with zero policies **breaks the documented writer** — `FORCE` applies RLS to the table owner, and a `SECURITY DEFINER` trigger runs as owner. | `20261016:53-60` vs `:16-17` |
| 4.5 | ⚠️ **No migration issues a single `GRANT`.** Under the new `auto_expose_new_tables = false` default, a perfect RLS policy is irrelevant and **every app write fails with `permission denied for table …`**. The field is removed **2026-10-30**. | `supabase/config.toml:19-24` |
| 4.6 | ⚠️ **17 of 21 migrations are untracked in git** — every `CREATE TABLE`, the entire RLS baseline and the accountability triggers exist in one working tree only. The `.gitignore` comment claims the opposite. | `git ls-files supabase/migrations/` |
| 4.7 | ⚠️ `get_my_role()` is `SECURITY DEFINER` with **no `set search_path`** and is referenced by 10 legacy policies — but it is not in this repository. | `.artifacts/…/full_project_schema.sql.artifact.md:13-21` |
| 4.8 | ⚠️ `expenses` has **no** `neo_stamp_accountability` trigger — its `created_by`/`updated_by` are client-supplied and unverified, contradicting the migration that exists to prevent exactly that. | `20260909_expenses.sql` |
| 4.9 | ⚠️ `user_devices(user_id)` has **no index** despite every device policy filtering on it. `20261016:81-124` adds 5 indexes, none on an RLS predicate column. | `20260816:162-163`, `20260929:137-145` |
| 4.10 | ⚠️ The write rate limiter counts **rows**, not requests, so one bulk `DELETE` burns the whole 600/minute budget; and `cleanup_write_rate_limits()` is never scheduled, so the table grows forever. | `20260929001000_write_rate_limit.sql:16-49`, `:77-91` |
| 4.11 | ⚠️ `Routes.AUDIT_LOGS`, `EXPENSES`, `SYNC`, `BACKUP_SETTINGS` have **no navigation guard** — four different authorization idioms across the graph. Whether this is a bypass depends on RLS. | `NavigationDashboardGraph.kt:32-34` |
| 4.12 | ⚠️ `manage-staff`'s `UPDATE_PROFILE` is missing from the self-guard list, so an admin can change their own role through the wrong action. | `manage-staff/index.ts:102-104` |
| 4.13 | ⚠️ `notify-update` fans out FCM **serially** with no batching — 200 devices means 200 round-trips and a mid-list timeout. | `notify-update/index.ts:71-98` |

🔍 Items 4.2, 4.3, 4.5 and 4.7 need a live database to settle. The SQL to run is in
[[Row Level Security]].

## Suspect — real smells, unproven impact

| # | Finding | Why unproven |
|---|---|---|
| S-1 | `inventory_deductions` has **no sync descriptor** and is never uploaded | Is anything supposed to drain it? If not, the server has no record of which doses moved |
| S-2 | `isExpired` fails open on an unparseable date | A blank `expiryDate` batch counts toward stock. Pinned as intentional by a test, but `addBatch` (borrow-return path) does not validate the field |
| S-3 | `isLowStock = totalStock <= globalThreshold` (default 5) | Exactly-at-threshold stock is flagged `LOW_STOCK`. Probably unintended |
| S-4 | `consultations.visitId` is `NOT NULL` with a CASCADE FK but defaults to `""` | No live insert path found; a latent trap |
| S-5 | `Result.failure()` from `SyncWorker` cancels its appended dependents | WorkManager-version-dependent; the next sync is delayed up to 15 minutes |
| S6 | `_syncState` has no `try`/`finally` around `= SYNCING` | An escaping DB exception strands the Dashboard on "syncing" |
| S-7 | `VaccineDetailViewModel.kt:91-95` re-filters in Kotlin with no test | Rows saved before `vaccineName` normalisation would be counted on the card and hidden in the list |
| S-8 | 60 unguarded `viewModelScope.launch` bodies | Crash-capable, but each individual site may be unreachable in practice |
| S-9 | `VaccineItem.batchId` defaults to `""` | `deductStockFromBatch("")` throws → bug 1.2's full-rollback path, but only if legacy rows exist |

## Suggested order of work

1. **1.4** — add `is_deleted = 0` to the 9 reminder queries (and extend
   `UpcomingVaccinationDaoTest`). Cheapest, and it is already a stated rule.
2. **1.5 + 1.6** — fix the trigger: `-OLD.quantity + NEW.quantity`, and drop the legacy
   trigger before installing. Both are one-line SQL.
3. **1.1** — add the ledger cap to `applyInventoryDiff` and one test.
4. **1.2** — move the two `IllegalStateException`s behind the recovery branch.
5. **1.3** — move the reminder loop into `recordVaccination`'s transaction.
6. **2.3, 2.7, 2.8** — error surfacing. Small diffs, large user-visible win.
7. **4.5, 4.6** — verify grants against the live DB, and `git add` the migrations.
   4.5 has a **2026-10-30** deadline.

## Related

- [[Testing]] — which of these are untested, and why
- [[Technical Debt]] — the duplication behind several of these
- [[Row Level Security]] — the SQL to settle the live-DB questions
- [[Inventory Management]] · [[Vaccination Management]] · [[Due Vaccination]] — per-feature context
