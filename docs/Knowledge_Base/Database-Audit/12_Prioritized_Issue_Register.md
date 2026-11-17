# Prioritized Issue Register

Severity: **S1** critical data corruption/data loss, **S2** high (wrong data, security, offline failure), **S3** medium, **S4** low/hygiene.
Confidence: **High** = code path proven; **Medium** = code path proven but reachability/live state depends on config or data; **Low** = structural risk not reproduced.

---

## DBA-01 — Stock trigger UPDATE arithmetic corrupts inventory

- **Severity:** S1 Critical
- **Confidence:** High (code); live corruption extent unknown
- **Status:** Confirmed defect. Contradicts Bug Register “NCC-002/NCC-003 fixed”.
- **Evidence:** `supabase/migrations/20261017_stock_trigger_duplicate_and_update_arithmetic.sql:66-104` (`v_delta := new.quantity - old.quantity`; then undo `- old.quantity`; then apply `+ v_delta`).
- **Reproduction (verified on PostgreSQL 18.2, 2026-10-22):** batch 0 + purchase `+10` -> 10; re-upsert the same row (UPDATE path) -> **0**. Batch 10 + vaccination `-2` -> 8; re-upsert the same row -> **10**. Batch 100 + purchase `+10` -> 110; edit that row to `+4` -> **94** (expected 104). Move `-2` from batch A (50 -> 48) to batch B (50) with quantity `-3` -> A 50, B **49** (expected B 47). An edit `-2 -> -3` (98 -> **99**, expected 97) is corrupted too; the only UPDATE that skips the bad undo is the coincidence `new.quantity = 2 * old.quantity`. Earlier pure-arithmetic examples in this register used a synthetic starting value and have been replaced by these observed sequences.
- **Root cause:** The function both undoes the old movement and applies the new-minus-old delta. The undo condition `v_delta <> old.quantity` (`new <> 2*old`) is true for nearly every update.
- **Impact:** Stock inflation/deflation, negative-guard rollback of legitimate ledger rows, retry-induced corruption, incorrect availability, wrong inventory valuations and purchase decisions. Repairing the function does not repair history.
- **Fix direction:** Make the UPDATE branch atomic and single-application:
  - same batch: `remaining = remaining + (new.quantity - old.quantity)`;
  - moved batch: `old.remaining -= old.quantity; new.remaining += new.quantity`.
  Remove the conditional undo. Add a trigger test matrix and a stock reconciliation/repair plan (separate approval).
- **Verification:** T-01 in `11_Test_Coverage_And_Plan.md`; replay ledger for each batch and assert `remaining_quantity == initial + sum(quantity)`.
- **Remediation (2026-10-22):** `supabase/migrations/20261022_fix_stock_trigger_update_arithmetic.sql` replaces the UPDATE branch and recreates exactly one stock trigger. `supabase/tests/stock_trigger_arithmetic.sql` runs in a rolled-back transaction and covers insert, purchase retry, `ON CONFLICT DO NOTHING` retry, edit, vaccination retry, move, delete and the negative guard. `SyncUploader.kt` now sends ledger CREATEs insert-only. On PostgreSQL 18.2 the old function failed at the first purchase retry (`expected 10, got 0`); the new function passed. The Kotlin change is uncompiled. Not deployed, and no historical stock was repaired. See `16_Phase1_Stock_Trigger_Remediation.md`.

---

## DBA-02 — Visit deletion no longer unlinks the stock ledger; FK behavior unknown

