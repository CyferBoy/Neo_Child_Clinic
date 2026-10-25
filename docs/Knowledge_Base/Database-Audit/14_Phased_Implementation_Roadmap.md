# Phased Implementation Roadmap

**Status 2026-10-22:** Phase 0 live checks remain blocked (no Supabase credentials/connection in this environment). Phase 1's trigger-arithmetic fix is implemented and tested locally on PostgreSQL 18.2; ledger CREATE uploads are insert-only in `SyncUploader.kt`, but that Kotlin change is uncompiled; the `inventory_reconciliation` diagnostic view and test are added. Nothing is deployed and no data was repaired. See `16_Phase1_Stock_Trigger_Remediation.md` and `17_Inventory_Reconciliation_View.md`. All other phases and the remaining Phase 1 tasks are unchanged and gated on approval.

Each phase is gated on explicit approval; phases that deploy server objects or change production data additionally require the read-only verification in Phase 0. Effort labels: **S** ≤ 1 day, **M** 2–4 days, **L** 1–2 weeks (including tests/review, not calendar estimates).

## Phase 0 — Verify and freeze (no code changes)

**Goal:** replace live-state unknowns with facts before touching data.

| Task | Output | Effort |
| --- | --- | --- |
| Run read-only SQL from `15_Unknowns_And_Live_DB_Gaps.md` | trigger count/body, FK list, RLS policies, schema columns | S |
| Export/record migration ledger | which of the 23 migrations are applied | S |
| Quantify stock drift with reconciliation query | affected batch count, worst divergence | S |
| Quantify finance date drift | rows whose `transaction_date` ≠ visit date in IST | S |
| Export affected rows / take a verified backup | restore-tested snapshot | S |
| Confirm device queue backlogs | pending/failed counts per entity | S |
| Freeze schema changes during the fix window | change freeze note | S |

**Exit criteria:** every “live unknown” in this audit is answered or explicitly accepted; a restorable backup exists; the stock/finance blast radius is known.

**Stop point:** present Phase 0 results and the exact repair plan. Do not proceed without approval.

## Phase 1 — Stop stock corruption (S1)

**Goal:** no new wrong stock, retries idempotent, tests prove it.

| Task | Detail | Effort |
| --- | --- | --- |
| Fix `update_batch_stock()` UPDATE branch | same-batch delta or move semantics; remove conditional undo | S |
| Make CREATE uploads insert-only | `on conflict do nothing` for ledger CREATE; no trigger UPDATE on retry | M |
| Stop updating ledger rows for corrections | compensating rows (target) or keep fixed UPDATE as interim | M |
| Add SQL trigger test matrix | retry, edit, move, delete, negative guard | M |
| Verify exactly one trigger installed | deployment check + migration guard | S |
| Add drift view/alarm | `inventory_reconciliation` | S |

**Implemented locally (2026-10-22):** the UPDATE-arithmetic fix, the single-trigger guard, the SQL regression matrix, insert-only ledger CREATE uploads, and the `inventory_reconciliation` diagnostic view are in the new migrations/tests and `SyncUploader.kt` (see `16_Phase1_Stock_Trigger_Remediation.md` and `17_Inventory_Reconciliation_View.md`). Still open in this phase: compensating rows for corrections, compiling/testing the Kotlin change, scheduling an alarm on the view, and deployment to a disposable Supabase project.

**Exit criteria:** T-01 passes on a disposable DB; replaying any ledger batch yields zero drift; retries are no-ops; a duplicate trigger is impossible or detected.

**Rollback:** restore the prior function definition; the schema is unchanged. Do not deploy data repair in this phase.

## Phase 2 — Data repair decision (S1, separate gate)

**Goal:** correct historical stock, only after approval.

| Task | Detail | Effort |
| --- | --- | --- |
| Build repair preview | per-batch computed vs recorded, with ledger evidence | M |
| Review with clinic owner | confirm business treatment for each category | S |
| Repair in a transaction | snapshot table, update batches, record audit | M |
| Verify | reconciliation view zero drift; spot-check top batches | S |

**Exit criteria:** approved repair report, snapshot retained, drift zero, no unexplained adjustments.

**Do not** run a blanket “set stock to ledger sum” without reviewing legitimate manual adjustments. If the app has legitimate manual stock entries, they must be in the ledger.

## Phase 3 — Finance correctness and timezone (S2)

