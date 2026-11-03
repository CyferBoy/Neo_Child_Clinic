# Neo Child Clinic — Features Overview

This document provides a feature-by-feature breakdown of all 20 business modules in Neo Child Clinic.

---

## Feature Matrix

| Feature | Key Responsibility | Primary Screens | Repository & Domain Service |
|---|---|---|---|
| **Patient Management** (`feature/patient`) | Patient registration, medical history, documents, OP ID generation | `PatientListScreen`, `PatientDetailsScreen`, `AddPatientScreen` | `PatientRepositoryImpl`, `DocumentRepositoryImpl`, `MergePatientsUseCase` |
| **Vaccination Administration** (`feature/vaccination`) | Multi-vial vaccination recording, dose calculation, stock deduction, next-dose reminders | `AddVaccinationScreen`, `VaccineRowCard` | `VaccinationRepositoryImpl`, `ClinicalVaccinationService`, `VaccinationEditEngine` |
| **Inventory & Batches** (`feature/inventory`) | Vaccine vials, lot/batch tracking, expiry monitoring, stock movement | `VaccineInventoryScreen`, `AddStockScreen`, `AddBatchScreen`, `StockHistoryScreen` | `InventoryRepositoryImpl`, `InventoryStockMovement` |
| **Due & Reminders** (`feature/reminder`) | Due/overdue vaccination tracking, patient outreach, dismissal | `DueScreen`, `DueTab`, `CompletedDismissedScreen` | `ReminderRepositoryImpl`, `ReminderDueListProcessor` |
| **Personal Reminders** (`feature/personalreminder`) | Custom clinic reminders (follow-ups, lab reviews, general clinic alerts) | `PersonalReminderScreen`, `AddEditPersonalReminderScreen` | `PersonalReminderRepositoryImpl` |
| **Doctor Consultations** (`feature/consultation`) | Clinical notes, vitals, prescriptions, consultation charges | `AddConsultationScreen` | `ConsultationRepositoryImpl`, `ConsultationEditEngine` |
| **Doctor Availability** (`feature/doctor`) | Weekly clinic slot schedules, exception days, slot booking limits | `WeeklyDoctorSlotsScreen` | `DoctorAvailabilityRepositoryImpl`, `GetAvailableSlotsUseCase` |
| **Clinic Dashboard** (`feature/dashboard`) | Daily patient schedule, appointment slots, quick status tiles | `DashboardScreen`, `TodayPatientsScreen` | `DashboardViewModel`, `TodaySlotFilter` |
| **Clinic Finances & Expenses** (`feature/finance`) | Revenue tracking, clinical expense ledger, receipt numbering | `ExpenseListScreen`, `AddExpenseScreen` | `FinanceRepositoryImpl`, `ExpenseRepositoryImpl`, `ExpenseValidator` |
| **Borrowed Vaccines** (`feature/borrowed`) | Inter-clinic vaccine borrowing, lending, and physical batch returns | `BorrowedScreen`, `ReturnVaccineDialog` | `BorrowRepositoryImpl`, `BorrowBatchSelection` |
| **Wastage Logging** (`feature/waste`) | Expired/damaged vial recording, wastage reconciliation | `WasteScreen` | `WasteRepositoryImpl` |
| **Clinic Statistics & Reports** (`feature/statistics`) | Longitudinal patient metrics, vaccine uptake charts, PDF export | `StatisticsScreen`, `FullReportScreen`, `MonthlyFinancesScreen` | `FinanceCalculator`, `ExpenseCalculator`, `FullReportPdfExporter` |
| **Clinic Search** (`feature/search`) | Unified multi-criteria search across patients and clinic records | `SearchScreen` | `SearchViewModel`, `SearchPatientsUseCase` |
| **Staff Administration** (`feature/staff`) | Clinic staff accounts, roles (Admin, Doctor, Nurse, Staff), deactivation | `ManageStaffScreen`, `AddStaffScreen`, `StaffDetailsScreen` | `StaffManagementRepositoryImpl` |
| **Clinic Profile & Branding** (`feature/profile`) | Clinic name, doctor credentials, header printing config | `ProfileScreen` | `ProfileRepositoryImpl` |
| **App & Biometric Settings** (`feature/settings`) | Biometric unlock, notification settings, theme, backups | `SettingsScreen`, `BackupSettingsScreen`, `SecuritySettingsScreen` | `BackupRepositoryImpl`, `NotificationSettingsManager` |
| **Offline Synchronization** (`feature/sync`) | Sync queue status, pending changes review, conflict triage | `SyncScreen` | `SyncRepositoryImpl`, `RefreshDataUseCase` |
| **In-App Software Updates** (`feature/update`) | GitHub releases / custom APK updates, background download | `AppUpdateScreen`, `AppUpdateDialog` | `AppUpdateManager`, `AppUpdateInstallReceiver` |
| **Authentication & Device** (`feature/auth`) | Multi-tenant staff sign-in, device session binding | `LoginScreen` | `AuthViewModel`, `DeviceRepositoryImpl` |
| **Home Screen Widget** (`feature/widget`) | Today's due vaccinations widget for Android home screen | `VaccineWidget`, `VaccineWidgetConfigurationActivity` | `WidgetWorker`, `WidgetLocalDataSource` |
