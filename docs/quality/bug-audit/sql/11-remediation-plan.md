# SQL Audit Remediation Plan

**Status:** proposal only. The verification basis is `10-findings-verification.md`; migration mechanics and data repair are in `12-migration-and-data-repair-plan.md`.

**Implementation note (2026-10-26):** after approval, a code-only subset was started in the working tree: DBA-03/04, DBA-05/23 (partial), DBA-10, DBA-13 and DBA-16. It has not been compiled or tested; the statuses below remain the verification baseline. No migration, data repair, RLS or production write has been applied. See `18-master-bug-register.md`.

## 1. Rules for this repair

1. **Stop new damage before repairing old damage.** Deploy/verify the stock trigger, fix the finance overwrite and the delete/merge ordering before touching historical rows.
2. **One root cause, one shared fix.** Do not patch only the screen named in a report; fix the repository/DAO path used by every caller.
3. **No speculative schema.** Reuse the existing tables, DTOs and sync groups. A schema change is allowed only when it removes a race or an integrity gap that code cannot close.
4. **No automatic deletion of business data.** Queue rows and diagnostic caches may be cleaned after export. Patients, visits, finance, reminders, notes and audit rows need an explicit list, owner approval, backup and rollback.
5. **No `Fixed` label without the original reproduction failing/repassing.** Source presence alone is not enough for release.
6. **Do not change RLS, retention, merge semantics or money representation without the owner decision named in §6.**
7. **Keep the app usable on the existing database.** Room migrations are forward-only; Supabase migrations must be ordered, transactional and idempotent.

## 2. Dependency-ordered waves

| Wave | Goal | Findings | Gate before starting |
|---|---|---|---|
| **0** | Know the live state | preflight for all | Read-only Supabase access; backup; owner availability |
| **1** | Stop ongoing corruption, loss and unauthorized destruction | DBA-01 deployment + repair, DBA-02, DBA-03, DBA-04, DBA-05, DBA-10, DBA-23, DBA-26, DBA-08 (decision first) | Wave 0 complete; trigger state known; RLS matrix approved or explicitly deferred |
| **2** | Make sync retries and conflicts deterministic | DBA-09, DBA-11, DBA-12, DBA-13, DBA-16, DBA-17, DBA-18 | Wave 1 app build available; Room v34 migration tested |
| **3** | Restore offline history and bound performance | DBA-07, DBA-14, DBA-15, DBA-19, DBA-20/VER-04, DBA-21, DBA-22, DBA-25 verification | Wave 2 queue/conflict behavior stable; product decisions on DBA-07/15/20/22 |
| **4** | Close deployment and policy gaps | VER-01, VER-02, VER-03, VER-06; documentation and regression suite | Owner decisions on retention and RLS recorded |

Do not mix Wave 1 data repair with Wave 2 Room schema changes in one release. A failed stock repair must not be obscured by an app schema migration.

## 3. Wave 1 — stop the bleeding

### 3.1 DBA-01 + DBA-24 + VER-01 — stock trigger deployment

**Root cause.** The old trigger applied the UPDATE delta twice. The source fix is present; the risk is now deployment state: missing trigger, two triggers, an old function body, or a migration that fails halfway.

**Smallest safe correction.**

- Do **not** edit the final migration in place. Apply the ordered sequence through the Supabase migration runner, with the final `20261022` last.
- Before any DDL, run the preflight query in `12-migration-and-data-repair-plan.md` §3. If more than one non-internal trigger calls `update_batch_stock()`, stop.
- Make the final migration explicit:

```sql
begin;

-- Preflight: fail before changing anything if a stray stock trigger exists.
do $$
declare n integer;
begin
  select count(*) into n
  from pg_trigger
  where tgrelid = 'public.inventory_transactions'::regclass
    and not tgisinternal
    and tgfoid = 'public.update_batch_stock()'::regprocedure;
  if n > 1 then
    raise exception 'refusing to apply: % stock triggers already exist', n;
  end if;
end $$;

drop trigger if exists tr_update_batch_stock on public.inventory_transactions;
drop trigger if exists inventory_transactions_update_batch_stock on public.inventory_transactions;

create or replace function public.update_batch_stock() ...; -- current final body
create trigger inventory_transactions_update_batch_stock
after insert or update or delete on public.inventory_transactions
for each row execute function public.update_batch_stock();

do $$ ... exactly one trigger ... $$;

commit;
```

