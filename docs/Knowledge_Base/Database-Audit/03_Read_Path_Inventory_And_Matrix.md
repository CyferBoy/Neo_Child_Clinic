# Read Path Inventory and Traceability Matrix

Rule applied: a screen is **local-first** only if its `Flow` terminates in a Room DAO. Repositories that call PostgREST during collection or exposure are marked remote.

## 1. Screen → ViewModel → repository → source

| Feature / screen | ViewModel (representative) | Repository method | Source | Tables / remote object | Notes |
| --- | --- | --- | --- | --- | --- |
| Dashboard | `DashboardViewModel` | `getConsultationsByDateAndStatus`, `getVaccinationsByDateAndStatus`, `getDatesWithData` | Room | `consultation_todos`, `vaccination_todos` | realtime refresh for todos, debounced |
| Dashboard stats | `DashboardViewModel`, `ClinicStatsManager` | `getAllVaccinations`, `getAllTransactions`, inventory/reminder Flows | Room | visits/items/finance/batches/reminders | aggregates on local cache |
| Patient list | `PatientListViewModel` | `allPatients`, `searchPatients`, `patientIdsWithMissingPrice` | Room (+`PatientCache`) | `patients`, visits, items | generation-guarded cache |
| Patient details | `PatientDetailsViewModel` | `getPatientById`, `getVaccinationsForPatient`, `getVaccinationCardsForPatient`, `getConsultationsForPatient`, `getNotes` | Room | patients, visits, items, consultations, notes | one-shot patient read may use cache |
| Patient audit history | `AuditLogViewModel` | `AuditLogDao.getLogsForPatient` | Room | `audit_logs` | local staging only |
| Full audit history | `FullAuditLogViewModel` | `AuditLogRepository.getPaged` | **Supabase** | `audit_logs` | intentionally remote-only; offline error |
| Add/edit patient | `AddPatientViewModel` | `getPatientById`, `addPatient` | Room | patients | — |
| Vaccination list / detail | `VaccinationViewModel` / detail VM | `allVaccinations`, `getVaccinationCardsForPatient`, `getVaccinationById` | Room | visits, items, vaccines, batches | — |
| Add/edit vaccination | `AddVaccinationViewModel` | `getAvailableSlots`, catalogs, existing visit | Room | vaccines, batches, slots, reminders, finance | no remote read on open |
| Reminder due list | `DueViewModel` | `getDueList` | Room | reminders | screen calls `refreshReminders()` too |
| Reminder completed/dismissed | corresponding VM | `getDueList` variants | Room | reminders | — |
| Personal reminders | `PersonalReminderViewModel` | `getActiveReminders`, `getCompletedReminders`, `getCancelledReminders` | Room | personal_vaccine_reminders | refresh online |
| Inventory list | `InventoryViewModel` | `getInventoryItems`, `getAllVaccines`, `getVaccineBatches` | Room | vaccines, batches, transactions | refetch on screen refresh |
| Batch detail | `BatchDetailViewModel` | `getBatchById`, `getTransactionsForVaccine` | Room | batches, inventory_transactions | ledger absent on new installs |
| Stock history | `StockHistoryViewModel` | `getStockHistoryPage(..., remoteOnly = true)` | **Supabase** | `inventory_transactions` | no local fallback; no ledger pull |
| Finance | `FinanceViewModel` / `FinanceDetailsViewModel` | `getAllTransactions` | Room | finance_transactions | — |
| Expenses | `ExpenseListViewModel` | `getAllExpenses`, `getFilteredExpensesPage` | Room | expenses | local pagination |
| Borrowed | `BorrowedViewModel` | `getActiveBorrowedRecords`, `getReturnedRecords`, `getReturnRecords` | Room | borrow_records, borrow_returns, batches | screen refresh calls two repos |
| Waste | `WasteViewModel` | `getAllWaste`, `getWasteCount` | Room | waste_records | screen refresh calls inventory too |
| Consultations | `ConsultationViewModel` | `getConsultationsForPatient` | Room | consultations, visits | — |
| Doctor schedule | `WeeklyDoctorSlotsViewModel` | `getWeeklySlots`, `getExceptions` | Room | doctor_weekly_slots, doctor_slot_exceptions | screen refresh also profiles |
| Profile / staff | `ProfileViewModel` | `allProfiles`, `getProfileById` | Room | profiles | bootstrap can hit remote |
| Widget | `WidgetWorker` | reminder/patient/inventory Flows | Room | widget_due_cache, reminders, visits | cache rebuilt in worker |
| Notifications | workers | inventory/reminder Flows | Room | batches, reminders, personal reminders | no network read |
| Backup history | `BackupViewModel` | `observeHistory` | Room | backup_history | local only |
| Backup create (local/cloud) | `BackupRepositoryImpl` | `exportBackupToUri`, `cloudBackupNow` | Room snapshot | all business tables, `backup_history` | full-table read |
| Backup history | `BackupRepositoryImpl` | `observeHistory` | Room | `backup_history` | local metadata |
| Backup restore preview | `BackupRepositoryImpl` | `peekBackupFromUri`, `peekCloudBackup` | local file / Cloudflare Worker + R2 | `.nccb` archive | validates before apply |

