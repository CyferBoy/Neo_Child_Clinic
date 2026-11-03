# SQL Audit Findings Verification

**Status:** verification baseline for `b91142a`. No application code, migration, Supabase policy, production record, or configuration was changed during verification. Approval was received on 2026-10-26 for a code-only subset; those unverified working-tree edits are recorded in `11-remediation-plan.md` and `18-master-bug-register.md`, not in this snapshot.

- **Repository:** `Neo_Child_Clinic`
- **Audit evidence snapshot:** `ca4bed4` plus the dirty `SyncUploader.kt` recorded in `docs/Knowledge_Base/Database-Audit/01_Executive_Summary.md:3-7`.
- **Source inspected:** `main` at `b91142a` (2026-10-25), clean worktree.
- **Audit date of this pass:** 2026-10-26.
- **Primary source register:** `docs/Knowledge_Base/Database-Audit/12_Prioritized_Issue_Register.md:1-364` (DBA-01…DBA-26).
- **Live Supabase:** not available. No read-only database connection, staging project, credentials, Android SDK/device, Gradle build, or production access was used.
- **No finding is confirmed merely because a prior register says “Fixed”.** DBA-01, DBA-06, DBA-12, DBA-16 and DBA-25 were re-read against the original reproduction at current HEAD.

## 1. Result summary

| Result | Count | Findings |
|---|---:|---|
| Already fixed at HEAD, source-verified | 3 | DBA-01, DBA-06, DBA-25 |
| Partially fixed at HEAD | 2 | DBA-12, DBA-16 |
| Confirmed open at HEAD | 18 | DBA-03, DBA-04, DBA-05, DBA-07, DBA-08, DBA-09, DBA-10, DBA-11, DBA-13, DBA-14, DBA-15, DBA-17, DBA-18, DBA-20, DBA-21, DBA-22, DBA-23, DBA-26 |
| Code-confirmed, live state required to close | 3 | DBA-02, DBA-19, DBA-24 |

**Correct interpretation of “already fixed”:** the source contains a fix that addresses the named reproduction. It does **not** prove the Supabase project has that migration, that the historical stock data is correct, or that the fix is regression-protected on a device. DBA-01’s arithmetic test was executed locally; DBA-06 and DBA-25 were source-verified only.

**Disproved as stated at HEAD: 2** — DBA-06 (remote deletion convergence now exists through `RemotePrune`) and DBA-25 (the reported cross-device sequential-ID collision is addressed by a per-install suffix). Both were true findings against the audit snapshot, so both are also **regression-risk** findings: VER-04/VER-05 for DBA-06, and a 1/65,536 suffix collision with no `23505` retry for DBA-25.

## 2. Verification matrix

