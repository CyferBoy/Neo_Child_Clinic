# Project: Neo Child Clinic Android Codebase First-Principles Untangling

## Architecture
- **Application Type**: Offline-first Android medical clinic application (Kotlin, Jetpack Compose, Room SQLite, WorkManager, Hilt, SQLCipher, Biometrics).
- **Clean Architecture Principle**: Dependencies flow strictly inward toward stability:
  `UI / Presentation -> Feature Domain -> Data Layer -> Core Layer`
  `core` must contain framework-independent primitives and must never import feature packages. Application wiring (Hilt DI modules) resides in `com.neochildclinic.di`.
- **Offline-First Synchronization Invariants**:
  - Local-first writes to encrypted Room database (`PRAGMA busy_timeout = 30000`, WAL mode, fail-closed migrations).
  - Outbox queue (`sync_queue`) with transaction grouping, single-flight execution, deduplication, and dependency ordering.
  - Background `SyncWorker` with `SyncUploader` and conflict resolution.
  - Cloudflare encrypted backup container (`.nccb` format, PBKDF2 + AES-256-GCM).

## Structured Decision Log (Survey & Problem Inventory)
Mapping all identified issues across the four core dimensions (Hidden Links, Needless Links, Surplus, Special Cases, Comments, Tests) to first-principles fixes:

