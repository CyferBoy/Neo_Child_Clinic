# Where Things Belong — Developer Placement & Navigation Guide

This document is the definitive guide for engineers contributing to Neo Child Clinic. It establishes clear rules and practical examples for where every type of code, asset, configuration, and test belongs.

---

## 1. Quick Directory Reference

```text
Neo_Child_Clinic/
│
├── app/                                 # Android application module
│   ├── src/main/java/com/neochildclinic/
│   │   ├── app/                         # App entry, host Activity, NavHost, sealed Routes
│   │   ├── core/                        # Shared infrastructure (di, common, ui, sync, cache, security)
│   │   ├── data/                        # Local Room DB, DAOs, entities, remote sources, backup engine
│   │   ├── domain/model/                # Pure Kotlin domain models (no Android or DB imports)
│   │   └── feature/<name>/              # Self-contained business features (patient, inventory, etc.)
│   ├── src/test/java/                   # Unit test suite (JVM-based, fast)
│   ├── src/androidTest/java/            # Android instrumentation tests (Room migrations, DAOs)
│   ├── schemas/                         # Room database version schema JSONs (v33)
│   └── build.gradle.kts                 # Application Gradle build configuration
│
├── supabase/                            # Supabase cloud backend
│   ├── migrations/                      # Numbered, lexically ordered SQL schema & RLS migrations
│   ├── functions/                       # Deno Edge Functions (manage-staff, notify-update)
│   ├── tests/                           # SQL regression tests
│   └── config.toml                      # Supabase local environment configuration
│
├── cloudflare/                          # Cloudflare edge infrastructure
│   └── backup-worker/                   # TypeScript Cloudflare Worker for R2 cloud backups
│
├── design/                              # Design assets and design system
│   ├── design-system/                   # Design system documentation & contrast checkers
│   └── assets/                          # Application screenshots and branding assets
│
├── docs/                                # Project documentation
│   ├── getting-started/                 # Onboarding, environment setup, and quickstart guides
│   ├── architecture/                    # System architecture, sync models, placement guides
│   ├── features/                        # Business feature specifications and matrices
│   ├── database/                        # Database schema, relationships, and indexing documentation
│   ├── operations/                      # Backup, restore, release, and deployment runbooks
│   ├── quality/                         # Security audits, bug audits, and compliance registers
│   └── Knowledge_Base/                  # Linked Obsidian vault with 66 interconnected notes
│
├── scripts/                             # Repository tooling & verification scripts
│   ├── validate_docs.py                 # Strict documentation and citation validator
│   └── test_validate_docs.py            # Regression test suite for documentation validation
│
├── .github/                             # GitHub Actions CI/CD workflows
├── README.md                            # Primary project overview and navigation guide
├── ARCHITECTURE.md                      # High-level architecture summary
├── CONTRIBUTING.md                      # Developer contribution workflow
└── SECURITY.md                          # Vulnerability reporting policy
```

---

## 2. Practical Examples: Where Does My Code Go?

### 1. Where should a new patient screen go?
- **UI Screen Composable**: `app/src/main/java/com/neochildclinic/feature/patient/presentation/<NewScreen>.kt`
- **Screen ViewModel**: `app/src/main/java/com/neochildclinic/feature/patient/presentation/<NewScreen>ViewModel.kt`
- **Route Definition**: Add a new entry to `app/src/main/java/com/neochildclinic/app/Routes.kt`
- **Navigation Graph**: Wire the composable into `app/src/main/java/com/neochildclinic/app/NavigationPatientGraph.kt`

### 2. Where should a new vaccination business rule go?
- **Domain Service / Engine**: `app/src/main/java/com/neochildclinic/feature/vaccination/domain/`
  - For complex clinical workflows: extend `ClinicalVaccinationService.kt`
  - For editing and reconciling dose changes: extend `VaccinationEditEngine.kt` or `EditReconciler.kt`
