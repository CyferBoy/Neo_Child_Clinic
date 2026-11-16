# MEMORY.md — Neo Child Clinic Codebase Untangling Handoff

> **Last Updated**: 2026-11-16T11:21:00+05:30  
> **Git Head**: Commit `aa8b84e` on `main` (synced with `origin/main`)  
> **Working Tree**: Clean (`git status` shows 0 uncommitted changes)  
> **Active Goal**: Complete `/teamwork /untangle-codebase` structural refactor across 5 milestones.

---

## 1. Project & Architecture Snapshot

- **Project Path**: `/home/nadeem/Desktop/vaccine_manager_app`
- **Application Type**: Android Healthcare / Pediatric Clinic (Kotlin, Jetpack Compose, Room SQLite with SQLCipher, WorkManager, Hilt, Supabase, Cloudflare Workers).
- **Hard Invariant 1 (Clean Architecture)**: `com.neochildclinic.core.*` must NEVER import `feature.*` or `app.*`. Verified: 0 violations.
- **Hard Invariant 2 (DI Location)**: Application Hilt DI modules reside in `com.neochildclinic.di` (not `core/di`).
- **Hard Invariant 3 (Offline-First Sync)**: SQLite outbox sync via `sync_queue` table with single-flight execution, transaction grouping, and idempotency.

---

## 2. Milestone Progress Tracker

| Milestone | Scope | Description | Status | Evidence / Commit |
|:---|:---|:---|:---:|:---|
| **M1** | UNTANGLE-01 – UNTANGLE-08 | Core & Data layer untangling, 0 `feature` imports in `core/`, DI relocation, `SyncableEntity` abstraction, dead code pruning | **COMPLETE** | Commit `f8b031c`, all 395 unit tests passing offline, docs validation 37/37 passing |
| **M2** | UNTANGLE-09 – UNTANGLE-13 | Feature & Domain Layer Clean Architecture & Structural Decoupling | **IN PROGRESS** (1/5 done) | Partial commit `aa8b84e` |
| **M3** | UNTANGLE-14 – UNTANGLE-15 | State Mutation Safety, Concurrency (`.update {}`, `CancellationException`), Comments cleanup | **PLANNED** | Ready to start after M2 |
| **M4** | UNTANGLE-16 – UNTANGLE-18 | Test Architecture Cleanup & Behavior Assertions | **PLANNED** | Ready to start after M3 |
| **M5** | UNTANGLE-19 | Comprehensive Offline Verification, Hardening & Final Gate | **PLANNED** | Final pass |

---

## 3. Detailed Task Breakdown & Implementation Guide

### Milestone 1 (COMPLETED & PUSHED in `f8b031c`)
- **UNTANGLE-01**: `AuthViewModel` & `LockScreen` relocated from `core` to `feature.auth.presentation`. Repositories injected instead of raw `AppDatabase`.
- **UNTANGLE-02**: Extracted `InventoryRemoteDataSource` to isolate stock transaction network calls from `PatientRemoteDataSource`.
- **UNTANGLE-03**: Dismantled monolithic `PatientUtils` facade into `DateUtils`, `AgeUtils`, `VaccineUtils`, and `VaccinationUtils`.
- **UNTANGLE-04**: Relocated Hilt DI modules from `core.di` to `com.neochildclinic.di`.
- **UNTANGLE-05**: Created `SyncableEntity` interface; all 16 Room entities implement it; eliminated 20-line `when` ladder in `SyncUploader.isEntityModifiedOffline`.
- **UNTANGLE-06**: Pruned dead queries (`deletePendingDuplicates`), unread fields, dead imports.
- **UNTANGLE-07**: Workers inject domain interfaces (`SyncRepository`, `ReminderScheduler`); resolved `BiometricLockManager` cold-start race.
- **UNTANGLE-08**: Corrected misleading KDocs; pruned restating comments while strictly keeping rationale comments.
- **Core Boundary Fix**: Fixed `NotificationHelper` importing `MainActivity` by replacing it with package-manager launch intent.

---

### Milestone 2 (CURRENT ACTIVE MILESTONE)

#### ✅ UNTANGLE-11: Decouple FinanceCalculator (COMPLETED in `aa8b84e`)
- Relocated `FinanceCalculator.kt` and `FinanceCalculatorTest.kt` from `feature.statistics.domain` to `feature.finance.domain`.
- Decoupled from `StatisticsUtils` by adding local `MONTH_NAMES` and `filterValidVaccinations` directly in `FinanceCalculator`.
- Updated all callers across dashboard, finance, statistics, and vaccination modules.

