# 06 — Data Integrity and Concurrency

**Scope:** stock accounting, sync atomicity, delete ordering, soft-delete consistency, and time/identity handling.
**Method:** end-to-end trace of the Android write path against the server functions and declared FKs. No runtime execution.
**Status (2026-10-26):** SQL-006, 008, 021, 023, 024, 025, 026 and 030 are fixed in the working tree — see `14-fix-log.md`. The analysis below is `b91142a`.

---

## 1. Intended stock model

The design is a two-writer system:

1. **Local (Room)** — `vaccine_batches.remainingQuantity` is authoritative for the UI and is updated inside a Room transaction.
2. **Server (Postgres)** — `vaccine_batches.remaining_quantity` is maintained **only** by `update_batch_stock()` reacting to `inventory_transactions` rows (`20261022…:33-95`).
3. **Bridge** — `SyncUploader.uploadBatch()` deliberately sends `remaining_quantity = 0` on CREATE and omits it on UPDATE (`SyncUploader.kt:617-641`), so the paired ledger row is the only thing that moves server stock.

This works only if:

- exactly one ledger row describes each movement,
- the batch and its ledger row are uploaded in the right order,
- a retry does not re-apply a movement,
- a retry does not reset the batch to 0,
- a moved/deleted ledger row is validated against the **old** batch too.

The code satisfies the first three in the happy path and fails the last two (`SQL-006`, `SQL-008`).

---

## 2. Sync transaction model

`SyncRepositoryImpl.processGroup()` (`:272-333`):

1. marks every item in the group `SYNCING` in one local Room transaction,
2. fetches remote conflict data,
3. loops items in order, calling `uploadEntity()` (one PostgREST request per item),
4. after each success, `markUploaded(item)` then `syncDao.deleteItem(item)`,
5. on failure, leaves earlier items committed and releases later items to `PENDING`.

A “group” is therefore an **ordering hint**, not a database transaction.

### SQL-009 — Sync groups are not atomic

- **Severity:** High
- **Confidence:** Confirmed
- **Object:** `processGroup`, `SyncRepositoryImpl.kt:272-333`
- **Workflow:** any multi-row operation: vaccination create, stock purchase, patient delete, borrow with returns, consultation delete
- **Root cause:** each item is a separate HTTP request; there is no server-side transaction boundary spanning them and no compensating action for a partial group.
- **Failure scenario:** a patient delete group uploads the reminder DELETEs and some visit DELETEs, then hits the 600-write rate limit (`20260929001000…:26,43-45`) or a network timeout. The patient DELETE never runs. The device shows the patient gone; the server still has the patient and some children. A later pull re-imports the surviving rows, so the patient reappears with partial history. Conversely, a stock group can commit the batch CREATE and fail the PURCHASE, leaving server stock at 0 while the device shows the purchased quantity.
- **Impact:** divergent local/server state; “deleted” records resurrect; stock and revenue disagree; no automatic repair.
- **Evidence:** `:294-305` (per-item upload + delete), `:316-331` (only later items are released), `20260929001000…:26,43-45` (rate limit), `SyncUploader.kt:630-641` (batch zero).
- **Correction:** the smallest robust fix is to make the server operations idempotent and self-healing, not to build a distributed transaction:
  - upload the ledger row **before** the batch row (or send the real `remaining_quantity` and make the trigger ignore the paired row),
  - make the batch CREATE an insert-only operation that does not reset stock on retry,
  - add a reconciliation pull that imports `inventory_transactions` and recomputes local stock,
  - for deletes, order children before parents and tolerate 23503 as “retry after parent delete” rather than a permanent failure.
  Longer term, a single Postgres function per business operation (RPC) would give real atomicity; that is a larger change and should be a separate decision.
- **Regression test:** fail the second item of a three-item group, then retry; assert both local and server end in the same state, with no stock reset and no resurrected rows.
- **Live check:** yes — compare local and server row counts for a sample of patients/visits.

---

## 3. Stock findings

### SQL-008 — Batch CREATE retry can reset stock to zero