- **Severity:** S1 if the server FK exists, otherwise S2
- **Confidence:** Medium (source contradiction proven; live FK unknown)
- **Evidence:** `VaccinationRepositoryImpl.kt:429-430` says clearing `inventory_transactions.visit_id` is “no longer needed; visit_id stays”; `SyncUploader.kt:537-551` says the visit delete depends on that unlink and that an intact FK fails with `23503`; repo-wide grep finds **no** `clearVisitLink` implementation or caller.
- **Reproduction:** Create a visit with a stock deduction, delete the visit, let the sync group upload. If `inventory_transactions_visit_id_fkey` exists, the final `DELETE /patient_visits` is rejected, the group is marked FAILED, and the visit remains remotely/local queue stuck. Locally the visit is already deleted.
- **Root cause:** Step 2b was removed/changed without updating the uploader contract or proving the FK was dropped.
- **Impact:** Permanent sync failure on visit/patient deletion; remote orphan visits and ledger rows; local/remote divergence.
- **Fix direction:** Verify `pg_constraint` for the FK. If present, either (a) restore the unlink and force an explicit-null UPDATE **before** the visit DELETE in the same group, or (b) drop the FK deliberately and document the orphan-ledger policy. Do not leave the two comments contradicting each other.
- **Verification:** SQL constraint inspection + T-05-style integration test: create visit + ledger + delete; assert visit gone and ledger retained with null visit id (or documented retention).

---

## DBA-03 — Finance pull self-heal overwrites pending local edits

- **Severity:** S2 High
- **Confidence:** High
- **Evidence:** `FinanceRepositoryImpl.kt:413-431`: `shouldUpdateLocal` respects pending ids, but `if (local != null && wasHealed)` sets `finalEntity = normalizedRemote.copy(isSynced=false)` and `shouldUpdateLocal = true`, then enqueues UPDATE.
- **Reproduction:** Device edits a finance amount offline (queue PENDING, `isSynced=false`). Remote row has a date that the local heal logic considers different. Pull runs before push (or concurrently with another repeater). The pending row is replaced by remote values; the queued UPDATE then pushes the remote values back.
- **Root cause:** Heal branch bypasses both pending-queue and `isSynced` guards.
- **Impact:** Silent loss of income corrections; wrong totals; write storm.
- **Fix direction:** If the id is in `pendingFinanceIds` or `!local.isSynced`, never overwrite; at most record the mismatch. Do not enqueue sync work from a pull. If healing is still needed, make it an explicit maintenance task.
- **Verification:** T-02.

---

## DBA-04 — Finance date normalization uses device timezone

- **Severity:** S2 High
- **Confidence:** High
- **Evidence:** `FinanceRepositoryImpl.kt:52-59` and `:394-404`: `parseDate(visit.dateGiven).toInstant().atZone(ZoneId.systemDefault())`, despite `DateUtils` anchoring dates to `Asia/Kolkata`.
- **Reproduction:** Device set to `America/New_York`, visit date `9 May 2026` (IST midnight). Conversion yields `8 May 2026`. Finance row is written/healed to the previous day; the next pull sees the same mismatch and rewrites/re-enqueues again.
- **Root cause:** Device zone used where the clinic zone is required.
- **Impact:** Off-by-one transaction dates, wrong monthly/yearly totals, endless re-uploads, cross-device ping-pong.
- **Fix direction:** Use the existing `Date.toLocalDate()` IST extension (or explicit `ZoneId.of("Asia/Kolkata")`) everywhere. Stop enqueueing heal updates during pull. Add a timezone test matrix.
- **Verification:** T-08.

---

## DBA-05 — Patient deletion deletes reminders by local id, not server id

- **Severity:** S2 High
- **Confidence:** High
- **Evidence:** `PatientRepositoryImpl.kt:215,220-223` collects `ReminderEntity.id` and enqueues `REMINDERS` DELETE; `ReminderEntity.kt:145,168-170` shows local `id` is a random UUID and remote identity is `serverId`. `deleteVaccination` correctly uses `serverId ?: id` (`VaccinationRepositoryImpl.kt:450`).
- **Reproduction:** Pull a reminder on device B (local id X, serverId S), delete the patient on B. Queue sends DELETE for X; PostgREST deletes zero rows and returns success; S remains on the server. If the visit still exists remotely, the next refresh re-imports the orphan; if not, it remains as a server orphan.
- **Root cause:** Reminder identity mapping ignored in one cleanup path; deletes are ungrouped and LOW priority, so the patient delete can upload first.
- **Impact:** Patient data (reminder with patient name/vaccine) is not deleted remotely; repeated re-import attempts; possible FK failures; privacy/compliance issue.
- **Fix direction:** Use `serverId ?: id` in `deletePatient`, route both delete paths through one reminder-cleanup helper, add a `transactionGroupId`, and order child deletes before the patient delete.
- **Verification:** T-03.

