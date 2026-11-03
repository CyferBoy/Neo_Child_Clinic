# Neo Child Clinic — Complete Repository Inventory

This document represents an exhaustive, ground-truth inventory of every major directory, module, package, and configuration file across the Neo Child Clinic codebase as audited on November 2, 2026.

---

## 1. Executive Summary & Inventory Scope

The repository contains:
- **Android Application**: 1 root module (`:app`), target SDK 35, compile SDK 35, Kotlin 2.0.21, Jetpack Compose with Material 3, Hilt DI, Room DB (v33), Offline-first sync with Supabase and Cloudflare R2 backup.
- **Backend & Cloud Infrastructure**: Supabase migrations (23 migration scripts + catalog CSV), Supabase Edge Functions (`manage-staff`, `notify-update`), Supabase live tests & configs, and Cloudflare Worker (`cloudflare/backup-worker`) with Vitest test harness.
- **Design System & Assets**: Material 3 theme, custom typography/color tokens, contrast validation scripts (`contrast_check.py`), and documentation in `design-system/`.
- **Knowledge Base & Documentation**: Obsidian Vault with 66 interconnected notes in `docs/Knowledge_Base/`, 30+ historical audit notes in `docs/bug-audit/`, and scattered root architectural documents.
- **Scripts & Developer Tooling**: Python documentation validation harness (`validate_docs.py`, `test_validate_docs.py`), graph analysis scripts, and agent skill sets.

---

## 2. Root-Level Configuration & Entry Files

| Path | Responsibility | Correct Location? | Duplication / Redundancy | Recommendation | Dependencies & References | Move / Change Risk |
|---|---|---|---|---|---|---|
| `build.gradle.kts` | Root Gradle build configuration | Yes | None | Retain | AGP, Kotlin, Hilt plugins | High (build failure if moved) |
| `settings.gradle.kts` | Gradle project & repository settings | Yes | None | Retain | Defines `:app` subproject | High (build failure if moved) |
| `gradle.properties` | JVM memory & Gradle daemon flags | Yes | None | Retain | Gradle daemon, Kotlin daemon | High (build failure if moved) |
| `local.properties` | Local Android SDK path | Yes (gitignored) | None | Retain | Android SDK location | High (local build fails) |
| `gradle/libs.versions.toml` | Version catalog for dependencies | Yes | None | Retain | Referenced across all build files | High (catalog resolution) |
| `gradle/wrapper/*` | Gradle 8.13 wrapper binaries & props | Yes | None | Retain | CI/CD, `./gradlew` | High (wrapper execution) |
| `README.md` | Primary developer onboarding document | Yes | Partially overlaps with `docs/GETTING_STARTED.md` | Retain & overhaul into comprehensive master guide | GitHub homepage, developers | Low (prose only) |
| `ARCHITECTURE.md` | Root architectural summary | Yes | Overlaps with `CACHE_ARCHITECTURE.md` and KB notes | Retain as high-level entry; link to `docs/architecture/` | Developers, onboarding | Low |
| `CACHE_ARCHITECTURE.md` | Local memory cache architecture specs | No (belongs in architecture docs) | Belongs alongside architectural docs | Move to `docs/architecture/cache-architecture.md` (keep root reference) | References `core/cache/MemoryCache.kt` | Low |
| `SECURITY.md` | Security vulnerability reporting policy | Yes | None | Retain | GitHub Security tab | Low |
| `CONTRIBUTING.md` | Developer contribution & coding guidelines | Missing at root | None | Create at root | New contributors, open-source standards | Low |
| `AGENTS.md` | AI agent operational protocol (Ponytail ultra) | Yes | None | Retain | AI assistants, docs validation rules | Low |
| `LICENSE` | Apache 2.0 license file | Yes | None | Retain | Open source licensing | Low |
| `.gitignore` | Git ignore rules for Android, Node, Gradle | Yes | Needs audit for clutter | Retain & harden | Git repository cleanliness | Low |
| `.env.local` | Local environment variables | Yes (gitignored) | None | Retain | Supabase and Worker keys | Medium (local runtime) |
| `tui.heapsnapshot`, `server.heapsnapshot` | Node/Wrangler V8 memory dumps | No (transient clutter) | None | Delete safely (already gitignored) | None | None |
| `node_modules/` (root) | Leftover Node modules from root npm runs | No (root npm not configured) | Duplicates worker node_modules | Clean/ignore (belongs in `cloudflare/backup-worker/`) | None at root | Low |
| `neochildclinic-release`, `*.jks` | Signing keys (gitignored) | Yes (local only) | None | Retain locally (never commit) | Release APK signing | High if deleted |

