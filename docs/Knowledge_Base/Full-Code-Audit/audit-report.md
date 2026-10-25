# Full-Code Audit — Neo Child Clinic Android App

- **Date:** 2026-10-25
- **Repository:** `CyferBoy/Neo_Child_Clinic`
- **Audited commit:** `3b61789` (`main`; local untracked: `docs/Knowledge_Base/Database-Audit/`, `docs/Knowledge_Base/Full-Code-Audit/`, `supabase/migrations/20261022_fix_stock_trigger_update_arithmetic.sql`)
- **Mode:** read-only source, schema, build, and worker audit. No app code, migration, policy, production data, or CI configuration was changed. No defect was fixed.
- **Scope:** security, runtime correctness, database/sync, business integrity, performance, build/release, tests, and the Cloudflare backup worker.
- **Prior work:** the database/sync deep dive is in `docs/Knowledge_Base/Database-Audit/` at `ca4bed4`. This report re-verifies its open items against HEAD and adds full-stack findings, including a new data-loss risk in the remote-prune path.

> **Approval gate:** the audit is complete. Remediation is proposed in `docs/Knowledge_Base/Full-Code-Audit/remediation-roadmap.md`. No fix, deployment, migration, or data repair may start until the relevant batch is explicitly approved.

## Verdict

The offline-first core is real: Room is the UI source of truth, writes and queue enqueues share a transaction, uploads are dependency-ordered, Room is SQLCipher-encrypted, backups are password-encrypted, and the worker enforces backup ownership.

The highest-risk problem is the interaction between **unpaginated pulls** and the new **remote-prune** step. `RemotePrune` treats the pulled ID set as complete. Supabase returns at most 1,000 rows by default, and none of the pulls request a range. Once a table grows past the cap, a successful pull returns a prefix and the prune deletes every synced local row outside it — for patients, cascading away visits, notes, reminders and consultations. An empty `200 []` response does the same.

The stock-trigger fix is present in tracked migrations, but deployment and historical corruption are unverified. RLS still grants every active staff role full CRUD on clinical and financial tables. The release path is not exercised by CI, and signing/minification are not verified.

## Findings summary

| Severity | Count | IDs |
| --- | --- | --- |
| S1 Critical | 3 | AUD-01 (confirmed), AUD-02 (suspected, live-FK dependent), AUD-03 (unverified live/deployment risk) |
| S2 High | 7 | AUD-04 … AUD-10 |
| S3 Medium | 11 | AUD-11 … AUD-21 |
| S4 Low | 2 | AUD-22, AUD-23 |

Severity model: **S1** data loss/corruption or a security break with direct patient/financial impact; **S2** wrong data, privacy/authorization weakness, or broken offline behavior; **S3** conditional correctness/performance/release risk; **S4** hardening/hygiene. Confidence is stated per finding; a live-database-dependent claim is never marked confirmed.

---

## S1 — Critical

### AUD-01 — Remote prune can delete valid local data because pulls are not complete

- **Type:** Confirmed code defect; data loss triggers at scale or on an empty success.
- **Severity:** S1 · **Confidence:** High (code and platform default); live project row counts/max-rows unverified.
- **Evidence:** `app/src/main/java/com/neochildclinic/data/remote/PatientRemoteDataSourceImpl.kt:17` fetches all patients with a single `select` and no `range()`. The other pull paths do the same, e.g. `app/src/main/java/com/neochildclinic/feature/vaccination/data/VaccinationRepositoryImpl.kt:96`, `app/src/main/java/com/neochildclinic/feature/finance/data/FinanceRepositoryImpl.kt:384`, and `app/src/main/java/com/neochildclinic/feature/consultation/data/ConsultationRepositoryImpl.kt:207`. Each then prunes: `app/src/main/java/com/neochildclinic/feature/patient/data/PatientRepositoryImpl.kt:158`, `app/src/main/java/com/neochildclinic/feature/vaccination/data/VaccinationRepositoryImpl.kt:139`, `app/src/main/java/com/neochildclinic/feature/finance/data/FinanceRepositoryImpl.kt:443`. `RemotePrune.computeBatches` deletes every local synced ID absent from `remoteIds` (`app/src/main/java/com/neochildclinic/core/sync/RemotePrune.kt:36`).
- **Platform fact:** Supabase projects return a maximum of 1,000 rows by default unless the Project API setting is changed; `range()` is the documented pagination mechanism: <https://supabase.com/docs/reference/javascript/v1/select>.
- **Trigger A:** any pruned table larger than the configured cap. The response is the first page; the local tail is treated as remotely deleted. A patients table above the cap deletes patients and, through Room foreign keys, their visits, vaccination items, notes, consultations, reminders and todos.
- **Trigger B:** a successful empty response. RLS filters rows through `is_active_staff()` (`supabase/migrations/20260929000000_security_baseline.sql:73`); a deactivated/missing profile, a policy mistake, or a changed Supabase project yields `200 []`, not an HTTP error. `cloudRefresh` only rethrows session/HTTP failures (`app/src/main/java/com/neochildclinic/core/sync/CloudRefresh.kt:20`), so an empty success reaches the prune unchanged.
- **Impact:** silent local loss of clinical and financial history; no queue entry is created, so the loss is not propagated as an intentional delete. A backup restore may be the only recovery.
- **Fix direction:** disable pruning until the pull can prove completeness (paginate in a stable order, compare `count=exact`, or use server tombstones); never prune on an empty set when local synced rows exist; add explicit tests for >limit and empty-success responses.
- **Verification:** unit test with 1,500 local / 1,000 remote IDs; fake empty `200`; two-page pagination test; then a disposable Supabase project with >1,000 rows.

