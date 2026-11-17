# Audit Reconciliation

> Phase 1 audit: cross-check of `docs/AUDIT_REPORT_2026-10-16.md` against the source as it
> stands at commit `f8e5d8a`. This page records what the earlier audit claimed, what
> re-verification found, and the status of each claim.

## Method

Every claim in the audit's **Phase 6 implementation record** was re-verified against the
current source. Findings were not taken on trust. Where a claim could not be settled
without a live database, it is marked 🔍 rather than counted either way.

## Headline

⚠️ **The audit's "Fixed" table overclaimed. Four entries were partial fixes: the mechanism
was corrected, but a neighbouring code path still produced the original symptom.**

This is not a criticism of the fixes — each one improved a real problem. It is a warning
that the *claims* are stronger than the evidence, which matters because closing a finding
removes it from anyone's follow-up list.

✅ **Update 2026-10-19: all four are now closed.** Each neighbouring path named below has
since been fixed and re-verified against the current source — see *2026-10-19 verification
pass* at the end of this page. The original verdict text is preserved.

| Audit claim | Re-verified state | Verdict |
|---|---|---|
| #14 `SingleFlight` waiter reported success | `SingleFlight.run` **does** return the owner's result, and the caller now uses it: `SyncRepositoryImpl.kt:103-104` is `= flight.run { processNextItemsInternal() }` | ✅ **Confirmed fixed** (2026-10-19) |
| #15 two screens showed different revenue | `computeMonthly` **does** use `effectiveIncome`, and so does `computeDaily` now (`FullReportViewModel.kt:298`) | ✅ **Confirmed fixed** (2026-10-19) |
| #38 due-list counts ignored `is_deleted` | All 16 `SELECT`s in `DueReminderDao` now filter `is_deleted = 0` (plus `BackupDao.kt:124`); the other 5 `@Query`s are 3 `DELETE` + 2 `UPDATE`, correctly unfiltered | ✅ **Confirmed fixed** (2026-10-19) |
| #2 the "insufficient stock" catch was too wide | Every pre-write rejection is now `StockDeductionBlockedException` or its `InsufficientStockException` subtype (`InventoryRepositoryImpl.kt:462`, `:472`, `:477`) and is caught (`ClinicalVaccinationService.kt:152`) | ✅ **Confirmed fixed** (2026-10-19) |
| #4 editing discarded the whole clinical edit | `applyInventoryDiff` **does** capture shortfalls and complete the edit | ✅ **Confirmed fixed** |
| #3 deleting a patient restored every dose | `deletePatient` **does** clear deductions first | ✅ **Confirmed fixed** |
| #1 borrow across batches returned doses to the wrong batch | `BorrowBatchSelection.selectBorrowBatch` exists and refuses to span batches | ✅ **Confirmed fixed** |
| #19 18 of 22 migrations were git-ignored | 19 migration files are visible to git and now tracked | ✅ **Confirmed fixed** |
| #25 negative-stock trigger never installed | `20261016` installs it under the function-existence condition | ✅ **Confirmed fixed** |
| #27 14 read paths swallowed 401 | `CloudRefresh` rethrows session failures | ✅ **Confirmed fixed** |
| #37 `nextDueDate` compared dates as text | Compares parsed dates; 6 tests | ✅ **Confirmed fixed** |
| #36 due dates shifted east of IST | `parseDate` anchors to IST; 10 tests | ✅ **Confirmed fixed** |

## The four partial fixes, in detail

✅ **Banner (2026-10-19): all four entries below are now closed** — see *2026-10-19
verification pass* at the end of this page. The detail is preserved as written at the time
of the original pass.

### #14 — `SingleFlight`

The fix is real. `core/sync/SingleFlight.kt` now completes a shared
`CompletableDeferred` and returns the owner's value, so `RefreshDataUseCase.kt:49`'s
`= flight.run { runRefresh() }` works correctly.

But `core/sync/SyncRepositoryImpl.kt:103-107` was never converted to use the return value:

```kotlin
override suspend fun processNextItems(): Boolean {
    var anyFailed = false
    flight.run { anyFailed = processNextItemsInternal() }   // waiter's run() returns, never assigns
    return anyFailed                                        // waiter's answer: always false
}
```

A **waiter** takes `SyncWorker.kt:37-40`'s success branch → resets `failure_count` →
`Result.success()` → the "Some changes could not be synced" alert is cancelled.

`SingleFlightResultTest` exercises `run()`'s **return value**, not this wrapper — which is
why it passes. The audit's own note "watched fail" applied to `run()`, not the call site.

### #15 — `effectiveIncome`

