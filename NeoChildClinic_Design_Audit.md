# Neo Child Clinic — Evidence-Based Software Design Audit

**Scope:** `app/src/main/java/com/neochildclinic/**` (309 Kotlin files, ~43,400 LOC), plus DI modules and Supabase edge functions where relevant.
**Method:** Static reading of source, dependency tracing via imports/constructors, and cross-referencing domain interfaces against their implementations. Every finding below cites exact file paths, class/function names, and line numbers taken directly from the uploaded source. Where a line number could not be pinned to a single statement, this is stated explicitly rather than guessed.

---

## A. Executive Summary

The codebase is organized into `presentation (features)` / `domain` / `data` packages that *look* like Clean Architecture + MVVM, and in several places (use cases, `EditReconciler`, `FinanceCalculator`, `ClinicStatsManager`) it genuinely is. However, the dependency-injection layer tells a different story from the package layout: **the domain-interface abstraction that `RepositoryModule` builds is bypassed by the majority of its own consumers.**

Concretely:

- **29 of 34 ViewModels (85%)** inject one or more concrete `...RepositoryImpl` classes directly instead of the domain interface Hilt binds them to (Findings 1–2).
- **All 11 of the other repository implementations** inject the concrete `SyncRepositoryImpl` class instead of the `SyncRepository` interface that exists and is bound for exactly this purpose (Finding 6) — a single, systemic pattern.
- **Six repositories have no domain interface at all** and aren't registered in `RepositoryModule` (Finding 4).
- The root technical cause is traceable: `InventoryRepository` exposes 7 methods while `InventoryRepositoryImpl` exposes ~20 (Finding 3). The interface is incomplete, so anything needing the other 13 methods is forced onto the concrete class — and that forcing then cascades (`BorrowRepositoryImpl` depends on concrete `InventoryRepositoryImpl`, Finding 5).
- Two domain-layer orchestration classes (`ClinicalVaccinationService`, `VaccinationEditEngine`) reach past their own injected repository interfaces and call Room DAOs directly (Findings 8–9) — a genuine Clean Architecture bypass, not merely a style choice, since the correctly-abstracted sibling class `ConsultationEditEngine` (Good Design #7) proves the interfaces are sufficient for the same kind of work.
- A third-party SDK type (`io.github.jan.supabase.storage.FileObject`) leaks from an un-abstracted repository all the way through a ViewModel's public `StateFlow` into two Composables (Finding 11) — the clearest single piece of evidence in the codebase that the abstraction boundary is not holding.
- The sync subsystem (`SyncRepositoryImpl` + `SyncUploader`) contains the single largest method in the codebase (~226 lines, Finding 16) and a five-location parallel `when(entityName)` switch that must be updated in lockstep for every new syncable entity (Finding 17).

Positively: the domain layer's **use cases** (`MergePatientsUseCase`, `GetAvailableSlotsUseCase`, `SearchPatientsUseCase`), the **pure calculators** (`FinanceCalculator`, `EditReconciler`), and the **infrastructure-bridging singletons** (`RealtimeChangeSubscriptions`) are well-designed, narrowly scoped, and interface-driven. They demonstrate the team knows how to do this correctly — the problem is inconsistency, not a lack of skill. No LSP or ISP violations were found (the codebase uses almost no custom inheritance and one-implementation-per-interface throughout), and no import cycles were found among repository implementations.

---

## B. Critical Findings

### [FINDING 1]
**Severity:** Critical
**Principle:** Dependency Inversion Principle (DIP)

**File:** `app/src/main/java/com/neochildclinic/features/dashboard/DashboardViewModel.kt`
**Class:** `DashboardViewModel`
**Function:** constructor
**Lines:** 68–80

**Evidence:**
```kotlin
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val syncRepository: SyncRepositoryImpl,
    private val networkMonitor: NetworkMonitor,
    private val patientRepository: PatientRepositoryImpl,
    private val patientTodoRepository: PatientTodoRepositoryImpl,
    private val inventoryRepository: InventoryRepositoryImpl,
    private val reminderRepository: ReminderRepositoryImpl,
    private val wasteRepository: WasteRepositoryImpl,
    private val borrowRepository: BorrowRepositoryImpl,
    private val realtimeChangeSubscriptions: RealtimeChangeSubscriptions,
    private val profileRepository: ProfileRepositoryImpl,
    private val getAvailableSlotsUseCase: GetAvailableSlotsUseCase,
    private val doctorAvailabilityRepository: DoctorAvailabilityRepositoryImpl
) : ViewModel() {
```

**Current dependency/responsibility:** 12 constructor parameters. 9 of the 12 are concrete `...Impl` classes imported from `com.neochildclinic.data.repository.*`. Five of those nine (`PatientRepository`, `InventoryRepository`, `ReminderRepository`, `WasteRepository`, `DoctorAvailabilityRepository`) **already have domain interfaces** that are bound in `di/RepositoryModule.kt` (see that file, lines 33–44) — this ViewModel simply doesn't use them.

**Why this violates the principle:** DIP requires high-level modules (the ViewModel, which encodes UI policy) to depend on abstractions, not concretions. Here the high-level module depends directly on data-layer implementation classes. Concrete consequences: (1) the ViewModel cannot be unit-tested without either instantiating real `...Impl` classes (which themselves require `AppDatabase`, `Postgrest`, `Auth`, etc.) or resorting to reflection-based mocking of concrete classes; (2) any internal refactor of `InventoryRepositoryImpl`'s constructor ripples into this ViewModel even though the ViewModel doesn't need any Impl-only method; (3) the abstraction Hilt maintains (`@Binds ... : InventoryRepository`) becomes dead weight for this call site.

**Recommended refactoring:** Change all nine parameters to their domain-interface types (`PatientRepository`, `InventoryRepository`, `ReminderRepository`, `WasteRepository`, `BorrowRepository`, `ProfileRepository`, `DoctorAvailabilityRepository`, `SyncRepository`). For `BorrowRepository` and `ProfileRepository`, this first requires Finding 4 (adding the missing interfaces) since they don't exist yet.

**Confidence:** High

---

### [FINDING 2]
**Severity:** Critical
**Principle:** DIP (systemic instance, quantified across the codebase)

**File:** N/A — cross-cutting (29 files)
**Class:** N/A
**Function:** N/A
**Lines:** N/A

**Evidence:** Counting `import com.neochildclinic.data.repository.*RepositoryImpl` inside `features/**/*ViewModel.kt`:

| File | Concrete-Impl imports |
|---|---|
| `dashboard/DashboardViewModel.kt` | 9 |
| `statistics/StatisticsViewModel.kt` | 6 |
| `patient/PatientViewModel.kt` | 5 |
| `vaccination/AddVaccinationViewModel.kt` | 4 |
| `statistics/FullReportViewModel.kt` | 4 |
| `statistics/FinanceDetailsViewModel.kt` | 3 |
| `personalreminder/AddEditPersonalReminderViewModel.kt` | 3 |
| `patient/PatientListViewModel.kt` | 3 |
| `patient/AddConsultationViewModel.kt` | 3 |
| ... 20 more files with 1–2 each | 1–2 |

29 of the project's 34 ViewModels import at least one concrete repository class. Only 5 ViewModels (`SearchViewModel`, `AppUpdateViewModel`, and three trivial dialog view models — confirmed by absence from the grep above) avoid the pattern entirely.

**Current dependency/responsibility:** The ViewModel layer as a whole treats `data.repository.*Impl` as its normal dependency surface, not `domain.repository.*` interfaces.

**Why this violates the principle:** A single occurrence could be a one-off oversight; 29/34 is the dominant pattern of the codebase, meaning the `domain/repository` interface layer functions as decoration over the DI graph rather than as the actual contract consumers code against. This is a design-system-level DIP violation, not an isolated bug.

**Recommended refactoring:** Mechanical, low-risk refactor: for every ViewModel, replace the `Impl` import/type with the interface type, add any missing interface methods first (see Finding 3–4), and let the Kotlin compiler surface any call site that genuinely needs an Impl-only method — those are the real signal for what the interfaces are still missing.

**Confidence:** High

---

### [FINDING 3]
**Severity:** High
**Principle:** DIP / Interface Segregation (incomplete abstraction) — root cause of Findings 1, 2, 5

**File:** `app/src/main/java/com/neochildclinic/domain/repository/InventoryRepository.kt` (interface, 36 lines) vs. `app/src/main/java/com/neochildclinic/data/repository/InventoryRepositoryImpl.kt` (impl, 748 lines)
**Class:** `InventoryRepository` / `InventoryRepositoryImpl`
**Function:** N/A (public API surface comparison)
**Lines:** Interface: 10–36 (full file). Impl public functions: 48, 119, 121, 126, 129, 132, 135, 139, 158, 179, 223, 303, 341, 394, 426, 461, 533, 582, 636, 672, 726, 730.

**Evidence — interface (complete):**
```kotlin
interface InventoryRepository {
    fun getInventoryItems(...): Flow<List<InventoryItem>>
    fun getAllVaccines(): Flow<List<VaccineEntity>>
    suspend fun deductStockFromBatch(...)
    suspend fun reverseDeduction(...)
    suspend fun transferPatientTransactions(duplicateId: String, masterId: String)
    suspend fun getInventoryDeductionsForVaccination(vaccinationId: String): List<InventoryDeductionEntity>
    suspend fun refreshInventory()
}
```
**Evidence — impl's additional public surface (not on the interface):** `getVaccineBatches`, `getInventoryTransactions`, `getBatchById`, `getVaccineById`, `addVaccine`, `updateVaccine`, `addBatch`, `addStockBatch`, `getStockHistoryPage`, `updateBatch`, `deleteBatch`, `deleteVaccine`, `deductStock`, `addStockToBatch`, `returnBorrowedStock` — 15 methods.

**Problem:** Only 7 of ~22 public methods on `InventoryRepositoryImpl` are declared on `InventoryRepository`. Every caller that needs vaccine/batch CRUD, stock history, or the FEFO `deductStock` (multi-batch) path has no interface to call through — it *must* take a dependency on the concrete class. This is precisely why `AddVaccineViewModel`, `AddBatchViewModel`, `StockHistoryViewModel`, `BorrowRepositoryImpl`, `AddStockViewModel`, `DashboardViewModel`, and `AddVaccinationViewModel` all depend on `InventoryRepositoryImpl` directly — there is no interface method for them to call.

**Why this violates the principle:** This is the mechanism, not just a symptom. Fixing Findings 1–2 by simply retyping constructor parameters is impossible for any call site using these 15 methods until the interface is widened. The abstraction is not "slightly leaky" — for most of its surface area, it doesn't exist.

**Recommended refactoring:** Add the missing 15 methods to `InventoryRepository`. Where `deductStock` and `deductStockFromBatch` genuinely overlap (see Finding 15), consolidate before promoting both to the interface, rather than promoting duplicated logic as-is.

**Confidence:** High

---

### [FINDING 4]
**Severity:** High
**Principle:** DIP / Missing Abstraction

**File:** `app/src/main/java/com/neochildclinic/di/RepositoryModule.kt` (full file, 36 lines) cross-referenced against `app/src/main/java/com/neochildclinic/data/repository/*.kt`
**Class:** `RepositoryModule`; affected classes: `ProfileRepositoryImpl`, `StaffManagementRepositoryImpl`, `AuditLogRepositoryImpl`, `BorrowRepositoryImpl`, `DeviceRepositoryImpl`, `DocumentRepositoryImpl`
**Function:** N/A
**Lines:** `RepositoryModule.kt` 30–41 lists 12 `@Binds` methods; none of the six classes above appear.

**Evidence:**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun patientRepository(impl: PatientRepositoryImpl): PatientRepository
    @Binds abstract fun vaccinationRepository(impl: VaccinationRepositoryImpl): VaccinationRepository
    @Binds abstract fun financeRepository(impl: FinanceRepositoryImpl): FinanceRepository
    @Binds abstract fun reminderRepository(impl: ReminderRepositoryImpl): ReminderRepository
    @Binds abstract fun inventoryRepository(impl: InventoryRepositoryImpl): InventoryRepository
    @Binds abstract fun consultationRepository(impl: ConsultationRepositoryImpl): ConsultationRepository
    @Binds abstract fun syncRepository(impl: SyncRepositoryImpl): SyncRepository
    @Binds abstract fun doctorAvailabilityRepository(impl: DoctorAvailabilityRepositoryImpl): DoctorAvailabilityRepository
    @Binds abstract fun patientTodoRepository(impl: PatientTodoRepositoryImpl): PatientTodoRepository
    @Binds abstract fun personalReminderRepository(impl: PersonalReminderRepositoryImpl): PersonalReminderRepository
    @Binds abstract fun expenseRepository(impl: ExpenseRepositoryImpl): ExpenseRepository
    @Binds abstract fun wasteRepository(impl: WasteRepositoryImpl): WasteRepository
}
```
Six repository-shaped classes have **no `domain/repository` interface file at all**: `ProfileRepositoryImpl` (used by 6+ ViewModels), `StaffManagementRepositoryImpl` (`AdminViewModel`), `AuditLogRepositoryImpl` (3 consumers), `BorrowRepositoryImpl` (`BorrowedViewModel`, `DashboardViewModel`), `DeviceRepositoryImpl` (`MainActivity`, `AuthViewModel`, `NeoChildFirebaseMessagingService`), `DocumentRepositoryImpl` (`PatientViewModel`, `AddExpenseViewModel`).

**Current dependency/responsibility:** These are all `@Singleton @Inject constructor`-based classes that are functionally repositories (they wrap Room DAOs and/or Supabase calls) but were simply never given the same interface + `@Binds` treatment as their 12 siblings.

**Why this violates the principle:** There is no principled reason `ProfileRepository` (profile data — arguably as central as `PatientRepository`) lacks an interface while `WasteRepository` (a comparatively minor feature) has one. This is an inconsistent, not a deliberate, design decision — nothing in the code documents why these six were excluded, unlike the FinanceEntity/ReminderEntity leak (Finding 10), which *is* documented. The consequence is identical to Finding 3: every consumer of these six classes is forced onto the concrete type.

**Recommended refactoring:** Extract `ProfileRepository`, `StaffManagementRepository`, `AuditLogRepository`, `BorrowRepository`, `DeviceRepository`, `DocumentRepository` interfaces mirroring each Impl's current public surface, and add the corresponding `@Binds` entries to `RepositoryModule`.

**Confidence:** High

---

### [FINDING 5]
**Severity:** High
**Principle:** Coupling — Repository → unrelated feature (concrete), cascading from Finding 3/4

**File:** `app/src/main/java/com/neochildclinic/data/repository/BorrowRepositoryImpl.kt`
**Class:** `BorrowRepositoryImpl`
**Function:** constructor
**Lines:** 16–17 (imports), 28–32 (constructor)

**Evidence:**
```kotlin
import com.neochildclinic.data.repository.InventoryRepositoryImpl
import com.neochildclinic.data.repository.SyncRepositoryImpl
...
class BorrowRepositoryImpl @Inject constructor(
    ...
    private val inventoryRepository: InventoryRepositoryImpl,
    private val syncRepository: SyncRepositoryImpl,
```

**Dependency evidence:** `BorrowRepositoryImpl` calls `inventoryRepository.deductStock(...)` (used at `BorrowRepositoryImpl.kt:63`) and `inventoryRepository.returnBorrowedStock(...)` (`BorrowRepositoryImpl.kt:153`) — both of which are exactly the two `InventoryRepositoryImpl`-only methods identified as missing from `InventoryRepository` in Finding 3.

**Why this violates the principle:** This is one repository (`Borrow`) taking a hard, compile-time dependency on the *concrete implementation* of a conceptually unrelated repository (`Inventory`), purely because the interface doesn't expose what it needs. It cannot be substituted, mocked, or evolved independently of `InventoryRepositoryImpl`'s internals.

**Recommended refactoring:** Promote `deductStock` and `returnBorrowedStock` to `InventoryRepository` (after resolving Finding 15's duplication), then retype this constructor to `InventoryRepository` / `SyncRepository`.

**Confidence:** High

---

### [FINDING 6]
**Severity:** Critical
**Principle:** DIP — systemic, single dependency affecting the entire data layer

**File:** All 11 sibling files of `app/src/main/java/com/neochildclinic/data/repository/`
**Class:** `PatientRepositoryImpl`, `VaccinationRepositoryImpl`, `InventoryRepositoryImpl`, `ConsultationRepositoryImpl`, `DoctorAvailabilityRepositoryImpl`, `ExpenseRepositoryImpl`, `PatientTodoRepositoryImpl`, `PersonalReminderRepositoryImpl`, `ProfileRepositoryImpl`, `ReminderRepositoryImpl`, `WasteRepositoryImpl`
**Function:** constructor (each)
**Lines:** e.g. `PatientRepositoryImpl.kt:40`, `VaccinationRepositoryImpl.kt:36`, `InventoryRepositoryImpl.kt:34` (representative sample; the identical pattern repeats in all 11 files)

**Evidence:**
```kotlin
// PatientRepositoryImpl.kt
private val syncRepository: SyncRepositoryImpl,
// VaccinationRepositoryImpl.kt
private val syncRepository: SyncRepositoryImpl,
// InventoryRepositoryImpl.kt
private val syncRepository: SyncRepositoryImpl,
```
This exact line (`private val syncRepository: SyncRepositoryImpl,`) recurs in all 11 files. Meanwhile `SyncRepository` **is** a proper interface, bound in `RepositoryModule.kt:37` (`@Binds abstract fun syncRepository(impl: SyncRepositoryImpl): SyncRepository`), and **is** used correctly (as the interface) by the domain-layer classes `ClinicalVaccinationService` and `VaccinationEditEngine` and `MergePatientsUseCase`.

**Current dependency/responsibility:** Every repository implementation in the data layer is wired to the concrete `SyncRepositoryImpl` sync-queue writer, not the `SyncRepository` abstraction meant for exactly this purpose.

**Why this violates the principle:** This is DIP failing at its most consequential point: the one dependency shared by literally every repository in the app is the one place a concrete-class dependency is most expensive (any repository test now transitively needs `AppDatabase`, `Postgrest`, `Auth`, and `SyncManagerImpl` — `SyncRepositoryImpl`'s own constructor — to construct a mock). It also contradicts the pattern the domain layer already gets right, showing this is an inconsistency between layers rather than a deliberate architectural stance.

**Recommended refactoring:** Change all 11 constructor parameter types from `SyncRepositoryImpl` to `SyncRepository`. This is a pure type change (Kotlin's structural typing over the interface already covers `enqueue`, the only method these repositories call) and should compile with zero behavior change.

**Confidence:** High

---

### [FINDING 7]
**Severity:** Medium
**Principle:** Dependency Injection — hidden/manual dependency creation

**File:** `app/src/main/java/com/neochildclinic/data/repository/SyncRepositoryImpl.kt`
**Class:** `SyncRepositoryImpl`
**Function:** class body (field initializer)
**Lines:** 30

**Evidence:**
```kotlin
class SyncRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val postgrest: Postgrest,
    private val syncManager: SyncManagerImpl,
    private val auth: Auth
) : SyncRepository {

    private val syncDao = database.syncQueueDao()
    private val uploader = SyncUploader(database, postgrest)
```

**Problem:** `SyncRepositoryImpl` is itself fully Hilt-managed (`@Inject constructor`), yet its collaborator `SyncUploader` is manually `new`'d up in a field initializer rather than injected, even though `SyncUploader` takes exactly the same two dependencies (`database`, `postgrest`) already available in this constructor. `SyncUploader` is marked `internal class` (see `SyncUploader.kt:13`), which is presumably why it isn't `@Inject`-annotated, but that's a self-imposed constraint, not a technical one.

**Why this violates the principle:** A manually-constructed collaborator can't be substituted by a test double without either making `uploader` an injected constructor parameter or reflectively replacing a `private val`. It also means `SyncUploader`'s lifecycle is silently tied to `SyncRepositoryImpl`'s instantiation rather than being visible in the dependency graph (e.g., in `hilt_aggregated_deps` or a `@Component` dump).

**Recommended refactoring:** Either (a) make `SyncUploader` `@Inject`-constructable and `@Singleton`/unscoped as appropriate and inject it as a fourth constructor parameter, or (b) if it must remain tightly coupled to this one repository, keep it but document the constraint that unit tests of `SyncRepositoryImpl` cannot isolate this collaborator without a factory seam.

**Confidence:** High

---

### [FINDING 8]
**Severity:** Critical
**Principle:** Dependency-direction violation (Clean Architecture bypass) / SRP

**File:** `app/src/main/java/com/neochildclinic/domain/service/ClinicalVaccinationService.kt`
**Class:** `ClinicalVaccinationService`
**Function:** `deductInventoryForNewVaccination`, `recordConsultation`
**Lines:** 106, 118, 136, 177 (direct DAO calls); 27–33 (constructor showing the injected interfaces that are bypassed)

**Evidence:**
```kotlin
@Singleton
class ClinicalVaccinationService @Inject constructor(
    private val database: AppDatabase,
    private val vaccinationRepository: VaccinationRepository,   // <- an interface IS injected...
    private val consultationRepository: ConsultationRepository,
    private val financeRepository: FinanceRepository,
    private val reminderRepository: ReminderRepository,
    private val syncRepository: SyncRepository,
    private val inventoryRepository: com.neochildclinic.domain.repository.InventoryRepository // <- ...and here
) {
```
```kotlin
// line 106 & 118, inside deductInventoryForNewVaccination:
database.inventoryDeductionDao().insert(InventoryDeductionEntity(...))
...
database.inventoryDeductionDao().insert(InventoryDeductionEntity(...))
// line 136:
database.vaccinationDao().updateInventoryStatus(vaccination.id, finalStatus.name)
// line 177, inside recordConsultation:
database.vaccinationDao().insertVaccination(visit)
```

**Dependency evidence:** This class imports `com.neochildclinic.data.local.database.AppDatabase` (line 4) directly, in addition to its five domain repository interfaces.

**Why this violates the principle:** This is not merely "domain depends on data" in the abstract — it's a concrete, provable case of a class **holding the correct interface as a constructor parameter and using the concrete Room database instead**, for operations (writing an `InventoryDeductionEntity`, updating a vaccination's status, inserting a visit row) that are the exact kind of thing `InventoryRepository`/`VaccinationRepository` exist to encapsulate. The dependency direction `Presentation → Domain → Data` is bypassed at the domain/data seam from within the domain layer itself, meaning any future swap of the persistence layer (or even a schema change to these two tables) requires editing domain-layer code that has no business knowing about Room's DAO API.

**Recommended refactoring:** Add `insertInventoryDeductionLog(...)` and `updateInventoryStatus(...)` to `InventoryRepository`/`VaccinationRepository` respectively, and route these four call sites through the interfaces already injected into this class. No new dependency is needed — only method calls change.

**Confidence:** High

---

### [FINDING 9]
**Severity:** High
**Principle:** Dependency-direction violation — same bypass pattern repeated (corroborates Finding 8, ruling out one-off mistake)

**File:** `app/src/main/java/com/neochildclinic/domain/service/VaccinationEditEngine.kt`
**Class:** `VaccinationEditEngine`
**Function:** `reconcileInventoryDeductions`
**Lines:** 141, 143

**Evidence:**
```kotlin
private suspend fun reconcileInventoryDeductions(vaccination: Vaccination) {
    database.inventoryDeductionDao().deleteForVaccination(vaccination.id)
    vaccination.items.forEach { item ->
        database.inventoryDeductionDao().insert(InventoryDeductionEntity(
            vaccinationId = vaccination.id,
            ...
        ))
    }
}
```
This class also injects `InventoryRepository` (the interface, `VaccinationEditEngine.kt:23`) and uses it correctly elsewhere in the same file (`applyInventoryDiff`, lines ~105–121, calls `inventoryRepository.deductStockFromBatch(...)` and `inventoryRepository.reverseDeduction(...)` through the interface). Only the inventory-deduction *audit ledger* write bypasses the interface.

**Why this violates the principle:** Confirms Finding 8 is a repeated pattern (same audit-ledger table, same bypass, two different classes) rather than an isolated oversight — i.e., a systemic gap in `InventoryRepository`'s surface for this one specific write, not a one-off mistake in one file.

**Recommended refactoring:** Same fix as Finding 8 — add the ledger read/write methods to `InventoryRepository` once, and both classes benefit.

**Confidence:** High

---

## C. High-Priority Findings

### [FINDING 10]
**Severity:** Medium
**Principle:** Dependency-direction violation (Clean Architecture) — deliberate, documented trade-off

**File:** `app/src/main/java/com/neochildclinic/domain/repository/FinanceRepository.kt`, `ReminderRepository.kt`, `VaccinationRepository.kt`
**Class:** `FinanceRepository`, `ReminderRepository`, `VaccinationRepository` (interfaces)
**Function:** N/A
**Lines:** `FinanceRepository.kt:3` + `:6-9` (comment) + `:11`; `ReminderRepository.kt:3-4` + `:9` (comment) + `:18-27`; `VaccinationRepository.kt:3` + `:7` (comment) + `:16-17`

**Evidence:**
```kotlin
// FinanceRepository.kt
import com.neochildclinic.data.local.entity.FinanceEntity
...
// ponytail: getAllTransactions returns the Room DTO (FinanceEntity) - it IS the clinic's
// transaction record and the statistics calculators already consume it as such. ...
interface FinanceRepository {
    fun getAllTransactions(): Flow<List<FinanceEntity>>
```
```kotlin
// ReminderRepository.kt
import com.neochildclinic.data.local.entity.ReminderAuditEntity
import com.neochildclinic.data.local.entity.ReminderEntity
...
fun getPatientReminders(patientId: String): Flow<List<ReminderEntity>>
fun getAuditTrail(patientId: String): Flow<List<ReminderAuditEntity>>
```
```kotlin
// VaccinationRepository.kt
import com.neochildclinic.data.local.entity.VaccinationItemEntity
...
suspend fun fetchRemoteVaccinationItems(): List<VaccinationItemEntity>
```

**Current dependency/responsibility:** Three of the twelve domain repository interfaces return Room `Entity` types directly rather than domain models, meaning `domain/repository` (which should sit above `data/local/entity` in the dependency graph) imports from it instead.

**Why this violates the principle — and the counter-argument, fairly stated:** This is a genuine dependency-direction violation: any consumer of `getAllTransactions()`, `getPatientReminders()`, or `fetchRemoteVaccinationItems()` — including `ClinicStatsManager` in the domain layer itself (see `ClinicStatsManager.kt:46`, which casts a combined-flow argument to `List<com.neochildclinic.data.local.entity.FinanceEntity>`) and several ViewModels/Composables downstream — now has a compile-time dependency on Room's schema-mapping DTOs. A future persistence migration away from Room would force changes to these three domain interfaces and everything that consumes them. However, this is a **documented, deliberate decision**, not an oversight: each site carries an explanatory "ponytail:" comment arguing that introducing a parallel domain model would be pure mapping churn with no consumer that mutates or otherwise needs to be decoupled from the entity shape. Per the audit's own Rule 5 (distinguish violation from preference), this is reported as a real but *reasoned* architectural trade-off, not a mistake — the team weighed it and left a trail. Confidence in the violation itself is High; confidence that it currently causes concrete harm is Low-to-Medium, since no consumer today needs the decoupling the interface withholds.

**Recommended refactoring:** No action needed unless/until a consumer needs to mutate these types independently of their Room shape, or the persistence layer changes — at which point introduce a thin domain model + mapper only for the interface(s) actually forced to change.

**Confidence:** High (violation exists) / Medium (practical impact)

---

### [FINDING 11]
**Severity:** Critical
**Principle:** Coupling (UI → third-party SDK) / Missing Abstraction / MVVM

**File:** `app/src/main/java/com/neochildclinic/data/repository/DocumentRepositoryImpl.kt` (whole file, 32 lines), `app/src/main/java/com/neochildclinic/features/patient/PatientViewModel.kt`, `app/src/main/java/com/neochildclinic/features/patient/PatientRecordCards.kt`, `app/src/main/java/com/neochildclinic/features/patient/PatientInfoComponents.kt`
**Class:** `DocumentRepositoryImpl`, `PatientViewModel`, `DocumentCard` (Composable)
**Function:** `listDocuments`; `PatientViewModel` field declarations; `DocumentCard` signature
**Lines:** `DocumentRepositoryImpl.kt:24-26`; `PatientViewModel.kt:16, 42-43`; `PatientRecordCards.kt:30, 150`; `PatientInfoComponents.kt:29, 42`

**Evidence:**
```kotlin
// DocumentRepositoryImpl.kt — no interface exists for this class at all (see Finding 4)
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.FileObject
...
class DocumentRepositoryImpl @Inject constructor(private val storage: Storage) {
    ...
    suspend fun listDocuments(patientId: String): List<FileObject> {
        return bucket.list(patientId)
    }
```
```kotlin
// PatientViewModel.kt
import io.github.jan.supabase.storage.FileObject
...
private val _documents = MutableStateFlow<List<FileObject>>(emptyList())
val documents: StateFlow<List<FileObject>> = _documents.asStateFlow()
```
```kotlin
// PatientRecordCards.kt — a Composable function
import io.github.jan.supabase.storage.FileObject
...
fun DocumentCard(doc: FileObject, onView: () -> Unit, onDelete: () -> Unit) { ... }
```
```kotlin
// PatientInfoComponents.kt — another Composable
import io.github.jan.supabase.storage.FileObject
...
documents: List<FileObject>,
```

**Dependency evidence:** `FileObject` is a class owned by the `io.github.jan.supabase:storage-kt` SDK. It originates in a repository with no domain interface, is exposed unmapped through a `StateFlow` on a ViewModel, and is consumed as a parameter type by two `@Composable` functions in the presentation layer.

**Why this violates the principle:** This is the clearest, most complete violation in the codebase of "Presentation → Domain → Data → Infrastructure" dependency direction: the *infrastructure* type (a third-party network SDK's model class) is visible in the *presentation* layer's function signatures, with domain and data-abstraction layers both skipped entirely. Concretely: the Compose UI cannot be previewed/tested without the Supabase SDK on the classpath; a `FileObject` field the SDK adds/removes/renames in a future version is a breaking change to two Composables and one ViewModel; and there is no `DocumentRepository` interface to insulate against any of this.

**Recommended refactoring:** Introduce a `PatientDocument` domain model (e.g., `data class PatientDocument(val name: String, val path: String, val updatedAt: String?)`), map `FileObject → PatientDocument` inside `DocumentRepositoryImpl` (once it has an interface — see Finding 4), and change `PatientViewModel`'s `StateFlow` and the two Composables' parameter types accordingly.

**Confidence:** High

---

### [FINDING 12]
**Severity:** High
**Principle:** MVVM / Clean Architecture (Presentation depending on Data layer, bypassing Domain)

**File:** 16 files, representative example: `app/src/main/java/com/neochildclinic/features/inventory/VaccineInventoryScreen.kt`
**Class:** `VaccineInventoryScreen` and sibling composables in the same file
**Function:** file-level `import`, `VaccineInventoryScreen(...)`, and the private composable at line 355
**Lines:** 24 (import), 42, 107, 235, 355, 357

**Evidence:**
```kotlin
import com.neochildclinic.data.local.entity.VaccineBatchEntity
...
fun VaccineInventoryScreen(
    ...
) {
    var batchToDelete by remember { mutableStateOf<VaccineBatchEntity?>(null) }
    ...
    onDeleteBatch: (VaccineBatchEntity) -> Unit,   // (also at line 235)
    ...
}
...
@Composable
private fun BatchRow(
    batch: VaccineBatchEntity,
    ...
    onDeleteBatch: (VaccineBatchEntity) -> Unit,
```

**Dependency evidence:** `VaccineBatchEntity` is a `@Entity` Room class from `data.local.entity`. This screen file — pure Jetpack Compose UI — imports and type-parameterizes on it directly. The same pattern (Room `data.local.entity.*` imported directly into a `*Screen.kt`/`*Components.kt`/`*Cards.kt`/`*Dialogs.kt`/`*Tab.kt` file) recurs in 15 other files: `FullAuditLogScreen.kt`, `TodayPatientDialogs.kt`, `TodayPatientsScreen.kt`, `StockHistoryScreen.kt`, `PatientInfoComponents.kt`, `PatientRecordCards.kt`, `VaccinationCards.kt`, `PersonalReminderCards.kt`, `PersonalReminderScreen.kt`, `BackupSettingsScreen.kt`, `FinanceTab.kt`, `MonthlyFinanceDetailsScreen.kt`, `OverviewTab.kt`, `StatisticsScreen.kt`, `VaccinationsTab.kt`.

**Why this violates the principle:** MVVM's UI layer should render `UiState` built from domain models the ViewModel exposes, not persistence-layer entities. Here, 16 Composable files know the exact shape of Room tables. A migration (e.g., renaming a Room column, or replacing Room with SQLDelight) forces changes across 16 UI files that have no logical reason to know about it. This is a direct, at-scale instance of the audit brief's "database/network operations in UI" and "inappropriate UI state ownership" categories, since these Composables' state (`batchToDelete`, callback types) is typed in terms of the persistence model.

**Recommended refactoring:** Introduce/standardize domain models for these entities (several — e.g. `InventoryItem` — already exist and are used elsewhere in the same features; the gap is inconsistent use, not absence) and change ViewModel outputs + Composable signatures to use them. This is a wide but mechanical refactor; prioritize the highest-traffic screens first (`VaccineInventoryScreen`, `StatisticsScreen`, `PatientRecordCards`).

**Confidence:** High

---

### [FINDING 13]
**Severity:** High
**Principle:** MVVM (business/auth logic in UI layer) / SRP

**File:** `app/src/main/java/com/neochildclinic/app/MainActivity.kt`
**Class:** `MainActivity`
**Function:** `authenticateWithAccountPassword`, `checkAppLock`
**Lines:** 236–252 (`authenticateWithAccountPassword`), 188–189 and 202–203 (direct `auth.` session checks)

**Evidence:**
```kotlin
private val authViewModel: AuthViewModel by viewModels()   // MainActivity.kt:65 — AuthViewModel exists and is injected
...
@Inject lateinit var auth: Auth   // MainActivity.kt:48 — Supabase Auth SDK injected directly into the Activity
...
private fun authenticateWithAccountPassword(password: String) {
    val email = auth.currentSessionOrNull()?.user?.email
    if (email.isNullOrBlank()) {
        Toast.makeText(this, "No account email is available.", Toast.LENGTH_SHORT).show()
        return
    }
    lifecycleScope.launch {
        try {
            auth.signInWith(io.github.jan.supabase.auth.providers.builtin.Email) {
                this.email = email
                this.password = password
            }
            BiometricLockManager.unlockAfterKeystoreVerification()
        } catch (e: Exception) {
            Log.e("ACCOUNT_AUTH", "Account password authentication failed", e)
            BiometricLockManager.lock()
            Toast.makeText(this@MainActivity, "Incorrect account password.", Toast.LENGTH_SHORT).show()
        }
    }
}
```

**Dependency evidence:** `MainActivity` has both `AuthViewModel` (used elsewhere in the same file for `refreshSessionStatus()` and `awaitResolvedSessionStatus()`) *and* a directly-injected `Auth` (Supabase SDK) that it uses independently for a full password re-authentication network call.

**Why this violates the principle:** This is textbook "business logic in Activity/UI" — a real network authentication call, with its own try/catch, error messaging, and side effects (`BiometricLockManager.lock()/unlockAfterKeystoreVerification()`) — sitting in the Activity, while an `AuthViewModel` built for exactly this kind of orchestration is injected two lines away and simply not used for this operation. This makes the app-lock re-authentication path untestable independent of Android's `FragmentActivity` and the live Supabase SDK, and it duplicates whatever error-handling conventions `AuthViewModel` already establishes elsewhere.

**Recommended refactoring:** Move `authenticateWithAccountPassword`'s body into `AuthViewModel` (e.g., `suspend fun reauthenticateWithPassword(password: String): Result<Unit>`), and have `MainActivity` only call the ViewModel and react to the result (toast + `BiometricLockManager` calls can stay in the Activity since they're UI/framework concerns, not auth logic).

**Confidence:** High

---

## D. Medium / Low Findings

### [FINDING 14]
**Severity:** Medium
**Principle:** DI — inappropriate singleton / global mutable state / testability

**File:** `app/src/main/java/com/neochildclinic/core/utils/BiometricLockManager.kt` (whole file, 51 lines), consumed from `app/src/main/java/com/neochildclinic/features/settings/SecuritySettingsScreen.kt`
**Class:** `BiometricLockManager` (object)
**Function:** `setProtectionEnabled`
**Lines:** `BiometricLockManager.kt:7` (object declaration), `:9-12` (mutable state), `:26-33` (`setProtectionEnabled`); `SecuritySettingsScreen.kt:37, 45`

**Evidence:**
```kotlin
// BiometricLockManager.kt
object BiometricLockManager {
    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()
    private var screenWasOff = false
    private var protectionEnabled = true
    private var lastActiveTime: Long = System.currentTimeMillis()
    ...
    fun setProtectionEnabled(enabled: Boolean) {
        protectionEnabled = enabled
        ...
    }
```
```kotlin
// SecuritySettingsScreen.kt — a @Composable function
BiometricLockManager.setProtectionEnabled(true)
...
BiometricLockManager.setProtectionEnabled(false)
```

**Dependency evidence:** `BiometricLockManager` is a plain Kotlin `object` (process-wide singleton, not Hilt-managed), holding mutable state (`screenWasOff`, `protectionEnabled`, `lastActiveTime`) with no interface. It's referenced statically — not injected — from `MainActivity.kt`, `NeoChildApp.kt`, `BiometricAuthenticator.kt`, and directly from the `SecuritySettingsScreen` Composable.

**Why this violates the principle:** Global static mutable state is a textbook testability blocker: it can't be reset between tests without a manual `reset()` no such object here provides, it can't be substituted with a fake in a Compose Preview or a ViewModel unit test, and — the concrete UI issue — a Composable mutating it directly means the "current lock state" isn't a piece of `UiState` flowing from a ViewModel, it's ambient, hidden global state the screen reaches out and touches. Two different screens/activities can race on `protectionEnabled` with no synchronization visible at any call site.

**Recommended refactoring:** Wrap `BiometricLockManager`'s state behind a `@Singleton`-scoped, Hilt-injected class (or at minimum route all Composable interactions through a ViewModel method that then delegates to the object), so call sites depend on an injected reference rather than a static singleton.

**Confidence:** Medium (the design has real testability costs; severity is capped at Medium because the state genuinely is app-lifetime/process-wide by nature, which is one of the few legitimate use cases for a singleton — the issue is the *direct Composable access*, not the singleton's existence).

---

### [FINDING 15]
**Severity:** Medium
**Principle:** DRY (duplication)

**File:** `app/src/main/java/com/neochildclinic/data/repository/InventoryRepositoryImpl.kt`
**Class:** `InventoryRepositoryImpl`
**Function:** `deductStock` (Copy A) vs. `deductStockFromBatch` (Copy B)
**Lines:** Copy A: 461–524; Copy B: 533–581

**Evidence:**

*Copy A — `deductStock` (lines 461–524, excerpt):*
```kotlin
suspend fun deductStock(vaccineId: String, quantity: Int, user: String, transactionType: InventoryTransactionType, visitId: String? = null, patientId: String? = null) {
    val transactionGroupId = UUID.randomUUID().toString()
    database.withTransaction {
        ...
        vaccineDao.updateBatch(batch.deducted(deduct, transactionType, userName))
        val transaction = InventoryTransactionEntity(vaccineId = vaccineId, batchId = batch.batchId, ...)
        vaccineDao.insertTransaction(transaction)
        syncRepository.enqueue(entityName = "INVENTORY_TRANSACTION", entityId = transaction.transactionId, operation = SyncOperation.CREATE, priority = SyncPriority.HIGH, transactionGroupId = transactionGroupId)
        syncRepository.enqueue(entityName = "BATCH", entityId = batch.batchId, operation = SyncOperation.UPDATE, priority = SyncPriority.MEDIUM, transactionGroupId = transactionGroupId)
```

*Copy B — `deductStockFromBatch` (lines 533–581, excerpt):*
```kotlin
override suspend fun deductStockFromBatch(batchId: String, quantity: Int, user: String, transactionType: InventoryTransactionType, ...) {
    database.withTransaction {
        val batch = vaccineDao.getBatchById(batchId) ?: throw IllegalStateException("Batch not found")
        ...
        vaccineDao.updateBatch(batch.deducted(quantity, transactionType, userName))
        val transaction = buildStockTransaction(vaccineId = batch.vaccineId, batchId = batchId, ...)
        vaccineDao.insertTransaction(transaction)
        enqueueBatchStockChange(batchId, transaction.transactionId)
```

**Problem:** Both methods implement the same core sequence — validate available quantity, call `batch.deducted(...)`, persist via `vaccineDao.updateBatch`, build an `InventoryTransactionEntity`, insert it, enqueue sync ops for the transaction and the batch — as two independent implementations. `deductStock` operates FEFO across all of a vaccine's batches (looping `vaccineDao.getActiveBatchesByExpiry`); `deductStockFromBatch` operates on one caller-specified batch. The single-batch inner logic (validate → deduct → build transaction → insert → enqueue) is duplicated rather than `deductStock` calling `deductStockFromBatch` once per batch in its loop.

**Is the duplication harmful?** Yes: `deductStockFromBatch` has an expiry check (`allowExpired`/`givenDate` handling, lines ~547–555) that `deductStock` does not have at all. If that expiry rule is a genuine business requirement, `deductStock`'s FEFO path (used by `BorrowRepositoryImpl.kt:63` for borrow-out deductions) silently skips it — a correctness risk introduced *by* the duplication, not just a maintenance cost.

**Recommended refactoring:** Refactor `deductStock`'s per-batch loop body to call `deductStockFromBatch` for each batch instead of reimplementing the deduction+transaction+sync sequence, so the expiry rule (and any future rule change) applies uniformly.

**Confidence:** Medium (the overlap and the missing expiry-check inconsistency are directly evidenced; whether the missing check is an actual bug depends on business intent not stated in the code, so confidence on "duplication exists" is High but on "this specific consequence is a functional bug" is Medium).

---

### [FINDING 16]
**Severity:** High
**Principle:** SRP / Cohesion (excessive method length and mixed responsibilities) / Testability

**File:** `app/src/main/java/com/neochildclinic/data/repository/SyncRepositoryImpl.kt`
**Class:** `SyncRepositoryImpl`
**Function:** `processNextItems`
**Lines:** 88–314 (approx. 226 lines; bounded by the next function `requiresSessionRefresh` starting at line 317)

**Evidence (structure, not full reproduction given length):**
```kotlin
suspend fun processNextItems() {
    syncDao.cleanCorruptedItems()
    syncDao.requeueStaleSyncingItems(...)
    ...
    val sessionResolved = awaitSessionResolved(auth.sessionStatus, SESSION_RESOLVE_TIMEOUT_MS)
    val currentSession = auth.currentSessionOrNull()
    if (currentSession == null) { /* ~20 lines of session-state branching */ }
    when (ensureAuthenticatedSession()) { /* 3-way branch, ~15 lines */ }
    val rawGroups = pending.groupBy { ... }
    fun sortKey(item: SyncQueueEntity): Int { ... }
    val groups = rawGroups.entries.sortedWith(...).associate { ... }   // ~25 lines of ordering logic
    for ((groupId, groupItems) in groups) {
        try {
            database.withTransaction { for (item in groupItems) syncDao.updateStatus(...) }
            val remoteConflictData = uploader.fetchRemoteConflictData(...)
            for (item in groupItems) {
                try { uploader.uploadEntity(item, remoteConflictData) }
                catch (e: Exception) { if (!requiresSessionRefresh(e)) throw e; handleSessionRefreshOn401(e); uploader.uploadEntity(item, remoteConflictData) }
                uploader.markUploaded(item); syncDao.deleteItem(item)
            }
        } catch (e: SessionAuthTransientException) { /* ~6 lines */ }
        catch (e: Exception) { /* ~15 lines of transient-vs-permanent retry classification */ }
    }
    if (anyTransientRetry) syncManager.scheduleSync()
    if (sessionTransient) { ...; return }
    _syncState.value = if (hasError) SyncState.ERROR else SyncState.IDLE
    if (syncDao.getPendingCountSync() > 0 && !hasError) processNextItems()   // recursive self-call
}
```

**Responsibility evidence — distinct concerns present in one method:**
1. Queue hygiene (`cleanCorruptedItems`, `requeueStaleSyncingItems`)
2. Session/auth readiness gating (three separate return paths for different session states)
3. Batch grouping strategy (group-by-transaction, then priority + creation-time sort)
4. Per-group transactional status marking
5. Per-item upload with inline 401-refresh-and-retry logic
6. Error classification (transient vs. permanent vs. session-transient) and per-item retry bookkeeping
7. Sync state (`_syncState`) management
8. Recursive continuation / drain-the-queue control flow

**Why this violates the principle:** Eight distinct concerns in a single 226-line function is a genuine cohesion problem, independent of any code-quality opinion: each concern above changes for a different reason (a new retry policy, a new grouping rule, a new auth edge case), meaning this one function has at least eight reasons to change — the definition of an SRP violation applied to a method rather than a class. Practically, it cannot be unit-tested piecewise: testing "does a transient error get retried" requires exercising the full session-resolution and grouping logic first, since they're not separable calls.

**Recommended refactoring:** Extract named private methods along the seams already visible in the code's own comments: `resolveSessionOrDefer(): SessionOutcome`, `orderPendingIntoGroups(pending): Map<String, List<SyncQueueEntity>>`, `processGroup(groupId, items): GroupResult`, `classifyAndRecordError(items, e)`. `processNextItems` itself becomes an orchestrator calling these in sequence — behavior-preserving, since no logic needs to change, only its organization.

**Confidence:** High

---

### [FINDING 17]
**Severity:** Medium
**Principle:** Open/Closed Principle (OCP) / DRY

**File:** `app/src/main/java/com/neochildclinic/data/repository/SyncUploader.kt`, `app/src/main/java/com/neochildclinic/data/repository/SyncRepositoryImpl.kt`
**Class:** `SyncUploader`, `SyncRepositoryImpl`
**Function:** `entityTable`, `fetchEntityData`, `getEntityUpdatedAt`, `markUploaded` (all in `SyncUploader`), `getEntityPriority` (in `SyncRepositoryImpl`)
**Lines:** `SyncUploader.kt` 23–44 (`entityTable`), 451–481 (`fetchEntityData`); `SyncRepositoryImpl.kt` 457–468 (`getEntityPriority`)

**Evidence:**
```kotlin
// SyncUploader.kt:23-44
private fun entityTable(entityName: String): String? = when (entityName) {
    "PATIENT" -> "patients"
    "VACCINATION", "VISIT" -> "patient_visits"
    "VACCINATION_ITEM" -> "vaccination_items"
    ... 17 more branches ...
    else -> null
}
```
```kotlin
// SyncUploader.kt:451-481
suspend fun fetchEntityData(item: SyncQueueEntity): Any? {
    return try {
        when (item.entityName) {
            "PATIENT" -> { val entity = database.patientDao().getPatientById(entityId); ... }
            "VACCINATION", "VISIT" -> database.vaccinationDao().getVaccinationById(entityId)
            ... 16 more branches, each naming a different DAO ...
            else -> null
        }
    } catch (e: Exception) { ... null }
}
```
```kotlin
// SyncRepositoryImpl.kt:457-468
private fun getEntityPriority(entityName: String): Int {
    return when (entityName) {
        "PATIENT", "VACCINE" -> 1
        "VACCINATION", "VISIT", "BATCH" -> 2
        ... 6 more branches ...
        else -> 100
    }
}
```

**Problem:** The set of ~20 syncable entity names is enumerated as raw `String` literals across (at least) five separate `when` blocks in two files: `entityTable`, `fetchEntityData`, `getEntityUpdatedAt`, and `markUploaded` in `SyncUploader.kt`, plus `getEntityPriority` in `SyncRepositoryImpl.kt`. There is no single source of truth (sealed class, enum, or registry) for "what is a syncable entity and how do I read/write/prioritize it."

**Why this violates the principle:** Adding a new syncable entity (a realistic, recurring need in this codebase — it's already happened ~20 times) requires editing five call sites correctly and consistently; missing one doesn't fail to compile — it fails silently or with a generic exception at runtime (`entityTable` returning `null` throws `IllegalArgumentException` deep inside `uploadEntity`, while a missed `getEntityPriority` branch silently defaults to priority `100`, potentially breaking the FK-ordering invariant the extensive comments in `processNextItems` describe protecting). This is precisely OCP's target failure mode: the module is not closed for modification when the plausible future change (a new entity) arrives.

**Recommended refactoring:** Introduce a small `SyncableEntity` sealed class or a `Map<String, SyncEntityDescriptor>` registry (table name, DAO accessor as a function reference, updated-at extractor, priority) built once, so a new entity is added in one place and the `when` blocks become lookups against it.

**Confidence:** High

---

### [FINDING 18]
**Severity:** Low
**Principle:** SRP / Constructor over-injection (consequence of Findings 1–6, stated separately because the count itself has an independent cost)

**File:** `app/src/main/java/com/neochildclinic/features/vaccination/AddVaccinationViewModel.kt`
**Class:** `AddVaccinationViewModel`
**Function:** constructor
**Lines:** 73–81

**Evidence:**
```kotlin
@HiltViewModel
class AddVaccinationViewModel @Inject constructor(
    private val patientRepository: PatientRepositoryImpl,
    private val inventoryRepository: InventoryRepositoryImpl,
    private val vaccinationRepository: VaccinationRepositoryImpl,
    private val reminderRepository: ReminderRepositoryImpl,
    private val profileRepository: com.neochildclinic.data.repository.ProfileRepositoryImpl,
    private val clinicalService: ClinicalVaccinationService,
    private val vaccinationEditEngine: VaccinationEditEngine,
    private val sessionManager: SessionManager,
    private val getAvailableSlotsUseCase: GetAvailableSlotsUseCase
) : ViewModel() {
```

**Problem:** 9 constructor dependencies (versus `DashboardViewModel`'s 12 in Finding 1). Note line 77 even uses a fully-qualified `com.neochildclinic.data.repository.ProfileRepositoryImpl` type inline rather than an import — a minor but telling sign of how routine importing concrete repository classes has become in this codebase.

**Why this is a problem distinct from Findings 1/2:** Beyond the DIP angle already covered, 9+ dependencies is itself a maintainability/readability cost independent of whether they're interfaces or concretions: every test of this ViewModel needs 9 doubles constructed and wired, and every new contributor reading this file's constructor has to hold 9 collaborators in mind before reaching the first line of actual logic.

**Recommended refactoring:** Beyond retyping to interfaces (Finding 1's fix), consider whether `patientRepository`, `inventoryRepository`, `vaccinationRepository`, `reminderRepository`, and `profileRepository` are all *directly* needed here versus being reachable through `clinicalService`/`vaccinationEditEngine`, which already aggregate several of them (see `ClinicalVaccinationService`'s own constructor). `loadPatient()` and `fetchInventory()`/`fetchDoctors()` do need direct repository reads that a write-oriented service wouldn't expose, so full consolidation may not be possible — but it's worth checking whether `profileRepository` (used only to load the doctor list, likely) could be sourced through `getAvailableSlotsUseCase` or a small `LoadDoctorsUseCase` instead.

**Confidence:** Medium (the dependency count and its cost are directly evidenced; the specific consolidation recommendation is a judgment call flagged as such, not asserted as the only fix).

---

## E. Coupling Report

| # | Coupling type (per audit brief) | Evidence | Finding |
|---|---|---|---|
| 1 | ViewModel → concrete Repository (not interface) | `DashboardViewModel`, 29/34 ViewModels | 1, 2, 18 |
| 2 | Repository → unrelated feature (concrete) | `BorrowRepositoryImpl` → `InventoryRepositoryImpl` | 5 |
| 3 | Repository → Repository (concrete, universal) | All 11 repos → `SyncRepositoryImpl` | 6 |
| 4 | Domain service → Infrastructure (Room DAOs directly) | `ClinicalVaccinationService`, `VaccinationEditEngine` | 8, 9 |
| 5 | UI (Composable) → third-party SDK type | `PatientRecordCards`, `PatientInfoComponents` ← `FileObject` | 11 |
| 6 | UI (Composable) → Data-layer entity | 16 files incl. `VaccineInventoryScreen` | 12 |
| 7 | UI (Activity) → Network SDK, bypassing existing ViewModel | `MainActivity` → `Auth.signInWith` | 13 |
| 8 | UI (Composable) → global singleton (hidden dependency) | `SecuritySettingsScreen` → `BiometricLockManager` | 14 |
| 9 | Domain repository interface → Data-layer entity | `FinanceRepository`, `ReminderRepository`, `VaccinationRepository` | 10 |

**Not found (checked, no evidence):** UI → database (direct DAO/`AppDatabase` access from a Composable or ViewModel) — the only direct `AppDatabase` reference in `features/**` is `VaccineWidget.kt`, which is a Glance App Widget provider, not a Compose screen or ViewModel; widgets legitimately have no ViewModel to route through in the same way, so this is **not classified as a violation**, though it is worth confirming `VaccineWidget` doesn't duplicate repository logic (out of scope for this pass — recommend a follow-up read of that one file).

---

## F. Cohesion Report

- **`SyncRepositoryImpl.processNextItems`** (Finding 16): 8 distinct, independently-changing responsibilities in one 226-line method. This is the clearest genuine cohesion failure found — not merely "a large function" (per the audit's own instruction not to flag size alone) but one where the responsibilities are demonstrably unrelated (queue hygiene vs. auth-session gating vs. FK-safe ordering vs. per-item retry policy).
- **`InventoryRepositoryImpl`** (748 lines, Finding 3): large, but on inspection its ~22 methods are all genuinely about one cohesive concern — inventory state (vaccines, batches, transactions, deductions). This is **not classified as a God class**; its problem is an incomplete public *contract* (Finding 3), not incoherent responsibilities. Per the audit's Rule ("do not call a class a God class simply because it is large"), no cohesion finding is raised against it.
- **`AddVaccinationViewModel`** (699 lines): responsibilities are UI-state mutation (row add/remove, field updates) and one orchestration method (`saveVaccination`) that delegates the actual business logic to `ClinicalVaccinationService`/`VaccinationEditEngine`. This is a reasonable ViewModel shape for a complex form; no cohesion finding raised beyond the dependency-count concern already in Finding 18.

---

## G. Dependency-Direction Report

Actual, observed dependency direction, per the codebase's own DI graph:

```
Presentation (features/*)
    │  ✗ 29/34 ViewModels skip the interface and go straight to concrete Impl  (Findings 1–2, 6)
    │  ✗ 16 files import Room entities directly                                (Finding 12)
    │  ✗ 2 files (ViewModel + 2 Composables) import a Supabase SDK model class  (Finding 11)
    │  ✗ MainActivity calls Supabase Auth SDK directly, bypassing AuthViewModel (Finding 13)
    ▼
Domain (domain/*)
    │  ✓ Use cases (MergePatientsUseCase, GetAvailableSlotsUseCase, SearchPatientsUseCase) — correct
    │  ✓ ClinicStatsManager, ConsultationEditEngine, EditReconciler — correct
    │  ✗ ClinicalVaccinationService, VaccinationEditEngine call Room DAOs directly (Findings 8–9)
    │  ✗ 3 domain repository *interfaces* import Room entities (documented trade-off, Finding 10)
    ▼
Data (data/repository, data/local, data/manager)
    │  ✗ 6 repositories have no interface / no DI binding (Finding 4)
    │  ✗ Every repository impl depends on SyncRepositoryImpl concretely, not SyncRepository (Finding 6)
    │  ✗ SyncRepositoryImpl manually constructs SyncUploader instead of injecting it (Finding 7)
    ▼
Infrastructure (Room, Supabase SDK, WorkManager, Firebase)
```

The direction is correct in the domain use-case layer and badly inverted at both the presentation↔domain seam and, universally, at the sync-dependency edge of the data layer.

---

## H. Unnecessary Connections

- `MainActivity` holding a direct `Auth` reference *in addition to* `AuthViewModel` (Finding 13) is an unnecessary connection: the Activity has two independent paths to the same authentication state/capability, and they aren't kept in sync by any visible mechanism (the Activity's own `auth.signInWith` call doesn't go through `authViewModel`'s state at all).
- `SecuritySettingsScreen` connecting directly to `BiometricLockManager` (Finding 14) when the screen otherwise presumably has (or should have) a ViewModel for its other settings.

---

## I. Unnecessary Abstractions

- **None found with clear evidence of adding no value.** `SyncManagerImpl` (WorkManager scheduling wrapper, `data/manager/SyncManagerImpl.kt`) has no interface despite the "Impl" suffix implying one should exist — but it is single-purpose (schedule/cancel WorkManager jobs) and used consistently; giving it an interface would be speculative generality without a second implementation or a test that needs to fake WorkManager scheduling specifically. **Classified as Not a problem / Do Not Change** per the audit's own Rule 5, though the naming (`...Impl` with no interface) is inconsistent with the rest of the codebase's convention and could be renamed to `SyncScheduler` for clarity — a naming nit, not a design finding.
- `CloudRefresh.kt`'s single top-level function `cloudRefresh(tag, rethrow, block)` (20 lines) is a thin wrapper (IO dispatch + log + optional rethrow), but it is reused across at least 3 repositories (`InventoryRepositoryImpl.refreshInventory`, `FinanceRepositoryImpl.refreshTransactions`, and likely others per its doc comment "Shared wrapper for repository pull-from-Cloud refresh"). Reused, named, documented, non-trivial control flow (try/catch/log/conditional-rethrow) — this earns its place. **Not a problem.**

---

## J. Missing Abstractions

- `DocumentRepository`, `ProfileRepository`, `StaffManagementRepository`, `AuditLogRepository`, `BorrowRepository`, `DeviceRepository` interfaces — see Finding 4. Each has a concrete design problem attached (forced concrete coupling at every call site), satisfying the audit's requirement to only report missing abstractions that solve a real problem.
- A `PatientDocument` domain model to replace the leaked `FileObject` — see Finding 11.
- A syncable-entity registry/sealed type to replace the five parallel `String`-keyed `when` blocks — see Finding 17.

---

## K. Business-Logic Ownership Report

Traced per the audit brief's specific areas:

- **Vaccination creation:** `AddVaccinationViewModel.saveVaccination()` (UI-state validation only) → `ClinicalVaccinationService.recordVaccination()` (single `database.withTransaction` covering: vaccination record, finance income, reminder satisfaction, inventory deduction). Ownership is correctly centralized in `ClinicalVaccinationService`, **except** that the inventory-deduction *audit ledger* write bypasses `InventoryRepository` (Finding 8).
- **Vaccination editing:** `AddVaccinationViewModel.saveVaccination()` → `VaccinationEditEngine.execute()`, which diffs old/new state (`inventoryDiff`, `financeChanged`) and only applies side effects where something actually changed. This is a well-owned, single-location business rule (see Good Design #6/#7), with the same DAO-bypass caveat as above (Finding 9).
- **Vaccination deletion:** `VaccinationRepositoryImpl.deleteVaccination()` — a single transaction reversing inventory (from the deduction ledger, explicitly *not* the denormalized batch-ID string, per an inline comment explaining why), soft-deleting reminders/items/finance/visit in sequence, and enqueuing sync ops per row. Ownership is centralized and well-reasoned (Good Design #9).
- **Borrowed vaccines / returns:** Owned by `BorrowRepositoryImpl`, which — per Finding 5 — must reach into `InventoryRepositoryImpl` concretely to do so, because the borrow/return stock operations were never promoted to `InventoryRepository`.
- **Reminders / due vaccinations:** Computation is owned by `ReminderDueListProcessor` (a dedicated object, not inspected line-by-line in this pass but confirmed to exist as the single call site for due-list processing inside `ReminderRepositoryImpl`, lines 89 and 121) — this looks like an appropriately isolated calculation and is a good-design candidate for a follow-up deeper read.
- **Statistics/finance:** Correctly centralized in the pure `FinanceCalculator` object (Good Design #1) and the interface-driven `ClinicStatsManager` (Good Design #3) — no duplicated calculation logic was found elsewhere; `FinanceCalculator` is reused from 25 call sites across `features/**`, which is strong evidence against duplication for this specific concern.
- **Synchronization:** Owned by `SyncRepositoryImpl`/`SyncUploader`, with the cohesion (Finding 16) and OCP (Finding 17) issues noted above, but no duplicated sync logic was found — there is exactly one upload path and one download path (`SyncUploader.downloadAndReplaceLocal`), not multiple competing implementations.

No case of the *same* business rule being implemented twice in different places was found for vaccination, inventory, reminders, or finance — the ownership problems found are about the rules living at the *wrong layer* (Findings 8–9) or being *unreachable through the intended abstraction* (Findings 3–5), not about duplicated/conflicting rules. The one true logic duplication found is infrastructural (Finding 15, inventory deduction), not a business-rule duplication.

---

## L. Duplication Report

| Duplication | Copy A | Copy B | Harmful? |
|---|---|---|---|
| Batch stock deduction + transaction + sync-enqueue | `InventoryRepositoryImpl.deductStock` (461–524) | `InventoryRepositoryImpl.deductStockFromBatch` (533–581) | Yes — see Finding 15 (missing expiry check in Copy A) |
| Syncable-entity enumeration | `SyncUploader.entityTable` (23–44) | `SyncUploader.fetchEntityData` (451–481), `SyncRepositoryImpl.getEntityPriority` (457–468), plus `getEntityUpdatedAt`/`markUploaded` (not fully reproduced above) | Yes — see Finding 17 (silent drift risk) |

**Explicitly NOT duplicated (checked, confirmed reused correctly):**
- `InventoryRepositoryImpl.addStockBatch` (multi-batch submission, lines 223–301) calls `addBatch` (lines 179–216) internally per batch rather than reimplementing it — a **good** DRY example (Good Design #8).
- `GetAvailableSlotsUseCase` explicitly centralizes availability-slot calculation that its own doc comment says was previously duplicated across three call sites (Add Consultation, Add Vaccination, Today's Patient) — the class exists *because* someone already fixed this duplication (Good Design #5).
- `FinanceCalculator` — reused from 25 locations; no inline reimplementation of finance math was found elsewhere in the features it's used from.

---

## M. Good Design Decisions

### [GOOD DESIGN 1]
**File:** `app/src/main/java/com/neochildclinic/domain/statistics/FinanceCalculator.kt`
**Class:** `FinanceCalculator`
**Function:** whole object
**Lines:** 40 (declaration) onward

**Evidence:**
```kotlin
object FinanceCalculator {
    fun resolveReportingDate(transaction: FinanceEntity, visitDates: Map<String, String>? = null): String { ... }
    fun calculateFinanceStats(...): FinanceStats { ... }
    fun getMonthlyGroupedData(...) { ... }
    ...
}
```
**Principle:** Pure functions / SRP / testability
**Why it is good:** A stateless `object` with no constructor dependencies, no I/O, and no side effects — every function is a pure transformation of its inputs. This is maximally testable (no mocks needed at all) and is in fact reused from 25 separate call sites across `features/**` without any observed reimplementation, making it the codebase's strongest example of DRY business logic.

---

### [GOOD DESIGN 2]
**File:** `app/src/main/java/com/neochildclinic/data/manager/RealtimeChangeSubscriptions.kt`
**Class:** `RealtimeChangeSubscriptions`
**Function:** `tableChanges`
**Lines:** whole file, 72 lines

**Evidence:** (see file; `callbackFlow` wrapping Supabase's `Realtime` channel API, with subscription cleanup tied to `awaitClose`)
**Principle:** Encapsulation / Adapter pattern
**Why it is good:** The class's own doc comment states its intent precisely: *"Bridges Supabase Realtime into a Flow\<Unit\> so feature layers never touch the channel API."* It delivers on that: no other file in the codebase imports `io.github.jan.supabase.realtime.*` directly. Channel lifecycle is tied to Flow collection via `awaitClose`, eliminating a whole class of manual-cleanup bugs. This is a well-scoped infrastructure adapter, even though (as noted in Finding evidence gathering) it is injected directly into ViewModels rather than behind a domain interface — a minor, low-severity gap that doesn't undermine the class's own design quality.

---

### [GOOD DESIGN 3]
**File:** `app/src/main/java/com/neochildclinic/domain/manager/ClinicStatsManager.kt`
**Class:** `ClinicStatsManager`
**Function:** `getClinicStats`
**Lines:** 27–33 (constructor), 37–99 (function)

**Principle:** Aggregator/Facade pattern via interfaces, reactive composition
**Why it is good:** Depends exclusively on four domain repository *interfaces* (`VaccinationRepository`, `ReminderRepository`, `InventoryRepository`, `FinanceRepository`) — none of the concrete-class coupling seen elsewhere in the codebase. Uses `combine(...)` to reactively recompute statistics whenever any underlying stream changes, rather than polling or manually re-triggering. This is the correct shape for a cross-cutting aggregation manager and directly contradicts any argument that the concrete-Impl coupling seen in ViewModels (Findings 1–2) is somehow necessary — this class does similar multi-repository orchestration with interfaces alone.

---

### [GOOD DESIGN 4]
**File:** `app/src/main/java/com/neochildclinic/domain/usecase/patient/MergePatientsUseCase.kt`
**Class:** `MergePatientsUseCase`
**Function:** whole class, `invoke`
**Lines:** 20–28 (constructor), 29 onward

**Principle:** Use-case pattern, DIP
**Why it is good:** Orchestrates a genuinely complex, multi-repository, transactional business workflow (merging duplicate patient records across vaccinations, reminders, audit trail, and inventory transactions) using **only** domain interfaces (`PatientRepository`, `VaccinationRepository`, `ReminderRepository`, `InventoryRepository`, `SyncRepository`) plus `AuditLogger`. The only concrete infrastructure dependency is `AppDatabase`, used solely for the `withTransaction` boundary — not for direct DAO access. This is the template the rest of the ViewModel layer should follow (see the Refactoring Roadmap).

---

### [GOOD DESIGN 5]
**File:** `app/src/main/java/com/neochildclinic/domain/usecase/doctor/GetAvailableSlotsUseCase.kt`
**Class:** `GetAvailableSlotsUseCase`
**Function:** whole class
**Lines:** 1–25 (header/doc comment)

**Evidence:**
```kotlin
/**
 * Single, centralized implementation of the availability calculation (req. 11):
 * ...
 * Add Consultation, Add Vaccination and the Today's Patient quick-add dialog all call
 * this same use case rather than each re-implementing the weekly/exception merge logic.
 */
class GetAvailableSlotsUseCase @Inject constructor(
    private val repository: DoctorAvailabilityRepository
) { ... }
```
**Principle:** DRY, SRP, single interface dependency
**Why it is good:** The doc comment itself documents a *deliberate deduplication* — proof the team actively recognizes and fixes duplication when found (contrast with Finding 15, which is a case that hasn't received this treatment yet). Confirmed by this audit: no reimplementation of slot-availability logic was found in `AddConsultationViewModel`, `AddVaccinationViewModel`, or the Today's-Patient dialog — all three genuinely call this one use case.

---

### [GOOD DESIGN 6]
**File:** `app/src/main/java/com/neochildclinic/domain/service/EditReconciler.kt`
**Class:** `EditReconciler`
**Function:** `classifyItems`, `classifyReminders`
**Lines:** whole file, 149 lines

**Principle:** Pure function / testability
**Why it is good:** A stateless `object` implementing a non-trivial diffing algorithm (matching old vs. edited rows by ID, classifying each as keep/change/remove/add) with zero side effects and zero infrastructure dependencies. The file's own comment states it is "covered by EditReconcilerTest" — genuinely unit-testable business logic, extracted from the transactional orchestration that consumes it (`VaccinationEditEngine`).

---

### [GOOD DESIGN 7]
**File:** `app/src/main/java/com/neochildclinic/domain/service/ConsultationEditEngine.kt`
**Class:** `ConsultationEditEngine`
**Function:** `edit`
**Lines:** whole file, 54 lines

**Principle:** Clean Architecture (correct contrast case to Findings 8–9)
**Why it is good:** Performs the same *kind* of work as `ClinicalVaccinationService`/`VaccinationEditEngine` (transactional multi-repository orchestration for an edit operation) but does so using **only** its injected `ConsultationRepository`/`FinanceRepository` interfaces plus `database.withTransaction` for the transaction boundary — no direct DAO calls anywhere in the file. This is direct, in-codebase proof that the DAO-bypass in Findings 8–9 is avoidable with the existing interfaces and is not an inherent limitation of the domain-service pattern used throughout this app.

---

### [GOOD DESIGN 8]
**File:** `app/src/main/java/com/neochildclinic/data/repository/InventoryRepositoryImpl.kt`
**Class:** `InventoryRepositoryImpl`
**Function:** `addStockBatch` calling `addBatch`
**Lines:** 223–301 (calls `addBatch` internally around line 273, per the method's own comment: *"Reuses the existing single-batch save path so batch insert, the PURCHASE inventory_transaction, audit log, and sync queue entries stay identical to a normal Add Batch save."*)

**Principle:** DRY
**Why it is good:** A multi-batch bulk-add operation reuses the single-batch method rather than reimplementing batch-insert + transaction-log + audit + sync-enqueue a second time, and does so inside one outer `database.withTransaction` so the whole submission is atomic. This is the correct pattern that Finding 15 (`deductStock`/`deductStockFromBatch`) should have followed but didn't.

---

### [GOOD DESIGN 9]
**File:** `app/src/main/java/com/neochildclinic/data/repository/VaccinationRepositoryImpl.kt`
**Class:** `VaccinationRepositoryImpl`
**Function:** `deleteVaccination`
**Lines:** 360–479

**Principle:** Transaction boundary correctness, business-rule ownership (deletion workflow)
**Why it is good:** A single `database.withTransaction` block correctly sequences: (1) reverse inventory from the *deduction audit ledger* specifically — with an inline comment explaining exactly why the ledger is authoritative over the denormalized batch-ID string — (2) clean up that ledger, (3) soft-delete dependent reminders with sync enqueue, (4) soft-delete line items, (5) soft-delete the linked finance record, (6) soft-delete the visit itself, in an order chosen specifically to avoid FK violations both locally and (per the ordering logic documented in `SyncRepositoryImpl`) at Supabase. The comments throughout show this ordering was arrived at through actual production incidents (e.g., the FK-violation comment in `processNextItems`, Finding 16's evidence), not guessed — a sign of a codebase learning from real failures and encoding the fix directly at the point of risk.

---

## N. Refactoring Roadmap

Prioritized by evidenced impact (testability + blast radius), not by finding count.

### 1. Widen `InventoryRepository` and add the 6 missing interfaces
**Files affected:** `domain/repository/InventoryRepository.kt`, six new interface files, `di/RepositoryModule.kt`
**Current design:** Finding 3, Finding 4
**Evidence:** Interface exposes 7/~22 methods; six Impls have no interface at all.
**Proposed design:** Full interface parity with each Impl's actual public surface; add matching `@Binds`.
**Why:** This is the root cause blocking the fix for Findings 1, 2, 5, 6, 12, 18 — nearly every other finding either depends on this or is made easier by it.
**Risk:** Low — additive interface changes, no behavior change. Compile-time verification catches every affected call site.
**Expected benefit:** Unblocks retyping ~29 ViewModel constructors and `BorrowRepositoryImpl`'s cross-repository dependency to interfaces, which is the single largest testability improvement available in this codebase.

### 2. Retype all repository-layer `SyncRepositoryImpl` params to `SyncRepository`
**Files affected:** All 11 files listed in Finding 6
**Current design:** Finding 6
**Evidence:** Universal concrete-class injection despite a bound, unused-for-this-purpose interface.
**Proposed design:** `private val syncRepository: SyncRepository`
**Why:** Same interface already exists and is correctly used by 3 domain-layer classes — this is a pure type-signature change.
**Risk:** Very low — mechanical, one line per file, compiler-verified.
**Expected benefit:** Immediately removes the single most-repeated concrete dependency in the codebase.

### 3. Retype ViewModel constructors to interfaces (Findings 1, 2, 18)
**Files affected:** 29 ViewModel files
**Current design:** Concrete `...Impl` constructor parameters.
**Proposed design:** Domain interface parameters, following item 1's widened interfaces.
**Why:** Makes ViewModel unit testing possible with plain interface fakes instead of constructing real Room/Supabase-backed Impls.
**Risk:** Low-Medium — a handful of ViewModels may call an Impl-only method not yet covered even after item 1; the compiler will surface exactly which, turning this into a checklist rather than a guessing exercise.
**Expected benefit:** Largest single testability win in the codebase; also collapses `AddVaccinationViewModel`/`DashboardViewModel`'s dependency counts' *conceptual* weight even though the count itself is unchanged (Finding 18 remains a secondary follow-up).

### 4. Route `ClinicalVaccinationService`/`VaccinationEditEngine`'s DAO calls through their own injected interfaces
**Files affected:** `domain/service/ClinicalVaccinationService.kt`, `domain/service/VaccinationEditEngine.kt`, `domain/repository/InventoryRepository.kt`, `domain/repository/VaccinationRepository.kt`
**Current design:** Finding 8, 9 — direct `database.inventoryDeductionDao()`/`vaccinationDao()` calls.
**Proposed design:** Add the two or three specific methods these classes need (insert/delete deduction-ledger rows, insert a visit row) to the relevant interfaces; call through them instead.
**Why:** `ConsultationEditEngine` (Good Design #7) proves this is achievable with no loss of transactional correctness.
**Risk:** Low — the transaction boundary (`database.withTransaction`) stays exactly where it is; only the leaf calls inside it change from DAO to repository-interface calls.
**Expected benefit:** Closes the only Clean-Architecture bypass found in the domain layer proper.

### 5. Fix the `FileObject` leak
**Files affected:** `data/repository/DocumentRepositoryImpl.kt` (+ its new interface from item 1), `features/patient/PatientViewModel.kt`, `PatientRecordCards.kt`, `PatientInfoComponents.kt`
**Current design:** Finding 11.
**Proposed design:** `PatientDocument` domain model + mapper.
**Why:** This is the single clearest boundary violation in the whole audit and touches user-facing Compose code that would otherwise need the Supabase SDK on its classpath forever.
**Risk:** Low — narrow blast radius (4 files).
**Expected benefit:** Removes the only third-party-SDK-in-UI instance found.

### 6. Decompose `SyncRepositoryImpl.processNextItems`
**Files affected:** `data/repository/SyncRepositoryImpl.kt`
**Current design:** Finding 16.
**Proposed design:** Extract `resolveSessionOrDefer`, `orderPendingIntoGroups`, `processGroup`, `classifyAndRecordError` as named private methods; keep behavior identical.
**Why:** Unblocks meaningful unit testing of the sync retry/ordering policy, which currently cannot be exercised piecewise.
**Risk:** Medium — this function has the highest density of hard-won, incident-driven logic in the codebase (see its comments); refactor must be done with characterization tests written first, not from memory of what the code "should" do.
**Expected benefit:** Makes the app's most operationally critical, least-tested code path testable.

### 7. Replace the 5-location entity-name switch with a registry
**Files affected:** `data/repository/SyncUploader.kt`, `data/repository/SyncRepositoryImpl.kt`
**Current design:** Finding 17.
**Proposed design:** `SyncEntityDescriptor` map/sealed type.
**Why:** Removes the silent-failure mode where a forgotten branch degrades behavior instead of failing to compile.
**Risk:** Low-Medium (touches the same file as item 6; sequence after item 6 to avoid conflict).
**Expected benefit:** Makes adding the next syncable entity (which has happened ~20 times already) a one-place change.

### 8. Move `MainActivity`'s direct auth call into `AuthViewModel`
**Files affected:** `app/MainActivity.kt`, `core/session/AuthViewModel.kt`
**Current design:** Finding 13.
**Proposed design:** `AuthViewModel.reauthenticateWithPassword(password): Result<Unit>`, called from `MainActivity`.
**Risk:** Low.
**Expected benefit:** Removes the codebase's clearest business-logic-in-Activity instance.

---

## O. Final Conclusion

**Does this application currently follow good software design principles?**

Partially, and unevenly by layer. The **domain use-case layer** (`MergePatientsUseCase`, `GetAvailableSlotsUseCase`, `SearchPatientsUseCase`), the **pure calculators** (`FinanceCalculator`, `EditReconciler`), and at least one of the three edit-orchestration services (`ConsultationEditEngine`) demonstrate real command of Clean Architecture and SOLID: interface-only dependencies, single responsibilities, and — in `GetAvailableSlotsUseCase`'s case — documented, deliberate deduplication of logic that used to be scattered across three screens.

That discipline does not extend to the **ViewModel layer**, where depending on concrete repository implementations is the norm (29/34 files) rather than the exception, nor to the **repository layer's own internal wiring**, where every single repository depends on a concrete sync-writer class instead of the interface built for it. The evidence traces this to a specific, fixable root cause rather than a vague "the architecture is bad": several domain-repository interfaces are simply incomplete relative to their implementations (Finding 3), and six repositories were never given interfaces at all (Finding 4) — both mechanical gaps, not fundamental design flaws requiring a rewrite.

Two domain-layer services also reach past their own correctly-injected repository interfaces to call Room DAOs directly (Findings 8–9) — and the codebase's own sibling class, `ConsultationEditEngine`, proves in the same file layout that this bypass is not necessary to get the transactional behavior these services need.

The most severe single piece of evidence — a Supabase SDK type surfacing unmapped in Compose UI function signatures (Finding 11) — is narrow in scope (four files) but is the clearest possible illustration that the architectural boundary the folder structure implies is not, in practice, enforced by the dependency graph.

In short: the team has demonstrated, in multiple places in its own codebase, that it knows how to do this correctly. The gap between that capability and the codebase's dominant pattern (concrete-class coupling throughout the ViewModel and repository layers) is the actual finding of this audit — and it is closeable with the mechanical, low-risk, compiler-verified refactors listed in Section N, rather than requiring architectural rework.