- If production already has a stray trigger, do not run the `drop/create` sequence blindly. Either drop every trigger that calls the function in one reviewed migration, or stop and resolve the stray object manually.
- Update `docs/bug-audit/16-release-readiness.md` and `docs/bug-audit/20-next-actions.md` to name `20261022_fix_stock_trigger_update_arithmetic.sql`, not the older `20261021001000`. This is VER-02 and is part of the release gate, not a code fix.

**Migration need.** Yes: a transactional wrapper/guard migration or a corrected application procedure. No schema change beyond the trigger.

**Existing corruption.** Unknown. Detect with the reconciliation view; repair only with physical counts (doc 12). Do not rebuild `remaining_quantity` from a ledger that may be missing rows rejected by the old negative guard.

**Compatibility.** The trigger is server-side and does not change the app DTO. The app must still send ledger CREATEs insert-only; confirm `SyncUploader` does so.

**Tests.** Stock trigger matrix (already passes locally), trigger-count query, re-apply guard, and a two-device retry test. The original reproduction must fail on the old function and pass on the new one.

### 3.2 DBA-03 + DBA-04 — finance pull and clinic date

**Root cause.** The heal branch bypasses the pending queue, and date conversion uses the device zone.

**Smallest safe correction.** In `FinanceRepositoryImpl`:

```kotlin
val isPending = id in pendingFinanceIds
val visitDate = resolveClinicTransactionDate(remote.visitId) // IST, null-safe
val normalizedRemote = remote.copy(
    transactionDate = visitDate ?: remote.transactionDate
)

if (isPending || local?.isSynced == false) {
    // Local edit wins until its queued upload is acknowledged.
    // Do not write, do not enqueue from a pull. Optionally record a conflict flag.
    continue
}

if (local == null || local.isSynced) {
    financeDao.insertTransaction(normalizedRemote.copy(isSynced = true))
}
```

- Delete the `wasHealed -> enqueue(UPDATE)` branch. A pull must not create sync work.
- `resolveTransactionDate` must use `ZoneId.of("Asia/Kolkata")`, not `systemDefault()`. Use the existing clinic-date helper if it exists; do not add a second date library.
- If a local row has no date and a remote row has a valid date, filling the blank is safe. If both have a date and differ, do not overwrite a pending edit; record the mismatch.

**Migration need.** None.

**Existing corruption.** Rows already overwritten cannot be reconstructed from the pull. Detect finance rows whose `transaction_date` is blank or differs from the linked visit date; export before changing. Do not bulk-update without reviewing each affected row.

**RLS.** No change. Finance writes remain covered by DBA-08’s matrix.

**Transaction/retry.** The pull write is inside the existing `transactionRunner.run`; keep it. The queue item must remain PENDING after a skipped heal; the next upload decides. No new retry loop.

**Tests.** Add to `FinanceRefreshImportTest`: pending local amount/date is unchanged when a remote row needs healing; no UPDATE is enqueued; IST conversion under UTC and America/New_York; blank remote date does not erase a local date. Existing test `FinanceRefreshImportTest.kt` covers NCC-044 but not this branch.

### 3.3 DBA-05 + DBA-23 + DBA-26 — delete and merge convergence

These share one root cause: **patient-owned rows are moved/deleted in multiple queues with no shared order or complete ownership map.**

**Smallest safe correction.** Add one helper that enqueues a patient delete/merge as a group, and use server IDs for reminders:

```kotlin
val group = UUID.randomUUID().toString()
for (reminder in reminders) {
    syncRepository.enqueue(
        entityName = "REMINDERS",
        entityId = reminder.serverId ?: reminder.id,
        operation = DELETE,
        priority = HIGH,
        transactionGroupId = group
    )
}
// ... other child deletes ...
syncRepository.enqueue(
    entityName = "PATIENT",
    entityId = patient.serverId ?: patient.id,
    operation = DELETE,
    priority = LOW,
    transactionGroupId = group
)
```

- The parent delete must be last. Child failures must keep the group retryable; the parent must not be marked uploaded first.
- For merge, enumerate every patient-owned table, not just the three currently handled. At minimum: visits/vaccination items, reminders, consultations, consultation todos, vaccination todos, personal reminders, finance transactions, notes, inventory transaction patient ids, and document/storage paths. Use the same group, with the duplicate patient delete last.
- If a table has no repository transfer method, add the smallest DAO update that rewrites the patient id; do not re-create and delete rows.
- Do not transfer a finance row that is tied to a visit unless the visit itself moves; otherwise the row follows the visit and is counted once.
- `MergePatientsUseCase` must not call `deletePatient` until the child updates are enqueued and the local transaction can still roll back.