---

## 3. Android Application Module (`app/`)

### 3.1 Build & Manifest Configuration
| Path | Responsibility | Correct Location? | Duplication / Redundancy | Recommendation | Dependencies & References | Move / Change Risk |
|---|---|---|---|---|---|---|
| `app/build.gradle.kts` | Module build dependencies, SDKs, build types | Yes | None | Retain | KSP, Room, Supabase, Compose, Hilt | High |
| `app/proguard-rules.pro` | R8 / ProGuard obfuscation & keep rules | Yes | None | Retain | Release builds, serialization | High |
| `app/google-services.json` | Firebase configuration for push messaging | Yes (gitignored) | None | Retain | Firebase Messaging, Google Services | High |
| `app/schemas/` | Room database exported schema JSONs (v33) | Yes | None | Retain | MigrationTestHelper, schema tests | High (migration tests fail) |
| `app/src/main/AndroidManifest.xml` | App component declarations, permissions, schemes | Yes | None | Retain | Android OS, Activities, Services, Receivers | High |

### 3.2 Application & Navigation (`app/src/main/java/com/neochildclinic/app/`)
| Path | Responsibility | Correct Location? | Duplication / Redundancy | Recommendation | Dependencies & References | Move / Change Risk |
|---|---|---|---|---|---|---|
| `app/NeoChildApp.kt` | Application class, Hilt entry, WorkManager init | Yes | None | Retain | Application context, WorkManager | High |
| `app/MainActivity.kt` | Single-activity host, intent filter, biometric lock | Yes | None | Retain | Compose setContent, deep links | High |
| `app/Routes.kt` | Sealed navigation routes and argument keys | Yes | None | Retain | All screens and navigation graphs | Medium |
| `app/Navigation.kt` | Root NavHost, bottom navigation, account isolation | Yes | None | Retain | Routes, feature screens | Medium |
| `app/Navigation*Graph.kt` | Modular NavGraph builders for features | Yes | None | Retain | Feature screens, navigation | Medium |

### 3.3 Core Packages (`app/src/main/java/com/neochildclinic/core/`)
| Package | Responsibility | Correct Location? | Duplication / Redundancy | Recommendation | Dependencies & References | Move / Change Risk |
|---|---|---|---|---|---|---|
| `core/cache/` | In-memory LRU cache (`MemoryCache.kt`, `CacheRegistry.kt`) | Yes | None | Retain | Repository layers, memory clearing | High |
| `core/common/` | Shared utilities (DateUtils, AgeUtils, PatientUtils, CurrencyUtils) | Yes | None | Retain | Domain models, UI formatters | High |
| `core/database/` | `TransactionRunner` abstraction | Yes | None | Retain | Repository transactions | High |
| `core/designsystem/` | Theme, Color, Type, Shape, Spacing, Elevation | Yes | None | Retain | All Compose screens and components | High |
| `core/di/` | Hilt modules (DatabaseModule, SupabaseModule, RepositoryModule) | Yes | None | Retain | Entire dependency injection tree | High |
| `core/logger/` | Audit logging helper (`AuditLogger.kt`) | Yes | None | Retain | Repositories, auth, audit log DAO | Medium |
| `core/network/` | Connectivity observation (`NetworkMonitor.kt`) | Yes | None | Retain | SyncManager, offline banners | Medium |
| `core/notification/` | Push & local notifications, WorkManager workers | Yes | None | Retain | Firebase, ReminderScheduler | Medium |
| `core/preferences/` | SharedPreferences / DataStore managers | Yes | None | Retain | Settings, biometric preferences | Medium |
| `core/security/` | SessionManager, AuthViewModel, BiometricLockManager | Yes | Core security and session handling | Retain | MainActivity, Navigation, Security | High |
| `core/sync/` | SyncManager, SyncUploader, SyncReconciler, SingleFlight | Yes | Offline-first sync engine | Retain | Room database, Supabase REST | High |
| `core/ui/` | Reusable Compose UI components (Buttons, Dialogs, States, etc.) | Yes | None | Retain | All feature presentation screens | High |

