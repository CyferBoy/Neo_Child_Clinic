# Implementation Plan - Fix Inventory and Data Synchronization

This plan addresses the critical synchronization failures between the Android app and the Supabase database by aligning table names, primary keys, and enhancing the sync logic for complex entities.

## User Review Required

> [!IMPORTANT]
> This update involves significant database schema changes. After applying these changes, existing local data that has not been synced may be lost during the mandatory database migration (Version 3 to 4).
> You will also need to run a new set of SQL commands in your **Supabase SQL Editor**.

## Proposed Changes

### Data Layer (Entities & DAOs)

#### [MODIFY] [VaccineBatchEntity.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/VaccineEntity.kt)
- Add `@SerialName("id")` to `batchId` to match Supabase's expected column name.
- Ensure all other fields match the SQL schema.

#### [MODIFY] [InventoryTransactionEntity](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/VaccineEntity.kt)
- Change `transactionId: Long` to `id: String` (UUID) to support unique identification across devices.
- Update `InventoryTransactionDao` to handle the new ID type.

#### [MODIFY] [PatientNotesEntity.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/PatientNotesEntity.kt)
- Change `id: Long` to `id: String` (UUID).

#### [MODIFY] [AuditLogEntity.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/AuditLogEntity.kt)
- Change `id: Long` to `id: String` (UUID).

#### [MODIFY] [AppDatabase.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/database/AppDatabase.kt)
- Increment database version to `4`.

### Repository Layer

#### [MODIFY] [SyncRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/repository/SyncRepositoryImpl.kt)
- Correct table mappings:
    - `VACCINATION`, `VISIT` -> `patient_visits`
    - `TRANSACTION`, `INVENTORY_TRANSACTION` -> `inventory_transactions`
- Add support for `VACCINATION_ITEM` entity.

#### [MODIFY] [VaccinationRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/repository/VaccinationRepositoryImpl.kt)
- Update `addVaccination` to enqueue individual `VACCINATION_ITEM`s for synchronization.

### Database (SQL Update)

#### [NEW] [missing_tables.sql](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/.artifacts/712413f3-ddb1-48f7-8e1b-47a0109fd12e/scratch/missing_tables.sql)
- Provide SQL for `inventory_transactions`, `waste_records`, `patient_notes`, and `audit_logs`.
- Fix the `vaccine_batches` primary key and column names.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to verify the build.

### Manual Verification
1. Run the updated SQL in Supabase.
2. Add a new vaccination in the app.
3. Verify that the "Visit" appears in `patient_visits`.
4. Verify that individual vaccines appear in `vaccination_items`.
5. Verify that stock is deducted and a record appears in `inventory_transactions` (both locally and in Supabase).
