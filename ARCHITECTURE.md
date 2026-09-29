# Neo Child Clinic – Architecture

Single Gradle module (`:app`), root package `com.neochildclinic`.
This document describes the package layout after the folder/package restructure. No behaviour changed; only packages, folders and references moved.

## 1. Package structure

```
com.neochildclinic
├── app/                 Application, MainActivity, navigation graphs, Routes
├── core/                Shared infrastructure (no feature knowledge unless noted below)
│   ├── cache/           MemoryCache
│   ├── common/          Constants, date helpers, PatientUtils, AgeUtils, DateClassifier, JSON helper
│   ├── designsystem/    Theme, Color, Type
│   ├── di/              Hilt modules (Cache, Database, DataSource, RemoteDataSource, Repository, Supabase)
│   ├── logger/          AuditLogger
│   ├── network/         NetworkMonitor
│   ├── notification/    FCM service, NotificationHelper, ReminderScheduler, notification workers
│   ├── preferences/     DataStore preference holders (PreferenceManager, NotificationSettingsManager)
│   ├── security/        SessionManager, AuthViewModel, biometric lock/authenticator, SecurityUtils
│   ├── sync/            SyncManagerImpl, SyncRepositoryImpl, SyncUploader, RealtimeChangeSubscriptions, SyncWorker, cloudRefresh
│   └── ui/              Shared Compose components
├── domain/              Models, repository interfaces, use cases, domain services/statistics
│   ├── model/  repository/  usecase/{doctor,patient,sync}/  service/  statistics/
│   └── TransactionRunner
├── data/                Data infrastructure shared by several features
│   ├── backup/          Backup collector/restorer/crypto/cloud API, BackupRepositoryImpl, AutoBackupWorker, scheduler, settings
│   ├── local/
│   │   ├── dao/  entity/  database/   Room: DAOs, entities, AppDatabase, Converters, TransactionRunnerImpl
│   └── remote/          PatientRemoteDataSource(+Impl) – used by more than one feature
└── feature/
    ├── <feature>/presentation/   Screens, components, ViewModels
    ├── <feature>/domain/         Feature-only pure logic (only where it exists)
    └── <feature>/data/           Repository implementations, feature data sources, workers
```

Features: `audit, auth, borrowed, dashboard, doctor, finance, inventory, patient, personalreminder, profile, reminder, search, settings, staff, statistics, sync, update, vaccination, waste, widget`.
Consultations live in `feature/patient` (the UI is part of the patient flow and `ConsultationRepositoryImpl` shares helpers with the patient repositories).

## 2. Dependency direction

```
Presentation   (feature/*/presentation, app)
     ↓
Domain         (domain/*, feature/*/domain)
     ↓
Data           (feature/*/data, data/*)
     ↓
Infrastructure (core/*, Room, Supabase, WorkManager, DataStore)
```

Rules: UI/ViewModels/use cases never touch DAOs or the Supabase SDK; repository interfaces live in `domain/repository`, implementations in the owning `feature/<x>/data` (or `data/backup`, `core/sync`); Hilt binds them in `core/di/RepositoryModule`.

## 3. Ownership and infrastructure

- **Room** – everything in `data/local` (kept together, not in `core`, because `AppDatabase` needs every DAO/entity, and entities map to domain models). Schema, queries and migrations are untouched.
- **Supabase** – client provided by `core/di/SupabaseModule`; PostgREST/Realtime/Auth calls live in repository implementations, `core/sync`, `data/backup` and `data/remote`.
- **Cache** – `core/cache/MemoryCache`; provided by `CacheModule`. Feature-specific cache policy stays with its repository.
- **Synchronization** – `core/sync` (queue upload, pull refresh, realtime subscriptions, `SyncWorker`).
- **Security** – `core/security` (session, biometric lock, crypto helpers); backup encryption stays in `data/backup`.
- **Widget** – `feature/widget/presentation` (Glance widget, receiver, config activity) and `feature/widget/data` (`WidgetLocalDataSource`, `WidgetWorker`, `WidgetUtils`). The widget reads through `WidgetLocalDataSource`, never AppDatabase/DAO/Supabase directly.
- **DI** – Hilt modules in `core/di`; scopes unchanged.

## 4. Known existing dependencies (documented, intentionally not changed here)

1. `domain/repository/*`, `domain/service/*`, `domain/statistics/ClinicStatsManager` and `MergePatientsUseCase` import Room entities from `data/local/entity`; `BackupRepository` imports `android.net.Uri`; some domain classes use `core/logger/AuditLogger`.
2. `core/security/AuthViewModel` depends on `feature/profile/data/ProfileRepositoryImpl` (concrete class).
3. `feature/widget/data/WidgetWorker` depends on `PatientRepositoryImpl` and `ReminderRepositoryImpl`.
4. `feature/reminder/data` and `feature/vaccination/data` call `feature/widget/data/WidgetUtils` to trigger a widget refresh (single sanctioned cross-feature entry point).
5. `feature/personalreminder` uses `FilterTabRow` from `feature/reminder/presentation`; `feature/update` uses `SettingsDetailTopBar` from `feature/settings/presentation`.
6. `feature/inventory/data` uses `data/remote/PatientRemoteDataSource`.
7. `core/sync` and `core/logger` depend on `data/local` (Room).
8. `app/MainActivity` uses the Supabase Auth/Postgrest client directly (deep-link handling); `feature/widget/presentation` reads `WidgetDueEntity` (an entity, not a DAO) returned by `WidgetLocalDataSource`.

Each should be revisited in a dedicated refactor (interfaces instead of concrete classes, shared UI moved to `core/ui`).

## 5. Rules for adding a feature

1. Create `feature/<name>/presentation` for screens, components and ViewModels (flat is fine).
2. Repository interface + models → `domain/repository`, `domain/model`; implementation → `feature/<name>/data`; bind it in `core/di/RepositoryModule`.
3. Add `feature/<name>/domain` only for pure logic used by that feature alone.
4. Shared Room entities/DAOs → `data/local/{entity,dao}` and register them in `AppDatabase`.
5. Do not import another feature's `presentation`/`data` internals. Extract shared UI to `core/ui`, shared logic to `domain`/`core/common`.
6. ViewModels and use cases depend on domain interfaces only – never DAOs or the Supabase SDK.
7. Mirror the production package in `src/test` / `src/androidTest`.
8. Use lowercase, singular-style package names; avoid `utils`, `helpers`, `manager` packages.
