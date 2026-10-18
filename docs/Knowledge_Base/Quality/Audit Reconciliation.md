# Audit Reconciliation

> Phase 1 audit: cross-check of `docs/AUDIT_REPORT_2026-10-16.md` against the source as it
> stands at commit `f8e5d8a`. This page records what the earlier audit claimed, what
> re-verification found, and the status of each claim.

## Method

Every claim in the audit's **Phase 6 implementation record** was re-verified against the
current source. Findings were not taken on trust. Where a claim could not be settled
without a live database, it is marked 🔍 rather than counted either way.

## Headline

⚠️ **The audit's "Fixed" table overclaims. Four entries are partial fixes: the mechanism was
corrected, but a neighbouring code path still produces the original symptom.**

This is not a criticism of the fixes — each one improved a real problem. It is a warning
that the *claims* are stronger than the evidence, which matters because closing a finding
removes it from anyone's follow-up list.

| Audit claim | Re-verified state | Verdict |
|---|---|---|
| #14 `SingleFlight` waiter reported success | `SingleFlight.run` **does** now return the owner's result. But the caller still captures into a local instead of using it | ⚠️ **Partial** |
| #15 two screens showed different revenue | `computeMonthly` **does** use `effectiveIncome`. `computeDaily` does not | ⚠️ **Partial** |
| #38 due-list counts ignored `is_deleted` | 7 queries **do** filter. 14 remain unfiltered, 5 of which feed live screens | ⚠️ **Partial** |
| #2 the "insufficient stock" catch was too wide | The catch **is** now narrow (`InsufficientStockException` only). But 2 `IllegalStateException`s still escape it | ⚠️ **Partial** |
| #4 editing discarded the whole clinical edit | `applyInventoryDiff` **does** capture shortfalls and complete the edit | ✅ **Confirmed fixed** |
| #3 deleting a patient restored every dose | `deletePatient` **does** clear deductions first | ✅ **Confirmed fixed** |
| #1 borrow across batches returned doses to the wrong batch | `BorrowBatchSelection.selectBorrowBatch` exists and refuses to span batches | ✅ **Confirmed fixed** |
| #19 18 of 22 migrations were git-ignored | 19 migration files are visible to git and now tracked | ✅ **Confirmed fixed** |
| #25 negative-stock trigger never installed | `20261016` installs it under the function-existence condition | ✅ **Confirmed fixed** |
| #27 14 read paths swallowed 401 | `CloudRefresh` rethrows session failures | ✅ **Confirmed fixed** |
| #37 `nextDueDate` compared dates as text | Compares parsed dates; 6 tests | ✅ **Confirmed fixed** |
| #36 due dates shifted east of IST | `parseDate` anchors to IST; 10 tests | ✅ **Confirmed fixed** |

## The four partial fixes, in detail

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
| Postgres stock trigger's `UPDATE` branch corrupts stock on a retried upsert | `20261013140000:56-61` |
| Legacy `tr_update_batch_stock` never dropped → possible double-application | `20261016:134-138` |
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
(The denominator is now 22 — `20261017_…` landed after this was written.)
Commit `f8e5d8a` ("chore: track migrations and full project documentation") added the folder
before the vault was generated, so the finding was already stale when it was written down.

```bash
git ls-files supabase/migrations/ | grep -c '\.sql$'   # → 21
git check-ignore supabase/migrations/*.sql             # → no output
```

The `.gitignore` comment at `supabase/.gitignore:5-8` describes a real *past* incident — it
is the historical record of the gap that `f8e5d8a` closed, not a current defect.

⚠️ **What survives** is the separate problem in `Known Bugs` §4.6's neighbourhood: these 21
migrations never `CREATE` the ~12 legacy base tables, so **a clean clone still cannot
reproduce the database** — that half of the claim stands, and the base schema artifact
remains gitignored. Splitting the finding into "tracking ✅ fixed" and "baseline schema ⚠️
still missing" is the correction; deleting the row entirely would lose a real defect.

Also corrected in the same pass: `Database Schema.md` and `Supabase.md` both carried the
false "only 4 tracked" claim, and `Release Notes.md` listed it as a shipping blocker.

## Net position

| | Count |
|---|---|
| Audit claims **confirmed fixed** | 8 |
| Audit claims **partial fixes, still open** | **4** |
| Audit findings **deliberately not done** | 5 |
| Findings **not in the audit**, found later | 15 |
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