---

## DBA-06 — Remote deletions never converge locally

- **Severity:** S2 High
- **Confidence:** High
- **Status:** Known open issue NCC-023; still present at HEAD.
- **Evidence:** Every pull loop is “for each remote row, upsert if not pending” and has no “delete local rows absent remotely” step: `PatientRepositoryImpl.kt:91-145`, `VaccinationRepositoryImpl.kt:91-145`, `FinanceRepositoryImpl.kt:379-435`, `ConsultationRepositoryImpl.kt:205-215`, waste/expense/borrow equivalents. Remote deletes are hard SQL deletes, so no tombstone reaches the client.
- **Reproduction:** Device A deletes a patient/visit/finance row; device B refreshes online. B keeps the row indefinitely; statistics, search, reminders and pickers still include it.
- **Root cause:** Upsert-only cache synchronization; hard deletes leave no marker.
- **Impact:** Stale medical/financial data, double-counted statistics, duplicate-looking records, wrong outstanding reminders, possible FK reuse.
- **Fix direction:** Add server tombstones (`deleted_at`/`deleted_by`) and a pull cursor, or perform a full ID diff per table with the pending-queue guard. Applying tombstones must be one transaction per table and must preserve local pending edits. Do not hard-delete server rows before clients can observe tombstones.
- **Verification:** T-05, plus two-device manual test.

---

## DBA-07 — Stock History is remote-only and the ledger is never pulled

- **Severity:** S2 High
- **Confidence:** High
- **Evidence:** `StockHistoryViewModel.kt:129-138,167-176` always passes `remoteOnly = true`; `InventoryRepositoryImpl.kt:277-292` routes that to `PatientRemoteDataSourceImpl.getStockHistoryPage`; no `refreshInventoryTransactions`/`from("inventory_transactions").select` pull exists. `INVENTORY_TRANSACTION` descriptor has no downloader.
- **Reproduction:** Open Stock History offline or on a fresh install: request fails/empty even though the local DB has batches. On another device, the ledger is absent entirely.
- **Root cause:** The inventory ledger was treated as upload-only while a primary screen was built on it.
- **Impact:** Broken offline behavior; no history after reinstall/device change; extra latency and data usage.
- **Fix direction:** Pull the ledger (paged/cursor) into Room or cache remote pages; render from Room. If the ledger must stay remote-only, label the screen online-only and provide a local fallback showing batches/movements.
- **Verification:** Offline instrumentation/UI test + fresh-install test.

---

## DBA-08 — RLS allows every active staff role to write clinical and financial data

- **Severity:** S2 High
- **Confidence:** High (as written in migrations; live state unknown)
- **Evidence:** `20260929000000_security_baseline.sql:40-88` creates full CRUD for 20 tables using only `is_active_staff()`. `20261013120000_role_policy_carveouts.sql` narrows only expenses, doctor schedules and device rows. `UserRole` includes `receptionist`, `nurse`, `inventory_manager` (`Profile.kt:7-9`), while UI gates assume admin/doctor (`PatientDetailsScreen.kt:48-50`, `PatientListScreen.kt:58-59`).
- **Reproduction:** Log in as receptionist/nurse; call `DELETE /rest/v1/patients?id=eq.<id>` or `DELETE /rest/v1/finance_transactions?id=eq.<id>` with the session token. RLS allows it.
- **Root cause:** Coarse single-clinic access model plus UI-only role checks for most tables.
- **Impact:** Any staff account (or stolen token) can delete patients, rewrite visits/finance, adjust stock, or remove reminders. Audit logs still record the action, but destruction is possible.
- **Fix direction:** Approve a role→table→operation matrix and implement RLS policies for clinical, finance and inventory tables. At minimum restrict destructive operations (patients, visits, finance, stock) to admin (and doctor where clinically required). Keep reads open per product decision.
- **Verification:** T-07 role matrix against a disposable project.

