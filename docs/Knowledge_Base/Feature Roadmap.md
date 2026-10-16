# Feature Roadmap

> Suggested order of work, derived from [[Known Bugs]] and [[Technical Debt]].
> Not a commitment — a prioritisation.

## The organising principle

> Fix bugs that corrupt clinical or financial data before anything else, then bugs that lose
> user work, then polish.

The app is **structurally sound** — Clean Architecture holds, Hilt is clean, 252 unit tests
pass, the crypto is correct, the offline-first guarantee is real. The problems are
concentrated in a small number of specific places, most of them untested orchestrators.

## Stage 1 — Data integrity (do first)

These corrupt stock or visit records. All are small diffs.

| Task | Bug | Effort |
|---|---|---|
| Add `is_deleted = 0` to the 9 unfiltered `DueReminderDao` queries + a test | [[Known Bugs]] §1.4 | S |
| Fix the stock trigger: `- OLD.quantity + NEW.quantity` | §1.5 | XS |
| `drop trigger if exists tr_update_batch_stock` before installing the new one | §1.6 | XS |
| Cap the **positive** delta in `applyInventoryDiff` by the ledger, or set `COMPLETED` when stock moved | §1.1 | S |
| Move the two `IllegalStateException`s in `deductStockFromBatch` behind the `InsufficientStockException` recovery path | §1.2 | S |
| Move the reminder loop into `recordVaccination`'s transaction | §1.3 | M |

**Why this stage first:** §1.1 and §1.3 produce a stock ledger that disagrees with the shelf,
and a duplicate visit with a second deduction. For a clinic doing real inventory, that is the
most expensive class of bug here.

## Stage 2 — Backend hardening (has a deadline)

| Task | Bug | Effort |
|---|---|---|
| 🔍 Verify table `GRANT`s against the live DB — **`auto_expose_new_tables` is removed 2026-10-30** | §4.5 | S |
| `git add` the 17 untracked migrations | §4.6 | XS |
| 🔍 Check for the legacy `get_my_role()` policies on `profiles` / `audit_logs` | §4.2 | S |
| 🔍 Confirm `todo_notification_events` RLS is actually applied | §4.3, §4.4 | S |
| Narrow `profiles` SELECT to `id = auth.uid() or is_admin()` + column grants | §4.1 | S |
| Add `idx_user_devices_user_id` | §4.9 | XS |
| Schedule `cleanup_write_rate_limits()` (or delete the table) | §4.10 | S |
| Add `neo_stamp_accountability` to `expenses` | §4.8 | XS |

**Why this stage:** §4.5 has a **hard date**. §4.6 means a fresh clone cannot rebuild the
database at all.

## Stage 3 — Error surfacing (biggest user-visible win)

Small diffs, disproportionate impact.

| Task | Bug | Effort |
|---|---|---|
| Wire `DashboardViewModel.errorMessage` or delete it | §2.7 | XS |
| Wire `BorrowedViewModel.actionError` **or** give `BorrowedScreen` an error surface | §2.7 | S |
| Give `savePatient` an `onError` callback | §2.8 | S |
| Add `.catch {}` to the 3 statistics ViewModels | §2.9 | S |
| Add `finally` blocks to the 2 `ProfileViewModel` paths | §2.9 | XS |

**Why this stage:** today a failed patient save, a failed borrow return, or a failed backup
**all fail silently**. For clinic staff this is worse than a visible crash.

## Stage 4 — Scheduling correctness

| Task | Bug | Effort |
|---|---|---|
| Reschedule notifications when `reminderTime` changes | §2.4 | S |
| Call `BackupAutoScheduler.schedule()` from `BackupViewModel` | §2.5 | XS |
| Make `ReminderScheduler.runNow()` a **unique** work name | [[Due Vaccination]] #2 | XS |
| Make `APPEAR` → `REPLACE` for the automatic-sync chain | §3.1 | S |

## Stage 5 — Known-correctness cleanups

| Task | Bug | Effort |
|---|---|---|
| Fix the 4 `"d MMM yyyy"` lexicographic sorts | §2.10 | XS |
| Make `addStockBatch` transactional | §2.11 | S |
| Wrap `InventoryRepositoryImpl` batch reads+writes in a transaction | §3.3 | M |
| `applyInventoryDiff` — pass `transactionGroupId` through to the stock calls | §3.x | XS |
| `computeDaily` — route through `FinanceCalculator.effectiveIncome` | §2.2 | XS |
| `SyncRepositoryImpl.processNextItems` — return the gated result | §2.3 | XS |

## Stage 6 — Deletion of dead code

~24 unused DAO methods, 50 lines of unreachable FEFO spread, `reservedQuantity`,
`doseSchedule`, `Vaccine.stock`, and three unreachable sync paths. See [[Technical Debt]].

⚠️ **Do `VaccineDao.getAuditCountForVaccine` first** — it is not just dead, it is *wrong*, so
the destructive-delete guard it was meant to provide is enforced nowhere. Either fix the
column or remove the claim that it exists.

## Stage 7 — Tests

The pattern is already established: pure functions are tested, orchestrators are not. Add
tests in that order:

1. `ClinicalVaccinationService.deductInventoryForNewVaccination` — the COMPLETED/FAILED
   distinction and PARTIAL status
2. `VaccinationEditEngine.applyInventoryDiff` + `reconcileInventoryDeductions`
3. `BackupRestorer` — the three conflict modes and FK ordering
4. `ReminderDueListProcessor.process()` / `.stats()`
5. `SyncRepositoryImpl.processNextItems` — the wrapper, not just the extracted helpers
6. One `*ViewModelTest.kt` — any of them, to establish the pattern

Also worth doing: **make `SyncOrderingTest` assert against `SYNC_ENTITY_REGISTRY`** instead of
its hand-copied table, which already disagrees with the registry.

## Stage 8 — Structural improvements (larger)

| Task | Why |
|---|---|
| Extract a shared `UiState` sealed hierarchy; retire the `error: String?` pattern | Would have made §2.7 impossible. The pattern already exists — `SlotsUiState` in `core/ui/Dropdowns.kt:162-169` |
| Type-safe navigation | `Routes.kt:59-67` documents the bug and fixed it for one param; 26 raw interpolations remain |
| Collapse `AuditLogger.log()` into `recordLog()` | Removes an entire class of out-of-transaction bug |
| Give `vaccine_batches` an `@Version` column | Room's optimistic locking would fix §3.3 properly |
| Consolidate the FEFO selector | 4 implementations, 5 "usable batch" predicates, 2 expiry semantics |
| Run `MigrationTestHelper` properly | Only `33.json` was ever exported, so 14 of 16 migrations are untested |

## Explicitly not worth doing

To keep this honest about scope:

- **A clinical schedule engine.** `Vaccine.doseSchedule` is write-only today and next-due
  dates are free-text staff entry. Building a schedule engine is a real feature — but it is
  a *product* decision for the clinic, not a technical debt item. Raise it with the clinic
  before spending a sprint on it.
- **Type-safe navigation across all 51 destinations.** Fix the `enc`/`dec` helpers for the
  remaining 26 call sites (cheap) and leave the full migration.
- **JaCoCo coverage measurement.** The untested orchestrators are obvious from the test
  inventory; a percentage number adds little.

## Related

- [[Known Bugs]] — every item referenced here, with severity and evidence
- [[Technical Debt]] — the duplication behind stages 6 and 8
- [[Testing]] — where to add tests and why
- [[Release Notes]] — what is already shipped
