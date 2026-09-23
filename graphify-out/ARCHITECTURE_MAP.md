# Neo Child Clinic — Vaccine Manager: Architecture Map

Graphify-assisted map built from source of truth only (imports, constructors, DAO queries, Manifest, Gradle, Supabase migrations). Graph: **3,098 nodes · 8,089 edges · 174 communities**. No source code was modified.

Interactive graph: `graphify-out/graph.html` · Audit: `graphify-out/GRAPH_REPORT.md`

---

## 1. LEVEL 1 — SYSTEM OVERVIEW

```
Application  (com.neochildclinic)
│
├── Android entry / lifecycle
│     NeoChildApp · MainActivity · WorkManager · Receivers · FCM · Glance widget
│
├── Presentation
│     Compose Screens · Navigation (Routes) · ViewModels (@HiltViewModel)
│
├── Domain (partial — not every feature has a Use Case)
│     Models · Use Cases (4) · Services (3) · Managers
│
├── Data
│     Repositories (*RepositoryImpl, concrete @Singleton) · Hilt DI
│     Room + SQLCipher (AppDatabase v30)  ·  Sync outbox  ·  Supabase client
│
├── Background
│     SyncWorker · DailySummaryWorker · PersonalReminderNotificationWorker
│     AutoBackupWorker · WidgetWorker · PatientClinicIdMigrationWorker
│
└── External
      Supabase (Auth, PostgREST, Realtime, Storage, Edge Functions)
      Firebase Cloud Messaging · GitHub Releases (in-app update)
      Cloudflare Worker + R2 (encrypted cloud backups)
```

**Offline-first in one line:** UI always reads Room; writes hit Room first and enqueue `sync_queue`; workers push to Supabase; pull refresh and Realtime bring remote rows back into Room.

---

## 2. LEVEL 2 — FEATURE ARCHITECTURE (layers that actually exist)

Most features use: **Screen → ViewModel → Repository → DAO / PostgREST**.  
Domain Use Cases exist only for: `SearchPatientsUseCase`, `MergePatientsUseCase`, `GetAvailableSlotsUseCase`, `RefreshDataUseCase`.  
Domain Services that orchestrate multi-repo writes: `ClinicalVaccinationService`, `VaccinationEditEngine`, `ConsultationEditEngine`.

| Feature | UI | ViewModel / Presenter | Domain | Repository | Local | Remote |
|---|---|---|---|---|---|---|
| Auth / Session | `LoginScreen`, `LockScreen` | `AuthViewModel` | session readiness helpers in `SyncRepositoryImpl` / `SessionManager` | Supabase `Auth` via module | encrypted prefs (`SecurityUtils`) | Supabase Auth + deep link `neochild://auth-callback` |
| Dashboard | `DashboardScreen` + cards | `DashboardViewModel` | — | patient/todo/reminder repos | Room | PostgREST + Realtime (todos) |
| Today’s Patients | `TodayPatientsScreen` | shared `DashboardViewModel` | — | `PatientTodoRepositoryImpl` | `consultation_todos`, `vaccination_todos` | PostgREST + Realtime |
| Patients | `PatientListScreen`, `AddPatientScreen`, `PatientDetailsScreen`, `PatientInfoComponents` | `PatientListViewModel`, `PatientViewModel` | `SearchPatientsUseCase`, `MergePatientsUseCase` | `PatientRepositoryImpl` | `patients`, `patient_notes` | PostgREST + Realtime + Storage (docs) |
| Consultations | `AddConsultationScreen` | `AddConsultationViewModel` | `ClinicalVaccinationService.recordConsultation`, `ConsultationEditEngine` | `ConsultationRepositoryImpl` | `consultations`, `patient_visits` | sync queue |
| Vaccinations / Add / Edit | `AddVaccinationScreen` | `AddVaccinationViewModel` | `ClinicalVaccinationService`, `VaccinationEditEngine` | `VaccinationRepositoryImpl` + finance + inventory + reminders | `patient_visits`, `vaccination_items` | sync queue |
| Due / Next vaccinations | `DueScreen`, `DueTab`, `CompletedDismissedScreen` | `DueViewModel`, `CompletedDismissedViewModel` | date filtering via `PatientUtils` / `DateClassifier` | `ReminderRepositoryImpl` | `reminders` (`DueReminderDao`) | sync queue |
| Reminders (personal) | `PersonalReminderScreen`, `AddEditPersonalReminderScreen` | `PersonalReminderViewModel`, `AddEditPersonalReminderViewModel` | — | `PersonalReminderRepositoryImpl` | `personal_vaccine_reminders` | sync queue |
| Inventory (master + batches + stock) | `VaccineInventoryScreen`, `AddVaccineScreen`, `AddBatchScreen`, `AddStockScreen`, `StockHistoryScreen` | `VaccineInventoryViewModel`, `AddVaccineViewModel`, `AddBatchViewModel`, `AddStockViewModel`, `StockHistoryViewModel` | — | `InventoryRepositoryImpl` | `vaccines`, `vaccine_batches`, `inventory_transactions` | sync queue (batch remaining qty via server trigger) |
| Borrowed | `BorrowedScreen`, dialogs | `BorrowedViewModel` | — | `BorrowRepositoryImpl` → inventory | `borrow_records`, `borrow_returns` | sync queue |
| Waste | `WasteScreen` | `WasteViewModel` | — | `WasteRepositoryImpl` → inventory | `waste_records` | sync queue |
| Finance / Statistics | `StatisticsScreen`, tabs, `MonthlyFinanceDetailsScreen`, `FullReportScreen`, `VaccineDetailScreen` | `StatisticsViewModel`, `FinanceDetailsViewModel`, `FullReportViewModel`, `VaccineDetailViewModel`, `MilestonePatientsViewModel` | `FinanceCalculator`, `ExpenseCalculator`, `StatisticsUtils` (feature-local calculators) | `FinanceRepositoryImpl`, `ExpenseRepositoryImpl` | `finance_transactions`, `expenses` | sync queue refresh |
| Consultations billing | part of add/edit consultation | same | `ClinicalVaccinationService` | `FinanceRepositoryImpl.recordIncome` | `finance_transactions` | sync queue |
| Staff / Admin | `ManageStaffScreen`, `Add/EditStaffScreen`, `StaffDetailsScreen` | `AdminViewModel` | — | edge function `manage-staff` via `Functions` | `profiles` (read/refresh) | Supabase Edge Function (service role) |
| Audit logs | `FullAuditLogScreen`, `PatientAuditLogPager` | `FullAuditLogViewModel` | `AuditLogger` | `AuditLogDao` via logger | `audit_logs` | sync queue |
| Sync status | `SyncScreen` | `SyncViewModel` | — | `SyncRepositoryImpl` | `sync_queue` | PostgREST |
| Backup / Restore | `BackupSettingsScreen` | `BackupViewModel` | backup models / crypto / validator | `BackupRepositoryImpl` | `BackupDao` over all tables + `backup_history` | `CloudBackupApi` → Cloudflare Worker → R2 |
| Notifications settings | `NotificationSettingsScreen` | `NotificationSettingsViewModel` | `NotificationSettingsManager` | local prefs | — | — |
| App update | `AppUpdateScreen`, banner, dialog | `AppUpdateViewModel` | `AppUpdateManager`, `AppUpdateInfo` | HTTP to GitHub Releases | — | GitHub Releases + FCM push |
| Doctor availability | `WeeklyDoctorSlotsScreen` | `WeeklyDoctorSlotsViewModel` | `GetAvailableSlotsUseCase` | `DoctorAvailabilityRepositoryImpl` | `doctor_weekly_slots`, `doctor_slot_exceptions` | sync queue |
| Home widget | `VaccineWidget` (Glance), config activity | — | `WidgetWorker` precompute | `ReminderRepositoryImpl`, `PatientRepositoryImpl` | `widget_due_cache` | (via due list refresh) |
| Profile | `ProfileScreen` | `ProfileViewModel` | — | `ProfileRepositoryImpl` | `profiles` | PostgREST update-only policy |
| Documents | patient details / expense attachments | viewmodels | — | `DocumentRepositoryImpl` | local path metadata | Storage bucket `patient-docs` |

