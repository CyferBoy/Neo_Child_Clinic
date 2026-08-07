# Tasks: Fix Foreign Key Violations on Deletion

## 1. Finance Module Updates
- [x] Add Visit ID lookup and deletion to `FinanceDao.kt`
- [x] Implement `deleteTransactionsByVisitId` in `FinanceRepository.kt` & `FinanceRepositoryImpl.kt`

## 2. Vaccination Deletion Fix
- [x] Update `VaccinationRepositoryImpl.kt` to delete linked finance records first

## 3. Consultation Deletion Fix
- [x] Update `ConsultationRepositoryImpl.kt` to delete linked finance records and visit header

## 4. Sync Engine Integrity
- [x] Update `SyncRepositoryImpl.kt` to sort `DELETE` operations in reverse priority

## 5. Verification
- [x] Verify linked deletion of vaccination and finance records
- [x] Verify linked deletion of consultation, visit header, and finance records
- [x] Verify sync ordering handles server-side foreign keys correctly