**Migration need.** None for the code path. If the owner wants server-side cascades or a merge RPC, that is a separate decision; do not invent one now.

**Existing corruption.** Remote reminders can already be orphaned by old deletes. Detect:

```sql
select r.id, r.patient_id
from public.reminders r
left join public.patients p on p.id = r.patient_id
where p.id is null;
```

Do not auto-delete. Export the rows and let the owner choose delete vs restore. Merges already executed may have lost consultations/notes/finance; reconstruct only from backup or audit history, not by guessing.

**RLS.** DBA-08 applies: a delete group is only safe if the role is allowed to delete the parent and children.

**Tests.** Two-device delete with a pulled reminder (`serverId != id`); delete with a mid-group failure; merge a duplicate populated in every child table and assert zero child rows point to the duplicate after merge; statistics/finance totals unchanged. Add a test that a patient delete cannot upload before its child deletes.

### 3.4 DBA-10 — borrow return state

**Root cause.** The final state transition uses the UI’s stale remaining quantity.

**Smallest safe correction.** Inside the repository, after loading `latest`:

```kotlin
val outstanding = latest.quantity - latest.returnedQuantity
if (quantity <= 0 || quantity > outstanding) return false
val newReturned = latest.returnedQuantity + quantity
val fullyReturned = newReturned >= latest.quantity
```

- Remove the caller’s `remainingQuantity` from the decision. Keep it only for display if needed.
- Cap at the freshly read `outstanding`, not the dialog value.
- Return a typed error so the ViewModel can reload and tell the user the dialog was stale.

**Migration need.** None for single-device. Multi-device over-return needs a server-side aggregate guard or RPC; that is a separate decision and should not be faked with a client cap.

**Existing corruption.** A borrow can be marked RETURNED with stock still out, or ACTIVE with nothing out. Detect by comparing `quantity`, sum of `borrow_returns.quantity` and `is_returned`; export before changing. Do not silently flip statuses without reviewing the return rows.

**Tests.** `2+3` on quantity 5 marks returned; `3+3` on quantity 5 rejects the second; two devices returning concurrently do not over-return. The existing `BorrowBatchSelectionTest` does not cover this.

### 3.5 DBA-02 — visit/ledger contract

**Root cause.** The code intentionally keeps `inventory_transactions.visit_id`, while the uploader still documents a nulling step; the live FK decides whether visit deletion fails.

**Smallest safe correction.** Pick one contract and make code and schema agree:

- **Preferred:** keep the ledger row and set `visit_id` to null on visit deletion, using an FK with `on delete set null`. The ledger is a stock audit trail; deleting it is worse than losing the link.
- **Fallback:** if the FK is RESTRICT and the owner wants the visit link retained, make visit deletion a soft delete or block deletion with a clear error. Do not delete ledger rows.

Illustrative migration (only after inspecting the live constraint name):

```sql
alter table public.inventory_transactions
  drop constraint inventory_transactions_visit_id_fkey,
  add constraint inventory_transactions_visit_id_fkey
    foreign key (visit_id) references public.patient_visits(id) on delete set null;
```

Then either restore the explicit unlink before the visit DELETE, or rely on the FK action. Do not leave the two comments contradicting each other.

**Migration need.** Conditional on live FK. **Data repair.** None; the ledger row remains.

**Tests.** Create visit + ledger + delete visit; assert visit gone, ledger retained, `visit_id` null (or documented retention). Run inside a transaction and roll back.

### 3.6 DBA-08 — Supabase authorization

**Root cause.** The baseline grants every active staff member full CRUD on clinical, financial and inventory tables. UI role checks are not a security boundary.

**This is a decision first.** The owner must choose the role → table → operation matrix. A recommended minimum, not approved:

| Table group | SELECT | INSERT | UPDATE | DELETE |
|---|---|---|---|---|
| patients, visits, vaccination items, notes | active staff | active staff | admin/doctor | admin |
| finance, expenses | admin/doctor | admin/doctor | admin/doctor | admin |
| vaccines, batches, inventory transactions | active staff | admin/inventory_manager | admin/inventory_manager | admin |
| reminders, personal reminders, todos | active staff | active staff | active staff/owner | admin |
| audit logs | admin/doctor | server/logger only | none | none |
| doctor slots/exceptions | active staff | admin | admin | admin |