---

## DBA-09 — Queue dedupe is non-atomic

- **Severity:** S2 High (because duplicates feed DBA-01)
- **Confidence:** High
- **Evidence:** `SyncRepositoryImpl.kt:68-83` calls `deletePendingDuplicates` then `enqueue`; no unique index on `(entityName, entityId, operation, status)`; code comment admits concurrent enqueues can duplicate. Schema has only status/priority indexes.
- **Reproduction:** Two coroutines save the same entity/operation concurrently (double tap, two ViewModels). Both delete zero rows, both insert; two uploads of the same write.
- **Root cause:** Read-then-write dedupe without a database uniqueness constraint.
- **Impact:** Duplicate uploads, wasted requests; on the stock path each duplicate retry is an UPDATE and corrupts stock (DBA-01).
- **Fix direction:** Add a partial unique index on PENDING rows and use `INSERT OR IGNORE`/upsert; or serialize enqueue in one transaction with a unique key. Keep SYNCING rows exempt.
- **Verification:** T-06.

---

## DBA-10 — Borrow return status uses the caller's stale remaining quantity

- **Severity:** S2 High
- **Confidence:** High (single device); Medium (multi-device reach)
- **Evidence:** `BorrowRepositoryImpl.kt:215-221` correctly recomputes `outstanding`, but `:276` marks returned with `if (quantity >= remainingQuantity)`, where `remainingQuantity` is a caller parameter (`:200`). The function never uses the recomputed outstanding for that decision.
- **Reproduction:** Borrow 10; previous return 1; dialog still passes `remainingQuantity=10`; return 9. `9 >= 10` is false, so the borrow remains ACTIVE even though nothing is outstanding. Reverse: stale `remainingQuantity=1`, return 2 (allowed because outstanding=10), `2 >= 1` marks RETURNED with 8 still out.
- **Root cause:** Two sources of truth for outstanding; UI value trusted for a state transition.
- **Impact:** Wrong active/returned lists, missing follow-up on outstanding stock, premature closure; with multiple devices, over-return can inflate stock because only the local ledger caps it.
- **Fix direction:** Compute `newReturned = localReturned + quantity`; mark returned iff `newReturned >= borrow.quantity`. Remove the `remainingQuantity` parameter. Add a server-side aggregate guard (RPC/trigger) for multi-device returns.
- **Verification:** T-04 + two-device return test.

---

## DBA-11 — Repository refreshes bypass single-flight

- **Severity:** S3 Medium
- **Confidence:** High
- **Evidence:** `SingleFlight` used only in `RefreshDataUseCase` and `SyncRepositoryImpl.processNextItems`; screens call `refreshBorrows()`, `refreshInventory()`, `refreshReminders()`, `refreshProfiles()` directly (`BorrowedViewModel.kt:82,174-175`, etc.).
- **Reproduction:** Launch app; pipeline pulls inventory while Borrowed screen's manual refresh pulls it again; reminders screens each pull reminders on open.
- **Root cause:** No per-table refresh coordinator.
- **Impact:** Duplicate network requests, battery/data waste, latency, duplicate heal side effects.
- **Fix direction:** Keyed single-flight per table, or expose one refresh coordinator and remove repository refresh calls from screens.
- **Verification:** Request-count instrumentation test.

---

## DBA-12 — Parentless child pulls can abort a refresh step

- **Severity:** S3 Medium
- **Confidence:** Medium
- **Evidence:** `ConsultationRepositoryImpl.kt:205-215` inserts every remote consultation with only a pending-id guard, no patient/visit existence check. `PersonalReminderRepositoryImpl.kt:172-183` does the same. In contrast, `refreshReminders` explicitly skips missing-parent rows.
- **Reproduction:** Remote consultation/personal reminder whose local parent was deleted but whose server delete is pending/failed. Room FK enforcement rejects the insert; the whole step fails (repeated every refresh).
- **Root cause:** Inconsistent child-pull validation.
- **Impact:** Stale consultations/reminders; repeated refresh errors; other steps still run, so the failure is partly masked.
- **Fix direction:** Apply the reminder-style orphan guard; skip and log, or defer until the parent arrives.
- **Verification:** Room/integration test with dangling remote row.

