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
├── core/                        Shared cross-cutting infrastructure
│   ├── cache/                   In-memory LRU cache (`MemoryCache`, `CacheRegistry`)
│   ├── common/                  Shared pure utilities (DateUtils, AgeUtils, PatientUtils)
│   ├── database/                TransactionRunner abstraction
│   ├── designsystem/            Material 3 theme, Color, Typography, Shape, Spacing
│   ├── di/                      Hilt modules (DatabaseModule, SupabaseModule, RepositoryModule)
│   ├── logger/                  Audit logger
│   ├── network/                 Network connectivity monitoring
│   ├── notification/            Notification infrastructure, WorkManager workers
│   ├── preferences/             DataStore/preferences managers
│   ├── security/                Authentication, session, and biometric lock infrastructure
│   ├── sync/                    Offline-first sync engine (SyncUploader, SingleFlight)
│   └── ui/                      Reusable UI design components (Buttons, Dialogs, States)
├── domain/
│   └── model/                   Pure Kotlin business models (33 domain models)
├── data/                        Shared persistence and cloud integration
│   ├── backup/                  Encrypted `.nccb` backup and Cloudflare R2 client
│   ├── local/                   Room database (v33), 21 DAOs, and 21 SQLite entities
│   └── remote/                  Shared remote data sources (Supabase REST)
└── feature/                     Self-contained business modules
    ├── audit/                   Audit logs and patient history
    ├── auth/                    Staff login and device binding
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
    ├── search/                  Unified clinic and patient search
    ├── settings/                Application settings, biometric configuration, backups
    ├── staff/                   Staff administration and role management
    ├── statistics/              Clinic analytics, uptake trends, PDF report export
    ├── sync/                    Sync status dashboard and conflict triage
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

- **Room Database**: `data/local` owns DAOs, entities, converters, and `AppDatabase`. `core/di/DatabaseModule` provides Hilt bindings.
- **Repositories**: Feature-specific repository contracts are colocated with the feature (`feature/<name>/domain/repository/`); implementations reside in `feature/<name>/data/`.
- **Offline Sync Engine**: Low-level background sync (`SyncWorker`, `SyncUploader`, `SyncRepositoryImpl`) lives in `core/sync/`, while the manual sync user interface resides in `feature/sync/`.
- **Global Auth & Session**: `AuthViewModel` resides in `core/security/` as it orchestrates app-wide session states, biometric lock screens, and logout queue safety across all screens.
- **Transactional Integrity**: All multi-table mutations execute within atomic Room transactions via `TransactionRunner`.