### AUD-02 — Visit deletion and stock-ledger unlink contradict each other; live FK unknown

- **Type:** Suspected defect; source contradiction confirmed, production impact depends on the deployed foreign key.
- **Severity:** S1 if `inventory_transactions.visit_id` has a restricting FK, otherwise S2 · **Confidence:** Medium.
- **Evidence:** `app/src/main/java/com/neochildclinic/feature/vaccination/data/VaccinationRepositoryImpl.kt:443` says unlinking `visit_id` is “no longer needed; visit_id stays”. The uploader still assumes the unlink exists and explains that a delete fails with `23503` otherwise: `app/src/main/java/com/neochildclinic/core/sync/SyncUploader.kt:546` and `app/src/main/java/com/neochildclinic/core/sync/SyncUploader.kt:557`. A repo-wide search finds no `clearVisitLink` definition or caller.
- **Impact:** if the FK exists with `RESTRICT`/`NO ACTION`, every visit-with-ledger deletion ends as a permanently failed sync group: locally deleted, remotely present, queue stuck. If it is `CASCADE`, deleting the visit removes the stock audit trail. Either outcome contradicts the comments and intended traceability.
- **Fix direction:** inspect `pg_constraint` on the live database first. Then either restore an explicit-null UPDATE before the visit DELETE in the same sync group, or deliberately drop/document the FK. Do not leave both code paths contradictory.
- **Verification:** constraint query; create visit + ledger + delete integration test; assert visit gone and ledger retained with null `visit_id` (or documented retention).

### AUD-03 — Stock trigger is fixed in the repo, but deployment and historical corruption are unverified

- **Type:** Unverified live/deployment risk. The arithmetic defect itself is fixed in tracked migrations at HEAD.
- **Severity:** S1 until live state and historical stock are reconciled · **Confidence:** High for repo code; Medium for production state.
- **Evidence:** the tracked fixes apply the net delta on same-batch UPDATE and reverse/apply on batch reassignment (`supabase/migrations/20261021002000_fix_stock_trigger_batch_reassignment.sql:61`). A rolled-back regression test covers insert, retry, edit, move, delete and the negative guard (`supabase/tests/stock_trigger_arithmetic.sql:1`).
- **Residual risks:** (1) the untracked `supabase/migrations/20261022_fix_stock_trigger_update_arithmetic.sql` duplicates the fix and could be applied out of order; (2) it is unknown which migrations production has; (3) the trigger may be missing or duplicated; (4) stock corrupted before the fix is not repaired by it.
- **Fix direction:** do not deploy blindly and do not apply the untracked duplicate. Verify the trigger and migration ledger, take a restorable backup, reconcile `inventory_reconciliation`, then repair history under a separate approval.
- **Verification:** trigger/migration queries in the roadmap; replay ledger per batch; assert `remaining_quantity == initial + sum(quantity)`.

---

## S2 — High

### AUD-04 — Finance pull self-heal overwrites pending edits and enqueues writes