**Not present as separate features:** dedicated “finance app module”, FCM-driven clinical push (FCM is used for app-update alerts only), server-side app-update service beyond GitHub Releases.

---

## 3. LEVEL 3 — IMPLEMENTATION DEPENDENCIES (patterns)

Dependency injection is **Hilt constructor injection** (`@Inject` / `@HiltWorker` / `@HiltAndroidApp`). Only two DI modules: `di/DatabaseModule.kt`, `di/SupabaseModule.kt`.

Typical call chain:

```
Screen (Composable)
  → hiltViewModel() → *ViewModel
    → *RepositoryImpl (@Singleton)
      → AppDatabase.*Dao()  and/or  Postgrest.from(table)
      → syncRepository.enqueue(entityName, id, op)   // on local mutation
```

Multi-write orchestration:

```
AddVaccinationViewModel.saveVaccination
  → ClinicalVaccinationService.recordVaccination   // Room transaction
      → VaccinationRepositoryImpl.addVaccination
      → FinanceRepositoryImpl.recordIncome / updateIncomeForVisit
      → ReminderRepositoryImpl.markReminderCompleted (satisfyRelatedReminders)
      → InventoryRepositoryImpl.deductStockFromBatch + inventory_deductions row
  → ReminderRepositoryImpl (next-vaccine ReminderSpec via VaccinationEditEngine)
```

---

## 4. ARCHITECTURAL LAYERS (actual roles)

| Layer | Evidence | Role |
|---|---|---|
| Application | `NeoChildApp` `@HiltAndroidApp`, Manifest `android:name` | Hilt graph, WorkManager factory, SQLCipher load, schedules sync/reminder/backup |
| UI / Screens | `features/**` Compose files | Render + collect ViewModel state |
| Navigation | `Navigation.kt` `NavHost`, `Routes.kt` | Single-activity Compose Navigation; role guards (`AdminGuard`) |
| ViewModels | `*ViewModel.kt` under features | UI state, call repos/services |
| Domain models | `domain/model/*` | Patient, Vaccination, Finance, etc. |
| Use cases | `domain/usecase/**` (4 classes) | Search, merge, slots, full refresh |
| Domain services | `domain/service/**` (3) | Transactional clinical writes, edit engines |
| Repositories | `data/repository/*Impl` (17 concrete classes, no interface layer in code) | Local-first orchestration + enqueue + cloud refresh |
| Room | `AppDatabase` (SQLCipher, v30) | Encrypted local source of truth |
| DAOs | `data/local/dao/*` (incl. `BackupDao`) | SQL access |
| Entities | `data/local/entity/*` | Tables (see §5) |
| Remote | `SupabaseModule` installs Postgrest, Auth, Functions, Realtime, Storage | Backend |
| Sync | `SyncRepositoryImpl`, `SyncQueueEntity`, `SyncManagerImpl`, `SyncWorker` | Outbox upload + conflict check |
| Pull refresh | `cloudRefresh()` + `RefreshDataUseCase` | Ordered download |
| Cache | Room is primary cache; `widget_due_cache` is derived | Widget display |
| Background | `worker/*` + `ReminderScheduler` + `BackupAutoScheduler` | WorkManager jobs |
| Notifications | `NotificationHelper`, `ReminderScheduler`, workers, FCM service | Channels + scheduling |
| Backup | `data/backup/*`, `BackupRepositoryImpl`, Cloudflare worker | `.nccb` export/import, cloud R2 |
| Shared utilities | `core/utils/*` (`PatientUtils`, `DateClassifier`, `SecurityUtils`, receipts), `core/ui/*`, `AuditLogger`, `NetworkMonitor` | Cross-feature |

