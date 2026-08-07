# Tasks: Unified Doctor Selection

## 1. Database & Domain Layer Updates
- [x] Add `doctorId` to `VisitEntity.kt` and update mappings
- [x] Add `doctorId` to `Vaccination.kt` domain model
- [x] Update `toEntity` and `toVaccination` conversion logic

## 2. UI Components
- [x] Implement `DoctorDropdown` in `Dropdowns.kt`

## 3. Add Vaccination Refactoring
- [x] Update `AddVaccinationUiState` and `AddVaccinationViewModel.kt` to handle doctor selection
- [x] Integrate `DoctorDropdown` into `AddVaccinationScreen.kt`
- [x] Add mandatory validation for doctor selection

## 4. Add Consultation Refactoring
- [x] Update `AddConsultationUiState` and `AddConsultationViewModel.kt` to handle doctor selection
- [x] Integrate `DoctorDropdown` into `AddConsultationScreen.kt`
- [x] Add mandatory validation for doctor selection

## 5. Display & Lookup Logic
- [x] Expose `doctorMap` in `PatientViewModel.kt` for UI lookups
- [x] Update `VaccinationRecordCard` to use `doctorId` for display name lookup
- [x] Update `ConsultationRecordCard` to use `doctorId` for display name lookup
- [x] Update `ReceiptFormatter.kt` to resolve doctor name from `doctorId`

## 6. Verification
- [x] Verify doctor list loads and defaults correctly for Doctor role
- [x] Verify `employee_id` is stored in the database
- [x] Verify history cards display the correct doctor name dynamically