- **Type:** Confirmed defect (DBA-03) · **Confidence:** High.
- **Evidence:** `app/src/main/java/com/neochildclinic/feature/finance/data/FinanceRepositoryImpl.kt:424` sets `shouldUpdateLocal = true` whenever `wasHealed`, even when the pending guard at `:417` returned false. It then writes the remote row as unsynced and enqueues an UPDATE from inside the pull (`:429`).
- **Impact:** an offline amount edit can be replaced by remote values; the pull also creates a write loop/ping-pong when dates keep mismatching. Finance totals become wrong.
- **Fix direction:** pending queue and `!isSynced` are authoritative; never overwrite them during a pull and never enqueue from a pull. Move healing to an explicit maintenance operation.
- **Verification:** unit test: pending local row + healed remote row → local unchanged, no queue growth.

### AUD-05 — Patient deletion queues reminders with the local Room id, not `serverId`

- **Type:** Confirmed defect (DBA-05) · **Confidence:** High.
- **Evidence:** `app/src/main/java/com/neochildclinic/feature/patient/data/PatientRepositoryImpl.kt:230` collects local ids and `:237` enqueues them as REMINDERS DELETE. The remote identity is `serverId` (`app/src/main/java/com/neochildclinic/data/local/entity/ReminderEntity.kt:38`, `:145`). The visit-delete path correctly prefers `serverId` (`app/src/main/java/com/neochildclinic/feature/vaccination/data/VaccinationRepositoryImpl.kt:464`).
- **Impact:** PostgREST deletes zero rows and returns success. Pulled reminders survive remotely, can be re-imported, and may leave the patient delete blocked by an FK. Patient-linked reminder data is not actually deleted.
- **Fix direction:** use `serverId ?: id`, route patient/visit reminder cleanup through one helper, group child deletes before the patient delete.
- **Verification:** pulled reminder with `localId != serverId`; delete patient; assert remote delete targets `serverId` and no row remains.

### AUD-06 — RLS grants full CRUD on clinical/financial tables to every active staff role

- **Type:** Confirmed policy defect (DBA-08); live policy state unverified · **Confidence:** High for migrations.
- **Evidence:** the baseline loop creates SELECT/INSERT/UPDATE/DELETE policies for 20 tables using only `is_active_staff()` (`supabase/migrations/20260929000000_security_baseline.sql:73`, `:85`). Only expenses, doctor schedules, profiles and device rows receive later carve-outs.
- **Impact:** a receptionist, nurse or inventory account can delete patients, rewrite visits/finance, adjust stock or remove reminders through PostgREST. Admin/doctor checks exist only in Compose, so they are not authorization.
- **Fix direction:** approve a role→table→operation matrix; restrict destructive clinical/financial operations to admin (and doctor where clinically required); test with role tokens against a disposable project.
- **Verification:** RLS role-matrix SQL/integration tests; direct PostgREST attempts must fail with 401/403/empty effect as designed.

### AUD-07 — Patient merge deletes the duplicate's consultations, finance and notes instead of transferring them

- **Type:** Confirmed defect (DBA-26) · **Confidence:** High.
- **Evidence:** `app/src/main/java/com/neochildclinic/feature/patient/domain/MergePatientsUseCase.kt:34` transfers only vaccinations, reminders and inventory transactions, then calls `deletePatient` at `:57`. That path deletes consultations (`app/src/main/java/com/neochildclinic/feature/patient/data/PatientRepositoryImpl.kt:261`) and the visit-delete path removes linked finance (`app/src/main/java/com/neochildclinic/feature/vaccination/data/VaccinationRepositoryImpl.kt:488`). Notes are not enumerated at all. Moved-row updates and the parent delete have no shared transaction group (`MergePatientsUseCase.kt:60`).
- **Impact:** permanent loss of clinical notes, consultations and income during deduplication; remote cascade risk if the parent delete uploads before moved children.
- **Fix direction:** enumerate every patient-owned table; transfer all children; one ordered sync group with the parent DELETE last; integrity test with a fully populated duplicate.
- **Verification:** merge a duplicate with visit+item+finance+consultation+note+todo+reminder; assert zero child rows lost and remote order is child-first.

### AUD-08 — Sync-queue dedupe is read-then-insert, not atomic

