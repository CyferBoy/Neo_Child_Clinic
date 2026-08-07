# Implementation Plan: Fix Foreign Key Violations on Deletion

Address the `finance_transactions_visit_id_fkey` error by ensuring that associated financial records and visit headers are correctly deleted and synced when a Vaccination or Consultation record is removed.

## User Review Required

> [!IMPORTANT]
> **Data Integrity**: This change will ensure that when you delete a clinical record, its associated financial income is also automatically removed from the system. This prevents "orphaned" money records that point to non-existent visits.
>
> **Sync Ordering**: I am updating the sync engine to process `DELETE` operations in reverse priority. This ensures children (like Finance or Vaccination Items) are deleted from Supabase before their parents (Visits), satisfying database constraints.

## Proposed Changes

### 1. Finance Module (Link Deletion)

#### [MODIFY] [FinanceDao.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/dao/FinanceDao.kt)
- Add `getTransactionsByVisitId(visitId: String): List<FinanceEntity>` to find linked records.
- Add `deleteTransactionById(id: String)` for individual removal.

#### [MODIFY] [FinanceRepository.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/domain/repository/FinanceRepository.kt) & [FinanceRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/repository/FinanceRepositoryImpl.kt)
- Implement `deleteTransactionsByVisitId(visitId: String)`.
- Logic: Iterate through all transactions for the visit, delete them locally, and enqueue a `FINANCE` entity `DELETE` operation for each.

---

### 2. Clinical Repositories (Coordinate Deletion)

#### [MODIFY] [VaccinationRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/repository/VaccinationRepositoryImpl.kt)
- Inject `FinanceRepository`.
- In `deleteVaccination(id: String)`, call `financeRepository.deleteTransactionsByVisitId(id)` before deleting the vaccination record.

#### [MODIFY] [ConsultationRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/repository/ConsultationRepositoryImpl.kt)
- Inject `FinanceRepository` and use `database.vaccinationDao()`.
- In `deleteConsultation(id: String)`:
    - Retrieve the consultation to get its `visitId`.
    - Delete associated finance records using the `visitId`.
    - Delete the `ConsultationEntity` (locally and enqueued).
    - Delete the `VisitEntity` (locally and enqueued) to clean up the shared `patient_visits` table.

---

### 3. Sync Engine (Integrity Sorting)

#### [MODIFY] [SyncRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/repository/SyncRepositoryImpl.kt)
- Update `processNextItems` sorting logic.
- For `DELETE` operations, negate the entity priority.
- This ensures `FINANCE` (Priority 7 -> -7) is processed before `VACCINATION` (Priority 4 -> -4), avoiding foreign key violations on the server.

---

## Verification Plan

### Manual Verification
1. **Vaccination Deletion**:
    - Add a vaccination with a fee.
    - Delete the vaccination.
    - Verify in the "Finance" section that the income record is gone.
    - Verify in the "Audit Log" that both Finance and Vaccination deletions were recorded.
2. **Consultation Deletion**:
    - Add a consultation.
    - Delete it from the patient details history.
    - Verify that both the consultation record and the visit header (in `patient_visits`) are removed.
3. **Sync Check**:
    - Perform a deletion while offline.
    - Go online and trigger sync.
    - Verify Supabase tables (`finance_transactions` and `patient_visits`) are updated in the correct order without errors.