- **Severity:** Critical
- **Confidence:** Confirmed
- **Object:** `SyncUploader.uploadBatch()`, `SyncUploader.kt:630-641`; group order in `SYNC_ENTITY_REGISTRY`
- **Workflow:** purchase/restock (`InventoryRepositoryImpl.addStockBatch`), any batch create
- **Root cause:** on `item.operation == CREATE`, the uploader **always** overwrites `remaining_quantity` with `0` (`:635-636`). The queue item is only deleted after a successful response (`SyncRepositoryImpl.kt:303-304`). If the batch INSERT committed but the response was lost, the item is retried as CREATE and the upsert writes `0` over the stock the PURCHASE row already applied. The paired PURCHASE retry is `ignoreDuplicates = true` (`SyncUploader.kt:564-568,665-667`), so it does **not** re-apply the quantity.
- **Failure scenario:**
  1. Device creates batch B with local `remainingQuantity = 50` and enqueues `BATCH CREATE` + `TRANSACTION PURCHASE +50` in one group.
  2. BATCH CREATE commits server-side (`remaining_quantity = 0`); the response is lost.
  3. PURCHASE commits server-side; the trigger raises stock to 50.
  4. The sync retries the group. BATCH CREATE is still a CREATE, so it sends `remaining_quantity = 0` and overwrites 50.
  5. PURCHASE retry is insert-only and does nothing.
  6. Server stock is 0; the device still shows 50.
- **Impact:** silent stock loss; expired/zero-stock batches; vaccinations blocked by the negative guard; physical inventory disagrees.
- **Evidence:** `SyncUploader.kt:635-639`; `SyncRepositoryImpl.kt:294-305`; `SyncUploader.kt:665-667`; registry order BATCH priority 2 before TRANSACTION priority 4 (`SyncUploader.kt:157-175`).
- **Correction:** do not send `remaining_quantity` on CREATE either. Let the batch row default to 0 and let the paired PURCHASE be the only writer. If a NOT NULL constraint requires a value, send `0` only when the row does not exist (true INSERT … ON CONFLICT DO NOTHING), not on upsert. Concretely:
  ```kotlin
  postgrest.from(table).insert(JsonObject(fields)) { ignoreDuplicates = true }
  ```
  for CREATE, so a retry is a no-op and never touches `remaining_quantity`.
- **Regression test:** simulate a lost response after batch+purchase commit, retry the pair, assert server stock is still 50. Add this to the SQL test and a sync integration test.
- **Live check:** yes — find batches whose `remaining_quantity` is 0 but whose ledger sums to > 0: `select * from public.inventory_reconciliation where drift <> 0;`

### SQL-011 — Inventory pull omits the ledger, so local guards are blind

- **Severity:** Medium
- **Confidence:** Confirmed
- **Object:** `InventoryRepositoryImpl.refreshInventory()`, `:641-658`; `deleteBatch`, `:350-368`
- **Workflow:** batch delete on a device that did not create the ledger rows
- **Root cause:** `refreshInventory` pulls `vaccines` and `vaccine_batches` only; it never pulls `inventory_transactions`. `deleteBatch` guards on local counts (`getTransactionCountForBatch`, `:358`), so a ledger row created on another device is invisible.
- **Failure scenario:** device A purchases 10 doses (ledger row exists server-side). Device B pulls batches but not the ledger, so its local count is 0. Staff deletes the batch on B; the guard passes. The remote DELETE cascades to the ledger (if the FK has `ON DELETE CASCADE`) and fires the stock trigger’s DELETE branch, which tries to subtract from a batch row that is already gone and raises (`SQL-012`). The batch delete fails server-side but the local row is already gone.
- **Impact:** phantom local deletion; server retains the batch; next pull resurrects it; ledger/history guard cannot be trusted.
- **Evidence:** `:641-658` (only two pulls); `:358` (local count).
- **Correction:** pull `inventory_transactions` (at least `id, batch_id, quantity, transaction_type, timestamp`) during `refreshInventory`, or make `deleteBatch` a server-side validation call that returns a typed “has history” error. Pulling the ledger is also required for `SQL-012` and for offline reconciliation.
- **Regression test:** create a ledger row on device A, sync, pull on device B, attempt batch delete on B, assert it is blocked.
- **Live check:** no.

### SQL-012 — Remote batch DELETE can fail inside the stock trigger

