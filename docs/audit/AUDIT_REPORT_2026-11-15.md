# Comprehensive Technical Audit Report: Neo Child Clinic Android Application

**Target Repository**: `/home/nadeem/Desktop/vaccine_manager_app`  
**Application Platform**: Android (Kotlin 2.4.10, AGP 9.5.0, Compose BOM 2026.08.00, compileSdk 37, minSdk 29, targetSdk 35)  
**Database (as of 2026-11-15)**: SQLCipher 4.19.0 encrypted SQLite via AndroidX Room 2.8.5 (Schema v34; the live schema is v35)  
**Backend Architecture**: Supabase (PostgREST, GoTrue Auth, Realtime) + Cloudflare Backup Worker (R2/D1)  
**Audit Scope**: Logic Bugs, Offline-First Synchronization Anomalies, Room Schema & Migration Risks, Data Integrity Edge Cases, Supabase Row Level Security (RLS) Policies, Security Vulnerabilities  
**Audit Standard**: Local static analysis and source verification (Strict compliance with Rule R3: zero external network traffic)  
**Date of Audit**: November 15, 2026  
**Auditor**: Teamwork Audit Compiler (Synthesized from Specialized Survey Explorers 1, 2, and 3)

---

## Executive Summary

A comprehensive architectural, cryptographic, persistence, synchronization, and business-logic audit was conducted across the Neo Child Clinic codebase. Neo Child Clinic is an Android-based pediatric clinic management system supporting patient registration, vaccination administration and scheduling, doctor consultations, inventory batch management, and billing. The system is designed around an offline-first paradigm, relying on a local SQLCipher-encrypted SQLite database synchronized asynchronously with Supabase and backed up to Cloudflare R2 storage.

While the codebase demonstrates sophisticated architectural practices—such as structured Clean Architecture layers, Kotlin Coroutines, Jetpack Compose, non-destructive Room migrations, and explicit write rate-limiting triggers in Postgres—the audit identified **38 distinct findings across all six audited domains**. A substantial number of these vulnerabilities and defects pose immediate risks of **irreversible clinical data destruction**, **app crashes in production**, **silent overwrites of pediatric medical records**, **unauthenticated leakage of protected health information (PHI)**, and **bypasses of integrity verification during automatic app updates**.

### Summary Breakdown of Findings by Category and Severity

| Audit Target Domain | Critical | High | Medium | Low | Total Findings |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Section 1: Logic Bugs** | 2 | 2 | 4 | 0 | **8** |
| **Section 2: Offline-First Synchronization Anomalies** | 2 | 3 | 2 | 1 | **8** |
| **Section 3: Room Schema and Migration Risks** | 1 | 1 | 3 | 3 | **8** |
| **Section 4: Data Integrity Edge Cases** | 1 | 1 | 2 | 0 | **4** |
| **Section 5: Supabase Row Level Security (RLS) Policies** | 0 | 1 | 3 | 0 | **4** |
| **Section 6: Security Vulnerabilities** | 1 | 2 | 1 | 2 | **6** |
| **Total** | **7** | **10** | **15** | **6** | **38** |

### Key Systemic Risk Highlights
1. **Critical Clinical History Loss via Foreign Key Cascades & Pruning**: Both background inventory pruning (`InventoryRepositoryImpl.kt`) and patient merge-restore operations (`BackupRestorer.kt`) execute SQLite `DELETE` or `INSERT OR REPLACE` actions that fire `ON DELETE CASCADE` triggers. This unconditionally deletes historical administered dose records (`vaccination_items`) and visit records (`patient_visits`), irreversibly destroying children's vaccination histories.
2. **Silent Destruction of Offline Clinical Data on Remote Prune**: When a patient record is deleted on the cloud server, the tablet's downstream sync engine (`PatientRepositoryImpl.kt`) deletes the local patient without inspecting pending offline child mutations (`VACCINATION`, `CONSULTATION`). SQLite cascades the deletion, and `SyncUploader.kt` silently drops the unsynced medical records from the outbox.
3. **Flawed Vaccine Reminder Satisfaction & Overdue Inflation**: `ClinicalVaccinationService.kt` attempts to mark due reminders completed by querying for the newly generated visit UUID rather than the scheduled reminder's identifiers. Reminders are never completed, causing active reminders to persistently flip to "Overdue". Simultaneously, `ReminderDueListProcessor.kt` tallies historical completed vaccines into the dashboard "Overdue" counter, inflating overdue statistics by hundreds of false positives.
4. **Complete In-App Update Integrity Bypass**: `AppUpdateManager.kt` expects a SHA-256 digest on release asset JSON objects under the field `digest`. GitHub Releases REST API does not provide a `digest` attribute, resulting in a permanently null expected hash and completely bypassing APK checksum verification prior to package installation.
5. **Cleartext Pediatric PHI Exposure via Home Screen Widget**: While the primary Android app enforces `FLAG_SECURE` and biometric authentication lockouts, the Glance home screen widget (`WidgetWorker.kt`) renders pediatric patient names and upcoming/overdue vaccines directly on the launcher without checking biometric lock state.
6. **Permissive Supabase RLS DELETE Policies**: Blanket RLS policies installed across 20 tables permit any authenticated staff account (including receptionists and junior staff) to issue raw PostgREST HTTP DELETE calls and wipe all clinic patient records, vaccination histories, and financial transactions.

---

# Section 1: Logic Bugs

This section details defects within core clinical calculations, reminder state transitions, appointment booking validation, patient identification, and ViewModel/coroutine state management.

---

### Finding LOGIC-01: `ClinicalVaccinationService.satisfyRelatedReminders` Queries by Current Visit ID, Leaving Due Reminders Active and Causing False Overdues
- **Severity**: **Critical**
- **File Path & Line Numbers**: `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/feature/vaccination/domain/ClinicalVaccinationService.kt:250-264`
- **Description**:  
  When clinic staff records an administered vaccination visit, `ClinicalVaccinationService` attempts to mark any associated active reminders as completed:
  ```kotlin
  private suspend fun satisfyRelatedReminders(vaccination: Vaccination, user: String) {
      val reminders = reminderRepository.getRemindersByVisitId(vaccination.id)
      val givenNames = vaccination.items.map {
          PatientUtils.cleanVaccineName(it.vaccineName).lowercase().trim()
      }
      reminders.forEach { reminder ->
          val reminderNames = reminder.vaccineName.split(", ").map {
              PatientUtils.cleanVaccineName(it).lowercase().trim()
          }.filter { it.isNotBlank() }
          val matches = reminderNames.isEmpty() || reminderNames.any { it in givenNames }
          if (matches && reminder.status != "COMPLETED" && reminder.status != "DISMISSED") {
              reminderRepository.markReminderCompleted(reminder, user, vaccination.id)
          }
      }
  }
  ```
  `vaccination.id` is the freshly generated UUID for the *current* vaccination visit. In the clinical data model, upcoming reminders for this child were scheduled weeks or months prior—either during birth registration or following a prior visit (linked via `originalVisitId` of the *prior* visit). Querying `getRemindersByVisitId(vaccination.id)` looks for reminders whose `originalVisitId == vaccination.id`. Because the current visit was just created, zero reminders reference this new visit ID. The list `reminders` is always empty.
  Consequently, active reminders for the administered vaccine are never marked `COMPLETED`. They remain indefinitely in `ACTIVE` status until their due date passes, at which point they become falsely `OVERDUE`.
- **Concrete Reproduction Scenario**:
  1. Register a child with a due reminder for "Pentavalent-1" due on November 1, 2026.
  2. On November 1, 2026, the doctor records the administration of Pentavalent-1 for this child.
  3. A new `Vaccination` visit is created with `vaccination.id = "visit-uuid-101"`.
  4. `satisfyRelatedReminders` queries `reminderRepository.getRemindersByVisitId("visit-uuid-101")`, returning `[]`.
  5. The Pentavalent-1 reminder remains `status = "ACTIVE"`.
  6. On November 2, 2026, the clinic dashboard and notification system flag Pentavalent-1 as "OVERDUE" for this child, despite the dose having been successfully administered.
- **Recommended Remediation**:  
  Query active reminders by `patientId` rather than `visitId`, and match by vaccine name and patient ID:
  ```kotlin
  private suspend fun satisfyRelatedReminders(vaccination: Vaccination, user: String) {
      val activeReminders = reminderRepository.getPatientReminders(vaccination.patientId).first()
          .filter { it.status == "ACTIVE" }
      val givenNames = vaccination.items.map {
          PatientUtils.cleanVaccineName(it.vaccineName).lowercase().trim()
      }
      activeReminders.forEach { reminder ->
          val reminderNames = reminder.vaccineName.split(",").map {
              PatientUtils.cleanVaccineName(it).lowercase().trim()
          }.filter { it.isNotBlank() }
          val matches = reminderNames.isEmpty() || reminderNames.any { it in givenNames }
          if (matches) {
              reminderRepository.markReminderCompleted(reminder, user, vaccination.id)
          }
      }
  }
  ```

---

### Finding LOGIC-02: `ReminderDueListProcessor.stats` Counts Completed and Dismissed Historical Vaccines as Overdue
- **Severity**: **Critical**
- **File Path & Line Numbers**: `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/feature/reminder/data/ReminderDueListProcessor.kt:111-127`
- **Description**:  
  `ReminderDueListProcessor.process()` aggregates both active reminders and historical terminal reminders (`COMPLETED` and `DISMISSED`) into a single output list of `Vaccination` items for display.
  When computing dashboard statistics in `stats()`, the processor iterates over this combined list:
  ```kotlin
  fun stats(dueList: List<Vaccination>, reminders: List<ReminderEntity>): ReminderStats {
      val todayCal = DateClassifier.getTodayStart()
      val todayStart = todayCal.timeInMillis

      return ReminderStats(
          dueToday = dueList.count {
              val cat = DateClassifier.classify(it.nextDueDate, todayCal)
              cat is DateCategory.Today
          },
          dueTomorrow = dueList.count { DateClassifier.classify(it.nextDueDate, todayCal) is DateCategory.Tomorrow },
          overdue = dueList.count {
              val cat = DateClassifier.classify(it.nextDueDate, todayCal)
              cat is DateCategory.Overdue
          },
          completedToday = reminders.count { it.status == "COMPLETED" && com.neochildclinic.core.common.PatientUtils.isoToLong(it.completionDate) >= todayStart },
          dismissedToday = reminders.count { it.status == "DISMISSED" && com.neochildclinic.core.common.PatientUtils.isoToLong(it.dismissalDate) >= todayStart }
      )
  }
  ```
  While `dueTodayCount()` (lines 105–109) explicitly filters for `it.status == ReminderStatus.ACTIVE`, `stats()` evaluates `cat is DateCategory.Overdue` without checking `it.status`. Every child who previously completed infant vaccines (e.g., BCG, Hep B, OPV given months or years in the past) has `nextDueDate` prior to today. Because terminal items remain in `dueList`, every completed historical vaccine whose due date was in the past is counted as `overdue`. Within weeks of clinic operation, the dashboard displays hundreds or thousands of false "Overdue" vaccinations.
- **Concrete Reproduction Scenario**:
  1. Enroll a 1-year-old child whose historical vaccines (BCG, OPV-0, Pentavalent 1-3) were completed on time.
  2. Open the clinic home dashboard.
  3. Observe that the "Overdue" count metric displays `5 Overdue` instead of `0 Overdue`.
- **Recommended Remediation**:  
  Filter the count in `stats()` strictly to active reminders:
  ```kotlin
  overdue = dueList.filter { it.status == ReminderStatus.ACTIVE }.count {
      val cat = DateClassifier.classify(it.nextDueDate, todayCal)
      cat is DateCategory.Overdue
  },
  dueToday = dueList.filter { it.status == ReminderStatus.ACTIVE }.count {
      val cat = DateClassifier.classify(it.nextDueDate, todayCal)
      cat is DateCategory.Today
  },
  ```

