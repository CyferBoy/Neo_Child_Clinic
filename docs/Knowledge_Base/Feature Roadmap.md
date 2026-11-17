# Feature Roadmap

> Suggested order of work, derived from [[Quality/Bug Register]] and [[Technical Debt]].
> Not a commitment — a prioritisation.
> Status lives in [[Quality/Bug Register]] — this page is an order of work, not a status
> board. Struck-through rows were completed after it was written (register row is the
> evidence); 🔍 marks what still needs a live database. Verification standard: see
> [[Documentation Maintenance]] § Verification evidence format.

## The organising principle

> Fix bugs that corrupt clinical or financial data before anything else, then bugs that lose
> user work, then polish.

The app is **structurally sound** — Clean Architecture holds, Hilt is clean, 277 unit tests
exist (`app/src/test`), the crypto is correct, the offline-first guarantee is real. The
problems are concentrated in a small number of specific places, most of them untested
orchestrators.

⚠️ **Before starting Stage 1**, read [[Quality/Audit Reconciliation]]. Four findings were
already "fixed" in a previous pass and are still open; re-deriving them wastes a day.

## Stage 1 — Data integrity

| Task | Bug | Sev | Effort | Test to add |
|---|---|---|---|---|
| ~~Cap the positive delta by the ledger in `applyInventoryDiff`~~ | NCC-001 | S1 | — | ✅ `VaccinationEditEngineTest` (8) |
| ~~Add `is_deleted = 0` to the reminder queries~~ | NCC-006 | S1 | — | 🔍 androidTest only |
| **Re-fix** the stock trigger's UPDATE branch — the retry path still nets `-OLD.quantity` (`20261017:80,101`) | NCC-002 | S1 | S | 🔍 live-DB pgTAP/psql delta assertion |
| ~~`drop trigger if exists tr_update_batch_stock` before installing~~ | NCC-003 | S1 | — | 🔍 live-DB trigger listing |
| ~~Route the pre-write throws through the recovery path~~ | NCC-005 | S1 | — | compile-verified |
| ~~Move the reminder loop into `recordVaccination`'s transaction~~ | NCC-004 | S1 | — | ✅ `RecordVaccinationTransactionTest` (3) |

**Why first:** NCC-001 and NCC-004 left the stock ledger disagreeing with the shelf. For a
clinic doing real inventory that was the most expensive class of bug here.

⚠️ **NCC-002 and NCC-003 are SQL migrations and must not be applied until you have run the
trigger query in [[Release Checklist]] § 5.** NCC-003's fix drops a trigger that may or may
not exist in your hosted project; without that check you would be removing the only trigger
that is live.

## Stage 2 — Backend hardening (has a deadline)

| Task | Bug | Sev | Effort |
|---|---|---|---|
| 🔍 Verify table `GRANT`s — **`auto_expose_new_tables` removed 2026-10-30** | NSC-005 | High | S |
| 🔍 Check for legacy `get_my_role()` policies on `profiles` / `audit_logs` | NSC-002 | Critical | S |
| Narrow `profiles` SELECT + column grants | NSC-001 | High | S |
| Confirm `todo_notification_events` RLS is applied everywhere | NSC-004, NSC-003 | High | S |
| Add `idx_user_devices_user_id` | NSC-006 | Low | XS |
| Schedule or drop `cleanup_write_rate_limits()` | NSC-012 | Medium | S |
| Add `neo_stamp_accountability` to `expenses` | NSC-007 | Medium | XS |

**Why:** NSC-005 has a hard date. Everything else needs the live database — see
[[Release Checklist]] § 5.

## Stage 3 — Error surfacing

| Task | Bug | Sev | Effort |
|---|---|---|---|
| ~~Wire or delete `DashboardViewModel.errorMessage`~~ | NCC-013 | S2 | — |
| ~~Wire `BorrowedViewModel.actionError` **or** give `BorrowedScreen` an error surface~~ | NCC-014 | S2 | — |
| ~~Give `savePatient` an `onError` callback~~ | NCC-015 | S2 | — |
| ~~Add `.catch {}` / `finally` to 5 ViewModels' loading flags~~ | NCC-016 | S2 | — |

All four are fixed in source and source-verified, with **no regression test** — see
[[Quality/Bug Register]]'s source-verified table.

**Why:** when this page was written, a failed patient save, a failed borrow return and a
failed backup **all failed silently**. For clinic staff that is worse than a visible crash —
which is why they were fixed first.

## Stage 4 — Scheduling correctness