- **Severity:** Medium
- **Confidence:** Probable (depends on live FK action)
- **Object:** `vaccine_batches` DELETE; `update_batch_stock()` DELETE branch
- **Workflow:** deleting a batch with ledger rows
- **Root cause:** if `inventory_transactions.batch_id` has `ON DELETE CASCADE` (scratch `missing_tables.sql:25`), deleting the batch deletes its ledger rows. The AFTER DELETE trigger then runs the `DELETE` branch (`20261022…:61-63`), selects the batch `for update` (`:71-75`), finds no row, and raises `stock batch … does not exist` (`:77-79`).
- **Failure scenario:** a batch with any ledger row cannot be deleted through a path that cascades; the error surfaces as a generic sync failure. If the FK has no action instead, the DELETE fails with 23503 before the trigger. Either way the result is a failed batch delete, not a clean rejection.
- **Impact:** batch deletion is unreliable; the app’s “no history” guard is the only working protection, and it is blind (`SQL-011`).
- **Evidence:** `20261022…:61-79`; scratch `missing_tables.sql:25`.
- **Correction:** decide the delete semantics explicitly. If history must block deletion, enforce it with a `before delete` trigger on `vaccine_batches` that raises a typed error when ledger rows exist, and set the ledger FK to `RESTRICT`. If cascade is intended, make the stock trigger skip when the batch row is gone (`if not found then return null;`) instead of raising.
- **Regression test:** batch with one ledger row, delete it, assert a deterministic typed error (or a clean cascade with no trigger exception).
- **Live check:** yes — `pg_get_constraintdef` for `inventory_transactions_batch_id_fkey`.

### SQL-010 — Insert-only ledger retries can drop a changed CREATE

- **Severity:** Medium
- **Confidence:** Requires Verification (reachability)
- **Object:** `uploadWithExplicitNulls(..., insertOnly = CREATE)`, `SyncUploader.kt:564-568,655-668`
- **Workflow:** a ledger CREATE whose payload changes before the retry
- **Root cause:** `ignoreDuplicates = true` makes the retry a no-op even if the local row’s quantity/type changed after the first attempt committed.
- **Failure scenario:** a stock reversal row is created with `quantity = -2`, the request commits but the response is lost, and the local row is subsequently corrected to `-3` before the retry. The retry is still a CREATE, so the server keeps `-2`; the correction is never applied and no UPDATE is enqueued.
- **Impact:** ledger and local intent diverge; stock is off by the delta.
- **Evidence:** `:564-568`, `:665-667`; `SyncUploader` treats ledger rows as append-only (`:559-563`).
- **Correction:** if ledger rows are truly immutable, forbid local edits; otherwise enqueue an UPDATE when the payload changes, or use `on conflict do update` for non-create operations. The current hybrid is only safe if no edit path exists — verify by grepping for updates to `inventory_transactions`.
- **Regression test:** create, lose the response, edit `quantity`, retry, assert the server matches the edited value or the edit is rejected at the UI.
- **Live check:** no.

---

## 4. Delete findings

### SQL-021 — Patient hard delete is likely blocked by audit/ledger FKs

- **Severity:** Critical
- **Confidence:** Probable (FKs from the stale scratch file; live check required)
- **Object:** `PatientRepositoryImpl.deletePatient`, `:246-304`; `audit_logs.patient_id`; `inventory_transactions.patient_id`
- **Workflow:** deleting a patient with any history
- **Root cause:** `deletePatient` hard-deletes the patient locally and enqueues a remote DELETE (`:291-292`). The hand-applied schema declares `audit_logs.patient_id → patients(id)` with **no ON DELETE action** (`missing_tables.sql:67`) and `inventory_transactions.patient_id → patients(id)` with no action (`:26`). Any patient with an audit row or stock movement therefore blocks the delete with 23503. Worse, `deletePatient` then calls `recordLog(patientId = id)` (`:294-300`), inserting a **new** audit row that references the patient being deleted.
- **Failure scenario:** every patient has audit rows (registration/update) and most have ledger rows. The remote patient DELETE fails 23503. The device has already removed the patient locally, so the server row survives and reappears on the next pull. The new audit row is also orphaned: if the patient DELETE somehow succeeds first, the audit INSERT fails 23503.
- **Impact:** patient deletion is effectively broken; local/server divergence; orphaned audit rows; repeated sync failures.
- **Evidence:** `PatientRepositoryImpl.kt:291-300`; `missing_tables.sql:26,67`; audit rows are inserted for patient create/update (`PatientRepositoryImpl.kt` recordLog calls).
- **Correction:** choose an explicit retention policy:
  - **Preferred for clinical data:** do not hard-delete patients. Use a `deleted_at`/`is_deleted` tombstone, keep children and audit rows, and filter reads. This is the direction the server columns already support (`SQL-028`).
  - If hard delete is required: `ON DELETE SET NULL` or `CASCADE` on `audit_logs.patient_id` and `inventory_transactions.patient_id`, and stop writing an audit row that points at the deleted patient (write a patient-less “PATIENT_DELETED” event, or write it before the delete with the FK set null).