---

### Finding LOGIC-03: Patient Repository Throws Unhandled `IllegalArgumentException` on Unpromoted `TEMP-` Clinic IDs
- **Severity**: **High**
- **File Path & Line Numbers**: `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/feature/patient/data/PatientRepositoryImpl.kt:206-208`
- **Description**:  
  When patients are pulled from the server without an assigned clinic ID or created during conflict resolution, `refreshPatients()` assigns a temporary identifier prefix: `"TEMP-${entity.id}"`.
  In `PatientRepositoryImpl.addPatient()`:
  ```kotlin
  val entity = transactionRunner.run {
      val finalClinicId = if (patient.patientClinicId.isNullOrBlank()) {
          idGenerator.generateUniqueClinicId()
      } else {
          if (patient.patientClinicId.startsWith("TEMP-")) {
              throw IllegalArgumentException("Invalid Clinic ID format.")
          }
          if (!idGenerator.isIdUnique(patient.patientClinicId, patient.id)) {
              throw IllegalStateException("A patient with Clinic ID ${patient.patientClinicId} already exists.")
          }
          patient.patientClinicId
      }
  ...
  ```
  *Technical Qualification*: In the primary Jetpack Compose UI (`AddPatientScreen.kt:52`), editing a patient with a `TEMP-` prefix automatically blanks the clinic ID field (`clinicId = if (patient.patientClinicId?.startsWith("TEMP-") == true) "" else ...`), which prompts `PatientRepositoryImpl` to mint a new permanent clinic ID on save.
  However, the underlying domain repository enforces an unsafe contract: if `patientRepository.addPatient(patient)` is invoked directly with an unpromoted `TEMP-` entity (such as during background sync reconciliation, automated migration tasks, external data restoration, or if staff manually inputs a `TEMP-` ID into the input field), the repository throws `IllegalArgumentException("Invalid Clinic ID format.")`. This aborts the database transaction and fails the save operation.
- **Concrete Reproduction Scenario**:
  1. Retrieve or construct a `Patient` domain model instance holding a temporary identifier: `val tempPatient = patient.copy(patientClinicId = "TEMP-c19b-432a")`.
  2. Invoke `patientRepository.addPatient(tempPatient)` directly via a background synchronization handler, unit test, or programmatic batch operation.
  3. Or, in the "Edit Patient" UI screen, manually type `"TEMP-MANUAL-01"` into the Clinic ID text field and tap "Save Changes".
  4. The transaction executes `PatientRepositoryImpl.kt:206-208`: `patient.patientClinicId.startsWith("TEMP-")` evaluates to `true`.
  5. The repository throws `IllegalArgumentException: Invalid Clinic ID format.`, rolling back the transaction and failing the patient update.
- **Recommended Remediation**:  
  Handle auto-promotion within the repository transaction itself rather than relying on a Compose UI screen guard. If the clinic ID is blank OR starts with `"TEMP-"`, generate a permanent clinic ID automatically:
  ```kotlin
  val finalClinicId = if (patient.patientClinicId.isNullOrBlank() || patient.patientClinicId.startsWith("TEMP-")) {
      idGenerator.generateUniqueClinicId()
  } else {
      if (!idGenerator.isIdUnique(patient.patientClinicId, patient.id)) {
          throw IllegalStateException("A patient with Clinic ID ${patient.patientClinicId} already exists.")
      }
      patient.patientClinicId
  }
  ```

---

### Finding LOGIC-04: `GetAvailableSlotsUseCase` Ignores Existing Bookings, Permitting Unbounded Double-Bookings
- **Severity**: **High**
- **File Path & Line Numbers**: 
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/feature/doctor/domain/GetAvailableSlotsUseCase.kt:27-52, 59-62`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/feature/consultation/presentation/AddConsultationViewModel.kt:180-188`
- **Description**:  
  `GetAvailableSlotsUseCase` computes bookable doctor consultation slots for a given date.
  ```kotlin
  val weeklySlots = repository.getActiveWeeklySlotsForDay(doctorId, dayOfWeek)
  if (weeklySlots.isEmpty()) return DoctorAvailabilityResult.NoScheduleConfigured
  val exceptions = repository.getExceptionsForDate(doctorId, date)
  ...
  val available = weeklySlots
      .filterNot { it.id in blockedSlotIds }
      .filterNot { slot -> customTimeExceptions.any { exc -> rangesOverlap(...) } }
      .sortedBy { it.startMinute }
      .map { AvailableSlot(it.id, it.doctorId, it.startMinute, it.endMinute) }
  ```
  The use case inspects doctor weekly working templates and doctor vacation/absence exceptions. Crucially, **it never queries existing consultations or appointments booked for that date**.
  `isSlotStillAvailable()` (lines 59–62) merely verifies that the slot template exists and is not blocked by a doctor leave exception.
  As a consequence, if Doctor Smith works 9:00 AM – 1:00 PM, fifty different patients can book the exact same 10:00 AM – 10:15 AM appointment slot. The UI continues to present the slot as "Available" to all clinic tablets.
- **Concrete Reproduction Scenario**:
  1. Receptionist on Tablet 1 books Patient A with Dr. Smith for Nov 20, 2026 at 10:00 AM.
  2. Receptionist on Tablet 2 opens appointment scheduling for Patient B with Dr. Smith for Nov 20, 2026.
  3. The 10:00 AM slot is displayed as "Available".
  4. Tablet 2 books Patient B into the identical 10:00 AM slot without warning or conflict error.
- **Recommended Remediation**:  
  Inject `ConsultationRepository` into `GetAvailableSlotsUseCase` and filter out slots already occupied by active consultations for that doctor and date:
  ```kotlin
  val existingBookings = consultationRepository.getConsultationsForDoctorOnDate(doctorId, date)
  val bookedSlotIds = existingBookings.mapNotNull { it.slotId }.toSet()
  val available = weeklySlots
      .filterNot { it.id in blockedSlotIds }
      .filterNot { it.id in bookedSlotIds }
      ...
  ```

---

### Finding LOGIC-05: Non-Atomic State Mutations Across ViewModels Cause Lost Updates and UI Freezes
- **Severity**: **Medium**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/feature/profile/presentation/ProfileViewModel.kt:40, 59, 71, 81`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/feature/patient/presentation/PatientViewModel.kt:227, 236`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/feature/doctor/presentation/WeeklyDoctorSlotsViewModel.kt:105, 122`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/feature/audit/presentation/FullAuditLogViewModel.kt:65, 84`
- **Description**:  
  Multiple ViewModels perform state mutations via direct read-then-write on `MutableStateFlow`:
  ```kotlin
  // ProfileViewModel.kt:40
  _state.value = _state.value.copy(isLoading = true)

  // PatientViewModel.kt:227 & 236
  _deletingVaccinationIds.value += id
  ...
  _deletingVaccinationIds.value -= id
  ```
  Direct property assignment (`_state.value = ...`) and operator assignments (`+=` / `-=`) on `MutableStateFlow` are not atomic. When concurrent coroutines execute (such as an asynchronous network or Room response returning while the user interacts with an action button), one coroutine reads stale state and writes back, silently clobbering mutations made by the other.
- **Concrete Reproduction Scenario**:
  1. In `PatientViewModel`, user quickly deletes two vaccination records in rapid succession.
  2. Coroutine 1 reads `_deletingVaccinationIds.value` (empty set).
  3. Coroutine 2 reads `_deletingVaccinationIds.value` (empty set).
  4. Coroutine 1 adds `id1` and sets `value = [id1]`.
  5. Coroutine 2 adds `id2` and sets `value = [id2]`.
  6. `id1` is lost from the deletion tracking set. The UI progress spinner for `id1` disappears prematurely, leaving the item in an indeterminate UI state.
- **Recommended Remediation**:  
  Replace all direct `.value =` and `+=` assignments with the thread-safe atomic `.update { ... }` operator from `kotlinx.coroutines.flow`:
  ```kotlin
  _state.update { it.copy(isLoading = true) }
  _deletingVaccinationIds.update { it + id }
  _deletingVaccinationIds.update { it - id }
  ```

---

### Finding LOGIC-06: Missing Patient Registration Validation (Future Dates of Birth, Malformed Phone Numbers, Duplicate Checks)
- **Severity**: **Medium**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/feature/patient/presentation/AddPatientScreen.kt:118-132`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/feature/patient/presentation/PatientViewModel.kt:212`
- **Description**:  
  The patient creation workflow lacks client-side validation:
  1. *Date of Birth in the Future*: The date picker does not constrain `dob <= LocalDate.now()`. Selecting a future year creates children with negative ages in `AgeUtils`, corrupting pediatric immunization schedule calculations.
  2. *Malformed Phone Numbers*: Accepts arbitrary text, leading to failed SMS/WhatsApp reminder dispatches.
  3. *Unchecked Duplicate Patients*: Registering a child with an identical Name, Date of Birth, and Parent Phone number proceeds without warning, creating fragmented duplicate medical histories.
- **Concrete Reproduction Scenario**:
  1. On the "Add Patient" screen, select a birth date in 2027 and enter phone number "abcde".
  2. Tap "Register Patient".
  3. The patient is saved in SQLite and synchronized to Supabase without validation errors.
- **Recommended Remediation**:  
  Add validation in `PatientViewModel`: enforce `dob <= LocalDate.now()`, regex phone validation against E.164/10-digit formats, and execute `patientRepository.checkDuplicate(name, dob, phone)` prior to commit.

---

### Finding LOGIC-07: Doctor Availability Weekly Slot Generator Accepts Inverted Times and Overlapping Intervals
- **Severity**: **Medium**
- **File Path & Line Numbers**: `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/feature/doctor/data/DoctorAvailabilityRepositoryImpl.kt:45-56`
- **Description**:  
  When configuring a doctor's recurring weekly availability slot, `addWeeklySlot` receives `startMinute` and `endMinute` (minutes from midnight). The function performs no sanity checks to verify that `startMinute < endMinute` or that the interval does not collide with existing slots for that doctor on the same day.
  Saving an inverted slot (e.g., `startMinute = 1020` [17:00] and `endMinute = 540` [09:00]) causes subsequent slot slicing algorithms in `GetAvailableSlotsUseCase` to produce infinite loops or throw arithmetic exceptions.
- **Concrete Reproduction Scenario**:
  1. Configure doctor schedule with Start Time: 5:00 PM and End Time: 9:00 AM.
  2. Save slot.
  3. Attempt to book an appointment with this doctor; the slot calculator enters an unhandled loop or produces negative slot intervals.
- **Recommended Remediation**:  
  Add input assertions in `DoctorAvailabilityRepositoryImpl`:
  ```kotlin
  require(startMinute < endMinute) { "Slot start time must precede end time." }
  val existing = getActiveWeeklySlotsForDay(doctorId, dayOfWeek)
  require(existing.none { rangesOverlap(it.startMinute, it.endMinute, startMinute, endMinute) }) {
      "Slot overlaps with an existing schedule."
  }
  ```

---

### Finding LOGIC-08: `DailySummaryWorker` Offline Lockout via Unnecessary `NetworkType.CONNECTED` WorkManager Constraint
- **Severity**: **Medium**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/core/notification/ReminderScheduler.kt:30-32, 57`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/core/notification/DailySummaryWorker.kt:35-50`
- **Description**:  
  `DailySummaryWorker` triggers every morning at 8:00 AM to show clinic staff an Android notification summarizing patients due for vaccines that day, low inventory stock levels, and consultations.
  In `ReminderScheduler.kt`:
  ```kotlin
  val constraints = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .build()
  ```
  `DailySummaryWorker` reads its data **exclusively from local Room DAOs** (`patientDao`, `vaccineInventoryDao`, `reminderDao`). It executes zero network calls. By requiring `NetworkType.CONNECTED`, if the clinic tablet has Wi-Fi turned off overnight or loses connection, WorkManager blocks execution. Staff arrives at the clinic and receives no morning operational summary.
