# Graph Report - vaccine_manager_app  (2026-09-23)

## Corpus Check
- 312 files · ~181,343 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 23 file(s) not represented in the graph (top: (none) 6, .xml 6, .toml 3)

## Summary
- 3098 nodes · 8089 edges · 174 communities (150 shown, 24 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 222 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Core UI Components
- App Navigation Graph
- Routes and Drawer Shell
- Constants and Repos Utils
- App Prefs and Sync Manager
- Audit Logger and Sync Enums
- Biometric Authentication
- Personal Reminder DAO
- Today Patients Screen
- Receipt Formatting
- Skeleton and Pull Refresh
- Dropdowns and Text Fields
- Cloud Backup API
- Vaccine DAO
- Cloud Refresh Repos
- Finance DAO
- Backup History UI
- Clinic ID Migration
- Add Vaccination Flow
- Due Reminder DAO
- Waste Screen UI
- Sync Queue Repository
- Local Room Entities
- Backup Error Types
- Backup Repository Core
- Backup DAO Clears
- DB Migrations
- Borrow Repository
- Network Monitor Slots
- Vaccination DAO
- Sync Session Readiness
- Cloudflare Backup Worker
- Unit and DAO Tests
- Expense Domain Model
- Worker Package Config
- Clinical Vaccination Service
- Backup Settings UI
- Notification Settings
- Date Classifier
- Inventory Expiry Utils
- Statistics Charts
- Patient ViewModel
- App Update Manager
- MainActivity Entry
- Inventory Stock Ops
- Dashboard ViewModel
- Statistics Tabs
- Doctor Availability DAO
- Finance Calculator
- Backup Validator
- Vaccine CRUD DAO
- Doctor Availability Repo
- Patient Repository
- DI Modules Supabase
- Patient List ViewModel
- Statistics Overview UI
- Sync Status Enums
- Expense List Filters
- Borrowed Cards UI
- Backup DAO Batch Ops
- Reminder Status Model
- Reminder Repository
- Database DI Providers
- Vaccine Widget Glance
- Patient Todo DAO
- Patient DAO
- Sync Queue DAO
- Admin Staff ViewModel
- Weekly Slots ViewModel
- Widget Theme Layout
- Patient Info Components
- Add Expense ViewModel
- Personal Reminder Tabs
- Patient Stats Distribution
- App Update ViewModel
- Borrow DAO
- Expense DAO
- Reminder Sync Repo
- Add Stock ViewModel
- Stock History Filters
- Expense Calculator Stats
- Security Utils Crypto
- Backup Crypto
- Patient Search Use Case
- Expense Validation Tests
- App Lock Biometrics
- Application Security Provider
- Converters and Serializers
- Audit Log DAO
- Consultation DAO
- Profile DAO
- Waste DAO
- Backup Auto Scheduler
- Device Registration
- Widget Config Activity
- Expense DAO Instrumented Tests
- Clinic Stats Manager
- Backup Collect Restore
- Patient Notes DAO
- Expense Repository
- Dashboard Cards
- Waste ViewModel
- Statistics Screen
- Notification Helper
- Backup Restore Docs
- Worker TSConfig
- Borrow Return Records
- Age Milestone Tests
- Backup Models Envelope
- Borrow Return DAO
- Add Consultation Flow
- Drawer Screenshot
- Manage Staff Function
- CI and Getting Started
- Update Install Receiver
- Inventory Screen UI
- Inventory Deduction DAO
- Document Repository
- Waste Repository
- Available Slots Use Case
- Auth Login ViewModel
- Edit Reminder Screen
- Sync Screen ViewModel
- Expense Migration Tests
- Widget Due DAO
- Patient Todo Repository
- Inventory Filter Enums
- Inventory Sort Enums
- Stock History Filters
- Inventory ViewModel
- Profile ViewModel
- Dashboard Screenshot
- Widget Update Vaccination
- Doctor Availability Model
- Vaccination Edit Engine
- Add Batch ViewModel
- Statistics Date Utils
- OpenCode Graphify Config
- Widget Refresh Action
- App Database Core
- Consultation Repository
- Audit Log Paging
- Today Patients Card
- Borrow Screenshot
- Vaccination Todo DAO
- Backup History Types
- Inventory Status Enums
- Weekly Slots Screen
- Due Vaccination Screenshot
- Patient Details Screenshot
- Expense Sort Options
- Backup Architecture Docs
- Security and Guides Docs
- Inventory Screenshot
- Graphify Plugin Config
- GitHub CI Workflows
- Design System Typography
- JSON Metadata Utils
- Backup History Status
- Sync State Enum
- Widget Receiver
- Gradle Wrapper Script
- AGENTS Instructions
- App Build Gradle
- Worker R2 and Versioning
- Backup Crypto Docs
- Vaccine Domain Model
- App Logo Asset
- JWT Isolation Docs
- Encryption Docs
- Clinic Logo Asset
- Backup Access Key Docs

## God Nodes (most connected - your core abstractions)
1. `Vaccination` - 85 edges
2. `BackupDao` - 76 edges
3. `AppBackground()` - 72 edges
4. `AppDatabase` - 67 edges
5. `BackTopAppBar()` - 55 edges
6. `AppNavigation()` - 51 edges
7. `PatientUtils` - 51 edges
8. `SyncRepositoryImpl` - 48 edges
9. `AppPullToRefresh()` - 44 edges
10. `ReminderEntity` - 44 edges

## Surprising Connections (you probably didn't know these)
- `BackupRepositoryImpl` --implements--> `Layered Architecture (UI to Domain to Data)`  [INFERRED]
  docs/BACKUP_RESTORE.md → README.md
- `DueTab()` --calls--> `CompletedDismissedSummaryCards()`  [INFERRED]
  app/src/main/java/com/neochildclinic/features/reminder/DueTab.kt → app/src/main/java/com/neochildclinic/features/reminder/ReminderCards.kt
- `Notify App Users About Release Workflow` --references--> `.env.local Configuration (SUPABASE_URL / SUPABASE_PUBLISHABLE_KEY)`  [INFERRED]
  .github/workflows/notify-update.yml → docs/GETTING_STARTED.md
- `Supported Versions (0.5.x)` --references--> `Neo Child Clinic - Vaccine Manager`  [INFERRED]
  SECURITY.md → README.md
- `Restore Resync via Existing Sync Pipeline` --conceptually_related_to--> `Offline-First Architecture (Room/SQLCipher + Supabase Sync)`  [INFERRED]
  docs/BACKUP_RESTORE.md → README.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Backup/Restore Data Flow** — docs_backup_restore_backup_settings_screen, docs_backup_restore_backup_view_model, docs_backup_restore_backup_repository_impl, docs_backup_restore_backup_collector, docs_backup_restore_backup_restorer, docs_backup_restore_safety_backup_store, docs_backup_restore_cloud_backup_api, docs_backup_restore_backup_dao [EXTRACTED 1.00]
- **Release Update Notification Flow** — _github_workflows_notify_update_workflow, docs_getting_started_notify_update, docs_getting_started_update_notifier_secret, docs_getting_started_fcm [EXTRACTED 1.00]
- **Client/Server Secret Separation** — readme_never_commit_secrets, docs_getting_started_rls, docs_backup_restore_no_r2_access_key [INFERRED 0.85]

## Communities (174 total, 24 thin omitted)

### Community 0 - "Core UI Components"
Cohesion: 0.05
Nodes (72): add, EmptyState(), Color, Modifier, RoundedCornerShape, StandardButton(), AuditLogDialog(), DeleteConfirmationDialog() (+64 more)

### Community 1 - "App Navigation Graph"
Cohesion: 0.06
Nodes (67): AccessDeniedScreen(), AdminGuard(), AppNavigation(), androidx, com, nullableStringArg(), stringArg(), AppBackground() (+59 more)

### Community 2 - "Routes and Drawer Shell"
Cohesion: 0.11
Nodes (41): alignment, alpha, Routes, AppDrawer(), DrawerMenuItem(), ImageVector, DashboardSmallActionsRow(), DashboardScreen() (+33 more)

### Community 3 - "Constants and Repos Utils"
Cohesion: 0.10
Nodes (31): Constants, metadataString(), ExpenseRepositoryImpl, ProfileRepositoryImpl, BatchStatus, ACTIVE, Profile, RefreshDataUseCase (+23 more)

### Community 4 - "App Prefs and Sync Manager"
Cohesion: 0.05
Nodes (39): Flow, PreferenceManager, PatientIdGenerator, SyncManagerImpl, BackupFrequency, DAILY, WEEKLY, Flow (+31 more)

### Community 5 - "Audit Logger and Sync Enums"
Cohesion: 0.10
Nodes (28): AuditLogger, SyncOperation, CREATE, DELETE, UPDATE, SyncPriority, HIGH, LOW (+20 more)

### Community 6 - "Biometric Authentication"
Cohesion: 0.07
Nodes (31): aeadbadtagexception, BiometricAuthenticator, AuthenticationCallback, Context, FragmentActivity, SecretKey, ByteArray, SecretKey (+23 more)

### Community 7 - "Personal Reminder DAO"
Cohesion: 0.06
Nodes (11): Flow, PersonalReminderDao, PersonalReminderEntity, Flow, PersonalReminderRepositoryImpl, AddEditPersonalReminderUiState, AddEditPersonalReminderViewModel, Flow (+3 more)

### Community 8 - "Today Patients Screen"
Cohesion: 0.06
Nodes (43): activityresultcontracts, CustomColors, AddTypeSelectionDialog(), DateItem(), EnhancedAddTodoDialog(), HorizontalDateSelector(), com, Patient (+35 more)

### Community 9 - "Receipt Formatting"
Cohesion: 0.07
Nodes (33): Context, Patient, ReceiptFormatter, Context, Patient, ReceiptGenerator, Bundle, Context (+25 more)

### Community 10 - "Skeleton and Pull Refresh"
Cohesion: 0.08
Nodes (42): animatefloat, HorizontalLineRefreshIndicator(), Modifier, Dp, Modifier, PaddingValues, rememberShimmerBrush(), SkeletonBox() (+34 more)

### Community 11 - "Dropdowns and Text Fields"
Cohesion: 0.12
Nodes (35): AvailableSlotDropdown(), DateDropdownPicker(), DoctorDropdown(), com, Modifier, T, SelectDropdown(), Modifier (+27 more)

### Community 12 - "Cloud Backup API"
Cohesion: 0.09
Nodes (25): BackupMetadataDto, CloudBackupApi, ConfirmBody, ByteArray, T, RetentionBody, WorkerErrorBody, CloudBackupMetadata (+17 more)

### Community 13 - "Vaccine DAO"
Cohesion: 0.08
Nodes (4): Flow, VaccineDao, InventoryTransactionEntity, VaccineBatchEntity

### Community 14 - "Cloud Refresh Repos"
Cohesion: 0.07
Nodes (13): cloudRefresh(), FinanceRepositoryImpl, Flow, com, Flow, VaccinationRepositoryImpl, StateFlow, ViewModel (+5 more)

### Community 15 - "Finance DAO"
Cohesion: 0.10
Nodes (16): FinanceDao, Flow, FinanceEntity, ChartMode, BAR, LINE, FullReportDataPoint, FullReportUiState (+8 more)

### Community 16 - "Backup History UI"
Cohesion: 0.10
Nodes (27): kotlinx, BackupHistoryEntity, Flow, AutomaticBackupSection(), BackupProgressDialog(), BackupSettingsScreen(), CloudBackup, CloudBackupRow() (+19 more)

### Community 17 - "Clinic ID Migration"
Cohesion: 0.11
Nodes (19): com, CoroutineWorker, PatientClinicIdMigrationWorker, ConsultationEditEngine, Result, NO_CHANGES, UPDATED, AutoBackupWorker (+11 more)

### Community 18 - "Add Vaccination Flow"
Cohesion: 0.09
Nodes (8): AddVaccinationUiState, AddVaccinationViewModel, StateFlow, ViewModel, NextVaccinationGroup, NextVaccinationItem, VaccineSelectionState, VaccinationItem

### Community 19 - "Due Reminder DAO"
Cohesion: 0.11
Nodes (6): DueReminderDao, Flow, ReminderEntity, calculateUpcomingVaccineNeeds(), UpcomingVaccineNeedSection(), UpcomingVaccineTypeStat

### Community 20 - "Waste Screen UI"
Cohesion: 0.12
Nodes (27): NeoChildTheme(), NextVaccinationSummary, Modifier, WasteRecord, WasteContent(), WasteEntryDialog(), WasteItemCard(), WastePreview() (+19 more)

### Community 21 - "Sync Queue Repository"
Cohesion: 0.11
Nodes (7): SyncQueueEntity, Exception, Flow, io, kotlinx, SessionAuthTransientException, SyncRepositoryImpl

### Community 22 - "Local Room Entities"
Cohesion: 0.27
Nodes (8): toDomain(), columninfo, entity, foreignkey, index, primarykey, serializable, serialname

### Community 23 - "Backup Error Types"
Cohesion: 0.07
Nodes (29): AuthExpired, BackupException, BrokenRelationships, Corrupted, Failed, InsufficientStorage, Exception, NoInternet (+21 more)

### Community 24 - "Backup Repository Core"
Cohesion: 0.17
Nodes (12): BackupLocation, CLOUD, LOCAL, BackupRepositoryImpl, ByteArray, CharArray, Uri, BackupOperationResult (+4 more)

### Community 26 - "DB Migrations"
Cohesion: 0.13
Nodes (15): Migration, Migration, Migration, Migration, Migration, Migration, Migration, Migration (+7 more)

### Community 27 - "Borrow Repository"
Cohesion: 0.11
Nodes (14): BorrowedVaccine, toDomain(), BorrowRepositoryImpl, Flow, NewBatchInfo, BorrowedDisplayItem, BorrowedUiState, BorrowedViewModel (+6 more)

### Community 28 - "Network Monitor Slots"
Cohesion: 0.10
Nodes (24): Flow, NetworkMonitor, Error, FullDayUnavailable, Idle, Loaded, Loading, loadUiState() (+16 more)

### Community 29 - "Vaccination DAO"
Cohesion: 0.11
Nodes (7): Flow, VaccinationDao, PatientVaccinationCardEntity, toEntity(), VisitEntity, embedded, relation

### Community 30 - "Sync Session Readiness"
Cohesion: 0.11
Nodes (11): awaitSessionResolved(), classifySessionReadinessAfterFailedRefresh(), isSessionTokenUsable(), SessionStatus, StateFlow, SessionReadiness, LOGGED_OUT, RETRY_LATER (+3 more)

### Community 31 - "Cloudflare Backup Worker"
Cohesion: 0.17
Nodes (26): BackupRow, Env, fetch(), getOwnedBackup(), handleConfirm(), handleDelete(), handleDownload(), handleRetention() (+18 more)

### Community 32 - "Unit and DAO Tests"
Cohesion: 0.11
Nodes (19): after, applicationprovider, assertarrayequals, assertequals, assertfalse, assertnotequals, assertnotnull, assertnull (+11 more)

### Community 33 - "Expense Domain Model"
Cohesion: 0.08
Nodes (22): toDomain(), ExpenseCategory, CLEANING, ELECTRICITY, EQUIPMENT, INTERNET, MAINTENANCE, MARKETING (+14 more)

### Community 34 - "Worker Package Config"
Cohesion: 0.08
Nodes (24): description, devDependencies, @cloudflare/vitest-pool-workers, @cloudflare/workers-types, typescript, vitest, wrangler, name (+16 more)

### Community 35 - "Clinical Vaccination Service"
Cohesion: 0.12
Nodes (12): toVaccination(), Vaccination, ClinicalVaccinationService, DueUiState, DueViewModel, com, Flow, StateFlow (+4 more)

### Community 36 - "Backup Settings UI"
Cohesion: 0.17
Nodes (10): BackupUiMessage, BackupUiState, BackupViewModel, Cloud, CharArray, StateFlow, Uri, ViewModel (+2 more)

### Community 37 - "Notification Settings"
Cohesion: 0.11
Nodes (15): NotificationSettings, NotificationSettingsManager, StateFlow, ViewModel, NotificationSettingsViewModel, StateFlow, ViewModel, SettingsViewModel (+7 more)

### Community 38 - "Date Classifier"
Cohesion: 0.12
Nodes (13): DateCategory, DateClassifier, Future, Overdue, Today, Tomorrow, Flow, ClinicStats (+5 more)

### Community 39 - "Inventory Expiry Utils"
Cohesion: 0.13
Nodes (9): InventoryUtils, endOfDay(), toLocalDate(), Flow, Modifier, PatientAvatar(), PatientCard(), PatientInfoSubtitle() (+1 more)

### Community 40 - "Statistics Charts"
Cohesion: 0.13
Nodes (20): BarLineChart(), BarLineSeries, Modifier, ChartModeToggle(), FullReportScreen(), composable, fillmaxsize, icon (+12 more)

### Community 41 - "Patient ViewModel"
Cohesion: 0.11
Nodes (9): ByteArray, com, FileObject, Flow, Patient, StateFlow, ViewModel, PatientVaccinationCardData (+1 more)

### Community 42 - "App Update Manager"
Cohesion: 0.15
Nodes (6): AppUpdateInfo, AppUpdateManager, Modifier, StartupUpdateBanner(), HttpURLConnection, JSONObject

### Community 43 - "MainActivity Entry"
Cohesion: 0.13
Nodes (18): Auth, Bundle, FragmentActivity, Intent, io, Postgrest, MainActivity, LockScreen() (+10 more)

### Community 44 - "Inventory Stock Ops"
Cohesion: 0.14
Nodes (13): InventoryTransactionType, BORROW_RETURN, BORROWED, COLD_CHAIN_FAILURE, CONTAMINATED, DAMAGED, EXPIRED, MANUAL_ADJUSTMENT (+5 more)

### Community 45 - "Dashboard ViewModel"
Cohesion: 0.11
Nodes (5): DashboardViewModel, Flow, Patient, StateFlow, ViewModel

### Community 46 - "Statistics Tabs"
Cohesion: 0.14
Nodes (14): FinanceTab(), Patient, OverviewTab(), Patient, StatisticsTabContent(), StatisticsUtils, AdministeredDose, administeredDoses() (+6 more)

### Community 47 - "Doctor Availability DAO"
Cohesion: 0.14
Nodes (5): DoctorAvailabilityDao, Flow, DoctorSlotExceptionEntity, DoctorWeeklySlotEntity, toEntity()

### Community 48 - "Finance Calculator"
Cohesion: 0.16
Nodes (9): startOfDay(), FinanceCalculator, FinanceSummaryItem, FinanceTable(), FinanceTableHeader(), FinanceTableRow(), FinanceTableTotalRow(), improvementLabel() (+1 more)

### Community 49 - "Backup Validator"
Cohesion: 0.17
Nodes (10): BackupValidator, BackupProgress, BackupValidationResult, Done, Invalid, RestoreSummary, Stage, TableDone (+2 more)

### Community 50 - "Vaccine CRUD DAO"
Cohesion: 0.11
Nodes (5): VaccineEntity, AddVaccineUiState, AddVaccineViewModel, StateFlow, ViewModel

### Community 51 - "Doctor Availability Repo"
Cohesion: 0.15
Nodes (8): toDomain(), DoctorAvailabilityRepositoryImpl, DoctorWeeklySlot, Flow, DoctorSlotException, TimeRange, AddExceptionDialog(), TimePickerDialog()

### Community 52 - "Patient Repository"
Cohesion: 0.15
Nodes (7): Flow, Patient, PatientRepositoryImpl, CompletedDismissedUiState, CompletedDismissedViewModel, StateFlow, ViewModel

### Community 53 - "DI Modules Supabase"
Cohesion: 0.16
Nodes (13): Context, Auth, Postgrest, SupabaseModule, createsupabaseclient, Functions, installin, kotlinxserializer (+5 more)

### Community 54 - "Patient List ViewModel"
Cohesion: 0.11
Nodes (8): Patient, StateFlow, ViewModel, PatientListViewModel, PatientSortOption, NAME_AZ, NEWEST, RefreshState

### Community 55 - "Statistics Overview UI"
Cohesion: 0.13
Nodes (18): FinanceStatsData, FinanceContent(), com, OverviewContent(), FilterSection(), Color, ImageVector, Modifier (+10 more)

### Community 56 - "Sync Status Enums"
Cohesion: 0.15
Nodes (15): SyncStatus, FAILED, PENDING, SYNCED, SYNCING, SyncErrorDetails, SyncItem, toDomain() (+7 more)

### Community 57 - "Expense List Filters"
Cohesion: 0.16
Nodes (5): ExpenseFilters(), ExpenseListUiState, ExpenseListViewModel, StateFlow, ViewModel

### Community 58 - "Borrowed Cards UI"
Cohesion: 0.16
Nodes (18): BorrowedRecordCard(), Color, Modifier, QuantityStat(), statusColor(), statusLabel(), BorrowStatus, BORROWED (+10 more)

### Community 59 - "Backup DAO Batch Ops"
Cohesion: 0.18
Nodes (7): Flow, VaccinationItemDao, VaccinationItemEntity, dao, insert, onconflictstrategy, query

### Community 60 - "Reminder Status Model"
Cohesion: 0.18
Nodes (15): PersonalReminderStatus, CANCELLED, COMPLETED, PENDING, READY, Color, Modifier, Patient (+7 more)

### Community 61 - "Reminder Repository"
Cohesion: 0.15
Nodes (7): ReminderAuditEntity, Flow, ReminderStatus, ACTIVE, COMPLETED, DISMISSED, EXTERNAL

### Community 62 - "Database DI Providers"
Cohesion: 0.20
Nodes (3): AppDatabase, DatabaseModule, com

### Community 63 - "Vaccine Widget Glance"
Cohesion: 0.15
Nodes (14): actionparametersof, actionruncallback, actionstartactivity, Color, GlanceAppWidget, VaccineWidget, ColorProvider, cornerradius (+6 more)

### Community 64 - "Patient Todo DAO"
Cohesion: 0.16
Nodes (3): Flow, PatientTodoDao, ConsultationTodoEntity

### Community 65 - "Patient DAO"
Cohesion: 0.18
Nodes (3): Flow, PatientDao, PatientEntity

### Community 67 - "Admin Staff ViewModel"
Cohesion: 0.20
Nodes (6): AdminUiState, AdminViewModel, CreateStaffRequest, StateFlow, ViewModel, StaffActionRequest

### Community 68 - "Weekly Slots ViewModel"
Cohesion: 0.17
Nodes (3): StateFlow, ViewModel, WeeklyDoctorSlotsViewModel

### Community 69 - "Widget Theme Layout"
Cohesion: 0.15
Nodes (13): Context, GlanceId, Palette, VaccineWidgetTheme, CLINIC_BLUE, CLINIC_GREEN, DARK, GLASS (+5 more)

### Community 70 - "Patient Info Components"
Cohesion: 0.16
Nodes (15): DocumentCard(), EmptySectionText(), HistorySegmentedButton(), InfoGridRow(), InfoRow(), InventoryDeductionsDialog(), androidx, FileObject (+7 more)

### Community 71 - "Add Expense ViewModel"
Cohesion: 0.13
Nodes (5): AddExpenseUiState, AddExpenseViewModel, ByteArray, StateFlow, ViewModel

### Community 72 - "Personal Reminder Tabs"
Cohesion: 0.12
Nodes (8): StateFlow, ViewModel, PersonalReminderTab, ACTIVE, CANCELLED, COMPLETED, PersonalReminderUiState, PersonalReminderViewModel

### Community 73 - "Patient Stats Distribution"
Cohesion: 0.29
Nodes (13): AgeDistributionSection(), calculatePatientStats(), GenderDistributionCard(), GenderLegendItem(), Color, Modifier, Patient, MilestoneCard() (+5 more)

### Community 74 - "App Update ViewModel"
Cohesion: 0.20
Nodes (5): AppUpdateViewModel, DownloadProgress, Job, StateFlow, ViewModel

### Community 75 - "Borrow DAO"
Cohesion: 0.21
Nodes (4): BorrowDao, Flow, BorrowEntity, toEntity()

### Community 76 - "Expense DAO"
Cohesion: 0.18
Nodes (4): ExpenseDao, Flow, ExpenseEntity, toEntity()

### Community 78 - "Add Stock ViewModel"
Cohesion: 0.15
Nodes (6): AddStockUiState, AddStockViewModel, StateFlow, ViewModel, StockBatchFormState, StockVaccineFormState

### Community 79 - "Stock History Filters"
Cohesion: 0.24
Nodes (5): StockHistoryFilters(), StateFlow, ViewModel, StockHistoryUiState, StockHistoryViewModel

### Community 81 - "Security Utils Crypto"
Cohesion: 0.24
Nodes (6): ByteArray, Context, SecurityUtils, encryptedsharedpreferences, generalsecurityexception, MasterKey

### Community 82 - "Backup Crypto"
Cohesion: 0.32
Nodes (4): BackupCrypto, ByteArray, CharArray, BackupCryptoTest

### Community 83 - "Patient Search Use Case"
Cohesion: 0.20
Nodes (9): Flow, Patient, SearchPatientsUseCase, Patient, StateFlow, ViewModel, SearchUiState, SearchViewModel (+1 more)

### Community 86 - "Application Security Provider"
Cohesion: 0.18
Nodes (8): android, com, NeoChildApp, ProviderInstallListener, Application, Configuration, HiltWorkerFactory, Provider

### Community 87 - "Converters and Serializers"
Cohesion: 0.19
Nodes (7): WidgetUtils, Converters, ReminderStats, decodefromstring, encodetostring, json, typeconverter

### Community 88 - "Audit Log DAO"
Cohesion: 0.28
Nodes (3): AuditLogDao, Flow, AuditLogEntity

### Community 89 - "Consultation DAO"
Cohesion: 0.23
Nodes (4): ConsultationDao, Flow, ConsultationEntity, toEntity()

### Community 90 - "Profile DAO"
Cohesion: 0.24
Nodes (4): Flow, ProfileDao, ProfileEntity, toEntity()

### Community 91 - "Waste DAO"
Cohesion: 0.24
Nodes (3): Flow, WasteDao, WasteEntity

### Community 92 - "Backup Auto Scheduler"
Cohesion: 0.22
Nodes (4): BackupAutoScheduler, AutoBackupSettings, BackupSettingsManager, Flow

### Community 93 - "Device Registration"
Cohesion: 0.23
Nodes (5): DeviceRepositoryImpl, UserDevice, com, NeoChildFirebaseMessagingService, FirebaseMessagingService

### Community 94 - "Widget Config Activity"
Cohesion: 0.21
Nodes (10): Bundle, VaccineWidgetConfigurationActivity, WidgetConfigurationScreen(), appwidgetmanager, ComponentActivity, glanceappwidgetmanager, mainscope, preferencesglancestatedefinition (+2 more)

### Community 96 - "Clinic Stats Manager"
Cohesion: 0.20
Nodes (8): ClinicStatsManager, com, StateFlow, ViewModel, VaccineDetailEntry, VaccineDetailUiState, VaccineDetailViewModel, savedstatehandle

### Community 97 - "Backup Collect Restore"
Cohesion: 0.36
Nodes (4): BackupCollector, BackupPayloadV1, BackupRestorer, Outcome

### Community 98 - "Patient Notes DAO"
Cohesion: 0.23
Nodes (4): Flow, PatientNotesDao, PatientNotesEntity, ClinicalNoteCard()

### Community 99 - "Expense Repository"
Cohesion: 0.21
Nodes (3): Flow, Expense, ExpenseEntityMappingTest

### Community 100 - "Dashboard Cards"
Cohesion: 0.29
Nodes (11): DashboardCard(), DashboardCardSmall(), Color, Dp, ImageVector, Modifier, DashboardMainGrid(), DashboardPatientCard() (+3 more)

### Community 101 - "Waste ViewModel"
Cohesion: 0.21
Nodes (5): StateFlow, ViewModel, WasteInventoryItem, WasteUiState, WasteViewModel

### Community 102 - "Statistics Screen"
Cohesion: 0.27
Nodes (11): ImageVector, Modifier, StatisticsAccessDeniedScreen(), StatisticsContent(), StatisticsScreen(), TabIndicator(), TabItem, horizontalscroll (+3 more)

### Community 104 - "Backup Restore Docs"
Cohesion: 0.18
Nodes (12): Cloudflare Backup Worker, AppDatabase (Room/SQLCipher), BackupCollector, BackupDao, BackupRepositoryImpl, BackupRestorer, BackupSettingsScreen, BackupViewModel (+4 more)

### Community 105 - "Worker TSConfig"
Cohesion: 0.17
Nodes (11): compilerOptions, lib, module, moduleResolution, noEmit, resolveJsonModule, skipLibCheck, strict (+3 more)

### Community 106 - "Borrow Return Records"
Cohesion: 0.22
Nodes (8): BorrowReturnRecord, toDomain(), RemoteReminder, toLocal(), toRemote(), encodedefault, experimentalserializationapi, transient

### Community 108 - "Backup Models Envelope"
Cohesion: 0.29
Nodes (6): BackupDeviceInfo, BackupEnvelope, BackupMigrator, BackupSerializer, ByteArray, CharArray

### Community 109 - "Borrow Return DAO"
Cohesion: 0.25
Nodes (4): BorrowReturnDao, Flow, BorrowReturnEntity, toEntity()

### Community 110 - "Add Consultation Flow"
Cohesion: 0.20
Nodes (4): AddConsultationUiState, AddConsultationViewModel, StateFlow, ViewModel

### Community 111 - "Drawer Screenshot"
Cohesion: 0.24
Nodes (11): Audit Log Nav Item, Dashboard Background (Patient, Inventory, Waste cards), Dashboard Nav Item, Logout Button, Manage Staff Nav Item, Navigation Drawer, Online Status Indicator, Personal Reminders Nav Item (+3 more)

### Community 112 - "Manage Staff Function"
Cohesion: 0.20
Nodes (5): ref_https, corsHeaders, corsHeaders, getAccessToken(), str2ab()

### Community 113 - "CI and Getting Started"
Cohesion: 0.20
Nodes (10): Notify App Users About Release Workflow, .env.local Configuration (SUPABASE_URL / SUPABASE_PUBLISHABLE_KEY), Firebase Cloud Messaging (FCM), manage-staff Edge Function, notify-update Edge Function, patient-docs Storage Bucket, Row Level Security (RLS), UPDATE_NOTIFIER_SECRET (+2 more)

### Community 114 - "Update Install Receiver"
Cohesion: 0.27
Nodes (6): androidentrypoint, Intent, BroadcastReceiver, AppUpdateInstallReceiver, Context, Intent

### Community 115 - "Inventory Screen UI"
Cohesion: 0.38
Nodes (9): animatedvisibility, BatchRow(), capitalize(), FilterButton(), SortButton(), StockStatusBadge(), VaccineInventoryContent(), VaccineInventoryScreen() (+1 more)

### Community 117 - "Document Repository"
Cohesion: 0.22
Nodes (5): DocumentRepositoryImpl, ByteArray, FileObject, minutes, upload

### Community 118 - "Waste Repository"
Cohesion: 0.36
Nodes (3): Flow, WasteRecord, WasteRepositoryImpl

### Community 119 - "Available Slots Use Case"
Cohesion: 0.29
Nodes (3): AvailableSlot, GetAvailableSlotsUseCase, calendar

### Community 120 - "Auth Login ViewModel"
Cohesion: 0.29
Nodes (5): AuthViewModel, SessionStatus, StateFlow, ViewModel, UserInfo

### Community 121 - "Edit Reminder Screen"
Cohesion: 0.40
Nodes (8): AddEditPersonalReminderScreen(), clickableSelect(), FieldError(), com, Modifier, PatientPicker(), SectionLabel(), VaccineDropdown()

### Community 122 - "Sync Screen ViewModel"
Cohesion: 0.20
Nodes (3): StateFlow, ViewModel, SyncViewModel

### Community 123 - "Expense Migration Tests"
Cohesion: 0.22
Nodes (7): androidjunit4, ExpenseMigrationTest, frameworksqliteopenhelperfactory, instrumentationregistry, ioexception, rule, supportsqlitedatabase

### Community 124 - "Widget Due DAO"
Cohesion: 0.39
Nodes (3): Flow, WidgetDueDao, WidgetDueEntity

### Community 126 - "Inventory Filter Enums"
Cohesion: 0.22
Nodes (8): InventoryFilter, ALL, AVAILABLE, EXPIRED, HIDDEN, LOW_STOCK, NEAR_EXPIRY, OUT_OF_STOCK

### Community 127 - "Inventory Sort Enums"
Cohesion: 0.22
Nodes (8): InventorySort, ALPHABETICAL, EXPIRY, HIGHEST_STOCK, LOWEST_STOCK, MANUFACTURER, NEWEST, OLDEST

### Community 128 - "Stock History Filters"
Cohesion: 0.22
Nodes (9): StockHistoryTypeFilter, ADDED, ADJUSTMENT, ALL, BORROWED, RETURNED, REVERSAL, USED (+1 more)

### Community 129 - "Inventory ViewModel"
Cohesion: 0.22
Nodes (5): StateFlow, ViewModel, Quad, VaccineInventoryUiState, VaccineInventoryViewModel

### Community 130 - "Profile ViewModel"
Cohesion: 0.22
Nodes (4): StateFlow, ViewModel, ProfileUiState, ProfileViewModel

### Community 131 - "Dashboard Screenshot"
Cohesion: 0.36
Nodes (9): Borrowed Card, Dashboard Screen, Due Card, Inventory Card, Neo Child Clinic Branding, Patient List Card, Statistics Card, Today's Patient Card (+1 more)

### Community 132 - "Widget Update Vaccination"
Cohesion: 0.25
Nodes (4): Context, onetimeworkrequestbuilder, outofquotapolicy, workmanager

### Community 133 - "Doctor Availability Model"
Cohesion: 0.25
Nodes (7): Available, DoctorAvailabilityResult, FullDayUnavailable, NoScheduleConfigured, SlotExceptionType, FULL_DAY, SLOT

### Community 135 - "Add Batch ViewModel"
Cohesion: 0.25
Nodes (4): AddBatchUiState, AddBatchViewModel, StateFlow, ViewModel

### Community 137 - "OpenCode Graphify Config"
Cohesion: 0.25
Nodes (7): enabled, type, url, mcp, graphify, plugin, $schema

### Community 138 - "Widget Refresh Action"
Cohesion: 0.48
Nodes (5): ActionCallback, ActionParameters, Context, GlanceId, RefreshWidgetAction

### Community 139 - "App Database Core"
Cohesion: 0.29
Nodes (6): Context, database, room, RoomDatabase, supportopenhelperfactory, typeconverters

### Community 141 - "Audit Log Paging"
Cohesion: 0.43
Nodes (4): AuditLogUiState, FullAuditLogViewModel, StateFlow, ViewModel

### Community 142 - "Today Patients Card"
Cohesion: 0.33
Nodes (7): AddTodoDialog(), Modifier, Patient, TodayPatientsCard(), TodoTab, CONSULTATION, VACCINATION

### Community 143 - "Borrow Screenshot"
Cohesion: 0.38
Nodes (7): Add Borrow Floating Action Button, Vaccine Borrow/Return Workflow, Borrowed/Returned Status Tabs, Borrowed Vaccines Screen, By/From Direction Filter Toggle, Nexipox Plus Borrow Card, Vaccine Inventory Batch and Expiry Data

### Community 145 - "Backup History Types"
Cohesion: 0.33
Nodes (6): BackupHistoryType, CLOUD_BACKUP, CLOUD_RESTORE, LOCAL_EXPORT, LOCAL_IMPORT, SAFETY_BACKUP

### Community 146 - "Inventory Status Enums"
Cohesion: 0.33
Nodes (5): displayLabel(), InventoryStatus, COMPLETED, FAILED, PARTIAL

### Community 147 - "Weekly Slots Screen"
Cohesion: 0.33
Nodes (6): DaySlotSection(), ExceptionRow(), DoctorWeeklySlot, UnavailabilityTab(), WeeklySlotsTab(), WeeklyDoctorSlotsUiState

### Community 148 - "Due Vaccination Screenshot"
Cohesion: 0.53
Nodes (6): Completed Status Card, Dismissed Status Card, Due Vaccinations Screen, No Vaccinations Due Empty State, Due Filter Tabs (All/Overdue/Today/Week/Month), Search by Name or Phone Field

### Community 149 - "Patient Details Screenshot"
Cohesion: 0.47
Nodes (6): Add Record FAB, Consultation Tab, No Vaccination Records Empty State, Patient Info Card, Patient Details Screen, Vaccination Tab

### Community 150 - "Expense Sort Options"
Cohesion: 0.40
Nodes (5): ExpenseSortOption, AMOUNT_ASC, AMOUNT_DESC, DATE_ASC, DATE_DESC

### Community 151 - "Backup Architecture Docs"
Cohesion: 0.40
Nodes (5): Merge Hard-Delete Resurrection Limitation, Merge Rule: Last-Write-Wins + Insert-If-Absent, Restore Resync via Existing Sync Pipeline, SyncRepositoryImpl, Offline-First Architecture (Room/SQLCipher + Supabase Sync)

### Community 152 - "Security and Guides Docs"
Cohesion: 0.40
Nodes (5): Getting Started Guide, Neo Child Clinic - Vaccine Manager, Security Policy, Responsible Disclosure Policy, Supported Versions (0.5.x)

### Community 153 - "Inventory Screenshot"
Cohesion: 0.40
Nodes (5): Add Vaccine FAB, Filter and Search Controls, Inventory Screen, Low Stock Indicator, Vaccine Stock List

### Community 154 - "Graphify Plugin Config"
Cohesion: 0.40
Nodes (3): IMPORTANT: keep the reminder string free of backticks and $(...) constructs., ref_fs, ref_path

### Community 155 - "GitHub CI Workflows"
Cohesion: 0.50
Nodes (4): CI Firebase Configuration Stub, Android CI Workflow, CI Firebase Configuration Stub, CodeQL Advanced Workflow

### Community 156 - "Design System Typography"
Cohesion: 0.50
Nodes (3): fontfamily, textstyle, typography

### Community 157 - "JSON Metadata Utils"
Cohesion: 0.50
Nodes (3): contentornull, jsonelement, jsonprimitive

### Community 158 - "Backup History Status"
Cohesion: 0.50
Nodes (4): BackupHistoryStatus, FAILED, IN_PROGRESS, SUCCESS

### Community 159 - "Sync State Enum"
Cohesion: 0.50
Nodes (4): SyncState, ERROR, IDLE, SYNCING

### Community 160 - "Widget Receiver"
Cohesion: 0.83
Nodes (3): GlanceAppWidget, VaccineWidgetReceiver, GlanceAppWidgetReceiver

### Community 161 - "Gradle Wrapper Script"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 162 - "AGENTS Instructions"
Cohesion: 0.67
Nodes (3): Use Graphify Before Grep, Ponytail Lazy Senior Dev Mode, Root-Cause Bug Fixing

### Community 164 - "Worker R2 and Versioning"
Cohesion: 0.67
Nodes (3): neo-child-clinic-backups R2 Bucket, Backup Versioning / BackupMigrator, NCCB Backup File Format (.nccb)

### Community 165 - "Backup Crypto Docs"
Cohesion: 0.67
Nodes (3): BackupCrypto, BackupSerializer, BackupValidator

## Knowledge Gaps
- **252 isolated node(s):** `$schema`, `plugin`, `type`, `url`, `enabled` (+247 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 745 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **24 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AppDatabase` connect `Database DI Providers` to `Constants and Repos Utils`, `App Prefs and Sync Manager`, `Audit Logger and Sync Enums`, `Personal Reminder DAO`, `App Database Core`, `Vaccine DAO`, `Finance DAO`, `Clinic ID Migration`, `Due Reminder DAO`, `Backup DAO Clears`, `DB Migrations`, `Vaccination DAO`, `Unit and DAO Tests`, `Doctor Availability DAO`, `DI Modules Supabase`, `Backup DAO Batch Ops`, `Vaccine Widget Glance`, `Patient Todo DAO`, `Patient DAO`, `Sync Queue DAO`, `Borrow DAO`, `Expense DAO`, `Converters and Serializers`, `Audit Log DAO`, `Consultation DAO`, `Profile DAO`, `Waste DAO`, `Expense DAO Instrumented Tests`, `Patient Notes DAO`, `Borrow Return DAO`, `Inventory Deduction DAO`, `Widget Due DAO`?**
  _High betweenness centrality (0.096) - this node is a cross-community bridge._
- **Why does `Vaccination` connect `Clinical Vaccination Service` to `Routes and Drawer Shell`, `Constants and Repos Utils`, `Widget Update Vaccination`, `Audit Logger and Sync Enums`, `Vaccination Edit Engine`, `Today Patients Screen`, `Receipt Formatting`, `Statistics Date Utils`, `Cloud Refresh Repos`, `Finance DAO`, `Add Vaccination Flow`, `Waste Screen UI`, `Local Room Entities`, `Date Classifier`, `Inventory Expiry Utils`, `Statistics Tabs`, `Finance Calculator`, `Patient Repository`, `Statistics Overview UI`, `Reminder Repository`, `Patient Info Components`, `Patient Stats Distribution`, `Statistics Screen`?**
  _High betweenness centrality (0.047) - this node is a cross-community bridge._
- **Why does `PatientUtils` connect `Audit Logger and Sync Enums` to `Core UI Components`, `App Navigation Graph`, `Routes and Drawer Shell`, `Constants and Repos Utils`, `App Prefs and Sync Manager`, `Today Patients Screen`, `Backup History UI`, `Clinic ID Migration`, `Waste Screen UI`, `Sync Queue Repository`, `Date Classifier`, `Inventory Expiry Utils`, `Inventory Stock Ops`, `Finance Calculator`, `Reminder Repository`, `Patient Info Components`, `Patient Stats Distribution`, `Clinic Stats Manager`, `Age Milestone Tests`?**
  _High betweenness centrality (0.044) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `Vaccination` (e.g. with `toVaccination()` and `.saveVaccination()`) actually correct?**
  _`Vaccination` has 2 INFERRED edges - model-reasoned connections that need verification._
- **Are the 7 inferred relationships involving `AppBackground()` (e.g. with `LockScreen()` and `ManageStaffScreen()`) actually correct?**
  _`AppBackground()` has 7 INFERRED edges - model-reasoned connections that need verification._
- **What connects `$schema`, `plugin`, `type` to the rest of the system?**
  _252 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Core UI Components` be split into smaller, more focused modules?**
  _Cohesion score 0.04936820452541875 - nodes in this community are weakly interconnected._