- **Regression test:** patient with one visit, one ledger row, and three audit rows; delete; assert either a clean tombstone or a successful cascade, and no 23503.
- **Live check:** yes — enumerate FK actions for every `patient_id`/`visit_id` column.

### SQL-022 — Todo rows retain patient PII after patient delete

- **Severity:** High
- **Confidence:** Confirmed
- **Object:** `consultation_todos.patient_id`, `vaccination_todos.patient_id`
- **Workflow:** patient delete
- **Root cause:** the FKs are `on delete set null` (`20260825_patient_todos.sql:8,27`), and `deletePatient` does not delete todo rows (`PatientRepositoryImpl.kt:246-304` has no todo DAO call). The todo table stores `name`, `mobile`, and `address` denormalized (`CSV`).
- **Failure scenario:** deleting a patient removes the patient row but leaves todos containing the child’s name, mobile, and address. They remain visible in Today’s Patient and are never cleaned up.
- **Impact:** PHI retained after an erasure request; misleading “today’s patient” entries with no patient record.
- **Evidence:** `20260825_patient_todos.sql:8,27`; CSV columns; `PatientRepositoryImpl.kt:246-304`.
- **Correction:** delete (or anonymize) todos for the patient inside the same local transaction and enqueue remote DELETEs before the patient DELETE. If todos must survive for historical reporting, null the PII columns (`name`, `mobile`, `address`) and keep only the clinical fields.
- **Regression test:** patient with one consultation todo and one vaccination todo; delete; assert 0 rows remain (or PII is null).
- **Live check:** yes — count orphaned todos with `patient_id is null`.

### SQL-023 — Visit delete leaves ledger rows pointing at the visit

- **Severity:** High
- **Confidence:** Confirmed (client); `Probable` for the 23503 (live FK)
- **Object:** `VaccinationRepositoryImpl.deleteVaccination`, `:452-453`; `inventory_transactions.visit_id`
- **Workflow:** deleting a vaccination/visit
- **Root cause:** step 2b explicitly says “Unlink the inventory_transactions audit trail’s visit_id is no longer needed; visit_id stays” (`:452-453`). `SyncUploader.kt:545-557` still claims `deleteVaccination` sets it to null and relies on that to avoid a 23503. The reversal rows created in step 1 also carry `visitId = id` (`:434-441`). The local ledger rows are never deleted or updated.
- **Failure scenario:** any visit with a stock movement has at least one `inventory_transactions` row with `visit_id = <visit>`. The visit DELETE (enqueued last in its group, `:515-521`) hits the FK and fails 23503. The device already deleted the visit; the server keeps it; the next pull resurrects it. The reversal rows created during the delete add **new** references to the same dead visit.
- **Impact:** visit deletion fails whenever stock was deducted; phantom visits; ledger rows referencing a missing visit.
- **Evidence:** `VaccinationRepositoryImpl.kt:452-453,515-521`; `SyncUploader.kt:545-557`; `InventoryRepositoryImpl.kt:551` (`visitId` passed to reversal).
- **Correction:** restore the unlink before the visit DELETE: update local ledger rows `set visitId = null` and enqueue UPDATEs for them (or enqueue a bulk remote update). Do not create reversal rows with the dead visit’s id — pass `visitId = null` for delete reversals. Keep the ledger traceable by patient/notes instead.
- **Regression test:** create a visit with a deduction, delete it, sync, assert the visit is gone and no ledger row references it.
- **Live check:** yes — `select count(*) from inventory_transactions t left join patient_visits v on v.id=t.visit_id where t.visit_id is not null and v.id is null;`