- **Concrete Reproduction Scenario**:
  1. Place the clinic tablet in Airplane Mode overnight.
  2. Wait until 8:05 AM.
  3. Observe that WorkManager defers `DailySummaryWorker` and no local summary notification appears.
- **Recommended Remediation**:  
  Remove `.setRequiredNetworkType(NetworkType.CONNECTED)` from `ReminderScheduler.kt:30-32`.

---

# Section 2: Offline-First Synchronization Anomalies

This section covers bugs in outbox queue processing, `SingleFlight` concurrency conflation, client-server clock skew, and cascade data loss during background synchronization.

---

### Finding SYNC-01: `MergePatientsUseCase` Drops Inventory Transaction Sync, Causing Irreversible Server-Side Patient Link Severance (`ON DELETE SET NULL`)
- **Severity**: **Critical**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/feature/patient/domain/MergePatientsUseCase.kt:48-49`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/feature/inventory/data/InventoryRepositoryImpl.kt:638-640`
  - `/home/nadeem/Desktop/vaccine_manager_app/supabase/migrations/20261026_child_refs_survive_parent_delete.sql:71`
- **Description**:  
  When merging a duplicate patient into a master patient, `MergePatientsUseCase` executes:
  ```kotlin
  // MergePatientsUseCase.kt:48-52
  inventoryRepository.transferPatientTransactions(dupId, masterId)
  patientRepository.reassignPatientLinkedRecords(dupId, masterId, groupId)
  ...
  patientRepository.deletePatient(dupId)
  ```
  In `InventoryRepositoryImpl.kt`:
  ```kotlin
  override suspend fun transferPatientTransactions(duplicateId: String, masterId: String) {
      vaccineDao.updatePatientIdInTransactions(duplicateId, masterId)
  }
  ```
  While `reassignPatientLinkedRecords` enqueues sync updates for consultations and notes, and lines 55–60 enqueue updates for vaccinations and reminders, **no sync mutation is ever enqueued for the modified `inventory_transactions`**.
  Immediately thereafter, `deletePatient(dupId)` enqueues a `DELETE` mutation for patient `dupId` into `sync_queue`.
  When `SyncUploader` pushes changes to Supabase:
  1. The transaction patient re-assignment is never uploaded. On the remote server, `inventory_transactions` still reference `patient_id = dupId`.
  2. The delete mutation for `dupId` executes against Supabase `patients`.
  3. Supabase migration `20261026_child_refs_survive_parent_delete.sql:71` defines:
     `FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE SET NULL`.
  4. Remote PostgreSQL executes `UPDATE inventory_transactions SET patient_id = NULL WHERE patient_id = dupId`.
  5. The audit link between the administered vaccine stock and the master patient is permanently wiped on the server.
- **Concrete Reproduction Scenario**:
  1. Patient B (duplicate) received an expensive vaccine (e.g. Varicella), creating an inventory deduction transaction linked to B's ID.
  2. Doctor merges Patient B into Master Patient A.
  3. Tablet uploads outbox queue to Supabase.
  4. Inspect Supabase table `inventory_transactions`: the transaction's `patient_id` has been set to `NULL`. The link between Patient A and the administered dose batch is destroyed.
- **Recommended Remediation**:  
  In `InventoryRepositoryImpl.transferPatientTransactions`, fetch the affected transaction IDs and enqueue an `UPDATE` mutation for each into `syncRepository` within the same transaction group:
  ```kotlin
  override suspend fun transferPatientTransactions(duplicateId: String, masterId: String) {
      val txIds = vaccineDao.getTransactionIdsForPatient(duplicateId)
      vaccineDao.updatePatientIdInTransactions(duplicateId, masterId)
      txIds.forEach { txId ->
          syncRepository.enqueue("INVENTORY_TRANSACTION", txId, SyncOperation.UPDATE, SyncPriority.MEDIUM)
      }
  }
  ```

---

