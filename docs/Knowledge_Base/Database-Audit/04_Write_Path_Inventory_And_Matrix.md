# Write Path Inventory and Traceability Matrix

## 1. Write pipelines by feature

| Feature / action | Repository method | Local tables written | Queue entity / op | Transaction boundary | Server side effects |
| --- | --- | --- | --- | --- | --- |
| Create/edit patient | `PatientRepositoryImpl.addPatient` | `patients` | `PATIENT` CREATE/UPDATE | Room transaction | upsert; clinic-id conflict resolution |
| Delete patient | `deletePatient` | `patients`, reminders, personal reminders, visits, items, finance, consultations, deductions | `REMINDERS` DELETE, `PERSONAL_REMINDER` DELETE, `VACCINATION`/`VACCINATION_ITEM`/`FINANCE` DELETE, `CONSULTATION` DELETE, `PATIENT` DELETE | one `withTransaction` | ordered child→parent; see DBA-05 |
| Merge duplicate patients | `MergePatientsUseCase` | patients, visits, items, reminders, inventory ledger; duplicate's consultations/finance/notes are deleted | PATIENT UPDATE (master), PATIENT DELETE (duplicate), VACCINATION/REMINDERS UPDATE | one outer `TransactionRunner` | moved rows share no transaction group with the parent delete (DBA-26) |
| Create/edit vaccination | `ClinicalVaccinationService` → `VaccinationRepositoryImpl.addVaccination` + `VaccinationEditEngine` | visits, items, deductions, inventory transactions, batches, finance, reminders | `VACCINATION` CREATE/UPDATE, `VACCINATION_ITEM` CREATE/UPDATE/DELETE, `TRANSACTION`/`BATCH` UPDATE, `FINANCE`, `REMINDERS` | `TransactionRunner.run` | receipt trigger; stock trigger |
| Delete vaccination | `deleteVaccination` | reverses batches + ledger, deletes deductions/items/finance/reminders/visit | `VACCINATION`/`VACCINATION_ITEM`/`FINANCE`/`REMINDERS` DELETE + batch/transaction UPDATE | `TransactionRunner.run` | stock trigger on reversal; visit FK risk (DBA-02) |
| Add/edit consultation | `ConsultationEditEngine` → `ConsultationRepositoryImpl` | consultations, visit header, finance | `CONSULTATION` CREATE/UPDATE, `VISIT` UPDATE, `FINANCE` | `TransactionRunner.run` | receipt trigger only on visit create |
| Delete consultation | `deleteConsultation` | consultation, visit, finance | `CONSULTATION`/`VACCINATION`/`FINANCE` DELETE | `TransactionRunner.run` | visit delete risk if ledger exists |
| Record income / edit visit income | `FinanceRepositoryImpl.recordIncome`, `updateIncomeForVisit`, `updateConsultationIncome` | finance_transactions | `FINANCE` CREATE/UPDATE | Room transaction | deterministic row id for visit income |
| Add/update/delete expense | `ExpenseRepositoryImpl` | expenses | `EXPENSE` CREATE/UPDATE/DELETE | Room transaction | hard delete |
| Add/update/delete waste | `WasteRepositoryImpl` | waste_records, batches, inventory_transactions | `WASTE` CREATE/UPDATE/DELETE, `BATCH`/`TRANSACTION` | Room transaction | stock trigger |
| Stock add / batch edit / delete | `InventoryRepositoryImpl.addBatch`, `addStockBatch`, `updateBatch`, `deleteBatch` | vaccines, batches, inventory_transactions | `VACCINE`/`BATCH`/`TRANSACTION` | Room transaction | `remaining_quantity` omitted on update; trigger owns stock |
| Borrow item | `BorrowRepositoryImpl.saveBorrowedItem` | borrow_records, batches, inventory_transactions | `BORROW`, `BATCH`/`TRANSACTION` | Room transaction | negative stock guard |
| Return item | `submitReturn` | borrow_returns, borrow_records, batches, inventory_transactions (+ new batch) | `BORROW_RETURN`, `BORROW` UPDATE, `BATCH`/`TRANSACTION` | Room transaction | no server-side outstanding cap |
| Delete borrow | `deleteBorrowedItem` | borrow_records, returns, batches/ledger | DELETE + stock updates | Room transaction | stock restore |
| Save next vaccination | `ReminderRepositoryImpl.saveNextVaccination` | reminders | `REMINDERS` CREATE/UPDATE | outer transaction or own `transactionRunner` | server unique index |
| Complete/dismiss/reschedule/cancel reminder | `ReminderRepositoryImpl` | reminders, audit_logs | `REMINDERS` UPDATE/DELETE, `AUDIT_LOG` | Room transaction | append-only audit |
| Personal reminder CRUD | `PersonalReminderRepositoryImpl` | personal_vaccine_reminders | `PERSONAL_REMINDER` CREATE/UPDATE/DELETE | Room transaction | — |
| Todo status / add / delete | `PatientTodoRepositoryImpl` | consultation_todos, vaccination_todos | `CONSULTATION_TODO`/`VACCINATION_TODO` | Room transaction | — |
| Doctor slots/exceptions | `DoctorAvailabilityRepositoryImpl` | weekly slots, exceptions | `DOCTOR_WEEKLY_SLOT`/`DOCTOR_SLOT_EXCEPTION` | Room transaction | role checks server-side (`carveouts`) |
| Vaccination item pull reconciliation | `applyDownloadedVaccinationItems` | vaccination_items | none (pull) | Room transaction | — |
| Backup export / cloud upload | `BackupRepositoryImpl.exportBackupToUri`, `cloudBackupNow` | `backup_history` | none (backup is a read/snapshot) | file + metadata write | Cloudflare Worker/R2 |
| Backup restore | `BackupRepositoryImpl.restoreBackupFromUri` → `BackupRestorer` | replaces all business tables | none; restore re-enqueues rows/resync | single Room transaction | may leave old `sync_queue` rows |
| Legacy clinic-id migration | `PatientClinicIdMigrationWorker` | patients | `PATIENT` UPDATE per row | one Room transaction | sequential ID assignment (DBA-25) |
| Logout | `AuthViewModel.logout` | `clearAllTables()` after pending guard | none | — | local wipe |
| Device registration | `DeviceRepositoryImpl` | none | **no queue** | direct PostgREST | online-only |
| Staff management | `StaffManagementRepositoryImpl` | none | **no queue** | edge function | service role |
| Document upload | `DocumentRepositoryImpl` | none | **no queue** | Storage API | online-only |