### SQL-024 — Reminder DELETE uses the local id instead of `serverId`

> **Status at `93021c2`:** fixed. The delete now uses `reminder.serverId ?: reminder.id` and one group id (`PatientRepositoryImpl.kt:263-266`). See `09-final-sql-audit-report.md §4.3`. The analysis below describes `b91142a`.

- **Severity:** Medium
- **Confidence:** Confirmed
- **Object:** `PatientRepositoryImpl.deletePatient`, `:259-261`
- **Workflow:** patient delete with synced reminders
- **Root cause:** `reminderIds.forEach { enqueue("REMINDERS", it, DELETE, …) }` uses the local Room `id`. The correct remote identity is `serverId ?: id` (`VaccinationRepositoryImpl.kt:466-473` does this correctly). For a reminder created locally, `serverId` equals the local id (per `toRemote`/`toLocal`), but for a reminder downloaded from another device, `serverId` is the server id and the local id may differ.
- **Failure scenario:** deleting a patient on device B removes the local reminder queued by local id; the server row remains because the DELETE matched 0 rows (PostgREST treats 0 rows as success). The next reminder pull re-imports it, now parentless or re-linked to a stale visit, and a later create/update fails FK.
- **Impact:** reminders survive deletion; duplicate/parentless reminders; push notifications for a deleted patient.
- **Evidence:** `:259-261`; contrast `VaccinationRepositoryImpl.kt:473`.
- **Correction:** capture `reminder.serverId ?: reminder.id` before deleting, and enqueue that. Same pattern as the vaccination path.
- **Regression test:** reminder with `serverId != id`, delete patient, assert the remote DELETE targets `serverId`.
- **Live check:** no.

### SQL-025 — Patient delete removes the same visit twice through two paths

- **Severity:** High
- **Confidence:** Confirmed
- **Object:** `deletePatient` → `deleteVaccination` for every visit, plus consultation deletion
- **Workflow:** patient with a consultation visit
- **Root cause:** `vaccinationIds` is every row in `patient_visits` for the patient (`VaccinationDao.getVaccinationsForPatient`, `:14-15`), including visits that belong to a consultation. `deletePatient` calls `deleteVaccination` on all of them (`:279-281`), then deletes the consultation rows locally and enqueues their DELETEs (`:284-287`). The visit DELETE group and the consultation DELETE group are separate; the consultation group is enqueued without the visit group id.
- **Failure scenario:** the visit group deletes the visit before the consultation group deletes the consultation. If `consultations.visit_id → patient_visits(id)` has no `ON DELETE CASCADE`/`SET NULL`, the visit DELETE fails 23503. If it cascades, the consultation group’s DELETE is a harmless no-op. If the consultation group runs first, the visit is still deleted by the visit group; also fine. The failure depends on live FK action and group order, but the double ownership is a design bug.
- **Impact:** visit deletion can be blocked by its own consultation; orphaned or double-deleted rows; confusing sync errors.
- **Evidence:** `:252,279-287`; `VaccinationDao.kt:14-15`; `ConsultationRepositoryImpl.kt:186-195` shows the correct child-first order within one group.
- **Correction:** exclude consultation visits from the `vaccinationIds` loop, or delete consultations (and their finance rows) **before** calling `deleteVaccination`, all under one `transactionGroupId`. The latter matches `deleteConsultation`’s own order.
- **Regression test:** patient with one consultation visit; delete; assert the visit and consultation both disappear with no FK error.
- **Live check:** yes — inspect `consultations.visit_id` FK action.

### SQL-012 (cross-ref) — batch delete trigger failure

See §3.

---

## 5. Soft delete and statistics

### SQL-028 — Hard-delete client vs soft-delete server