---

## DBA-13 — Vaccination edit rewrites `created_at`

- **Severity:** S3 Medium
- **Confidence:** High
- **Evidence:** `VaccinationEntity.kt:107,130`: `timestamp = updatedAt.ifBlank { now }`, then `createdAt = timestamp` in `toEntity`. Consultation edit preserves `createdAt` (`ConsultationRepositoryImpl.kt:79-81`); vaccination edit does not.
- **Reproduction:** Create a visit, wait, edit it. Local and uploaded `created_at` become the edit time; receipt remains correct.
- **Root cause:** Missing “preserve persisted creation time” logic in the edit mapper.
- **Impact:** Audit/lineage timestamps wrong; any future report using `created_at` is wrong.
- **Fix direction:** Carry `existing.createdAt` in `addVaccination`, as consultation/patient paths do.
- **Verification:** Room test asserting `createdAt` unchanged across edit.

---

## DBA-14 — Device timezone used for “today”, notifications and display

- **Severity:** S3 Medium
- **Confidence:** High
- **Evidence:** `DashboardViewModel.kt:123` (`LocalDate.now()`), `DailySummaryWorker.kt:29`, `PersonalReminderNotificationWorker.kt:25`, `StockHistoryViewModel.kt:197`, `AddExpenseViewModel.kt:164`, `ExpenseListViewModel.kt:131`, `DateUtils.kt:121,146,155,165`, `FinanceCalculator.kt:338-346`.
- **Reproduction:** Set device to UTC-8; open dashboard after 00:00 IST but before 00:00 local → wrong “today”; stock-history filter off by one day; finance monthly bucket can shift.
- **Root cause:** Incomplete adoption of the IST helper layer.
- **Impact:** Wrong daily lists, reminders, filters and reports for traveling staff; date-dependent writes can store the wrong day.
- **Fix direction:** Central `clinicToday()`, `clinicZone()`, and IST formatters; replace all `systemDefault`/`LocalDate.now()` in business paths. Leave device-zone formatting only for explicitly local timestamps.
- **Verification:** T-08.

---

## DBA-15 — Audit history is remote-only

- **Severity:** S3 Medium (deliberate design)
- **Confidence:** High
- **Evidence:** `FullAuditLogViewModel.kt:14-18` states it is intentionally remote-only; the Room `audit_logs` staging table is not read by that screen.
- **Reproduction:** Disable the network and open Full Audit History. The screen shows an error or an empty list even though `audit_logs` contains locally staged rows for recent actions.
- **Root cause:** `FullAuditLogViewModel` was wired only to `AuditLogRepository.getPaged` (PostgREST); the Room staging table has no read path for this screen.
- **Impact:** No audit history offline or on a fresh install before sync; inconsistent with the local-first promise.
- **Fix direction:** Either cache pages locally or label the screen “requires connection”. Do not silently show an empty list on network error.
- **Verification:** Offline UI test.

---

## DBA-16 — Audit logger commits outside the business transaction

- **Severity:** S3 Medium
- **Confidence:** High
- **Evidence:** `AuditLogger.kt` uses its own `CoroutineScope(Dispatchers.IO + SupervisorJob())`; comment explicitly says it survives a caller rollback. It enqueues `AUDIT_LOG` independently.
- **Reproduction:** A business transaction fails after calling `auditLogger.recordLog`; the audit row remains and uploads, claiming an action that never committed.
- **Root cause:** Fire-and-forget design.
- **Impact:** Misleading audit trail; ordering not guaranteed relative to the business row.
- **Fix direction:** Make audit insertion a suspend call in the caller's transaction (or at least only log after commit). If fire-and-forget is retained, record a correlation id and status.
- **Verification:** Rollback test asserting no audit row.

---

## DBA-17 — Missing sync-queue lookup index

