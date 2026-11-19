# Neo Child Clinic — Architecture Overview

Single Gradle module (`:app`), root package `com.neochildclinic`.

For in-depth architectural guides and placement policies, see:
- 🧭 **[Where Things Belong Guide](docs/architecture/where-things-belong.md)**
- 📋 **[Repository Inventory](docs/architecture/repository-inventory.md)**
- 🔍 **[Architecture Assessment](docs/architecture/architecture-assessment.md)**
- 🔄 **[Offline-First Synchronization Architecture](docs/architecture/offline-first-sync.md)**
- ⚡ **[In-Memory Cache Architecture](docs/architecture/cache-architecture.md)**
- 📂 **[Folder Migration Plan](docs/architecture/folder-migration-plan.md)**

---

## 1. Package Structure

```text
com.neochildclinic
├── app/                         Application entry point, navigation graphs, sealed Routes
│   ├── notification/            App-level notification wiring
│   ├── sync/                    App-level sync glue (SyncReconciler)
│   └── work/                    WorkManager workers (SyncWorker, DailySummaryWorker)
├── core/                        Shared cross-cutting infrastructure
│   ├── cache/                   In-memory LRU cache (`MemoryCache`, `CacheRegistry`)
│   ├── common/                  Shared pure utilities (DateUtils, AgeUtils, DateClassifier, CurrencyUtils)
│   ├── database/                TransactionRunner abstraction
│   ├── designsystem/            Material 3 theme, Color, Typography, Shape, Spacing
│   ├── logger/                  Audit logger
│   ├── network/                 Network connectivity monitoring
│   ├── notification/            Notification infrastructure and reminder workers
│   ├── preferences/             DataStore/preferences managers
│   ├── security/                Session, biometric lock and credential helpers
│   ├── sync/                    Low-level sync helpers (SingleFlight, CloudRefresh, SyncEnqueuer)
│   └── ui/                      Reusable UI design components (Buttons, Dialogs, States)
├── di/                          Hilt modules (DatabaseModule, SupabaseModule, RepositoryModule,
│                                RemoteDataSourceModule)
├── domain/
│   └── model/                   Pure Kotlin business models (35 domain models)
├── data/                        Shared persistence and cloud integration
│   ├── backup/                  Encrypted `.nccb` backup and Cloudflare R2 client
│   ├── local/                   Room database (schema 35, Room library 2.8.5), 21 DAOs,
│   │                            25 SQLite entities (dao/, entity/, database/)
│   └── remote/                  Shared remote data sources (Supabase REST)
└── feature/                     Self-contained business modules
    ├── audit/                   Audit logs and patient history
    ├── auth/                    Staff login and device binding (presentation/AuthViewModel)
    ├── borrowed/                Inter-clinic vaccine borrowing and batch returns
    ├── consultation/            Clinical doctor consultations and prescriptions
    ├── dashboard/               Daily schedule, patient slots, and clinic tiles
    ├── doctor/                  Doctor weekly availability and exception slots
    ├── finance/                 Financial ledger, receipt numbers, and clinic expenses
    ├── inventory/               Vials, batch tracking, expiry monitoring, stock history
    ├── patient/                 Patient registry, documents, medical history
    ├── personalreminder/        Custom patient alerts and follow-ups
    ├── profile/                 Clinic profile and physician credentials
    ├── reminder/                Due and overdue vaccination tracking
    ├── settings/                Application settings, biometric configuration, backups
    ├── staff/                   Staff administration and role management
    ├── statistics/              Clinic analytics, uptake trends, PDF report export
    ├── sync/                    Sync status dashboard; data/ owns SyncUploader and
    │                            SyncRepositoryImpl; domain/ owns repository contracts
    ├── update/                  In-app APK updates via GitHub Releases
    ├── vaccination/             Clinical vaccination recording and multi-table transactions
    ├── waste/                   Vial spoilage, breakage, and waste reconciliation
    └── widget/                  Android home screen widget
```

Each feature owns its repository contracts under `domain/repository/` and implementations under `data/`. Pure feature domain logic lives under `domain/`.

---

## 2. Dependency Direction

```text
feature presentation / app
        ↓
feature domain + shared domain models
        ↓
feature data / shared data
        ↓
core infrastructure + Room + Supabase + WorkManager
```

The architecture strictly enforces:
- **No Presentation -> DAO / Supabase direct dependencies**: ViewModels access data solely through repository interfaces.
- **No Cross-Feature Direct Coupling**: Features interact via Compose Navigation routes with primitive arguments.
- **Domain Independence**: Code in `domain/` contains zero Android, Compose, or Room dependencies.

---

## 3. Important Ownership & Boundary Decisions

- **Room Database**: `data/local` owns DAOs, entities, converters, and `AppDatabase`. `di/DatabaseModule` provides Hilt bindings.
- **Repositories**: Feature-specific repository contracts are colocated with the feature (`feature/<name>/domain/repository/`); implementations reside in `feature/<name>/data/`.
- **Offline Sync Engine**: `SyncWorker` lives in `app/work/`, `SyncUploader` and `SyncRepositoryImpl` in `feature/sync/data/`, low-level helpers (`SingleFlight`, `CloudRefresh`, `SyncEnqueuer`) in `core/sync/`, and the manual sync UI in `feature/sync/presentation/`.
- **Global Auth & Session**: `AuthViewModel` resides in `feature/auth/presentation/`; app-wide session state and biometric helpers live in `core/security/`.
- **Transactional Integrity**: All multi-table mutations execute within atomic Room transactions via `TransactionRunner`.