- **Severity:** High
- **Confidence:** Confirmed
- **Object:** 18 tables with `is_deleted`/`deleted_at`/`deleted_by`; Room v33; pull filters
- **Workflow:** delete on a current client, delete on an old client, statistics, pull sync
- **Root cause:** the app moved from soft delete to hard delete (Room 31→32 deletes marked rows and drops the trio from 18 tables), but the server migration `20260925_business_soft_delete_columns.sql` still adds the trio to those tables (`:30-56`), and every pull path still filters `is_deleted = false` (e.g. `FinanceRepositoryImpl.kt:394-400`, `ExpenseRepositoryImpl.kt:126`, `ProfileRepositoryImpl.kt:45`). `20260919` drops `doctor_slot_exceptions.is_deleted`, then `20260925` re-adds it.
- **Failure scenario:**
  - An old v31 client deletes a row by setting `is_deleted = true`. The server keeps it; current clients never pull it (filter), so it vanishes from their view. `RemotePrune` may then delete the local row as “not in the remote live set” — fine — but the server row remains forever, invisible and unmaintained.
  - A current client hard-deletes a row; the server row is gone. An old client still has it locally and pushes an UPDATE (not a delete); the upsert re-creates it with `is_deleted = false`, so it reappears on all current clients.
  - Statistics count only non-deleted rows locally; server-side reports (if any) see the tombstones.
- **Impact:** resurrected records, invisible server rows, inconsistent statistics, mixed-version corruption during rollout.
- **Evidence:** `20260925…:30-56`; `AppDatabase.kt:326-390` (31→32 purge); `ReminderEntity.kt:78-80` (only entity retaining the trio); pull filters cited above.
- **Correction:** pick one model. Given the current app, the smaller consistent move is:
  1. stop writing soft-delete fields from the client (already true),
  2. keep the server columns for one deprecation window but make pulls **not** filter `is_deleted` (so tombstoned rows are visible and can be hard-deleted or ignored explicitly),
  3. add a migration that hard-deletes `is_deleted = true` rows and drops the columns once no v31 client is active.
  Alternatively, revert to soft delete end-to-end; that is a larger change.
- **Regression test:** old-client soft-delete row + current-client pull + current-client hard delete; assert no resurrection and no orphan tombstone.
- **Live check:** yes — count `is_deleted = true` rows per table and identify the oldest app version in use.

---

## 6. Time and identity

### SQL-030 — Postgres text timestamps are parsed 5.5 hours off

- **Severity:** Medium
- **Confidence:** Confirmed
- **Object:** `PatientUtils.isoToLong` → `DateUtils.isoToLong` → `parseDate`
- **Workflow:** sync conflict checks, “remote is newer” decisions, audit ordering
- **Root cause:** `Instant.parse` requires a `T` and a strict offset (`DateUtils.kt:204`). Postgres `now()::text` produces `2026-08-22 03:48:08.105121+00` (space, variable fractional digits). `Instant.parse` fails, then `parseDate` matches the `"yyyy-MM-dd HH:mm:ss"` pattern (`:84`) with `SimpleDateFormat`, which ignores the trailing `.105121+00` and interprets the wall time in **IST** (`:100`). The actual value is UTC.
- **Failure scenario:** remote `2026-08-22 03:48:08+00` is parsed as `2026-08-22 03:48:08 IST` = `2026-08-21 22:18:08 UTC`, i.e. 5.5 hours **older** than reality. A local edit made 5 hours after the remote change looks newer, so the conflict check uploads the local row and overwrites a newer remote change.
- **Impact:** last-write-wins is wrong for a 5.5-hour window on every text-timestamp table; audit/ledger ordering can be wrong; retention compares the wrong instant.
- **Evidence:** `DateUtils.kt:80-89,100-106,195-209`; CSV text timestamps; `SyncUploader.kt:504-508`.
- **Correction:** normalise on the server (real `timestamptz`) or parse Postgres text explicitly: replace the space with `T`, trim to 6 fractional digits, and parse with `OffsetDateTime`. In Kotlin:
  ```kotlin
  OffsetDateTime.parse(raw.replace(' ', 'T')).toInstant().toEpochMilli()
  ```
  with a fallback for values without an offset (interpret as UTC for server timestamps, IST for legacy local dates).
- **Regression test:** table of inputs (`2026-08-22 03:48:08.105121+00`, `2026-08-22T03:48:08.105121Z`, `2026-08-22`) → expected epoch; assert the UTC value equals `Instant.parse` for the ISO form.
- **Live check:** no.

