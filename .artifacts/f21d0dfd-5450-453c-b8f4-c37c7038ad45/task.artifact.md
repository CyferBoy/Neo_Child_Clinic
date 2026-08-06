# Tasks - Fix Supabase Schema Mismatches & Model Refactoring

- [x] Entity & Model Alignment (`@SerialName`)
    - [x] `ConsultationEntity.kt`
    - [x] `WasteRecord.kt` & `WasteEntity.kt` mappers
    - [x] `VaccineEntity.kt` (Vaccine & InventoryTransaction)
    - [x] `BorrowEntity.kt`
    - [x] `VaccinationItemEntity.kt`
- [x] Reminder Refactoring
    - [x] Update `ReminderEntity.kt` (Split Local/Remote, change date types)
    - [x] Update `ReminderRepositoryImpl.kt` call sites
    - [x] Update `SyncRepositoryImpl.kt` for reminders
- [x] Borrowing Refactoring
    - [x] Update `BorrowedVaccine.kt`
    - [x] Update `BorrowedViewModel.kt`
- [x] Verify Build & Sync