`FinanceCalculator.kt:307-318` states the invariant: *"Every screen that sums income must go
through here — do not re-implement the sum."*

| Line | Uses `effectiveIncome`? |
|---|---|
| `FullReportViewModel.kt:198` (`computeMonthly`) | ✅ |
| `FullReportViewModel.kt:282` (`computeDaily`) | ❌ raw sum |

Both are in the **same file**. Drilling Full Report into a single month still over-reports
revenue, cash, online and net profit for any visit with two `VACCINATION` income rows.

### #38 — `is_deleted`

The audit says "4 DAO queries now filter `is_deleted = 0`". Current state of
`DueReminderDao` — 19 queries, 7 filtered, 14 not (including 6 DELETE/UPDATE statements,
which are correctly not filtered).

The 5 that matter, all reachable:

| Query | Reached by |
|---|---|
| `getRemindersByVisitId` `:39` | `PatientVaccinationCardEntity` via `@Relation` — **cannot filter** |
| `getReminderById` `:42` | 13 call sites |
| `getReminderByUniqueEvent` `:45` | save-path dedupe |
| `getReminderByStableId` `:48` | cloud-pull conflict guard |
| `getDueRemindersForPatient` `:109` | `deletePatient` |

The 3 `get*RemindersPage` queries are dead code today.

### #2 — the narrow catch

The fix introduced `InsufficientStockException` and narrowed the catch
(`ClinicalVaccinationService.kt:118`, `:120`). Correct.

But `InventoryRepositoryImpl.deductStockFromBatch` still throws **two plain
`IllegalStateException`s** that are not that type:

```kotlin
:435  ?: throw IllegalStateException("Batch not found")
:445  throw IllegalStateException("Cannot deduct stock from a batch that had already expired …")
```

Both propagate out of `transactionRunner.run` and roll back the whole visit — the exact
outcome `InsufficientStockException.kt:7-11` says must not happen.

## Claims that remain open, not fixed

These were in the audit body but **not** in the Phase 6 "Fixed" or "Deliberately not done"
tables, so they are unclaimed in both directions:

| Audit # | Finding | Current state |
|---|---|---|
| 5 | `doseSchedule` never read | ✅ Still open. Listed as "deliberately not done" — a product decision |
| 35 | statistics paging | ✅ Still open. Deliberately not done |
| 30 | sync queue state-machine tests | ✅ Still open. Deliberately not done |

And these were **not in the original audit at all** — found during the knowledge-base pass:

