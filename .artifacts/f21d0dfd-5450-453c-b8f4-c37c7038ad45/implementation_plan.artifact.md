# Implementation Plan: Unified Doctor Selection

Implement a standardized Doctor Selection feature using `employee_id` across Vaccination and Consultation modules. This ensures consistent business identifiers and dynamic lookup of doctor names.

## Proposed Changes

### 1. Database & Domain Layer (Schema Updates)

#### [MODIFY] [VisitEntity.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/VaccinationEntity.kt)
- Add `doctorId: String = ""` to `VisitEntity`.
- Update `toVaccination()` and `toEntity()` to map `doctorId`.

#### [MODIFY] [Vaccination.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/domain/model/Vaccination.kt)
- Add `doctorId: String = ""` to the `Vaccination` domain model.

#### [MODIFY] [Consultation.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/domain/model/Consultation.kt)
- Ensure the model's documentation or internal usage reflects that `doctorId` stores `employee_id`.

---

### 2. UI Components

#### [NEW] `DoctorDropdown` in [Dropdowns.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/core/ui/Dropdowns.kt)
- Implement `DoctorDropdown` using Material 3 `ExposedDropdownMenuBox`.
- It will accept `doctors: List<Profile>`, `selectedDoctor: Profile?`, and `onDoctorSelected: (Profile) -> Unit`.
- Show validation error state if mandatory selection is missing.

---

### 3. Feature Modules (ViewModels & Screens)

#### [MODIFY] [AddVaccinationViewModel.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/vaccination/AddVaccinationViewModel.kt)
- Inject `ProfileRepository`.
- Add `allDoctors: StateFlow<List<Profile>>` to the state, filtered by `role == UserRole.doctor` and `isActive == true`.
- Add `selectedDoctor: Profile?` to `AddVaccinationUiState`.
- In `init`, load current profile; if role is `doctor`, set as default `selectedDoctor`.
- Update `saveVaccination` to pass `selectedDoctor.employeeId` to the service.

#### [MODIFY] [AddVaccinationScreen.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/vaccination/AddVaccinationScreen.kt)
- Insert the `DoctorDropdown` component immediately below the **Given Date** field.
- Add mandatory validation (disable save button or show error text).

#### [MODIFY] [AddConsultationViewModel.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/patient/AddConsultationViewModel.kt)
- Add `allDoctors: StateFlow<List<Profile>>` similar to vaccination.
- Update `AddConsultationUiState` to use `selectedDoctor: Profile?` instead of a string `doctorName`.
- Update `saveConsultation` to use `selectedDoctor.employeeId`.

#### [MODIFY] [AddConsultationScreen.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/patient/AddConsultationScreen.kt)
- Replace the current **Doctor Name** `StandardTextField` with the shared `DoctorDropdown`.

---

### 4. Display & Receipt Logic

#### [MODIFY] [PatientViewModel.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/patient/PatientViewModel.kt)
- Expose `doctorMap: StateFlow<Map<String, String>>` (mapping `employeeId -> displayName`) for efficient lookups in list cards.

#### [MODIFY] [PatientInfoComponents.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/patient/PatientInfoComponents.kt) & [VaccinationCards.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/patient/VaccinationCards.kt)
- Update cards to lookup `doctorDisplayName` using the `doctorId` (employeeId) via the new `doctorMap`.

#### [MODIFY] [ReceiptFormatter.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/core/utils/ReceiptFormatter.kt)
- Update PDF generation logic to lookup the doctor's `displayName` from the `Profile` table based on the stored `doctorId`.

---

## User Review Required

> [!IMPORTANT]
> **Data Migration**: Existing records in `patient_visits` do not have a `doctorId` (employeeId), only a `doctor` (name). I will initialize `doctorId` as an empty string for existing records. Moving forward, the app will prioritize the `doctorId` lookup.
>
> **Validation**: Saving will be blocked until a doctor is selected. If the logged-in user is a Doctor, it will be pre-filled, reducing friction.

## Verification Plan

### Automated Tests
- N/A (Manual UI verification preferred for this interaction).

### Manual Verification
1. **Add Vaccination**: Verify the doctor list loads dynamically. Select a doctor and save. Verify the database/audit log shows the `employee_id`.
2. **Add Consultation**: Verify the TextField is gone and replaced by the Dropdown.
3. **Role Defaulting**: Log in as a Doctor. Open "Add Vaccination". Verify your name is pre-selected.
4. **Validation**: Try saving with "No Doctor Selected". Verify the error message appears.
5. **Display**: View the history card for the saved record. Verify the current `display_name` of the doctor is shown (test this by changing the doctor's name in the Profile section).