| Issue ID | Dimension | Problem Summary | Root Cause & Evidence | First-Principles Redesign Fix | Milestone |
|:---|:---|:---|:---|:---|:---:|
| **UNTANGLE-01** | Needless Links / Layer Inversion | `AuthViewModel` & `LockScreen` placed in `core` | `core.security.AuthViewModel` imports `feature.profile`, `feature.sync`, `feature.widget`, and `data.local.AppDatabase`. `core.ui.LockScreen` is a 217-line Composable. | Relocate `AuthViewModel` to `feature/auth/presentation/` and `LockScreen` to `feature/auth/presentation/ui/`. Convert ViewModel state to `StateFlow`. Inject repositories instead of raw `AppDatabase`. | M1 |
| **UNTANGLE-02** | Cross-Feature Leak | `PatientRemoteDataSource` handles stock transactions | `getStockHistoryPage()` returning `List<InventoryTransactionEntity>` is declared on `PatientRemoteDataSource`, forcing `InventoryRepositoryImpl` to inject patient data source. | Move `getStockHistoryPage` to `InventoryRemoteDataSource` (or query Postgrest directly in inventory data). Decouple `InventoryRepositoryImpl` from `PatientRemoteDataSource`. | M1 |
| **UNTANGLE-03** | Needless Links / Shallow Facade | `PatientUtils` 77-line delegating wrapper | Pure pass-through facade over `AgeUtils` and `DateUtils` with misplaced methods (`cleanVaccineName`, deprecated `filterVaccinationsByPeriod`). | Inline calls to `DateUtils` and `AgeUtils` directly; move `cleanVaccineName` to domain helper; move `filterVaccinationsByPeriod` to `VaccinationUtils`. Safely deprecate/prune `PatientUtils`. | M1 |
| **UNTANGLE-04** | Architecture Layer Inversion | DI wiring located in `core/di` | `core/di/RepositoryModule.kt` binds 19 feature repositories, inverting Clean Architecture stability by making `core` aware of all features. | Move application DI modules from `com.neochildclinic.core.di` to `com.neochildclinic.di`, restoring `core` as an independent leaf layer. | M1 |
| **UNTANGLE-05** | Special Cases / Type Ladder | 16-type `when` ladder in `isEntityModifiedOffline` | `SyncUploader.kt:775-794` performs 16 manual `is Entity -> !entity.isSynced` checks due to missing interface. | Define `interface SyncableEntity { val isSynced: Boolean }`. Have all 16 syncable entities implement it. Replace 20-line `when` ladder with polymorphic `(entity as? SyncableEntity)?.let { !it.isSynced } ?: false`. | M1 |
| **UNTANGLE-06** | Surplus / Dead Code | Dead dependencies, dead queries, unread fields | Unused `postgrest` injection and `syncDao` alias in `SyncRepositoryImpl`; uncalled `deletePendingDuplicates` in `SyncQueueDao`; unread `protectedSecretLabel` in `BiometricAuthenticator`; unused imports in `AuditLogger`. | Remove unused `postgrest` injection, `syncDao` alias, uncalled DAO query, unread label, and dead imports. | M1 |
| **UNTANGLE-07** | Hidden Links & Concurrency | Concrete injections and temporal callbacks in core | Workers (`DailySummaryWorker`, `SyncWorker`, `PersonalReminderNotificationWorker`) inject concrete `*Impl` / `AppDatabase`. `BiometricLockManager` uses a mutable callback pointer. | Inject domain interfaces (`InventoryRepository`, `SyncRepository`, `PersonalReminderDao`) in workers. Convert `BiometricLockManager` to injectable singleton or direct preference observer. | M1 |
| **UNTANGLE-08** | Comments Cleanup (Core/Data) | Misleading and restating comments | `DateUtils.kt:186` claims `formatBytes` returns current ISO timestamp; restating comments in `DateUtils`, `DatabaseModule`. | Correct misleading KDoc on `formatBytes`; prune restating comments; strictly preserve rationale comments (SQLCipher busy timeout, GoTrue token rotation, PBKDF2 backup encryption). | M1 |
| **UNTANGLE-09** | Inverted Domain Dependency | Domain models typealiased to Room entities | `Patient`, `WasteRecord`, and `DoctorWeeklySlot` in `domain/model/` are typealiases to Room `@Entity` data classes, coupling domain to SQLite/Room annotations. | Create pure Kotlin data classes in `domain/model/` for `Patient`, `WasteRecord`, `DoctorWeeklySlot`. Provide `toDomain()` and `toEntity()` mappers in data layer. | M2 |
| **UNTANGLE-10** | Cross-Feature Misplaced Responsibility | `recordConsultation` implemented in `vaccination` package | `ClinicalVaccinationService.kt:193-248` handles consultation records, forcing `AddConsultationViewModel` to inject vaccination service. | Extract `RecordConsultationUseCase` (or clinical visit service) to consultation/clinical domain. Decouple `AddConsultationViewModel` from `ClinicalVaccinationService`. | M2 |
| **UNTANGLE-11** | Reverse Domain Dependency | `FinanceCalculator` misplaced in `statistics` | `feature/statistics/domain/FinanceCalculator.kt` is used by core transactional code in `ClinicalVaccinationService` and `FinanceRepositoryImpl`. | Move `FinanceCalculator` to `feature/finance/domain/` so `feature/statistics` depends on `feature/finance`, establishing proper dependency flow toward stability. | M2 |
| **UNTANGLE-12** | Implicit Conventions & Inconsistent Dates | Divergent date string formats | Callers pass `"d MMM yyyy"` and `"yyyy-MM-dd"`, forcing `GetAvailableSlotsUseCase` to regex-normalize and query DB with multiple date permutations. | Standardize domain/entity slot interfaces on ISO (`yyyy-MM-dd`). Confine display formatting (`"d MMM yyyy"`) strictly to UI Composables. Remove dual-format regex parsing in `GetAvailableSlotsUseCase`. | M2 |
| **UNTANGLE-13** | Surplus / Shallow Feature Splitting | Redundant 2-file `feature/search` module | `SearchScreen` and `SearchViewModel` duplicate `PatientListViewModel` debounced search; `SearchPatientsUseCase` is a 6-line pass-through. | Consolidate `feature/search` into `feature/patient`. Prune shallow pass-through `SearchPatientsUseCase` in favor of direct repository flow. | M2 |
| **UNTANGLE-14** | Concurrency & Resilience Hazards | 100+ non-atomic `.value = ` and swallowed cancellations | Direct state assignment in ViewModels risks clobbering concurrent updates; catch blocks catching `Exception` swallow coroutine `CancellationException`. | Replace direct `.value = ...` with atomic `.update { ... }`. Add `if (e is CancellationException) throw e` to all coroutine catch blocks. | M3 |
| **UNTANGLE-15** | Comments Cleanup (Features) | Syntax-restating comments | Comments like `// 1. Search Bar`, `// 1. Patient Summary`, `// 1. Save Locally` clutter feature code. | Prune restating comments; strictly preserve external constraint comments (NCC-004 retry duplicate deduction rationale, URLEncoder `%20` explanation, reversal cap rationale). | M3 |
| **UNTANGLE-16** | Tautological Tests | Tests testing local test variables or stdlib | Tests in `InventorySettingsAndFilteringTest:32-63, 117-144`, `StatisticsRegressionTest:105-118`, `ExpenseCalculatorTest:70-79`, `InventorySortKeysTest:34-48`, `ExpenseValidatorTest:72-78` test local variables or stdlib. | Prune redundant tests that test local variables; verify that genuine behavior is covered by domain unit tests. | M4 |
| **UNTANGLE-17** | Brittle & Weak Tests | Tests tied to internals or weak assertions | `BackupCryptoTest` comparing `x == x`; `PatientUtilsTest` using weak `assertNotNull`; `DoctorAvailabilityRepositoryTest` using `sun.misc.Unsafe` and reflection proxies; `AppUpdateManagerTest` testing regex. | Rewrite tests to assert on public behavior: exact SHA-256 hash in `BackupCryptoTest`, exact IST epoch millis in `PatientUtilsTest`, extract validator for `DoctorAvailabilityRepositoryTest`, test public release parser in `AppUpdateManagerTest`. | M4 |
| **UNTANGLE-18** | Hidden Links & Leaks in Tests | State leaks, timing dependencies, misplaced tests | `DateClassifierTest` leaks `Locale.GERMANY` to JVM worker; `SingleFlightTest` has arbitrary `delay()`; `RefreshStepTest` & `RefreshTablesTest` placed in `core/sync/` instead of `feature/sync/`. | Restore default Locale in `DateClassifierTest.tearDown()`; merge `SingleFlightTest` into deterministic coroutine test suite; relocate misplaced tests to `feature/sync/domain`; clean dead stubs in `ServiceStubs.kt`. | M4 |
| **UNTANGLE-19** | Verification & Hardening | Full offline test suite and documentation validation | Ensure zero regressions, complete offline verification, clean architecture compliance, and forensic audit clean verdict. | Execute `./gradlew testDebugUnitTest --offline` and `python3 scripts/test_validate_docs.py`. Run challenger stress verification and forensic auditor integrity audit. | M5 |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Core & Data Layer Untangling & Clean Architecture Invariants | UNTANGLE-01 to UNTANGLE-08 | none | COMPLETE |
| M2 | Feature & Domain Layer Clean Architecture & Structural Decoupling | UNTANGLE-09 to UNTANGLE-13 | M1 | COMPLETE |
| M3 | State Mutation Safety, Concurrency & Comments Cleanup | UNTANGLE-14, UNTANGLE-15 | M1, M2 | COMPLETE |
| M4 | Test Architecture Cleanup & Behavior Assertions | UNTANGLE-16, UNTANGLE-17, UNTANGLE-18 | M1, M2, M3 | COMPLETE |
| M5 | Comprehensive Offline Verification, Hardening & Final Gate | UNTANGLE-19 | M1, M2, M3, M4 | COMPLETE |