### 3.4 Data Layer (`app/src/main/java/com/neochildclinic/data/`)
| Package | Responsibility | Correct Location? | Duplication / Redundancy | Recommendation | Dependencies & References | Move / Change Risk |
|---|---|---|---|---|---|---|
| `data/local/database/` | `AppDatabase.kt`, Room TypeConverters, TransactionRunnerImpl | Yes | None | Retain | Room 33, all DAOs and Entities | High |
| `data/local/dao/` | 21 Room DAOs (PatientDao, VaccineDao, VaccinationDao, etc.) | Yes | None | Retain | Repositories, sync engine, Room | High |
| `data/local/entity/` | 21 Room Entity definitions | Yes | None | Retain | Room schema 33, DB migrations | Critical (Room schema) |
| `data/remote/` | Supabase remote data sources (PatientRemoteDataSource) | Yes | None | Retain | Repositories, Supabase client | High |
| `data/backup/` | Local & Cloud backup engines, encryption, serialization | Yes | None | Retain | Cloudflare R2 worker, Room DB | High |

### 3.5 Domain Layer (`app/src/main/java/com/neochildclinic/domain/`)
| Package | Responsibility | Correct Location? | Duplication / Redundancy | Recommendation | Dependencies & References | Move / Change Risk |
|---|---|---|---|---|---|---|
| `domain/model/` | 33 pure Kotlin domain models (Patient, Vaccine, Vaccination, etc.) | Yes | None | Retain | Used across data, domain, and UI | High |

### 3.6 Feature Packages (`app/src/main/java/com/neochildclinic/feature/`)
All 20 features adhere to Clean Architecture packaging with encapsulated `data/`, `domain/`, and `presentation/` layers:
- `feature/audit/`: Audit logs viewing and patient history.
- `feature/auth/`: Login screen and device registration.
- `feature/borrowed/`: Inter-clinic vaccine borrowing and returns.
- `feature/consultation/`: Clinical doctor consultations.
- `feature/dashboard/`: Daily clinic summary and today's appointments.
- `feature/doctor/`: Doctor availability schedules and slots.
- `feature/finance/`: Clinic expenses and financial ledger.
- `feature/inventory/`: Stock management, batches, and transactions.
- `feature/patient/`: Patient records, document attachments, and ID generation.
- `feature/personalreminder/`: Custom non-vaccine patient reminders.
- `feature/profile/`: Clinic profile and doctor information.
- `feature/reminder/`: Due and overdue vaccination reminders.
- `feature/search/`: Unified patient and record search.
- `feature/settings/`: App configuration, security settings, backup/restore.
- `feature/staff/`: Staff administration and role management.
- `feature/statistics/`: Clinic metrics, analytics, and PDF export.
- `feature/sync/`: Sync dashboard and manual conflict resolution.
- `feature/update/`: In-app updates via GitHub/custom releases.
- `feature/vaccination/`: Core vaccination administration and multi-table recording.
- `feature/waste/`: Vaccine wastage logging and vial reconciliation.
- `feature/widget/`: Home screen widget and Glance-like Glance/RemoteViews receiver.

---

## 4. Backend Infrastructure (`supabase/` & `cloudflare/`)

### 4.1 Supabase (`supabase/`)
| Path | Responsibility | Correct Location? | Duplication / Redundancy | Recommendation | Dependencies & References | Move / Change Risk |
|---|---|---|---|---|---|---|
| `supabase/config.toml` | Supabase local development & auth config | Yes | None | Retain at root `supabase/` | Supabase CLI, DB reset | High (CLI requires root) |
| `supabase/migrations/*.sql` | 23 tracked SQL schema, trigger, & RLS migrations | Yes | None | Retain at root `supabase/` | Supabase CLI, GitHub actions | Critical (migration sequence) |
| `supabase/functions/` | Deno Edge Functions (`manage-staff`, `notify-update`) | Yes | None | Retain at root `supabase/` | Supabase Functions deploy | High |
| `supabase/tests/*.sql` | RLS role tests, stock trigger arithmetic tests | Yes | None | Retain at root `supabase/` | SQL regression tests, CI | High |
| `supabase/verify_security.sql` | Production security validation script | Yes | None | Retain at root `supabase/` | Security audits | Medium |

### 4.2 Cloudflare Backup Worker (`cloudflare/`)
| Path | Responsibility | Correct Location? | Duplication / Redundancy | Recommendation | Dependencies & References | Move / Change Risk |
|---|---|---|---|---|---|---|
| `cloudflare/backup-worker/wrangler.toml` | Wrangler deployment config for R2 worker | Yes | None | Retain in `cloudflare/` | Cloudflare Workers, Wrangler | High |
| `cloudflare/backup-worker/src/*.ts` | TypeScript worker code with JWT auth & R2 upload | Yes | None | Retain in `cloudflare/` | Cloudflare runtime | High |
| `cloudflare/backup-worker/test/*.ts` | Vitest integration and unit tests | Yes | None | Retain in `cloudflare/` | `.github/workflows/worker-ci.yml` | High |
| `cloudflare/backup-worker/package.json` | Dependencies for worker and testing harness | Yes | None | Retain in `cloudflare/` | npm ci, Vitest, Wrangler | High |