| Task | Detail | Effort |
| --- | --- | --- |
| Remove pull-time self-heal | pulls never overwrite pending rows and never enqueue | S |
| Use IST everywhere in finance | replace `systemDefault()` with clinic zone helper | S |
| Centralize clinic date helpers | `clinicToday()`, `clinicZone()`, `clinicFormatter()` | M |
| Replace business-path `LocalDate.now()`/`Calendar` | dashboard, workers, stock filter, expenses, statistics | M |
| Fix finance query/index | backfill `transaction_date`; direct ORDER BY or expression index | S |
| Tests | T-02 and T-08 | M |

**Exit criteria:** pending finance edits survive a pull; dates are identical under four device zones; no pull enqueues writes; finance lists use an index.

## Phase 4 — Identity and delete correctness (S2)

| Task | Detail | Effort |
| --- | --- | --- |
| Reminder delete helper | always `serverId ?: id`, grouped child-first | S |
| Resolve inventory ledger visit FK | verify constraint; restore unlink or drop FK deliberately | S–M |
| Preserve `created_at` on visit edit | copy existing value in mapper | S |
| Audit logger transaction decision | same-transaction or post-commit only | S |
| Tests | T-03, visit-delete integration, rollback audit | M |

**Exit criteria:** deleting a pulled patient removes its remote reminders; visit deletes with ledger rows succeed; `created_at` stable; no audit row for a rolled-back write.

## Phase 5 — Sync convergence (S2)

| Task | Detail | Effort |
| --- | --- | --- |
| Add tombstones/`updated_at` server-side | migration + policies | M |
| Cursor-based pull | per-table cursor, batched | L |
| Apply tombstones locally | transaction per table, pending guard | M |
| Queue uniqueness | partial unique index for PENDING + atomic enqueue | S |
| Borrow return guard | server aggregate cap; client uses recomputed outstanding | M |
| Tests | T-05, T-06, T-04, two-device scenarios | M |

**Exit criteria:** remote delete disappears on the second device; duplicate enqueue yields one row; over-return is rejected server-side; no local pending edit is lost.

## Phase 6 — Offline completeness and performance (S2/S3)

| Task | Detail | Effort |
| --- | --- | --- |
| Pull inventory ledger / cache stock history | Room-backed stock history | M |
| Per-table single-flight | coordinator used by all screens | M |
| Cursor + pagination for large tables | patients/visits/finance | M |
| Indexes after measurement | sync queue, finance expression, search | S |
| Audit offline state | label or cache | S |
| Benchmark | cold start, screen open, pull payload | M |

**Exit criteria:** stock history works offline after one successful sync; request count per launch is bounded; benchmarks recorded before/after.

## Phase 7 — Authorization (S2)

| Task | Detail | Effort |
| --- | --- | --- |
| Approve role→table→operation matrix | product decision | S |
| Implement RLS policies | SELECT stays broad; writes/deletes by role | M |
| Mirror in UI | keep existing gates; fix mismatches | S |
| SQL role tests | every role + inactive user | M |

**Exit criteria:** T-07 passes; every UI-only restriction has a matching SQL restriction or an explicit accepted risk; client-side gates remain UX only.

## Phase 8 — Test and operations hardening

| Task | Detail | Effort |
| --- | --- | --- |
| Export missing Room schemas | v31/v32 for migration tests | S |
| Migration tests | real `MigrationTestHelper` for supported versions | M |
| CI: unit + instrumentation + SQL | disposable services | M |
| Sync health/debug screen | pending/failed/drift/last-pull | M |
| Update docs | replace stale claims in `docs/SYNC.md`, Bug Register | S |

**Exit criteria:** CI runs the regression suite; drift and sync age visible; documentation matches HEAD.

## Dependency order

```text
Phase 0 → Phase 1 → Phase 2 (separate approval)
                 ↘ Phase 3 → Phase 4 → Phase 5 → Phase 6
Phase 7 can run in parallel with 3–6 after the matrix is approved.
Phase 8 runs continuously, but the trigger/RLS tests are required before Phases 1/7 ship.
```

## Release gates

Every phase requires:

1. a reviewed diff and migration with a rollback statement;
2. tests that fail before and pass after;
3. a disposable-environment run;
4. a production backup and restore check for data migrations;
5. no unrelated refactors bundled;
6. updated audit evidence in this directory.

## Explicit non-goals

- No desktop client work.
- No large-scale rewrite of repositories or DI.
- No automatic production data repair.
- No speculative performance work before Phase 6 measurements.
- No weakening of RLS to make tests pass.