## Session 2026-11-17 follow-up (see `docs/audit/untangle-2026-11-17.md`)

Resumed the decision log from the committed M1/M2 state and closed the remaining items:
PatientUtils facade inlined and deleted (UN-03), ISO date contract unified (UN-12),
`feature/search` consolidated (UN-13), cancellation guards completed (UN-14),
restating comments pruned (UN-15), tautological/reflection/timing tests cleaned
(UN-16/17/18), and full offline verification run (UN-19). Additional first-principles
fixes: one cause-chain walker, one refresh-step key set, one password minimum, one
clinical vaccination predicate, 43 zero-caller DAO methods deleted across both passes.
Verification: `./gradlew testDebugUnitTest --offline` — 493 tests, 0 failures;
`python3 scripts/validate_docs.py --strict` — 0 errors.

Pass 2 (2026-11-17, see `docs/audit/untangle-2026-11-17-pass2.md`) widened scope to
supabase/cloudflare/CI: unified the IST constant and FY parsing, swapped two worker
concrete injections for interfaces, deduped CI Firebase config and tightened the
docs validator, and removed ~55 restating comments plus three tautological tests.
Verification: 496 unit tests, 22 worker tests, 37 validator tests, 0 errors.

## Interface Contracts

### SyncableEntity ↔ SyncUploader
- `com.neochildclinic.data.local.entity.SyncableEntity`:
  ```kotlin
  interface SyncableEntity {
      val isSynced: Boolean
  }
  ```
- All 16 syncable Room entities implement `SyncableEntity`.
- `SyncUploader.isEntityModifiedOffline(entity: Any?): Boolean`:
  ```kotlin
  internal fun isEntityModifiedOffline(entity: Any?): Boolean =
      (entity as? SyncableEntity)?.let { !it.isSynced } ?: false
  ```

### Pure Domain Models ↔ Room Entities
- `com.neochildclinic.domain.model.Patient`: Pure Kotlin data class with domain properties, zero Room/KSP annotations.
- `com.neochildclinic.data.local.entity.PatientEntity`: Room `@Entity` with SQLite metadata.
- Mapping extensions:
  - `fun PatientEntity.toDomain(): Patient`
  - `fun Patient.toEntity(isSynced: Boolean = true): PatientEntity`

### Clinical Consultation ↔ Clinical Visit Service
- `RecordConsultationUseCase` (in `feature/consultation/domain`):
  Coordinates visit creation, consultation insertion, and financial transaction recording within a single atomic database transaction.
- `AddConsultationViewModel`: Depends on `RecordConsultationUseCase` rather than `ClinicalVaccinationService`.

### Finance Calculator ↔ Statistics
- `FinanceCalculator`: Located in `com.neochildclinic.feature.finance.domain`.
- `feature/statistics` imports `FinanceCalculator` from `feature.finance.domain`, ensuring dependencies flow toward transactional stability.

## Code Layout
- `app/src/main/java/com/neochildclinic/core/`: Pure shared primitives (common, cache, preferences, security primitives, network base). No feature imports.
- `app/src/main/java/com/neochildclinic/di/`: Central application Hilt dependency injection modules (`RepositoryModule`, `DatabaseModule`, `RemoteDataSourceModule`).
- `app/src/main/java/com/neochildclinic/data/`: Data access layer (Room database, entities, DAOs, converters, remote data sources, outbox repository).
- `app/src/main/java/com/neochildclinic/domain/model/`: Pure Kotlin business models (`Patient`, `Vaccination`, `Consultation`, `WasteRecord`, `DoctorAvailability`).
- `app/src/main/java/com/neochildclinic/feature/`: Feature modules (auth, patient, consultation, vaccination, inventory, finance, doctor, reminder, settings, staff, statistics, widget).
- `app/src/test/java/com/neochildclinic/`: Behavior-driven unit tests executing offline.
- `scripts/`: Offline validation scripts (`test_validate_docs.py`).
