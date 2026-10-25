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
| **Not a bug** | The card's premise is contradicted by the code — the behaviour is deliberate, or the stated impact does not occur | must quote the evidence that refutes the card |
| **Needs decision** | A real gap, but closing it needs a policy or product decision nobody has made; landing a fix unilaterally would be a guess | must name the decision and the options |

⚠️ **A fix that changes the named mechanism is not a fix.** Audit entry #14, #15, #38 and #2
were each marked fixed in `docs/AUDIT_REPORT_2026-10-16.md`; re-verification found a
neighbouring path still producing the original symptom. See
[[Quality/Audit Reconciliation]].

⚠️ **What "Fixed" means here, and where it is weakest.** The vocabulary above requires
verification evidence. This register does not carry that evidence uniformly, and the
difference matters when deciding what is safe to regress:

| Evidence class | Meaning | Items |
|---|---|---|
| **Test-pinned** | a named test fails without the fix and passes with it | NCC-001, NCC-002, NCC-006 (🔍 instrumented only), NCC-017, NCC-043, NCC-044 |
| **Source-verified** | re-read at `e05f7c0`, or re-verified 2026-10-19 / 2026-10-25, and confirmed fixed; **no regression test** | NCC-003, NCC-005, NCC-008, NCC-009 (🔍 test pins `run()` only), NCC-013, NCC-014, NCC-015, NCC-016, NCC-018, NCC-019 (⚠️ no test), NCC-025, NCC-029, NCC-030, NCC-035, NCC-039, NCC-040 |
| **Needs a live DB** | cannot be settled by reading source | NCC-003, NCC-007 |

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

IDs are section headings below — jump to them with the outline pane (`Ctrl/Cmd+Shift+O`).
They stay plain text here because a wikilink alias needs a `|`, which would end the table cell.