- **Type:** Confirmed defect (DBA-09) · **Confidence:** High.
- **Evidence:** `app/src/main/java/com/neochildclinic/core/sync/SyncRepositoryImpl.kt:68` deletes pending duplicates, then inserts at `:75`; the comment admits concurrent enqueues can duplicate. Room indexes only cover status and priority (`app/src/main/java/com/neochildclinic/data/local/entity/SyncQueueEntity.kt:13`).
- **Impact:** two concurrent saves of the same entity/operation can enqueue two uploads. For ledger writes this re-enters the stock trigger UPDATE path; the fixed trigger is idempotent for identical rows, but duplicates still waste requests and can surface unexpected conflicts.
- **Fix direction:** partial unique index on PENDING `(entityName, entityId, operation)` plus `INSERT OR IGNORE`/upsert; Room migration with schema export; concurrency test.
- **Verification:** two coroutines enqueue the same operation; assert one PENDING row.

### AUD-09 — Borrow return status trusts the caller's stale `remainingQuantity`

- **Type:** Confirmed defect (DBA-10) · **Confidence:** High.
- **Evidence:** the repository correctly rechecks outstanding before restocking (`app/src/main/java/com/neochildclinic/feature/borrowed/data/BorrowRepositoryImpl.kt:220`), but marks the borrow RETURNED using the caller parameter `quantity >= remainingQuantity` (`:279`).
- **Impact:** over-return is now capped, but the state transition is still wrong: stale high remaining leaves a fully returned borrow ACTIVE; stale low remaining closes it with stock still outstanding. Follow-up lists and outstanding stock are wrong.
- **Fix direction:** derive the decision from `borrow.quantity - totalReturnedIncludingThisReturn`; remove the `remainingQuantity` parameter; add a multi-device/server aggregate guard.
- **Verification:** borrow 10, return 1, return 9 with a stale dialog value of 10; assert RETURNED. Return 2 with stale value 1; assert not RETURNED while outstanding > 0.

### AUD-10 — Finance date normalization still uses the device timezone

- **Type:** Confirmed code path (DBA-04); impact depends on stored `dateGiven` formats · **Confidence:** Medium.
- **Evidence:** finance normalizes through `ZoneId.systemDefault()` (`app/src/main/java/com/neochildclinic/feature/finance/data/FinanceRepositoryImpl.kt:58` and `:399`), while the date layer explicitly anchors clinic dates to `Asia/Kolkata` (`app/src/main/java/com/neochildclinic/core/common/DateUtils.kt:26`). `parseDate` parses date-only strings in the JVM/device zone and also accepts full timestamps (`DateUtils.kt:57`).
- **Impact:** date-only values round-trip unchanged in the same device zone, so the prior “always off by one” claim is too broad. Any timestamp-formatted `dateGiven` (legacy or imported data) is reinterpreted in the device zone and can shift a day; the self-heal then rewrites the finance row and enqueues an update on every pull.
- **Fix direction:** use the IST `Date.toLocalDate()` helper everywhere for clinic dates; verify/backfill timestamp-formatted visit dates; stop heal writes during pull.
- **Verification:** timezone test matrix (IST, UTC−8, UTC+14) with date-only and timestamp inputs; assert stable dates and zero pull-side enqueues.

---

## S3 — Medium

### AUD-11 — Stock History and Audit History are online-only

- **Type:** Confirmed design gap (DBA-07 / DBA-15) · **Confidence:** High.
- **Evidence:** Stock History forces `remoteOnly = true` (`app/src/main/java/com/neochildclinic/feature/inventory/presentation/StockHistoryViewModel.kt:137`, `:175`); the ledger is never pulled into Room. Full Audit History is documented and implemented as remote-only (`app/src/main/java/com/neochildclinic/feature/audit/presentation/FullAuditLogViewModel.kt:14`).
- **Impact:** offline or on a fresh install these screens fail/empty despite local batches and staged audit rows. This breaks the offline-first promise on two history surfaces.
- **Fix direction:** pull the inventory ledger (paged/cursor) and read from Room, or label the screens online-only with a local fallback; add local paging for audit logs.

### AUD-12 — Widget configuration leaks its activity scope and can call `setContent` after destruction