| ID | Result | Evidence at HEAD | Why it remains open / residual risk |
|---|---|---|---|
| **DBA-01** | **Already fixed at HEAD**; deployment and historical data unverified | `supabase/migrations/20261022_fix_stock_trigger_update_arithmetic.sql:28-117`; `supabase/tests/stock_trigger_arithmetic.sql:1-121` | Final function applies INSERT/DELETE/same-batch UPDATE/moved-batch UPDATE once. Local PostgreSQL 18.2 run printed `PASS: stock trigger arithmetic`. The migration is not proven deployed; historical `remaining_quantity` values are not repaired. |
| **DBA-02** | **Confirmed code contradiction**; live FK required | `app/src/main/java/com/neochildclinic/feature/vaccination/data/VaccinationRepositoryImpl.kt:452-453`; `app/src/main/java/com/neochildclinic/core/sync/SyncUploader.kt:545-557`, `:644-645`; `app/src/main/java/com/neochildclinic/feature/inventory/data/InventoryRepositoryImpl.kt:220-240` | `deleteVaccination` now deliberately keeps `inventory_transactions.visit_id`; uploader comments still describe a nulling step that does not exist. If a RESTRICT/NO ACTION FK exists, the final visit DELETE fails with `23503`. Live `pg_constraint` is required. |
| **DBA-03** | **Confirmed open** | `app/src/main/java/com/neochildclinic/feature/finance/data/FinanceRepositoryImpl.kt:436-457` | The `local != null && wasHealed` branch sets `shouldUpdateLocal = true` after the pending guard and enqueues an UPDATE. A pending local finance edit can be overwritten by the pulled remote value and then pushed back. |
| **DBA-04** | **Confirmed open** | `app/src/main/java/com/neochildclinic/feature/finance/data/FinanceRepositoryImpl.kt:55-62`, `:415-423` | Clinic dates are converted using `ZoneId.systemDefault()`; a parse failure returns `null` and the caller falls back to the current instant. A device outside IST can move a finance row one day and make it invisible to date-filtered totals. |
| **DBA-05** | **Confirmed open** | `app/src/main/java/com/neochildclinic/feature/patient/data/PatientRepositoryImpl.kt:246-292`; `app/src/main/java/com/neochildclinic/data/local/entity/ReminderEntity.kt:36-39` | Patient cleanup enqueues the reminder’s local `id`, not `serverId`; patient DELETE is MEDIUM and reminder DELETEs are LOW with no shared group. The remote reminder can remain, and the patient delete can upload first. |
| **DBA-06** | **Disproved at HEAD** (true at audit snapshot) | `app/src/main/java/com/neochildclinic/core/sync/RemotePrune.kt:1-71`; `app/src/main/java/com/neochildclinic/feature/patient/data/PatientRepositoryImpl.kt:175-190`; `app/src/main/java/com/neochildclinic/feature/finance/data/FinanceRepositoryImpl.kt:460-467`; `app/src/main/java/com/neochildclinic/feature/vaccination/data/VaccinationRepositoryImpl.kt:147-160` | `RemotePrune`/absence reconciliation now exists for patients, visits and vaccination items, finance, borrow, expenses, reminders, consultations, todos and doctor slots. It was added after `ca4bed4`. Residual risk: offset pagination plus pruning can transiently delete a synced local row if the remote set shifts mid-pull (see VER-04). |
| **DBA-07** | **Confirmed open** | `app/src/main/java/com/neochildclinic/feature/inventory/presentation/StockHistoryViewModel.kt:129-138`, `:167-176`; `app/src/main/java/com/neochildclinic/feature/inventory/data/InventoryRepositoryImpl.kt:295-310` | The screen always passes `remoteOnly = true`; `INVENTORY_TRANSACTION` has no downloader (`SyncUploader.kt:178-185`) and `refreshInventory()` pulls only vaccines/batches. Offline history and fresh-install local history are unavailable. |
| **DBA-08** | **Confirmed open** | `supabase/migrations/20260929000000_security_baseline.sql:36-80`, `:91-147`; `supabase/migrations/20261013120000_role_policy_carveouts.sql:1-120` | The baseline creates blanket CRUD for 20 tables using only `is_active_staff()`. Only expenses, doctor schedules, audit logs, profiles and user devices are narrowed. Any active staff token can delete patients, visits, finance, stock and reminders. Live policies were not inspected. |
| **DBA-09** | **Confirmed open** | `app/src/main/java/com/neochildclinic/core/sync/SyncRepositoryImpl.kt:68-83`; `app/src/main/java/com/neochildclinic/data/local/dao/SyncQueueDao.kt:12-15` | Dedupe is a read (`deletePendingDuplicates`) followed by a write. The Room v33 schema has no unique constraint for pending rows. Two concurrent enqueues can both survive and upload the same write. |
| **DBA-10** | **Confirmed open** | `app/src/main/java/com/neochildclinic/feature/borrowed/data/BorrowRepositoryImpl.kt:200-233`; `app/src/main/java/com/neochildclinic/feature/borrowed/presentation/BorrowedViewModel.kt:216-227` | The repository recomputes outstanding quantity, but the final `markReturned` decision still uses the caller’s `remainingQuantity`. A stale dialog can close a borrow with stock still out, or leave it active with nothing out. |
| **DBA-11** | **Confirmed open** | `app/src/main/java/com/neochildclinic/feature/sync/domain/RefreshDataUseCase.kt:1-180`; direct repository refreshes in `BorrowedViewModel.kt` and other screens | Single-flight wraps only `RefreshDataUseCase` and the uploader pipeline. Screen-level `refreshBorrows()`, `refreshInventory()`, `refreshReminders()` and similar calls can overlap the pipeline and each other. |
| **DBA-12** | **Partially fixed** | Fixed: `app/src/main/java/com/neochildclinic/feature/consultation/data/ConsultationRepositoryImpl.kt:220-226`. Open: `app/src/main/java/com/neochildclinic/feature/personalreminder/data/PersonalReminderRepositoryImpl.kt:189-196`; FK at `app/src/main/java/com/neochildclinic/data/local/entity/PersonalReminderEntity.kt:26-40` | Consultation refresh now skips missing local visits. Personal reminders still insert without a patient-existence check; a dangling remote row aborts that refresh step on the Room FK. |
| **DBA-13** | **Confirmed open** | `app/src/main/java/com/neochildclinic/data/local/entity/VaccinationEntity.kt:130-133`; `app/src/main/java/com/neochildclinic/feature/vaccination/data/VaccinationRepositoryImpl.kt:283-310` | `toEntity` sets `createdAt = timestamp` (the edit time). The edit path does not carry `existing.createdAt`, so editing a visit rewrites creation time locally and remotely. |
| **DBA-14** | **Confirmed open** | `app/src/main/java/com/neochildclinic/feature/dashboard/presentation/DashboardViewModel.kt:121`; `app/src/main/java/com/neochildclinic/feature/inventory/presentation/StockHistoryViewModel.kt:194-198`; `docs/Knowledge_Base/Database-Audit/12_Prioritized_Issue_Register.md:183-192` | Business-day logic still uses device-default time (`LocalDate.now()`, `ZoneId.systemDefault()`). A device outside IST can show the wrong day, stock-history window and finance bucket. |
| **DBA-15** | **Confirmed open design gap** | `app/src/main/java/com/neochildclinic/feature/audit/presentation/FullAuditLogViewModel.kt:14-18`; `app/src/main/java/com/neochildclinic/core/logger/AuditLogger.kt:15-17` | The full audit screen is intentionally remote-only and does not read the local staging table. Offline or fresh-install history is empty/error. |
| **DBA-16** | **Partially fixed** | `app/src/main/java/com/neochildclinic/core/logger/AuditLogger.kt:26-49`, `:54-97`; `app/src/main/java/com/neochildclinic/feature/borrowed/data/BorrowRepositoryImpl.kt:155-161` | `recordLog` is suspendable and can join a caller transaction, but `log()` is still fire-and-forget and is still used inside a transaction on at least the borrow create path. The audit row can survive a rollback. |
| **DBA-17** | **Confirmed open** | `app/src/main/java/com/neochildclinic/core/sync/SyncRepositoryImpl.kt:68-83`; Room v33 `sync_queue` indices | Lookups filter on `entityName/entityId/status`, but only status and priority are indexed. Large failed backlogs scan the queue. |
| **DBA-18** | **Confirmed open** | `app/src/main/java/com/neochildclinic/core/sync/SyncUploader.kt:42-47`, `:169-185`, `:525-531` | The descriptor download default is a no-op. `TRANSACTION`/`INVENTORY_TRANSACTION` omit a downloader, so a remote-newer conflict calls the no-op, returns, and deletes the queue item as if refreshed. Reachability is low but the silent-divergence path is real. |
| **DBA-19** | **Confirmed static mismatch**; live unique index unverified | `app/src/main/java/com/neochildclinic/data/local/entity/ReminderEntity.kt:26`; `supabase/migrations/20261016_rls_index_and_stock_trigger_hardening.sql:102-111`; `app/src/main/java/com/neochildclinic/feature/reminder/data/ReminderRepositoryImpl.kt:197-203` | Local key includes `dueDate`; the server key omits it, and the server index is created only if no duplicates exist. A same-event reminder with a different due date can be accepted locally and rejected remotely with `23505`. |
| **DBA-20** | **Confirmed open performance risk**; pagination partially added | `app/src/main/java/com/neochildclinic/core/sync/Pagination.kt:14-27`; `app/src/main/java/com/neochildclinic/feature/inventory/data/InventoryRepositoryImpl.kt:641-695`; `app/src/main/java/com/neochildclinic/core/sync/RemotePrune.kt:1-71` | Pulls now page, but still fetch whole tables with offset ranges; aggregate Flows still load all visits/items/transactions. Offset pages are not a snapshot. Do not confuse page size with bounded history. |
| **DBA-21** | **Confirmed open** | `app/src/main/java/com/neochildclinic/data/local/dao/FinanceDao.kt:15-19`; `app/src/main/java/com/neochildclinic/feature/finance/data/FinanceRepositoryImpl.kt:64-65` | The query orders by `COALESCE(transaction_date, substr(timestamp,1,10))`. The index on `transaction_date` cannot serve that expression, so the plan scans and sorts. |
| **DBA-22** | **Confirmed open** | `app/src/main/java/com/neochildclinic/data/local/dao/PatientDao.kt:44-49`; `app/src/main/java/com/neochildclinic/feature/patient/domain/SearchPatientsUseCase.kt:19` | Name/phone search uses leading-wildcard `LIKE`; visit search uses an `IN (SELECT … LIKE …)` subquery. At clinic scale it is tolerable; at ~20k patients it is a full scan per keystroke. |
| **DBA-23** | **Confirmed open** | `app/src/main/java/com/neochildclinic/feature/patient/data/PatientRepositoryImpl.kt:108-169`; `app/src/main/java/com/neochildclinic/core/sync/SyncRepositoryImpl.kt:61-83` | The clinic-ID conflict branch can upsert and `continue` before the pending/isSynced guard. A pending local patient edit can be overwritten in a collision. Delete/merge child operations also lack a shared sync group. |
| **DBA-24** | **Confirmed deployment risk**; live state unverified | `supabase/migrations/20261022_fix_stock_trigger_update_arithmetic.sql:28-117`; `docs/bug-audit/16-release-readiness.md:12-16`; `.github/workflows/supabase-sql.yml:1` | The final trigger is correct in source, but the release note lists the older `20261021000000`/`20261021001000`, not the final `20261022`, and the repository cannot prove which migrations production applied. A local reproduction of a re-apply with a stray trigger left two active triggers (see VER-01). |
| **DBA-25** | **Reported collision disproved at HEAD**; residual risk | `app/src/main/java/com/neochildclinic/feature/patient/data/PatientIdGenerator.kt:15-54`; `app/src/main/java/com/neochildclinic/feature/patient/data/PatientClinicIdMigrationWorker.kt:43-53` | IDs now have a stable per-install 4-character suffix, and the legacy migration uses the same generator. Two devices no longer generate the same `NEO-<number>` by default. Residual: 1/65,536 suffix collision and no `23505` retry on upload. |
| **DBA-26** | **Confirmed open** | `app/src/main/java/com/neochildclinic/feature/patient/domain/MergePatientsUseCase.kt:31-83`; `app/src/main/java/com/neochildclinic/feature/patient/data/PatientRepositoryImpl.kt:246-292` | Merge transfers vaccinations, reminders and inventory patient ids, then deletes the duplicate. Consultations, finance, notes, todos, personal reminders and documents are not transferred; moved-row UPDATEs and the duplicate DELETE have no shared group. |