| ID | Title | Sev | Status | Area |
|---|---|---|---|---|
| NCC-001 | Edit that raises quantity on a failed batch moves stock but records `FAILED` | S1 | ✅ **Fixed** | Inventory |
| NCC-002 | Postgres stock trigger corrupts stock on any retried upsert | S1 | ✅ **Fixed** | Supabase |
| NCC-003 | Legacy stock trigger never dropped — **confirmed live, double-applying** | S1 | ✅ **Fixed** | Supabase |
| NCC-004 | Reminder failure after commit duplicates the visit and double-deducts | S1 | ✅ Fixed | Vaccination |
| NCC-005 | `deductStockFromBatch` pre-write throws bypass stock recovery | S1 | ✅ **Fixed** | Inventory |
| NCC-006 | Soft-delete filter missing on reminder queries | S1 | ✅ **Fixed** | Room |
| NCC-007 | Trigger absent ⇒ every batch created at zero stock | S2 | ✅ Fixed | Supabase |
| NCC-008 | `computeDaily` re-sums income, disagreeing with `computeMonthly` | S2 | ✅ **Fixed** | Statistics |
| NCC-009 | `SingleFlight` waiter always reports "nothing failed" | S2 | ✅ **Fixed** | Sync |
| NCC-010 | Notification-time change needs a process restart | S2 | ✅ **Fixed** | Reminders |
| NCC-011 | Auto-backup enable/disable needs a process restart | S2 | ✅ **Fixed** | Backup |
| NCC-012 | Lock screen is an overlay, not a gate | S2 | ✅ Fixed | Security UX |
| NCC-013 | `DashboardViewModel.errorMessage` can never be set | S2 | ✅ **Fixed** | UI |
| NCC-014 | `BorrowedViewModel.actionError` can never be set | S2 | ✅ **Fixed** | UI |
| NCC-015 | `savePatient` swallows its own validation exception | S2 | ✅ **Fixed** | UI |
| NCC-016 | `isLoading` can never clear | S2 | ✅ **Fixed** | UI |
| NCC-017 | Inventory sorts compare `"d MMM yyyy"` lexicographically | S2 | ✅ **Fixed** | Inventory |
| NCC-018 | `addStockBatch` is not atomic | S2 | ✅ **Fixed** | Inventory |
| NCC-019 | WorkManager chain grows without bound | S3 | ✅ Fixed | Sync |
| NCC-020 | Uploader conflict branch is unreachable | S3 | ✅ Fixed | Sync |
| NCC-021 | Lost-update race on every batch mutation | S3 | ✅ Fixed | Inventory |
| NCC-022 | `AuditLogger.log()` writes outside the caller's transaction | S3 | ✅ Fixed | Logging |
| NCC-023 | Pull paths never apply tombstones | S3 | ✅ Fixed (absence reconciliation shipped instead) — see note | Sync |
| NCC-024 | Realtime subscription dies permanently on first error | S3 | ✅ Fixed | Sync |
| NCC-025 | `SyncQueueDao` orders a TEXT priority column backwards | S4 | ✅ Fixed | Sync |
| NCC-026 | `isExpired` fails open on an unparseable date | S4 | ⚠️ Not a bug | Inventory |
| NCC-027 | `isLowStock` uses `<=`, flagging exactly-at-threshold stock | S4 | Open | Inventory |
| NCC-028 | `consultations.visitId` defaults to `""` — an FK-violating value | S4 | Open | Room |
| NCC-029 | `AuditLogDao` references a composite index that does not exist | S4 | ✅ Fixed | Room |
| NCC-030 | `VaccineDao` audit-count guards query the wrong column | S4 | ✅ Fixed | Inventory |
| NCC-031 | `FinanceDao` sorts on an expression no index can serve | S4 | Open | Room |
| NCC-032 | `reschedule` discards its `reminderDate` argument | S3 | ✅ Fixed | Reminders |
| NCC-033 | `notificationsSentToday` is structurally always 0 | S3 | ✅ Fixed | Reminders |
| NCC-034 | Personal reminder with a blank date notifies every run | S3 | ⚠️ Not a bug | Reminders |
| NCC-035 | Personal-reminder notification ID has a precedence bug | S4 | ✅ Fixed | Reminders |
| NCC-036 | 60 `viewModelScope.launch` bodies have no `try`/`catch` | S3 | Open | UI |
| NCC-037 | `readBytesFromUri` size check is TOCTOU | S3 | ✅ Fixed | Backup |
| NCC-038 | `inventory_deductions` is never synced to the server | S3 | ⚠️ Needs decision | Sync |
| NCC-039 | `neochild_db` opened twice as a singleton | S4 | ✅ Fixed | DI |
| NCC-040 | Latest-batch fallback in `AddBatchViewModel` picks the oldest | S2 | ✅ Fixed | Inventory |
| NCC-041 | Brand drill-down returned 0 rows for every brand — query matched JSON as comma-text | S2 | ✅ Fixed | Statistics |
| NCC-042 | Instrumented tests asserted with Kotlin `assert()`, a no-op without `-ea` — 7 assertions could never fail | S3 | ✅ Fixed | Tests |
| NCC-043 | Statistics visit-type cards gated on the wrong helper — consultation visits counted as 0 | S2 | ✅ Fixed | Statistics |
| NCC-044 | `refreshTransactions` re-imports finance rows deleted locally while their remote DELETE was in flight | S2 | ✅ Fixed | Finance |

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
the ledger is only read for the reversal cap (`:158-161`). `reconcileInventoryDeductions`
infers status through `deductionStatus` (`:47-64`), whose carry-forward branch is
`forVaccine.none { it.status == "COMPLETED" } -> "FAILED"` (`:62`).

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
deductedThisEdit, exact, previousForVaccine)` (`:47-64`) — the same pattern `inventoryDiff`
already uses in this file. `reconcileInventoryDeductions` derives
`deductedThisEdit = (delta[item.batchId] ?: 0) > 0` from the edit's own delta (`:225`), and
`deductedThisEdit -> "COMPLETED"` (`:59`) is evaluated directly after the shortfall branch.
A prior `FAILED` row can no longer mask a deduction that succeeded. `applyInventoryDiff`
returns the shortfall map (`:149`, `:204`).

⚠️ **Corrected 2026-10-19.** This paragraph previously named a `stockMoved` parameter, a
`StockDiffResult(shortfalls, deducted)` return type and a `stockMoved -> "COMPLETED"` branch.
No `StockDiffResult` type exists anywhere in the repository: the delta drives the decision,
and `applyInventoryDiff` returns `Map<String, String>` shortfalls.

Also bundled (same code path, S3): `reverseDeduction` now receives the `transactionGroupId`
the engine already computed, so the stock reversal uploads in the same sync group as the
visit edit rather than as a singleton group.

**Regression test.** `VaccinationEditEngineTest`
(`app/src/test/java/com/neochildclinic/feature/vaccination/domain/VaccinationEditEngineTest.kt`,
8 tests): the restock reproduction (`:74-87`), the shortfall branch (`:90`), and the three
carry-forward outcomes (`:103`, `:118`, `:133`); the other three pin `inventoryDiff`.
⚠️ **Corrected 2026-10-19:** the card named `VaccinationEditEngineLedgerTest`; no such class
exists anywhere in the repository.

**Verification evidence.** Reverting only the `deductedThisEdit -> "COMPLETED"` branch makes
`deduction that succeeds after a restock is COMPLETED not the inherited FAILED`
(`VaccinationEditEngineTest.kt:75`) fail; restoring it makes all 8 pass. Suite was
**260 tests, 0 failures, 0 errors, 0 skipped** (was 252). ⚠️ **Corrected 2026-10-19:** the
two test names previously quoted here do not exist in the suite, the restock test above is
the one that pins the branch, and there is no multiple-`FAILED`-rows test. 🔍 This pass did
not re-run the suite.

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
| **Files** | fixed definition `supabase/migrations/20261022_fix_stock_trigger_update_arithmetic.sql:1-100`; previous body `20261017_stock_trigger_duplicate_and_update_arithmetic.sql:54-108`; client upsert `core/sync/SyncUploader.kt:551` |
| **Impact** | A retried ledger upload invents or destroys doses server-side. |

**Evidence.** The `ELSE` branch applies `- OLD.quantity` with no `+ NEW.quantity`, and only
ever debits `OLD.batch_id`. The trigger now fires `after insert or **update** or delete`, and
the client uploads transaction rows with `upsert` (`core/sync/SyncUploader.kt:646`, dispatched
at `:551`; the row's Postgres id is its UUID, `data/local/entity/VaccineEntity.kt:80`).
`SyncUploader.kt:477` explicitly acknowledges that retries after a lost response happen.

- `VACCINATION` row `quantity = -2` → `-(-2)` = **+2 doses invented**
- `PURCHASE` row `quantity = +10` → **10 doses destroyed**

**Fix.** `- OLD.quantity + NEW.quantity`, and credit `NEW.batch_id` when it changes.
**Regression test.** `pgTAP` or a psql script: insert a `PURCHASE` row, re-upsert the same
id with a changed quantity, assert `remaining_quantity` reflects only the delta.

**Verification.** ❌ **Re-verified 2026-10-19: the retry path still reproduces.** `20261017` is
the only `create or replace function public.update_batch_stock()` in the repository, and
`20261018_ensure_stock_trigger_installed.sql` installs the trigger without redefining the body.
The rewrite corrected the apply step but added an **undo** whose guard fires on the wrong
condition:

- `v_delta := new.quantity - old.quantity` (`:71`)
- undo when `v_delta <> old.quantity` (`:80`) — true for an idempotent retry
  (`0 <> old.quantity`, and a ledger quantity is never 0), so it subtracts `OLD.quantity`
- apply `remaining_quantity + v_delta` (`:101`)

Net effect of any UPDATE is `new - 2 * old`, i.e. `-Q` for a retry of a row of quantity `Q`:
a retried `VACCINATION` upsert (−2) still invents +2 doses and a retried `PURCHASE` (+10)
still destroys 10 — the two examples in the migration's own comment (`:52-53`). The `INSERT`
and `DELETE` branches are correct; only `UPDATE` is wrong. The correct implementation is the
one in *Fix* above — undo `OLD.quantity` whenever the row is updated, then apply
`NEW.quantity` to `NEW.batch_id` — not `v_delta` on top of the undo.

✅ **Resolution (2026-10-25).** `supabase/migrations/20261022_fix_stock_trigger_update_arithmetic.sql`
replaces the function: it reverses `OLD` and applies `NEW` to `NEW.batch_id` when the batch
changes, and the same-batch path applies only the quantity delta. `supabase/tests/stock_trigger_arithmetic.sql`
exercises retry, edit, batch reassignment, and negative-stock rejection against a real
PostgreSQL 16 instance in `.github/workflows/supabase-sql.yml`; it passes. Production migration
state is still covered by NCC-AUDIT-003, not by this source-level fix. [[Hypotheses]] HYP-005
covers the unrelated client-side question the same migration left behind.

⚠️ **Status history.** This card read **In review** from 2026-10-18, then ✅ Fixed when
`origin/main` fast-forwarded in the migration. The 2026-10-19 pass re-read the SQL and found
the guard wrong, so it was open again with the arithmetic above as evidence; the
2026-10-25 resolution above closes it with a real-Postgres regression test.

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

**Fix.** `StockDeductionBlockedException` (new type, `feature/inventory/domain/StockDeductionBlockedException.kt:30`) is thrown from both sites — `InventoryRepositoryImpl.kt:462` ("Batch not found") and `:472` (expired) — and caught in the same branch (`ClinicalVaccinationService.kt:152`, also `VaccinationEditEngine.kt:183`). The visit and its items save; the ledger row is recorded `FAILED` for staff to reconcile.
**Regression test.** `ClinicalVaccinationService` with a `deductStockFromBatch` stub that throws `StockDeductionBlockedException`; assert the visit **is** saved and the ledger row is `FAILED` — the original defect was that one bad batch line rolled the whole visit back, so "visit not saved" was the bug, not the contract.

**Verification.** ✅ Source-verified 2026-10-19: the throws (`InventoryRepositoryImpl.kt:462`, `:472`), the catch (`ClinicalVaccinationService.kt:152`) and the FAILED-ledger path are present; **no regression test** (`🔍`).

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

⚠️ **Correction (2026-10-18; re-counted 2026-10-19).** This card previously gave three
mutually inconsistent figures — index "9 queries", heading "5 live reminder queries",
evidence "7 of 19", and fix "18 predicates in total". Measured against `DueReminderDao.kt`:
**21 `@Query`, 16 `SELECT`, all 16 now filtered.** The file contains **16** `is_deleted = 0`
predicates; the text appears 18 times, but two are comments (`:11`, `:89`). Repo-wide the
DAO layer holds **17** (16 here + `BackupDao.kt:124`). ⚠️ **2026-10-19:** the earlier
figure of 18 predicates in the file counted those two comments, and the claim that
`VaccineDetailViewModel.kt` added a 20th predicate outside the DAOs was false — `:59` there
is a comment.

**Live reachability:** `getRemindersByVisitId` feeds `@Relation`, and **`@Relation` cannot
filter**. `getReminderById` has 13 call sites. `BackupDao.getAllRemindersForBackup` exports
them into the `.nccb` payload.

**Note.** Nothing in `app/src/main` writes `is_deleted = 1` today — only a pre-v31 client
could create the rows. The inconsistency is latent, not active.

**Fix applied.** Every lookup now filters, plus the paginated variants (dead code, but
wiring them later unfiltered is the same trap) and `BackupDao.getAllRemindersForBackup` —
17 `is_deleted = 0` predicates across the DAO layer. The file's header comment asserted the
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
| **Status** | ✅ **Fixed** · **Severity** S2 High |
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

**NCC-023 fix — 2026-10-20.** The reconciliation this note demanded was implemented, once, in a
shared helper and applied to every pull path: `core/sync/RemotePrune.kt:29-41` deletes local rows
that are synced, absent from the freshly-pulled remote id set, and unclaimed by the queue
(`:45-59` does the same for reminders whose local PK is device-local and whose remote PK lives in
`serverId`). Safety against the "partial or failed remote fetch" blast radius: the prune runs only
after the pull's `decodeList` has completed without exception, so a failed fetch never reaches it;
the queue guard keeps every row a local write is about to push. Unit-pinned (`RemotePruneTest`, 12
tests) and instrument-pinned in CI (`RemotePruneDaoTest` — projection semantics, patient-cascade
delete, reminder identity mapping). 🔍 Live two-device divergence drill still open as a staged
rollout step.

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

**Verification evidence (2026-10-19).** `20261018_ensure_stock_trigger_installed.sql` is on
this branch: it installs `inventory_transactions_update_batch_stock` when the function and
table exist but the trigger does not (`:20-39`), and `raise exception`s if it still cannot
(`:43-68`). The migration was statically checked only (delimiter balance, structure).
🔍 **Not verified against a live database** — the original reproduction (a batch created at
zero stock) cannot be re-run from the repository, so this closure rests on the migration's
install-and-raise structure and is listed under "Needs a live DB" in the evidence table
above. Run the query before shipping.

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
| **Status** | ✅ **Fixed** · **Severity** S2 High |
| **Files** | `app/MainActivity.kt:114-128`; `core/ui/LockScreen.kt`; `core/security/BiometricLockManager.kt:8`; `app/NeoChildApp.kt:93-98` |
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

**Verification evidence (2026-10-19).** Re-read at HEAD: `AppNavigation` is composed only
inside `if (lockStateResolved && !isLocked)` (`app/MainActivity.kt:114-116`), the unresolved
state renders a progress indicator instead of patient data (`:121-128`), and
`checkAppLock()` marks the state resolved in a `finally` on every path (`:246-251`).
`BiometricLockManagerTest` pins the four resolved-state rules (`:74`, `:83`, `:96`, `:103`);
🔍 not executed in this pass.

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
not on a test that fails without the fix — narrower evidence than [[#NCC-001 — Edit raising quantity on a failed batch moves stock but records `FAILED`|NCC-001]] and
[[#NCC-009 — `SingleFlight` waiter always reports "nothing failed"|NCC-009]] carry. Do not read "Fixed" here as "regression-protected".

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
| NCC-019 | S3 | ✅ **Fixed** | `APPEND_OR_REPLACE` + per-row scheduling ⇒ unbounded work chain | `SyncManagerImpl.kt:39`, `:61` — now `ExistingWorkPolicy.REPLACE`; `:32-36` is the comment recording the switch. This is what refuted [[Quality/Hypotheses]] HYP-002. |
| NCC-020 | S3 | ✅ **Fixed** | Uploader conflict branch unreachable; `fetchRemoteConflictData` read then ignored | `SyncUploader.kt`; new `SyncQueueDao.isUnsyncedExcluding` |
| NCC-021 | S3 | ✅ **Fixed** | Lost-update race: read-then-write `@Update`, no `@Version`, no transaction | `InventoryRepositoryImpl.kt` — `deductStockFromBatch` and `reverseDeduction` now transactional |
| NCC-022 | S3 | ✅ **Fixed** | `AuditLogger.log()` fired on a separate coroutine inside 5 live transactions | `WasteRepositoryImpl.kt:64,102`; `MergePatientsUseCase.kt:47`; `BorrowRepositoryImpl.kt:185,287` — all now call `recordLog` |
| NCC-023 | S3 | ✅ **Fixed** | Absence reconciliation shipped for every pull path (no tombstones exist; the remote id set is the authoritative absence signal) | shared helper `core/sync/RemotePrune.kt:25-59` (`computeBatches`, `computeRemindersBatches` for the reminders `serverId` identity), wired at `PatientRepositoryImpl.kt:152-170` and all 13 refresh sites; unit-pinned by `RemotePruneTest` (12) and CI instrumented by `RemotePruneDaoTest`; commit `f28ce21` |
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
| NCC-025 | S4 | ✅ **Fixed** | SQL no longer orders by the TEXT `priority` column; `orderPendingIntoGroups` applies the entity-name weight after the query | `SyncQueueDao.kt:12-14` |
| NCC-026 | S4 | ⚠️ Not a bug | `isExpired` returns false on an unparseable date — deliberate, see below | `InventoryUtils.kt:24` — comment added; no behaviour change |
| NCC-027 | S4 | Open | `isLowStock = totalStock <= globalThreshold` flags at-threshold stock | `InventoryRepositoryImpl.kt:78` (was `:77`) |
| NCC-028 | S4 | Open | `consultations.visitId` defaults to `""` against a NOT NULL CASCADE FK | `ConsultationEntity.kt:33` — ✅ re-verified 2026-10-18 |
| NCC-029 | S4 | ✅ **Fixed** | Comment corrected: the entity has separate `entityType` and `entityId` indexes, not a composite | `AuditLogDao.kt:47-50`; `AuditLogEntity.kt:18-25` |
| NCC-030 | S4 | ✅ **Fixed** | Both dead-and-wrong audit-count guards were deleted in `f28ce21`; zero references remain | `VaccineDao.kt` (methods removed) |
| NCC-031 | S4 | Open | `FinanceDao` orders by `COALESCE(...)` — unindexable, so its indices are dead | `FinanceDao.kt:15,18,36` — ✅ re-verified 2026-10-18 |

### NCC-026 — reclassified, not fixed

The card read `?: return false` as an oversight. It is not: it is the defensible choice, and
failing closed would be worse.

`isExpired == true` is not a label on the batch — `InventoryRepositoryImpl:73` uses it to build
`activeBatches`, the list of doses a clinician can find and use. Returning `true` for a date nobody
can read would **drop real stock out of that list**, so someone holding a usable dose would not
find it and could conclude the clinic has none. Returning `false` keeps it visible, with the raw
expiry string shown next to it. Both directions assert something unknown; the difference is that
one hides stock and the other does not.

The codebase already decided this once, explicitly — `BorrowBatchSelection:38`: *"An unparseable
expiry date sorts last instead of first so it is only used when nothing better exists."* That is
the same stance: usable, but deprioritised. `isExpired` was simply the undocumented half of it.

**Not reachable by any app path.** The expiry field is a `DateDropdownPicker`, which writes
`date.format(Constants.DATE_FORMAT)` — `"d MMM yyyy"` — and `DateUtils.parseDate` lists
`Constants.DATE_FORMAT` among its accepted formats, so picker and parser agree. Both write paths
reject a blank (`AddBatchScreen:155`, `AddStockViewModel:173`), and a batch's expiry *is* editable
afterwards (`AddBatchViewModel:98-107`), so a bad value would be repairable even if one appeared.
A remote row cannot supply one quietly either: `VaccineBatchEntity.expiryDate` is a non-nullable
`String`, so a null `expiry_date` fails deserialization loudly rather than arriving as `""`.

**What was changed.** A comment on `isExpired` recording the stance and why, so the next reader
does not "fix" it into a stock-hiding bug. No behaviour change, so no test — there is nothing new
to be wrong. The upgrade path, if it is ever hit for real, is to surface *expiry unknown* in the
UI rather than to pick a direction.

**Separately noted, unverified.** The picker's `"d MMM yyyy"` is written straight through
`@SerialName("expiry_date")` with no ISO conversion, so that is the literal text sent to Postgres.
If that column is a `date` rather than `text`, inserts would fail — the table's DDL is not in the
migrations, so this needs the live database to settle. Grouped with the other unexecuted-migration
checks in [[Release Checklist]].

**Links.** [[Room Database]] · [[Inventory Management]]

---

## NCC-032 to NCC-040 — remaining

| ID | Sev | Status | Summary | Location |
|---|---|---|---|---|
| NCC-032 | S3 | ✅ **Fixed** | `reschedule` accepted `reminderDate` and discarded it — deleted the dead chain | `ReminderDialogs.kt` (picker removed), `DueTab.kt`, `DueViewModel.kt`, `ReminderRepository.kt`, `ReminderRepositoryImpl.kt`, `ReminderEntity.kt`, `Reminder.kt` |
| NCC-033 | S3 | ✅ **Fixed** | `notificationsSentToday` read two `@Transient` fields with no writer ⇒ always 0 — deleted the dead chain | `ReminderDueListProcessor.kt`, `ReminderStats.kt`, `ReminderEntity.kt`, `Reminder.kt` |
| NCC-034 | S3 | ⚠️ **Not a bug** | Blank `reminderDate` notifies on every run — this is the stated design | `PersonalReminderNotificationWorker.kt:27-31`; intent quoted from `NotificationHelper.kt:171` |
| NCC-035 | S4 | ✅ **Fixed** | `2000 + (hash and 0x7fffffff) % 100000` parses as `2000 + (hash and 83647)` | `NotificationHelper.kt:187` — now `PERSONAL_REMINDER_BASE_ID + ((id.hashCode() and 0x7fffffff) % 100000)`; comment at `:185` records the old precedence bug |
| NCC-036 | S3 | Open | `viewModelScope.launch` bodies with no `try`/`catch` | **140** launches across **33** `*ViewModel.kt` files. ⚠️ The card said "60 … 27 ViewModels"; re-measured 2026-10-18, file count corrected 2026-10-19. The exact count of *unprotected* bodies was not recomputed here — treat the number as an upper bound. |
| NCC-037 | S3 | ✅ **Fixed** | `readBytesFromUri` checked `OpenableColumns.SIZE` then read unbounded | `BackupRepositoryImpl.kt` — read is now bounded at `MAX_BACKUP_BYTES` |
| NCC-038 | S3 | ⚠️ **Needs decision** | `inventory_deductions` is local-only — but this is a missing feature, not a defect, and the impact is narrower than stated | no server table exists in any migration, nothing enqueues `INVENTORY_DEDUCTION`, no descriptor |
| NCC-039 | S4 | ✅ **Fixed** | Removed the redundant Hilt `@Singleton`; `AppDatabase.getDatabase()` is the single owner | `AppDatabase.kt:442-444`; `DatabaseModule.kt:32-37` |
| NCC-040 | S2 | ✅ **Fixed** | `AddBatchViewModel` `maxByOrNull { it.purchaseDate }` — same lexicographic defect as NCC-017 | `AddBatchViewModel.kt:59-60` (was cited `:56`) — now `newestBatch()` with a `parseDate` fallback |
| NCC-041 | S2 | ✅ **Fixed** | `getUpcomingVaccinationsByVaccineId` matched `nxt_vaccine_id` as comma-joined text; the column is JSON, so it returned 0 rows for every brand | `DueReminderDao.kt` — quoted-id `instr()` match; `ReminderEntity.kt` comment corrected |

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

**Fix.** Match the JSON text exactly — the needle is the id wrapped in its own quotes:

```sql
instr(COALESCE(nxt_vaccine_id, ''), '"' || :vaccineId || '"') > 0
```

`'"vac-a"'` is found inside `["vac-a"]` and inside `["vac-b","vac-a"]`, but never inside
`["vac-a2"]` — the closing quote has to be there. So the match is exact without needing
delimiters, and it tolerates whitespace or escaping variations that a delimiter scheme would not.

`instr()` rather than `LIKE` because the id becomes part of the pattern: a `%` or `_` in an id would
be a wildcard under `LIKE`, but `instr()` has no pattern semantics at all.

`NULL` needs no special case (`instr(NULL, x)` is `NULL`, and `NULL > 0` is not true), and a row
still holding the old comma format simply fails to match rather than raising.

> **Room cannot use `json_each` here.** The first version of this fix used SQLite's JSON1
> table-valued function, which is the more literal expression of the intent and is *valid SQL* —
> it passed Room's compile-time verification and behaved correctly under `sqlite3` 3.45.1. It then
> failed in `androidTest` with `IllegalArgumentException: There is no table with name json_each`.
> `TriggerBasedInvalidationTracker.validateTableNames` rejects it: Room parses the identifiers in
> the `FROM` clause to learn which tables a `Flow` query must observe, treats `json_each` as a
> table name, and cannot resolve it against the schema. A table-valued function in a `Flow`-returning
> query is therefore unusable, and only a real device run reveals it — compilation does not.
> The generated code for the final query registers exactly `arrayOf("reminders")`.

**Related, not fixed.** `VaccineDao.getVaccinationCountForVaccine` matches
`patient_visits.vaccine_ids` with `LIKE '%' || :vaccineId || '%'`, which is a substring match. That
column genuinely *is* comma-joined (`VaccinationEntity:115`, and
`20260822_patient_visit_backup_and_timestamps.sql` splits it with `string_to_array`), so unlike the
query above the shape is right — it is only imprecise. Vaccine ids are `uuid`
(`vaccine_id uuid references public.vaccines(id)`), so one cannot realistically be a substring of
another. It over-counts only if that ever changes, and the effect would be a delete guard that is
too strict — not data loss. Left alone deliberately.

**Verification.** Executed against SQLite 3.45.1 with fixtures mirroring the instrumented tests:

| Probe | Result |
|---|---|
| `vac-a` with `["vac-a"]`, `["vac-a","vac-b"]`, `["vac-b","vac-a"]` | matched — including middle position |
| `vac-a` must not match `["vac-a2"]` | not matched |
| `vac_ax` probed as `vac_a` | **not** matched — `_` stayed literal |
| `NULL`, `[]`, soft-deleted, `status = 'COMPLETED'` | excluded |
| a malformed comma-format row present | query did not raise; row excluded |
| **the old formula, same data** | matched **only** the malformed row — and nothing JSON-shaped |

The `vac_ax` probe is the case that rules out `LIKE`: with a pattern it would have matched as a
wildcard.

**Regression test.** `UpcomingVaccinationDaoTest` and `ReminderSoftDeleteFilterTest` — 28 cases in
total (16 + 12), already written, red until this fix. They had never executed before PR #17; they are the
check. Local confirmation is limited to the compile plus the observed-table array in the generated
`DueReminderDao_Impl`, because the failure mode above only appears on a real device.

**Links.** [[Statistics Dashboard]] · [[Database Schema]]

## NCC-042 — seven instrumented assertions that could not fail

| | |
|---|---|
| **Status** | ✅ Fixed 2026-10-18 · **Severity** S3 Medium |
| **Files** | `androidTest/.../DateGivenIndexMigrationTest.kt`; `androidTest/.../ExpenseMigrationTest.kt`; `.github/workflows/android-instrumented.yml` |
| **Found by** | reading the `androidTest` log of a *green* build |

**Evidence.** `DateGivenIndexMigrationTest` has two `@Test` methods that run on every push and used
Kotlin's `assert(condition) { "message" }` — seven assertions in total, including the two the whole
class exists for:

```kotlin
assert(after.any { it.contains("index_patient_visits_dateGiven") }) { "got $after" }
assert(after.none { it.contains("TEMP B-TREE") }) { "still sorting: $after" }
```

Kotlin compiles `assert` against `_Assertions.ENABLED`, which is
`javaClass.desiredAssertionStatus()` — **false** unless the JVM is started with `-ea`. An
instrumentation process is started by `am instrument` and does not pass `-ea`, and Android cannot
be made to: there is no `testOptions` flag for it. Every one of those assertions was therefore a
no-op, and the class reported **green unconditionally**. It was the only thing verifying that
`migration32_33` actually removes the temp B-tree sort it was written to remove; had the index
silently stopped being used, CI would have said nothing.

The trigger for finding this was a *false alarm in the other direction*: the instrumented job for
NCC-041 was green (`BUILD SUCCESSFUL`, Gradle exited 0) while `.github/scripts/androidtest-failures.py`
printed `FAILED: 1 instrumented test(s)` for the `@Ignore`d
`ExpenseMigrationTest.migrate22To23_createsExpensesTable_withoutTouchingExistingData`.

**Fix.** JUnit's real assertions, which have no such flag:
`assertTrue("got $after", ...)`, `assertEquals("got $count", 3, count)`. The two plan assertions were
first checked against SQLite 3.45.1 on the test's own 3-row fixture to confirm they *pass* once
armed — `SCAN patient_visits` + `USE TEMP B-TREE FOR ORDER BY` before the index,
`SCAN patient_visits USING INDEX index_patient_visits_dateGiven` after — so arming them does not
turn a green test red.

**Guard.** `android-instrumented.yml` now rejects any `(^|[^A-Za-z_.])assert\(` in `androidTest`
before the emulator boots, so a no-op assertion cannot come back. Verified by inserting a probe
file, confirming the grep caught it, and removing it. A bare `assert` there is never correct, so
the rule needs no exceptions.

**Also changed, same investigation.** The failure reporter now prints each `<testsuite>`'s own
`tests/failures/errors/skipped` counters alongside its per-case scan, and the report upload is
`if: always()` instead of `if: failure()`. Disassembling
`com.android.ddmlib.testrunner.XmlTestRunListener` shows it writes `<failure>` only for
`TestStatus.FAILURE` and `<skipped/>` for an ignored test — so the `@Ignore`d case above should
not have produced a `<failure>` node at all, and the reason is not yet established. Gradle's own
exit code, re-raised by both steps, is what determines the build; the reporter runs afterwards and
never changes it. The next run's counters will settle which of the two disagrees, rather than
guessing now.

**Links.** [[Room Database]] · [[Documentation Maintenance]]

## NCC-043 — Statistics visit-type cards gated on the wrong helper — consultation visits counted as 0

| | |
|---|---|
| **Status** | ✅ Fixed 2026-10-24 · **Severity** S2 High |
| **Files** | `feature/statistics/domain/StatisticsUtils.kt:168-173` (new); `feature/statistics/presentation/OverviewTab.kt:52`, `:82-87`, `:109-111`; `feature/statistics/presentation/FullReportViewModel.kt:159-172`, `:256-269`; `feature/statistics/presentation/VaccinationsTab.kt` (comments only) |
| **Introduced by** | commit `8b3c415` ("exclude cancelled and dismissed visits from all statistics filters") |

**Impact.** The Overview "Consulted Patients" and "Total Consultation" cards and the
consultation series in the Patient Activity chart were **permanently 0**. The same
over-gating made `computeEffectiveRegistrationDates` receive only valid vaccinations in
`OverviewTab`/`FullReportViewModel`, so a consultation-first patient got a later effective
registration date than on the Patients tab (which passes raw visits). In Full Report the
monthly and daily consultation series were also permanently 0.

**Evidence.** Consultation visits are persisted with `ReminderStatus.ACTIVE` — they have no
completion state — so `isCountedVaccination` (`StatisticsUtils.kt:43-44`) returns false for
**100%** of them. Commit `8b3c415` wrapped the Overview visit-type input in
`StatisticsUtils.filterValidVaccinations(...)` before `StatisticsUtils.visitTypeStats(...)`,
which silently excluded every consultation from the count.

**Root cause.** The correct-by-design rule in `visitTypeStats` (`StatisticsUtils.kt:144-149`
— deliberately *not* status-gated) was undermined at the call site: the caller pre-filtered
its input with the vaccination-status gate before calling it, so the function's own
ungated loop never saw a consultation visit.

**Fix applied.** New `StatisticsUtils.visitTypeStatsForFilter(visits, filterMode,
fyQuarter, selectedMonth)` (`StatisticsUtils.kt:168-173`) applies only the date filter
(`isDateInFilter`), never the status gate. `OverviewTab.kt:82-87` now calls it and passes
raw visits to `computeEffectiveRegistrationDates` (`:52`) and the consultation chart series
(`:109-111`); `FullReportViewModel.kt` threads a raw `allVisits` parameter through
`computeMonthly` (`:162`, `:172`, `:204`) and `computeDaily` (`:259`, `:269`, `:298`) for
the same two purposes. Vaccination-type metrics remain status-gated
(`filterValidVaccinations`, `OverviewTab.kt:60`, `FullReportViewModel.kt` `validVaccinations`)
— a pending vaccination visit must not count as administered. Also corrected two misleading
comments in `VaccinationsTab.kt` that claimed doses were "never gated on the parent visit's
status" while `administeredDoses` does gate (documentation-only, no behaviour change).

**Regression test.** `VisitTypeStatsTest.kt`
(`app/src/test/java/com/neochildclinic/feature/statistics/domain/VisitTypeStatsTest.kt`) —
two new cases: `visitTypeStatsForFilter counts ACTIVE consultation visits instead of
filtering them out` (`:61-82`) and `visitTypeStatsForFilter applies the date window before
counting` (`:85-95`). The first also asserts the pre-fix composition
`visitTypeStats(filterValidVaccinations(visits))` returns 0 consultations (`:80-81`), so
the test provably bites on the original code.

**Verification evidence.** Reported by the change author 2026-10-24:
`./gradlew app:testDebugUnitTest` → **312 tests, 0 failures** (was 310); the two new tests
above pin the fix, and the first fails without it by construction (it asserts the old
composition's 0 directly). `./gradlew app:assembleDebug` → **BUILD SUCCESSFUL**.
🔍 The docs pass did not re-run either Gradle command — the suite result is the author's
run, not an independent one here.

**Links.** [[Statistics Dashboard]] · [[Testing]] · [[Quality/Audit Reconciliation]]

---

## NCC-044 — `refreshTransactions` re-imported finance rows deleted locally while their remote DELETE was in flight

| | |
|---|---|
| **Status** | ✅ Fixed 2026-10-24 · **Severity** S2 High |
| **Files** | `feature/finance/data/FinanceRepositoryImpl.kt:417-421` (guard call site), `:461-465` (`financeImportAllowed`, new); cascades at `feature/vaccination/data/VaccinationRepositoryImpl.kt:488-500`, `feature/consultation/data/ConsultationRepositoryImpl.kt:170-182`, `feature/finance/data/ExpenseRepositoryImpl.kt:97-114` |

**Impact.** Income deleted on this device came back from the next pull and stayed in
**Statistics → Finance** and **Monthly Finance Details** — both read
`FinanceDao.getAllTransactions()` (`data/local/dao/FinanceDao.kt:19`, consumed at
`feature/statistics/presentation/StatisticsViewModel.kt:62` and
`feature/statistics/presentation/FinanceDetailsViewModel.kt:30`). The resurrected row also
became *un-prunable*: `RemotePrune.computeBatches` skips ids still claimed by the queue
(`core/sync/RemotePrune.kt:36-38`), so as long as the FINANCE DELETE stayed pending the row
could not be removed by absence reconciliation either.

**Reproduction.**

1. Delete a visit or consultation (or an expense). The finance rows are hard-deleted locally
   and a FINANCE `DELETE` is enqueued
   (`VaccinationRepositoryImpl.kt:488-500`, `ConsultationRepositoryImpl.kt:170-182`,
   `ExpenseRepositoryImpl.kt:97-114`).
2. Before `SyncWorker` uploads it (a one-time WorkManager request,
   `core/sync/SyncManagerImpl.kt:22`) — a race the user typically loses by opening
   Statistics right after the delete — a pull runs.
3. The pull sees `local == null` for the deleted id, short-circuits the pending check, and
   re-inserts the row as `isSynced = true`.
4. The deleted income is visible again in Statistics, and stays visible while the DELETE is
   queued.

**Root cause.** The import guard in `refreshTransactions` was
`local == null || (local.isSynced && id !in pendingFinanceIds)`: the pending-set check sat
*inside* the `local != null` arm only, so the `local == null` case (a row we just
hard-deleted) never consulted the queue and re-imported the row the DELETE was about to
remove.

**Fix applied.** Extracted `financeImportAllowed(local, remoteId, pendingIds)`
(`FinanceRepositoryImpl.kt:461-465`) — the pending set now gates the `local == null` case
too (`remoteId !in pendingIds && (local == null || local.isSynced)`), contract documented in
its KDoc (`:453-460`). This mirrors `todoUpsertsForRefresh`
(`feature/patient/data/PatientTodoRepositoryImpl.kt:135-143`), which already had the pending
set as an outer gate. The heal-override branch (`local != null && wasHealed`,
`:424-427`) is unchanged. **Finance was the only pull path with this shape** — every sibling
gates the insert on the pending set unconditionally: patients
(`PatientRepositoryImpl.kt:135`), visits (`VaccinationRepositoryImpl.kt:126`),
consultations (`ConsultationRepositoryImpl.kt:211`), expenses
(`ExpenseRepositoryImpl.kt:125`), todos (`PatientTodoRepositoryImpl.kt:141`), profiles
(`ProfileRepositoryImpl.kt:75`), reminders (`ReminderRepositoryImpl.kt:493-498`).

**Regression test.** `FinanceRefreshImportTest.kt`
(`app/src/test/java/com/neochildclinic/feature/finance/data/FinanceRefreshImportTest.kt`) —
5 cases against `financeImportAllowed`, including the exact defect: `row pending a remote
DELETE is not re-imported after the local delete` (`:26-29`) asserts `local == null` +
pending id ⇒ not imported.

**Verification evidence.** Reported by the change author 2026-10-24:
`./gradlew app:testDebugUnitTest` → **317 tests, 0 failures** (was 312);
`./gradlew app:assembleDebug` → **BUILD SUCCESSFUL**. 🔍 The docs pass did not re-run either
Gradle command — the suite result is the author's run, not an independent one here.

⚠️ **Suspected, NOT fixed — do not read this card as closing it.** Income rows created by
legacy builds with a blank/null `visitId` would escape the visit cascade entirely: the
cascade selects `WHERE visitId = :id` (`FinanceDao.kt:21-22`) and
`ConsultationRepositoryImpl.kt:171` guards its whole finance-delete block on
`existing.visitId.isNotBlank()`, so such a row matches neither. Current code regenerates the
visit id when it is blank (`ClinicalVaccinationService.kt:199`), so no *new* rows can look
like this — only pre-existing data could, and that is unverified without a live database.

**Links.** [[Offline Synchronization]] · [[Statistics Dashboard]] · [[Testing]]

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
