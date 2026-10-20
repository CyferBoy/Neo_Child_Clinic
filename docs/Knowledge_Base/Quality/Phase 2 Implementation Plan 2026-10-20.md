# Phase 2 Implementation Plan — 2026-10-20 (data access)

> Ordered, reviewable batches implementing the findings of
> [[Data Access Findings Register 2026-10-20]]. Evaluate against the report first — **nothing in
> this file has been executed**. Architectural redesigns are separated from targeted fixes;
> every batch names tests, risks, metrics, rollback, and dependencies. Code changes wait for
> your explicit approval per batch (Phase 1 remains read-only).

**Batch order principle**: biggest correctness win first; measurement batch first because every
later batch must show before/after numbers; cheap polish interleaved early.

---

## Batch 0 — Instrumentation (before/after evidence)

- **Goal**: make all perf claims measurable at any future change.
- **Findings**: DA-02, DA-04, DA-12, DA-14 (all 🔍 today).
- **Files**: `feature/sync/domain/RefreshDataUseCase.kt` (step timers), `core/sync/SyncRepositoryImpl.kt`
  (batch/queue timings), `core/di/SupabaseModule.kt` (dev-only ktor byte counter),
  optional cache/`WidgetWorker` counters.
- **Implementation**: debug-only timers + log lines, aggregate only (durations, byte totals,
  row counts); no payload text — PII rule preserved (`CloudRefresh.kt:23-29` pattern is the model).
- **Tests**: none needed for logging-only additions beyond a unit test asserting production
  release behavior emits nothing (a `BuildConfig.DEBUG` guard assert).
- **Risks**: negligible. **Migration**: none. **Before/after**: this *creates* the metric table
  from [[Performance and Measurement Plan 2026-10-20]].
- **Rollback**: revert single commit.
- **Acceptance**: metric log lines appear in a device run at benchmark size and vanish on
  production build variant.

## Batch 1 — Deletion divergence fix (once the approach is chosen)

- **Goal**: remotely deleted rows disappear locally, and the silently-lost edit chains that
  follow them stop happening (fixes the systemic gap; the largest correctness item).
- **Findings**: DA-01 (= NCC-023).
- **Decision required first** (see [[Data Access Audit Summary 2026-10-20]]) — three options:
  - **(b) = Recommended**: per-table diff pull — after the normal upsert pass, fetch
    `SELECT id` (single column) each refresh and `DELETE` local rows whose ids are absent from
    that set, **skipping any row with a pending queue entry** (`unsyncedIdSet` guard) and
    skipping rows inside the same transaction that must not cascade (children are covered by
    local FK order; child deletes emit no remote writes).
  - (a) server tombstones table: safest semantics, more server setup + a new pull step.
  - (c) re-introduce soft-delete booleans: largest change; registered as not-preferred here.
- **Files**: all 14 repositories’ `refresh*()` implementations (patient, vaccination,
  consultation, todos, waste, borrow, finance, expense, doctor slots, personal reminder,
  reminder, inventory x2, profile) — one shared helper in `core/sync/` (`pruneMissingLocal(...)`
  with the guard semantics) so the diff is one function + call lines per repository.
- **Tests** (instrumented in CI + JVM for the helper): remote-only drain removes local rows;
  pending-queued rows are never pruned; tombstoning visits retains their consultation FK order;
  retries: prunes long-run idempotent.
- **Risks**: false deletes if the id-fetch itself fails (mitigate: on fetch error, skip pruning
  that table); restoring via cached local row — that is the finding itself, not a new bug;
  watch client-specific “pull skip rows whose FK patient is missing locally” interplay in
  `VaccinationRepositoryImpl.kt:121-126` (pruner must run after that table’s guard).
- **Before/after metric**: the second device’s differential patient/visit/reminder counts
  converge to zero drift after a replayed sync of a delete-then-edit scenario.
- **Rollback**: guard-flag constant in each repository (default ON; flip OFF to restore old
  behavior).

## Batch 2 — Trivial hygiene (no behavioral change)

- **Goal**: delete dead code and stop leaking identifiers into Logcat.
- **Findings**: DA-09 (dead `deductStock()` multi-batch loop; dead `VaccineDao.getAuditCount*`
  wildcard-scan queries), DA-11 (3 log sites: `VaccinationRepositoryImpl.kt:111,119`,
  `ProfileRepositoryImpl.kt:45,63`), DA-15 (WidgetWorker concrete-impl injection).
- **Files**: `InventoryRepositoryImpl.kt`, `VaccineDao.kt`, the 2 repositories above,
  `feature/widget/data/WidgetWorker.kt:31-32`.
- **Tests**: existing suites pass untouched; add one assert in `InventoryRepository` callers
  scan: nothing calls `deductStock(` anymore.
- **Before/after**: fewer scans on `audit_logs`, cleaner logs; measurable only by the Batch 0
  counters (bytes for the two dead queries → 0).
- **Rollback**: these are deletions; single commit each.

