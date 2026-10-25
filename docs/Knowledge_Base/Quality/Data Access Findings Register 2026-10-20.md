# Data Access Findings Register — 2026-10-20

> Phase 1 findings from the 2026-10-20 data-access audit. All file references verified at
> `main@c6db012`. Severity uses the Bug Register scale (S1 data-loss/corruption shared… see
> [[Bug Register]] § Status vocabulary). Runtime measurement was not available in this
> environment — every finding marked 🔍 needs a Phase 2 measurement before/after.
>
> New findings carry `DA-*` IDs. Defects the audit re-verified but that already exist in
> [[Bug Register]] keep their `NCC-*` IDs and are cross-referenced at the bottom — they are not
> duplicated here.

## Index

| ID | Sev | Category | Title (root cause, not symptom) | Confidence | New? |
|---|---|---|---|---|---|
| DA-01 | **High (multi-device)** | Offline-first correctness | Pulls are insert/update-only: remote deletions never remove local rows; edits to remotely-deleted rows upload as silent no-ops | Confirmed code path; 🔍 live divergence | in register (cross-ref NCC-023) |
| DA-02 | Medium | Remote efficiency & scale | Every pull + conflict-check downloads full collections / full rows: no projection, no pagination, no delta cursor | Confirmed (code shape); 🔍 sizes | New |
| DA-03 | Medium | Local pull efficiency | Per-row single-row SQL inside pull loops (N+1) where a set-read is already the established pattern | Confirmed | New |
| DA-04 | Medium | Statistics scalability | Statistics/FullReport aggregate in memory over combined whole-table Room flows, recomputed on every table change | Strongly suspected; 🔍 | New |
| DA-05 | Medium | Search & scale | Patient search is mid-string LIKE across + subquery over visits — no index can serve it | Confirmed; 🔍 perf | New |
| DA-06 | Medium | Widget cost | `WidgetWorker` reads the whole `patients` table per widget refresh just to map names | Confirmed; 🔍 | New |
| DA-07 | Low | Resilience config | No explicit request timeout / retry policy on the Supabase client — SDK defaults (unverified) are load-bearing | 🔍 needs-validation | New |
| DA-08 | Low | Queue integrity | No unique key on `(entityName, entityId, operation)`: concurrent duplicate enqueue window | Confirmed (documented `shortcut:`) | New card (known comment) |
| DA-09 | Low | Dead code | `InventoryRepositoryImpl.deductStock()` and `VaccineDao.getAuditCount*` have zero callers | Confirmed | New |
| DA-10 | Medium | Retention / scale | `audit_logs` grows without bound locally and remotely; delete-guard queries scan it with wildcards | Confirmed | New |
| DA-11 | Low | Privacy hygiene | 3 log sites put patientId / email / remote id into Logcat | Confirmed | New |
| DA-12 | Low | Storage perf | SQLCipher + explicit `TRUNCATE` journal serializes reads behind sync writes; cold-start adds a verification open | Strongly suspected [S]; 🔍 | New |
| DA-13 | Informational | Documented decisions | Audit screen remote-only (offline blank); `patient-docs` staff-wide path boundary; policy intentionally matches table boundary | Confirmed | New (recorded, not a defect) |
| DA-14 | Low | Refresh policy | Manual pull-to-refresh always runs the full 13-step pipeline incl. COGS migration, regardless of screen needs | Confirmed | New (design accepted) |
| DA-15 | Low | DI boundary | Widget worker injects concrete `*RepositoryImpl` instead of feature interfaces | Confirmed | New |
| — | see [[Bug Register]] | Sync/Trigger/Room | NCC-002 (Open, 🔍 live DB), NCC-025, NCC-027–031, NCC-036, NCC-038, NCC-039 | various | existing |

## DA-01 — Cross-device deletion divergence (the one systemic correctness gap)

- **Resolution (2026-10-20, shipped)**: implemented as absence reconciliation against a
  one-shot full pull — `RemotePrune.kt:29-41` (general tables), `:45-59` (reminders'
  `serverId` identity), wired into all 13 refresh paths starting
  `PatientRepositoryImpl.kt:152-170`; queue-guarded, unit-pinned (`RemotePruneTest`, 12),
  CI-instrumented (`RemotePruneDaoTest`); commit `f28ce21`. Consequence 2 below now
  self-resolves for future uploads (a phantom edit on a device whose upstream deletion
  arrived while queued is pruned on the next successful pull) — 🔍 live two-device drill
  still open as the staged-rollout step.