## 3. Findings that cannot be closed without a database

These are not “unverified findings”; their code paths or schema text are confirmed. The **live consequence** needs a read-only Supabase connection or a disposable project.

| ID | What cannot be verified statically | Query / test needed |
|---|---|---|
| DBA-02 | Whether `inventory_transactions.visit_id` has a RESTRICT/NO ACTION FK | `pg_constraint` inspection plus a visit-delete transaction test |
| DBA-19 | Whether `reminders_natural_key` was actually created, and whether duplicate reminders exist | `pg_indexes`/`pg_index` + duplicate-key query |
| DBA-24 | Whether production has zero, one, or two stock triggers and which function body is installed | `pg_trigger`, `pg_proc`, migration ledger |
| DBA-08 | Whether live policies match the migration text or were changed out of band | `pg_policies` snapshot, JWT role probes |
| DBA-01 data | Which batches drifted and why; the trigger fix does not repair history | `inventory_reconciliation` view plus physical count |

## 4. Additional findings discovered during verification

These were not numbered in the original DBA register. They are included because they affect deployment safety, data deletion, or the fixes that are already in the tree.

| ID | Severity | Result | Evidence | Required treatment |
|---|---:|---|---|---|
| **VER-01** | S2 | **Confirmed** | `supabase/migrations/20261022_fix_stock_trigger_update_arithmetic.sql:28-117` has no `begin;`/`commit;`. On a disposable PostgreSQL 18.2 database, adding a second legacy trigger and re-running the file with `psql -f` dropped/recreated the known trigger, then failed the guard with `expected exactly one update_batch_stock() trigger, found 2`; both `inventory_transactions_update_batch_stock` and `legacy_stock_trigger` were left active. | Wrap the migration in an explicit transaction; preflight all triggers calling the function before any DDL; or drop every trigger that calls the function, then create exactly one. Do not apply it with a non-transactional runner. |
| **VER-02** | S2 | **Confirmed** | `docs/bug-audit/16-release-readiness.md:12-16` lists only `20261021000000` and `20261021001000`; it omits `20261021002000`, `20261018`, the final `20261022` trigger migration and the reconciliation view. `docs/bug-audit/20-next-actions.md:5-7` repeats the older migration name. `docs/bug-audit/18-master-bug-register.md:5` says the final file is fixed. | A deployment that follows the release note can leave the DBA-01 bug installed. Replace the note with one ordered, checksummed sequence and a post-deploy trigger query. |
| **VER-03** | S2 | **Confirmed decision risk** | `supabase/migrations/20261025_audit_log_retention.sql:1-54` schedules a security-definer function that permanently deletes `audit_logs` older than one year. There is no owner-approved retention period, export, dry-run count, or backup in the migration. A malformed timestamp that matches the regex but fails the cast aborts the prune. | Do not deploy until the owner sets the retention period. Count and export affected rows first. Disable/unschedule if policy is not approved. |
| **VER-04** | S3 | **Confirmed regression risk from the DBA-06 fix** | `app/src/main/java/com/neochildclinic/core/sync/RemotePrune.kt:1-71`; `app/src/main/java/com/neochildclinic/core/sync/Pagination.kt:14-27`. A full-table offset pull is not a snapshot. If a row is inserted/deleted mid-pull, the returned ID set can be incomplete, and `RemotePrune` can delete a synced local row that still exists remotely. A later refresh can restore it, but offline users see a transient disappearance. | Keep prune, but only prune when the fetched count matches the server’s exact count; otherwise skip prune and retry. Add a two-pass/integration test with concurrent insert/delete. |
| **VER-05** | S3 | **Confirmed regression risk from the DBA-06 fix** | `app/src/main/java/com/neochildclinic/feature/patient/data/PatientRepositoryImpl.kt:100-173` reads `pendingPatientIds` and then performs individual writes without wrapping the loop in `database.withTransaction` (unlike finance). A queue entry created between the read and a write is invisible to the guard. | Move the pending-set read and the write loop into one Room transaction (the DAO comment already states this contract). Add a concurrent enqueue test. |
| **VER-06** | S2 | **Confirmed migration hazard** | `supabase/migrations/20261021000000_drop_legacy_profiles_policy.sql:18-25` uses `drop function public.get_my_role() cascade`. CASCADE can drop any dependent object, not just the 14 policies named in the comment. | Preflight `pg_depend`/dependent views and functions; drop the known policies explicitly, then drop the function with `RESTRICT`. Snapshot `pg_policies` before applying. |

