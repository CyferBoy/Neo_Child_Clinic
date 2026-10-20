# Implementation Plan: Patient Details Screen Redesign

Redesign the **Patient Details** screen to be cleaner and more modern, following the provided specification. This includes restructuring the patient info card, switching to a 2-option medical history segment (Vaccinations and Consultations), and enhancing the vaccination/consultation cards with more detailed payment and record information.

## User Review Required

> [!IMPORTANT]
> **Data Model Enhancements**: I will update the `Consultation` model and `consultations` table to include `doctorName`, `cashAmount`, and `onlineAmount`. This is necessary to fulfill the requirement of displaying doctor names and split payment details in consultation cards.

> [!NOTE]
> **Medical History Segments**: The "Documents" segment will be removed from the main segmented button as requested (the spec only lists Vaccination and Consultation), but clinical notes and follow-ups will still be visible below the primary list as before, unless otherwise specified.

## Proposed Changes

### [Component] Domain & Data Layer (Consultation Model)

#### [MODIFY] [Consultation.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/domain/model/Consultation.kt)
- Add `doctorName: String`, `cashAmount: Double`, `onlineAmount: Double`.

#### [MODIFY] [ConsultationEntity.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/ConsultationEntity.kt)
- Update Room entity and mappers to include new fields.

### [Component] Feature: Consultation Entry

#### [MODIFY] [AddConsultationViewModel.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/patient/AddConsultationViewModel.kt)
- Update `saveConsultation` to handle the new fields.

#### [MODIFY] [AddConsultationScreen.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/patient/AddConsultationScreen.kt)
- Add UI fields for "Doctor Name", "Cash", and "Online" payments.

### [Component] Feature: Patient Details Screen

#### [MODIFY] [PatientDetailsScreen.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/patient/PatientDetailsScreen.kt)
- Update Top App Bar overflow menu (Edit, Delete, Audit).
- Update FAB to show a menu (Add Vaccination, Add Consultation).

#### [MODIFY] [PatientInfoComponents.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/patient/PatientInfoComponents.kt)
- Update `PatientInfoSection`: card-based layout with all patient fields.
- Update `HistorySegmentedButton`: restrict to "Vaccination" and "Consultation".
- Update `PatientDetailsContent`: simplify segment handling.
- Update `ConsultationRecordCard`: match new design (Doctor, Notes, Fee, Payment).

#### [MODIFY] [VaccinationCards.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/patient/VaccinationCards.kt)
- Update `VaccinationRecordCard`: match new design (Date, Vaccine Names, Next Vax, Due Date, Total Price, Payment).

## Verification Plan

### Automated Tests
- Build the project to ensure schema changes and ViewModel updates are valid.

### Manual Verification
- Open Patient Details and verify the new card layouts.
- Test switching between Vaccination and Consultation segments.
- Test adding a consultation with the new fields and verify it appears correctly in the history.
- Verify split payment display logic (e.g., "Cash: ₹500 | Online: ₹750").
- Verify that "Delete Patient" and "Audit Log" are available in the overflow menu.