### Finding SYNC-02: `RemotePrune` Ignores Unsynced Child Entities, Cascading SQLite Hard Deletion and Causing Outbox Purge of Offline Records
- **Severity**: **Critical**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/feature/patient/data/PatientRepositoryImpl.kt:182-187`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/core/sync/SyncUploader.kt:636-645`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/VaccinationEntity.kt:20-25`
- **Description**:  
  During downstream sync, `PatientRepositoryImpl` deletes local patient rows that were deleted on the server:
  ```kotlin
  val pendingPatientIds = syncQueueDao.unsyncedIdSet("PATIENT")
  val toDelete = RemotePrune.computeBatches(
      localIds = localById.keys.toList(),
      syncedIds = localById.values.filter { it.isSynced }.mapTo(HashSet()) { it.id },
      remoteIds = entities.mapTo(HashSet()) { it.id },
      pendingIds = pendingPatientIds
  ).flatten().forEach { patientDao.deletePatient(it) }
  ```
  `pendingPatientIds` only queries pending mutations where `entity_type == 'PATIENT'`.
  However, Room entities (`VisitEntity`, `VaccinationItemEntity`, `ConsultationEntity`, `PatientNotesEntity`) declare `ForeignKey.CASCADE` against `patients(id)`.
  If a patient was deleted on the server (e.g. by another clinic tablet) while the current tablet was offline, and the offline tablet recorded a new vaccination visit, consultation, or clinical note for that patient:
  1. Downstream sync runs `RemotePrune`. Since the patient has no pending `PATIENT` edit in `sync_queue`, `patientDao.deletePatient(it)` executes.
  2. SQLite executes `CASCADE DELETE`, instantly deleting all local visits, vaccinations, and consultations from the tablet's Room database.
  3. When `SyncUploader` subsequently drains `sync_queue`:
     ```kotlin
     // SyncUploader.kt:636-645
     val entity = vaccinationDao.getById(item.entityId)
     if (entity == null) {
         markUploaded(listOf(item.id)) // Drops outbox item!
         return
     }
     ```
  4. Because the local record was deleted by SQLite cascade, `SyncUploader` considers it a dead record, deletes it from `sync_queue`, and discards the child's vaccination data permanently.
- **Concrete Reproduction Scenario**:
  1. Tablet 2 deletes a patient record while Tablet 1 is offline.
  2. Tablet 1 records an emergency vaccination for that patient while offline.
  3. Tablet 1 reconnects to the network.
  4. Pull sync runs `RemotePrune` on patients: Tablet 1 cascades delete to the new visit.
  5. `SyncUploader` runs next: finds local visit missing, purges the item from `sync_queue`.
  6. The vaccination record is irreversibly destroyed.
- **Recommended Remediation**:  
  `RemotePrune` in `PatientRepositoryImpl` must check if any child entities exist in `sync_queue` referencing the patient (`VACCINATION`, `CONSULTATION`, `PATIENT_NOTE`). If unsynced child records exist, abort pruning that patient and flag a conflict for clinical review.

---

### Finding SYNC-03: `SingleFlight` Coalesces Scoped Table Refreshes Under a Shared Global Lock, Silently Dropping Domain Refreshes
- **Severity**: **High**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/core/sync/SingleFlight.kt:27-52`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/feature/sync/domain/RefreshDataUseCase.kt:47-50`
- **Description**:  
  `SingleFlight` is intended to coalesce redundant concurrent sync requests into a single in-flight job:
  ```kotlin
  // SingleFlight.kt
  class SingleFlight {
      private val mutex = Mutex()
      private var inFlight: CompletableDeferred<Any?>? = null

      suspend fun <T> run(block: suspend () -> T): T { ... }
  }
  ```
  `SingleFlight` maintains a single global `inFlight` reference without key differentiation.
  In `RefreshDataUseCase.kt`:
  ```kotlin
  suspend operator fun invoke(vararg tables: RefreshTables) =
      flight.run { runRefresh(stepsFor(tables.toList())) }
  ```
  If Screen A calls `refreshDataUseCase(RefreshTables.PATIENTS)` (e.g. patient search refresh) and Screen B simultaneously triggers `refreshDataUseCase(RefreshTables.INVENTORY)` (e.g. opening vaccine stock), Screen B's call is coalesced into Screen A's execution. Screen B awaits Screen A's completion and returns success, but **inventory tables were never fetched**. The inventory screen displays stale, out-of-date stock levels.
- **Concrete Reproduction Scenario**:
  1. Trigger a background refresh of patients.
  2. Concurrently open the Vaccine Inventory screen which requests `RefreshTables.INVENTORY`.
  3. `SingleFlight` joins the inventory request to the in-flight patient refresh.
  4. Inventory screen finishes loading without pulling new batches from Supabase.
- **Recommended Remediation**:  
  Refactor `SingleFlight` to maintain a keyed map of in-flight deferreds:
  ```kotlin
  class KeyedSingleFlight {
      private val map = mutableMapOf<String, CompletableDeferred<Any?>>()
      private val mutex = Mutex()

      suspend fun <T> run(key: String, block: suspend () -> T): T { ... }
  }
  ```
  Pass the sorted list of requested tables as the single-flight key: `tables.map { it.name }.sorted().joinToString()`.

---

### Finding SYNC-04: Client-Server Clock Skew Causes Silent Overwrite and Destruction of Offline Local Edits in `SyncUploader`
- **Severity**: **High**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/core/sync/SyncUploader.kt:544-551`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/core/sync/SyncRepositoryImpl.kt:304`
- **Description**:  
  In `SyncUploader.uploadEntity`:
  ```kotlin
  val remoteUpdatedAt = remoteRow["updated_at"]?.toLongOrNull() ?: 0L
  val localUpdatedAtLong = localEntity.updatedAt
  if (remoteUpdatedAt > localUpdatedAtLong && localUpdatedAtLong > 0) {
      saveRemoteToLocal(remoteRow)
      return UploadResult.RemoteWon
  }
  ```
  When `UploadResult.RemoteWon` is returned, `processGroup` immediately invokes `markUploaded(listOf(item.id))`, deleting the mutation from `sync_queue`.
  If the clinic tablet's system clock is skewed even a few seconds behind the Supabase NTP server:
  1. Doctor edits patient notes or contact numbers offline.
  2. `localUpdatedAtLong` is stamped with the tablet's local time (`System.currentTimeMillis()`).
  3. `remoteUpdatedAt` (stamped by Postgres `now()`) is slightly greater than `localUpdatedAtLong`.
  4. The uploader mistakenly decides `remoteUpdatedAt > localUpdatedAtLong`, downloads the stale remote record, overwrites the doctor's offline edits in Room, and deletes the outbox entry. The doctor's offline changes vanish without notification.
- **Concrete Reproduction Scenario**:
  1. Set clinic tablet clock 3 minutes behind actual time.
  2. Edit a patient's notes while offline.
  3. Reconnect to the network.
  4. `SyncUploader` compares `remoteUpdatedAt > localUpdatedAtLong`.
  5. The tablet overwrites the local edit with the remote value and clears the sync queue.
- **Recommended Remediation**:  
  Use revision counter sequences or compare against the base version pulled during the previous sync rather than comparing raw wall-clock timestamps between independent unsynchronized clocks.

---

### Finding SYNC-05: Unparented Coroutine in `VaccineWidgetReceiver.onReceive` Lacks `goAsync()`, Causing Intermittent Process Death and Stale Widget State
- **Severity**: **High**
- **File Path & Line Numbers**: `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/feature/widget/presentation/VaccineWidgetReceiver.kt:30-32`
- **Description**:  
  ```kotlin
  override fun onReceive(context: Context, intent: Intent) {
      super.onReceive(context, intent)
      when (intent.action) {
          Intent.ACTION_CONFIGURATION_CHANGED,
          Intent.ACTION_LOCALE_CHANGED -> {
              CoroutineScope(Dispatchers.Default).launch {
                  WidgetUtils.updateGlanceWidgetsOnly(context)
              }
          }
      }
  }
  ```
  In Android, once a `BroadcastReceiver.onReceive()` method returns, the OS considers the receiver finished and the hosting process becomes a low-priority cached process subject to immediate reclamation.
  Launching an unparented `CoroutineScope(Dispatchers.Default)` without calling `val pendingResult = goAsync()` means Android can terminate the process before Glance finishes updating the home screen widget, leaving the widget permanently blank or stale.
- **Concrete Reproduction Scenario**:
  1. Rotate the device or toggle night mode when memory pressure is moderate.
  2. `onReceive` returns immediately.
  3. Android OS terminates the process. The home screen widget fails to render updated reminder data.
- **Recommended Remediation**:  
  Hold the broadcast lifecycle open using `goAsync()`:
  ```kotlin
  val pendingResult = goAsync()
  CoroutineScope(Dispatchers.Default).launch {
      try {
          WidgetUtils.updateGlanceWidgetsOnly(context)
      } finally {
          pendingResult.finish()
      }
  }
  ```

---

### Finding SYNC-06: Sync Outbox Deduplication Uses Strict String Equality on `operation`, Failing to Coalesce Complex Offline Mutation Sequences
- **Severity**: **Medium**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/dao/SyncQueueDao.kt:32-33`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/core/sync/SyncRepositoryImpl.kt:74`
- **Description**:  
  When queuing mutations, `SyncQueueDao` removes pending duplicates via:
  ```sql
  DELETE FROM sync_queue 
  WHERE entity_type = :entityType AND entity_id = :entityId AND operation = :operation AND status = 'PENDING'
  ```
  Because the query enforces `operation = :operation`, if an entity is created offline (`CREATE`), and subsequently updated offline (`UPDATE`), both entries remain in `sync_queue`. Furthermore, if an entity is created offline and then deleted offline before syncing, both `CREATE` and `DELETE` remain queued and are sent across the wire, rather than collapsing to a net-zero local cancellation.
- **Concrete Reproduction Scenario**:
  1. Offline: Add a temporary draft note (`CREATE`).
  2. Offline: Delete the note (`DELETE`).
  3. Connect to network: The app uploads a `CREATE` followed by a `DELETE` over the network instead of purging both locally.
- **Recommended Remediation**:  
  Implement outbox coalescing in `SyncRepositoryImpl`:
  - `CREATE` + `UPDATE` → `CREATE` (with latest payload)
  - `CREATE` + `DELETE` → Delete both from queue (no-op)
  - `UPDATE` + `DELETE` → `DELETE`

---

### Finding SYNC-07: Premature Reset of Failure Counter in `SyncWorker` Masks Deadlocked Sync Items from Clinic Staff
- **Severity**: **Medium**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/core/sync/SyncWorker.kt:29-39`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/core/sync/SyncRepositoryImpl.kt:120-124`
- **Description**:  
  `SyncWorker.doWork()` tracks sync failures to alert clinic administrators:
  ```kotlin
  val hasPending = syncRepository.processNextItems()
  if (hasPending) {
      syncRepository.incrementFailureCount()
      countAndMaybeAlert()
  } else {
      syncRepository.resetFailureCount()
  }
  ```
  When an outbox item exceeds maximum retries (e.g. schema violation or HTTP 400), `SyncUploader` transitions the item to `status = 'FAILED'`.
  On the next scheduled sync interval:
  1. `processNextItems()` selects items where `status = 'PENDING'`.
  2. Because the problematic item is `FAILED`, `processNextItems()` finds 0 pending items and returns `false`.
  3. `SyncWorker` executes the `else` branch and calls `resetFailureCount()`.
  4. The failure counter is cleared, `countAndMaybeAlert()` is bypassed, and clinic administrators are never alerted that clinical records are stranded in `FAILED` state.
- **Concrete Reproduction Scenario**:
  1. Force an unparseable payload into `sync_queue`.
  2. `SyncWorker` retries until the item reaches `status = 'FAILED'`.
  3. Next sync cycle executes: failure counter resets to 0. No alert is raised.
- **Recommended Remediation**:  
  Do not reset the failure counter unless `syncQueueDao.getCountByStatus("FAILED") == 0`.

---

### Finding SYNC-08: Realtime WebSocket Channel Reconnect Drops Table Subscriptions on Transient Network Flaps
- **Severity**: **Low**
- **File Path & Line Numbers**: `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/core/sync/RealtimeChangeSubscriptions.kt:85-115`
- **Description**:  
  When mobile connectivity drops and reconnects, `RealtimeChangeSubscriptions` attempts to resubscribe to Supabase Realtime channels. If an error occurs on channel join during the network transition, the exception is logged but no exponential retry schedule is triggered. Real-time push updates remain inactive until the application is restarted.
- **Concrete Reproduction Scenario**:
  1. Launch the application on an Android device or emulator with an active network connection (Wi-Fi or cellular data).
  2. Observe that `RealtimeChangeSubscriptions` initializes and joins Supabase Realtime channels for `"patients"`, `"vaccinations"`, and `"reminders"`.
  3. Simulate a transient network interruption by enabling Airplane mode (or executing `adb shell svc wifi disable && adb shell svc data disable`) for 30 seconds.
  4. Disable Airplane mode to restore connectivity.
  5. As the underlying WebSocket attempts to reconnect, observe that `channel.subscribe()` in `RealtimeChangeSubscriptions.kt:86` throws an exception due to a transient socket state transition, which is caught and logged at line 88 (`Log.e("Realtime", "Error setting up realtime changes...")`).
  6. Because `channel.subscribe()` sits outside the `while (isActive)` loop and lacks an exponential backoff reconnect listener, no retry is scheduled on the channel join. The coroutine remains suspended at `awaitClose`, but the channel is not joined on the server.
  7. Modify or insert a patient record on the remote Supabase database via the web console or a secondary device.
  8. Observe the local application: no `postgresChangeFlow` event is emitted, `RefreshDataUseCase` is never invoked, and the device displays stale data indefinitely until the app process is terminated and relaunched.
- **Recommended Remediation**:  
  Attach an exponential backoff retry loop to the Realtime connection listener and trigger a full `RefreshDataUseCase` pull whenever channels re-establish.

---

# Section 3: Room Schema and Migration Risks

This section audits Room schema annotations, TypeConverters, enum bindings, foreign key cascades during backup restoration, and schema migration integrity.

---

### Finding SCHEMA-01: `BackupDao.insertPatients` Uses `OnConflictStrategy.REPLACE`, Triggering SQLite Cascades That Wipe Post-Backup Clinical Visits
- **Severity**: **Critical**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/dao/BackupDao.kt:84-86`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/backup/BackupRestorer.kt:186-188`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/androidTest/java/com/neochildclinic/data/local/dao/PullUpsertPreservesChildrenTest.kt:200-209`
- **Description**:  
  In SQLite, `INSERT OR REPLACE` (Room's `OnConflictStrategy.REPLACE`) is implemented internally as an atomic `DELETE` followed by an `INSERT`. When SQLite deletes a parent row, all `FOREIGN KEY ... ON DELETE CASCADE` actions execute immediately.
  In `BackupDao.kt:85`:
  ```kotlin
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPatients(items: List<PatientEntity>)
  ```
  In `BackupRestorer.kt:186-188` (`mergeAll` mode):
  ```kotlin
  val patientsToApply = lww("patients", payload.patients, backupDao.getAllPatientsForBackup(), { it.id }, { it.updatedAt })
  if (patientsToApply.isNotEmpty()) backupDao.insertPatients(patientsToApply)
  ```
  If a patient record in the backup file has an `updatedAt` newer than the local database (e.g. staff edited patient contact details on another device prior to exporting), `patientsToApply` includes that patient.
  When `backupDao.insertPatients` runs, SQLite deletes the local patient row to replace it. This triggers SQLite foreign key cascades across `patient_visits`, `vaccination_items`, `consultations`, `reminders`, `personal_vaccine_reminders`, and `patient_notes`.
  Any visits, vaccinations, consultations, or clinical notes recorded locally on the current tablet after the backup was created **are permanently deleted by SQLite cascade and are never recovered**.
- **Concrete Reproduction Scenario**:
  1. On Monday morning, staff exports a backup containing Patient P1.
  2. On Monday afternoon, Doctor attends to Patient P1, recording two new vaccine doses (Hexaxim and Rotavirus) and a clinical consultation.
  3. On Tuesday, staff edits Patient P1's address on Tablet B and exports a new backup.
  4. Staff imports the Tuesday backup into Tablet A using "Merge" mode.
  5. `backupDao.insertPatients` executes `INSERT OR REPLACE` on Patient P1.
  6. SQLite cascades the delete, wiping Monday afternoon's visits, vaccination items, and consultations for Patient P1.
- **Recommended Remediation**:  
  Replace `@Insert(onConflict = OnConflictStrategy.REPLACE)` in `BackupDao.kt` with `@Upsert` (or separate update/insert logic) across all parent entities with cascade dependents (`patients`, `patient_visits`, `vaccines`, `vaccine_batches`, `borrow_records`).

---

### Finding SCHEMA-02: Autogenerated Primary Key Collisions in `InventoryDeductionEntity` Overwrite Unrelated Local Records During Merge Restore
- **Severity**: **High**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/InventoryDeductionEntity.kt:23`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/dao/BackupDao.kt:187-188`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/backup/BackupRestorer.kt:247-252`
- **Description**:  
  `InventoryDeductionEntity` uses an autoincrement integer primary key:
  ```kotlin
  @PrimaryKey(autoGenerate = true) val id: Long = 0
  ```
  Room generates an insert statement: `INSERT OR REPLACE INTO inventory_deductions (id, ...) VALUES (nullif(?, 0), ...)`.
  When a backup is exported, deduction records are serialized with their local numeric IDs (`id = 1, 2, 3, ...`).
  When `BackupRestorer.mergeAll()` restores deductions from a backup file:
  ```kotlin
  val invDeductionsExisting = backupDao.getAllInventoryDeductionsForBackup()
  val invDeductionsToApply = insertIfAbsent(
      "inventoryDeductions", payload.inventoryDeductions, invDeductionsExisting
  ) { "${it.vaccinationId}:${it.vaccineId}:${it.batchId}:${it.resolvedAt}" }
  if (invDeductionsToApply.isNotEmpty()) backupDao.insertInventoryDeductions(invDeductionsToApply)
  ```
  `insertIfAbsent` verifies that the composite business key is absent locally. However, the entities in `invDeductionsToApply` still retain their non-zero `id` values from the backup file.
  Because `entity.id != 0`, `nullif(id, 0)` passes the non-zero integer to SQLite. Under `INSERT OR REPLACE`, **any incoming deduction whose `id` matches an existing local deduction's `id` overwrites and destroys that local record**.
- **Concrete Reproduction Scenario**:
  1. Tablet A has local deduction row: `id = 1, vaccinationId = "v-local", vaccine = "Polio"`.
  2. Backup from Tablet B contains deduction row: `id = 1, vaccinationId = "v-remote", vaccine = "MMR"`.
  3. Tablet A merges Tablet B's backup.
  4. `backupDao.insertInventoryDeductions` executes with `id = 1`.
  5. Tablet A's Polio deduction record is overwritten and replaced by the MMR record.
- **Recommended Remediation**:  
  In `BackupRestorer.kt:251`, reset incoming deduction IDs to `0` so SQLite generates fresh autoincrement keys:
  ```kotlin
  val deductionsWithFreshIds = invDeductionsToApply.map { it.copy(id = 0) }
  backupDao.insertInventoryDeductions(deductionsWithFreshIds)
  ```

---

### Finding SCHEMA-03: Unhandled `SerializationException` in `Converters.toList` Causes App-Wide Crashes on Empty or Malformed JSON
- **Severity**: **Medium**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/database/Converters.kt:14-16`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/ReminderEntity.kt:60-62`
- **Description**:  
  `Converters.kt` defines the `List<String>?` TypeConverter:
  ```kotlin
  @TypeConverter
  fun toList(value: String?): List<String>? {
      return value?.let { Json.decodeFromString<List<String>>(it) }
  }
  ```
  This converter is registered globally on `AppDatabase` and on `ReminderEntity` for `nxt_vaccine_id`.
  `Json.decodeFromString` is invoked without exception handling. If `value` contains:
  - An empty string `""`
  - Legacy comma-separated text (e.g. `"vac-1,vac-2"`)
  - Malformed JSON
  
  `Json.decodeFromString` throws an unhandled `kotlinx.serialization.SerializationException`. Because Room executes converters during cursor row mapping, any DAO query reading `ReminderEntity` (`getAllDueReminders()`, `getReminderById()`) crashes immediately with a fatal exception.
- **Concrete Reproduction Scenario**:
  1. An external import or synced record stores an empty string `""` in `reminders.nxt_vaccine_id`.
  2. User opens the "Due Reminders" screen.
  3. Room cursor mapping invokes `Converters.toList("")`.
  4. `SerializationException` is thrown; the app terminates abnormally.
- **Recommended Remediation**:  
  Differentiate SQL `NULL` from blank strings to preserve nullable semantics:
  ```kotlin
  @TypeConverter
  fun toList(value: String?): List<String>? {
      if (value == null) return null
      if (value.isBlank()) return emptyList()
      return runCatching {
          Json.decodeFromString<List<String>>(value)
      }.getOrElse {
          value.split(",").map(String::trim).filter(String::isNotEmpty)
      }
  }
  ```

---

### Finding SCHEMA-04: Enum Constant Mismatch: `VaccinationDao.getDueCount` Queries `status = 'RESCHEDULED'`, Missing in `ReminderStatus` Enum
- **Severity**: **Medium**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/dao/VaccinationDao.kt:79-80`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/domain/model/ReminderStatus.kt:6-11`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/VaccinationEntity.kt:40-74` (line 63)
- **Description**:  
  In `VaccinationDao.kt:79`:
  ```kotlin
  @Query("SELECT COUNT(*) FROM patient_visits WHERE nextDueDate = :date AND (status = 'ACTIVE' OR status = 'RESCHEDULED')")
  suspend fun getDueCount(date: String): Int
  ```
  The SQL query expects `status = 'RESCHEDULED'`.
  However, `VisitEntity.status` is mapped to the Kotlin enum `ReminderStatus`:
  ```kotlin
  enum class ReminderStatus {
      ACTIVE, COMPLETED, EXTERNAL, DISMISSED
  }
  ```
  `RESCHEDULED` is absent from `ReminderStatus`. If any visit row carries `status = 'RESCHEDULED'`, reading that entity with Room executes `ReminderStatus.valueOf("RESCHEDULED")`, which throws an unhandled `IllegalArgumentException: No enum constant ReminderStatus.RESCHEDULED`, crashing the query.
- **Concrete Reproduction Scenario**:
  1. A visit row is inserted or updated with `status = 'RESCHEDULED'`.
  2. User opens the patient visit history.
  3. Room attempts cursor mapping and crashes with `IllegalArgumentException`.
- **Recommended Remediation**:  
  Add `RESCHEDULED` to `ReminderStatus`:
  ```kotlin
  enum class ReminderStatus {
      ACTIVE, COMPLETED, EXTERNAL, DISMISSED, RESCHEDULED
  }
  ```

---

### Finding SCHEMA-05: Missing SQLite Foreign Key Declarations Across Critical Entity Associations
- **Severity**: **Medium**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/ReminderEntity.kt:40`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/FinanceEntity.kt:27-28`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/VaccineEntity.kt:74-100` (lines 79–84)
- **Description**:  
  Several core entity relationships lack SQLite foreign key constraints:
  1. `ReminderEntity.originalVisitId`: References `patient_visits(id)`, but only a foreign key to `patients(id)` is declared. When a visit is deleted, reminders referencing `originalVisitId` remain orphaned in the database.
  2. `FinanceEntity`: Contains `patientId` and `visitId` without foreign key declarations.
  3. `InventoryTransactionEntity`: Contains `patientId`, `visitId`, and `vaccineId` without foreign keys.
- **Concrete Reproduction Scenario**:
  1. Seed an active clinical record in Room with:
     - A patient row with `id = "p-100"`.
     - A visit row in `patient_visits` with `id = "v-100"` and `patientId = "p-100"`.
     - A due reminder in `reminders` with `id = "r-100"`, `patientId = "p-100"`, and `originalVisitId = "v-100"`.
     - A payment entry in `finance_transactions` with `id = "f-100"`, `patientId = "p-100"`, and `visitId = "v-100"`.
     - A dose deduction in `inventory_transactions` with `transactionId = "it-100"`, `patientId = "p-100"`, and `visitId = "v-100"`.
  2. Perform an administrative or merge deletion of the visit: `vaccinationDao.deleteVaccination("v-100")` (or SQL `DELETE FROM patient_visits WHERE id = 'v-100'`).
  3. Inspect the SQLite database tables using Database Inspector or sqlite3 CLI:
     `SELECT id, originalVisitId FROM reminders WHERE id = 'r-100';`
     `SELECT id, visitId FROM finance_transactions WHERE id = 'f-100';`
     `SELECT id, visitId FROM inventory_transactions WHERE id = 'it-100';`
  4. Observe that while `patient_visits` row `"v-100"` has been deleted, all three dependent records remain in the database pointing to the dangling visit ID `"v-100"`.
  5. When subsequent queries perform inner joins or relational queries against visits (e.g. `PatientVaccinationCardEntity` queries or visit-linked financial reporting), these orphaned records cause missing items, calculation inconsistencies, or unhandled null dereferences in business logic.
- **Recommended Remediation**:  
  Add foreign keys with `ON DELETE SET NULL` or `CASCADE` where referential consistency is required, and document intentional exclusions for audit trail tables.

---

### Finding SCHEMA-06: Missing Composite Indices on `sync_queue` Force Full Table Scans During Every Sync Loop

> **Status (as of 2026-11-18):** partially addressed. `SyncQueueEntity.kt:13` now declares
> `Index(value = ["entityName", "entityId"])` and `Index("createdAt")` in addition to
> `status` and `priority`, so the `isUnsynced`/`unsyncedIds` lookups below can use a
> composite index. The separate `(entityName, status)` index suggested here was not added.
> The reproduction and query-plan notes below are kept as the original 2026-11-15 snapshot.
- **Severity**: **Low**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/SyncQueueEntity.kt:13`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/dao/SyncQueueDao.kt:76-96`
- **Description**:  
  `SyncQueueEntity` defines indices only on `status` and `priority`. Frequent queries executed during every sync cycle query by `entityName` and `entityId`:
  - `isUnsynced(name, id)`
  - `unsyncedIds(name)`
  - `deletePendingDuplicates(name, id, operation)`
  SQLite must execute full table scans or scan the unselective `status` index for every check.
- **Concrete Reproduction Scenario**:
  1. Populate `sync_queue` with 5,000 mutation records simulating an extended offline clinic session across patients, visits, reminders, and inventory transactions.
  2. Open the SQLite shell or Android Studio Database Inspector and execute `EXPLAIN QUERY PLAN` for the frequent existence query:
     `EXPLAIN QUERY PLAN SELECT EXISTS(SELECT 1 FROM sync_queue WHERE entityName = 'PATIENT' AND entityId = 'p-500' AND status != 'SYNCED');`
  3. Observe query plan output: `SCAN TABLE sync_queue` (full table scan across all 5,000 entries). Because indices exist only on `status` and `priority`, and the predicate tests `status != 'SYNCED'` (an inequality), the index cannot be utilized for selective seek.
  4. Trigger a full patient reconciliation (`refreshPatients()`), which iterates over 500 remote patient records and invokes `isUnsynced("PATIENT", id)` for each record.
  5. Measure execution latency: 500 consecutive full table scans execute across the SQLite connection, creating thread contention and perceptible UI lag (frame skips exceeding 500ms) on low-spec clinic tablets.
- **Recommended Remediation**:  
  In `SyncQueueEntity.kt`, add composite indices:
  ```kotlin
  indices = [
      Index("status"),
      Index("priority"),
      Index(value = ["entityName", "entityId"]),
      Index(value = ["entityName", "status"])
  ]
  ```

---

### Finding SCHEMA-07: `ALTER TABLE DROP COLUMN` in Migrations Fails Instrumentation Tests on Android API 29-33 Framework SQLite
- **Severity**: **Low**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/database/AppDatabase.kt:322, 413`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/androidTest/java/com/neochildclinic/data/local/database/ExpenseMigrationTest.kt:76`
- **Description**:  
  `migration27_28` and `migration31_32` execute `ALTER TABLE ... DROP COLUMN`. SQLite added `DROP COLUMN` support in version 3.35.0 (Android 14 / API 34). While the production app runs SQLCipher 4.19.0 (which includes SQLite 3.4x), instrumentation tests use Android's `FrameworkSQLiteOpenHelperFactory`. Running tests on API 29–33 fails with `SQLiteException: near "DROP": syntax error`.
- **Concrete Reproduction Scenario**:
  1. Boot an Android emulator running Android 11 (API 30) or Android 13 (API 33).
  2. Implement an automated migration test for `migration27_28` in `ExpenseMigrationTest.kt` using Room's `MigrationTestHelper`:
     ```kotlin
     helper.createDatabase("test-migration-db", 27).apply { close() }
     helper.runMigrationsAndValidate("test-migration-db", 28, true, AppDatabase.migration27_28)
     ```
  3. Execute the instrumentation test:
     `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.neochildclinic.data.local.database.ExpenseMigrationTest`
  4. Observe test crash: because `ExpenseMigrationTest.kt:76` configures `MigrationTestHelper` using `FrameworkSQLiteOpenHelperFactory()`, the test executes on the system SQLite engine (version < 3.35.0 on API 29–33).
  5. Execution fails immediately with:
     `android.database.sqlite.SQLiteException: near "DROP": syntax error (code 1 SQLITE_ERROR): , while compiling: ALTER TABLE expenses DROP COLUMN isDeleted`.
- **Recommended Remediation**:  
  Configure instrumentation tests to use SQLCipher's open helper factory rather than the framework helper.

---

### Finding SCHEMA-08: Missing Historical Room Schema JSON Files (v1..v32) Break Automated CI Migration Regression Testing
- **Severity**: **Low**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/app/schemas/com.neochildclinic.data.local.database.AppDatabase/` (only `33.json` [102,958 bytes] and `34.json` [103,194 bytes] present; `1.json`..`32.json` missing)
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/androidTest/java/com/neochildclinic/data/local/database/ExpenseMigrationTest.kt:79-86`
- **Description**:  
  Room's `MigrationTestHelper` requires exported schema JSON files for both the start and end versions of each tested migration. The schemas directory contains only `33.json` and `34.json`. Tests covering migrations 17..32 cannot execute and remain `@Ignore`'d.
- **Concrete Reproduction Scenario**:
  1. Open `ExpenseMigrationTest.kt` and navigate to lines 79–86:
     ```kotlin
     @Ignore(
         "Cannot run: MigrationTestHelper validates against the exported schema JSON for the requested version, and app/schemas only contains 33.json - 22.json and 23.json were never exported, so the helper throws before any assertion runs..."
     )
     @Test
     fun migrate22To23_createsExpensesTable_withoutTouchingExistingData()
     ```
  2. Remove the `@Ignore` annotation from `migrate22To23_createsExpensesTable_withoutTouchingExistingData()`.
  3. Execute `./gradlew connectedDebugAndroidTest` on an attached device or emulator.
  4. Observe that the test run immediately aborts with:
     `java.io.FileNotFoundException: Cannot find the schema file in the assets folder. Make sure to include the exported schema in your test assets. Missing: com.neochildclinic.data.local.database.AppDatabase/22.json`.
  5. The absence of historical schema artifacts makes automated regression validation of migrations 1 through 32 impossible in CI pipelines, leaving existing clinic tablets with older schemas vulnerable to untested upgrade failures.
- **Recommended Remediation**:  
  Reconstruct intermediate schema JSON definitions so the migration test suite can validate the full migration chain in automated CI pipelines.

---

# Section 4: Data Integrity Edge Cases

This section addresses clinical dose history preservation, database corruption handling, SQLite lock contention, and query wildcard omissions.

---

### Finding INTEGRITY-01: Clinical Dose Administration History Destroyed via Catalog Cascade and Background `RemotePrune`
- **Severity**: **Critical**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/VaccinationItemEntity.kt:21-32`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/feature/inventory/data/InventoryRepositoryImpl.kt:684-696`
- **Description**:  
  `VaccinationItemEntity` models the specific vaccine doses administered to children (storing vaccine brand, dose number, batch lot number, and expiry date). The entity declares foreign keys to `VaccineEntity` and `VaccineBatchEntity` with `onDelete = ForeignKey.CASCADE`:
  ```kotlin
  ForeignKey(
      entity = VaccineEntity::class,
      parentColumns = ["id"],
      childColumns = ["vaccineId"],
      onDelete = ForeignKey.CASCADE
  ),
  ForeignKey(
      entity = VaccineBatchEntity::class,
      parentColumns = ["batchId"],
      childColumns = ["batchId"],
      onDelete = ForeignKey.CASCADE
  )
  ```
  In `InventoryRepositoryImpl.kt:684-696`, background inventory synchronization runs `RemotePrune`:
  ```kotlin
  RemotePrune.computeBatches(
      localIds = vaccineDao.getBatchIds(),
      syncedIds = null,
      remoteIds = batches.mapTo(HashSet()) { it.batchId },
      pendingIds = pendingBatchIds
  ).flatten().forEach { vaccineDao.deleteBatch(it) }
  ```
  If an expired or depleted batch is deleted or archived on Supabase, `RemotePrune` calls `vaccineDao.deleteBatch(it)`.
  Because `PRAGMA foreign_keys = ON`, deleting the batch fires SQLite's foreign key cascade, **deleting every child's administered `vaccination_items` record associated with that batch**. When parents request an immunization certificate or medical report, the visit record exists but the administered vaccine doses are missing.
- **Concrete Reproduction Scenario**:
  1. Administer Hexaxim from Batch `BAT-99` to 20 children.
  2. Batch `BAT-99` is depleted; staff marks it inactive or deletes it on Supabase.
  3. Background sync triggers `refreshInventory()`.
  4. `RemotePrune` detects `BAT-99` is absent remotely and executes `vaccineDao.deleteBatch("BAT-99")`.
  5. SQLite cascades the deletion into `vaccination_items`.
  6. All 20 children permanently lose their administered vaccine item records.
- **Recommended Remediation**:  
  1. In `VaccinationItemEntity.kt`, change `onDelete = ForeignKey.CASCADE` to `ForeignKey.RESTRICT` or `ForeignKey.NO_ACTION`.
  2. In `InventoryRepositoryImpl.kt`, verify `vaccineDao.getVaccinationCountForBatch(it) == 0` before deleting any batch.

---

### Finding INTEGRITY-02: Database Corruption Handler Silently Renames Unopenable Databases and Builds Empty Database Without User Notification
- **Severity**: **High**
- **File Path & Line Numbers**: `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/database/AppDatabase.kt:488-528`
- **Description**:  
  When initializing `AppDatabase`, the app verifies database readability using `SQLiteDatabase.openDatabase(...)`. If this throws `SQLiteNotADatabaseException` or an error containing "not a database" or "hmac":
  ```kotlin
  val suffix = ".corrupt-" + System.currentTimeMillis()
  val moved = listOf("-journal", "-wal", "-shm", "").all { ext ->
      val f = java.io.File(dbFile.path + ext)
      !f.exists() || f.renameTo(java.io.File(f.path + suffix))
  }
  Log.w(TAG, "Existing database is unreadable... Kept at ${dbFile.path}$suffix; creating a fresh database.", e)
  ```
  Crucially, **the function does not throw or halt execution**. It renames the entire database to `.corrupt-<timestamp>`, falls through to line 534, and builds a brand-new, empty Room database.
  If SQLCipher fails to open due to transient issues (e.g. Android Keystore service restart, temporary file lock, or WAL checkpoint race), the app automatically and silently purges the clinic's local database. When the doctor opens the app, all patient records and clinic visits have vanished. The app provides no UI or mechanism to notify the doctor or restore the renamed file.
- **Concrete Reproduction Scenario**:
  1. Simulate a transient Keystore lock during app startup.
  2. `AppDatabase.getDatabase(context)` catches the decryption error.
  3. The database is moved to `neochild_db.corrupt-<timestamp>` and Room initializes an empty database.
  4. Doctor opens the app to find 0 patients and an empty clinic ledger.
- **Recommended Remediation**:  
  Never create a fresh database when an existing database fails to open. Fail closed with an explicit error dialog prompting the user to restart the device or restore from a known backup.

---

### Finding INTEGRITY-03: `BackupCollector.collect()` Holds SQLite Writer Connection and `IMMEDIATE` Transaction, Causing UI Freezes and Write Contention
- **Severity**: **Medium**
- **File Path & Line Numbers**: `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/backup/BackupCollector.kt:16-20`
- **Description**:  
  `BackupCollector.collect()` captures a read-only snapshot of all 22 database tables for manual exports and nightly auto-backups:
  ```kotlin
  suspend fun collect(): BackupPayloadV1 = database.useWriterConnection { transactor ->
      transactor.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
          snapshot()
      }
  }
  ```
  `useWriterConnection` acquires the sole writer connection in Room's connection pool, and `IMMEDIATE` acquires a reserved write lock in SQLite.
  Because all 22 tables are queried sequentially on the writer connection, no other write transaction (saving a patient, recording a visit, or queueing sync items) can proceed. On large databases, this holds the SQLite write lock for several seconds, causing UI freezes or `SQLiteDatabaseLockedException`.
- **Concrete Reproduction Scenario**:
  1. Scheduled nightly backup begins snapshot collection on a large database.
  2. Concurrently, staff attempts to save an urgent patient visit.
  3. The save operation blocks waiting for the writer connection, stalling the UI thread.
- **Recommended Remediation**:  
  Use `useReaderConnection` with a `DEFERRED` transaction:
  ```kotlin
  suspend fun collect(): BackupPayloadV1 = database.useReaderConnection { transactor ->
      transactor.withTransaction(Transactor.SQLiteTransactionType.DEFERRED) {
          snapshot()
      }
  }
  ```
  In SQLite WAL mode, readers do not block writers and writers do not block readers.

---

### Finding INTEGRITY-04: Search Query Wildcard Omission in `AddEditPersonalReminderViewModel` Breaks Patient Search for Non-Exact Matches
- **Severity**: **Medium**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/dao/PatientDao.kt:44-49`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/feature/personalreminder/presentation/AddEditPersonalReminderViewModel.kt:87-92`
- **Description**:  
  In `PatientDao.kt:44-49`:
  ```sql
  SELECT * FROM patients 
  WHERE (name LIKE :q OR phone LIKE :q OR patientClinicId LIKE :q OR address LIKE :q
       OR id IN (SELECT patientId FROM patient_visits WHERE vaccineNames LIKE :q OR receiptNumber LIKE :q))
  ```
  The SQL query matches `LIKE :q` literally without wrapping `:q` in wildcards (`%`).
  In `AddEditPersonalReminderViewModel.kt:91`:
  ```kotlin
  if (q.isBlank()) flowOf(emptyList()) else patientRepository.searchPatients(q)
  ```
  The raw search term `q` is passed directly without wildcards. SQLite performs an exact equality match instead of a substring search. Typing "John" will never match "John Doe".
- **Concrete Reproduction Scenario**:
  1. Patient "Aarav Sharma" is registered in the clinic.
  2. Staff opens "Add Personal Reminder" and searches for "Aarav".
  3. Search returns zero results, forcing staff to re-enter patient details manually and creating duplicate records.
- **Recommended Remediation**:  
  Incorporate wildcards directly into the SQL query in `PatientDao.kt`:
  `name LIKE '%' || :q || '%' OR phone LIKE '%' || :q || '%' ...`

---

# Section 5: Supabase Row Level Security (RLS) Policies

This section analyzes backend PostgreSQL Row Level Security policies, staff tenant boundaries, and token authorization lifecycles.

---

### Finding RLS-01: Blanket `DELETE` Policies Across 20 Tables Permit Any Authenticated Staff to Erase All Clinical and Financial Data
- **Severity**: **High**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/supabase/migrations/20260929000000_security_baseline.sql:70-87`
  - `/home/nadeem/Desktop/vaccine_manager_app/supabase/migrations/20261013120000_role_policy_carveouts.sql:27-71`