- **Pure Rule / Calculator**: Create a pure Kotlin class or extension under `feature/vaccination/domain/` with zero Android UI dependencies.
- **Unit Test**: `app/src/test/java/com/neochildclinic/feature/vaccination/domain/<RuleName>Test.kt`

### 3. Where should a new Room DAO go?
- **Interface**: `app/src/main/java/com/neochildclinic/data/local/dao/<EntityName>Dao.kt`
- **Entity**: `app/src/main/java/com/neochildclinic/data/local/entity/<EntityName>Entity.kt`
- **Registration**: Register the new DAO and Entity in `app/src/main/java/com/neochildclinic/data/local/database/AppDatabase.kt`
- **Hilt DI**: Expose the DAO provider in `app/src/main/java/com/neochildclinic/core/di/DatabaseModule.kt`
- **Database Versioning**: If modifying existing tables or adding tables, bump the version in `AppDatabase.kt` and create a migration script.

### 4. Where should a Supabase query or remote call go?
- **Remote Data Source**: `app/src/main/java/com/neochildclinic/data/remote/<Domain>RemoteDataSource.kt`
- **Repository Implementation**: If syncing, add table handling into `app/src/main/java/com/neochildclinic/core/sync/SyncUploader.kt` and `SyncRepositoryImpl.kt`.
- **Never from UI**: UI Composables and ViewModels must NEVER call Supabase REST or PostgREST directly.

### 5. Where should a new reusable Compose component go?
- **Generic Component** (used across 2+ features): `app/src/main/java/com/neochildclinic/core/ui/<ComponentName>.kt`
- **Design Token** (colors, typography, shapes, spacing): `app/src/main/java/com/neochildclinic/core/designsystem/`
- **Feature-Specific Component**: `app/src/main/java/com/neochildclinic/feature/<feature_name>/presentation/`

### 6. Where should a new database migration go?
- **Cloud (PostgreSQL / Supabase)**: `supabase/migrations/<YYYYMMDDHHMMSS>_<description>.sql`
  - Must be wrapped in `BEGIN; ... COMMIT;` when transactional DDL is used.
  - Must include Row Level Security (RLS) policies.
- **Local (Room / SQLite)**: In `AppDatabase.kt` under `MIGRATION_<OLD>_<NEW>`, and export schema JSON to `app/schemas/`.

### 7. Where should a backup feature change go?
- **Local Serializer / Restorer**: `app/src/main/java/com/neochildclinic/data/backup/` (`BackupRestorer.kt`, `BackupCollector.kt`, `BackupCrypto.kt`)
- **Cloud Worker**: `cloudflare/backup-worker/src/index.ts`
- **Worker Tests**: `cloudflare/backup-worker/test/backup-worker.spec.ts`

### 8. Where should a new test go?
- **ViewModel / Business Logic / UseCase Unit Test**:
  `app/src/test/java/com/neochildclinic/feature/<feature_name>/...`
- **Core Utility / Sync Test**:
  `app/src/test/java/com/neochildclinic/core/<subpackage>/...`
- **Room Migration / Live SQLite Test**:
  `app/src/androidTest/java/com/neochildclinic/data/local/database/...`
- **Cloudflare Worker Integration Test**:
  `cloudflare/backup-worker/test/...`
- **Supabase SQL Test**:
  `supabase/tests/...`

---

## 3. Core Architectural Boundaries & Rules

1. **Unidirectional Data Flow**: ViewModels expose `StateFlow<UiState>`. Composables emit events to ViewModels.
2. **Domain Purity**: Code in `domain/` must have no imports from `android.*`, `androidx.compose.*`, or `androidx.room.*`.
3. **Database Transparency**: Repositories abstract all database operations. Screens read Room `Flow`s through repository interfaces.
4. **Offline First**: All user mutations write to Room first and enqueue an outbox row in `sync_queue` atomically.
5. **No Shortcuts Without Comments**: If cutting an intentional corner with a known ceiling, mark it with `// ponytail: <ceiling> -> <upgrade path>`.