- **Category**: offline-first correctness; **Severity**: High *for multi-device use*, n/a single-device.
- **Where (root cause = representation diff, one pattern, 14 sites)**: every `refresh*()` upserts
  remote rows and never deletes locals absent from the remote set. Verified at:
  `app/src/main/java/com/neochildclinic/feature/patient/data/PatientRepositoryImpl.kt:91-152`,
  `.../feature/vaccination/data/VaccinationRepositoryImpl.kt:91-141`,
  `.../feature/borrowed/data/BorrowRepositoryImpl.kt:301-322`,
  `.../feature/inventory/data/InventoryRepositoryImpl.kt:689-707`,
  `.../feature/reminder/data/ReminderRepositoryImpl.kt:465-513` (representative). A repo-wide grep
  for any prune/tombstone pattern finds none; `docs/SYNC.md:95` explicitly documents
  “Remote rows deleted by another device are not removed by refresh (insert/update only)”.
- **Call path / repro**: device A deletes patient P (transactional local delete + queued remote
  `DELETE`s, `PatientRepositoryImpl.kt:208-265`) → Supabase rows gone → device B (clinic’s second
  tablet) ran any refresh before and after; nothing B runs removes P.
- **Consequences** (shared root cause):
  1. B shows deleted patients forever — patient list, dashboard count, statistics.
  2. B editing the phantom’s visit enqueues an `UPDATE`; PostgREST update-matches-zero-rows is
     silent success, the queue row is acked and deleted (`SyncUploader.kt:294-305`), and the edit
     is lost with no visible failure.
  3. Reminders for the phantom keep firing (local, ACTIVE rows remain).
- **Confidence**: Confirmed for 1 (code + doc); Strongly suspected (Unverified, 🔍) for 2 —
  PostgREST semantics assumed, not measured.
- **Already tracked as**: NCC-023 ⚠️ “needs decision — systemic” in [[Bug Register]]; the audit
  elevates its analysis but does not replace its card.
- **Remediation options** (decision required): (a) server-side tombstones table pulled like data;
  (b) per-table “pull id list, delete locals not in set, respecting the pending-queue guard” —
  simple, no schema change beyond the pull; (c) soft-delete booleans restored server-side.
  Full analysis: [[Phase 2 Implementation Plan 2026-10-20]] Batch 1.
- **Required tests**: instrumented — deleted-remotely row vanishes after next pull; queued-edit +
  remotely-deleted row is not re-upserted; guard preserved (`PullUpsertPreservesChildrenTest`
  sibling suites are the house pattern).

## DA-02 — Full-collection downloads with no projection / pagination / delta cursor

- **Where**: all 14 pull sites (see [[Data Access Architecture Audit 2026-10-20]] § E catalog) +
  the conflict check `app/src/main/java/com/neochildclinic/core/sync/SyncUploader.kt:400-428`
  (decodes **entire rows** only to read one timestamp column).
- **Impact**: payload = a clinic’s entire collection (positions, snapshots, receipt text,
  addresses) on every screen entry, every debounced Realtime burst, every pull-to-refresh, every
  reconnect. Partial offset: server-side `is_deleted=false` filtering is applied everywhere, and
  `inventory_transactions` history + audit screen already use `range()` paging — the pattern to
  extend.
- **Confidence**: Confirmed (code); magnitude 🔍 unmeasured.
- **Remediation**: (i) project only needed columns for merge columns; (ii) `updated_at > cursor`
  incremental pulls using the existing merge guards; (iii) conflict-check `select(columns =
  listOf("id","updated_at"))`. Risks: cursor needs a monotonic, timezone-safe column and a reset
  path per table. Multi-batch with staged rollout per table.
- **Required tests**: unit — fixture rows prove decoder compatibility; instrumented — cursor skip
  vs full equivalence, guard intact.

## DA-03 — Per-row SQL inside pull loops (N+1 local)

- **Where**: `VaccinationRepositoryImpl.kt:121-126` (`patientDao.getPatientById` per visit),
  `ReminderRepositoryImpl.kt:477-492` (`getVaccinationById` + `getReminderByStableId` per remote
  reminder), `ProfileRepositoryImpl.kt:70-74` (`isUnsynced` per profile).