- **Severity:** S4 Low (until queue size grows)
- **Confidence:** High
- **Evidence:** v33 schema has only `index_sync_queue_status` and `index_sync_queue_priority`; dedupe/`unsyncedIdSet`/`isUnsynced` filter on `entityName/entityId/status` (`SyncQueueDao.kt:30,65,83`).
- **Reproduction:** With a 10k-row queue, save the same entity repeatedly and inspect `EXPLAIN QUERY PLAN`; the dedupe and `unsyncedIdSet` queries scan the table.
- **Root cause:** Only `status` and `priority` are indexed; the lookup columns `(entityName, entityId, status)` are not.
- **Impact:** Full scans on every enqueue and every pull guard; normally small queue, but a large failed backlog degrades.
- **Fix direction:** Add `(entityName, entityId, status)` index in a future migration after measuring queue size.
- **Verification:** `EXPLAIN QUERY PLAN` on a 10k-row queue.

---

## DBA-18 — Upload-only descriptors have a no-op “download” conflict path

- **Severity:** S3 Medium
- **Confidence:** High (code); reachability low
- **Evidence:** default `download = { _, _ -> Unit }` (`SyncUploader.kt:45`); `TRANSACTION`/`INVENTORY_TRANSACTION` omit it (`:168-185`). The remote-newer branch calls `descriptor.download` then returns and marks the item uploaded (`:521-523`).
- **Reproduction:** Unit-test the uploader with a `TRANSACTION` local row and a fake remote row that compares newer. The branch calls the no-op downloader, deletes the queue item, and leaves the local row unchanged.
- **Root cause:** The descriptor contract makes `download` optional, and the conflict path treats a missing downloader as a successful refresh instead of declining the conflict.
- **Impact:** If a remote ledger row ever appears newer, the local upload is dropped and local state is not refreshed; silent divergence.
- **Fix direction:** Implement a downloader or make the conflict branch skip (upload local) for append-only ledger entities; never treat a no-op as a successful sync.
- **Verification:** Unit test with a fake remote-newer response.

---

## DBA-19 — Reminder uniqueness keys differ between Room and Postgres

- **Severity:** S3 Medium
- **Confidence:** Low/Medium (reachability not proven)
- **Evidence:** local unique index `(patientId, originalVisitId, dueDate, type, vaccineName)` (`ReminderEntity.kt:26`); server unique index `(patient_id, original_visit_id, vaccine_name, type)` (`20261016:102-111`). `saveNextVaccination` looks up by the five-column key including dueDate (`ReminderRepositoryImpl.kt:197-203`).
- **Reproduction:** Same visit/vaccine/type saved twice with different due dates outside the edit reconcile path (duplicate spec or retry); local accepts two rows, server rejects the second with 23505.
- **Root cause:** The local and server uniqueness rules were designed independently: Room includes `dueDate`, the server unique index omits it, so key-equivalent rows differ by database.
- **Impact:** Permanent sync failure, local/server divergence.
- **Fix direction:** Align the keys (probably include due_date server-side, or remove it locally and overwrite), after de-duplicating existing rows.
- **Verification:** SQL unique-violation test + local insert test.

---

## DBA-20 — Unbounded pulls and unbounded list Flows

- **Severity:** S3 Medium
- **Confidence:** High (static)
- **Evidence:** `fetchAllPatients`, all `refresh*` selects have no `range`/cursor; `getAllVaccinationsWithItems()` loads every visit+item; finance `getAllTransactions` loads all rows.
- **Reproduction:** Seed ~20k visits and open the dashboard. The `getAllVaccinationsWithItems` Flow and the startup pull both scale with total history, not with the visible day/range.
- **Root cause:** No cursor/range in pull queries and no pagination in the aggregate Flows; the screen requests everything.
- **Impact:** Startup/pull time and memory grow with clinic history; mobile data and battery cost.
- **Fix direction:** Cursor pull by server `updated_at` (and tombstones for deletes); paginate screens that do not need all rows; keep full aggregates server-side or in a local precomputed table.
- **Verification:** Payload/row-count benchmark on realistic data.

---

## DBA-21 — Finance ordering defeats its index

