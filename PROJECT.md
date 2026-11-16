# Project: Neo Child Clinic Android Application Re-Audit & Remediation

## Architecture
- **Application Type**: Offline-first Android medical clinic application (Kotlin, Jetpack Compose, Room SQLite, WorkManager, Hilt, SQLCipher, Biometrics).
- **Backend Infrastructure**: Supabase (PostgreSQL with RLS, Auth, Edge Functions) and Cloudflare Workers (D1/R2 encrypted backup service).
- **Security Boundary**: Android Keystore hardware-backed cryptography, biometric authentication, encrypted database storage, strict role-based access control (Admin, Doctor, Nurse, Staff).
- **Data Flow**: Local-first writes to Room database -> Outbox queue (`sync_queue`) -> Background `SyncWorker` with `SyncUploader` -> Supabase PostgREST & Realtime replication -> Cloudflare backup worker.

## Feature Inventory
Every feature and defect identified in Phase 0 Survey is cataloged below with its assigned milestone:

| # | Feature / Finding | Description | Milestone | Source |
|---|-------------------|-------------|-----------|--------|
| 1 | SEC-01: Keystore Initialization | Time-bound key init throws UserNotAuthenticatedException on API 30+ | M1 | Survey 3 |
| 2 | SEC-02: Cryptographic Bypass | Null cipher guard in BiometricAuthenticator skips secret verification | M1 | Survey 3 |
| 3 | SEC-03: Enrollment Invalidation | Missing setInvalidatedByBiometricEnrollment(true) on API 30+ | M1 | Survey 3 |
| 4 | SEC-04: Launcher PHI Leak | BiometricLockManager.onScreenOff fails to set isAppLocked.value = true | M1 | Survey 3 |
| 5 | SEC-05: Widget Cache Corruption | WidgetWorker writes masked "Patient (Locked)" strings to SQLite cache | M1 | Survey 3 |
| 6 | SEC-06: Update Checksum Regex | RE_SHA256_LINE misses standard hyphenated SHA-256 notation | M1 | Survey 3 |
| 7 | SEC-07: Keystore File Security | Secure file permissions for signing keystores in repository | M1 | Survey 3 |
| 8 | BACKEND-01: Tenant Isolation | Cloudflare backup worker accountId spoofing via user_metadata | M1 | Survey 3 |
| 9 | BACKEND-02: Supabase RLS DELETE | 9 clinical & inventory tables retain unconstrained staff DELETE policy | M1 | Survey 3 |
| 10 | BACKEND-03: Session Invalidation | UPDATE_PROFILE in manage-staff omits signOut on demoted roles | M1 | Survey 3 |
| 11 | SYNC-01: SingleFlight Cancellation | Coroutine cancellation in finally block deadlocks single-flight gate | M2 | Survey 2 |
| 12 | SYNC-02: Scoped Refresh Deduplication | RefreshDataUseCase missing @Singleton breaks cross-screen deduplication | M2 | Survey 2 |
| 13 | SYNC-03: Clock Skew Overwrite | Device clock skew behind server triggers silent overwrite of offline edits | M2 | Survey 2 |
| 14 | SYNC-04: Outbox FAILED Row Leak | SyncRepositoryImpl.enqueue ignores FAILED items causing chronic alerts | M2 | Survey 2 |
| 15 | SYNC-05: Realtime Subscription Drop | RealtimeChangeSubscriptions lacks reconnect/retry on socket failure | M2 | Survey 2 |
| 16 | DB-01: PatientDao Typo & Omission | hasUnsyncedRecordsForPatient typo ('REMINDER') & missing child entities | M2 | Survey 2 |
| 17 | DB-02: Inventory Prune Safety | RemotePrune omits waste_records and inventory_transactions checks | M2 | Survey 2 |
| 18 | DB-03: Converters String "null" | Converters.toList decodes literal "null" to listOf("null") | M2 | Survey 2 |
| 19 | DB-04: Hazardous DAO Annotations | Prune dead PatientDao.insertPatient; migrate VaccinationDao to @Upsert | M2 | Survey 2 |
| 20 | CLINIC-01: Doctor ID Slot Collision | GetAvailableSlotsUseCase fails to match slots for doctors with employeeId | M3 | Survey 1 |
| 21 | CLINIC-02: Reschedule Slot Block | Consultation edit does not update visit header slotId, blocking old slot | M3 | Survey 1 |
| 22 | CLINIC-03: Slot Exception Validation | DoctorAvailabilityRepositoryImpl.addException omits start < end check | M3 | Survey 1 |
| 23 | CLINIC-04: Receipt Double Billing | ReceiptFormatter adds ₹400 fee twice to already collected totalPaid | M3 | Survey 1 |
| 24 | CLINIC-05: Newborn Age Off-By-One | AgeUtils.calculateAgeLabel labels 0-6 day infants as "1 week" | M3 | Survey 1 |
| 25 | CLINIC-06: Reminder Drop & Epoch 1970 | Terminal reminder drop on missing visit; isoToLong(null) yields 1970 | M3 | Survey 1 |
| 26 | DEAD-01: Non-atomic State Mutations | 42 _uiState.value = ... in Backup, Admin, FullReport, VaccineDetail VM | M4 | Survey 1 |
| 27 | DEAD-02: Dead DateUtils Function | Prune unused DateUtils.cleanVaccineName | M4 | Survey 1 |
| 28 | DEAD-03: Redundant PatientUtils Method | Prune redundant forwarder PatientUtils.filterVaccinationsByPeriod | M4 | Survey 1 |
| 29 | DEAD-04: Orphaned Model Property | Prune unused Vaccine.doseSchedule column and property | M4 | Survey 1 |
| 30 | DEAD-05: Duplicate Data Class | Consolidate PatientVaccinationCardData with PatientVaccinationCard | M4 | Survey 1 |
| 31 | DEAD-06: Dead SyncItem Function | Prune unused SyncItem.toEntity() | M4 | Survey 1 |
| 32 | TEST-01: Test Suite Verification | Run 100% of Gradle unit tests and Python docs validation offline | M5 | Survey 1,2,3 |
| 33 | TEST-02: Unit Test Hardening | Add unit tests for fixed components (SingleFlight, AgeUtils, etc.) | M5 | Survey 1,2,3 |
| 34 | REPORT-01: Master Audit Report | Compile final comprehensive audit report per R1 requirements | M5 | Survey 1,2,3 |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Security, Cryptography & Cloud Services | SEC-01 to SEC-07, BACKEND-01 to BACKEND-03 | none | DONE |
| M2 | Offline-First Sync & Room Database Integrity | SYNC-01 to SYNC-05, DB-01 to DB-04 | none | DONE |
| M3 | Clinical & Business Logic Integrity | CLINIC-01 to CLINIC-06 | none | DONE |
| M4 | State Mutation Safety & Dead Code Elimination | DEAD-01 to DEAD-06 | none | DONE |
| M5 | Test Verification, Adversarial Hardening & Audit Report | TEST-01, TEST-02, REPORT-01 | M1, M2, M3, M4 | DONE |

