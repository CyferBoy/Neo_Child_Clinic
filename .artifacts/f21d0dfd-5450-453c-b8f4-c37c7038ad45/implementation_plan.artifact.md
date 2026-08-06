# Implementation Plan - Fix SQL Timestamp Syntax & Schema Alignment

The project is encountering two related issues during Supabase synchronization:
1. **Timestamp Syntax Error**: Supabase rejects `""` (empty strings) for `TIMESTAMPTZ` columns. Additionally, the app stores clinical dates (like `dob` or `date_given`) in a user-friendly format (e.g., "6 Aug 2026") which PostgreSQL's `DATE` type cannot parse.
2. **Schema Mismatch**: Some parts of the app are still using domain models for cloud fetching, leading to errors like "Could not find column 'followUps'".

## User Review Required

> [!IMPORTANT]
> This plan involves changing several column types in the Supabase schema from `DATE` or `TIMESTAMPTZ` to `TEXT`. This is necessary to support the app's current date-handling logic without a massive refactor of the local database.
>
> I will also update the Room entities to use `null` for empty timestamps, allowing Supabase to use its `now()` defaults.

## Proposed Changes

### 1. SQL Schema Refactoring
Update `full_project_schema.sql.artifact.md` to use `TEXT` for app-formatted dates. This makes the database "flexible" to the app's string-based date storage.

- **Tables affected**: `patients`, `patient_visits`, `consultations`, `vaccine_batches`, `reminders`, `borrow_records`.
- **Columns changed to TEXT**: `dob`, `date_given`, `next_due_date`, `purchase_date`, `expiry_date`, `due_date`, `borrowed_date`, `returned_date`.

### 2. Room Entity Refactoring
Update data classes to handle timestamps more safely for Supabase.

#### [MODIFY] [VaccinationEntity.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/VaccinationEntity.kt)
- Change `createdAt` and `updatedAt` defaults from `""` to `null`.
- Update `toEntity` mappers.

#### [MODIFY] [ConsultationEntity.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/ConsultationEntity.kt)
- Change `createdAt` and `updatedAt` to nullable strings.

### 3. Repository Refactoring
Ensure all cloud-to-local data flow uses Room entities.

#### [MODIFY] [PatientRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/repository/PatientRepositoryImpl.kt)
- Change `postgrest.from("patients").select().decodeList<Patient>()` to `decodeList<PatientEntity>()`.

#### [MODIFY] [ConsultationRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/repository/ConsultationRepositoryImpl.kt)
- Change `decodeList<Consultation>()` to `decodeList<ConsultationEntity>()`.

## Verification Plan

### Manual Verification
- Re-run the SQL script in Supabase.
- Trigger a sync in the app.
- Check Logcat for "invalid input syntax" errors.
- Verify that `patient_visits` and `patients` sync without schema/column errors.