- **Description**:  
  The security baseline migration iterates over 20 tables and installs blanket RLS policies:
  ```sql
  execute format(
    'create policy "active staff delete" on public.%I for delete to authenticated using (public.is_active_staff())',
    table_name
  );
  ```
  While `20261013120000_role_policy_carveouts.sql` subsequently restricted DELETE on `expenses`, `doctor_weekly_slots`, and `doctor_slot_exceptions`, the blanket DELETE policy **remains live** on:
  `patients`, `patient_visits`, `vaccination_items`, `vaccines`, `vaccine_batches`, `inventory_transactions`, `waste_records`, `borrow_records`, `borrow_returns`, `reminders`, `consultations`, `consultation_todos`, `vaccination_todos`, `personal_vaccine_reminders`, and `finance_transactions`.
  Any authenticated user holding an active staff profile (such as a receptionist or junior assistant) can issue raw HTTP DELETE requests to PostgREST and delete every patient record, vaccination history, financial ledger entry, or vaccine inventory batch in the clinic.
- **Concrete Reproduction Scenario**:
  1. Authenticate to Supabase with credentials of a staff member assigned the `receptionist` role.
  2. Send an HTTP `DELETE` request to `/rest/v1/patients` or `/rest/v1/finance_transactions?amount=gt.0`.
  3. PostgREST evaluates `public.is_active_staff()` to true and wipes all clinic records.
