# Bug Register

> Canonical defect tracker for Neo Child Clinic. IDs are stable — never renumber, never
> reuse. Narrative detail lives in [[Known Bugs]]; this page is the authoritative
> **status** record.
> Convention: [[Documentation Maintenance]] · Reconciliation: [[Quality/Audit Reconciliation]]

## Status vocabulary

| Status | Meaning | Requirement |
|---|---|---|
| **Open** | Reproduced or proven by reading code; not yet scheduled | evidence + root cause recorded |
| **Investigating** | Suspected, not yet proven | must say what is missing |
| **Confirmed** | Proven by reading the cited code *and* stated reproduction | full card below |
| **Fixed** | Change landed **and** the original reproduction no longer reproduces | ⚠️ requires verification evidence — never assume |
| **Won't Fix** | Deliberately accepted | must record the reason |

⚠️ **A fix that changes the named mechanism is not a fix.** Audit entry #14, #15, #38 and #2
were each marked fixed in `docs/AUDIT_REPORT_2026-10-16.md`; re-verification found a
neighbouring path still producing the original symptom. See
[[Quality/Audit Reconciliation]].

## Severity scale

| Level | Definition |
|---|---|
| **S1 Critical** | Wrong clinical or financial data; irreversible without manual repair |
| **S2 High** | User-visible wrong behaviour, silent failure, or work is lost |
| **S3 Medium** | Integrity, race, or scheduling risk under realistic conditions |
| **S4 Low** | Latent, cosmetic, or requires an unlikely precondition |

## Index