## 2. Table → writers

| Table | Production writers |
| --- | --- |
| `patients` | add/update/delete patient, merge/transfer, backup restore, patient refresh |
| `patient_visits` | vaccination create/edit/delete, consultation create/edit/delete, visit pull, transfer, restore |
| `vaccination_items` | vaccination create/edit/delete, item pull, transfer |
| `reminders` | next-vaccination save, complete/dismiss/reschedule/cancel/restore, delete visit/patient, pull |
| `personal_vaccine_reminders` | CRUD, patient delete, pull |
| `consultations` | consultation CRUD, pull |
| todos | todo CRUD, pull |
| `finance_transactions` | record/update income, vaccination/consultation create/edit/delete, expense-independent, pull, legacy COGS migration |
| `expenses` | expense CRUD, pull |
| `vaccines` | catalog CRUD, pull |
| `vaccine_batches` | add stock/batch, update/delete, waste, borrow/return, vaccination edit/delete reversal, pull |
| `inventory_transactions` | every stock movement, reversal, waste, borrow/return, transfer; no pull |
| `inventory_deductions` | vaccination create/edit/delete, patient delete |
| `waste_records` | waste CRUD, pull |
| `borrow_records` / `borrow_returns` | borrow/return/delete, pull |
| `doctor_*` | schedule CRUD, pull |
| `profiles` | profile edit, manage-staff edge function, restore |
| `audit_logs` | `AuditLogger` fire-and-forget staging |
| `sync_queue` | every synced write via `enqueue`, worker status updates/deletes |
| `backup_history` | backup operations |
| `widget_due_cache` | `WidgetWorker` |