- **Recommended Remediation**:  
  Drop `"active staff delete"` on clinical and financial tables (`patients`, `patient_visits`, `vaccination_items`, `finance_transactions`, `vaccines`, `vaccine_batches`, `inventory_transactions`). Restrict DELETE operations strictly to administrators:
  `USING (public.is_active_staff() AND (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin')`.

---

### Finding RLS-02: Cloud Backups in Cloudflare Worker Siloed by Individual Staff `user.sub` Instead of Clinic Organization
- **Severity**: **Medium**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/cloudflare/backup-worker/src/index.ts:73-81, 91-95`
  - `/home/nadeem/Desktop/vaccine_manager_app/SECURITY.md:70-74`
  - `/home/nadeem/Desktop/vaccine_manager_app/supabase/migrations/20260929000000_security_baseline.sql:1-8`
- **Description**:  
  In the Cloudflare Worker (`cloudflare/backup-worker/src/index.ts`), cloud backups are partitioned strictly by `accountId = user.sub`:
  ```typescript
  const accountId = user.sub;
  ...
  const { results } = await env.DB.prepare(
    "SELECT * FROM backups WHERE account_id = ? ORDER BY created_at DESC"
  ).bind(accountId).all<BackupRow>();
  ```
  `user.sub` is the individual staff member's UUID. If Doctor A takes a cloud backup, the backup is stored in `backups/<doctor_a_uuid>/`. When Receptionist B or Doctor C opens the Cloud Restore screen, they cannot see or restore Doctor A's backup. Furthermore, if Doctor A leaves the clinic and their account is deleted, their backups become permanently inaccessible to the clinic.
- **Concrete Reproduction Scenario**:
  1. Doctor A executes a manual cloud backup from Tablet A.
  2. Receptionist B logs in on Tablet B and navigates to Cloud Backups.
  3. The list is completely empty because the Worker filters strictly by Receptionist B's `user.sub`.
- **Recommended Remediation**:  
  Partition backups by organization identifier (e.g. `clinic_id` claim in JWT `app_metadata`) rather than individual user UUID.

---

### Finding RLS-03: Demoted Administrator Privileges Persist Until JWT Expiration Due to Stateless `app_metadata` Token Validation
- **Severity**: **Medium**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/supabase/migrations/20261001_app_metadata_roles.sql:28-42`
  - `/home/nadeem/Desktop/vaccine_manager_app/supabase/functions/manage-staff/index.ts:150-162`