If the clinic needs receptionists to delete patients or edit finance, the matrix must say so explicitly; do not infer it from the UI.

**Illustrative policy shape:**

```sql
create or replace function public.has_app_role(allowed text[])
returns boolean
language sql stable security definer
set search_path = public, auth
as $$
  select public.is_active_staff()
     and coalesce(auth.jwt() -> 'app_metadata' ->> 'role', '') = any(allowed)
$$;

revoke all on function public.has_app_role(text[]) from public;
grant execute on function public.has_app_role(text[]) to authenticated;

drop policy if exists patients_delete on public.patients;
create policy patients_delete on public.patients
for delete to authenticated
using (public.has_app_role(array['admin']));
```

- Define the helper once; do not repeat JWT parsing in 20 policies.
- Keep `is_active_staff()` checks so a deactivated profile loses access even with a live JWT.
- Do not add `security definer` to a policy helper that reads user-controlled tables without a fixed `search_path`.
- Keep `profiles` own-row update guarded by the existing privileged-field trigger.

**Migration need.** Yes, one reviewed RLS migration. No table rewrite and no data deletion. **Compatibility:** the current app may rely on staff deletes/updates; ship the app role gating first or accept a documented feature change. **Rollback:** snapshot `pg_policies` before; restore old policy definitions if the matrix breaks a required workflow.

**Tests.** JWT probes for each role/action, including a deactivated user; a stolen/expired token; `service_role` unaffected; audit log select still works. The existing `supabase/tests/rls_role_test.sql` has placeholder IDs and is not a passing test.

## 4. Wave 2 — sync correctness

### 4.1 DBA-09 + DBA-17 — atomic queue dedupe and index

**Root cause.** Dedupe is read-then-write; the queue has no uniqueness constraint and no lookup index.

**Smallest safe correction.** Room migration v34:

```sql
-- Export duplicates before deleting anything.
CREATE TABLE IF NOT EXISTS sync_queue_duplicate_backup_20261026 AS
SELECT * FROM sync_queue WHERE status = 'PENDING';

-- Keep the newest PENDING row per entity+operation; mark/remove older duplicates.
DELETE FROM sync_queue
WHERE status = 'PENDING'
  AND queueId NOT IN (
    SELECT MAX(queueId) FROM sync_queue
    WHERE status = 'PENDING'
    GROUP BY entityName, entityId, operation
  );

CREATE UNIQUE INDEX IF NOT EXISTS idx_sync_queue_pending_unique
ON sync_queue(entityName, entityId, operation)
WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_sync_queue_entity_status
ON sync_queue(entityName, entityId, status);
```

Then change `SyncRepositoryImpl.enqueue` to one atomic statement:

```kotlin
@Query("""
  INSERT INTO sync_queue(entityName, entityId, operation, priority, status, payload, createdAt, updatedAt)
  VALUES (:entityName, :entityId, :operation, :priority, 'PENDING', :payload, :now, :now)
  ON CONFLICT(entityName, entityId, operation) WHERE status = 'PENDING'
  DO UPDATE SET payload = excluded.payload, priority = excluded.priority, updatedAt = excluded.updatedAt
""")
suspend fun enqueueOrRefresh(...)
```

- Keep SYNCING/FAILED rows exempt; the partial index does what the comment promises.
- If the app cannot use a partial conflict target on its SQLite version, use `INSERT OR IGNORE` then `UPDATE … WHERE status='PENDING'` in one transaction. Do not use `REPLACE`; it changes `queueId` and can break in-flight state.
- Existing PENDING duplicates are transient outbox rows, but they are still evidence: export them to a file/table before deleting only the older duplicates.

**Migration need.** Yes, Room v34. **Compatibility:** old app versions do not know the index; a device runs only one app version, so the upgrade path is the normal Room migration. Test downgrade behavior; Room is forward-only.

**Tests.** Two concurrent enqueues create one row; one upload; payload update keeps one row; `EXPLAIN QUERY PLAN` uses the new index; a 10k-row queue does not scan.

### 4.2 DBA-11 — one refresh per table