- **Type:** Confirmed lifecycle defect (introduced in `3b61789`) · **Confidence:** High.
- **Evidence:** `MainScope().launch` is used at `app/src/main/java/com/neochildclinic/feature/widget/presentation/VaccineWidgetConfigurationActivity.kt:44` and `:72`; neither scope is retained or cancelled. The first coroutine reads Glance state asynchronously and then calls `setContent` at `:53`; rotation/finish before completion can render into a destroyed activity. The second holds the activity while writing preferences.
- **Impact:** activity leak and a possible crash/ignored result during rotation or quick dismissal. The feature is also uncompiled locally, so CI/build verification is required.
- **Fix direction:** use `lifecycleScope` (or `repeatOnLifecycle`) for one-shot work, check `isFinishing`/`isDestroyed`, and avoid calling `setContent` from a coroutine after an async read; render loading state first.

### AUD-13 — “Today” and several business dates still use the device timezone

- **Type:** Confirmed cross-cutting risk (DBA-14) · **Confidence:** High.
- **Evidence:** 21 `LocalDate.now()` uses and 17 `ZoneId.systemDefault()` uses remain. Examples: dashboard date (`app/src/main/java/com/neochildclinic/feature/dashboard/presentation/DashboardViewModel.kt:121`), daily notification (`app/src/main/java/com/neochildclinic/core/notification/DailySummaryWorker.kt:29`), borrow return date (`app/src/main/java/com/neochildclinic/feature/borrowed/data/BorrowRepositoryImpl.kt:211`).
- **Impact:** traveling staff or a device with the wrong zone see the wrong day on the dashboard, notifications, stock filters and returns; finance dates can shift where timestamps are involved.
- **Fix direction:** one `clinicToday()`/`clinicZone()` API, adopted in all business paths; device zone only for explicitly local timestamps.

### AUD-14 — Editing a vaccination overwrites `created_at`

- **Type:** Confirmed defect (DBA-13) · **Confidence:** High.
- **Evidence:** `app/src/main/java/com/neochildclinic/data/local/entity/VaccinationEntity.kt:130` sets `createdAt = timestamp`, where `timestamp` is the edit/update time. The edit mapper does not carry the persisted creation time forward.
- **Impact:** audit lineage and any report based on creation time are wrong after every edit. Receipt numbering is unaffected.
- **Fix direction:** preserve `existing.createdAt` in the edit path; add a Room/repository test asserting it is unchanged.

### AUD-15 — Patient clinic-ID conflict branch bypasses the pending-edit guard

- **Type:** Confirmed defect (DBA-23) · **Confidence:** Medium.
- **Evidence:** in `app/src/main/java/com/neochildclinic/feature/patient/data/PatientRepositoryImpl.kt:119`, a clinic-ID collision upserts the remote row and `continue`s at `:131`, before the pending/`isSynced` guard at `:135`.
- **Impact:** a local patient with a pending edit can be overwritten during a collision, and the queued edit then uploads the overwritten values.
- **Fix direction:** run the pending guard first; preserve pending fields or defer the remote upsert on collision.

### AUD-16 — Clinic IDs are generated locally and can collide across devices

- **Type:** Confirmed design risk (DBA-25) · **Confidence:** High for generator; collision frequency data-dependent.
- **Evidence:** `PatientIdGenerator` computes `NEO-<max+1>` from the local table only (`app/src/main/java/com/neochildclinic/feature/patient/data/PatientIdGenerator.kt:15`, loop at `:29`). The server has a unique clinic-ID index.
- **Impact:** two offline devices can mint the same ID. The first uploads; the second gets `23505`, the queue item fails, and the next pull renames the local row with a `-CONFLICT-` suffix or leaves it unsynced.
- **Fix direction:** issue IDs server-side (sequence/RPC) or use a random suffix; catch `23505` and retry; run any legacy migration once, coordinated.

### AUD-17 — Consultation pull inserts children without checking the parent

- **Type:** Confirmed defect for consultations (DBA-12); the same pattern was previously reported for personal reminders · **Confidence:** High for consultations.
- **Evidence:** `app/src/main/java/com/neochildclinic/feature/consultation/data/ConsultationRepositoryImpl.kt:209` inserts a remote consultation whenever its id is not pending, with no patient/visit existence check.
- **Impact:** a remote consultation whose parent is absent locally triggers a Room FK failure that fails the whole refresh step, repeatedly, until the parent arrives.
- **Fix direction:** skip and log parentless rows (the reminder pull already does this) or defer them until the parent is local.

### AUD-18 — Upload-only entities treat a no-op “download” as conflict resolution

