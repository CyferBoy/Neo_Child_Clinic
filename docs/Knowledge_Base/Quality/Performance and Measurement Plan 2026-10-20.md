# Performance and Measurement Plan — 2026-10-20

> Companion to the 2026-10-20 data-access audit ([[Data Access Architecture Audit 2026-10-20]],
> findings in [[Data Access Findings Register 2026-10-20]]). It defines what performance evidence
> exists, what is missing, exactly what to instrument, safe benchmark datasets, the baseline
> procedure, and success criteria. **No numbers below are measurements** — they are targets.
> The environment restricts live measurement: no device/emulator locally, no production contact.

## Current known performance evidence

Measured / verified before 2026-10-20 (from [[Performance Diagnosis and Plan 2026-10-20]], all
code-verified, none device-measured):

1. Cold-start and screen-entry refresh ran the whole 13-step pipeline — fixed for the two hottest
   screens (scoped pulls, `PatientListViewModel.kt:101-140`, `DashboardViewModel.kt:126-132`).
2. Dashboard tile counts loaded whole lists into memory — fixed (SQL aggregates,
   `InventoryTileCounts.kt`, `BorrowDao.kt:28`, `ReminderDueListProcessor.kt:102`).
3. Today’s Patient refresh did queue-set + snapshot per table inside loops — fixed
   (`PatientTodoRepositoryImpl.kt:34` batched, 2026-10-19).

Structural cost facts established by this audit (not measured, see register):
- 14 full-collection remote pulls per full refresh, no projection/pagination/delta (DA-02).
- 13-step manual pipeline ≈ 16+ remote calls incl. an unconditional COGS migration (DA-14).
- Pull loops with per-row local queries (DA-03); statistics recompute on every table change (DA-04);
  search scans (DA-05); widget full-table read (DA-06); `audit_logs` unbounded (DA-10);
  SQLCipher TRUNCATE journal (DA-12 → 🔍).

## Missing instrumentation (where, exactly)

Instrument behind `BuildConfig.DEBUG` only, aggregate counters only, **never row contents or
identifiers** (project rule: no patient/token data in logs):

| Metric | Where to instrument | Notes |
|---|---|---|
| Refresh step durations (per step, per scope) | `feature/sync/domain/RefreshDataUseCase.kt` inside `runRefreshStep` (:136-144) — wrap `block()` timers | one aggregate log per step name |
| Upload batch duration + queue size | `core/sync/SyncRepositoryImpl.processNextItemsInternal` (:112-184) — log start/end + pending count before/after | counts only |
| Remote payload bytes | one ktor interceptor installed in `core/di/SupabaseModule.kt` (development-only) summing response bodies per host path | totals, not contents |
| Local SQL duration for hot queries | Room `SupportSQLiteOpenHelper` callback or per-DAO timing in `TransactionRunner` (`core/database/`) | p50/p95 in benchmark only |
| Screen first Room paint | `stateIn(...)` emission timestamps in the 3 heaviest ViewModels (Dashboard, Statistics, PatientList) | `LaunchedEffect`-free measurement |
| Statistics recompute frequency | counter at the `combine` lambdas (`StatisticsViewModel.kt:73`, `FullReportViewModel.kt:99`) | events/minute |
| Cache hit/miss | `PatientCache` existing counters if any; else add a @get counter (memory only) | |
| Widget time | `WidgetWorker.doWork` (:25-) | |

## Required metrics & targets (auto-checkable in Phase 2 work)

Targets (not claims): 

- Scoped patient-list pull (PATIENTS+VISITS+CONSULTATIONS) ≤ 1.5 s at the benchmark dataset size;
  TODOS pull ≤ 800 ms.
- Full manual refresh completes ≤ 15 s at benchmark size (devices with ≥ 4 MB/s down).
- Dashboard first paint from Room ≤ 400 ms after process ready (SQLCipher on).
- Statistics screen emits ≤ 3 recomputes per 10 s while idle (no local writes).
- Widget refresh ≤ 500 ms and reads ≤ 2 queries (after DA-06 fix).
- Queue drain of 50 items ≤ 20 s with payload totals reported (after DA-02b).

## Safe benchmark dataset + baseline procedure

1. **Synthetic seed** (JVM/instrumented, local DB only): a small Kotlin script seeds Room through
   the real DAOs (keeps indexes/triggers honest): 1,000 patients / 5,000 visits / 15,000
   vaccination_items / 200 batches / 60 vaccines / 10,000 inventory transactions / 2,000 reminders
   / 10,000 audit_logs / 5,000 finance entries. Dates spread over 24 months, IDs as UUIDs,
   names = empty (`""`) **or clearly fake (`TEST-`)** tokens to honor PII rules.
2. **Local microbenchmarks**: run on the local box via JUnit (JVM) where possible (pure DAO
   semantics need androidTest; run those in CI `android-instrumented`) — record p50/p95 wall times
   per query family.
3. **Remote measurements** (📌 needs environment approval): a staging Supabase project with the
   same dataset; the ktor byte-counter interceptor reports per-call bytes; screenshots of the
   cockpit logs are sufficient evidence; **never** production credentials or patient records.
4. **Baseline procedure**: freeze commit → run synthetic seed → record the metric table → apply
   one batch → re-run → append to the batch’s diff report in [[Phase 2 Implementation Plan 2026-10-20]].
5. **Cold start note**: this box OOM-killed an 8 GiB-heap Gradle daemon; run Gradle with the
   frugal flags (`-Xmx2200m`, `--max-workers=1`) as documented in [[Documentation Repair 2026-10-20]].

## Deferred UI items (from the earlier perf plan, still waiting on decisions)

- **collectAsStateWithLifecycle** across ~10 screens (needs the `lifecycle-runtime-compose`
  dependency decision) — DA register cross-ref: the largest BatteryO value risk of the UI set.
- Formatter `remember{}` hoisting at ~7 sites (trivial).
- Patient-list pagination/design decision (UX change).

## Related reports

- Architecture audit: [[Data Access Architecture Audit 2026-10-20]]
- Findings register: [[Data Access Findings Register 2026-10-20]]
- Phase 2 ordering: [[Phase 2 Implementation Plan 2026-10-20]]
- Audit conclusion: [[Data Access Audit Summary 2026-10-20]]
- Earlier plan: [[Performance Diagnosis and Plan 2026-10-20]]