**Root cause.** Only the pipeline has single-flight; screens call repositories directly.

**Smallest safe correction.** Route screen refreshes through `RefreshDataUseCase`, or add one keyed coordinator:

```kotlin
class RefreshCoordinator @Inject constructor(...) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    suspend fun <T> refresh(key: String, block: suspend () -> T): T =
        locks.getOrPut(key) { Mutex() }.withLock { block() }
}
```

Use one key per table (`patients`, `inventory`, `reminders`, …). Do not add a global lock; it would serialize unrelated pulls and make startup slower. If routing through the use case is enough, prefer that and skip the class.

**Migration need.** None. **Tests.** Fake repository counts calls during two simultaneous refreshes; expect one network call. **Compatibility:** no DTO change.

### 4.3 DBA-12 — orphan guard

**Root cause.** Personal-reminder pull inserts without checking the patient; Room FK rejects the whole step.

**Smallest safe correction.** In the personal-reminder pull loop:

```kotlin
if (entity.patientId != null && patientDao.getPatientById(entity.patientId) == null) {
    log.w("PersonalReminder", "skipping orphan ${entity.id}, patient ${entity.patientId}")
    return@forEach
}
```

For a nullable patient, only guard when non-null. Mirror the consultation `localVisitIds` pattern. Do not swallow the error silently; count skipped orphans for diagnostics.

**Migration need.** None. **Data repair.** Detect remote orphans and let the owner decide; do not auto-delete. **Tests.** Dangling remote row → refresh completes, no local row, one log; valid row imports.

### 4.4 DBA-13 — preserve `created_at`

**Root cause.** `VaccinationEntity.toEntity` uses the edit timestamp for `createdAt`; the edit path does not pass the existing value.

**Smallest safe correction.** Add an optional preserved value:

```kotlin
fun Vaccination.toEntity(isSynced: Boolean, createdAtOverride: String? = null): VaccinationEntity {
    val timestamp = updatedAt.ifBlank { PatientUtils.getCurrentIsoTimestamp() }
    return VaccinationEntity(..., createdAt = createdAtOverride ?: timestamp, ...)
}
```

In `addVaccination`, pass `existing?.createdAt`; in the edit engine, pass the row being edited. Do not invent a new audit column.

**Migration need.** None. **Data repair.** Existing rewritten `created_at` values are not recoverable from the visit alone; export and compare to the earliest audit/visit timestamp. Do not bulk-rewrite without evidence. **Tests.** Create, wait, edit; local and upload payload keep the original `created_at`.

### 4.5 DBA-16 — audit inside the transaction

**Root cause.** `log()` launches on its own scope; it survives rollback.

**Smallest safe correction.** Use `recordLog` inside every `transactionRunner.run` / `withTransaction` block. For the borrow create path, replace `auditLogger.log(...)` with `auditLogger.recordLog(...)`. Keep `log()` only for callers that are genuinely outside a transaction and can tolerate a missing audit row. If audit failure must not block clinical work, catch it only outside a transaction; inside, let it roll back.

**Migration need.** None. **Data repair.** Audit rows already written for rolled-back actions cannot be distinguished automatically; review `action`/`remarks` only if a specific incident is reported. **Tests.** Force an audit insert failure in a transaction: business row rolls back and no audit row survives; successful commit has one audit row.

### 4.6 DBA-18 — ledger conflict path

**Root cause.** The descriptor download default is a no-op; the uploader treats it as a successful refresh.

**Smallest safe correction.** For append-only ledger entities, implement a real downloader (insert/ignore by primary key) or treat the conflict as unresolved and keep the queue item. Do not call a no-op and return success.

Preferred minimal code:

```kotlin
val downloader = descriptor.downloadOrNull
if (downloader == null) {
    markConflict(item, "no downloader for ${item.entityName}; remote is newer")
    return
}
downloader(database, remoteData)
```

- Add `downloadOrNull` to the descriptor instead of a default no-op; compile-time pressure is better than a silent success.
- For `TRANSACTION`/`INVENTORY_TRANSACTION`, a downloader can `upsert` the remote ledger row with `isSynced=true`; ledger rows are immutable, so insert-on-conflict-do-nothing is safe.
- If the owner wants local to win for append-only rows, the uploader must retry/idempotently insert and never drop the queue item.