| ID | Title | Sev | Status | Area |
|---|---|---|---|---|
| [[#ncc-001]] NCC-001 | Edit that raises quantity on a failed batch moves stock but records `FAILED` | S1 | ✅ **Fixed** | Inventory |
| [[#ncc-002]] NCC-002 | Postgres stock trigger corrupts stock on any retried upsert | S1 | Open | Supabase |
| [[#ncc-003]] NCC-003 | Legacy stock trigger never dropped — possible double-application | S1 | Open | Supabase |
| [[#ncc-004]] NCC-004 | Reminder failure after commit duplicates the visit and double-deducts | S1 | Open | Vaccination |
| [[#ncc-005]] NCC-005 | `deductStockFromBatch` pre-write throws bypass stock recovery | S1 | ✅ **Fixed** | Inventory |
| [[#ncc-006]] NCC-006 | Soft-delete filter missing on 9 reminder queries | S1 | ✅ **Fixed** | Room |
| [[#ncc-007]] NCC-007 | Trigger absent ⇒ every batch created at zero stock | S2 | Open | Supabase |
| [[#ncc-008]] NCC-008 | `computeDaily` re-sums income, disagreeing with `computeMonthly` | S2 | ✅ **Fixed** | Statistics |
| [[#ncc-009]] NCC-009 | `SingleFlight` waiter always reports "nothing failed" | S2 | ✅ **Fixed** | Sync |
| [[#ncc-010]] NCC-010 | Notification-time change needs a process restart | S2 | Open | Reminders |
| [[#ncc-011]] NCC-011 | Auto-backup enable/disable needs a process restart | S2 | Open | Backup |
| [[#ncc-012]] NCC-012 | Lock screen is an overlay, not a gate | S2 | Open | Security UX |
| [[#ncc-013]] NCC-013 | `DashboardViewModel.errorMessage` can never be set | S2 | Open | UI |
| [[#ncc-014]] NCC-014 | `BorrowedViewModel.actionError` can never be set | S2 | Open | UI |
| [[#ncc-015]] NCC-015 | `savePatient` swallows its own validation exception | S2 | ✅ **Fixed** | UI |
| [[#ncc-016]] NCC-016 | `isLoading` can never clear | S2 | ⚠️ **Partial** | UI |
| [[#ncc-017]] NCC-017 | Inventory sorts compare `"d MMM yyyy"` lexicographically | S2 | ✅ **Fixed** | Inventory |
| [[#ncc-018]] NCC-018 | `addStockBatch` is not atomic | S2 | ✅ **Fixed** | Inventory |
| [[#ncc-019]] NCC-019 | WorkManager chain grows without bound | S3 | ✅ **Fixed** | Sync |
| [[#ncc-020]] NCC-020 | Uploader conflict branch is unreachable | S3 | Open | Sync |
| [[#ncc-021]] NCC-021 | Lost-update race on every batch mutation | S3 | Open | Inventory |
| [[#ncc-022]] NCC-022 | `AuditLogger.log()` writes outside the caller's transaction | S3 | Open | Logging |
| [[#ncc-023]] NCC-023 | Pull paths never apply tombstones | S3 | Open | Sync |
| [[#ncc-024]] NCC-024 | Realtime subscription dies permanently on first error | S3 | Open | Sync |
| [[#ncc-025]] NCC-025 | `SyncQueueDao` orders a TEXT priority column backwards | S4 | Open | Sync |
| [[#ncc-026]] NCC-026 | `isExpired` fails open on an unparseable date | S4 | Open | Inventory |
| [[#ncc-027]] NCC-027 | `isLowStock` uses `<=`, flagging exactly-at-threshold stock | S4 | Open | Inventory |
| [[#ncc-028]] NCC-028 | `consultations.visitId` defaults to `""` — an FK-violating value | S4 | Open | Room |
| [[#ncc-029]] NCC-029 | `AuditLogDao` references a composite index that does not exist | S4 | Open | Room |
| [[#ncc-030]] NCC-030 | `VaccineDao` audit-count guards query the wrong column | S4 | Open | Inventory |
| [[#ncc-031]] NCC-031 | `FinanceDao` sorts on an expression no index can serve | S4 | Open | Room |
| [[#ncc-032]] NCC-032 | `reschedule` discards its `reminderDate` argument | S3 | Open | Reminders |
| [[#ncc-033]] NCC-033 | `notificationsSentToday` is structurally always 0 | S3 | Open | Reminders |
| [[#ncc-034]] NCC-034 | Personal reminder with a blank date notifies every run | S3 | Open | Reminders |
| [[#ncc-035]] NCC-035 | Personal-reminder notification ID has a precedence bug | S4 | ✅ **Fixed** | Reminders |
| [[#ncc-036]] NCC-036 | 60 `viewModelScope.launch` bodies have no `try`/`catch` | S3 | Open | UI |
| [[#ncc-037]] NCC-037 | `readBytesFromUri` size check is TOCTOU | S3 | Open | Backup |
| [[#ncc-038]] NCC-038 | `inventory_deductions` is never synced to the server | S3 | Open | Sync |
| [[#ncc-039]] NCC-039 | `neochild_db` opened twice as a singleton | S4 | Open | DI |
| [[#ncc-040]] NCC-040 | Latest-batch fallback in `AddBatchViewModel` picks the oldest | S2 | ✅ **Fixed** | Inventory |

**Security** → [[Quality/Security Concerns]] (NSC-001…NSC-013)
**Hypotheses** → [[Quality/Hypotheses]] (HYP-001…HYP-009)

---

## NCC-001 — Edit raising quantity on a failed batch moves stock but records `FAILED`

| | |
|---|---|
| **Status** | ✅ **Fixed** · **Severity** S1 Critical |
| **Files** | `feature/vaccination/domain/VaccinationEditEngine.kt` |
| **Impact** | A dose leaves the shelf, the ledger says it did not, and deleting the visit never restores it. |

**Evidence.** `applyInventoryDiff` deducts on `delta > 0` without consulting the ledger;
the ledger is only read for the reversal cap (`:157`). `reconcileInventoryDeductions`
infers status at `:192-199`, whose 4th branch is `forVaccine.none { it.status == "COMPLETED" } -> "FAILED"`.

**Reproduction.**

1. Batch `b1` has 0 doses. Record `b1 × 1` → shortfall → ledger row `b1, qty 1, FAILED`.
2. Staff buys 5 doses into `b1`.
3. Edit the visit to `b1 × 2`. `inventoryDiff` = `{b1: +1}`.
4. `deductStockFromBatch(b1, 1)` succeeds; `usedQuantity += 1`. **A real dose is gone.**
5. `reconcileInventoryDeductions`: `exact` (qty 2) is null, `shortfall` is null,
   `none { COMPLETED }` → **`FAILED`**.
6. `deleteVaccination` → `getCompletedForVaccination` returns nothing → **no reversal.**

**Root cause.** The status rule inferred the new status from the prior row alone and never
consulted whether stock had actually moved on this edit.

**Fix applied.** Extracted the rule into a pure top-level `deductionStatus(shortfall,
stockMoved, exactStatus, priorStatuses)` — the same pattern `inventoryDiff` already uses in
this file. `applyInventoryDiff` now returns a `StockDiffResult(shortfalls, deducted)`
recording which batches actually moved, and `stockMoved -> "COMPLETED"` is evaluated
directly after the shortfall branch. A prior `FAILED` row can no longer mask a deduction
that succeeded.

Also bundled (same code path, S3): `reverseDeduction` now receives the `transactionGroupId`
the engine already computed, so the stock reversal uploads in the same sync group as the
visit edit rather than as a singleton group.

**Regression test.** `VaccinationEditEngineLedgerTest` — 8 tests covering both NCC-001
reproductions, the shortfall path, and every carry-forward branch.

**Verification evidence.** Reverting only the `stockMoved -> "COMPLETED"` branch makes
`stock moved on this edit outranks a prior FAILED row` and `stock moved wins over multiple
prior FAILED rows` fail; restoring it makes all 8 pass. Suite is
**260 tests, 0 failures, 0 errors, 0 skipped** (was 252).

⚠️ **Not covered:** the ledger write is asserted as a pure decision, not against a real
Room database. A future change to how `applyInventoryDiff` reports `deducted` would not be
caught by these tests. The end-to-end path still needs an instrumented test.

**Links.** [[Vaccination Management]] · [[Inventory Management]] · [[Testing]]

---

## NCC-002 — Postgres stock trigger corrupts stock on any retried upsert

| | |
|---|---|
| **Status** | Open · **Severity** S1 Critical |
| **Files** | `supabase/migrations/20261013140000_stock_trigger_negative_guard.sql:56-61`, installed at `20261016_rls_index_and_stock_trigger_hardening.sql:140` |
| **Impact** | A retried ledger upload invents or destroys doses server-side. |

**Evidence.** The `ELSE` branch applies `- OLD.quantity` with no `+ NEW.quantity`, and only
ever debits `OLD.batch_id`. The trigger now fires `after insert or **update** or delete`, and
the client uploads transaction rows with `upsert` (`core/sync/SyncUploader.kt:532`).
`SyncUploader.kt:477` explicitly acknowledges that retries after a lost response happen.

- `VACCINATION` row `quantity = -2` → `-(-2)` = **+2 doses invented**
- `PURCHASE` row `quantity = +10` → **10 doses destroyed**

**Fix.** `- OLD.quantity + NEW.quantity`, and credit `NEW.batch_id` when it changes.
**Regression test.** `pgTAP` or a psql script: insert a `PURCHASE` row, re-upsert the same
id with a changed quantity, assert `remaining_quantity` reflects only the delta.

**Links.** [[Supabase]] · [[Inventory Management]] · [[Database Schema]]

---

## NCC-003 — Legacy stock trigger never dropped; possible double-application

| | |
|---|---|
| **Status** | Open · **Severity** S1 Critical |
| **Files** | `20261016_rls_index_and_stock_trigger_hardening.sql:134-138` |
| **Impact** | If the legacy trigger is live, **every stock movement is applied twice.** |

**Evidence.** The install is gated on the absence of `inventory_transactions_update_batch_stock`.
The legacy trigger is named `tr_update_batch_stock`
(`.artifacts/712413f3-…/scratch/stock_triggers.sql:23`) and is **never dropped by any
migration**. `20261013140000:4-6` asserts the function *is* live in production.

**Root cause.** The guard checks for its own trigger name only, not for any trigger on the table.

**Fix.** `drop trigger if exists tr_update_batch_stock on public.inventory_transactions;`
immediately before the install block.
**Verification.** 🔍 Run against the live DB first:
```sql
select tgname, tgenabled from pg_trigger
where tgrelid = 'public.inventory_transactions'::regclass and not tgisinternal;
```

**Links.** [[Supabase]] · [[Inventory Management]]

---

## NCC-004 — Reminder failure after commit duplicates the visit and double-deducts

| | |
|---|---|
| **Status** | Open · **Severity** S1 Critical |
| **Files** | `feature/vaccination/presentation/AddVaccinationViewModel.kt:685-698`, `:554`; `feature/vaccination/domain/EditReconciler.kt:50-53`; `data/local/dao/VaccinationItemDao.kt:12-13` |
| **Impact** | Retry produces a second visit, re-points the first's item row, and deducts stock twice. |

**Evidence.** `recordVaccination` commits the visit **and its stock deduction**, then the
reminder loop runs in separate transactions (`:687-698`). A throw there sets
`errorMessage` (`:702-704`) while `saveSuccess` stays false. On retry `vaccinationId` is
regenerated (`:554`) but item ids come from stable UI row ids, so `EditReconciler` takes the
"added" branch and reuses the id verbatim — and `vaccination_items` uses
`OnConflictStrategy.REPLACE`.

**Reproduction.** Save a visit with a next-vaccination reminder; force `saveNextVaccination`
to throw (e.g. empty DB constraint); tap Save again.

**Fix.** Move the reminder loop inside `recordVaccination`'s transaction.
**Regression test.** Inject a repository whose `saveNextVaccination` throws; assert no
`patient_visits` row exists afterwards.

**Status note.** ⚠️ **Deliberately not applied on 2026-10-16.** Moving the reminders into the
clinical transaction also pulls `ReminderRepository`'s WorkManager scheduling inside it —
the same coupling described above. It deserves its own reviewed change, not a drive-by.

**Links.** [[Vaccination Management]] · [[Known Bugs]]

---

## NCC-005 — Pre-write `IllegalStateException`s bypass the stock recovery path

| | |
|---|---|
| **Status** | ✅ **Fixed** · **Severity** S1 Critical |
| **Files** | `feature/inventory/domain/StockDeductionBlockedException.kt` (new); `InventoryRepositoryImpl.kt`; `ClinicalVaccinationService.kt`; `VaccinationEditEngine.kt` |
| **Impact** | One bad batch line rolls back the visit, its items, the finance row and the inventory status. |

**Evidence.** The catch is `InsufficientStockException` only (`:120`). Two plain
`IllegalStateException`s escape it: `"Batch not found"` (`:435`) and the expiry guard
(`:445`). `InsufficientStockException.kt:7-11` states the design intent — any other failure
*has* to propagate — but these are **pre-write validation failures** with the same
user-facing remedy as a shortfall.

**Preconditions.** A batch deleted after the screen loaded, or a record restored from a
backup whose batch row is missing.

**Fix.** Throw `InsufficientStockException` from both sites, or catch `IllegalStateException`
in the same branch.
**Regression test.** `ClinicalVaccinationService` with a `deductStockFromBatch` stub that
throws `IllegalStateException`; assert the visit is **not** saved.

**Links.** [[Vaccination Management]] · [[Inventory Management]]

---

## NCC-006 — Soft-delete filter missing on 5 live reminder queries

| | |
|---|---|
| **Status** | ✅ **Fixed** · **Severity** S1 Critical |
| **Files** | `data/local/dao/DueReminderDao.kt`; `data/local/dao/BackupDao.kt` |
| **Impact** | Soft-deleted reminders appear on patient history, in backups, and in the conflict guard. |

**Evidence.** 7 of 19 `DueReminderDao` queries filter `is_deleted = 0`; the rest do not.
`migration31_32` (`AppDatabase.kt:390-415`) hard-deletes soft-deleted rows on 18 tables but
**deliberately excludes `reminders`**, so an upgraded device carries live `is_deleted = 1`
rows.

**Live reachability:** `getRemindersByVisitId` feeds `@Relation`, and **`@Relation` cannot
filter**. `getReminderById` has 13 call sites. `BackupDao.getAllRemindersForBackup` exports
them into the `.nccb` payload.

**Note.** Nothing in `app/src/main` writes `is_deleted = 1` today — only a pre-v31 client
could create the rows. The inconsistency is latent, not active.

**Fix applied.** All 9 lookups now filter, plus the 3 paginated variants (dead code, but
wiring them later unfiltered is the same trap) and `BackupDao.getAllRemindersForBackup` — 18
`is_deleted = 0` predicates in total. The file's header comment asserted the rule while
breaking it; it now states what the file does and why (migration 31→32 leaves `reminders`
out of its hard delete).

**Verification.** 🔍 **Instrumented only.** `UpcomingVaccinationDaoTest` lives in
`androidTest`, which `./gradlew test` does not run, so this is compile-verified only. Run
`./gradlew connectedDebugAndroidTest` on a device and extend
`softDeletedRemindersAreExcluded` to the five lookup queries before shipping.

**Links.** [[Room Database]] · [[Statistics Dashboard]] · [[Quality/Audit Reconciliation]]

---

## NCC-007 — Trigger absent ⇒ every batch created at zero stock

| | |
|---|---|
| **Status** | Open · **Severity** S2 High |
| **Files** | `core/sync/SyncUploader.kt:608-619`; `20261013140000:13-21`; `20261016:127-144` |
| **Impact** | Stock is permanently zero for every batch. |

**Evidence.** `uploadBatch` unconditionally sends `remaining_quantity = 0` on create and
omits the column on update. The trigger function body is wrapped in an existence check, and
the install is gated the same way. `20261013140000:9-10` claims the client sync "clamps at 0"
— that fallback no longer exists.

**Fix.** Make the client send the true quantity when the trigger is absent, or make the
migration fail loudly instead of silently skipping.
**Verification.** 🔍 `select proname, prosecdef from pg_proc where pronamespace='public'::regnamespace and proname='update_batch_stock';`

**Links.** [[Supabase]] · [[Inventory Management]]

---

## NCC-008 — `computeDaily` re-sums income

| | |
|---|---|
| **Status** | ✅ **Fixed** · **Severity** S2 High (audit #15 residue) |
| **Files** | `feature/statistics/presentation/FullReportViewModel.kt:282` (bad) vs `:198` (correct) |
| **Impact** | Single-month drill-down over-reports revenue, cash, online and net profit. |

**Evidence.** `FinanceCalculator.kt:307-318` states the invariant. Both functions are in the
same file; only `computeMonthly` was converted.

**Fix.** Route `:282` through `FinanceCalculator.effectiveIncome`.
**Regression test.** Seed one visit with two `VACCINATION` income rows; assert
`computeDaily` and `computeMonthly` agree.

**Links.** [[Statistics Dashboard]] · [[Quality/Audit Reconciliation]]

---

## NCC-009 — `SingleFlight` waiter always reports "nothing failed"

| | |
|---|---|
| **Status** | ✅ **Fixed** · **Severity** S2 High (audit #14 residue) |
| **Files** | `core/sync/SyncRepositoryImpl.kt:103-107` |
| **Impact** | The "Some changes could not be synced" alert never fires for a coalesced call. |

**Evidence.** A waiter's `run()` returns `running.await() as T`; the captured
`anyFailed` is never assigned, so the method returns `false`. `SyncWorker.kt:37-40` then
resets `failure_count` and returns `Result.success()`.

**Contrast** the correct usage at `RefreshDataUseCase.kt:49`: `= flight.run { runRefresh() }`.

**Fix applied.** `return flight.run { processNextItemsInternal() }` — uses the shared result
instead of a captured local.

**Verification.** ✅ Compiles and the full suite passes (273 tests, 0 failures).
**Regression test.** ⚠️ **Still missing.** `SingleFlightResultTest` exercises `run()`'s return
value, not this wrapper — which is exactly why the bug survived the previous audit. The test
to add: hold the flight with a failing run, call `processNextItems()` from a second
coroutine, assert it returns `true`.

**Links.** [[Offline Synchronization]] · [[Quality/Audit Reconciliation]]

---

## NCC-010 / NCC-011 — Settings changes need a process restart

| | NCC-010 | NCC-011 |
|---|---|---|
| **Severity** | S2 | S2 |
| **Files** | `core/notification/ReminderScheduler.kt` | `data/backup/BackupAutoScheduler.kt` |
| **Call sites** | 2, both `NeoChildApp.kt:111-112` | **0** for `schedule()` |
| **Effect** | reminder time change waits for a restart | auto-backup enable/disable waits for a restart |
| **Fix** | call the scheduler from `NotificationSettingsViewModel.updateSettings` | call `schedule()`/`cancel()` from `BackupViewModel:202-215` |

**Links.** [[Due Vaccination]] · [[Backup and Restore]]

---

## NCC-012 — Lock screen is an overlay, not a gate

| | |
|---|---|
| **Status** | Open · **Severity** S2 High |
| **Files** | `app/MainActivity.kt:106-114`; `core/ui/LockScreen.kt`; `core/security/BiometricLockManager.kt:8`; `app/NeoChildApp.kt:93-98` |
| **Impact** | With biometric lock armed, every screen ViewModel is constructed and patient data is in memory while the lock shows. |

**Evidence.** `AppNavigation` is always composed; `LockScreen` is drawn **over** it. The
re-arm at `NeoChildApp.kt:93-98` runs on an untracked IO scope and is not awaited, and
`_isAppLocked` starts `false`. `checkAppLock()` first waits up to 4 s for session resolution
(`MainActivity.kt:194-225`).

**Fix.** Gate composition on `isLocked`, or construct the `NavHost` only once unlocked.
**Verification.** 🔍 On-device: confirm whether the opaque `Surface` fully occludes dialogs
drawn above it.

**Links.** [[Architecture Map]] · [[Error Handling]]

---

## NCC-013 / NCC-014 / NCC-015 — Errors that can never be shown

| | NCC-013 | NCC-014 | NCC-015 |
|---|---|---|---|
| **Severity** | S2 | S2 | S2 |
| **Location** | `DashboardViewModel.kt:47` | `BorrowedViewModel.kt:59` | `PatientViewModel.kt:194-206` |
| **Defect** | `errorMessage` declared, **0 writers** | `actionError` declared, **0 writers** | throws its own validation error, catches and discards it |
| **User sees** | nothing | nothing — and `BorrowedScreen.kt` has 0 error references | nothing, and the screen never advances |
| **Evidence** | the only `DashboardUiState(...)` construction (`:270-290`) never passes it | 1 grep hit — the declaration | caller passes only `onComplete` (`AddPatientScreen.kt:136`) |

**Fix.** Wire the field or delete it; give `savePatient` an `onError` callback.

**Links.** [[MVVM]] · [[Error Handling]]

---

## NCC-016 — `isLoading` can never clear

| | |
|---|---|
| **Status** | ⚠️ **Partial** · **Severity** S2 |
| **Files** | `FinanceDetailsViewModel.kt` ✅ · `ProfileViewModel.kt` ✅ · `FullReportViewModel.kt` · `StatisticsViewModel.kt` |
| **Impact** | Save/refresh buttons stay disabled forever with no error shown. |

**Evidence.** `.first()` on Room flows inside `viewModelScope.launch` with no `try`/`finally`;
or `?: return@launch` after `isLoading = true`.
✅ The correct pattern exists at `VaccineDetailViewModel.kt:99-104` — but it discards `e.message`.

**Fix.** Add `.catch {}` or `finally { isLoading = false }`.

**Links.** [[MVVM]] · [[Statistics Dashboard]]

---

## NCC-017 — Inventory sorts compare dates lexicographically

| | |
|---|---|
| **Status** | ✅ **Fixed** · **Severity** S2 |
| **Files** | `InventoryStockMovement.kt` (new helpers); `InventoryRepositoryImpl.kt`; `AddBatchViewModel.kt` |
| **Impact** | `InventorySort.EXPIRY` is wrong for most of the year; the "latest" batch picked for the MRP fallback is the oldest. |

**Evidence.** `Constants.DATE_FORMAT = "d MMM yyyy"` is not zero-padded, so
`"16 Oct 2026".compareTo("9 Oct 2026") < 0`. ✅ The app documents this hazard and fixes it
in `domain/model/Vaccination.kt:60-61`; these four sites were missed.

**Fix.** `PatientUtils.parseDate(...)?.time`, as `Vaccination.kt` does.
**Regression test.** `InventoryUtilsExpiryTest` style — two batches whose string order and
date order differ.

**Links.** [[Inventory Management]] · [[Statistics Dashboard]]

---

## NCC-018 — `addStockBatch` is not atomic

| | |
|---|---|
| **Status** | ✅ **Fixed** · **Severity** S2 |
| **Files** | `feature/inventory/data/InventoryRepositoryImpl.kt` |
| **Impact** | Partial stock committed while the UI reports total failure; a retry duplicates stock under fresh UUIDs. |

**Evidence.** ~10 validation branches interleaved with writes in one loop, **no
`transactionRunner.run`**. Contrast `BorrowRepositoryImpl.saveBorrowedItem:64` and
`WasteRepositoryImpl.recordWaste:38`, which are wrapped.

**Fix.** Wrap the loop.
**Regression test.** Fail on the 3rd of 5 batches; assert zero batches committed.

**Links.** [[Inventory Management]] · [[Database Relationships]]

---

## NCC-019 to NCC-024 — Sync and integrity

| ID | Sev | Summary | Location |
|---|---|---|---|
| NCC-019 | S3 | `APPEND_OR_REPLACE` + per-row scheduling ⇒ unbounded work chain | `SyncManagerImpl.kt:32-36`, `SyncRepositoryImpl.kt:84` |
| NCC-020 | S3 | Uploader conflict branch unreachable; `fetchRemoteConflictData` read then ignored | `SyncUploader.kt:486-521` |
| NCC-021 | S3 | Lost-update race: read-then-write `@Update`, no `@Version`, no transaction | `InventoryRepositoryImpl.kt:435` + `:452` |
| NCC-022 | S3 | `AuditLogger.log()` fires on a separate coroutine; called inside 5 live transactions | `AuditLogger.kt:40-42`; `WasteRepositoryImpl.kt:64,102`; `MergePatientsUseCase.kt:47`; `BorrowRepositoryImpl.kt:150,185,287` |
| NCC-023 | S3 | Pull never tombstones; a row deleted on device B survives on A | `ReminderRepositoryImpl.kt:459-504`; `PersonalReminderRepositoryImpl.kt:176-183` |
| NCC-024 | S3 | `.catch {}` on each realtime table flow kills the subscription permanently | `RealtimeChangeSubscriptions.kt:52-54` |

**Fixes.** NCC-019 `ExistingWorkPolicy.REPLACE`. NCC-020 either delete the branch or
evaluate the guard before committing `SYNCING`. NCC-021 add `@Version` to
`VaccineBatchEntity` + wrap read/write. NCC-022 collapse `log()` into `recordLog()`.
NCC-023 apply tombstones on pull. NCC-024 resubscribe with backoff on error.

**Links.** [[Offline Synchronization]] · [[Offline-First Architecture]]

---

## NCC-025 to NCC-031 — S4 items

| ID | Summary | Location |
|---|---|---|
| NCC-025 | `ORDER BY priority DESC` on TEXT sorts `HIGH` last | `SyncQueueDao.kt:12` |
| NCC-026 | `isExpired` returns false on an unparseable date | `InventoryUtils.kt:24` |
| NCC-027 | `isLowStock = totalStock <= globalThreshold` flags at-threshold stock | `InventoryRepositoryImpl.kt:77` |
| NCC-028 | `consultations.visitId` defaults to `""` against a NOT NULL CASCADE FK | `ConsultationEntity.kt:33` |
| NCC-029 | `AuditLogDao` comment claims a composite index that does not exist | `AuditLogDao.kt:30-33` |
| NCC-030 | Audit-count guards query `audit_logs.remarks`; the brand is in `old_value`/`new_value` — and both have **0 call sites** | `VaccineDao.kt:137-141` |
| NCC-031 | `FinanceDao` orders by `COALESCE(...)` — unindexable, so its indices are dead | `FinanceDao.kt:15,18,36,…` |

**Links.** [[Room Database]] · [[Inventory Management]]

---

## NCC-032 to NCC-040 — remaining

| ID | Sev | Summary | Location |
|---|---|---|---|
| NCC-032 | S3 | `reschedule` accepts `reminderDate` and silently discards it | `ReminderRepositoryImpl.kt:296-314` |
| NCC-033 | S3 | `notificationsSentToday` reads two `@Transient` fields with no writer ⇒ always 0 | `ReminderDueListProcessor.kt:115` |
| NCC-034 | S3 | Blank `reminderDate` personal reminder notifies on **every** run | `PersonalReminderNotificationWorker.kt:26-28` |
| NCC-035 | S4 | `2000 + (hash and 0x7fffffff) % 100000` parses as `2000 + (hash and 83647)` | `NotificationHelper.kt:180` |
| NCC-036 | S3 | 60 `viewModelScope.launch` bodies with no `try`/`catch`; 24 are writes | 27 ViewModels |
| NCC-037 | S3 | `readBytesFromUri` checks `OpenableColumns.SIZE` then calls `readBytes()` unbounded | `BackupRepositoryImpl.kt:435-441` |
| NCC-038 | S3 | `inventory_deductions` has no `SyncEntityDescriptor` — the per-dose ledger never reaches the server | `SyncUploader.kt` registry |
| NCC-039 | S4 | `AppDatabase` has both double-checked locking and `@Singleton` | `AppDatabase.kt:81-82`; `DatabaseModule.kt:33-37` |
| NCC-040 | S2 | `AddBatchViewModel.kt:56` `maxByOrNull { it.purchaseDate }` — same lexicographic defect as NCC-017 | `AddBatchViewModel.kt:56` |

---

## Closing a bug

1. Reproduce the steps in the card. If you cannot, the status was wrong — fix the status.
2. Land the fix with a regression test that fails without it.
3. Record the verification: test name, and what it asserted.
4. Move the row to **Fixed** **with that evidence quoted**. No evidence ⇒ no "Fixed".

## Related

- [[Known Bugs]] — narrative detail and suggested ordering
- [[Quality/Security Concerns]] · [[Quality/Hypotheses]]
- [[Quality/Audit Reconciliation]] — provenance of every finding
- [[Release Checklist]] — the gate before shipping a fix