- **Severity:** S3 Medium
- **Confidence:** High (static)
- **Evidence:** `FinanceDao.kt:15-19`: `ORDER BY COALESCE(transaction_date, substr(timestamp,1,10)) DESC`; index exists on `transaction_date`, but the expression prevents its use.
- **Reproduction:** Run `EXPLAIN QUERY PLAN` for `getAllTransactions()`; the plan scans `finance_transactions` and sorts with a temp B-tree even though `transaction_date` is indexed.
- **Root cause:** The `COALESCE(transaction_date, substr(timestamp,1,10))` expression is not covered by the plain `transaction_date` index.
- **Impact:** Full scan + temp sort for finance/statistics on every emission.
- **Fix direction:** Backfill `transaction_date` and order by it directly, or add an expression index (SQLite supports indexes on expressions) matching the query.
- **Verification:** `EXPLAIN QUERY PLAN` before/after.

---

## DBA-22 — Patient search is a leading-wildcard scan plus subquery

- **Severity:** S3 Medium
- **Confidence:** High (static)
- **Evidence:** `PatientDao.kt:44-49`: `name LIKE :q OR phone LIKE :q ... OR id IN (SELECT patientId FROM patient_visits WHERE vaccineNames LIKE :q ...)`; caller passes `%query%`.
- **Reproduction:** With ~20k patients, type a two-character query; the plan shows a full `patients` scan plus the correlated `patient_visits` LIKE subquery.
- **Root cause:** Leading-wildcard `LIKE` patterns (`%query%`) and the `IN (SELECT ... LIKE ...)` subquery cannot use a normal B-tree prefix index.
- **Impact:** Full patient scan and correlated visit scan on every keystroke (depending on debounce).
- **Fix direction:** Prefix search/FTS for name, normalized phone index, or a dedicated search table; avoid the visits subquery on the hot path.
- **Verification:** `EXPLAIN QUERY PLAN` + keystroke latency benchmark.

---

## DBA-23 — Patient refresh conflict branch bypasses pending protection

- **Severity:** S3 Medium
- **Confidence:** Medium (narrow precondition)
- **Evidence:** `PatientRepositoryImpl.kt:118-131`: when a clinic-id owner differs, the remote row is upserted as synced and the loop `continue`s before the `(existingLocal == null || existingLocal.isSynced) && id !in pendingPatientIds` guard at `:134`.
- **Reproduction:** Local patient X has a pending edit and a clinic id that collides with another local patient Y; remote X arrives with the same real clinic id as Y. The conflict branch overwrites X with remote fields; the pending queue then uploads the overwritten data.
- **Root cause:** The conflict branch `continue`s before the pending-queue/`isSynced` guard, so a clinic-ID collision is resolved against remote data even when the local row has an unsynced edit.
- **Impact:** Loss of local patient edits in a collision scenario.
- **Fix direction:** Run the pending guard first; when resolving a conflict, preserve the local row's pending fields or defer the remote upsert.
- **Verification:** Room test with pending row + clinic-id collision.

---

## DBA-24 — Trigger installation/deployment is an operational dependency

- **Severity:** S3 Medium (could be S1 if missing)
- **Confidence:** Medium (live state unknown)
- **Evidence:** `20261018_ensure_stock_trigger_installed.sql` exists because an earlier migration silently skipped trigger installation; `20261017` assumes exactly one trigger and drops the legacy one. The repository cannot prove which migrations production has.
- **Reproduction:** Run the read-only trigger query in `15_Unknowns_And_Live_DB_Gaps.md` against production. Zero non-internal triggers means purchases never move stock; two means every movement applies twice.
- **Root cause:** Trigger installation was historically conditional/out of band; neither the app nor CI asserts the deployed trigger state.
- **Impact:** If the trigger is missing, batches created with `remaining_quantity=0` never rise when the purchase ledger arrives; if the legacy trigger survives alongside the new one, every movement applies twice.
- **Fix direction:** Deployment health check query (`count(*) from pg_trigger ...`), migration ledger verification, and a startup/admin panel warning. Do not rely on code review.
- **Verification:** Run the verification queries in `15_Unknowns_And_Live_DB_Gaps.md` against a read-only production connection.