## 5. Prior claims reconciled

| Prior claim | Verification result |
|---|---|
| `docs/bug-audit/18-master-bug-register.md:13` — “NCC-001 through NCC-042 (Remaining items) ✅ Fixed” | **Not supported.** The canonical register lists NCC-027, 028, 031 and 036 as Open and NCC-038 as Needs decision (`docs/Knowledge_Base/Quality/Bug Register.md:81-98`); it also records NCC-029, 030 and 039 as Fixed, while the master table is stale for those three. The SQL register has 18 confirmed-open findings plus 2 partial. The blanket row is a documentation defect. |
| `docs/bug-audit/13-final-verification-report.md:7-9` — 310/310 tests and clean build | **Not independently reproduced.** This environment has no Android SDK/device and no Gradle run was performed. The SQL tests that were actually run are listed in §6. Do not treat the 310 figure as evidence for any SQL finding. |
| `docs/bug-audit/16-release-readiness.md:1-16` — conditionally ready after two migrations | **Contradicted by the migration set.** The final stock migration and the pre-existing trigger install are missing from the list; DBA-01 data repair is not planned there. See VER-02. |
| `docs/Knowledge_Base/Database-Audit/16_Phase1_Stock_Trigger_Remediation.md` — stock fix prepared and tested | **Arithmetic confirmed** by the local run in §6. **Deployment and historical repair not confirmed.** |
| DBA-06 “Remote deletions never converge locally” | **True at `ca4bed4`; disproved at HEAD.** `RemotePrune` was added after the audit snapshot. |
| DBA-25 “Local sequential clinic IDs can collide across devices” | **True at `ca4bed4`; reported mechanism disproved at HEAD.** The per-install suffix is present; residual collision/`23505` risk remains. |
| DBA-12 “Parentless child pulls can abort a refresh step” | **Consultation half fixed; personal-reminder half open.** Do not mark the whole finding fixed. |
| DBA-16 “Audit logger commits outside the business transaction” | **Partially fixed.** `recordLog` exists, but `log()` still runs on its own scope and is still used inside a transaction in the borrow path. |
| DBA-01 “Fixed” | **Code fixed, deployment and data repair unverified.** The master register row says Fixed; the correct release status is “fixed in repository, not yet proven deployed, historical stock unrepaired”. |