- **Why it stays a finding**: the codebase already fixed this class with `unsyncedIdSet`
  (`SyncQueueDao.kt:79-95`, and `PatientRepositoryImpl.kt:97-114` preload maps) — these three
  sites predate/were missed by that pass. Cost grows linearly with collection size, per refresh.
- **Remediation**: preload id sets/maps before the transaction loop (exact existing pattern).
- **Tests**: existing `BatchPullGuardEquivalenceTest` pattern extended per table.

## DA-04 — In-memory statistics aggregation over whole-table combine

- **Where**: `app/src/main/java/com/neochildclinic/feature/statistics/presentation/StatisticsViewModel.kt:57-105`
  (6 flows), `.../FullReportViewModel.kt:80-128` (4 flows + `computeReport`),
  `.../FinanceDetailsViewModel.kt:36-42` (3 flows). Every Room write to any of the feeding tables
  re-emits and re-computes the whole derived state on `Dispatchers.Default`.
- **Confidence**: Strongly suspected 🔍 (no measurement; row-count dependent).
- **Remediation**: per-tab SQL aggregates (the `FinanceDao.getTransactionsInRange*` /
  `InventoryTileCounts` pattern already exists, `data/local/dao/FinanceDao.kt:66-76`), chart
  series computed from pages, or at minimum `distinctUntilChanged` + lower recompute frequency.
- **Note**: correctness is fine (`FinanceCalculatorTest`); this is an efficiency/memory finding.

## DA-05 — Patient search LIKE scan (+ subquery over visits)

- **Where**: `app/src/main/java/com/neochildclinic/data/local/dao/PatientDao.kt:44-48` —
  `name LIKE :q OR phone ... OR patientClinicId ... OR id IN (SELECT patientId FROM patient_visits
  WHERE vaccineNames LIKE :q OR receiptNumber LIKE :q)`.
- **Impact**: full patients scan + full visits scan (the visits subquery is unindexed on
  `receiptNumber`) per query; typed-debounce behavior at the ViewModel is unverified (🔍 — check
  the search feature’s debounce before claiming UX lag).
- **Remediation**: either a narrow FTS4/FTS5 virtual table, or scope the subquery (receipt search
  only when `q` matches receipt prefix pattern). Dependency decision required (SQLite FTS on
  API 21+ is stdlib-level; Room supports `@Fuzzy`? no — raw creation statements).

## DA-06 — Widget loads the whole patients table per refresh

- **Where**: `feature/widget/data/WidgetWorker.kt:38-42`:
  `reminderRepository.getDueList().first()` + `patientRepository.allPatients.first()`
  (`associateBy { it.id }`) — evaluates the full patients Flow for one widget paint.
- **Confidence**: Confirmed (code); magnitude 🔍.
- **Remediation**: one SQL join/IN query returning `(dueRow, patientName)` pairs — WidgetDueDao
  already exists as the widget’s private table; extend its query.
- **Resolution (2026-10-20, shipped — minimal form)**: the worker no longer evaluates the
  whole patients Flow; it reads `PatientDao.getNamesByIds` (`PatientDao.kt:74-78`) for just the
  due rows' patient ids (`WidgetWorker.kt:38-44`). The two-query form beat a single SQL join on
  simplicity (one projection, no join SQL); regression risk nil — commit `f28ce21`.

## DA-07 — Supabase client has no explicit timeout/retry policy (unverified defaults)

- **Where**: `app/src/main/java/com/neochildclinic/core/di/SupabaseModule.kt:44-66` — `CIO.create()`
  engine, no `requestTimeout`, no explicit `postgrest` settings.
- **Why it matters**: queue uploads dedup and classify errors, but a hung connection (rare; e.g.
  captive portal that accepts TCP) would stall a `SingleFlight`-gated batch and, with strict
  backoff, reschedule repeatedly. supabase-kt ships a default `requestTimeout` (library-set);
  verification of the effective default against the pinned SDK version is 🔍. **resolved**:
  verified locally without any network contact — decompiled `SupabaseClientBuilder` from the
  cached 3.8.0 artifact shows a 10 s default.
- **Remediation**: set explicit `requestTimeout` (aligned with `SESSION_RESOLVE_TIMEOUT_MS`
  budgets) + document the choice. **needs-validation** ✅ applied (2026-10-20): default was
  10 s — too tight for slow-clinic heavy pulls — pinned to 60 s (`SupabaseModule.kt:53`),
  commit `511739a`.