**Migration need.** None. **Data repair.** Detect local queue rows already deleted by this branch? They are gone; use sync logs if enabled. **Tests.** Fake remote newer with no downloader: queue item is not deleted and an error is visible; with downloader: local refreshed and one row.

## 5. Wave 3 — offline, identity and performance

### 5.1 DBA-07 — stock history offline

**Root cause.** The screen is remote-only and the ledger is never pulled.

**Smallest safe correction.** Choose one:

- **Preferred for a local-first app:** pull the ledger into Room (paged, insert-ignore because rows are immutable), then render from Room with a local fallback. Do not prune ledger history; it is an audit trail.
- **Minimum if the owner accepts online-only:** keep `remoteOnly=true`, label the screen “Online only”, show a local batch/movement summary instead of an empty list, and handle errors explicitly.

Do not build a second cache table. Use `inventory_transactions` and the existing DAO. Pull only the last N months if volume is a concern; N is an owner decision.

**Migration need.** None if reusing the table. **Tests.** Fresh install with remote ledger shows history after one refresh; offline shows cached history; no duplicate rows on repeated pull.

### 5.2 DBA-14 — clinic clock

**Root cause.** Business-day code uses the device default zone.

**Smallest safe correction.** One helper, used everywhere the clinic day matters:

```kotlin
object ClinicClock {
    val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    fun today(): LocalDate = LocalDate.now(zone)
    fun nowIso(): String = OffsetDateTime.now(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
```

Inject a `Clock` for tests. Replace `LocalDate.now()`/`ZoneId.systemDefault()` in dashboard, stock history, finance, reminders, notifications and date formatting. Leave device-zone formatting only for explicitly local UI times.

**Migration need.** None. **Data repair.** Finance rows with wrong days are handled under DBA-04; reminder due dates may need review if they were generated in another zone. **Tests.** Default zone UTC and America/New_York; clinic day is IST; display/filter boundaries.

### 5.3 DBA-15 — audit history offline

**Root cause.** The screen is remote-only by design, but it shows an empty/error state without explaining it.

**Smallest safe correction.** First, label “Requires connection” and keep the local staged rows visible. If the product wants offline history, read local `audit_logs` and merge with remote pages; do not silently pretend the local table does not exist.

**Migration need.** None. **Tests.** Offline screen shows local rows + an explicit online-only notice; fresh install shows an empty state, not an unhandled error. **Decision:** owner chooses label vs cache.

### 5.4 DBA-19 — reminder key

**Root cause.** Local unique key includes `dueDate`; server key omits it.

**Smallest safe direction.** The product invariant is “one reminder per patient/visit/vaccine/type”; the server key is the intended one. Align local to the server, not the reverse:

```sql
-- Room v34/v35, after export and de-duplication:
-- Keep the newest active row per four-column key; do not silently delete a row that
-- has a different due date without recording which one was kept.
CREATE UNIQUE INDEX IF NOT EXISTS index_reminders_natural_key
ON reminders(patientId, originalVisitId, type, vaccineName);
```

Then change `saveNextVaccination` to look up by the four-column key and update `dueDate` on the existing row. If the owner decides multiple due dates per event are legitimate, the server key must change instead; do not mix the two.

**Migration need.** Room migration plus a server migration only if the server index is absent. The server migration must first de-duplicate and must not be applied blindly. **Data repair.** Export duplicates, choose one per key, record the kept/discarded IDs. **Tests.** Same event/new date updates one row; two devices do not create two rows; a duplicate insert is rejected with a clear error, not a crash.

### 5.5 DBA-20 + VER-04 — bounded pulls and safe prune

**Root cause.** Full-table offset pulls are not snapshots; aggregate Flows load all history.

**Smallest safe correction now.** Do not attempt a cursor/tombstone redesign in this repair. Keep the full pull, but make prune fail closed:

1. Request an exact count with the pull (`count=exact` / `Content-Range`).
2. Compare `fetched.size` with the count; if they differ, skip `RemotePrune` and retry on the next refresh.
3. Keep the pending-ID guard.
4. Add a generation/attempt token if the client can cheaply do so.

This prevents the DBA-06 fix from deleting a row missed by a shifted page. It does not bound payload size. For that, a later project can add `updated_at` cursor + tombstones; the current hard-delete schema cannot support a correct cursor alone.

**Migration need.** None. **Tests.** Concurrent insert/delete during a pull: no local row deleted; next refresh converges. Benchmark a realistic 20k-visit dataset only after the count guard.