---

## 5. Documentation (`docs/`)

| Path | Responsibility | Current Status | Recommendation | Target Category |
|---|---|---|---|---|
| `docs/GETTING_STARTED.md` | Developer onboarding and setup | Scattered at docs root | Move | `docs/getting-started/onboarding.md` |
| `docs/SYNC.md` | Offline sync protocol and queue architecture | Scattered at docs root | Move | `docs/architecture/offline-first-sync.md` |
| `docs/BACKUP_RESTORE.md` | Backup & restore protocols (Cloud & Local) | Scattered at docs root | Move | `docs/operations/backup-restore.md` |
| `docs/AUDIT_REPORT_2026-10-16.md` | Historical codebase audit report | Scattered at docs root | Move | `docs/quality/audit-report-2026-10-16.md` |
| `docs/SECURITY_AUDIT_2026-10-13.md` | Historical security audit report | Scattered at docs root | Move | `docs/quality/security-audit-2026-10-13.md` |
| `docs/bug-audit/` | 30+ historical SQL and bug audit markdown files | Subdirectory at docs root | Consolidate | `docs/quality/bug-audit/` |
| `docs/bug-audit/BitChord-1.8-linux-amd64.deb` | Unrelated 70MB Linux installer package | Clutter | Remove | Delete from disk |
| `docs/superpowers/` | Role metadata specifications and plans | Subdirectory at docs root | Move | `docs/architecture/specs/` |
| `docs/Screenshot/` | Application UI screenshots | Subdirectory at docs root | Move | `design/assets/screenshots/` |
| `docs/Knowledge_Base/` | Official Obsidian Knowledge Base (66 notes) | Core vault | Retain exactly at path | `docs/Knowledge_Base/` (Preserves validator & citations) |

---

## 6. Scripts & Developer Utilities (`scripts/`)

| Path | Responsibility | Correct Location? | Recommendation | Dependencies |
|---|---|---|---|---|
| `scripts/validate_docs.py` | Strict validation of KB citations, wikilinks, & mermaid | Yes | Retain | Python 3, Obsidian vault |
| `scripts/test_validate_docs.py` | Regression test suite for doc validator | Yes | Retain | Python unittest |
| `scripts/check_graphify_html.py`| Verification of generated graph HTML | Yes | Retain | Python 3, Graphify |
| `scripts/graphify-viewer.patch` | Patch file for Graphify viewer | Yes | Retain | Graphify |

---

## 7. AI Tooling & Metadata Directories

| Path | Responsibility | Status | Recommendation |
|---|---|---|---|
| `.agents/skills/` | Project-specific agent skills (`ui-states`, `kotlin-coroutines-expert`) | Active | Retain at root |
| `.claude/skills/` | Security audit skills and validation rules | Active | Retain at root |
| `.opencode/` | OpenCode agent configuration and docs-maintainer rules | Active | Retain at root |
| `graphify-out/` | Code knowledge graph outputs and report (generated) | Generated | Retain (gitignored) |
| `.artifacts/` | Historical SDD artifacts and implementation plans | Active citation source | Retain at root |

---

## 8. Summary of Identified Flaws & Clutter

1. **Scattered Documentation**: High-value guides (`GETTING_STARTED.md`, `SYNC.md`, `BACKUP_RESTORE.md`, `AUDIT_REPORT_...`) lived directly under `docs/` alongside the Obsidian vault, rather than being organized into clear topic domains (`getting-started/`, `architecture/`, `operations/`, `quality/`).
2. **Binary Artifact in Docs**: A 70MB unrelated Linux installer package (`BitChord-1.8-linux-amd64.deb`) was lingering in `docs/bug-audit/`.
3. **Transient Node Heap Dumps**: `tui.heapsnapshot` and `server.heapsnapshot` remained in the repository root.
4. **Missing Architectural Guide**: No single `where-things-belong.md` file existed for developers to know the exact placement for new DAOs, screens, migrations, and use cases.
5. **Missing CONTRIBUTING.md**: Standard open-source contribution guidelines were absent.