README’s “repository interfaces” box is aspirational: production code injects concrete `*RepositoryImpl` classes directly.

---

## 5. DATABASE ARCHITECTURE

**Database:** `neochild_db` · Room `@Database` version **30** · SQLCipher (`SupportOpenHelperFactory`) · passphrase from `SecurityUtils` · singleton via `AppDatabase.getDatabase` + `DatabaseModule`.

### Entities → tables (from `@Entity` / migrations)

| Entity | Table |
|---|---|
| `PatientEntity` | `patients` |
| `VaccinationEntity` (visit header) | `patient_visits` |
| `VaccinationItemEntity` | `vaccination_items` |
| `ReminderEntity` | `reminders` |
| `VaccineEntity` | `vaccines` |
| `VaccineBatchEntity` | `vaccine_batches` |
| `InventoryTransactionEntity` | `inventory_transactions` |
| `InventoryDeductionEntity` | `inventory_deductions` |
| `WasteEntity` | `waste_records` |
| `BorrowEntity` | `borrow_records` |
| `BorrowReturnEntity` | `borrow_returns` |
| `ConsultationEntity` | `consultations` |
| `ConsultationTodoEntity` | `consultation_todos` |
| `VaccinationTodoEntity` | `vaccination_todos` |
| `PersonalReminderEntity` | `personal_vaccine_reminders` |
| `FinanceEntity` | `finance_transactions` |
| `ExpenseEntity` | `expenses` |
| `ProfileEntity` | `profiles` |
| `AuditLogEntity` | `audit_logs` |
| `PatientNotesEntity` | `patient_notes` |
| `SyncQueueEntity` | `sync_queue` |
| `WidgetDueEntity` | `widget_due_cache` |
| `DoctorWeeklySlotEntity` | `doctor_weekly_slots` |
| `DoctorSlotExceptionEntity` | `doctor_slot_exceptions` |
| `BackupHistoryEntity` | `backup_history` |

`ReminderAuditEntity` / `PatientVaccinationCardEntity` are supporting types used by reminder/vaccination DAOs (card entity is an embedded/relation type on vaccination queries).

### DAOs

`PatientDao`, `VaccinationDao`, `DueReminderDao`, `VaccineDao`, `SyncQueueDao`, `WasteDao`, `WidgetDueDao`, `AuditLogDao`, `FinanceDao`, `ProfileDao`, `BorrowDao`, `PatientNotesDao`, `InventoryDeductionDao`, `ConsultationDao`, `VaccinationItemDao`, `PatientTodoDao`, `PersonalReminderDao`, `BorrowReturnDao`, `ExpenseDao`, `DoctorAvailabilityDao`, **`BackupDao`** (bulk get/insert/clear across the same tables; excludes `sync_queue` and `widget_due_cache`).

### Relationships (present in schema/queries)

```
patients 1—* patient_visits (vaccinations) 1—* vaccination_items
patients 1—* reminders          (patientId, originalVisitId → visit)
patients 1—* consultations / patient_notes / personal_vaccine_reminders / todos
patient_visits 1—* inventory_deductions (vaccinationId)
vaccines 1—* vaccine_batches 1—* inventory_transactions
vaccine_batches 1—* borrow_returns ; borrow_records 1—* borrow_returns
waste_records → vaccine/batch reference + inventory transaction side-effect
patient_visits / consultations → finance_transactions (visitId, patientId)
```

Backup mapping parent-first: profiles → vaccines → batches → doctor slots → patients → visits/items → reminders → … (see `BackupDao` header comment).

### Map

```
AppDatabase
  ├─ feature DAOs ──→ feature repositories ──→ ViewModels / ClinicalVaccinationService
  └─ BackupDao ─────→ BackupCollector / BackupRestorer / BackupRepositoryImpl
```

---

## 6. VACCINATION DATA FLOW

### A. Give a vaccine (write path)

```
AddVaccinationScreen
  → AddVaccinationViewModel.saveVaccination
      builds Vaccination + items (batchId, quantity) + nextVaccination ReminderSpecs
  → ClinicalVaccinationService.recordVaccination  [Room withTransaction]
      1. VaccinationRepositoryImpl.addVaccination
           → VaccinationDao inserts patient_visits + vaccination_items
           → sync enqueue VACCINATION / VACCINATION_ITEM (transactionGroupId)
      2. FinanceRepositoryImpl.recordIncome (category VACCINATION, cash/online split)
           → finance_transactions + sync FINANCE
           COGS snapshot embedded in remarks [COGS_SNAPSHOT:…]
      3. satisfyRelatedReminders → ReminderRepositoryImpl.markReminderCompleted
           reminders.status ACTIVE → COMPLETED (matches vaccine names)
      4. deductInventoryForNewVaccination (isNew only)
           per item: InventoryRepositoryImpl.deductStockFromBatch
             → vaccine_batches remaining −qty
             → inventory_transactions (type VACCINATION)
             → inventory_deductions row COMPLETED | FAILED
           vaccination.inventoryStatus = COMPLETED|PARTIAL|FAILED + sync UPDATE
  → Reminders for next doses written via ReminderRepositoryImpl
      (ReminderSpec from VaccinationEditEngine grouping in AddVaccinationViewModel)
```