- **Type:** Confirmed defect (DBA-18) · **Confidence:** High; low reachability.
- **Evidence:** `download` defaults to a no-op (`app/src/main/java/com/neochildclinic/core/sync/SyncUploader.kt:47`); TRANSACTION/INVENTORY_TRANSACTION omit it (`:169`). The remote-newer branch calls `descriptor.download` and returns (`:525`).
- **Impact:** if a remote ledger row ever appears newer, local state is not refreshed and the queue item is treated as resolved; silent divergence.
- **Fix direction:** implement a downloader or make append-only entities skip the remote-newer branch and upload local; never treat a no-op as success.

### AUD-19 — Reminder uniqueness keys differ between Room and Postgres

- **Type:** Suspected defect (DBA-19) · **Confidence:** Medium; reachability not proven.
- **Evidence:** Room's unique index includes `dueDate` (`app/src/main/java/com/neochildclinic/data/local/entity/ReminderEntity.kt:26`); the server index omits it (`supabase/migrations/20261016_rls_index_and_stock_trigger_hardening.sql:104`).
- **Impact:** two locally valid reminders (same patient/visit/vaccine/type, different due date) map to one server key; the second upload fails with `23505` permanently.
- **Fix direction:** align the keys (dedupe first), then migrate Room/server consistently; add a SQL unique-violation test.

### AUD-20 — Query and payload performance risks remain

- **Type:** Confirmed static risks (DBA-20/21/22); not measured · **Confidence:** High.
- **Evidence:** pulls and aggregate Flows remain unbounded (see AUD-01). Finance orders by `COALESCE(transaction_date, substr(timestamp, 1, 10))` (`app/src/main/java/com/neochildclinic/data/local/dao/FinanceDao.kt:15`), which defeats the plain `transaction_date` index. Patient search uses leading wildcards plus a visit subquery (`app/src/main/java/com/neochildclinic/data/local/dao/PatientDao.kt:46`). The sync queue has no `(entityName, entityId, status)` index (`app/src/main/java/com/neochildclinic/data/local/entity/SyncQueueEntity.kt:13`). 85 `collectAsState()` sites were counted; most are acceptable, but none are lifecycle-aware.
- **Impact:** startup/pull time and memory grow with total clinic history; finance/search can become janky on large datasets; a large failed queue scans repeatedly.
- **Fix direction:** cursor/paged pulls (required for AUD-01), expression index or normalized date column, FTS/prefix-phone search, queue lookup index after measuring, and `collectAsStateWithLifecycle` at high-churn screens.

### AUD-21 — Release hardening is not exercised by CI

- **Type:** Confirmed process/release gap · **Confidence:** High.
- **Evidence:** release enables R8/shrink (`app/build.gradle.kts:74`), but has no signing config, and lint disables release checks (`app/build.gradle.kts:90`). Android CI only runs `testDebugUnitTest`, `assembleDebug`, and `lintDebug` (`.github/workflows/android-ci.yml:85`). No workflow invokes `assembleRelease`/`bundleRelease` or `lintRelease`. `google-services.json` is intentionally generated only for the debug CI build (`.github/workflows/android-ci.yml:51`). `proguard-rules.pro` contains no explicit keeps.
- **Impact:** R8, serialization, signing and release lint can break without CI noticing; the update flow depends on a consistently signed APK. The release artifact pipeline is not reproducible from the repo.
- **Fix direction:** add a release-build job with ephemeral signing secrets, `lintRelease`, R8 mapping upload and an install smoke test; review targetSdk against the current distribution policy; add dependency/security scanning.

---

## S4 — Low / hardening

### AUD-22 — Backup worker hardening opportunities

- **Type:** Confirmed hardening gap; no direct breach path found · **Confidence:** High.
- **Evidence:** CORS is `*` (`cloudflare/backup-worker/src/index.ts:62`). JWT verification checks signature, `exp`, audience and subject (`cloudflare/backup-worker/src/jwt.ts:173`), but not `iss`, and does not check that the profile is still active. Ownership is correctly derived from the stored `account_id` (`cloudflare/backup-worker/src/index.ts:44`), and backups are client-side encrypted.
- **Impact:** low: a valid token is still required, and a user can only reach their own backups. Wildcard CORS permits browser-origin requests with a stolen token; a deactivated user keeps access until token expiry.
- **Fix direction:** allowlist the app origin(s) if browser clients are supported, validate `iss`, optionally require an active-staff check or short token lifetime, and refresh JWKS on an unknown `kid`.