### SQL-026 — Finance self-heal overwrites a pending edit and uses device timezone

> **Status at `93021c2`:** fixed. Both branches are gated on `pendingFinanceIds`, the pull no longer enqueues, and dates convert through IST (`FinanceRepositoryImpl.kt:408-452`; `DateUtils.kt:26`). See `09-final-sql-audit-report.md §4.3`. The analysis below describes `b91142a`.

- **Severity:** Medium
- **Confidence:** Confirmed
- **Object:** `FinanceRepositoryImpl.refreshTransactions`, `:406-458`
- **Workflow:** pull while a finance edit is queued; visit date normalisation
- **Root cause:**
  1. `pendingFinanceIds` gates the normal import (`:436-440`), but `if (local != null && wasHealed) shouldUpdateLocal = true` (`:443-446`) bypasses the gate, so a healed remote row overwrites a pending local edit.
  2. `visitDate` is computed with `ZoneId.systemDefault()` (`:418-419`), so the same visit can normalise to a different date on a device in another timezone.
- **Failure scenario:** staff edits an amount on device A; a pull on device A sees a remote row whose date differs from the visit date, “heals” it, overwrites the local pending amount, and enqueues the healed row. The edit is lost. A device in UTC+0 normalises a visit to the previous day, creating a date that disagrees with IST devices.
- **Impact:** lost finance edits; inconsistent transaction dates across devices; revenue reports differ.
- **Evidence:** `:407,418-419,436-446`.
- **Correction:** gate the heal on `!pendingFinanceIds.contains(id)`; compute the date in IST (`ZoneId.of("Asia/Kolkata")`) as the rest of the app does (`DateUtils.kt:100`).
- **Regression test:** pending finance edit + heal-needed remote row → assert the local edit survives; device timezone set to UTC → assert the healed date is the IST date.
- **Live check:** no.

---

## 7. Concurrency matrix

| Operation | Server protection | Gap |
|---|---|---|
| Two deductions from the same batch | `SELECT … FOR UPDATE` + negative guard (`20261022…:70-86`) | correct |
| Deduction vs batch move | Destination locked; **old batch not locked** (`:51-56`) | lost update / negative (`SQL-006`) |
| Two ledger INSERTs for the same batch | Row lock serialises | correct |
| Batch create retry | Upsert writes `0` | stock reset (`SQL-008`) |
| Two devices edit the same row | Client conflict check on `updated_at` | text timestamps + 5.5h skew (`SQL-030`); finance has no timestamp (`updatedAt = ""`, `SyncUploader.kt:202-205`) |
| Two devices delete the same row | DELETE is idempotent | fine |
| Patient delete vs child insert | No server transaction | child can be inserted after the parent check (`SQL-009`) |
| Rate limit during bulk sync | 600/min/user | partial group (`SQL-009`) |
| Receipt allocation | `INSERT … ON CONFLICT DO UPDATE … RETURNING` | correct, unless client supplies the number (`SQL-018`) |

---

## 8. Live checks

```sql
-- Stock drift and negative batches.
select * from public.inventory_reconciliation where drift <> 0;
select id, batch_number, remaining_quantity from public.vaccine_batches where remaining_quantity < 0;

-- Orphaned ledger/audit references.
select count(*) from public.inventory_transactions t
left join public.patient_visits v on v.id = t.visit_id
where t.visit_id is not null and v.id is null;

select count(*) from public.audit_logs a
left join public.patients p on p.id = a.patient_id
where a.patient_id is not null and p.id is null;

-- Tombstones.
select 'patients' t, count(*) from public.patients where is_deleted
union all select 'patient_visits', count(*) from public.patient_visits where is_deleted
union all select 'finance_transactions', count(*) from public.finance_transactions where is_deleted;

-- FK actions for delete-sensitive columns.
select conrelid::regclass, conname, confdeltype, pg_get_constraintdef(oid)
from pg_constraint
where contype='f'
  and confrelid in ('public.patients'::regclass, 'public.patient_visits'::regclass)
order by 1,2;
```

None was executed. `confdeltype`: `a` = NO ACTION, `r` = RESTRICT, `c` = CASCADE, `n` = SET NULL, `d` = SET DEFAULT.