Edit path: `VaccinationEditEngine` computes inventory diff (reverse + re-deduct) instead of `ClinicalVaccinationService` create path.

### B. Due / schedule path (read path)

```
reminders (status ACTIVE, dueDate)
  → DueReminderDao / ReminderRepositoryImpl.getDueList
  → DueViewModel filters: All | Overdue | Today | Tomorrow | Week | Month | Upcoming
  → DueScreen / DueTab
  → WidgetWorker reads getDueList → widget_due_cache → VaccineWidget
  → DailySummaryWorker counts due/overdue/low stock → NotificationHelper
```

IDs used: `patientId`, visit `id` (`originalVisitId` on reminders), `vaccineId`, `batchId`, `vaccination_items.vaccinationId` ↔ visit id, `reminderId` on next-vaccination selections.

---

## 7. INVENTORY FLOW

```
Vaccine master (vaccines)
  → Batch (vaccine_batches: purchaseQuantity, remainingQuantity, expiry)
  → Stock ops (inventory_transactions: PURCHASE / VACCINATION / BORROWED / BORROW_RETURN / WASTE-ish deduct / ADJUSTMENT / …)

Stock in:   addVaccine / addBatch / addStockBatch / addStockToBatch
Stock out:  deductStock / deductStockFromBatch
Reverse:    reverseDeduction
```

### Borrow

```
BorrowedViewModel → BorrowRepositoryImpl.saveBorrowedItem
  → InventoryRepositoryImpl.deductStock (direction out)
  → borrow_records + sync BORROW
Return:
  BorrowRepositoryImpl.submitReturn
  → return transaction (quantity in) + returnBorrowedStock
  → borrow_returns + sync BORROW_RETURN / inventory transaction
```

### Waste

```
WasteViewModel → WasteRepositoryImpl.recordWaste
  → waste_records insert
  → InventoryRepositoryImpl.deductStockFromBatch
  → sync WASTE (+ inventory side effects)
Update/delete reverse via positive transaction notes then re-apply/delete.
```

### Vaccination

```
ClinicalVaccinationService / VaccinationEditEngine
  → deductStockFromBatch + inventory_deductions audit row
```

**Server note (verified in sync code):** `vaccine_batches.remaining_quantity` on Supabase is maintained by DB trigger `tr_update_batch_stock` from paired `inventory_transactions`; client upload omits that column to avoid double-count. Room still computes it locally for offline UI.

---

## 8. FINANCE FLOW

Implemented pieces only:

```
Vaccination  ─┐
Consultation ─┼→ FinanceRepositoryImpl.recordIncome / updateIncomeForVisit
Other income ─┘     category VACCINATION | CONSULTATION | …
                     cashAmount + onlineAmount → finance_transactions

Expenses: ExpenseRepositoryImpl → expenses (amountPaise, category, paymentMethod)

Statistics:
  FinanceCalculator / ExpenseCalculator / StatisticsUtils (feature package)
  → FinanceTab / OverviewTab / MonthlyFinanceDetails / FullReport
  Net = revenue − expenses − (COGS snapshot from vaccination items when present)
```

- **Cash / Online:** yes (`cashAmount`, `onlineAmount` on visits + finance rows).
- **COGS:** yes — per-vaccination snapshot in finance `remarks`, plus `migrateLegacyVaccinationCogs` backfill on app start and after refresh.
- **Doctor account ledger:** no separate doctor wallet tables; doctor is `profiles` / visit `doctorId`.
- **Expenses:** yes (`expenses` table, categories, sort/filter UI).

---

## 9. REMINDER / DUE VACCINATION FLOW

```
                    reminders.status
                    ┌────────────┴────────────┐
                 ACTIVE                   COMPLETED
              (Due list)              (Completed tab)
                    │
                 DISMISSED
              (Dismissed tab)

ACTIVE + reminderEnabled → DueReminderDao.getAllDueReminders
COMPLETED                 → getAllCompletedReminders
DISMISSED                 → getAllDismissedReminders

DueViewModel filters (UI): Today / Tomorrow / This Week / Month / Upcoming / Overdue / All
  status mapping from filter → repository query
  PatientUtils.filterVaccinationsByPeriod + DateClassifier.Overdue

Transitions:
  complete  → moveDueToCompleted   (also automatic when matching vaccine given)
  dismiss   → moveDueToDismissed   (DueViewModel.dismissReminder)
  reschedule→ update dates (DueViewModel.rescheduleVaccination)
  restore   → back to ACTIVE

Consumers:
  DueScreen / CompletedDismissedScreen
  WidgetWorker → widget_due_cache → VaccineWidget
  DailySummaryWorker → notification
  Personal reminders: separate table + PersonalReminderNotificationWorker
  Statistics upcoming vaccine needs: calculateUpcomingVaccineNeeds() (DueReminderDao community)
```

