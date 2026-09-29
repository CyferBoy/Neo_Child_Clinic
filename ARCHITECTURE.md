# Neo Child Clinic – Architecture

Single Gradle module (`:app`), root package `com.neochildclinic`.
This version reflects the folder/package restructure performed on the current codebase. The migration changes ownership and references only; database schema and application behaviour were not intentionally changed.

## Package structure

```text
com.neochildclinic
├── app/                         Application entry point and navigation
├── core/                        Shared infrastructure
│   ├── cache/                   In-memory cache
│   ├── common/                  Shared pure utilities/constants
│   ├── database/                TransactionRunner abstraction
│   ├── designsystem/            Theme and design tokens
│   ├── di/                      Hilt modules
│   ├── logger/                  Audit logger
│   ├── network/                 Network monitoring
│   ├── notification/            Notification infrastructure/workers
│   ├── preferences/             DataStore/preferences
│   ├── security/                Authentication/session/biometric infrastructure
│   ├── sync/                    Sync implementation/infrastructure
│   └── ui/                      Reusable UI components
├── domain/
│   └── model/                   Shared business models
├── data/                        Shared persistence/integration infrastructure
│   ├── backup/                  Backup/restore implementation
│   ├── local/                   Room database, DAOs and entities
│   └── remote/                  Shared remote data sources
└── feature/
    ├── audit/
    ├── auth/
    ├── borrowed/
    ├── consultation/
    ├── dashboard/
    ├── doctor/
    ├── finance/
    ├── inventory/
    ├── patient/
    ├── personalreminder/
    ├── profile/
    ├── reminder/
    ├── search/
    ├── settings/
    ├── staff/
    ├── statistics/
    ├── sync/
    ├── update/
    ├── vaccination/
    ├── waste/
    └── widget/
```

Each feature now owns its repository contracts under `domain/repository` when those contracts are feature-specific, while implementations remain under `feature/<name>/data`. Pure feature logic lives under `feature/<name>/domain`.

## Dependency direction

```text
feature presentation / app
        ↓
feature domain + shared domain models
        ↓
feature data / shared data
        ↓
core infrastructure + Room + Supabase + WorkManager
```

The restructure does not introduce new feature-to-feature implementation dependencies.

## Important ownership decisions

- **Room:** `data/local` owns DAOs, entities, database and converters. The database module remains in `core/di` because it wires all Room dependencies.
- **Repositories:** feature-specific repository contracts are colocated with the feature; implementations remain in the feature's `data` package. Hilt bindings remain in `core/di`.
- **Statistics:** all shared statistics calculators/managers now live in `feature/statistics/domain`.
- **Vaccination domain:** vaccination edit/reconciliation/clinical services now live in `feature/vaccination/domain`.
- **Consultation domain:** consultation edit logic lives in `feature/consultation/domain`.
- **Inventory domain:** inventory-specific pure helpers live in `feature/inventory/domain`.
- **Patient domain:** patient search/merge use cases live in `feature/patient/domain`; DAO-bound `PatientIdGenerator` lives in `feature/patient/data`.
- **Sync:** sync contracts/use cases live in `feature/sync/domain`; sync implementation remains in `core/sync`.
- **TransactionRunner:** abstraction is now `core/database`; Room implementation remains in `data/local/database`.
- **Widget:** widget remains isolated under `feature/widget`; it does not own the application database.

## Persistence-boundary status

The feature-domain boundary has now been tightened so feature domain contracts and business logic do not import Room entities, DAOs, Supabase, or Android framework types. Persistence DTOs are converted to shared domain models at repository boundaries. This includes reminders, finance transactions, audit logs, patient notes, personal reminders, inventory/vaccine records, inventory deductions/transactions, vaccination items/cards, and backup history.

Intentional infrastructure exceptions remain below the domain boundary:

- `core/sync` and backup implementation operate directly on Room for bulk synchronization/backup;
- feature `data` packages may use Room/Supabase and perform entity/domain mapping;
- `app/MainActivity` handles the Supabase deep-link/auth integration required at the application entry point.

The backup repository contract represents SAF document locations as strings so the domain contract does not depend on `android.net.Uri`; the Android `Uri` conversion occurs in presentation/data edges.

## Rules for future work

1. Put screens/ViewModels in `feature/<name>/presentation`.
2. Put feature-specific pure business logic in `feature/<name>/domain`.
3. Put repository contracts beside the owning feature under `domain/repository`.
4. Put repository/data-source implementations in `feature/<name>/data`.
5. Keep Room-specific code under `data/local`.
6. Keep shared infrastructure under `core`; do not use `core` as a feature dump.
7. Do not add direct presentation → DAO/Supabase dependencies.
8. Do not add feature → feature implementation dependencies; extract genuinely shared pieces to `core` or a domain model/contract.
9. Mirror production packages in unit/instrumentation tests.
10. Use lowercase package names and avoid generic `utils`/`helpers` packages.