### 5.6 DBA-21 — finance ordering

**Root cause.** `COALESCE(transaction_date, substr(timestamp,1,10))` is not covered by the plain date index.

**Smallest safe correction.** Prefer backfilling `transaction_date` for rows linked to a visit, then order by `transaction_date` directly. If blank dates must remain, add a matching expression index:

```sql
CREATE INDEX IF NOT EXISTS finance_effective_date_idx
ON finance_transactions (
  COALESCE(transaction_date, substr(timestamp, 1, 10)) DESC,
  timestamp DESC
);
```

Verify `EXPLAIN QUERY PLAN` uses it. Do not add an index without checking the Room schema validation behavior; an extra raw index is usually harmless, but test the generated schema.

**Migration need.** Room migration for the index and/or data backfill. **Data repair.** Backfill blank dates from the linked visit only after DBA-04 is fixed. **Tests.** Plan no longer contains a full scan/temp sort.

### 5.7 DBA-22 — patient search

**Root cause.** Leading-wildcard `LIKE` and a visit subquery scan.

**Smallest safe correction.** Do not add FTS in the first repair. Change the hot path to prefix search (`query%`), normalize phone digits, and add a minimum query length/debounce if not already present. If substring search is a product requirement, add FTS/trigram only after measuring. The visit-name subquery can be moved to a separate “search visits” action rather than every keystroke.

**Migration need.** Indices only if prefix search is adopted: `patients(name)` and a normalized phone column/index. **Tests.** `EXPLAIN QUERY PLAN` uses the prefix index; two-character queries are not sent on every keystroke.

### 5.8 DBA-25 — clinic ID residual

**No code fix required for the reported reproduction.** The per-install suffix is the accepted minimum. Optionally add one server unique-violation retry:

```kotlin
// On 23505 for patients.patient_clinic_id: regenerate once and retry the upload.
```

Do not add a server sequence only for this audit unless the owner approves it; it changes ID allocation and migration behavior. **Tests.** Two-device offline create produces different IDs; a forced 1/65,536 collision is retried or surfaced, not left FAILED forever.

## 6. Owner decisions required

| Decision | Options | Why it blocks |
|---|---|---|
| DBA-08 role matrix | Blanket active staff vs admin/doctor/inventory split | Security vs workflow; cannot guess who may delete patients/finance |
| VER-03 audit retention | 1 year vs longer; export/archive | Permanent deletion of audit rows |
| DBA-26 merge semantics | Move everything vs block merge unless clean | Clinical/financial data ownership and duplicate resolution |
| DBA-02 visit/ledger contract | `on delete set null` vs retain link via soft delete | Determines visit deletion behavior |
| DBA-07 stock history | Pull ledger vs online-only label | Offline promise vs storage/network cost |
| DBA-15 audit history | Cache vs online-only label | Offline promise |
| DBA-19 reminder key | One per event vs multiple due dates | Data model |
| DBA-20 incremental sync | Keep full pull vs cursor + tombstones | Scope; hard deletes need tombstones |
| DBA-22 search | Prefix vs substring | UX vs index/performance |
| DBA-25 ID strategy | Suffix (current) vs server sequence | Migration and offline behavior |

## 7. Non-goals and deliberately skipped work

- No automatic deletion of remote orphans, duplicate reminders, or merged-patient children.
- No bulk stock rebuild without a physical count.
- No new UI framework, dependency, or generic sync engine.
- No change to integer paise / money representation in this SQL repair.
- No full re-audit of the 230 changed paths from the audit snapshot. Only paths named by DBA findings plus the post-audit fixes were inspected.
- No claim that the Android build or 310/310 tests pass; that requires an Android environment and is outside this SQL plan.

## 8. Definition of done for the first release

1. One stock trigger is installed; the final function hash matches the reviewed body; the arithmetic test passes.
2. The drifted-batch report is exported, physically reviewed, and either repaired with a backup or explicitly quarantined.
3. DBA-03/04 tests pass; no pull enqueues finance work; IST conversion is covered.
4. Patient delete/merge tests show no orphaned reminders, consultations, finance or notes.
5. RLS matrix is either approved and tested or explicitly deferred with a written risk acceptance.
6. Queue dedupe is enforced by a unique index; the concurrency test passes.
7. The release note names the exact migration order.
8. Every “Fixed” claim has a test or a live verification artifact behind it.
