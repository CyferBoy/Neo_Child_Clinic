# Full-Code Audit — Remediation Roadmap

- **Status:** Proposed only. Nothing in this document has been implemented, deployed, or run against production.
- **Companion report:** `docs/Knowledge_Base/Full-Code-Audit/audit-report.md`
- **Baseline:** `3b61789`; findings AUD-01 … AUD-23.
- **Hard rule:** each phase is a separate approval gate. Do not bundle data-changing migrations, RLS changes, or production repair into a code fix. No production query in this document may be run before the read-only/no-write gate is approved.

## Ordering principles

1. **Stop data loss before improving anything else.** AUD-01 and AUD-02 are the only blockers that can destroy local clinical history.
2. **Prove live state before touching the database.** The stock trigger, FK, RLS and row-count questions cannot be answered from source alone.
3. **Backup first.** A restorable backup of both the local device database and the Supabase project is a precondition for any migration or repair.
4. **One root-cause fix per shared path.** Fix refresh/prune centrally, not per screen or per repository.
5. **A fix is not done without a test that fails on the old behavior.** Prefer one small unit test plus one integration/device test; for SQL, a rolled-back transaction test.
6. **Do not deploy forward-only database changes without a written rollback.** Room downgrades are not supported; RLS and trigger changes need an explicit rollback SQL or a verified restore.

## Phase 0 — Read-only verification and safety net

**Goal:** answer the live-state questions and make recovery possible. No app or database writes.

**Effort:** S. **Blocks:** all later phases.

### 0.1 Snapshot and recovery

- Verify the last local export/restore on a spare device (`.nccb` password flow). A backup that has never been restored is a hope, not a backup.
- Verify the Supabase project backup/PITR restore path and retention. Do not test a restore against production.
- Record the installed APK signing certificate and the latest release artifact certificate (`apksigner verify --print-certs`); the self-update path depends on the same key.
- Snapshot the current Maven/NPM lockfiles and the `main` commit used for any build.

### 0.2 Row counts vs the API cap

Run read-only, one transaction with `SET TRANSACTION READ ONLY` where supported:

```sql
select 'patients' as t, count(*) as n from public.patients
union all select 'patient_visits', count(*) from public.patient_visits
union all select 'vaccination_items', count(*) from public.vaccination_items
union all select 'finance_transactions', count(*) from public.finance_transactions
union all select 'consultations', count(*) from public.consultations
union all select 'reminders', count(*) from public.reminders
union all select 'patient_notes', count(*) from public.patient_notes
union all select 'personal_vaccine_reminders', count(*) from public.personal_vaccine_reminders
union all select 'waste_records', count(*) from public.waste_records
union all select 'borrow_records', count(*) from public.borrow_records
union all select 'borrow_returns', count(*) from public.borrow_returns
union all select 'vaccine_batches', count(*) from public.vaccine_batches
union all select 'vaccines', count(*) from public.vaccines;
```