| Finding | Area |
|---|---|
| Stock ledger records `FAILED` while a dose leaves the shelf | `VaccinationEditEngine` |
| Postgres stock trigger's `UPDATE` branch corrupts stock on a retried upsert | ✅ Fixed by `20261022_fix_stock_trigger_update_arithmetic.sql`; NCC-002 is test-pinned by `supabase/tests/stock_trigger_arithmetic.sql` |
| Legacy `tr_update_batch_stock` never dropped → possible double-application | ✅ resolved by `20261017:26` (NCC-003) |
| A reminder-loop failure after commit creates a duplicate visit on retry | `AddVaccinationViewModel:687-698` |
| `inventory_deductions` has no sync descriptor — the dose ledger never reaches the server | `Database Schema` |
| Every active staff member can `select *` from `profiles` | `20260929000000:119-121` |
| `profiles` SELECT widened by the migration named "security baseline" | same |
| 4 `"d MMM yyyy"` lexicographic sorts in the inventory layer | `InventoryRepositoryImpl:81,121,123` |
| `FullReportViewModel.computeDaily` (this **is** audit #15's residue) | `:282` |
| Unbounded WorkManager chain via `APPEND_OR_REPLACE` | `SyncManagerImpl:32-36` |
| `AuditLogger.log()` writes audit rows outside the caller's transaction | 5 call sites |
| Two declared error fields that can never be set | `DashboardViewModel:47`, `BorrowedViewModel:59` |
| `savePatient` swallows its own validation exception | `PatientViewModel:198-204` |
| Three statistics ViewModels can never clear `isLoading` | — |
| Notification-time and auto-backup changes need a process restart | — |
| Lock screen is an overlay, not a gate | `MainActivity:106-114` |

## Corrections the audit made to itself

✅ These were verified and are correctly resolved — preserved here so they are not
re-raised:

| Claim | Resolution |
|---|---|
| #43 nested scrollable-in-scrollable | **False positive** — both hits are `verticalScroll` inside an `AlertDialog` text slot, not nested in a `LazyColumn` |
| #39's third claim | **Wrong** — `SimpleGenderChart` renders labelled text rows, not a Canvas; only the two real canvases needed `contentDescription` |
| #36's `InventoryUtils` half | Needed a testability fix, not just a timezone fix; `today` is now a defaulted parameter |

## 2026-10-18 verification pass — audit §4.6 was wrong

Re-verified against commit `e05f7c0`. The original finding is preserved above; this is the
reconciliation, not a rewrite.

**`Known Bugs` §4.6 ("17 of 21 migrations are untracked in git") is a false positive.**
(The denominator is now 23 — `20261017_…` and `20261018_…` landed after this was written.)
Commit `f8e5d8a` ("chore: track migrations and full project documentation") added the folder
before the vault was generated, so the finding was already stale when it was written down.

```bash
git ls-files supabase/migrations/ | grep -c '\.sql$'   # → 23
git check-ignore supabase/migrations/*.sql             # → no output
```

The `.gitignore` comment at `supabase/.gitignore:5-8` describes a real *past* incident — it
is the historical record of the gap that `f8e5d8a` closed, not a current defect.

⚠️ **What survives** is the separate problem in `Known Bugs` §4.6's neighbourhood: the 23
migrations contain only 9 `CREATE TABLE` statements, so the 15 legacy base tables
(`patients`, `patient_visits`, `patient_notes`, `vaccination_items`, `vaccines`,
`vaccine_batches`, `inventory_transactions`, `waste_records`, `borrow_records`, `reminders`,
`consultations`, `finance_transactions`, `audit_logs`, `profiles`, `user_devices`) plus
`sync_queue`, `widget_due_cache` and `todo_notification_events` have no DDL in git — **a clean
clone still cannot reproduce the database**, and the base schema artifact remains gitignored.
Splitting the finding into "tracking ✅ fixed" and "baseline schema ⚠️ still missing" is the
correction; deleting the row entirely would lose a real defect.

Also corrected in the same pass: `Database Schema.md` and `Supabase.md` both carried the
false "only 4 tracked" claim, and `Release Notes.md` listed it as a shipping blocker.

## 2026-10-19 verification pass — the four partial fixes are closed

Re-verified against the current source on 2026-10-19; every citation below resolves in the
working tree. **No test suite was run in this pass** — each closure is source evidence, and
the instrumented tests named are cited, not executed.

| # | Neighbouring path | Now |
|---|---|---|
| #14 | `processNextItems()` captured the flight's boolean into a local | `SyncRepositoryImpl.kt:103-104` — `= flight.run { processNextItemsInternal() }`, so a waiter returns the owner's answer |
| #15 | `computeDaily` summed raw income rows | `FullReportViewModel.kt:298` — `FinanceCalculator.effectiveIncome(...)`, the same rule as `computeMonthly` |
| #38 | 7 of 19 `DueReminderDao` queries filtered; the paged twins and the `@Relation` feeder did not | All 16 `SELECT`s filter `is_deleted = 0` (`:18` through `:129`), including `getRemindersByVisitId` (`:42`), the pages (`:30`, `:33`, `:36`) and `BackupDao.kt:124` |
| #2 | Two plain `IllegalStateException`s escaped the `InsufficientStockException` catch | `deductStockFromBatch` throws `StockDeductionBlockedException` (`InventoryRepositoryImpl.kt:462`, `:472`) and `InsufficientStockException` (`:477`, a subtype), all caught at `ClinicalVaccinationService.kt:152` |

⚠️ **What is still 🔍:** #38's instrumented check (`ReminderSoftDeleteFilterTest` 12 cases +
`UpcomingVaccinationDaoTest` 16 cases) has not been executed; #14's wrapper is covered by
source only, since `SingleFlightResultTest` exercises `run()`'s return value rather than this
call site; and the catch in #2 has no automated test.

## Net position

| | Count |
|---|---|
| Audit claims **confirmed fixed** | **12** (was 8; the four partials closed 2026-10-19) |
| Audit claims **partial fixes, still open** | **0** (was 4) |
| Audit findings **deliberately not done** | 5 |
| Findings **not in the audit**, found later | 16 |
| Audit findings **retracted as false positives** | **1** (§4.6, tracking half only) |
| 🔍 Needs a live database to settle | 5 |

**Lesson for the register**: "fixed" requires evidence against the *original reproduction*,
not just evidence that the named mechanism changed. Every `Fixed` row in
[[Quality/Bug Register]] carries its verification evidence for exactly this reason.

## Related

- [[Quality/Bug Register]] — the canonical defect list
- [[Quality/Audit Reconciliation]] ← you are here
- [[Known Bugs]] — the narrative version of the same findings
- [[Documentation Maintenance]] — how to keep this page current