#### ⏳ UNTANGLE-09: Pure Kotlin Domain Models (NEXT STEP)
- **Goal**: Break coupling where domain models are typealiases to Room `@Entity` data classes.
- **Files**:
  - `app/src/main/java/com/neochildclinic/domain/model/Patient.kt`:
    Replace `typealias Patient = PatientEntity` with pure Kotlin data class:
    ```kotlin
    package com.neochildclinic.domain.model

    data class Patient(
        val id: String,
        val patientClinicId: String? = null,
        val name: String,
        val phone: String,
        val alternatePhone: String? = null,
        val dob: String,
        val gender: String,
        val address: String? = null,
        val registrationDate: String? = null,
        val updatedAt: String? = null,
        val attachments: String? = null,
        val createdBy: String? = null,
        val updatedBy: String? = null
    )
    ```
  - `app/src/main/java/com/neochildclinic/data/local/entity/PatientEntity.kt`:
    Already contains `fun PatientEntity.toPatient()` (rename to `toDomain()`) and `fun Patient.toEntity(isSynced: Boolean = true)`.
  - `app/src/main/java/com/neochildclinic/feature/patient/data/PatientRepositoryImpl.kt`:
    Map between DAO entities and domain models:
    `dao.getAllPatients().map { list -> list.map { it.toDomain() } }`, `getPatientById(id)?.toDomain()`, `dao.insertPatient(patient.toEntity())`.
  - `app/src/main/java/com/neochildclinic/domain/model/WasteRecord.kt`:
    Replace `typealias WasteRecord = WasteEntity` with pure data class. Update `WasteRepositoryImpl.kt` with `toDomain()` / `toEntity()`.
  - `app/src/main/java/com/neochildclinic/domain/model/DoctorAvailability.kt`:
    Replace `typealias DoctorWeeklySlot = DoctorWeeklySlotEntity` with pure data class:
    ```kotlin
    data class DoctorWeeklySlot(
        val id: String,
        val doctorId: String,
        val dayOfWeek: Int,
        val startMinute: Int,
        val endMinute: Int,
        val isActive: Boolean = true,
        val createdAt: String = "",
        val updatedAt: String = "",
        val createdBy: String? = null,
        val updatedBy: String? = null
    ) {
        val timeRange: TimeRange get() = TimeRange(startMinute, endMinute)
    }
    ```
    Update `DoctorAvailabilityRepositoryImpl.kt` with `toDomain()` / `toEntity()`.

#### ⏳ UNTANGLE-10: Decouple Consultation Recording
- **Goal**: Decouple `AddConsultationViewModel` from `ClinicalVaccinationService`.
- **Action**:
  - Extract `RecordConsultationUseCase` into `app/src/main/java/com/neochildclinic/feature/consultation/domain/RecordConsultationUseCase.kt`.
  - In `AddConsultationViewModel.kt`, replace injection of `ClinicalVaccinationService` with `RecordConsultationUseCase`.
  - In `ClinicalVaccinationService.kt`, remove `recordConsultation` method and remove unused `ConsultationRepository` dependency.

#### ⏳ UNTANGLE-12: Standardize Slot Date Interfaces on ISO (`yyyy-MM-dd`)
- **Goal**: Remove dual-format regex parsing in `GetAvailableSlotsUseCase`.
- **Action**:
  - Standardize `GetAvailableSlotsUseCase` to accept ISO `yyyy-MM-dd`.
  - Replace `dayOfWeekFor` and `normalizeDates` with standard ISO date parsing.
  - Confine `"d MMM yyyy"` display formatting strictly to UI Composables.

#### ⏳ UNTANGLE-13: Consolidate `feature/search` into `feature/patient`
- **Goal**: Eliminate redundant 2-file feature module.
- **Action**:
  - Move `SearchScreen.kt` and `SearchViewModel.kt` into `feature.patient.presentation`.
  - Have `SearchViewModel` and `PatientListViewModel` query `PatientRepository` directly.
  - Delete `SearchPatientsUseCase.kt`.
  - Delete directory `feature/search`.
  - Update `NavigationPatientGraph.kt` import.

---

## 4. Verification Commands

Always run these commands before committing any changes:

```bash
cd /home/nadeem/Desktop/vaccine_manager_app

# 1. Check clean architecture boundary (MUST BE 0):
grep -r "com.neochildclinic.feature\|com.neochildclinic.app" app/src/main/java/com/neochildclinic/core/ 2>/dev/null | wc -l

# 2. Run documentation integrity check:
python3 scripts/test_validate_docs.py

# 3. Run full unit test suite offline:
./gradlew testDebugUnitTest --offline
```

---

## 5. Next Agent Prompt Suggestion
When opening a new session/account, say:
> "Read `MEMORY.md` and `PROJECT.md`. Milestone 1 and UNTANGLE-11 are committed and pushed (`aa8b84e`). Continue executing Milestone 2 starting with UNTANGLE-09 (pure Kotlin domain models for Patient, WasteRecord, DoctorWeeklySlot), followed by UNTANGLE-10, UNTANGLE-12, UNTANGLE-13, and verify with `./gradlew testDebugUnitTest --offline`."
