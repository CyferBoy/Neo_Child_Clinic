# Tasks: Add Consultation Screen Refactoring

- [x] Database Schema & Data Models
    - [x] Update `VisitEntity` in `VaccinationEntity.kt` (Add `visitType`, `createdAt`)
    - [x] Update `ConsultationEntity.kt` (Add `visitId`, `doctorId`, `createdAt`)
    - [x] Update `Consultation.kt` domain model
- [x] Business Logic & Services
    - [x] Update `ClinicalVaccinationService.kt` (Add `recordConsultation` transaction)
- [x] Presentation Layer
    - [x] Refactor `AddConsultationViewModel.kt` (Validation, Service integration)
    - [x] Refactor `AddConsultationScreen.kt` (New UI layout, split payment)
- [ ] Verification
    - [ ] Verify atomic transaction (Visit + Consultation + Finance)
    - [ ] Verify UI field behavior (Read-only patient info, Total calculation)