## 3. Queue enqueue sites (representative)

`syncRepository.enqueue(...)` appears in every repository listed above. Distinct patterns:

- **Grouped writes:** vaccination, consultation, borrow return, waste, stock edits pass a `transactionGroupId` so parent/child rows upload in order.
- **Ungrouped deletes:** patient delete enqueues reminder/personal-reminder deletes without a group and with `LOW` priority (`PatientRepositoryImpl.kt:220-228`). Priority ordering can send the patient DELETE (`MEDIUM`) before its `LOW` reminder deletes, so grouping is required for correctness (`PatientRepositoryImpl.kt:251-254`).
- **Audit:** `AuditLogger` enqueues `AUDIT_LOG` on its own scope, deliberately not joining the caller's transaction (`AuditLogger.kt` comment).

## 4. Untraced or dead write paths

| Path | Status | Evidence |
| --- | --- | --- |
| `InventoryRepositoryImpl.deductStock` | **zero production callers**; only interface declaration | `InventoryRepositoryImpl.kt:384`; repo-wide grep finds no call |
| Multi-batch `deductStock` loop | dead code today; each batch deduction is separately transactional, so a failure mid-loop would leave a partial deduction if it were ever wired up | same method |
| `TRANSACTION` descriptor | no downloader; upload-only ledger descriptor | `SyncUploader.kt:168-185` |
| `INVENTORY_TRANSACTION` descriptor | no downloader; upload-only | `SyncUploader.kt:177-185` |
| `sync_queue` old rows after restore/logout | restore does not clear the queue; logout clears all tables | `BackupDao`, `AuthViewModel` |

## 5. Transaction-boundary audit

Verified atomic (write + queue in one transaction):

- patient create/update/delete (delete uses `database.withTransaction`)
- vaccination create/edit/delete
- consultation create/edit/delete
- finance updates
- expense CRUD
- waste CRUD
- borrow/return
- doctor slots
- todos
- personal reminders

Exceptions / weaker boundaries:

- `AuditLogger` writes and enqueues independently of the business transaction. A rolled-back business write can still leave an audit row claiming success (DBA-16).
- `DeviceRepositoryImpl`, `DocumentRepositoryImpl`, `StaffManagementRepositoryImpl` write directly to Supabase with no outbox; a network failure loses the action (online-only by design).
- `SyncRepositoryImpl.enqueue` does delete-then-insert dedupe outside any unique constraint; concurrent enqueues can duplicate (DBA-09).

## 6. Write-path risks verified

1. **Stock trigger retry/idempotency** — every retry of an already-landed `INVENTORY_TRANSACTION` is an UPDATE in Postgres and hits the broken arithmetic (DBA-01).
2. **Visit/ledger FK** — the visit delete no longer nulls `inventory_transactions.visit_id`, while the uploader comments say it must (DBA-02).
3. **Reminder identity on patient delete** — local ids are queued instead of `serverId` (DBA-05).
4. **Finance pull can overwrite a pending write** — pull and push are independent; the heal branch sets `isSynced=false` and replaces the row (DBA-03).
5. **Borrow return cap is local-only** — no server aggregate constraint (DBA-10).
6. **Audit enqueue has no transaction group** — audit rows can upload before/after the business row they describe; acceptable for append-only logs, but ordering is not guaranteed.

## 7. Cross-check

All production `syncRepository.enqueue` call sites were enumerated with `rg`; all direct `postgrest.from` files were enumerated (16 files). The only writes outside the outbox are the intentional online-only paths in §1 and the uploader itself. No screen writes directly to Supabase.