---

## 10. OFFLINE-FIRST ARCHITECTURE

Present:

| Piece | Implementation |
|---|---|
| Local DB | Room + SQLCipher, no destructive migrations (crash rather than wipe) |
| Write path | Room first, always |
| Outbox | `sync_queue` (`SyncQueueEntity`) |
| Enqueue | `SyncRepositoryImpl.enqueue` after every mutating repo/service action |
| Worker | `SyncWorker` (periodic 15 min with network constraint from `NeoChildApp`; one-time from `SyncManagerImpl.scheduleSync` / `scheduleImmediateSync`) |
| Retry | exponential backoff; per-item retryCount ≤5 on network errors; stale SYNCING requeue after 5 min; session transient → requeue PENDING |
| Conflict | batch `fetchRemoteConflictData` — **last-write-wins** by `updated_at`/`last_updated`; if remote newer → `downloadAndReplaceLocal` (self-heal) |
| Pull | `RefreshDataUseCase` ordered refresh (`cloudRefresh` wrapper) |
| Realtime | only in `PatientListViewModel` and `DashboardViewModel` (channel `patients-db-changes`, `todays-patients-db-changes`) triggering refresh — **not a global merge bus** |
| Network | `core/network/NetworkMonitor`; WorkManager `NetworkType.CONNECTED` |

Not present: CRDTs, operational transforms, multi-master field-level merge (except explicit merge patients use case), a single global realtime applier for all tables.

---

## 11. SUPABASE / API ARCHITECTURE

```
App
 └─ SupabaseClient (SupabaseModule, BuildConfig.SUPABASE_URL + ANON_KEY, CIO, KotlinX serializer)
      ├─ Auth     — login/session; autoSave/autoLoad; deep link auth-callback
      ├─ PostgREST— all table sync + refresh SELECTs
      ├─ Realtime — 2 feature channels (patients, today's todos)
      ├─ Storage  — bucket patient-docs (DocumentRepositoryImpl)
      └─ Functions— manage-staff (AdminViewModel); notify-update (CI/release, not client-invoked)
```

**Session handling:** Supabase Auth storage + bounded waits in `Navigation` (`awaitResolvedSessionStatus`) and `SyncRepositoryImpl` (`SESSION_RESOLVE_TIMEOUT_MS`, one refresh per batch, 401 → single refresh retry). RLS: rows rejected if anonymous; profiles use UPDATE-only client policy (staff create via edge function).

Repositories touching Supabase directly: all `*RepositoryImpl` with `postgrest`/`storage` fields — especially Patient, Vaccination, Consultation, Inventory, Finance, Expense, Waste, Borrow, Reminder, PersonalReminder, PatientTodo, DoctorAvailability, Profile, Device, Document, Sync, Backup (via `CloudBackupApi` for file API).

---

## 12. SYNC ARCHITECTURE

```
Local mutation (repo/service)
  → sync_queue PENDING (entityName, entityId, operation, priority, transactionGroupId)
  → SyncManagerImpl.scheduleSync / immediate
  → SyncWorker.doWork
  → SyncRepositoryImpl.processNextItems
       cleanCorrupted + requeueStale
       await session / ensureAuthenticatedSession
       sort FK order (parent first; DELETE inverted)
       group by transactionGroupId
       batch fetch remote conflict rows
       uploadEntity → PostgREST insert/upsert/delete
       markUploaded + delete queue row
  → failures: retry PENDING (≤5) or FAILED with SyncErrorDetails
  → ≥5 failures + setting → NotificationHelper.showSyncAlert

Remote → local:
  RefreshDataUseCase (pull)  |  Realtime change → feature ViewModel refresh
  conflict-on-upload: remote newer → downloadAndReplaceLocal
```

**entityName → table map** lives in `SyncRepositoryImpl.entityTable()` (patients, patient_visits, vaccination_items, waste_records, reminders, vaccines, vaccine_batches, inventory_transactions, patient_notes, finance_transactions, expenses, profiles, borrow_records, borrow_returns, audit_logs, consultations, todos, personal_vaccine_reminders, doctor slots).

Restore path re-enqueues only rows actually written by merge (`BackupRestorer.enqueueUnsyncedForResync`).

---

## 13. BACKGROUND WORK

| Worker | Started by | Reads | Writes | Calls | Afterward |
|---|---|---|---|---|---|
| `SyncWorker` | `NeoChildApp` periodic 15m; `SyncManagerImpl` one-time/immediate | `sync_queue`, entity tables, session | marks queue; PostgREST | `NotificationHelper` on ≥5 fails | Result.success / retry |
| `DailySummaryWorker` | `ReminderScheduler.scheduleDailySummary` (hourly check, daily fire at settings time); `runNow` | due reminders, inventory lows | — | `NotificationHelper.showDailySummary` | notification |
| `PersonalReminderNotificationWorker` | `ReminderScheduler.schedulePersonalReminderNotifications` (24h) | `personal_vaccine_reminders` | — | NotificationHelper channel personal | notification |
| `AutoBackupWorker` | `BackupAutoScheduler.rearmIfEnabled` (from app start) | all tables via collector | `backup_history`, backup file / cloud | `BackupRepositoryImpl.performAutomaticBackup` | history + optional alert |
| `WidgetWorker` | `WidgetUtils.enqueueUniqueWork` (post-changes) | due list + patients | `widget_due_cache` | Glance `VaccineWidget.update` | widget repaint |
| `PatientClinicIdMigrationWorker` | `PatientRepositoryImpl` on patient create path (unique work) | patients needing clinic id | patient ids + enqueue sync | SyncRepositoryImpl | rows sync |