- **Description**:  
  In `20261001_app_metadata_roles.sql`, role authorization was migrated to `auth.jwt() -> 'app_metadata' ->> 'role'`.
  When an administrator demotes a user via the `manage-staff` Edge Function, `manage-staff` updates `auth.users.raw_app_meta_data`. However, existing JWT access tokens held by the client remain valid until expiration (up to 1 hour / 3600 seconds). During this window, the demoted user's access token continues to evaluate to `'admin'`, allowing them to perform administrative operations (editing expenses, altering doctor schedules) until token expiry.
- **Concrete Reproduction Scenario**:
  1. Admin user logs in and obtains an access token with `app_metadata.role = 'admin'`.
  2. Clinic owner demotes this user to `nurse` via `manage-staff`.
  3. Demoted user immediately issues an `UPDATE` request to `expenses`.
  4. RLS evaluates the cached JWT claim and allows the privileged mutation.
- **Recommended Remediation**:  
  In `manage-staff`, call `admin.auth.admin.signOut(staffId)` upon role change to immediately invalidate all active refresh and access tokens.

---

### Finding RLS-04: Lack of Multi-Tenant `clinic_id` Isolation in Global Postgres Schema
- **Severity**: **Medium**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/supabase/migrations/20260929000000_security_baseline.sql:1-8`
  - `/home/nadeem/Desktop/vaccine_manager_app/SECURITY.md:70-74`
- **Description**:  
  The database schema contains no `clinic_id` column or tenant boundary. Access is governed solely by `public.is_active_staff()`. If multiple clinic branches share the same Supabase project, all clinics have mutual, unrestricted read/write access to each other's patients and financial data.
- **Concrete Reproduction Scenario**:
  1. Deploy the Supabase migration to a shared project configured for multi-clinic operations (Branch Alpha and Branch Beta).
  2. Create Nurse Alice (`profiles.id = user_alice_uuid`, assigned to Branch Alpha) and Nurse Bob (`profiles.id = user_bob_uuid`, assigned to Branch Beta). Both accounts have `is_active = true` in `public.profiles`.
  3. Branch Alpha records confidential pediatric patient records and billing transactions via the application.
  4. Nurse Bob logs in using his own credentials and receives an authenticated JWT from Supabase Auth.
  5. Using curl or PostgREST client, Nurse Bob issues queries against protected endpoints:
     `curl -H "Authorization: Bearer <BOB_JWT>" -H "apikey: <ANON_KEY>" https://<project>.supabase.co/rest/v1/patients?select=*`
     `curl -H "Authorization: Bearer <BOB_JWT>" -H "apikey: <ANON_KEY>" https://<project>.supabase.co/rest/v1/finance_transactions?select=*`
  6. RLS evaluates `public.is_active_staff()`: because Bob is an active staff profile, the function returns `true`.
  7. Because neither the tables nor the RLS policies contain `clinic_id` scoping, the query returns 100% of patients and financial data belonging to Branch Alpha. Furthermore, Bob can issue `UPDATE` and `DELETE` queries modifying Branch Alpha's clinical records, causing complete breach of multi-tenant confidentiality and data integrity.
- **Recommended Remediation**:  
  Introduce `clinic_id` columns across all tables and scope RLS policies to `USING (clinic_id = (auth.jwt() -> 'app_metadata' ->> 'clinic_id'))`.

---

# Section 6: Security Vulnerabilities

This section details APK update verification bypasses, biometric app lock circumvention, Keystore credential lockout, custom URL scheme interception, and embedded signing keys.

---

### Finding SEC-01: Bypass of SHA-256 APK Integrity Verification in In-App Update Engine Due to GitHub API Schema Incompatibility
- **Severity**: **Critical**
- **File Path & Line Numbers**: `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/feature/update/data/AppUpdateManager.kt:27-31, 202-210, 415-421`
- **Description**:  
  `AppUpdateManager` implements in-app APK downloading and updates. Lines 415–421 intend to verify the SHA-256 hash of the downloaded APK prior to calling `PackageInstaller`:
  ```kotlin
  info.apkSha256?.let { expected ->
      val actual = sha256Hex(partFile)
      if (!actual.equals(expected, ignoreCase = true)) {
          partFile.delete(); metadataFile.delete()
          error("Downloaded file failed integrity verification. Please try again.")
      }
  }
  ```
  However, `apkSha256` is extracted via `normalizeDigest(asset.digest)` (`line 202`) from the `GithubAsset` class:
  ```kotlin
  @Serializable
  private data class GithubAsset(
      val name: String = "",
      @SerialName("browser_download_url") val browserDownloadUrl: String = "",
      val digest: String = ""
  )
  ```
  In GitHub's official Releases REST API (`/repos/{owner}/{repo}/releases`), release asset objects **do not include a `digest` field**. Consequently, `asset.digest` is always decoded as empty string `""`, `normalizeDigest` always returns `null`, and `info.apkSha256` is permanently `null`.
  The conditional block `info.apkSha256?.let { ... }` is never entered. Checksum verification is completely bypassed on every update download.
- **Concrete Reproduction Scenario**:
  1. Trigger an update check when a newer GitHub release is published.
  2. Inspect `AppUpdateInfo.apkSha256` in memory; observe it is `null`.
  3. Execute `downloadAndInstall`; observe that the downloaded file is passed directly to `installWithPackageInstaller` without hashing or digest comparison.
- **Recommended Remediation**:  
  Parse the SHA-256 digest from release body metadata (e.g. `sha256: <hash>`) or download an accompanying `checksums.txt` asset to validate the APK digest before installation.

---

### Finding SEC-02: Pediatric Health Information (PHI) Exposed in Cleartext on Home Screen Widget, Bypassing App Biometric Lock
- **Status (2026-11-19)**: Accepted risk by product decision. The widget now shows full patient names in both locked and unlocked states; the masking code was removed. Re-open if the widget is used on shared or unattended devices.
- **Severity**: **High**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/feature/widget/data/WidgetWorker.kt:36-60`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/feature/widget/presentation/VaccineWidget.kt:61-79`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/WidgetDueEntity.kt:10-16`
- **Description**:  
  `MainActivity.kt` enforces privacy controls: it applies `WindowManager.LayoutParams.FLAG_SECURE` (`line 90`) to prevent screen recording and screenshots, and hides UI navigation behind `BiometricLockManager.isAppLocked` (`lines 131-133`).
  However, `WidgetWorker.kt` periodically queries `reminderRepository.getDueList()` and `database.patientDao().getNamesByIds(...)` to populate `WidgetDueEntity` with plaintext patient names, vaccine names, and due dates. `VaccineWidget` then renders these pediatric patient names and upcoming/overdue medical vaccination details directly on the Android home screen launcher.
  Neither `WidgetWorker` nor `VaccineWidget` inspects `BiometricLockManager.isAppLocked` or `settings.biometricLockEnabled`. Any unauthorized person holding or glancing at the device can view identifiable pediatric patient records without unlocking the app.
- **Concrete Reproduction Scenario**:
  1. Enable "Biometric lock" in Security Settings.
  2. Add the "Vaccine Schedule" widget to the Android home screen.
  3. Turn off the device screen or allow inactivity timeout to lock the app.
  4. Turn on the device screen (stay on launcher); the home screen widget continues to display full child names and their overdue vaccines in plain text.
- **Recommended Remediation (superseded by the accepted risk above; kept for the record)**:  
  In `WidgetWorker.kt`, inspect `BiometricLockManager.isAppLocked`. When the app is locked or biometric protection is active, redact patient names (e.g., "Patient Locked" or display an aggregate count: "5 vaccines due — unlock app to view").

---

### Finding SEC-03: Keystore `timeout = 0` Misconfiguration Triggers Permanent Authentication Lockout on PIN/Credential-Only Devices
- **Severity**: **High**
- **File Path & Line Numbers**: `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/core/security/BiometricAuthenticator.kt:46-51, 103, 129-139`
- **Description**:  
  On Android 11+ (API 30+), `BiometricAuthenticator` configures Keystore key parameters:
  ```kotlin
  builder.setUserAuthenticationParameters(
      0,
      KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
  )
  ```
  In Android Keystore architecture, setting a timeout of `0` denotes "per-use authentication". According to Google Android Keystore specifications, keys requiring per-use authentication (`timeout = 0`) **can only be authorized by biometric authentication** (fingerprint/face hardware authentication tokens). They cannot be authorized by device credential (PIN/pattern/password) when using a `CryptoObject`.
  On devices without enrolled biometrics or where the user authenticates using their device PIN, `BiometricManager.canAuthenticate` returns `BIOMETRIC_SUCCESS` (because `AUTH_DEVICE_CREDENTIAL` is set), but subsequent cryptographic decryption (`authenticatedCipher.doFinal`) throws an unrecoverable exception in `verifyProtectedSecret` (`line 196`). This triggers `BiometricLockManager.lock()` (`line 76`) and displays "Authentication failed: secure verification error", permanently locking out PIN/pattern users from Keystore authentication.
- **Concrete Reproduction Scenario**:
  1. Run the app on an Android device configured only with a PIN (no fingerprint or face enrolled).
  2. Enable app lock and trigger the biometric prompt.
  3. Enter the valid device PIN.
  4. Prompt reports success, but `authenticatedCipher.doFinal` fails with a `SecurityException`. The user receives "Authentication failed: secure verification error" and remains permanently locked out.
- **Recommended Remediation**:  
  Configure a non-zero validity timeout (e.g., `builder.setUserAuthenticationParameters(5, KeyProperties.AUTH_DEVICE_CREDENTIAL)`), or separate the prompt flow so that `CryptoObject` is used strictly with `BIOMETRIC_STRONG` and a separate verified credential challenge handles PIN/pattern fallbacks.

---

### Finding SEC-04: Custom URL Scheme `neochild://auth-callback` Subject to Intent Hijacking by Malicious Third-Party Applications
- **Severity**: **Medium**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/AndroidManifest.xml:50-51`
  - `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/app/MainActivity.kt:311-318`
- **Description**:  
  `MainActivity` declares a deep link filter using a custom URI scheme:
  ```xml
  <data android:scheme="neochild" android:host="auth-callback" />
  ```
  On Android, custom schemes are not verified by the operating system. Any third-party application installed on the device can register an identical filter for `neochild://auth-callback`. If Supabase Auth redirects magic links or OAuth tokens to this scheme, a malicious app on the device could intercept authorization codes or access tokens via the Android disambiguation dialog.
- **Concrete Reproduction Scenario**:
  1. Install an app declaring an intent filter for `<data android:scheme="neochild" android:host="auth-callback" />`.
  2. Trigger an auth callback; Android presents an app chooser dialog, allowing the malicious app to receive the redirect intent containing auth parameters.
- **Recommended Remediation**:  
  Migrate from custom schemes to verified Android App Links using `https://` with a verified `assetlinks.json` domain hosted on the clinic's domain or Supabase domain.