| Task | Bug | Sev |
|---|---|---|
| ~~Reschedule notifications when `reminderTime` changes~~ | NCC-010 | S2 |
| ~~Call `BackupAutoScheduler.schedule()` from `BackupViewModel`~~ | NCC-011 | S2 |
| Make `ReminderScheduler.runNow()` a unique work name | — | S3 |
| ~~`APPEND_OR_REPLACE` → `REPLACE` for the sync chain~~ | NCC-019 | S3 | — |

NCC-010 and NCC-011 are fixed in source and source-verified, with **no regression test**
(register: `NotificationSettingsViewModel.kt:37-38`, `BackupViewModel.kt:209,215`).

## Stage 5 — Correctness cleanups

| Task | Bug | Sev | Effort |
|---|---|---|---|
| ~~Fix the `"d MMM yyyy"` lexicographic sorts~~ | NCC-017, NCC-040 | S2 | — | ✅ `InventorySortKeysTest` (13) |
| ~~Make `addStockBatch` transactional~~ | NCC-018 | S2 | — |
| Wrap batch read+write; add `@Version` to `VaccineBatchEntity` | NCC-021 | S3 | M |
| Pass `transactionGroupId` through `applyInventoryDiff` | — | S3 | XS |
| ~~Route `computeDaily` through `effectiveIncome`~~ | NCC-008 | S2 | — |
| ~~`return flight.run { … }` in `processNextItems`~~ | NCC-009 | S2 | — | ⚠️ test still missing |
| Collapse `AuditLogger.log()` into `recordLog()` | NCC-022 | S3 | S |

## Stage 6 — Dead code removal

~24 unused DAO methods, 50 lines of unreachable FEFO spread, `reservedQuantity`,
`doseSchedule`, `Vaccine.stock`, and three unreachable sync paths. See [[Technical Debt]].

⚠️ **Do NCC-030 first** — it is not merely dead, it is *wrong*, so the destructive-delete
guard it was meant to provide is enforced nowhere.

## Stage 7 — Tests

The established pattern: pure functions are tested, orchestrators are not. Add tests in that
order:

1. `ClinicalVaccinationService.deductInventoryForNewVaccination` — NCC-005 lives here
2. `VaccinationEditEngine.applyInventoryDiff` + `reconcileInventoryDeductions` — NCC-001
3. `BackupRestorer` — the three conflict modes and FK ordering
4. `ReminderDueListProcessor.process()` / `.stats()` — **0 tests today**
5. `SyncRepositoryImpl.processNextItems` — **the wrapper**, not the extracted helper — NCC-009
6. One `*ViewModelTest.kt` — any of them, to establish the pattern

Also: make `SyncOrderingTest` assert against `SYNC_ENTITY_REGISTRY` instead of its
hand-copied table, which already disagrees with the registry.

## Stage 8 — Structural (larger)

| Task | Why |
|---|---|
| Extract a shared sealed `UiState`; retire `error: String?` | Would have made NCC-013/014 impossible. The pattern exists — `SlotsUiState` |
| Type-safe navigation | `Routes.kt:59-67` documents the bug; 22 raw interpolations remain |
| Add `@Version` to `VaccineBatchEntity` | Room optimistic locking fixes NCC-021 properly |
| Consolidate the FEFO selector | 4 implementations, 5 "usable batch" predicates |
| Run `MigrationTestHelper` properly | only `33.json` is exported, so `ExpenseMigrationTest` is `@Ignore`d with that reason (`ExpenseMigrationTest.kt:28-84`); `DateGivenIndexMigrationTest` sidesteps the helper and asserts against raw SQLite (`DateGivenIndexMigrationTest.kt:22-23`) |
| Verify [[Quality/Hypotheses]] | 9 items blocked only on a runtime or live-DB check |

## Explicitly not worth doing

- **A clinical schedule engine.** `doseSchedule` is write-only today; next-due dates are
  free-text staff entry. That is a **product** decision for the clinic, not technical debt.
- **Type-safe navigation across all 51 destinations.** Fix the `enc`/`dec` helpers for the
  22 raw call sites (cheap); leave the full migration.
- **JaCoCo coverage.** The untested orchestrators are obvious from the test inventory.

## Related

- [[Quality/Bug Register]] — every item above, with evidence and status
- [[Quality/Audit Reconciliation]] — read this before starting Stage 1
- [[Quality/Performance Diagnosis and Plan 2026-10-20]] — the measured slow-loading diagnosis and its ordered plan
- [[Technical Debt]] — the duplication behind stages 6 and 8
- [[Release Checklist]] — the gate before shipping any stage
- [[Release Notes]] — what has already shipped