## 2. Table → readers

| Table | Read by (representative) |
| --- | --- |
| `patients` | patient list/details, dashboard, finance filters, vaccination pickers, widget |
| `patient_visits` | patient history, vaccination list, statistics, today list, widget, finance self-heal |
| `vaccination_items` | visit detail, statistics, inventory dialog, finance COGS |
| `reminders` | due/completed/dismissed lists, patient card, dashboard, widget, notification worker |
| `personal_vaccine_reminders` | personal reminder lists, notification worker |
| `consultations` / todos | patient history, dashboard, today list |
| `finance_transactions` | finance screens, statistics, monthly report, export |
| `expenses` | expense list, statistics profit |
| `vaccines` / `vaccine_batches` | inventory list/detail, vaccination form, waste, borrow, widget |
| `inventory_transactions` | batch detail (local), stock history (remote), COGS migration |
| `waste_records` | waste list, statistics |
| `borrow_records` / `borrow_returns` | borrowed list, inventory return dialog |
| `doctor_weekly_slots` / exceptions | booking slot calculation |
| `profiles` | staff directory, doctor picker, dashboard doctor filter |
| `audit_logs` | patient audit, full audit (remote), reminder audit trail |
| `inventory_deductions` | edit/delete reversal, patient inventory dialog |
| `sync_queue` | sync status screen, pending badge, logout guard |
| `backup_history` | backup screen |
| `widget_due_cache` | widget provider |

## 3. Remote reads (not Room-backed)

| Call | Location | Offline behavior | Verdict |
| --- | --- | --- | --- |
| `inventory_transactions` paged stock history | `InventoryRepositoryImpl.kt:277-292`; `PatientRemoteDataSourceImpl.kt:45-55` | page request throws; screen shows error/empty | **Confirmed offline gap** (DBA-07) |
| `audit_logs` paged full history | `AuditLogRepositoryImpl` | error/empty; local audit table not used | intentional, documented (DBA-15) |
| `profiles` by email/id on login/bootstrap | `ProfileRepositoryImpl` | login fails with “check connection” | intentional first-login dependency |
| `user_devices` register/update | `DeviceRepositoryImpl` | best-effort direct write, no queue | online-only device management |
| Supabase Storage documents | `DocumentRepositoryImpl` | list/upload/download fail offline | no local document index |
| Edge functions (`manage-staff`, `notify-update`) | `StaffManagementRepositoryImpl` | action fails; UI surfaces error | admin-only online operation |

No Compose screen calls `Postgrest` directly; every direct remote read is inside a repository. That distinction matters: the architecture rule is intact, but it does not make the screen offline-capable.

## 4. Read-path risks verified

1. **Upsert-only pulls.** Every refresh loop imports/updates remote rows but never deletes local rows missing remotely. Examples:
   - patients: `PatientRepositoryImpl.kt:91-145`
   - visits: `VaccinationRepositoryImpl.kt:91-145`
   - finance: `FinanceRepositoryImpl.kt:379-435`
   - consultations: `ConsultationRepositoryImpl.kt:205-215`
   - waste/expenses/borrow: same shape.
   A hard delete on another device therefore never reaches this device (DBA-06).
2. **Remote-only ledger.** `fetchRemoteVaccinationItems` and stock history read remotely, but `inventory_transactions` has no inbound refresh method. A new install has an empty ledger and a blank batch history until it performs movements locally.
3. **No query-level soft-delete leak found.** Main local tables no longer carry `is_deleted` (migration 31→32 drops it from 18 tables); remote pulls filter `is_deleted=false`. `reminders` intentionally retains the trio and its DAO filters it.
4. **`createdAt` use in reads.** Visit `createdAt` is not used by statistics; it is used for conflict normalization. Its rewrite on edit (DBA-13) damages audit lineage more than screen output.

## 5. Untraced / surprising read paths

- `InventoryRepositoryImpl.deductStock` has no caller (write path, listed in `04`).
- `TRANSACTION` and `INVENTORY_TRANSACTION` descriptors have no downloader; if the conflict branch selects “remote is newer”, the no-op downloader runs and the queue item is still marked uploaded (`SyncUploader.kt:521-523`). The local row is not refreshed.
- `StockHistoryViewModel.refresh()` and `loadPage()` both pass `remoteOnly=true`; there is no local pagination path at all.
- Dashboard date selection uses `LocalDate.now()` (system zone), while vaccination due dates are interpreted in IST (DBA-14).

## 6. Matrix completeness

Traced: dashboard, patients, patient details/audit, vaccination create/edit/delete, reminders (due/completed/dismissed/personal), inventory/catalog/batches/stock history, finance/expenses, borrow/returns, waste, consultations, doctor availability, todos, profile/staff, devices, documents, backup/restore, widget, notifications, auth/session.

Not fully traced: report export/PDF rendering internals (read-only formatting over the same repositories, no new DB queries), and Supabase table privileges beyond the SQL migrations (live state unknown). Both are recorded in `15_Unknowns_And_Live_DB_Gaps.md`.