## DA-08 — `sync_queue` lacks a unique key on (entityName, entityId, operation)

- **Decision (2026-10-20)**: deferred — the unique index needs a Room v33→34 migration whose
  dedup-and-requeue semantics (FAILED rows + twin PENDING rows after a stale `SYNCING`
  requeue) must be designed against the retry flows, not rushed inside the same batch. The
  enqueue-side `deletePendingDuplicates` guard already closes the common case; the residual
  window is the two-coroutine race this shortcut comment names
  (`SyncRepositoryImpl.kt:68-74`).

- **Where**: `app/src/main/java/com/neochildclinic/data/local/entity/SyncQueueEntity.kt:13`
  (indexes only `status`, `priority`) + documented `shortcut:` at
  `app/src/main/java/com/neochildclinic/core/sync/SyncRepositoryImpl.kt:68-74`.
- **Impact**: two concurrent saves of the same operation in one process beat the delete-first
  dedup and enqueue two rows; second upload is then an idempotent no-op (wasted payload +
  queue clutter, no corruption since upserts/delete are idempotent). Severity Low for exactly
  that reason.
- **Remediation**: Room v34 add unique index on `(entityName, entityId, operation, status)`,
  `enqueue` (`OnConflictStrategy.IGNORE`), keep the delete-first call. One migration, one test
  (concurrent enqueue → one row).

## DA-09 — Dead data-access code (two groups)

- `InventoryRepositoryImpl.deductStock()` multi-batch loop — zero callers
  (`app/src/main/java/com/neochildclinic/feature/inventory/data/InventoryRepositoryImpl.kt:399-448`;
  all call sites use `deductStockFromBatch`, verified by grep). Carries its own expired-batch
  prefilter — i.e. two divergent logic paths for the same task.
- `VaccineDao.getAuditCountForVaccine` / `getAuditCountExcludingCreation` — zero callers
  (`app/src/main/java/com/neochildclinic/data/local/dao/VaccineDao.kt:147-151`); they are also the
  unbounded `audit_logs` LIKE scans called out under DA-10, and their index requirement is the
  (differing) basis of NCC-030.
- **Remediation**: delete. (Deleting the queries also removes a growing scan surface.)

## DA-10 — `audit_logs` retention: unbounded local + remote growth

- **Where**: `app/src/main/java/com/neochildclinic/data/local/dao/AuditLogDao.kt` (no `DELETE`
  query; reads always `LIMIT`/`range` — clean reads, unbounded table). Remote table grows the
  same way (client inserts, no server retention found in tracked migrations).
- **Impact**: local LIKE scans and count queries never shrink (NCC-022-family audit rows land on
  every event); remote payload for audit screen paging grows. 🔍 sizes unmeasured.
- **Remediation decision needed**: defined retention (e.g. local 90 d, remote 1 y) implemented as
  a scheduled prune + server-side policy, or declare retention “keep all” explicitly and remove
  it from the risk list. User decision required.
- **Resolution (2026-10-25)**: local 90-day prune of synced rows
  (`app/src/main/java/com/neochildclinic/app/NeoChildApp.kt:85-93`,
  `app/src/main/java/com/neochildclinic/data/local/dao/AuditLogDao.kt:34-41`); remote 1-year
  `public.prune_audit_logs()` plus an optional `pg_cron` schedule when the extension is
  available (`supabase/migrations/20261025_audit_log_retention.sql:1`).

## DA-11 — Patient identifiers / email written to Logcat on pull failure

- **Resolution (2026-10-20, shipped)**: all four log sites cleaned — the two vaccination
  pull-failure sites no longer print remote ids or patient ids
  (`VaccinationRepositoryImpl.kt:113,120`) and the two profile sites no longer print the
  email/id (`ProfileRepositoryImpl.kt:45,63`); commit `f28ce21`.

- **Where**: `VaccinationRepositoryImpl.kt:111,119` (logs `remote.id` + `patientId`),
  `ProfileRepositoryImpl.kt:45,63` (logs email).
- **Why**: pull failures are common *offline* paths (thrown by `cloudFetch`→`Log.e` in
  `core/sync/CloudRefresh.kt:23-29` only for session failures — these additional sites log
  directly); adb/USB debugging exposes Logcat. Patient IDs + email are direct patient data under
  the project’s own logging rule (no patient/PII/tokens in logs).