### AUD-23 — Audit logger still allows fire-and-forget writes outside transactions

- **Type:** Confirmed design defect (DBA-16) · **Confidence:** High.
- **Evidence:** `AuditLogger` owns a separate `CoroutineScope` (`app/src/main/java/com/neochildclinic/core/logger/AuditLogger.kt:24`) and `log()` launches independently (`:35`), while the suspend `recordLog` exists for transactional use (`:54`). The class comment admits the row can survive a rollback. 12 `auditLogger.log()` callers remain versus 25 `recordLog()` callers.
- **Impact:** an audit entry can claim an action that never committed, or arrive before the business row. Clinical/financial audit integrity is weakened.
- **Fix direction:** require `recordLog` inside the caller's transaction or call it only after commit; remove or rename `log()` to make the semantics explicit.

---

## Security framework mapping (MASVS) and MASTG gap

This is a static mapping, not a certification. No dynamic MASTG test (emulator, Frida, instrumentation, network interception) was run.

| MASVS area | Static verdict | Evidence and caveat |
| --- | --- | --- |
| STORAGE | Strong | SQLCipher-encrypted Room (`app/src/main/java/com/neochildclinic/data/local/database/AppDatabase.kt:500`), `allowBackup=false` (`app/src/main/AndroidManifest.xml:22`), no world-readable database. |
| CRYPTO | Strong | PBKDF2 210k + AES-256-GCM backup format (`app/src/main/java/com/neochildclinic/data/backup/BackupCrypto.kt:44`); Keystore-backed DB passphrase. |
| AUTH | Partial | Supabase JWT + `is_active_staff()` RLS, but every active role has full CRUD (AUD-06); worker does not validate `iss` or live active status (AUD-22). |
| NETWORK | Strong | HTTPS-only update URL and API usage (`app/src/main/java/com/neochildclinic/feature/update/data/AppUpdateManager.kt:261`); targetSdk 35 blocks cleartext by default; no certificate pinning (not treated as a defect). |
| PLATFORM | Partial | Exported `MainActivity` deep link and widget configuration are justified but externally reachable; `PackageInstaller` is restricted to the app's own package (`AppUpdateManager.kt:406`). |
| RESILIENCE | Partial | R8/shrink is enabled (`app/build.gradle.kts:74`) but release signing/lint/CI is unverified (AUD-21); no anti-tamper or root detection (not required for this threat model). |
| PRIVACY | Partial | Local-first design and private backups, but audit history is remote-only (AUD-11) and PII-bearing logs/audit records need a retention review. |

## Kotlin/coroutines and secret scan

- **Concurrency/lifecycle:** 0 `GlobalScope`, 0 `runBlocking`, 0 `allowMainThreadQueries`; 2 `MainScope()` calls, both the widget issue in AUD-12; `collectAsState()` appears 85 times (non-lifecycle but mostly compositional). 19 `lateinit` and 39 `!!` sites remain review targets; sampled high-risk sites were guarded, and no additional confirmed NPE was found beyond AUD-12.
- **Exception handling:** 108 broad `catch (e: Exception)` sites. `cloudRefresh` catches `Exception` (`app/src/main/java/com/neochildclinic/core/sync/CloudRefresh.kt:22`). It rethrows for most pulls and for session failures; the `ExpenseRepositoryImpl` default can swallow a cancellation, which is a low-risk coroutine smell rather than a confirmed data bug.
- **Secrets/artifacts:** targeted scan found no live service-role keys, private keys, keystores, `local.properties`, `google-services.json`, or `.env*` files; only a deliberately fake AWS key in test fixtures. `.env.*`, `local.properties`, keystores and Firebase config are gitignored; CI generates the debug Firebase file from a secret.

## Positive controls verified (do not regress)