Screen-off receiver in `NeoChildApp` → `BiometricLockManager.onScreenOff` (in-process, not a manifest receiver).

---

## 14. NOTIFICATION ARCHITECTURE

```
Sources
  DailySummaryWorker / PersonalReminderNotificationWorker / SyncWorker / FCM
        ↓
  NotificationHelper (channels)
        ↓
  Android NotificationManagerCompat

Channels (const in NotificationHelper):
  daily_summary · low_stock_alerts · sync_backup_alerts · app_updates · personal_vaccine_reminders

FCM: NeoChildFirebaseMessagingService
  onMessageReceived type=app_update → showUpdateNotification
  onNewToken → DeviceRepositoryImpl.registerDeviceWithToken (Supabase user/device)
```

No clinical push from server for due vaccines — those are local scheduled notifications + widget.

---

## 15. BACKUP / RESTORE ARCHITECTURE

```
Backup:
  BackupSettingsScreen / BackupViewModel / AutoBackupWorker
    → BackupRepositoryImpl (exportBackupToUri | cloudBackupNow | performAutomaticBackup)
      → BackupCollector (all tables via BackupDao, parent-first)
      → BackupSerializer + BackupCrypto (password envelope .nccb)
      → local Uri  |  CloudBackupApi → Cloudflare Worker → R2 bucket
      → SafetyBackupStore (pre-restore safety copy)
      → BackupHistoryEntity row

Restore:
  file/cloud → BackupValidator (peek) → BackupRestorer
    RestoreMode: REPLACE (child-first delete, parent-first insert) | MERGE
    MERGE: last-write-wins vs existing rows (same rule as sync)
    → enqueueUnsyncedForResync → normal sync pipeline
    → UI refresh
```

Deliberately excluded from backup: `sync_queue`, `widget_due_cache`.

---

## 16. NAVIGATION MAP

```
App Launch (MainActivity)
  → await session → LOGIN | DASHBOARD
LOGIN ──success──→ DASHBOARD (pop login)

DASHBOARD (drawer + cards)
  ├→ patient_list → add_patient / patient_details / edit_patient
  │     patient_details → add_vaccine/{id} | add_consultation/{id}
  │                     → edit_vaccination/{id} | edit_consultation/{id} | edit_patient
  │     search → patient_details
  ├→ today_patients (tab/highlight query)
  ├→ due → completed_dismissed?tab=
  ├→ vaccine_inventory → add/edit vaccine definition, add/edit batch, stock history, add stock
  ├→ borrowed | waste
  ├→ statistics → monthly_finance_details | milestone_patients | full_report | vaccine_detail
  ├→ expenses → add/edit expense
  ├→ personal_reminders → add/edit personal reminder
  ├→ doctor_timings (weekly slots)
  ├→ manage_staff [AdminGuard] → staff details / add / edit
  ├→ sync | audit_logs | profile | search
  ├→ settings
  │    → notification | inventory | backup | security | help | privacy | terms | app_update
  └→ logout → login
```

Route constants: `Routes.kt` (54 routes). Role gates: `AdminGuard` for staff; statistics access admin|doctor in `StatisticsScreen`.

---

## 17. ANDROID ENTRY POINTS

| Entry | Class | How it enters architecture |
|---|---|---|
| Application | `NeoChildApp` | Hilt, SQLCipher, schedules Sync/Reminders/AutoBackup, COGS migrate, biometric receiver |
| Launcher Activity | `MainActivity` | Compose `AppNavigation`, biometric lock UI, PostgREST auth imports for session |
| Deep link | `neochild://auth-callback` on MainActivity | Supabase Auth redirect |
| Widget config Activity | `VaccineWidgetConfigurationActivity` | Glance prefs + widget setup |
| Widget receiver | `VaccineWidgetReceiver` | Glance `VaccineWidget` render from `widget_due_cache` |
| Install receiver | `AppUpdateInstallReceiver` | APK install lifecycle |
| FCM service | `NeoChildFirebaseMessagingService` | update notifications + token register |
| Workers (Hilt) | six workers listed above | WorkManager |
| Startup provider | androidx-startup with WorkManagerInitializer **removed** | manual `Configuration.Provider` |
| Dynamic receiver | screen-off in `NeoChildApp` | biometric lock |

No other manifest services/receivers.

---

## 18. SHARED COMPONENTS

| Component | Dependents (who) |
|---|---|
| `AppDatabase` | DatabaseModule, all repos, BackupDao path, workers, ClinicalVaccinationService |
| `SyncRepositoryImpl` | nearly every repository + AuditLogger + ClinicalVaccinationService + MergePatientsUseCase + BackupRestorer (re-enqueue) |
| `PatientUtils` / `DateClassifier` | ViewModels, WidgetWorker, Due, Stats, receipts, sync timestamps |
| `NotificationHelper` | ReminderScheduler workers, SyncWorker, FCM service |
| `AuditLogger` | repos on mutations → `audit_logs` |
| `SupabaseModule` client/plugins | Auth, AdminViewModel functions, all postgrest users, storage, realtime users |
| `NetworkMonitor` | dashboards/list VMs for refresh UX |
| `core/ui` (`AppBackground`, buttons, fields, skeleton, pull-refresh) | essentially every screen / nav graph |
| `RefreshDataUseCase` | Sync screen, pull-to-refresh paths |
| `BackupDao` | Collector + Restorer only (additive bulk API) |