---

## DBA-25 — Local sequential clinic IDs can collide across devices

- **Severity:** S3 Medium
- **Confidence:** High for local generation; Medium for collision frequency
- **Evidence:** `PatientIdGenerator.kt:16-38` generates `NEO-<max+1>` from the local table only; `PatientRepositoryImpl.kt:164-172` uses it for new patients; `PatientClinicIdMigrationWorker.kt:40-66` assigns bulk IDs and enqueues PATIENT UPDATEs; the server has a unique index on `patients.patient_clinic_id` (`20261016:113-130`).
- **Reproduction:** Two offline devices both see max `NEO-1010`; each creates `NEO-1011` for a different patient. The first uploads; the second gets `23505`, the queue item becomes FAILED, and the local patient keeps an ID that cannot sync. The next patient pull sees the collision and renames the local one to `...-CONFLICT-<suffix>` (or leaves the unsynced row).
- **Root cause:** A database-unique business identifier is generated from a local scan with no server reservation or collision retry.
- **Impact:** Patient identifiers diverge, sync failures, confusing renamed IDs, possible duplicate patient records.
- **Fix direction:** Issue clinic IDs server-side (sequence/RPC) or use a random suffix; at minimum catch `23505` and retry with a newly generated ID. Run the legacy migration once, coordinated, not independently per device.
- **Verification:** Two-device offline create test + SQL unique-constraint test.

## DBA-26 — Patient merge deletes duplicate's clinical/financial history instead of moving it

- **Severity:** S2 High
- **Confidence:** High
- **Evidence:** `MergePatientsUseCase.kt:32-86` transfers only vaccinations, reminders and inventory-transaction patient ids, then calls `patientRepository.deletePatient(dupId)`; `PatientRepositoryImpl.kt:245-254` deletes consultations and enqueues their DELETEs, and `deleteVaccination`/consultation deletion removes linked finance rows; notes and other children are not transferred. The moved-row UPDATEs are enqueued with `MEDIUM`/`LOW` priority and no shared `transactionGroupId`, while the duplicate PATIENT DELETE is also `MEDIUM`.
- **Reproduction:** Merge a duplicate that has one consultation with income and one patient note into the master. The duplicate's consultation/finance/note are deleted locally and queued for remote deletion; the master receives none of them. If the parent DELETE uploads before the moved vaccination/reminder UPDATEs, a server cascade can also remove the rows that were supposed to survive.
- **Root cause:** Merge is implemented as “move three tables, then delete the patient” rather than a complete ownership transfer with a single ordered sync group.
- **Impact:** Permanent loss of clinical notes, consultations and income during deduplication; remote cascade risk for moved visits/reminders.
- **Fix direction:** Enumerate every patient-owned table; transfer consultations, finance, notes, todos, personal reminders and ledger patient ids to the master. Wrap all updates + the final delete in one `transactionGroupId`, with the parent delete last. Add an integrity test that merges a fully populated duplicate and asserts zero child rows were lost.
- **Verification:** T-10.

## Positive controls (do not regress)

| Control | Evidence |
| --- | --- |
| Local write + outbox in one transaction | repositories use `TransactionRunner`/`withTransaction` |
| Dependency ordering for uploads | `SyncOrderingTest` |
| Receipt assigned once, immutable | `20260824_receipt_numbering.sql` |
| Audit append-only server-side | baseline policies |
| Profile privilege trigger | `20261013_profiles_privileged_fields_guard.sql` |
| Expense/doctor-schedule role carve-outs | `20261013120000_role_policy_carveouts.sql` |
| Private patient-docs bucket | `20261016:50-62` |
| Write rate limit | `20260929001000_write_rate_limit.sql` |
| Room encryption | `AppDatabase.kt:442-460` |
| Upserts preserve children | `PullUpsertPreservesChildrenTest` |
| Reminder reads filter soft deletes | `ReminderSoftDeleteFilterTest` |
| Patient cache generation guard | `PatientCache` + test |
| Single-flight for upload/pipeline | `SingleFlight` + tests |
