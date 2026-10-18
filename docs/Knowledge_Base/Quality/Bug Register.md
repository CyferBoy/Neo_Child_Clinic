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
| **In review** | A fix exists on a branch but is **not merged** into this branch | must cite the commit and the branch |
| **Won't Fix** | Deliberately accepted | must record the reason |

⚠️ **A fix that changes the named mechanism is not a fix.** Audit entry #14, #15, #38 and #2
were each marked fixed in `docs/AUDIT_REPORT_2026-10-16.md`; re-verification found a
neighbouring path still producing the original symptom. See
[[Quality/Audit Reconciliation]].

⚠️ **What "Fixed" means here, and where it is weakest.** The vocabulary above requires
verification evidence. This register does not carry that evidence uniformly, and the
difference matters when deciding what is safe to regress:

| Evidence class | Meaning | Items |
|---|---|---|
| **Test-pinned** | a named test fails without the fix and passes with it | [[#ncc-001]], [[#ncc-006]] (🔍 instrumented only), [[#ncc-009]] ⚠️ test still missing, [[#ncc-017]], [[#ncc-019]] |
| **Source-verified** | re-read at `e05f7c0` and confirmed fixed, **no regression test** | [[#ncc-002]], [[#ncc-003]], [[#ncc-005]], [[#ncc-008]], [[#ncc-013]], [[#ncc-014]], [[#ncc-015]], [[#ncc-016]], [[#ncc-018]], [[#ncc-035]], [[#ncc-040]] |
| **Needs a live DB** | cannot be settled by reading source | [[#ncc-002]], [[#ncc-003]] |

⚠️ The repository contains **zero** `*ViewModelTest.kt` files, which is why everything in the
UI layer is source-verified only. Do not read "Fixed" as "regression-protected".

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
| [[#ncc-002]] NCC-002 | Postgres stock trigger corrupts stock on any retried upsert | S1 | ✅ **Fixed** | Supabase |
| [[#ncc-003]] NCC-003 | Legacy stock trigger never dropped — **confirmed live, double-applying** | S1 | ✅ **Fixed** | Supabase |
| [[#ncc-004]] NCC-004 | Reminder failure after commit duplicates the visit and double-deducts | S1 | ✅ Fixed | Vaccination |
| [[#ncc-005]] NCC-005 | `deductStockFromBatch` pre-write throws bypass stock recovery | S1 | ✅ **Fixed** | Inventory |
| [[#ncc-006]] NCC-006 | Soft-delete filter missing on reminder queries | S1 | ✅ **Fixed** | Room |
| [[#ncc-007]] NCC-007 | Trigger absent ⇒ every batch created at zero stock | S2 | ✅ Fixed | Supabase |
| [[#ncc-008]] NCC-008 | `computeDaily` re-sums income, disagreeing with `computeMonthly` | S2 | ✅ **Fixed** | Statistics |
| [[#ncc-009]] NCC-009 | `SingleFlight` waiter always reports "nothing failed" | S2 | ✅ **Fixed** | Sync |
| [[#ncc-010]] NCC-010 | Notification-time change needs a process restart | S2 | ✅ **Fixed** | Reminders |
| [[#ncc-011]] NCC-011 | Auto-backup enable/disable needs a process restart | S2 | ✅ **Fixed** | Backup |
| [[#ncc-012]] NCC-012 | Lock screen is an overlay, not a gate | S2 | ✅ Fixed | Security UX |
| [[#ncc-013]] NCC-013 | `DashboardViewModel.errorMessage` can never be set | S2 | ✅ **Fixed** | UI |
| [[#ncc-014]] NCC-014 | `BorrowedViewModel.actionError` can never be set | S2 | ✅ **Fixed** | UI |
| [[#ncc-015]] NCC-015 | `savePatient` swallows its own validation exception | S2 | ✅ **Fixed** | UI |
| [[#ncc-016]] NCC-016 | `isLoading` can never clear | S2 | ✅ **Fixed** | UI |
| [[#ncc-017]] NCC-017 | Inventory sorts compare `"d MMM yyyy"` lexicographically | S2 | ✅ **Fixed** | Inventory |
| [[#ncc-018]] NCC-018 | `addStockBatch` is not atomic | S2 | ✅ **Fixed** | Inventory |
| [[#ncc-019]] NCC-019 | WorkManager chain grows without bound | S3 | ✅ Fixed | Sync |
| [[#ncc-020]] NCC-020 | Uploader conflict branch is unreachable | S3 | ✅ Fixed | Sync |
| [[#ncc-021]] NCC-021 | Lost-update race on every batch mutation | S3 | ✅ Fixed | Inventory |
| [[#ncc-022]] NCC-022 | `AuditLogger.log()` writes outside the caller's transaction | S3 | ✅ Fixed | Logging |
| [[#ncc-023]] NCC-023 | Pull paths never apply tombstones | S3 | ⚠️ Needs decision — systemic, see note | Sync |
| [[#ncc-024]] NCC-024 | Realtime subscription dies permanently on first error | S3 | ✅ Fixed | Sync |
| [[#ncc-025]] NCC-025 | `SyncQueueDao` orders a TEXT priority column backwards | S4 | Open | Sync |
| [[#ncc-026]] NCC-026 | `isExpired` fails open on an unparseable date | S4 | Open | Inventory |
| [[#ncc-027]] NCC-027 | `isLowStock` uses `<=`, flagging exactly-at-threshold stock | S4 | Open | Inventory |
| [[#ncc-028]] NCC-028 | `consultations.visitId` defaults to `""` — an FK-violating value | S4 | Open | Room |
| [[#ncc-029]] NCC-029 | `AuditLogDao` references a composite index that does not exist | S4 | Open | Room |
| [[#ncc-030]] NCC-030 | `VaccineDao` audit-count guards query the wrong column | S4 | Open | Inventory |
| [[#ncc-031]] NCC-031 | `FinanceDao` sorts on an expression no index can serve | S4 | Open | Room |
| [[#ncc-032]] NCC-032 | `reschedule` discards its `reminderDate` argument | S3 | ✅ Fixed | Reminders |
| [[#ncc-033]] NCC-033 | `notificationsSentToday` is structurally always 0 | S3 | ✅ Fixed | Reminders |
| [[#ncc-034]] NCC-034 | Personal reminder with a blank date notifies every run | S3 | ⚠️ Not a bug | Reminders |
| [[#ncc-035]] NCC-035 | Personal-reminder notification ID has a precedence bug | S4 | ✅ Fixed | Reminders |
| [[#ncc-036]] NCC-036 | 60 `viewModelScope.launch` bodies have no `try`/`catch` | S3 | Open | UI |
| [[#ncc-037]] NCC-037 | `readBytesFromUri` size check is TOCTOU | S3 | ✅ Fixed | Backup |
| [[#ncc-038]] NCC-038 | `inventory_deductions` is never synced to the server | S3 | ⚠️ Needs decision | Sync |
| [[#ncc-039]] NCC-039 | `neochild_db` opened twice as a singleton | S4 | Open | DI |
| [[#ncc-040]] NCC-040 | Latest-batch fallback in `AddBatchViewModel` picks the oldest | S2 | ✅ Fixed | Inventory |
| [[#ncc-041]] NCC-041 | Brand drill-down returned 0 rows for every brand — query matched JSON as comma-text | S2 | ✅ Fixed | Statistics |

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
prior FAILED rows` fail; restoring it makes all 8 pass. Suite was
**260 tests, 0 failures, 0 errors, 0 skipped** (was 252).

⚠️ **Dated figure.** That was the suite size when the fix was verified. The suite has since
grown — the last recorded run is **270 tests, 0 failures (2026-10-17 07:48)**, see
[[Testing]]. Do not use 252/260/273 as the current baseline.

⚠️ **Not covered:** the ledger write is asserted as a pure decision, not against a real
Room database. A future change to how `applyInventoryDiff` reports `deducted` would not be
caught by these tests. The end-to-end path still needs an instrumented test.

**Links.** [[Vaccination Management]] · [[Inventory Management]] · [[Testing]]

---

## NCC-002 — Postgres stock trigger corrupts stock on any retried upsert

| | |
|---|---|
| **Status** | ✅ **Fixed** · **Severity** S1 Critical |
| **Files** | `supabase/migrations/20261013140000_stock_trigger_negative_guard.sql:56-61`, installed at `20261016_rls_index_and_stock_trigger_hardening.sql:140`, corrected by `20261017_stock_trigger_duplicate_and_update_arithmetic.sql` |
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

**Verification.** ✅ Migration `20261017_stock_trigger_duplicate_and_update_arithmetic.sql`
lands on this branch: it `create or replace`s `public.update_batch_stock()` with the UPDATE
branch computing `v_delta := new.quantity - old.quantity` (`:67`) and undoing `OLD.batch_id`
when a row moves batch, instead of the old `- OLD.quantity`. 🔍 **Not verified against a
live database** — confirm with the pgTAP/psql script in *Fix* above before shipping.

⚠️ **Status history.** This card read **In review** from 2026-10-18 until `origin/main`
fast-forwarded into this branch and brought the migration with it. Before that merge the fix
existed only as commit `98f8172` on the now-deleted `fix/stock-trigger-double-apply`.

**Links.** [[Supabase]] · [[Inventory Management]] · [[Database Schema]]

---

## NCC-003 — Legacy stock trigger never dropped; possible double-application

| | |
|---|---|
| **Status** | ✅ **Fixed** · **Severity** S1 Critical |
| **Files** | `20261016_rls_index_and_stock_trigger_hardening.sql:134-138`, resolved by `20261017_stock_trigger_duplicate_and_update_arithmetic.sql:26` |
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
| **Status** | ✅ Fixed 2026-10-18 · **Severity** S1 Critical |
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

**Fix.** `ClinicalVaccinationService.recordVaccination` now takes `reminderSpecs` and writes
them inside its own `transactionRunner.run` block. `AddVaccinationViewModel` passes the specs
instead of looping. `vaccination.patientId` / `vaccination.id` replace `patient.id` /
`vaccinationId` at the call site — verified value-identical (`Vaccination` is built from both).
The edit path already did this via `VaccinationEditEngine`, so create is now symmetric.

**Regression test.** `RecordVaccinationTransactionTest` (3 cases). Asserts the reminder write
happens *inside* `TransactionRunner.run` — `RecordingTransactionRunner.inTransaction` is true
only while its block executes. Verified to fail when the loop is moved back out. Service-level
doubles are generated from the interfaces (`ServiceStubs.kt`) so they cannot drift.

⚠️ The test asserts ordering, not rollback. Room's `withTransaction` owns the rollback; proving
that needs a real instrumented DB test, which needs an emulator this environment lacks.

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

## NCC-006 — Soft-delete filter missing on reminder queries

| | |
|---|---|
| **Status** | ✅ **Fixed** · **Severity** S1 Critical |
| **Files** | `data/local/dao/DueReminderDao.kt`; `data/local/dao/BackupDao.kt` |
| **Impact** | Soft-deleted reminders appear on patient history, in backups, and in the conflict guard. |

**Evidence (as found, before the fix).** A subset of `DueReminderDao` queries omitted
`is_deleted = 0`; the rest filtered. `migration31_32` (`AppDatabase.kt:390-415`) hard-deletes
soft-deleted rows on 18 tables but **deliberately excludes `reminders`**, so an upgraded
device carries live `is_deleted = 1` rows.

⚠️ **Correction (2026-10-18).** This card previously gave three mutually inconsistent
figures — index "9 queries", heading "5 live reminder queries", evidence "7 of 19", and fix
"18 predicates in total". Measured against `DueReminderDao.kt` at `e05f7c0`: **21 `@Query`,
16 `SELECT`, all 16 now filtered, 18 `is_deleted = 0` predicates in the file** (two queries
filter in both a subquery and the outer `WHERE`). Repo-wide the DAO layer holds **19**
(18 here + `BackupDao.kt:124`); `VaccineDetailViewModel.kt` adds a 20th outside the DAOs.

**Live reachability:** `getRemindersByVisitId` feeds `@Relation`, and **`@Relation` cannot
filter**. `getReminderById` has 13 call sites. `BackupDao.getAllRemindersForBackup` exports
them into the `.nccb` payload.

**Note.** Nothing in `app/src/main` writes `is_deleted = 1` today — only a pre-v31 client
could create the rows. The inconsistency is latent, not active.

**Fix applied.** Every lookup now filters, plus the paginated variants (dead code, but
wiring them later unfiltered is the same trap) and `BackupDao.getAllRemindersForBackup` —
19 `is_deleted = 0` predicates across the DAO layer. The file's header comment asserted the
rule while breaking it; it now states what the file does and why (migration 31→32 leaves
`reminders` out of its hard delete).

**Verification.** 🔍 **Instrumented only.** `ReminderSoftDeleteFilterTest` (12 tests) and
`UpcomingVaccinationDaoTest` (16 tests) live in `androidTest`, which `./gradlew test` does not
run, so this is compile-verified only. ⚠️ **Not executed in this pass.** Run
`./gradlew connectedDebugAndroidTest` on a device before shipping.

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

**NCC-023 — re-scoped, not fixed.** The card describes this as "pull paths never apply
tombstones", which implies a tombstone exists to apply. **It does not.**

A reminder delete is a hard `postgrest.from(table).delete()` (`SyncUploader.kt:460`). There is no
soft-delete and no tombstone row. The `eq("is_deleted", false)` filter on the pull is a leftover
from `20260816_security_hardening.sql`, which added `is_deleted` to `profiles` explicitly and to
other tables opportunistically — the app never sets it for reminders. So the filter is inert, and
a row deleted on device B is simply *absent* from the remote set.

The only way to propagate that is **absence reconciliation**: fetch the full remote set, then
delete local rows that are `isSynced`, absent remotely, and have no pending queue entry. Nothing
in the codebase does this — checked all 15 refresh/pull sites, and the 21 `eq("is_deleted", ...)`
filters are all row-level, never set-level.

⚠️ **Not implemented here, deliberately.** That reconciliation deletes rows, and a partial or
failed remote fetch would then delete healthy local records — for a clinical app that is
catastrophic and not a bug's worth of blast radius. Doing it safely means deciding the policy once
and applying it consistently across every pull path, not patching the reminders query and calling
it fixed. It needs review and a staged rollout, and no pull path can be trusted to be complete
until the reconciliation exists.

**NCC-021 fix.** `getBatchById` → `updateBatch` is a read-modify-write, `VaccineBatchEntity`
carries no `@Version`, and the surrounding code held no transaction. Two concurrent deductions
could both read the same `remainingQuantity`, compute the same new value, and have the second
write discard the first — stock falls by one dose while the ledger records both.

`TransactionRunnerImpl` uses Room's `withTransaction`, which **nests safely**, so wrapping is
correct even where the caller already opened one.

Rather than patch only the cited lines, every sibling was checked. Four methods share the shape:

| Method | Caller holds a transaction? |
|---|---|
| `deductStockFromBatch` | no — **wrapped** |
| `reverseDeduction` | no — `execute` is called straight from `AddVaccinationViewModel:676` and opens none — **wrapped** |
| `addStockToBatch` | yes (both callers, in `WasteRepositoryImpl`) |
| `returnBorrowedStock` | yes (all three callers, in `BorrowRepositoryImpl`) |

Only the two genuinely unprotected ones were wrapped; the other two already inherit atomicity
from their callers and adding wrappers there would be noise.

⚠️ **Found while tracing this, not fixed:** `VaccinationEditEngine.execute` runs with **no
transaction around it at all** (`AddVaccinationViewModel:676` → `execute`, and `execute` opens
none). So the whole edit path — stock reversal, item reconciliation, reminder reconciliation — is
non-atomic, and a failure partway through leaves a half-edited visit. `reverseDeduction` being
unprotected above is a symptom of that. The fix mirrors NCC-004: put the transaction inside the
domain service. It changes failure semantics, so it is raised rather than done under this ticket.

**NCC-038 — re-scoped, not fixed.** The card says "`inventory_deductions` has no
`SyncEntityDescriptor`", which reads like one missing registry entry. It is not:

* no `INVENTORY_DEDUCTION` string exists anywhere in `app/src/main` — nothing enqueues it;
* `inventory_deductions` appears in **no** migration — there is no server table at all;
* there is therefore nothing for a descriptor to point at.

So closing it means building the feature: a Supabase table plus RLS, a descriptor, an enqueue call
at every deduction site, and a pull path. That is a product decision nobody has asked for, so it
is recorded rather than silently built.

**The impact is also narrower than the card implies.** `inventory_transactions` *is* synced
(`INVENTORY_TRANSACTION` descriptor) and carries `batchId` and `visitId`, so per-dose batch
linkage and server-side stock accounting are already covered. What is genuinely missing is the
**FAILED** deductions — a failed deduction writes no ledger row, so it is invisible on other
devices and on the server. That is a genuine audit gap, and it is the part worth deciding on.

**NCC-022 fix.** `AuditLogger.log()` launches on its own `CoroutineScope`, so it never joins
the caller's transaction. Called inside `transactionRunner.run`, the audit row commits
independently — it survives a rollback and records an action that never happened.

Re-measured rather than trusting the card: 17 `log()` call sites across 5 files, of which exactly
**5** sit inside a live transaction (`WasteRepositoryImpl:64,102`, `MergePatientsUseCase:47`,
`BorrowRepositoryImpl:185,287`). `BorrowRepositoryImpl:150` was cited but is not in a transaction.
The other 12 are genuine fire-and-forget and are untouched.

The five now call `recordLog`, the existing suspend version documented "for use inside
transactions". No new API.

`MergePatientsUseCase` is the worst of the five: a patient merge whose transaction later rolls
back would leave an audit row asserting two patient records were merged when they were not.

`log()` now carries a warning pointing at `recordLog` for transactional callers, since the trap is
invisible at the call site.

⚠️ **No automated test.** The distinction is observable only as a rollback-and-inspect case.

**NCC-020 fix.** The remote-newer self-heal branch was guarded by
`!syncQueueDao.isUnsynced(entityName, entityId)`. The item being uploaded had already been
committed as `SYNCING` (not `SYNCED`) by the group-status transaction, so that predicate was
permanently true for the row in flight and the branch **could never execute**. The conflict
policy had never run for anyone, and the `fetchRemoteConflictData` read was paid for on every
item purely to be ignored.

Added `SyncQueueDao.isUnsyncedExcluding(name, id, queueId)`, which asks the question the guard
actually means: is some *other* unacknowledged edit for this entity still queued? The in-flight
row says nothing about whether the local edit is safely on the server — it is about to be.

The other 15 `isUnsynced` callers are all in **pull** paths, where no row is in flight, so they
never shared the defect and are untouched.

⚠️ **Behaviour change.** This branch has never executed, so the code it enables has never run in
production. It is now reachable for the first time and will start discarding local copies when
the remote is genuinely newer. That is the documented policy (`docs/SYNC.md`), but it is worth
watching on the first real sync after this ships.

⚠️ **No automated test.** The branch sits inside the uploader's per-item path against a real
PostgREST client.

**NCC-024 fix.** `.catch { Log.e(...) }` completes a flow. Every transient failure — a dropped
socket, a failed token refresh, a server-side channel error, all routine in the field — therefore
ended that table's collector for the life of the process. The channel stayed joined, so nothing
looked broken and no further change ever arrived until restart.

Replaced with a retry loop: exponential backoff 1 s → 60 s, `CancellationException` rethrown so
cancellation still works, and a flow that completes on its own is treated as a failure (a live
subscription that completes will never deliver again) rather than exiting quietly. Bounded so a
persistently failing channel cannot spin.

⚠️ **No automated test.** The retry sits inside a `callbackFlow` over a real `Realtime`, which
would need a fake channel implementation. Verified by compilation and inspection only.

**NCC-037 fix.** `OpenableColumns.SIZE` is advisory, so the check could never be the only guard:
providers commonly report `SIZE` as null, and a streaming provider can declare a length that does
not match the bytes it yields. In that case the code called `readBytes()` on an unbounded stream —
not merely a race, but no bound at all.

The read is now bounded: a 64 KiB buffer loop that throws `TooLarge` the moment the running total
passes `MAX_BACKUP_BYTES`. The pre-check stays as a cheap fail-fast, but correctness no longer
depends on provider metadata being honest.

⚠️ **No automated test.** `readBytesFromUri` is private and takes a `ContentResolver`.

**NCC-033 fix.** Same shape as NCC-032. `notificationSent` and `lastReminderTime` were
`@Transient` on the entity with no writer anywhere, so
`reminders.count { it.notificationSent && it.lastReminderTime >= todayStart }` was structurally
`0`. That fed `ReminderStats.notificationsSentToday`, which **nothing reads** — no screen, no
consumer. So the statistic was a permanent, invisible zero.

Deleted the count, the unused `ReminderStats` field, and both dead declarations. A dashboard
number that is always 0 and never shown is not worth persisting two columns to fix; if the
statistic is ever wanted, it needs a real writer and a real consumer at the same time.

**NCC-034 reclassified — the register was wrong.** "Notifies on every run" is the stated
behaviour, not a defect. `NotificationHelper.showPersonalReminderNotification` renders the undated
branch as:

> `undated -> "Daily follow-up reminder"`

The worker is a 24-hour `PeriodicWorkRequest` (`ReminderScheduler.kt:81`), `getActiveReminders`
selects only `PENDING`/`READY`, and the notification uses a stable id with `setAutoCancel(true)` —
so it re-posts once a day until the reminder is completed or cancelled, which is exactly what the
copy promises. The overdue branch behaves the same way, consistently.

**No code changed.** Adding a "notify once" flag would have silently broken the documented
behaviour, on the strength of a report that never checked the copy. If daily nagging is *not*
wanted, that is a product decision, and the change is a `notified_at` column plus a guard in the
worker.

**NCC-032 fix.** The chain was dead at *every* layer, not just the repository:

| Layer | State |
|---|---|
| `RescheduleDialog` | collected a "Reminder Date" |
| `DueTab` → `DueViewModel` → interface → impl | passed it along |
| `ReminderRepositoryImpl.reschedule` | discarded it |
| `ReminderEntity.reminderDate` | `@Transient` — never stored, no column exists |
| `Reminder.reminderDate` (domain) | never read or written by anything |

`reminder_date` exists only on `PersonalReminderEntity`, a different table with its own worker.
So the user picked a date, the app accepted it, and nothing happened — with no error anywhere.

Deleted rather than implemented. Persisting it would mean a new column, a Room migration, a
sync field and a server-side counterpart, for a feature nobody asked for, against a field that
had been inert since it was written. Removing it makes the dialog tell the truth. Verified with
`grep`: no `reminderDate` reference remains outside the personal-reminder path.

**NCC-007 fix.** `20261018_ensure_stock_trigger_installed.sql`. The client cannot know whether the
server trigger exists, so the fix belongs server-side, and the server must refuse to continue
rather than silently zero all stock:
1. installs `inventory_transactions_update_batch_stock` if it is missing but installable —
   repairing 20261016's silent skip rather than only reporting it;
2. raises with the specific missing piece if it still cannot be installed.

Written as its own migration, not appended to `20261017`: a database that already applied
`20261017` will not re-run it, and that is exactly the case that must be caught.

⚠️ **Not executed.** No Postgres in this environment — statically checked only (delimiter
balance, structure). Applying it to a live database is still required.

**Verification.** ✅ once applied — the migration fails loudly if the trigger is absent.
🔍 independent check still pending:
`select proname, prosecdef from pg_proc where pronamespace='public'::regnamespace and proname='update_batch_stock';`

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

**Verification.** ✅ Compiles and the full suite passed (273 tests, 0 failures — ⚠️ dated;
the suite is **270** as of 2026-10-17, see [[Testing]]).
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
| **Status** | ✅ **Fixed** | ✅ **Fixed** |
| **Files** | `core/notification/ReminderScheduler.kt` | `data/backup/BackupAutoScheduler.kt` |
| **Call sites (as found)** | 2, both `NeoChildApp.kt:111-112` | **0** for `schedule()` |
| **Effect (as found)** | reminder time change waits for a restart | auto-backup enable/disable waits for a restart |
| **Fix** | call the scheduler from `NotificationSettingsViewModel.updateSettings` | call `schedule()`/`cancel()` from `BackupViewModel` |

⚠️ **Corrected 2026-10-18.** Both are **fixed in source**; the "as found" rows are kept for
provenance. Re-verified:

- **NCC-010** — `NotificationSettingsViewModel.updateSettings` now calls
  `reminderScheduler.scheduleDailySummary()` (`:37`) and
  `schedulePersonalReminderNotifications()` (`:38`). The inline comment records
  `ExistingPeriodicWorkPolicy.UPDATE` recomputing the delay. Note the card's fix text said
  one call; there are **two**.
- **NCC-011** — `BackupViewModel` now calls `backupAutoScheduler.schedule(...)` at `:209`
  and `:215`, and `cancel()` at `:221`. The card's fix text cited `:202-215`; the scheduler
  calls are at **`:209`/`:215`**, outside that range.

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

**Fix.** Two parts, because gating alone would not have closed the window.

1. `MainActivity` now composes `AppNavigation` only when `lockStateResolved && !isLocked`.
   `navController` deliberately stays outside the gate — `NavController` saves its own back
   stack, so unlocking returns the user where they were.
2. `isAppLocked == false` used to mean both "unlocked" and "nobody has checked". Deciding needs
   the session status, which takes seconds when offline, so the whole patient-data tree was
   built during that gap. `BiometricLockManager.lockStateResolved` now separates the two, and
   the UI shows a progress indicator while the answer is unknown — never patient data.

While unresolved the UI renders neither the app nor the lock screen; `checkAppLock()` marks the
state resolved in a `finally`, so the not-authenticated early return cannot strand a logged-out
user on the spinner.

**Regression test.** 4 cases added to `BiometricLockManagerTest`. The load-bearing one:
`lock()` must not mark the state resolved — NeoChildApp's pre-render re-arm makes the app locked
without anyone having authenticated.

⚠️ **Not covered:** the composition gate itself. Asserting "navigation is not composed" needs a
Compose UI test, which needs `createAndroidComposeRule` (not a dependency) and an emulator this
environment lacks. The gate is verified by compilation and inspection only.

**Verification.** 🔍 On-device still pending: confirm the opaque `Surface` fully occludes dialogs,
and that unlocking restores the previous screen.

**Links.** [[Architecture Map]] · [[Error Handling]]

---

## NCC-013 / NCC-014 / NCC-015 — Errors that can never be shown

| | NCC-013 | NCC-014 | NCC-015 |
|---|---|---|---|
| **Severity** | S2 | S2 | S2 |
| **Status** | ✅ **Fixed** | ✅ **Fixed** | ✅ **Fixed** |
| **Location** | `DashboardViewModel.kt:48` | `BorrowedViewModel.kt:61` | `PatientViewModel.kt:200-215` |
| **Defect (as found)** | `errorMessage` declared, **0 writers** | `actionError` declared, **0 writers** | throws its own validation error, catches and discards it |
| **User saw** | nothing | nothing | nothing, and the screen never advanced |
| **Evidence (as found)** | the only `DashboardUiState(...)` construction never passed it | 1 grep hit — the declaration | caller passed only `onComplete` |

**Fix.** Wire the field or delete it; give `savePatient` an `onError` callback.

⚠️ **Corrected 2026-10-18 (commit `e05f7c0`, "fix(ui): five silent-failure paths").** All
three are **fixed in source**; the "as found" rows are preserved for provenance.
Line numbers were previously off by 1–5:

| | Was cited | Actual | Fix in source |
|---|---|---|---|
| NCC-013 | `:47` | `:48` | written at `:279` from `_refreshError`; cleared at `:431-432` |
| NCC-014 | `:59` | `:61` | written at `:195, :200, :207, :238, :251`; note at `:74-77` records why it was dead |
| NCC-015 | `:194-206` | `:200-215` | `savePatient(onComplete, onError)`; `onError` at `:203`, rethrows `CancellationException` at `:212`, invokes at `:215` |

⚠️ "**`BorrowedScreen.kt` has 0 error references**" is **wrong** — the file has 9
case-insensitive matches. The defect was in the ViewModel, not the screen.

⚠️ **Regression tests are missing for all three.** Verified 2026-10-18: `grep -rl
savePatient app/src/test app/src/androidTest` returns nothing, and the repository contains
**zero** `*ViewModelTest.kt` files. These are ✅ Fixed on **source inspection** plus compile,
not on a test that fails without the fix — narrower evidence than [[#ncc-001]] and
[[#ncc-009]] carry. Do not read "Fixed" here as "regression-protected".

**Links.** [[MVVM]] · [[Error Handling]]

---

## NCC-016 — `isLoading` can never clear

| | |
|---|---|
| **Status** | ✅ **Fixed** · **Severity** S2 |
| **Files** | all five: `FinanceDetailsViewModel`, `ProfileViewModel`, `FullReportViewModel`, `StatisticsViewModel`, `VaccineDetailViewModel` |
| **Impact** | Save/refresh buttons stay disabled forever with no error shown. |

**Evidence (as found).** `.first()` on Room flows inside `viewModelScope.launch` with no
`try`/`finally`; or `?: return@launch` after `isLoading = true`.

**Fix.** Add `.catch {}` or `finally { isLoading = false }`.

⚠️ **Corrected 2026-10-18 (commit `e05f7c0`).** All five are fixed, but **by two different
mechanisms** — the card implies one. Re-verified:

| ViewModel | Mechanism |
|---|---|
| `FinanceDetailsViewModel` | `try`/`finally` at `:50-60`, comment citing NCC-016 at `:57` |
| `ProfileViewModel` | sets `isLoading = false` **and** an `error` message before `return@launch` (`:86-93`) — no `.catch`, no `finally` |
| `FullReportViewModel` | `.catch` at `:99` |
| `StatisticsViewModel` | `.catch` at `:100` |
| `VaccineDetailViewModel` | `.catch` — the reference pattern |

⚠️ The card called `VaccineDetailViewModel.kt:99-104` correct "but it discards `e.message`".
That remains true and is not part of this bug.

**Links.** [[MVVM]] · [[Statistics Dashboard]]

---

## NCC-017 — Inventory sorts compare dates lexicographically

| | |
|---|---|
| **Status** | ✅ **Fixed** · **Severity** S2 |
| **Files** | `InventoryStockMovement.kt` (new helpers); `InventoryRepositoryImpl.kt`; `AddBatchViewModel.kt` |
| **Impact** | `InventorySort.EXPIRY` is wrong for most of the year; the "latest" batch picked for the MRP fallback is the oldest. |

**Evidence (as found).** `Constants.DATE_FORMAT = "d MMM yyyy"` is not zero-padded, so
`"16 Oct 2026".compareTo("9 Oct 2026") < 0`. ✅ The app documents this hazard and fixes it
in `domain/model/Vaccination.kt:60-61`; these four sites were missed.

**Fix.** `PatientUtils.parseDate(...)?.time`, as `Vaccination.kt` does.
**Regression test.** `InventoryUtilsExpiryTest` style — two batches whose string order and
date order differ.

✅ **Fixed in source**, re-verified 2026-10-18 at `InventoryRepositoryImpl.kt:127-130`:
`EXPIRY` → `batchExpiryMillis()`, `NEWEST` → `latestPurchaseMillis()`, `OLDEST` →
`earliestPurchaseMillis()`. The MRP fallback at `:84` parses rather than string-maxing, with
an inline comment citing NCC-017. ⚠️ The card cites `:121`, `:123-124` and `:81`; those were
the pre-fix lines.

✅ **Regression test present:** `InventorySortKeysTest` (13 `@Test`) pins both halves — the
stored format genuinely misorders (`the stored format really does misorder across a month
boundary`) and the sort keys do not (`expiry key orders 2 Jan after 28 Dec`, plus day/month
and year boundaries and blank-expiry handling).

**Links.** [[Inventory Management]] · [[Statistics Dashboard]]

---

## NCC-018 — `addStockBatch` is not atomic

| | |
|---|---|
| **Status** | ✅ **Fixed** · **Severity** S2 |
| **Files** | `feature/inventory/data/InventoryRepositoryImpl.kt` |
| **Impact** | Partial stock committed while the UI reports total failure; a retry duplicates stock under fresh UUIDs. |

**Evidence (as found).** ~10 validation branches interleaved with writes in one loop, **no
`transactionRunner.run`**. Contrast `BorrowRepositoryImpl.saveBorrowedItem:64` and
`WasteRepositoryImpl.recordWaste:38`, which are wrapped.

**Fix.** Wrap the loop.
**Regression test.** Fail on the 3rd of 5 batches; assert zero batches committed.

✅ **Fixed in source** — `InventoryRepositoryImpl.addStockBatch` (`:239`) now opens
`transactionRunner.run {` at `:250`, with an inline comment citing NCC-018 and describing
the partial-commit failure mode. ⚠️ The regression test named above was **not** located in
`app/src/test` during the 2026-10-18 pass; `InventoryStockMovementTest` covers stock
movement but no test was confirmed to fail a 3rd-of-5-batch submission.

**Links.** [[Inventory Management]] · [[Database Relationships]]

---

## NCC-019 to NCC-024 — Sync and integrity

| ID | Sev | Status | Summary | Location |
|---|---|---|---|---|
| NCC-019 | S3 | ✅ **Fixed** | `APPEND_OR_REPLACE` + per-row scheduling ⇒ unbounded work chain | `SyncManagerImpl.kt:39`, `:61` — now `ExistingWorkPolicy.REPLACE`; `:32-36` is the comment recording the switch. This is what refuted [[#hyp-002]]. |
| NCC-020 | S3 | ✅ **Fixed** | Uploader conflict branch unreachable; `fetchRemoteConflictData` read then ignored | `SyncUploader.kt`; new `SyncQueueDao.isUnsyncedExcluding` |
| NCC-021 | S3 | ✅ **Fixed** | Lost-update race: read-then-write `@Update`, no `@Version`, no transaction | `InventoryRepositoryImpl.kt` — `deductStockFromBatch` and `reverseDeduction` now transactional |
| NCC-022 | S3 | ✅ **Fixed** | `AuditLogger.log()` fired on a separate coroutine inside 5 live transactions | `WasteRepositoryImpl.kt:64,102`; `MergePatientsUseCase.kt:47`; `BorrowRepositoryImpl.kt:185,287` — all now call `recordLog` |
| NCC-023 | S3 | ⚠️ **Needs decision** | No pull path reconciles remote *absence* — and there are no tombstones to apply | all pull paths; deletes are a hard PostgREST `delete()` (`SyncUploader.kt:460`) |
| NCC-024 | S3 | ✅ **Fixed** | `.catch {}` on each realtime table flow killed the subscription permanently | `RealtimeChangeSubscriptions.kt` — retry loop with backoff |

**Fixes.** NCC-019 `ExistingWorkPolicy.REPLACE`. NCC-020 either delete the branch or
evaluate the guard before committing `SYNCING`. NCC-021 add `@Version` to
`VaccineBatchEntity` + wrap read/write. NCC-022 collapse `log()` into `recordLog()`.
NCC-023 apply tombstones on pull. NCC-024 resubscribe with backoff on error.

**Links.** [[Offline Synchronization]] · [[Offline-First Architecture]]

---

## NCC-025 to NCC-031 — S4 items

| ID | Sev | Status | Summary | Location |
|---|---|---|---|---|
| NCC-025 | S4 | Open | `ORDER BY priority DESC` on TEXT sorts `HIGH` last | `SyncQueueDao.kt:12` — ✅ re-verified 2026-10-18 |
| NCC-026 | S4 | Open | `isExpired` returns false on an unparseable date | `InventoryUtils.kt:24` — ✅ re-verified 2026-10-18 |
| NCC-027 | S4 | Open | `isLowStock = totalStock <= globalThreshold` flags at-threshold stock | `InventoryRepositoryImpl.kt:78` (was `:77`) |
| NCC-028 | S4 | Open | `consultations.visitId` defaults to `""` against a NOT NULL CASCADE FK | `ConsultationEntity.kt:33` — ✅ re-verified 2026-10-18 |
| NCC-029 | S4 | Open | `AuditLogDao` comment claims a composite index that does not exist | `AuditLogDao.kt:29-33` — ✅ re-verified 2026-10-18: the comment spans `:29-33` and the DAO contains **zero** `Index(` declarations |
| NCC-030 | S4 | Open | Audit-count guards query `audit_logs.remarks`; the brand is in `old_value`/`new_value` — and both have **0 call sites** | `VaccineDao.kt:137-141` — ✅ re-verified 2026-10-18 |
| NCC-031 | S4 | Open | `FinanceDao` orders by `COALESCE(...)` — unindexable, so its indices are dead | `FinanceDao.kt:15,18,36` — ✅ re-verified 2026-10-18 |

**Links.** [[Room Database]] · [[Inventory Management]]

---

## NCC-032 to NCC-040 — remaining

| ID | Sev | Status | Summary | Location |
|---|---|---|---|---|
| NCC-032 | S3 | ✅ **Fixed** | `reschedule` accepted `reminderDate` and discarded it — deleted the dead chain | `ReminderDialogs.kt` (picker removed), `DueTab.kt`, `DueViewModel.kt`, `ReminderRepository.kt`, `ReminderRepositoryImpl.kt`, `ReminderEntity.kt`, `Reminder.kt` |
| NCC-033 | S3 | ✅ **Fixed** | `notificationsSentToday` read two `@Transient` fields with no writer ⇒ always 0 — deleted the dead chain | `ReminderDueListProcessor.kt`, `ReminderStats.kt`, `ReminderEntity.kt`, `Reminder.kt` |
| NCC-034 | S3 | ⚠️ **Not a bug** | Blank `reminderDate` notifies on every run — this is the stated design | `PersonalReminderNotificationWorker.kt:27-31`; intent quoted from `NotificationHelper.kt:171` |
| NCC-035 | S4 | ✅ **Fixed** | `2000 + (hash and 0x7fffffff) % 100000` parses as `2000 + (hash and 83647)` | `NotificationHelper.kt:187` — now `PERSONAL_REMINDER_BASE_ID + ((id.hashCode() and 0x7fffffff) % 100000)`; comment at `:185` records the old precedence bug |
| NCC-036 | S3 | Open | `viewModelScope.launch` bodies with no `try`/`catch` | **140** launches across **34** `*ViewModel.kt` files. ⚠️ The card said "60 … 27 ViewModels"; re-measured 2026-10-18. The exact count of *unprotected* bodies was not recomputed here — treat the number as an upper bound. |
| NCC-037 | S3 | ✅ **Fixed** | `readBytesFromUri` checked `OpenableColumns.SIZE` then read unbounded | `BackupRepositoryImpl.kt` — read is now bounded at `MAX_BACKUP_BYTES` |
| NCC-038 | S3 | ⚠️ **Needs decision** | `inventory_deductions` is local-only — but this is a missing feature, not a defect, and the impact is narrower than stated | no server table exists in any migration, nothing enqueues `INVENTORY_DEDUCTION`, no descriptor |
| NCC-039 | S4 | Open | `AppDatabase` has both double-checked locking and `@Singleton` | `AppDatabase.kt:81-82`; `DatabaseModule.kt:33-37` |
| NCC-040 | S2 | ✅ **Fixed** | `AddBatchViewModel` `maxByOrNull { it.purchaseDate }` — same lexicographic defect as NCC-017 | `AddBatchViewModel.kt:59-60` (was cited `:56`) — now `newestBatch()` with a `parseDate` fallback |
| NCC-041 | S2 | ✅ **Fixed** | `getUpcomingVaccinationsByVaccineId` matched `nxt_vaccine_id` as comma-joined text; the column is JSON, so it returned 0 rows for every brand | `DueReminderDao.kt` — `json_each`; `ReminderEntity.kt` comment corrected |

---

## NCC-041 — Brand drill-down returned nothing for every brand

| | |
|---|---|
| **Status** | ✅ Fixed 2026-10-18 · **Severity** S2 High |
| **Files** | `data/local/dao/DueReminderDao.kt`; `data/local/entity/ReminderEntity.kt` |
| **Found by** | the first real execution of `androidTest` (PR #17) |

**Evidence.** `nxtVaccineId` is a `List<String>?` with a `TypeConverter`, so it is stored as
**JSON** — `Converter.fromList` calls `Json.encodeToString`, producing `["vac-a"]`. The query
tested it as comma-joined text:

```sql
(',' || COALESCE(nxt_vaccine_id, '') || ',') LIKE '%,' || :vaccineId || ',%'
```

Building `,["vac-a"],` and looking for `,vac-a,` can never match, so
`getUpcomingVaccinationsByVaccineId` returned **zero rows for every brand**.

The entity comment is what caused it, and read plausibly: *"matching the comma-separated
convention already used for list-like columns elsewhere (e.g. `patient_visits.vaccine_ids`)"*.
That convention is real — but it belongs to `patient_visits.vaccine_ids`, a plain TEXT column
written with `joinToString(",")`. `nxt_vaccine_id` never used it; `Converters` has written JSON
since the column was introduced.

**Impact.** The Statistics → Vaccination → Upcoming → *by brand* drill-down showed no rows, and
the per-brand counts did not sum to the type card above them. The type-level view was unaffected
(`getUpcomingVaccinationsByType` does not touch this column), which is why it went unnoticed.

**Fix.** Match the column as JSON, exactly, with the JSON1 table-valued function:

```sql
CASE WHEN json_valid(nxt_vaccine_id)
     THEN EXISTS (SELECT 1 FROM json_each(nxt_vaccine_id) WHERE json_each.value = :vaccineId)
     ELSE 0 END
```

`json_each` is an exact element match with no delimiter or substring ambiguity, and `minSdk` is 29
so JSON1 is available. `json_valid` guards it — `json_each()` raises on malformed input, which
would fail the entire query rather than just that row.

**Verification.** Executed against SQLite 3.45.1 with fixtures mirroring the instrumented tests:

| Probe | Result |
|---|---|
| `vac-a` with `["vac-a"]`, `["vac-a","vac-b"]`, `["vac-b","vac-a"]` | matched — including middle position |
| `vac-a` must not match `["vac-a2"]` | not matched |
| `NULL`, soft-deleted, `status = 'COMPLETED'` | excluded |
| a malformed comma-format row present | query did not raise; row excluded |
| **the old formula, same data** | matched **only** the malformed row — and nothing JSON-shaped |

**Regression test.** `UpcomingVaccinationDaoTest` — 8 cases, already written, red until this fix.
They had never executed before PR #17; they are the check.

**Links.** [[Statistics Dashboard]] · [[Database Schema]]

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