## 6. What was actually executed in this pass

A disposable PostgreSQL 18.2 cluster was created under `/data/data/com.termux/files/usr/tmp/opencode/ncc_pg_verify`, then stopped and removed after the run. No production or Supabase database was contacted.

| Test | Command shape | Result |
|---|---|---|
| Stock trigger arithmetic | `supabase/migrations/20261022_fix_stock_trigger_update_arithmetic.sql` then `supabase/tests/stock_trigger_arithmetic.sql` on a minimal schema | `PASS: stock trigger arithmetic` (inside a rolled-back `DO` block) |
| Inventory reconciliation view | Final trigger + `20261022_inventory_reconciliation_view.sql` + `supabase/tests/inventory_reconciliation_test.sql` | `PASS: inventory reconciliation view` (inside a rolled-back `DO` block) |
| Migration re-apply failure mode | Add `legacy_stock_trigger`, re-run the final migration with `psql -f` (autocommit) | Failed the trigger-count guard; both triggers remained active. This is the evidence for VER-01, not a production test. |
| Android/Gradle tests | Not run | No SDK/device; the 310/310 claim was not reproduced. |
| RLS role probes | Not run | No Supabase/auth environment; `rls_role_test.sql` also contains placeholder IDs. |
| Data repair dry run | Not run against live data | Only the detection view was exercised; physical counts are required before repair. |

## 7. Register update

The existing master register is preserved as history and annotated in `docs/bug-audit/18-master-bug-register.md`. Its blanket “NCC-001 through NCC-042 Fixed” row is retained but explicitly superseded by the verification counts above. No canonical NCC card was silently renumbered or deleted.

The next documents are:

- `11-remediation-plan.md` — dependency-ordered fixes, owners/decisions, tests.
- `12-migration-and-data-repair-plan.md` — migration order, preflight SQL, stock/finance/reminder repair.
- `13-test-and-rollback-plan.md` — test matrix and rollback/forward recovery.

**Stop point:** no production fix is approved by this snapshot. The first live action remains the read-only preflight and backup in `12-migration-and-data-repair-plan.md`. The unverified code-only edits after approval do not change any status here.