- **Remediation**: replace identifiers with counts/`success` booleans behind existing
  `BuildConfig.DEBUG` logging gating (match `PatientUtils` count-based logs). Small, safe.

## DA-12 — SQLCipher database uses TRUNCATE journal + cold-start verification open

- **Where**: `app/src/main/java/com/neochildclinic/data/local/database/AppDatabase.kt:508`
  (`setJournalMode(JournalMode.TRUNCATE)` — explicit), `:454-498` (every process start opens the
  DB once READONLY to verify the passphrase before Room builds).
- **Suspected cost**: TRUNCATE blocks readers during every background sync write; WAL allows
  read-during-write, which would mainly help while a 15-min sync runs alongside UI reads [🔍].
  The verification open adds measurable cold-start latency [🔍]. None measured — report 3 adds
  both to the benchmark list. Do **not** change until measured (SQLCipher/WAL interplay needs a
  device test).

## DA-13 — Documented deliberate deviations (recorded so they are not re-flagged)

- Audit-log screens read Supabase only during network use; offline shows blank
  (`app/src/main/java/com/neochildclinic/feature/audit/data/AuditLogRepositoryImpl.kt:14-30`,
  documented in-file).
- `patient-docs` object paths are `<patient_id>/<file>` with no clinic segment; the bucket/objects
  policy is authenticated-staff-wide, matching the table policies — documented as intentional in
  `supabase/migrations/20261016_rls_index_and_stock_trigger_hardening.sql:19-24`. Any future
  multi-clinic split must revisit both the storage layout and the table policies together.
- Clinic ID lookups by `LIKE 'NEO-%'` scans are bounded by the unique index on
  `patientClinicId` + limited result (1) — acceptable.

## DA-14 — Manual refresh always runs the full pipeline (accepted design, measured cost)

- `refresh()` on 9 screens routes to the full `RefreshDataUseCase` (13 steps incl. the always-run
  COGS migration, `feature/sync/domain/RefreshDataUseCase.kt:62-129`). Entry/realtime triggers are
  already scoped; manual refresh is *intended* to be “refresh everything” (policy accepted in
  [[Performance Diagnosis and Plan 2026-10-20]]). Registered only so the Phase 2 measurement can
  compare: could manual refresh later reuse the scoped gate (explicit per-screen scoping) without
  breaking the freshness contract — flagged for a user decision, **not** a defect change by default.

## DA-15 — Widget worker injects concrete repository classes

- **Resolution (2026-10-20, shipped)**: `WidgetWorker` injects `ReminderRepository` and drops
  the concrete-patient dependency entirely (the name lookup moved into the DAO query,
  `WidgetWorker.kt:28-31`); commit `f28ce21`.

- `app/src/main/java/com/neochildclinic/feature/widget/data/WidgetWorker.kt:31-32`.
- Harmless at runtime (single implementation exists), but breaks the substitute-the-interface
  testing rule every other worker follows (e.g. `SyncWorker` composes the interface
  `SyncRepository`); for consistency with `ARCHITECTURE.md` rules, type to
  `ReminderRepository`/`PatientRepository` (both already expose the methods used).

## Re-verified existing findings (no new number — cross-references)

- **NCC-002** (Open ⚠️, 🔍 live DB required): Postgres stock-trigger UPDATE-branch arithmetic —
  re-read the exported fix migrations `20261017_stock_trigger_duplicate_and_update_arithmetic.sql`
  and `20261018_ensure_stock_trigger_installed.sql`; consistent with the register; still needs the
  live-DB confirmation note. Not re-classified here.
- **NCC-019/020/009/022/003/005/021/040**: sync fixes visible in current source at the exact
  registers’ cited lines (re-verified during the write-path audit; matches ✅/Source-verified
  status in [[Bug Register]]).
- **NCC-025/027/028/029/030/031/036/038/039**: re-confirmed open (source unchanged in audited
  paths), including `InventoryRepository.kt:67`’s orphaned multi-batch API (related to DA-09).

## Related reports

- Architecture audit: [[Data Access Architecture Audit 2026-10-20]]
- Measurement plan: [[Performance and Measurement Plan 2026-10-20]]
- Phase 2 batches: [[Phase 2 Implementation Plan 2026-10-20]]
- Summary + approvals: [[Data Access Audit Summary 2026-10-20]]
- Related: [[Bug Register]], [[Known Bugs]], [[Offline Synchronization]], [[Caching]]
