# Graph Report - vaccine_manager_app  (2026-09-27)

## Corpus Check
- 377 files · ~197,660 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 23 file(s) not represented in the graph (top: (none) 6, .xml 6, .toml 3)

## Summary
- 3608 nodes · 9410 edges · 190 communities (161 shown, 29 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 265 edges (avg confidence: 0.85)
- Token cost: 1,250 input · 850 output

## Community Hubs (Navigation)
- UI Components & Dialogs
- Navigation & UI Primitives
- Navigation Guards & UI Patterns
- UI State & Domain Models
- Skeleton Loading UI
- Audit & Backup Services
- Biometric Authentication
- Domain Entities & Mappers
- Receipt Printing
- Sync Engine
- Finance & Reporting
- Background Workers
- Vaccine & Inventory DAOs
- Navigation & Charts
- Vaccine & Inventory Repository
- Backup Serialization
- Finance DAOs
- Patient Utils & Cloud Backup
- Backup DAO & Cleanup
- Database Migrations
- Community 20
- Community 21
- Community 22
- Community 23
- Community 24
- Community 25
- Community 26
- Community 27
- Community 28
- Community 29
- Community 30
- Community 31
- Community 32
- Community 33
- Community 34
- Community 35
- Community 36
- Community 37
- Community 38
- Community 39
- Community 40
- Community 41
- Community 42
- Community 43
- Community 44
- Community 45
- Community 46
- Community 47
- Community 48
- Community 49
- Community 50
- Community 51
- Community 52
- Community 53
- Community 54
- Community 55
- Community 56
- Community 57
- Community 58
- Community 59
- Community 60
- Community 61
- Community 62
- Community 63
- Community 64
- Community 65
- Community 66
- Community 67
- Community 68
- Community 69
- Community 70
- Community 71
- Community 72
- Community 73
- Community 74
- Community 75
- Community 76
- Community 77
- Community 78
- Community 79
- Community 80
- Community 81
- Community 82
- Community 83
- Community 84
- Community 85
- Community 86
- Community 87
- Community 88
- Community 89
- Community 90
- Community 91
- Community 92
- Community 93
- Community 94
- Community 95
- Community 96
- Community 97
- Community 98
- Community 99
- Community 100
- Community 101
- Community 102
- Community 103
- Community 104
- Community 105
- Community 106
- Community 107
- Community 108
- Community 109
- Community 110
- Community 111
- Community 112
- Community 113
- Community 114
- Community 115
- Community 116
- Community 117
- Community 118
- Community 119
- Community 120
- Community 121
- Community 122
- Community 123
- Community 124
- Community 125
- Community 126
- Community 127
- Community 128
- Community 129
- Community 130
- Community 131
- Community 132
- Community 133
- Community 134
- Community 135
- Community 136
- Community 137
- Community 138
- Community 139
- Community 140
- Community 141
- Community 142
- Community 143
- Community 144
- Community 145
- Community 146
- Community 147
- Community 148
- Community 149
- Community 150
- Community 151
- Community 152
- Community 153
- Community 154
- Community 155
- Community 156
- Community 157
- Community 158
- Community 159
- Community 160
- Community 161
- Community 162
- Community 163
- Community 164
- Community 165
- Community 166
- Community 167
- Community 168
- Community 169
- Community 170
- Community 171
- Community 172
- Community 173
- Community 174
- Community 175
- Community 176
- Community 177
- Community 178
- Community 179
- Community 180
- Community 181
- Community 182
- Community 183
- Community 184
- Community 185
- Community 186
- Community 188

## God Nodes (most connected - your core abstractions)
1. `Vaccination` - 101 edges
2. `BackupDao` - 76 edges
3. `AppBackground()` - 72 edges
4. `AppDatabase` - 66 edges
5. `BackTopAppBar()` - 55 edges
6. `ReminderEntity` - 54 edges
7. `PatientUtils` - 51 edges
8. `InventoryRepository` - 45 edges
9. `AppPullToRefresh()` - 44 edges
10. `Profile` - 42 edges

## Surprising Connections (you probably didn't know these)
- `BackupDao` --conceptually_related_to--> `Room`  [INFERRED]
  docs/BACKUP_RESTORE.md → README.md
- `Room` --conceptually_related_to--> `Room Database Version 30`  [INFERRED]
  README.md → docs/GETTING_STARTED.md
- `SQLCipher` --conceptually_related_to--> `Local DB Encryption (SQLCipher + Keystore)`  [INFERRED]
  README.md → docs/BACKUP_RESTORE.md
- `Supabase` --conceptually_related_to--> `Supabase Configuration (.env.local)`  [INFERRED]
  README.md → docs/GETTING_STARTED.md
- `Firebase Cloud Messaging` --conceptually_related_to--> `Firebase Configuration (google-services.json)`  [INFERRED]
  README.md → docs/GETTING_STARTED.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Resolved Audit Findings (19 items)** — neochildclinic_audit_fullrecheck_md_syncrepositoryimpl, neochildclinic_audit_fullrecheck_md_syncuploader, neochildclinic_audit_fullrecheck_md_clinicalvaccinationservice, neochildclinic_audit_fullrecheck_md_vaccinationeditengine, neochildclinic_audit_fullrecheck_md_borrowrepositoryimpl, neochildclinic_audit_fullrecheck_md_inventoryrepository, neochildclinic_audit_fullrecheck_md_syncentitydescriptor, neochildclinic_audit_fullrecheck_md_biometriclockmanager, neochildclinic_audit_fullrecheck_md_authviewmodel, neochildclinic_audit_fullrecheck_md_mainactivity, neochildclinic_audit_fullrecheck_md_repositorymodule [EXTRACTED 0.95]
- **Backup & Restore Component Chain** — docs_backup_restore_md_backup_settings_screen, docs_backup_restore_md_backup_viewmodel, docs_backup_restore_md_backup_repository_impl, docs_backup_restore_md_backup_collector, docs_backup_restore_md_backup_validator, docs_backup_restore_md_backup_serializer, docs_backup_restore_md_backup_crypto, docs_backup_restore_md_backup_restorer, docs_backup_restore_md_safety_backup_store, docs_backup_restore_md_cloud_backup_api, docs_backup_restore_md_backup_dao [EXTRACTED 0.95]
- **CI/CD Pipeline Workflows** — github_workflows_codeql_yml_codeql_workflow, github_workflows_android_ci_yml_android_ci_workflow, github_workflows_notify_update_yml_release_notification [INFERRED 0.85]
- **Navigation Drawer Menu Items** — docs_screenshot_app_drawer_png_dashboard, docs_screenshot_app_drawer_png_personal_reminders, docs_screenshot_app_drawer_png_manage_staff, docs_screenshot_app_drawer_png_audit_log [EXTRACTED 1.00]
- **Dashboard Summary Cards** — docs_screenshot_app_drawer_png_patient_module, docs_screenshot_app_drawer_png_inventory_module, docs_screenshot_app_drawer_png_waste_module [EXTRACTED 1.00]
- **Borrow Vaccine Flow** — docs_screenshot_borrow_vaccine_borrow_vaccine_screen, docs_screenshot_borrow_vaccine_borrow_request, docs_screenshot_borrow_vaccine_source_facility, docs_screenshot_borrow_vaccine_destination_facility, docs_screenshot_borrow_vaccine_approval_workflow [INFERRED 0.80]
- **Main Dashboard Navigation Cards** — docs_screenshot_dashboard_patient_list_card, docs_screenshot_dashboard_todays_patient_card, docs_screenshot_dashboard_statistics_card, docs_screenshot_dashboard_inventory_card [EXTRACTED 1.00]
- **Inventory Lifecycle Cards** — docs_screenshot_dashboard_inventory_card, docs_screenshot_dashboard_borrowed_card, docs_screenshot_dashboard_waste_card, docs_screenshot_dashboard_low_stock_badge [INFERRED 0.75]
- **Due Vaccination Screen Layout Pattern** — docs_screenshot_due_vaccination_status_tabs, docs_screenshot_due_vaccination_search_bar, docs_screenshot_due_vaccination_time_filters, docs_screenshot_due_vaccination_empty_state [INFERRED 0.85]

## Communities (190 total, 29 thin omitted)

### Community 0 - "UI Components & Dialogs"
Cohesion: 0.06
Nodes (77): add, alignment, alpha, AuditLogDialog(), StaffCard(), StatusBadge(), ExceptionRow(), UnavailabilityTab() (+69 more)

### Community 1 - "Navigation & UI Primitives"
Cohesion: 0.06
Nodes (72): activityresultcontracts, inventoryGraph(), NavHostController, Routes, Constants, NeoChildTheme(), EmptyState(), Color (+64 more)

### Community 2 - "Navigation Guards & UI Patterns"
Cohesion: 0.07
Nodes (69): AdminGuard(), dashboardNavGraph(), com, NavHostController, NavHostController, reminderGraph(), AppBackground(), BackTopAppBar() (+61 more)

### Community 3 - "UI State & Domain Models"
Cohesion: 0.06
Nodes (26): Error, FullDayUnavailable, Idle, Loaded, Loading, loadUiState(), NoScheduleConfigured, SlotsUiState (+18 more)

### Community 4 - "Skeleton Loading UI"
Cohesion: 0.06
Nodes (52): animatefloat, AccessDeniedScreen(), HorizontalLineRefreshIndicator(), Modifier, Dp, Modifier, PaddingValues, rememberShimmerBrush() (+44 more)

### Community 5 - "Audit & Backup Services"
Cohesion: 0.08
Nodes (33): AuditLogger, WidgetUtils, BackupMigrator, applicationcontext, await, build, columns, combine (+25 more)

### Community 6 - "Biometric Authentication"
Cohesion: 0.07
Nodes (31): aeadbadtagexception, BiometricAuthenticator, AuthenticationCallback, Context, FragmentActivity, SecretKey, ByteArray, SecretKey (+23 more)

### Community 7 - "Domain Entities & Mappers"
Cohesion: 0.10
Nodes (29): toDomain(), toDomain(), toDomain(), toDomain(), toEntity(), toEntity(), toDomain(), toDomain() (+21 more)

### Community 8 - "Receipt Printing"
Cohesion: 0.07
Nodes (33): Context, Patient, ReceiptFormatter, Context, Patient, ReceiptGenerator, Bundle, Context (+25 more)

### Community 9 - "Sync Engine"
Cohesion: 0.07
Nodes (27): SyncOperation, CREATE, DELETE, UPDATE, SyncPriority, HIGH, LOW, MEDIUM (+19 more)

### Community 10 - "Finance & Reporting"
Cohesion: 0.11
Nodes (18): FinanceTransaction, FinanceCalculator, FinanceSummaryItem, FinanceTable(), FinanceTableHeader(), FinanceTableRow(), FinanceTableTotalRow(), improvementLabel() (+10 more)

### Community 11 - "Background Workers"
Cohesion: 0.09
Nodes (22): com, CoroutineWorker, PatientClinicIdMigrationWorker, ConsultationEditEngine, Result, NO_CHANGES, UPDATED, AutoBackupWorker (+14 more)

### Community 12 - "Vaccine & Inventory DAOs"
Cohesion: 0.08
Nodes (5): Flow, VaccineDao, InventoryTransactionEntity, VaccineBatchEntity, deducted()

### Community 13 - "Navigation & Charts"
Cohesion: 0.07
Nodes (34): AppNavigation(), androidx, com, NavHostController, statisticsGraph(), SyncState, ERROR, IDLE (+26 more)

### Community 14 - "Vaccine & Inventory Repository"
Cohesion: 0.07
Nodes (8): VaccineEntity, InventoryRepository, Flow, AddVaccineUiState, AddVaccineViewModel, StateFlow, ViewModel, update

### Community 15 - "Backup Serialization"
Cohesion: 0.14
Nodes (14): BackupDeviceInfo, BackupEnvelope, BackupSerializer, ByteArray, CharArray, BackupLocation, CLOUD, LOCAL (+6 more)

### Community 16 - "Finance DAOs"
Cohesion: 0.09
Nodes (7): FinanceDao, Flow, FinanceEntity, FinanceRepositoryImpl, Flow, FinanceRepository, Flow

### Community 17 - "Patient Utils & Cloud Backup"
Cohesion: 0.12
Nodes (27): PatientUtils, AutomaticBackupSection(), BackupProgressDialog(), BackupSettingsScreen(), CloudBackup, CloudBackupRow(), CloudBackupSection(), CloudRestore (+19 more)

### Community 19 - "Database Migrations"
Cohesion: 0.12
Nodes (16): Migration, Migration, Migration, Migration, Migration, Migration, Migration, Migration (+8 more)

### Community 20 - "Community 20"
Cohesion: 0.08
Nodes (23): displayLabel(), InventoryFilter, ALL, AVAILABLE, EXPIRED, HIDDEN, LOW_STOCK, NEAR_EXPIRY (+15 more)

### Community 21 - "Community 21"
Cohesion: 0.10
Nodes (20): StateFlow, AuditLogUiState, FullAuditLogViewModel, StateFlow, ViewModel, FinanceDetailsViewModel, StateFlow, ViewModel (+12 more)

### Community 22 - "Community 22"
Cohesion: 0.13
Nodes (10): Vaccination, ClinicalVaccinationService, ReminderSpec, VaccinationEditEngine, DueUiState, DueViewModel, com, Flow (+2 more)

### Community 23 - "Community 23"
Cohesion: 0.07
Nodes (29): AuthExpired, BackupException, BrokenRelationships, Corrupted, Failed, InsufficientStorage, Exception, NoInternet (+21 more)

### Community 24 - "Community 24"
Cohesion: 0.11
Nodes (17): FinanceStatsData, StatisticsUtils, FinanceContent(), FinanceTab(), FinanceTabPreview(), com, OverviewContent(), FilterSection() (+9 more)

### Community 25 - "Community 25"
Cohesion: 0.11
Nodes (8): JsonObject, SessionManager, AuditLogDao, Flow, AuditLogEntity, AuditLogRepositoryImpl, AuditLogRepository, auth

### Community 26 - "Community 26"
Cohesion: 0.12
Nodes (25): AvailableSlotDropdown(), DoctorDropdown(), com, Modifier, T, SelectDropdown(), DateItem(), HorizontalDateSelector() (+17 more)

### Community 27 - "Community 27"
Cohesion: 0.10
Nodes (22): LockScreen(), DaySlotSection(), DoctorWeeklySlot, WeeklySlotsTab(), arrowback, calendartoday, check, close (+14 more)

### Community 28 - "Community 28"
Cohesion: 0.11
Nodes (11): BackupValidationResult, CloudBackupMetadata, Invalid, RestoreMode, MERGE, REPLACE, Valid, BackupRepository (+3 more)

### Community 29 - "Community 29"
Cohesion: 0.17
Nodes (26): BackupRow, Env, fetch(), getOwnedBackup(), handleConfirm(), handleDelete(), handleDownload(), handleRetention() (+18 more)

### Community 31 - "Community 31"
Cohesion: 0.08
Nodes (22): toDomain(), ExpenseCategory, CLEANING, ELECTRICITY, EQUIPMENT, INTERNET, MAINTENANCE, MARKETING (+14 more)

### Community 32 - "Community 32"
Cohesion: 0.13
Nodes (6): ExpenseRepositoryImpl, Flow, Expense, ExpenseRepository, Flow, ExpenseEntityMappingTest

### Community 33 - "Community 33"
Cohesion: 0.15
Nodes (4): SlotFilterState, SlotSegment, TodaySlotFilter, TodaySlotFilterTest

### Community 34 - "Community 34"
Cohesion: 0.17
Nodes (10): BackupUiMessage, BackupUiState, BackupViewModel, Cloud, CharArray, StateFlow, Uri, ViewModel (+2 more)

### Community 35 - "Community 35"
Cohesion: 0.08
Nodes (24): description, devDependencies, @cloudflare/vitest-pool-workers, @cloudflare/workers-types, typescript, vitest, wrangler, name (+16 more)

### Community 36 - "Community 36"
Cohesion: 0.11
Nodes (19): Auth, Bundle, FragmentActivity, Intent, io, Postgrest, MainActivity, SyncManagerImpl (+11 more)

### Community 37 - "Community 37"
Cohesion: 0.14
Nodes (23): NavHostController, patientGraph(), SearchTopAppBar(), AddPatientScreen(), PatientDetailsScreen(), Modifier, Patient, ManualMergeDialog() (+15 more)

### Community 38 - "Community 38"
Cohesion: 0.10
Nodes (15): android, com, Intent, NeoChildApp, ProviderInstallListener, ReminderScheduler, Application, Configuration (+7 more)

### Community 39 - "Community 39"
Cohesion: 0.11
Nodes (9): InventoryDeduction, InventoryDeductionsDialog(), ByteArray, Flow, Patient, StateFlow, ViewModel, PatientVaccinationCardData (+1 more)

### Community 40 - "Community 40"
Cohesion: 0.12
Nodes (15): StockHistoryTypeFilter, ADDED, ADJUSTMENT, ALL, BORROWED, RETURNED, REVERSAL, USED (+7 more)

### Community 41 - "Community 41"
Cohesion: 0.15
Nodes (8): BorrowedVaccine, BorrowReturnRecord, toDomain(), BorrowRepositoryImpl, Flow, BorrowRepository, Flow, NewBatchInfo

### Community 42 - "Community 42"
Cohesion: 0.12
Nodes (11): BackupAutoScheduler, BackupSettingsManager, Flow, AutoBackupSettings, BackupFrequency, DAILY, WEEKLY, BackupProgress (+3 more)

### Community 43 - "Community 43"
Cohesion: 0.11
Nodes (12): DocumentRepositoryImpl, ByteArray, toPatientDocument(), PatientDocument, DocumentRepository, ByteArray, DocumentCard(), binds (+4 more)

### Community 44 - "Community 44"
Cohesion: 0.11
Nodes (12): NotificationSettings, StateFlow, ViewModel, NotificationSettingsViewModel, StateFlow, ViewModel, SettingsViewModel, StateFlow (+4 more)

### Community 45 - "Community 45"
Cohesion: 0.20
Nodes (6): EditReconciler, ItemPlan, VaccinationItem, ReminderPlan, ReminderRow, EditReconcilerTest

### Community 46 - "Community 46"
Cohesion: 0.11
Nodes (6): DashboardViewModel, Flow, Patient, StateFlow, ViewModel, TodoBundle

### Community 47 - "Community 47"
Cohesion: 0.13
Nodes (9): ExpenseListUiState, ExpenseListViewModel, ExpenseSortOption, AMOUNT_ASC, AMOUNT_DESC, DATE_ASC, DATE_DESC, StateFlow (+1 more)

### Community 48 - "Community 48"
Cohesion: 0.09
Nodes (7): AddEditPersonalReminderUiState, AddEditPersonalReminderViewModel, Flow, Patient, StateFlow, ViewModel, flowpreview

### Community 49 - "Community 49"
Cohesion: 0.08
Nodes (19): Converters, body, bodyasbytes, bodyastext, cio, contentnegotiation, contenttype, decodefromstring (+11 more)

### Community 50 - "Community 50"
Cohesion: 0.12
Nodes (10): ReminderDueListProcessor, Flow, ReminderStats, NextVaccinationSummary, DueTab(), DueTabPreview(), Patient, ManageDueBottomSheet() (+2 more)

### Community 51 - "Community 51"
Cohesion: 0.13
Nodes (4): Flow, VaccinationDao, PatientVaccinationCardEntity, VisitEntity

### Community 52 - "Community 52"
Cohesion: 0.13
Nodes (21): toDomain(), ConsultationTodo, VaccinationTodo, DashboardCard(), DashboardCardSmall(), Color, Dp, ImageVector (+13 more)

### Community 53 - "Community 53"
Cohesion: 0.14
Nodes (7): awaitSessionResolved(), classifySessionReadinessAfterFailedRefresh(), isSessionTokenUsable(), SessionStatus, StateFlow, shouldRefreshSessionFor(), SyncErrorClassificationTest

### Community 54 - "Community 54"
Cohesion: 0.23
Nodes (3): InventoryRepositoryImpl, Flow, buildStockTransaction()

### Community 55 - "Community 55"
Cohesion: 0.12
Nodes (6): isTransientSyncError(), Exception, Flow, io, SessionAuthTransientException, SyncRepositoryImpl

### Community 56 - "Community 56"
Cohesion: 0.22
Nodes (8): BackupMetadataDto, CloudBackupApi, ConfirmBody, ByteArray, T, RetentionBody, WorkerErrorBody, HttpResponse

### Community 57 - "Community 57"
Cohesion: 0.13
Nodes (5): DoctorAvailabilityDao, Flow, DoctorSlotExceptionEntity, DoctorWeeklySlotEntity, toEntity()

### Community 58 - "Community 58"
Cohesion: 0.11
Nodes (23): Neo Child Clinic Full Re-Audit Report, AuthViewModel, BiometricLockManager, BorrowedViewModel, BorrowRepositoryImpl, ClinicalVaccinationService, ClinicStatsManager, CompletedDismissedViewModel (+15 more)

### Community 59 - "Community 59"
Cohesion: 0.16
Nodes (4): ReminderAuditEntity, ReminderEntity, Flow, ReminderRepository

### Community 60 - "Community 60"
Cohesion: 0.20
Nodes (9): assertarrayequals, assertequals, assertfalse, assertnotequals, assertnull, assertthrows, asserttrue, test (+1 more)

### Community 61 - "Community 61"
Cohesion: 0.11
Nodes (18): Flow, NetworkMonitor, Flow, RealtimeChangeSubscriptions, awaitclose, callbackflow, cancel, catch (+10 more)

### Community 62 - "Community 62"
Cohesion: 0.16
Nodes (4): ProfileRepositoryImpl, Profile, Flow, ProfileRepository

### Community 63 - "Community 63"
Cohesion: 0.16
Nodes (13): Context, Auth, Postgrest, SupabaseModule, createsupabaseclient, Functions, installin, kotlinxserializer (+5 more)

### Community 64 - "Community 64"
Cohesion: 0.13
Nodes (8): Patient, StateFlow, ViewModel, PatientListViewModel, PatientSortOption, NAME_AZ, NEWEST, RefreshState

### Community 65 - "Community 65"
Cohesion: 0.19
Nodes (3): AppUpdateInfo, AppUpdateManager, JSONObject

### Community 66 - "Community 66"
Cohesion: 0.15
Nodes (4): InventoryDeductionDao, InventoryDeductionEntity, completedQuantityByBatch(), VaccinationDeletionTest

### Community 67 - "Community 67"
Cohesion: 0.15
Nodes (5): StateFlow, ViewModel, WeeklyDoctorSlotsUiState, WeeklyDoctorSlotsViewModel, doctorweeklyslot

### Community 68 - "Community 68"
Cohesion: 0.16
Nodes (18): BorrowedRecordCard(), Color, Modifier, QuantityStat(), statusColor(), statusLabel(), BorrowedDisplayItem, BorrowStatus (+10 more)

### Community 69 - "Community 69"
Cohesion: 0.15
Nodes (13): endOfDay(), java, startOfDay(), toLocalDate(), calendar, chronounit, instant, optional (+5 more)

### Community 70 - "Community 70"
Cohesion: 0.15
Nodes (3): Flow, SyncQueueDao, SyncQueueEntity

### Community 71 - "Community 71"
Cohesion: 0.13
Nodes (6): AddExpenseUiState, AddExpenseViewModel, ByteArray, StateFlow, ViewModel, roundtolong

### Community 72 - "Community 72"
Cohesion: 0.18
Nodes (6): BroadcastReceiver, AppUpdateInstallReceiver, Context, Intent, android, NotificationHelper

### Community 73 - "Community 73"
Cohesion: 0.13
Nodes (9): Flow, PreferenceManager, Flow, NotificationSettingsManager, booleanpreferenceskey, datastore, map, preferencesdatastore (+1 more)

### Community 74 - "Community 74"
Cohesion: 0.15
Nodes (10): metadataString(), kotlinx, T, SyncEntityDescriptor, SyncUploader, contentornull, decodefromjsonelement, JsonElement (+2 more)

### Community 75 - "Community 75"
Cohesion: 0.24
Nodes (13): AgeDistributionSection(), calculatePatientStats(), GenderDistributionCard(), GenderLegendItem(), Color, Modifier, Patient, MilestoneCard() (+5 more)

### Community 76 - "Community 76"
Cohesion: 0.15
Nodes (3): Flow, PatientTodoDao, VaccinationTodoEntity

### Community 77 - "Community 77"
Cohesion: 0.20
Nodes (3): AppDatabase, DatabaseModule, com

### Community 78 - "Community 78"
Cohesion: 0.17
Nodes (5): toDomain(), DoctorSlotException, DoctorAvailabilityRepository, DoctorWeeklySlot, Flow

### Community 79 - "Community 79"
Cohesion: 0.16
Nodes (4): cloudRefresh(), Flow, Patient, PatientRepositoryImpl

### Community 80 - "Community 80"
Cohesion: 0.18
Nodes (4): ConsultationRepositoryImpl, Consultation, ConsultationRepository, Flow

### Community 81 - "Community 81"
Cohesion: 0.14
Nodes (11): RefreshDataUseCase, StateFlow, ViewModel, StatisticsUiState, StatisticsViewModel, StateFlow, ViewModel, VaccineDetailEntry (+3 more)

### Community 82 - "Community 82"
Cohesion: 0.23
Nodes (16): Reminder, Patient, VaccinationRecordCard(), Patient, StatisticsTabContent(), AdministeredDose, administeredDoses(), calculateUpcomingVaccineNeeds() (+8 more)

### Community 83 - "Community 83"
Cohesion: 0.15
Nodes (14): actionparametersof, actionruncallback, actionstartactivity, Color, GlanceAppWidget, VaccineWidget, ColorProvider, cornerradius (+6 more)

### Community 84 - "Community 84"
Cohesion: 0.18
Nodes (3): Flow, PatientDao, PatientEntity

### Community 85 - "Community 85"
Cohesion: 0.17
Nodes (9): Available, AvailableSlot, DoctorAvailabilityResult, FullDayUnavailable, NoScheduleConfigured, SlotExceptionType, FULL_DAY, SLOT (+1 more)

### Community 86 - "Community 86"
Cohesion: 0.19
Nodes (5): AdminUiState, AdminViewModel, StateFlow, ViewModel, viewmodelscope

### Community 87 - "Community 87"
Cohesion: 0.15
Nodes (13): Context, GlanceId, Palette, VaccineWidgetTheme, CLINIC_BLUE, CLINIC_GREEN, DARK, GLASS (+5 more)

### Community 88 - "Community 88"
Cohesion: 0.13
Nodes (12): after, androidjunit4, ExpenseMigrationTest, applicationprovider, assertnotnull, before, frameworksqliteopenhelperfactory, instrumentationregistry (+4 more)

### Community 89 - "Community 89"
Cohesion: 0.23
Nodes (4): AgeMilestone, AgeUtils, InventoryUtils, PatientUtilsTest

### Community 90 - "Community 90"
Cohesion: 0.18
Nodes (10): DateCategory, DateClassifier, Future, Overdue, Today, Tomorrow, DuePatientCard(), Modifier (+2 more)

### Community 91 - "Community 91"
Cohesion: 0.13
Nodes (12): BackupHistoryEntity, BackupHistoryStatus, FAILED, IN_PROGRESS, SUCCESS, BackupHistoryType, CLOUD_BACKUP, CLOUD_RESTORE (+4 more)

### Community 93 - "Community 93"
Cohesion: 0.12
Nodes (13): InventoryTransactionType, BORROW_RETURN, BORROWED, COLD_CHAIN_FAILURE, CONTAMINATED, DAMAGED, EXPIRED, MANUAL_ADJUSTMENT (+5 more)

### Community 94 - "Community 94"
Cohesion: 0.18
Nodes (6): AddStockUiState, AddStockViewModel, StateFlow, ViewModel, StockBatchFormState, StockVaccineFormState

### Community 95 - "Community 95"
Cohesion: 0.15
Nodes (8): StateFlow, ViewModel, PersonalReminderTab, ACTIVE, CANCELLED, COMPLETED, PersonalReminderUiState, PersonalReminderViewModel

### Community 96 - "Community 96"
Cohesion: 0.20
Nodes (5): AppUpdateViewModel, DownloadProgress, Job, StateFlow, ViewModel

### Community 97 - "Community 97"
Cohesion: 0.22
Nodes (7): ByteArray, Context, SecurityUtils, encryptedsharedpreferences, generalsecurityexception, log, MasterKey

### Community 98 - "Community 98"
Cohesion: 0.21
Nodes (4): BorrowDao, Flow, BorrowEntity, toEntity()

### Community 99 - "Community 99"
Cohesion: 0.24
Nodes (5): Patient, StatisticsDateUtils, Patient, OverviewTab(), arrowforward

### Community 101 - "Community 101"
Cohesion: 0.32
Nodes (4): BackupCrypto, ByteArray, CharArray, BackupCryptoTest

### Community 102 - "Community 102"
Cohesion: 0.16
Nodes (3): Flow, VaccinationItemDao, VaccinationItemEntity

### Community 105 - "Community 105"
Cohesion: 0.22
Nodes (3): com, Flow, VaccinationRepositoryImpl

### Community 106 - "Community 106"
Cohesion: 0.19
Nodes (13): PatientNote, EmptySectionText(), HistorySegmentedButton(), InfoGridRow(), InfoRow(), androidx, PaddingValues, Patient (+5 more)

### Community 107 - "Community 107"
Cohesion: 0.21
Nodes (5): StateFlow, ViewModel, WasteInventoryItem, WasteUiState, WasteViewModel

### Community 108 - "Community 108"
Cohesion: 0.19
Nodes (8): BorrowedUiState, BorrowedViewModel, BorrowMainTab, BORROWED, RETURNED, BorrowReturnDisplayItem, StateFlow, ViewModel

### Community 110 - "Community 110"
Cohesion: 0.22
Nodes (3): ExpenseDao, Flow, ExpenseEntity

### Community 111 - "Community 111"
Cohesion: 0.26
Nodes (3): Flow, PersonalReminderDao, PersonalReminderEntity

### Community 112 - "Community 112"
Cohesion: 0.24
Nodes (3): Flow, ProfileDao, ProfileEntity

### Community 113 - "Community 113"
Cohesion: 0.24
Nodes (3): Flow, WasteDao, WasteEntity

### Community 114 - "Community 114"
Cohesion: 0.23
Nodes (4): DoctorAvailabilityRepositoryImpl, DoctorWeeklySlot, Flow, toentity

### Community 115 - "Community 115"
Cohesion: 0.22
Nodes (8): Flow, Patient, SearchPatientsUseCase, Patient, StateFlow, ViewModel, SearchUiState, SearchViewModel

### Community 117 - "Community 117"
Cohesion: 0.33
Nodes (12): categoryLabel(), FinanceMonthSummary(), FinanceTransactionCard(), formatCurrency(), Composable, Modifier, Patient, MonthlyFinanceDetailsScreen() (+4 more)

### Community 118 - "Community 118"
Cohesion: 0.21
Nodes (10): Bundle, VaccineWidgetConfigurationActivity, WidgetConfigurationScreen(), appwidgetmanager, ComponentActivity, glanceappwidgetmanager, mainscope, preferencesglancestatedefinition (+2 more)

### Community 120 - "Community 120"
Cohesion: 0.30
Nodes (6): AuthViewModel, SessionStatus, StateFlow, ViewModel, email, UserInfo

### Community 121 - "Community 121"
Cohesion: 0.18
Nodes (6): PatientIdGenerator, Context, existingworkpolicy, onetimeworkrequestbuilder, outofquotapolicy, workmanager

### Community 122 - "Community 122"
Cohesion: 0.36
Nodes (4): BackupCollector, BackupPayloadV1, BackupRestorer, Outcome

### Community 123 - "Community 123"
Cohesion: 0.38
Nodes (3): BackupValidator, RestoreSummary, BackupValidatorTest

### Community 124 - "Community 124"
Cohesion: 0.23
Nodes (4): BorrowReturnDao, Flow, BorrowReturnEntity, toEntity()

### Community 125 - "Community 125"
Cohesion: 0.26
Nodes (3): ConsultationDao, Flow, ConsultationEntity

### Community 128 - "Community 128"
Cohesion: 0.21
Nodes (4): AddConsultationUiState, AddConsultationViewModel, StateFlow, ViewModel

### Community 129 - "Community 129"
Cohesion: 0.17
Nodes (11): compilerOptions, lib, module, moduleResolution, noEmit, resolveJsonModule, skipLibCheck, strict (+3 more)

### Community 131 - "Community 131"
Cohesion: 0.25
Nodes (3): Flow, PatientNotesDao, PatientNotesEntity

### Community 132 - "Community 132"
Cohesion: 0.18
Nodes (6): Flow, PersonalReminderStatus, CANCELLED, COMPLETED, PENDING, READY

### Community 134 - "Community 134"
Cohesion: 0.33
Nodes (4): StaffManagementRepositoryImpl, CreateStaffRequest, StaffActionRequest, StaffManagementRepository

### Community 135 - "Community 135"
Cohesion: 0.35
Nodes (3): Flow, WasteRecord, WasteRepositoryImpl

### Community 136 - "Community 136"
Cohesion: 0.22
Nodes (6): BatchStatus, ACTIVE, AddBatchUiState, AddBatchViewModel, StateFlow, ViewModel

### Community 137 - "Community 137"
Cohesion: 0.33
Nodes (3): Flow, Patient, PatientRepository

### Community 138 - "Community 138"
Cohesion: 0.29
Nodes (3): Flow, WasteRecord, WasteRepository

### Community 139 - "Community 139"
Cohesion: 0.20
Nodes (11): BackupCollector, BackupCrypto, BackupRepositoryImpl, BackupRestorer, BackupValidator, CloudBackupApi (Ktor -> Worker), Two Independent Encryption Schemes, Local DB Encryption (SQLCipher + Keystore) (+3 more)

### Community 140 - "Community 140"
Cohesion: 0.20
Nodes (11): App Navigation Drawer Screenshot, Admin Role, Audit Log Screen, Dashboard Screen, Inventory Module with Low Stock Alert, Manage Staff Screen, Navigation Drawer, Online Connectivity Status (+3 more)

### Community 141 - "Community 141"
Cohesion: 0.20
Nodes (5): ref_https, corsHeaders, corsHeaders, getAccessToken(), str2ab()

### Community 142 - "Community 142"
Cohesion: 0.38
Nodes (9): animatedvisibility, BatchRow(), capitalize(), FilterButton(), SortButton(), StockStatusBadge(), VaccineInventoryContent(), VaccineInventoryScreen() (+1 more)

### Community 143 - "Community 143"
Cohesion: 0.20
Nodes (9): CustomColors, compositionlocalprovider, darkcolorscheme, dynamicdarkcolorscheme, dynamiclightcolorscheme, immutable, issystemindarktheme, lightcolorscheme (+1 more)

### Community 144 - "Community 144"
Cohesion: 0.27
Nodes (4): ClinicStatsManager, com, Flow, ClinicStats

### Community 146 - "Community 146"
Cohesion: 0.33
Nodes (3): Flow, WidgetDueDao, WidgetDueEntity

### Community 149 - "Community 149"
Cohesion: 0.40
Nodes (9): ActionRow(), ChangePasswordDialog(), EditProfileDialog(), InfoRow(), InfoSection(), Color, ImageVector, ProfileHeaderSection() (+1 more)

### Community 150 - "Community 150"
Cohesion: 0.27
Nodes (4): StateFlow, ViewModel, ProfileUiState, ProfileViewModel

### Community 151 - "Community 151"
Cohesion: 0.20
Nodes (10): Firebase Configuration (google-services.json), Supabase Configuration (.env.local), Firebase Cloud Messaging, Hilt, Jetpack Compose, Kotlin, MVVM / Clean Architecture, Supabase (+2 more)

### Community 152 - "Community 152"
Cohesion: 0.29
Nodes (10): Dashboard Screenshot, Borrowed Card, Due Card, Inventory Card, Low Stock Badge, Neo Child Clinic, Patient List Card, Statistics Card (+2 more)

### Community 153 - "Community 153"
Cohesion: 0.28
Nodes (7): RemoteReminder, toDomain(), toLocal(), toRemote(), encodedefault, experimentalserializationapi, transient

### Community 154 - "Community 154"
Cohesion: 0.53
Nodes (4): dao, insert, onconflictstrategy, query

### Community 155 - "Community 155"
Cohesion: 0.42
Nodes (7): Color, Modifier, Patient, PersonalReminderCard(), reminderDateBadge(), StatusChip(), statusColor()

### Community 157 - "Community 157"
Cohesion: 0.47
Nodes (8): AddEditPersonalReminderScreen(), clickableSelect(), FieldError(), com, Modifier, PatientPicker(), SectionLabel(), VaccineDropdown()

### Community 158 - "Community 158"
Cohesion: 0.22
Nodes (9): BackupDao, Supabase Edge Functions (manage-staff, notify-update), Getting Started Guide, Offline-First Development Workflow, Project Requirements (Kotlin 2.4.10, AGP 9.3.2, SDK 35/29), Room Database Version 30, Security Rules for Developers, Supabase notify-update Edge Function (+1 more)

### Community 159 - "Community 159"
Cohesion: 0.22
Nodes (9): BackupMigrator, Backup & Restore Architecture, BackupSerializer, BackupSettingsScreen, BackupViewModel, Known Limitations (hard-delete resurrection, patientClinicId collision), Merge Rules (last-write-wins, insert-if-absent), .nccb Backup File Format (+1 more)

### Community 160 - "Community 160"
Cohesion: 0.22
Nodes (9): Release Notification Workflow, UPDATE_NOTIFIER_SECRET, Backend Configuration (Supabase, Firebase), In-App Updates via GitHub Releases, Neo Child Clinic Vaccine Manager Project, Security & Privacy, Responsible Disclosure, Security Policy (+1 more)

### Community 161 - "Community 161"
Cohesion: 0.29
Nodes (5): androidentrypoint, com, NeoChildFirebaseMessagingService, FirebaseMessagingService, supervisorjob

### Community 162 - "Community 162"
Cohesion: 0.48
Nodes (5): ActionCallback, ActionParameters, Context, GlanceId, RefreshWidgetAction

### Community 163 - "Community 163"
Cohesion: 0.29
Nodes (6): Context, database, room, RoomDatabase, supportopenhelperfactory, typeconverters

### Community 165 - "Community 165"
Cohesion: 0.29
Nodes (5): ReminderStatus, ACTIVE, COMPLETED, DISMISSED, EXTERNAL

### Community 166 - "Community 166"
Cohesion: 0.38
Nodes (4): CompletedDismissedUiState, CompletedDismissedViewModel, StateFlow, ViewModel

### Community 167 - "Community 167"
Cohesion: 0.38
Nodes (7): Completed Status Indicator, Dismissed Status Indicator, Empty State Message, Due Vaccinations Screen, Search by Name or Phone, Vaccination Status Tabs, Time Filter Tabs (All/Overdue/Today/Week/Month)

### Community 168 - "Community 168"
Cohesion: 0.29
Nodes (7): Android CI Workflow, Debug APK Build, Android Lint (lintDebug), Unit Tests (testDebugUnitTest), CI Firebase Configuration, CodeQL Advanced Workflow, Java/Kotlin CodeQL Analysis

### Community 170 - "Community 170"
Cohesion: 0.47
Nodes (3): TimeRange, AddExceptionDialog(), TimePickerDialog()

### Community 172 - "Community 172"
Cohesion: 0.33
Nodes (6): Approval Workflow, Borrow Request, Borrow Vaccine Screen, Destination Facility, Source Facility, Vaccine Inventory

### Community 173 - "Community 173"
Cohesion: 0.40
Nodes (5): GroupResult, FAILED, FAILED_TRANSIENT_RETRY, SESSION_DEFERRED, SUCCESS

### Community 174 - "Community 174"
Cohesion: 0.40
Nodes (5): Cloudflare Backup Worker, BACKUP_WORKER_URL, Cloudflare R2 Bucket (neo-child-clinic-backups), SUPABASE_JWKS_URL, SUPABASE_JWT_SECRET

### Community 175 - "Community 175"
Cohesion: 0.40
Nodes (5): Sync Integration (resync unsynced rows), Architecture Layers (UI, Domain, Data), Encrypted Local Room Database, Offline-First Architecture, Supabase Synchronization

### Community 176 - "Community 176"
Cohesion: 0.50
Nodes (4): SessionReadiness, LOGGED_OUT, RETRY_LATER, USABLE

### Community 177 - "Community 177"
Cohesion: 0.50
Nodes (4): InventoryStatus, COMPLETED, FAILED, PARTIAL

### Community 178 - "Community 178"
Cohesion: 0.83
Nodes (3): GlanceAppWidget, VaccineWidgetReceiver, GlanceAppWidgetReceiver

### Community 179 - "Community 179"
Cohesion: 0.50
Nodes (4): Filter and Search Controls, Inventory List Layout, Inventory Screen, Low Stock Indicator

### Community 180 - "Community 180"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

## Knowledge Gaps
- **281 isolated node(s):** `CustomColors`, `CREATE`, `UPDATE`, `DELETE`, `PENDING` (+276 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 854 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **29 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Vaccination` connect `Community 22` to `UI Components & Dialogs`, `Navigation & UI Primitives`, `UI State & Domain Models`, `Audit & Backup Services`, `Domain Entities & Mappers`, `Receipt Printing`, `Finance & Reporting`, `Navigation & Charts`, `Finance DAOs`, `Community 21`, `Community 24`, `Community 156`, `Community 165`, `Community 166`, `Community 39`, `Community 50`, `Community 59`, `Community 60`, `Community 69`, `Community 75`, `Community 81`, `Community 82`, `Community 90`, `Community 99`, `Community 100`, `Community 105`, `Community 106`, `Community 117`, `Community 127`?**
  _High betweenness centrality (0.075) - this node is a cross-community bridge._
- **Why does `PatientUtils` connect `Patient Utils & Cloud Backup` to `Community 128`, `Navigation & UI Primitives`, `Navigation Guards & UI Patterns`, `UI Components & Dialogs`, `Community 132`, `Audit & Backup Services`, `UI State & Domain Models`, `Receipt Printing`, `Finance & Reporting`, `Background Workers`, `Vaccine & Inventory DAOs`, `Community 144`, `Community 145`, `Community 22`, `Community 32`, `Community 165`, `Community 39`, `Community 40`, `Community 47`, `Community 48`, `Community 50`, `Community 54`, `Community 55`, `Community 69`, `Community 71`, `Community 75`, `Community 82`, `Community 89`, `Community 91`, `Community 114`, `Community 117`?**
  _High betweenness centrality (0.067) - this node is a cross-community bridge._
- **Why does `AppDatabase` connect `Community 77` to `Community 131`, `Community 132`, `Audit & Backup Services`, `Community 135`, `Sync Engine`, `Background Workers`, `Vaccine & Inventory DAOs`, `Finance DAOs`, `Backup DAO & Cleanup`, `Database Migrations`, `Community 146`, `Community 147`, `Community 25`, `Community 30`, `Community 32`, `Community 163`, `Community 41`, `Community 51`, `Community 57`, `Community 63`, `Community 66`, `Community 70`, `Community 74`, `Community 76`, `Community 83`, `Community 84`, `Community 88`, `Community 91`, `Community 98`, `Community 102`, `Community 110`, `Community 111`, `Community 112`, `Community 113`, `Community 114`, `Community 119`, `Community 121`, `Community 124`, `Community 125`?**
  _High betweenness centrality (0.062) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `Vaccination` (e.g. with `toVaccination()` and `.saveVaccination()`) actually correct?**
  _`Vaccination` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `CustomColors`, `CREATE`, `UPDATE` to the rest of the system?**
  _281 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `UI Components & Dialogs` be split into smaller, more focused modules?**
  _Cohesion score 0.06418918918918919 - nodes in this community are weakly interconnected._
- **Should `Navigation & UI Primitives` be split into smaller, more focused modules?**
  _Cohesion score 0.060246360582306833 - nodes in this community are weakly interconnected._