Also read the **Project Settings → API → Max rows** value. The platform default is 1,000 (<https://supabase.com/docs/reference/javascript/v1/select>). Any table at or above that value is exposed to AUD-01 until the prune guard/pagination ships.

### 0.3 FK, trigger, migration ledger, RLS

```sql
-- Visit FK: name, delete action and full definition
select conname, confdeltype, pg_get_constraintdef(oid)
from pg_constraint
where conrelid = 'public.inventory_transactions'::regclass and contype = 'f';

-- Stock triggers on the batch table (must be exactly one)
select tgname, tgenabled, pg_get_triggerdef(oid)
from pg_trigger
where tgrelid = 'public.vaccine_batches'::regclass and not tgisinternal;

-- Applied migrations (column names vary by Supabase version; inspect first)
select * from supabase_migrations.schema_migrations
order by version desc limit 10;

-- Live policies (compare with the migrations; look for out-of-band grants)
select tablename, policyname, cmd, roles, qual, with_check
from pg_policies
where schemaname = 'public'
order by tablename, policyname;

-- RLS enabled per table
select relname, relrowsecurity, relforcerowsecurity
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind = 'r'
order by relname;
```

Decisions from this gate:

- FK present with `RESTRICT`/`NO ACTION` → AUD-02 is S1; restore the unlink before any visit delete test.
- FK absent or `CASCADE` → document the chosen retention policy; still resolve the contradictory comments.
- Trigger count ≠ 1 → do not run stock tests or repair until corrected.
- Migration ledger missing the 2026-10-21 fixes → production has no stock fix regardless of the repo.

### 0.4 Stock and orphan reconciliation

```sql
-- Only if the diagnostic view is deployed (20261022_inventory_reconciliation_view.sql)
select * from public.inventory_reconciliation
order by abs(delta) desc nulls last
limit 100;

-- Remote orphans that affect delete/refresh behavior
select count(*) as orphan_reminders
from public.reminders r
left join public.patients p on p.id = r.patient_id
where p.id is null;

select count(*) as orphan_consultations
from public.consultations c
left join public.patients p on p.id = c.patient_id
where p.id is null;

select count(*) as orphan_ledger_links
from public.inventory_transactions t
left join public.patient_visits v on v.id = t.visit_id
where t.visit_id is not null and v.id is null;
```

Record the exact query text, timestamp and result set in the audit folder. Do not repair anything in this phase.

### 0.5 Approval artifact

A short signed-off checklist: backup restore verified, row counts recorded, FK/trigger/migration/RLS results recorded, owner decision on the visit-ledger retention policy, and the approved Phase 1 scope.

## Phase 1 — Stop data loss (AUD-01, AUD-02)

**Goal:** make remote pruning safe and make visit deletion deterministic. This is the only critical-code phase.

**Effort:** M–L. **Risk:** high if done partially; do not ship a pagination fix without the prune guard, or a prune guard without tests for partial pages.

### 1.1 Prune safety (AUD-01)

Recommended order:

1. **Add a hard safety guard first.** A pull result must prove completeness before `RemotePrune` runs. For example, the fetch returns both `ids` and a `complete` flag (or a server count); pruning is skipped when the flag is false. Never prune when `localSyncedCount > 0 && remoteIds.isEmpty()`.
2. **Paginate every pruned pull.** Use a stable order (`updated_at, id` or the table's immutable id) and `range(offset, offset + pageSize - 1)` with `pageSize <= configured max rows`; loop until a short page. Pause/abort on any error; do not combine pages from different auth/session states. This applies to patients, visits, finance, consultations, reminders, waste, expenses, borrow records/returns, todos, personal reminders, profiles, doctor schedules and inventory/catalog tables that have a prune call.
3. **Only then re-enable pruning.** Treat tombstones or a full-sync marker as the long-term design; ID-diff pruning is acceptable only when the pull is provably complete.
4. **Do not rely on `select` + `decodeList` to tell you it was truncated.** The response is a valid `200` with a prefix.

Acceptance tests:

- 1,500 local / 1,000 remote → either all pages fetched before prune, or no prune; never delete the 500 tail.
- Empty `200 []` with local synced rows → no deletion.
- Page 2 fails → no deletion.
- Genuine remote deletion (two-device scenario) → local row still pruned after a complete pull.
- Patient cascade test: deleting a patient removes only that patient's children.
- Pending local edit is never pruned.

### 1.2 Visit deletion and ledger link (AUD-02)

1. Run the FK query from Phase 0.3.
2. If a restricting FK exists: restore an explicit-null UPDATE of `inventory_transactions.visit_id` before the visit DELETE, inside the same sync group/transaction, and keep the ledger row. The uploader already knows how to serialize explicit nulls (`SyncUploader.kt:564`).
3. If no FK exists: choose and document whether the ledger keeps a dangling `visit_id` (traceability) or is nulled. Remove the contradictory comments and add a test either way.
4. Verify delete ordering with `transactionGroupId`; the parent DELETE must not upload before the unlink.

Acceptance test: create visit + ledger → delete visit → local visit gone, ledger retained with the chosen link value, remote group succeeds, no FAILED queue item.

### 1.3 Rollout

- Ship the prune guard/pagination as one PR with unit tests; ship the visit-link change separately if the FK answer is uncertain.
- Use a feature flag for pruning if the team wants a one-release observation window. Default it off if the flag cannot be tested on a real device.
- Do not deploy the untracked `supabase/migrations/20261022_fix_stock_trigger_update_arithmetic.sql`.

## Phase 2 — Clinical and financial correctness

**Goal:** fix wrong data and silent loss that are not caught by Phase 1.

**Effort:** L. Split into independently reviewable PRs.

### 2A Finance and reminders (AUD-04, AUD-05, AUD-10)

- Finance pull: pending/`!isSynced` rows are immutable during pull; remove enqueueing from the pull; move date healing to an explicit maintenance task or a verified migration.
- Finance dates: use the IST helper (`DateUtils.kt:26`) everywhere; add a timezone matrix test with date-only and timestamp inputs; decide whether timestamp-formatted visit dates need a backfill.
- Patient reminder deletion: use `serverId ?: id`; share one child-cleanup helper between patient and visit deletion; group deletes before the parent and test pulled reminders where `localId != serverId`.

### 2B Merge, borrow, queue, IDs (AUD-07, AUD-08, AUD-09, AUD-15, AUD-16)

- Merge: enumerate every patient-owned table, transfer all children, one ordered `transactionGroupId`, parent DELETE last. Add a full-duplicate integration test.
- Borrow status: derive RETURNED from the recomputed outstanding after including this return; deprecate the `remainingQuantity` parameter; add the two-device/server guard if the product needs it.
- Queue: Room migration 34 with a partial unique index on PENDING `(entityName, entityId, operation)` and an insert-or-ignore enqueue; export the schema and add a concurrency test. Measure queue size before adding the lookup index (AUD-20).
- Patient conflict: apply the pending guard before the clinic-ID collision branch; test a pending row with a colliding clinic ID.
- Clinic ID: prefer server-issued IDs or a random suffix; catch `23505` and retry; run any legacy bulk migration once, coordinated. This can be deferred behind a product decision, but must not be “fixed” by a local uniqueness loop alone.

### 2C Visit, reminder keys, orphan pulls (AUD-14, AUD-17, AUD-19)

- Preserve `createdAt` on vaccination edit; test.
- Add parent-existence guards to consultation and personal-reminder pulls; skip and log, retry next pull.
- Align Room/Postgres reminder uniqueness after de-duplicating existing rows; add a SQL unique-violation test.

### 2D Date discipline (AUD-13)

Adopt `clinicToday()`/`clinicZone()` in dashboard, notifications, stock filters, returns and finance. Add a device-timezone test for the daily worker and dashboard. Leaving this to the end is acceptable only if the clinic does not travel across zones; the code should still be consistent.

## Phase 3 — Authorization and worker security (AUD-06, AUD-22)

**Goal:** move role boundaries from Compose into PostgreSQL and harden the backup worker.

**Effort:** M–L. **Gate:** the role→table→operation matrix must be approved by the product owner before writing policies.

### 3.1 RLS role matrix

Proposed starting point, to be confirmed:

| Table group | Read | Write | Delete |
| --- | --- | --- | --- |
| Patients, visits, notes, vaccination items, reminders | All active staff | Admin/doctor/receptionist as approved | Admin only (or admin/doctor) |
| Finance transactions, expenses | Admin/doctor | Admin/doctor | Admin only |
| Vaccines, batches, inventory ledger | All active staff | Admin/inventory manager | Admin only |
| Consultations/todos | All active staff | Admin/doctor/receptionist as approved | Admin/doctor |
| Audit logs | Active staff | Insert only | Denied |
| Profiles/devices | Self + admin as today | Self/admin as today | Denied/self |

Implementation notes:

- Keep the `SECURITY DEFINER` receipt/stock/rate-limit functions working; test them as `service_role` and as staff.
- Do not broaden `is_active_staff()` or grant `anon`.
- Preserve append-only audit logs and the private `patient-docs` bucket; consider path scoping by patient.
- Write policies as a new migration, test on a disposable project with JWTs for each role, and keep a rollback script.
- The UI gates can remain as UX, but must not be the only check.

### 3.2 Worker

- Restrict CORS to the actual client origin(s) if browser clients are supported; otherwise remove browser support and keep the wildcard explicit.
- Validate `iss` in addition to signature/exp/audience/subject; decide whether deactivated profiles must be rejected immediately (short token lifetime or an active-staff call).
- Refresh JWKS on an unknown `kid`; bound the cache and fail closed.
- Add tests for wrong issuer, expired token, cross-account backup id, and CORS preflight.
- Keep the current ownership check (`account_id` from the stored row) and client-side encryption.

## Phase 4 — Offline behavior and performance (AUD-11, AUD-18, AUD-20, AUD-23)

**Goal:** make history screens honest and keep large clinics usable.

- Stock History: either pull the ledger into Room with a paged sync or label it “online only” and show a local batch/movement fallback. Do not silently show an empty list on network failure.
- Audit History: page from the local staging table first, or cache remote pages; keep the remote-only label if that is a deliberate product decision.
- Upload-only entities: implement a downloader or skip the remote-newer branch for append-only rows; never complete a conflict on a no-op.
- Audit logger: require transactional `recordLog` or call after commit; remove the fire-and-forget `log()` path where audit integrity matters.
- Query work, after measuring with `EXPLAIN QUERY PLAN`: expression index/normalized `transaction_date`, FTS or prefix-phone search, sync-queue lookup index, lifecycle-aware collection on high-churn screens.
- Widget configuration: replace `MainScope()` with `lifecycleScope`, guard destroy/rotation, render a loading state instead of calling `setContent` after an async read.

## Phase 5 — Release and quality engineering (AUD-21)

**Goal:** make the shipped artifact reproducible, signed, linted and recoverable.

- Add a release CI job: `:app:assembleRelease` and `:app:bundleRelease` with secrets-based signing, `:app:lintRelease`, R8 mapping upload, and an install/launch smoke test on an emulator/device.
- Generate the real `google-services.json` in the release job from a secret; never commit it.
- Verify the update flow against the signed artifact: same certificate, versionCode strictly greater, SHA-256 digest present, PackageInstaller path.
- Review `targetSdk` against the current Play policy if the app is distributed there; self-distribution has different requirements.
- Add dependency/security scanning (Dependabot or equivalent) and a dependency-update policy.
- Adopt Detekt with a committed config and a baseline/ratchet; do not dump 2,751 default-config issues into the build. Ktlint is optional and style-only; it was not installed.
- Add a coverage signal (not necessarily a hard 80% gate) and keep the instrumented CI job on API 34 plus at least one current API level.
- Add a migration dry-run job against a disposable Postgres for `supabase/tests/*.sql`.

## Verification matrix

| Finding | Primary test | Acceptance evidence |
| --- | --- | --- |
| AUD-01 | Unit: 1,500/1,000 and empty-success; integration: two-device delete | No over-delete; genuine deletes still converge |
| AUD-02 | SQL FK + visit/ledger delete integration | Group succeeds; ledger retained per policy |
| AUD-03 | Read-only trigger/migration query; ledger replay | Exactly one trigger; reconciliation delta zero or documented |
| AUD-04 | Pending finance row + healed remote | Local unchanged; no new queue row |
| AUD-05 | Pulled reminder with distinct server id | Remote delete targets `serverId` |
| AUD-06 | RLS role-token matrix | Unauthorized operations rejected |
| AUD-07 | Fully populated duplicate merge | Zero child rows lost; ordered group |
| AUD-08 | Concurrent enqueue | One PENDING row |
| AUD-09 | Stale remaining-quantity return | Status matches computed outstanding |
| AUD-10 | Timezone matrix | Stable ISO date; no pull-side enqueue |
| AUD-11 | Offline/fresh install | Honest empty/error or local data |
| AUD-12 | Rotation/dismiss during config | No crash/leak; result consistent |
| AUD-13 | Device zone UTC−8/UTC+14 | Clinic “today” unchanged |
| AUD-14 | Edit visit | `createdAt` unchanged |
| AUD-15 | Pending + clinic-ID collision | Local edit survives |
| AUD-16 | Two offline creates | No permanent 23505; retry succeeds |
| AUD-17 | Dangling remote child | Refresh step succeeds; row deferred |
| AUD-18 | Remote-newer ledger conflict | No silent no-op completion |
| AUD-19 | Duplicate reminder key/different dueDate | Local/server agree |
| AUD-20 | `EXPLAIN QUERY PLAN` / payload benchmark | Documented improvement, not a guess |
| AUD-21 | Release CI job | Signed APK installs and updates |
| AUD-22 | Worker JWT/CORS tests | Wrong issuer/expiry/cross-account rejected |
| AUD-23 | Failed transaction + audit log | No orphan audit row |

## Rollback notes

- **Prune guard/pagination:** code-only; rollback by reverting the PR. Keep pruning disabled by default until the fix is verified.
- **Room migration 34 (queue index):** forward-only. Export the schema, test `MigrationTestHelper`, and keep a device DB backup before release.
- **Stock trigger:** take a restorable backup; keep the previous function definition; do not repair history in the same transaction as the function replacement.
- **RLS:** ship as a new migration with a matching rollback migration; test with role tokens on a disposable project; do not edit production policies ad hoc.
- **Worker:** deploy with `wrangler` only after the test suite passes in CI; keep the previous version pinned for rollback.

## Coverage gaps in this audit

- No Android SDK, Gradle CLI, JDK 17, emulator, Postgres or psql in this environment; no Gradle/lint/unit/instrumented/SQL test was executed. The recent widget change (`3b61789`) is uncompiled locally.
- No live Supabase/Postgres access; every migration/RLS/trigger/stock/row-count claim is untested live.
- Worker `npm ci` cannot run on this Android/Termux host because `workerd` rejects the platform; CI runs Node 24.
- Detekt was run with default config only (2,751 weighted issues) and is not a project gate.
- Not every Compose screen/ViewModel was read line-by-line; UI findings are targeted.
- No two-device manual sync test was performed.
- CI status for the current commit was not queried (no authenticated `gh`).

## Explicitly out of scope until approved

- Applying any fix, migration, RLS policy, trigger change, data repair, or release-workflow change.
- Deleting or modifying the untracked duplicate stock migration.
- Adding these findings to `Quality/Bug Register.md`; that documentation change is proposed for Phase 0 after approval so the register is not edited during a read-only audit.
- Installing additional skills or tools beyond the approved A–D list.

## Approval checklist

1. [ ] Phase 0 read-only access and backup/restore verification approved.
2. [ ] Phase 1 prune safety and visit-unlink scope approved.
3. [ ] Live FK/trigger/RLS/row-count results reviewed; S1 classification confirmed or downgraded with evidence.
4. [ ] Phase 2 PR breakdown approved (finance/reminders, merge/borrow/queue/IDs, visit/reminder keys, dates).
5. [ ] RLS role matrix approved before Phase 3 migration.
6. [ ] Release/signing workflow approved before Phase 5.
7. [ ] Every production change has a backup, a rollback, and a verification owner.