## Interface Contracts
### Doctor Availability ↔ Consultation & Vaccination
- `DoctorAvailabilityDao`: `getBookedSlotIdsForDates(doctorId: String, dates: List<String>)` must check both `doctorId = :doctorId` and match across doctor UUID or employee ID, or caller passes `listOf(doctor.id, doctor.employeeId).filterNotNull()`.
- `ConsultationRepositoryImpl`: When updating consultation availability slot, update `availabilitySlotId` in both `consultations` and parent `patient_visits`.

### SingleFlight ↔ Concurrency
- `SingleFlight.run`: Must wrap cleanup in `withContext(NonCancellable) { mutex.withLock { inFlight.remove(key) } }` to ensure mutex lock succeeds even if the outer coroutine is cancelled.

### Biometric Lock ↔ App Widget
- `BiometricLockManager.onScreenOff`: Must set `_isAppLocked.value = true` immediately when screen turns off.
- `WidgetWorker`: Stores raw patient names in `widget_due_cache`. `VaccineWidget` applies masking dynamically when `BiometricLockManager.isAppLocked.value == true`.

### Cloudflare Backup Worker ↔ Supabase Auth
- `cloudflare/backup-worker/src/index.ts`: Must resolve `accountId` exclusively from `user.app_metadata?.clinic_id || user.sub`. Client-writable `user.user_metadata` must never be used for tenant authorization.

## Code Layout
- `app/src/main/java/com/neochildclinic/core/security/`: Security, biometric authentication, lock manager.
- `app/src/main/java/com/neochildclinic/core/sync/`: Offline-first sync, SingleFlight, SyncUploader, SyncWorker.
- `app/src/main/java/com/neochildclinic/data/local/dao/`: Room DAOs (PatientDao, DoctorAvailabilityDao, VaccineDao).
- `app/src/main/java/com/neochildclinic/data/local/database/`: Room AppDatabase, TypeConverters.
- `app/src/main/java/com/neochildclinic/feature/`: Feature modules (consultation, vaccination, doctor, patient, reminder, inventory, widget, settings, staff, statistics).
- `cloudflare/backup-worker/`: Cloudflare D1/R2 backup worker.
- `supabase/`: Migrations and Edge Functions.
- `app/src/test/java/com/neochildclinic/`: Offline JUnit tests.