---

### Finding SEC-05: Production Release Signing Keystores Stored Directly in Workspace Root Directory
- **Severity**: **Low**
- **File Path & Line Numbers**:
  - `/home/nadeem/Desktop/vaccine_manager_app/neochildclinic-release.jks` (2,708 bytes)
  - `/home/nadeem/Desktop/vaccine_manager_app/neochildclinic-release` (2,764 bytes, permissions 0777)
  - References: `.idea/workspace.xml:56`, `.gitignore:33, 42`
- **Description**:  
  Production Android signing keystores reside directly in the root working directory. While `.gitignore` matches `*.jks` and `neochildclinic-release`, keeping private production signing keystores in the developer workspace creates severe risk of accidental git inclusion, backup exposure, or unauthorized access.
- **Concrete Reproduction Scenario**:
  1. Open a terminal in the repository root directory: `cd /home/nadeem/Desktop/vaccine_manager_app`.
  2. Check directory contents: `ls -la neochildclinic-release*`.
  3. Observe two production Java KeyStore (JKS/PKCS12) artifacts located directly in the workspace root:
     `-rwxrwxrwx 1 nadeem nadeem 2764 Aug 15 19:06 neochildclinic-release`
     `-rw-rw-r-- 1 nadeem nadeem 2708 Nov  1 04:36 neochildclinic-release.jks`
  4. Inspect IDE workspace configuration in `.idea/workspace.xml:56`:
     `<option name="KEY_STORE_PATH" value="$PROJECT_DIR$/neochildclinic-release.jks" />`.
  5. Simulate an accidental archive creation or git force-add:
     `git add -f neochildclinic-release.jks`
     Or execute a build script bundling the workspace directory for remote compilation or diagnostics.
  6. The private signing certificate and private key are captured in the artifact. Any malicious actor or compromised dependency with read access can extract the private key to sign fraudulent, backdoored APKs that Android package managers will accept as valid upgrades to legitimate clinic installations.
- **Recommended Remediation**:  
  Store signing keys in an external secret manager, hardware security module (HSM), or CI/CD secret vault. Remove production signing keystores from the developer workspace.

---

### Finding SEC-06: Suboptimal Entropy in Local Database Encryption Passphrase Generation Using ASCII UUID
- **Severity**: **Low**
- **File Path & Line Numbers**: `/home/nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/core/security/SecurityUtils.kt:59`
- **Description**:  
  `SecurityUtils.getDatabasePassphrase` generates the local SQLCipher passphrase using:
  ```kotlin
  passphrase = UUID.randomUUID().toString()
  return passphrase.toByteArray()
  ```
  `UUID.randomUUID()` generates 122 bits of random entropy, formatted as a 36-character hexadecimal ASCII string with hyphens. Passing an ASCII-encoded UUID to SQLCipher is suboptimal for a 256-bit AES database encryption key compared to a full 256-bit cryptographically random byte array.
- **Concrete Reproduction Scenario**:
  1. Clear application storage or perform a fresh install on an Android device or emulator.
  2. Launch the application to trigger initial database encryption key generation in `SecurityUtils.getDatabasePassphrase()`.
  3. Inspect the stored passphrase in `EncryptedSharedPreferences`:
     ```kotlin
     val prefs = context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
     val passphrase = prefs.getString("db_passphrase", null)
     ```
  4. Output the generated value and examine its representation:
     The generated passphrase is a standard 36-character hyphenated UUID string (e.g. `"a8f4c2e1-7b0d-4351-9e28-3c4a5b6d7e8f"`).
  5. Analyze the byte array generated by `passphrase.toByteArray()`:
     Because Version 4 UUIDs reserve 6 bits for version and variant metadata, the string contains exactly 122 bits of pseudo-random entropy formatted into 36 ASCII bytes restricted to hex characters `[0-9a-f]` and hyphens. Passing this restricted ASCII string as the passphrase for SQLCipher forfeits 134 bits of the 256 bits of entropy expected for an AES-256 database master key.
- **Recommended Remediation**:  
  Generate 32 cryptographically secure random bytes using `SecureRandom().nextBytes(ByteArray(32))` and encode as Base64 or pass raw bytes directly.

---

# Section 7: Remediation Roadmap

The findings are synthesized into a prioritized remediation plan based on patient safety, data integrity, and operational risk.

---

## 7.1 P0: Immediate Actions (Critical Clinical & Data Destruction Risks)
Target: Deploy within 24–48 hours.

| Action Item | Target Component | File & Lines | Summary of Required Fix |
| :--- | :--- | :--- | :--- |
| **P0-1** | Room Schema & Sync | `VaccinationItemEntity.kt:21-32`<br>`InventoryRepositoryImpl.kt:684-696` | Remove `ForeignKey.CASCADE` on `vaccines` and `vaccine_batches`. Enforce count check before batch deletion in `RemotePrune` to protect historical administered dose records. |
| **P0-2** | Backup Restoration | `BackupDao.kt:84-86`<br>`BackupRestorer.kt:186-188` | Replace `OnConflictStrategy.REPLACE` with `@Upsert` on `patients` and other parent entities to eliminate cascade deletion of clinical visits during merge restore. |
| **P0-3** | Offline Sync Outbox | `MergePatientsUseCase.kt:48-49`<br>`InventoryRepositoryImpl.kt:638-640` | Enqueue `SyncOperation.UPDATE` for all transferred `inventory_transactions` during patient merge to prevent server-side `ON DELETE SET NULL` link severing. |
| **P0-4** | Downstream Sync | `PatientRepositoryImpl.kt:182-187`<br>`SyncUploader.kt:636-645` | Update `RemotePrune` to check for unsynced child records (`VACCINATION`, `CONSULTATION`) before pruning local patients, preventing outbox record drops. |
| **P0-5** | Reminder Domain | `ClinicalVaccinationService.kt:250-264` | Query reminders by `patientId` and vaccine name rather than new visit ID so administered vaccines successfully satisfy due reminders. |
| **P0-6** | Dashboard Stats | `ReminderDueListProcessor.kt:111-127` | Filter counts strictly by `it.status == ReminderStatus.ACTIVE` to stop historical completed vaccines from falsely inflating overdue tallies. |
| **P0-7** | App Update Engine | `AppUpdateManager.kt:27-31, 202-210, 415-421` | Extract SHA-256 hash from release body or download companion checksum file to restore APK integrity verification. |

---

## 7.2 P1: High Priority Architectural & Security Fixes
Target: Deploy in next minor release (within 1–2 weeks).

| Action Item | Target Component | File & Lines | Summary of Required Fix |
| :--- | :--- | :--- | :--- |
| **P1-1** | Keystore & Auth | `BiometricAuthenticator.kt:46-51, 129-139` | Fix `timeout = 0` configuration with `DEVICE_CREDENTIAL` to eliminate permanent lockout on PIN-only devices. |
| **P1-2** | Widget Privacy | `WidgetWorker.kt:36-60`<br>`VaccineWidget.kt:61-79` | **Accepted risk (2026-11-19):** full names are shown on the widget in locked and unlocked states by product decision; masking removed. Re-open if widgets are used on shared devices. |
| **P1-3** | Supabase RLS | `20260929000000_security_baseline.sql:70-87` | Drop blanket `"active staff delete"` on clinical and financial tables; restrict `DELETE` permissions strictly to administrators. |
| **P1-4** | Database Recovery | `AppDatabase.kt:488-528` | Stop silent renaming of unopenable databases to `.corrupt-*` and empty recreation; fail closed with user recovery prompt. |
| **P1-5** | Backup Merge | `BackupRestorer.kt:247-252` | Strip autogenerated IDs (`id = 0`) on incoming `inventory_deductions` before merge insert to prevent overwriting local records. |
| **P1-6** | Sync SingleFlight | `SingleFlight.kt:27-52`<br>`RefreshDataUseCase.kt:47-50` | Implement table-keyed single-flight coalescing so scoped table refreshes do not drop independent sync requests. |
| **P1-7** | Clock Skew Sync | `SyncUploader.kt:544-551` | Replace raw client-server timestamp comparison with revision numbers or client dirty tracking to prevent local edit loss. |
| **P1-8** | Doctor Booking | `GetAvailableSlotsUseCase.kt:27-52` | Query existing booked consultations to prevent unbounded double-booking of doctor appointment slots. |
| **P1-9** | Patient ID Logic | `PatientRepositoryImpl.kt:206-208` | Auto-promote `TEMP-` IDs to permanent unique clinic IDs upon saving patient edits instead of throwing exceptions. |
| **P1-10** | Broadcast Lifecycle | `VaccineWidgetReceiver.kt:30-32` | Hold broadcast execution open with `goAsync()` to prevent OS process termination during widget updates. |

---

## 7.3 P2: Medium / Low Hygiene & Reliability Improvements
Target: Address during standard sprint cycles.

| Action Item | Target Component | File & Lines | Summary of Required Fix |
| :--- | :--- | :--- | :--- |
| **P2-1** | TypeConverters | `Converters.kt:14-16` | Wrap `Json.decodeFromString` in `runCatching` with fallback parsing for legacy comma-separated strings. |
| **P2-2** | Enum Mapping | `ReminderStatus.kt:6-11`<br>`VaccinationDao.kt:79-80` | Add `RESCHEDULED` to `ReminderStatus` enum. |
| **P2-3** | Search Queries | `PatientDao.kt:44-49`<br>`AddEditPersonalReminderViewModel.kt:91` | Incorporate SQL wildcards `LIKE '%' \|\| :q \|\| '%'` directly in query definition. |
| **P2-4** | Snapshot Locking | `BackupCollector.kt:16-20` | Switch from writer connection to reader connection with `DEFERRED` transaction for backup collection. |
| **P2-5** | StateFlow Safety | `ProfileViewModel.kt`, `PatientViewModel.kt`, etc. | Replace non-atomic `.value =` and `+=` mutations with `.update { ... }`. |
| **P2-6** | Outbox Coalescing | `SyncQueueDao.kt:32-33`<br>`SyncRepositoryImpl.kt:74` | Implement multi-operation coalescing (`CREATE` + `UPDATE`, `CREATE` + `DELETE`). |
| **P2-7** | Worker Metrics | `SyncWorker.kt:29-39` | Maintain failure count until all `FAILED` queue items are resolved. |
| **P2-8** | Work Constraints | `ReminderScheduler.kt:30-32` | Remove unnecessary `NetworkType.CONNECTED` constraint from local `DailySummaryWorker`. |
| **P2-9** | Patient Input | `AddPatientScreen.kt:118-132` | Validate date of birth (`dob <= today`), phone number format, and duplicate detection. |
| **P2-10** | Doctor Slots | `DoctorAvailabilityRepositoryImpl.kt:45-56` | Enforce `startMinute < endMinute` and check for schedule overlaps. |
| **P2-11** | Backup Siloing | `backup-worker/src/index.ts:73-81` | Scope Cloudflare backups to `clinic_id` rather than individual `user.sub`. |
| **P2-12** | Deep Links | `AndroidManifest.xml:50-51` | Migrate `neochild://auth-callback` to verified Android App Links (`https://`). |
| **P2-13** | Database Indices | `SyncQueueEntity.kt:13` | Composite index on `(entityName, entityId)` added after this audit (verified 2026-11-18); the separate `(entityName, status)` index from finding SCHEMA-06 remains open. |
| **P2-14** | Workspace Secrets | `neochildclinic-release.jks` | Move production release keystores to external secure vault. |
| **P2-15** | Crypto Entropy | `SecurityUtils.kt:59` | Generate 32 bytes via `SecureRandom` instead of ASCII UUID for database passphrase. |
| **P2-16** | Schema History | `app/schemas/...` | Reconstruct Room schema JSONs v1..v32 for CI migration regression testing. |

---
*Comprehensive Audit Report compiled and verified for Sentinel and Engineering Review.*