---

## 19. DEPENDENCY HOTSPOTS

(From graph degree — connectivity, not a verdict.)

| Node | Degree | Why highly connected |
|---|---:|---|
| `Vaccination` (domain model) | 85 | Shared by clinical service, edit engine, finance, reminders, stats, widget, receipts, UI |
| `BackupDao` | 76 | Single bulk API over nearly every table |
| `AppBackground()` | 72 | Universal screen chrome |
| `AppDatabase` | 67 | Root of all Room access + DI providers |
| `AppNavigation()` / `Navigation` | 51–64 | Every feature screen registered here |
| `PatientUtils` | 51 | Dates, names, ISO timestamps used everywhere |
| `SyncRepositoryImpl` | 48 | Outbox for all mutating features |
| `ReminderEntity` | 44 | Due/completed/dismissed/widget/summary |
| `InventoryRepositoryImpl` | 40 | Stock for vacc/borrow/waste/restock |
| `dp` / `modifier` / `hiltviewmodel` | high | language/UI tokens (graph noise, not architecture) |

---

## 20. CIRCULAR DEPENDENCIES

- **Import cycles:** GRAPH_REPORT — **none detected**.
- **Large undirected SCC (~2901 nodes):** artifact of treating an undirected graph as strongly connected once UI tokens link everything — **not** a real Kotlin package cycle.
- **Isolated multi-node SCCs** are within single JSON/TS config files (cloudflare worker, package.json, opencode config) — intra-file noise, not app cycles.
- **Real directed workflows** (service ↔ repos) are intentional: `ClinicalVaccinationService` calls repos; repos do not call the service back.

**No application-level circular dependency reported.**

---

## 21. POTENTIALLY UNUSED CODE

Labeled **potentially unused** only:

- Report: **252 isolated symbol nodes** (mostly `dp`, `modifier`, config keys like `$schema`/`plugin`, generated tokens) — extraction noise, not source files.
- **745 nodes with ≤1 connection** — mostly Compose modifiers, test asserts, thin doc nodes.
- Isolated graph nodes: root `build.gradle`, `settings.gradle`, logo drawable, one docs fragment — connected in reality via Gradle/Manifest outside AST edges.
- No Kotlin class was confirmed as a zero-reference orphan in this pass; Android entry points (receivers, workers) legitimately appear “unreferenced” in imports because the Manifest/WorkManager register them by name.

---

## 22. EXTERNAL SERVICES

```
App
 ├─ Supabase SDK → Auth / PostgREST / Realtime WS / Storage / Edge Functions
 ├─ Firebase Cloud Messaging → app update pushes + token
 ├─ GitHub Releases HTTP → AppUpdateManager version check + APK
 ├─ Cloudflare Worker (cloudflare/backup-worker) → R2 bucket neo-child-clinic-backups
 ├─ Google Play Services → ProviderInstaller (TLS security provider)
 └─ Biometric prompt / Android Keystore → SecurityUtils DB passphrase
```

---

## 23–24. FEATURE TRACEABILITY + CRUD

### Vaccination

| Op | Path |
|---|---|
| Create | `AddVaccinationScreen` → `AddVaccinationViewModel` → `ClinicalVaccinationService.recordVaccination` → `VaccinationRepositoryImpl` → `VaccinationDao` + finance + inventory + reminders → sync |
| Read | `PatientViewModel` / cards · `DueViewModel` · `StatisticsViewModel` · `WidgetWorker` → DAOs / `getDueList` |
| Update | `VaccinationEditEngine` (via edit route) → repo update + inventory reverse/rededuct + finance `updateIncomeForVisit` |
| Delete | `PatientRepositoryImpl.deletePatient` cascades visit/reminder deletes + sync DELETE (no free-standing visit delete UI) |

### Patient

| Op | Path |
|---|---|
| Create | `AddPatientScreen` → `PatientViewModel`/`PatientListViewModel` → `PatientRepositoryImpl.addPatient` → `PatientDao` → sync `PATIENT` (+ clinic-id migration worker) |
| Read | list/search/details → `PatientDao` flows → UI |
| Update | edit route → `PatientRepositoryImpl` update + audit + sync |
| Delete | `PatientRepositoryImpl` deletes notes/reminders/vaccinations then patient + enqueues DELETEs |

### Inventory batch

| Op | Path |
|---|---|
| Create | `AddStockScreen`/`AddBatchScreen` → VM → `InventoryRepositoryImpl.addBatch`/`addStockBatch` → vaccine/batch/transaction rows + grouped sync |
| Read | inventory screens → `VaccineDao` flows |
| Update | `updateBatch` + transaction notes + sync |
| Delete | `deleteBatch`/`deleteVaccine` + sync DELETE |

### Reminder

CRUD via `ReminderRepositoryImpl` + `DueReminderDao` (`insert/update/delete`, `moveDueToCompleted/Dismissed`), UI in Due + edit dialogs; sync `REMINDERS` (special serverId path).

### Finance transaction

Create/update only through `FinanceRepositoryImpl.recordIncome` / `updateIncomeForVisit` / consultation updates — **no delete** (comment: never delete historical finance rows).

### Expense / Waste / Borrow

Standard repo DAO CRUD + `syncRepository.enqueue` (see §7–8).

---

## 25. GRAPH VIEWS (how to open them)

All in `graphify-out/graph.html` (filter/community) and `GRAPH_REPORT.md`:

| View | How to see it |
|---|---|
| A System overview | §1 above + community hubs |
| B Feature map | §2 table + communities “*Screen UI” |
| C Feature implementation | §23 traces |
| D Data flow | §4 + §10 |
| E Database | §5 + communities `* DAO`, `App Database Core` |
| F Vaccination | §6 |
| G Inventory | §7 |
| H Finance | §8 |
| I Sync | §12 + community `Sync Queue Repository` |
| J Navigation | §16 |
| K Background/notifications | §13–14 |
| L Backup | §15 + hyperedge “Backup/Restore Data Flow” |
| M Hotspots | §19 god nodes |
| N Cycles | §20 (none) |

---

## 26–27. ORGANIZATION & LABELS

Communities already labeled in `GRAPH_REPORT.md` (e.g. “Add Vaccination Flow”, “Sync Queue Repository”, “Backup Collect Restore”, “Vaccine Widget Glance”).  
Node label convention for drill-down: **Name / Type / Layer / Feature** (e.g. `VaccinationRepositoryImpl · Repository · Data · Vaccination`).

---

## 28. VERIFICATION BASIS

Used: Manifest components, Hilt modules, constructor `@Inject` lists, Room `@Database`/`@Entity`/`@Query`, `entityTable()` map, WorkManager enqueue sites, Navigation `composable` graph, Supabase module installs, migrations under `supabase/`, edge function invokes, Storage bucket string, graph edges (EXTRACTED 97%), GRAPH_REPORT cycles/god nodes.

Not inferred from folder names alone.

---

## 29. SPECIAL-CASE COMPONENTS (why connected)

- **`AppDatabase`:** only SQLCipher door; every DAO hangs off it; backup + sync + clinical service all start here.
- **`BackupDao`:** by design one bulk CRUD surface so backup does not touch 20 feature DAOs.
- **`Vaccination`:** central clinical document (visit + items + money + next dose).
- **`SyncRepositoryImpl`:** every mutation that must reach Supabase goes through one outbox API; also holds conflict policy and entity→table map.
- **`ClinicalVaccinationService`:** single transactional entry so visit/finance/reminders/inventory cannot diverge.
- **Auth/session:** shared by Navigation start destination, SyncWorker gating, Admin edge function tokens.

---

## 30. HOW THE APP WORKS (26 answers)

1. **Starts:** Manifest → `NeoChildApp` (Hilt, SQLCipher, schedule workers) → `MainActivity` → `AppNavigation` waits for session.
2. **Auth:** Supabase Auth + optional deep link; profile role from `profiles`; biometric app lock separate from server auth.
3. **Navigation:** single Compose NavHost, `Routes`, drawer from dashboard, `AdminGuard` for staff.
4. **Feature structure:** Screen → VM → (few use cases/services) → concrete repo → Room and/or PostgREST.
5. **UI → logic:** ViewModel methods call repos/services; state via StateFlow.
6. **Repos → data:** local DAO write + `enqueue` + optional `cloudRefresh` read.
7. **Room:** encrypted `neochild_db` v30, explicit migrations only.
8. **Supabase:** one client module; PostgREST for tables, Auth, Realtime (2 channels), Storage docs, Functions manage-staff.
9. **Offline-first:** Room is truth; network optional for UI reads.
10. **Sync:** outbox → `SyncWorker` → FK-ordered groups → upsert with last-write-wins.
11. **Realtime:** patient list + today’s todos only → trigger feature refresh (not full merge).
12. **Workers:** sync, daily summary, personal reminders, auto backup, widget cache, clinic-id migration.
13. **Notifications:** local channels from workers; FCM only for app updates.
14. **Vaccination flow:** §6.
15. **Reminders:** §9 status machine + date filters.
16. **Inventory changes:** batch qty + `inventory_transactions` (+ server trigger remotely).
17. **Borrow/Waste:** deduct via inventory repo; returns/waste-reversals add compensating transactions.
18. **Finance:** income from vaccination/consultation + expenses → `finance_transactions`/`expenses`.
19. **Statistics:** pure in-memory calculators over refreshed DAO lists.
20. **Backup/restore:** §15 collector/envelope/validator/restorer.
21. **Widget:** `WidgetWorker` precomputes `widget_due_cache`; Glance reads cache.
22. **Shared:** §18.
23. **Hotspots:** §19.
24. **Cycles:** none detected in imports.
25. **Potentially unused:** §21 (mostly graph tokens).
26. **Where data lives:** every feature’s Room table + matching Supabase table via `entityTable()` (§5, §12).

---

### God Nodes (from GRAPH_REPORT)

1. `Vaccination` — 85  
2. `BackupDao` — 76  
3. `AppBackground()` — 72  
4. `AppDatabase` — 67  
5. `BackTopAppBar()` — 55  
6. `AppNavigation()` — 51  
7. `PatientUtils` — 51  
8. `SyncRepositoryImpl` — 48  
9. `AppPullToRefresh()` — 44  
10. `ReminderEntity` — 44  

### Surprising Connections (sample)

- `DueTab()` → `CompletedDismissedSummaryCards()` (summary cards shared across due/completed UI)
- Docs: restore resync reuses the same sync pipeline as offline-first architecture
- Release notify workflow references `.env.local` Supabase config + FCM

### Suggested next questions

- Why does `AppDatabase` bridge ~30 communities? (betweenness 0.096)
- Why does `Vaccination` bridge clinical, finance, stats, widget?
- Are the few INFERRED edges on `Vaccination` / `AppBackground` correct?