## Batch 3 — Remote payload trims + queue integrity + client timeouts

- **Goal**: (i) conflict-check reads stop fetching full rows; (ii) the schema enforces the
  queue dedup the code does by hand; (iii) an explicit request timeout is pinned and documented.
- **Findings**: DA-02b (SyncUploader conflict read :400-428), DA-08 (unique dedup index,
  Room v34), DA-07 (needs-validation first: verify supabase-kt default `requestTimeout` in code).
- **Files**: `core/sync/SyncUploader.kt:410-427`; `SyncQueueEntity.kt:13` (+ Room migration
  33→34); `SyncRepositoryImpl.kt:64-85` (`enqueue` → IGNORE strategy); `SupabaseModule.kt:44-66`.
- **Tests**: unit — registry-based conflict-mapping still returns rows with the narrower SELECT
  (`SyncEntityRegistryTest`-style fixture); Room — migration 33→34 forward-and-repeat test;
  concurrent enqueue yields a single PENDING row (follows `SyncQueueDao` instrumented house
  pattern).
- **Migration**: Room 33→34 — additive unique index only, no data transform, no column changes;
  re-runnable, and the old duplicate-row behavior has no other consumer.
- **Rollback**: DROP INDEX is a normal migration; deploy once.

## Batch 4 — Pull-loop N+1 removal

- **Goal**: batched per-entity set-reads in the three remaining pull loops (established house
  pattern).
- **Findings**: DA-03.
- **Files**: `VaccinationRepositoryImpl.kt:121-126` (one IN-list patient existence map),
  `ReminderRepositoryImpl.kt:477-492` (batch visit-id set + reminder-stable map),
  `ProfileRepositoryImpl.kt:70-74` (switch to `unsyncedIdSet("PROFILE")`).
- **Tests**: reuse `BatchPullGuardEquivalenceTest` dash-board pattern per repository; assertion:
  same outcomes with a 200-row synthetic pull as per-row.

## Batch 5 — Widget query + DI type

- **Goal**: widget refresh cost independent of patients-table size.
- **Findings**: DA-06, DA-15.
- **Files**: `data/local/dao/WidgetDueDao.kt` (join the due-cache rows to patient names in one
  SQL query), `feature/widget/data/WidgetWorker.kt` (make a query; typed-to-interface injection).
- **Tests**: instrumented DAO test with seed fixture naming two patients, one due row; assert
  single SELECT with names (and no patient table read).

## Batch 6 — Incremental remote pulls (staged; behind explicit per-table decision)

- **Goal**: stop re-downloading whole tables on every entry/event/refresh.
- **Findings**: DA-02 (projection/data size; delta behavior).
- **Files**: new small `RemoteSyncCursorDao`/table (local, non-synced) + per-repository `where
  updated_at > cursor` in the refresh merge paths that have a maintained `updatedAt` column
  (patients, visits, consultations, todos, waste, borrow, profiles, expense, doctor tables,
  reminders); finance and inventory stay on full pulls until their columns are verified.
- **Staged**: patients first (largest); verify per-table with an instrumented "changed row enters
  the pull" test; then extend table by table.
- **Depends on**: Batch 0 metric capability; Batch 1 (prune already landed — merges don't
  conflict). 2 runs of pattern → then automated for the rest.
- **Risk**: monotonic `updated_at` requirement — server-generated timestamps are reliable
  (Postgres trigger), local merge guards unchanged; count-preserving qualification added.
- **Before/after metric**: patients pull bytes/duration ~-90% at steady state (target).

## Batch 7 — Statistics SQL aggregates (gated on Batch 0 measurements)

- **Goal**: statistics screens stop recomputing over whole tables.
- **Findings**: DA-04 (+ DAO misses related to NCC-031 kept as is).
- **Files**: `feature/statistics/domain/*` + new DAO aggregate queries on finance/visits/expenses.
- **Only after** Batch 0 shows recomputes/durations over target. UX class preserved
  (`FinanceCalculatorTest` covers business rules, reuse).

## Batch 8 — Deferred UI items (decisions pending, unchanged from earlier plan)

`collectAsStateWithLifecycle` (needs lifecycle-runtime-compose dependency decision), formatter
hoists, list pagination design. Tracked in [[Performance and Measurement Plan 2026-10-20]] §Defer.

---

## Out of scope (recorded, no batch)

- DA-05 (search FTS) — requires benchmark evidence + design; queued decision list.
- DA-10 (audit retention) — policy decision needed first (keep-all vs window).
- DA-12 (WAL vs TRUNCATE) — measurement-only; do not change without device numbers.
- DA-13 — deliberate; no change.
- Server-side stock trigger verification (NCC-002) — live-DB confirmation, separate track.

## Related reports

- [[Data Access Architecture Audit 2026-10-20]] · [[Data Access Findings Register 2026-10-20]] ·
  [[Performance and Measurement Plan 2026-10-20]] · [[Data Access Audit Summary 2026-10-20]]