| Control | Evidence |
| --- | --- |
| Encrypted Room (SQLCipher, Keystore passphrase, fail-closed) | `app/src/main/java/com/neochildclinic/data/local/database/AppDatabase.kt:500` |
| Password-encrypted backups: PBKDF2 210k, AES-256-GCM, bounded header values | `app/src/main/java/com/neochildclinic/data/backup/BackupCrypto.kt:44` |
| Minimal permissions and backups disabled | `app/src/main/AndroidManifest.xml:5`, `:22` |
| Self-update requires HTTPS, verifies SHA-256 when GitHub supplies a digest, installs via PackageInstaller for its own package | `app/src/main/java/com/neochildclinic/feature/update/data/AppUpdateManager.kt:261`, `:406`, `:445` |
| Worker verifies Supabase JWT and enforces per-account backup ownership | `cloudflare/backup-worker/src/jwt.ts:146`, `cloudflare/backup-worker/src/index.ts:44` |
| Writes and outbox enqueues share a Room transaction; upload order is dependency-aware | `app/src/test/java/com/neochildclinic/core/sync/SyncOrderingTest.kt:1` |
| Remote-delete propagation exists for the main synced tables (subject to AUD-01) | `app/src/main/java/com/neochildclinic/feature/patient/data/PatientRepositoryImpl.kt:158` |
| Migration/schema tests and 59 test files (48 unit, 11 instrumented) | `app/src/test/java/com/neochildclinic/data/local/database/AppDatabaseMigrationTest.kt:1` |
| CI runs unit tests, debug build, debug lint, instrumented tests, CodeQL and worker tests | `.github/workflows/android-ci.yml:85` |

## Coverage and evidence limits

- **Codebase size:** 413 Kotlin files, 54,857 LOC, 28 SQL migrations, 48 unit test files, 11 instrumented test files.
- **Read in depth:** sync/upload/prune paths, pull repositories, finance, patient deletion/merge, reminders, borrow, statistics date utilities, manifest/build/CI, backup crypto, update manager, worker JWT/ownership, widget configuration, key DAOs.
- **Not read line-by-line:** every Compose screen and every ViewModel. UI findings are targeted, not exhaustive.
- **Detekt 1.23.8 (default config, no project config):** 2,751 weighted issues; top rules: MaxLineLength 732, MagicNumber 626, WildcardImport 431, FunctionNaming 256, LongMethod 113, LongParameterList 111, TooGenericExceptionCaught 110, CyclomaticComplexMethod 55. These are triage leads, not all defects. Detekt is not wired into Gradle.
- **Worker tests:** `npm ci` fails in this Android/Termux environment because `workerd` rejects `android arm64 LE`; CI runs them on Node 24. Not a code failure and not run locally.
- **Android build/tests:** **not run** — no Android SDK/`ANDROID_HOME`, no Gradle CLI, JDK 25 versus the project's Java 17 target, no Postgres. Therefore no compile, unit, lint, instrumented or migration test result is claimed.
- **Live data:** no Supabase, Postgres or production access. No SQL was executed. All live-state claims are marked unverified.

## Relationship to the prior Database-Audit

- `DBA-01` stock trigger: fixed in tracked migrations; live deployment and historical repair remain open (AUD-03).
- `DBA-06` remote deletions: the new `RemotePrune` partially fixes convergence, but its completeness assumption creates AUD-01, a worse failure mode at scale. Treat AUD-01 as the current blocker for that remediation.
- `DBA-02`, `DBA-03`, `DBA-04`, `DBA-05`, `DBA-07`, `DBA-08`, `DBA-09`, `DBA-10`, `DBA-12`–`DBA-26`: re-verified at HEAD and carried forward as AUD-02, AUD-04–AUD-19; statuses are unchanged unless stated above.
- The statistics filtering fixes landed after `ca4bed4`; this audit did not find a new statistics defect, but it did not execute the tests either. The date/timezone findings still apply.

## Unverified live-database properties

These must be answered read-only before remediation is designed:

1. Supabase Project API `max rows` setting (default 1,000) and actual row counts for every pruned table.
2. Whether `inventory_transactions.visit_id` has a foreign key and its `ON DELETE` action.
3. Whether exactly one stock trigger is installed, and which migrations the production ledger contains.
4. Live RLS policies/grants versus the migrations, including any out-of-band policies.
5. Whether historical `remaining_quantity` reconciles with the sum of ledger rows.
6. Counts of orphaned reminders, consultations and ledger rows.
7. Whether the published APK and the current release are signed with the same key (self-update precondition).

The read-only queries and the staged remediation are in `docs/Knowledge_Base/Full-Code-Audit/remediation-roadmap.md`.

## Approval gate

No code, migration, policy, build or production change was made. The next step is approval of the remediation batches and, before any data-changing work, a restorable backup and the Phase 0 live verification.
