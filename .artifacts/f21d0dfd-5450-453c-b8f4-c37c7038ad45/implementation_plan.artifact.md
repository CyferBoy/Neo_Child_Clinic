# Implementation Plan - Fix Supabase Schema Mismatches & Model Refactoring

The goal is to align Kotlin data classes with the Supabase PostgreSQL schema (`snake_case` columns) using `@SerialName` and refactor models that have structural mismatches with the remote database.

## User Review Required

> [!IMPORTANT]
> - **`ReminderEntity` Split**: I am splitting `ReminderEntity` into a local Room entity (`ReminderEntity`) and a remote DTO (`RemoteReminder`). This resolves the issue where the local table has more fields than the remote table.
> - **`BorrowedVaccine` structural change**: The `BorrowedVaccine` model is being changed to use `vaccine_id` and `batch_id` instead of denormalized names. The `BorrowedViewModel` will be updated to resolve names locally.
> - **Date Type Change**: `completionDate` and `dismissalDate` in reminders are changing from `Long?` (millis) to `String?` (ISO) to match the database `TEXT` type.

## Proposed Changes

### 1. Entity & Model Alignment (`@SerialName`)

#### [MODIFY] [ConsultationEntity.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/ConsultationEntity.kt)
- Add `@SerialName` to all multi-word fields matching Supabase snake_case.

#### [MODIFY] [WasteRecord.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/domain/model/WasteRecord.kt)
- Add `@SerialName` annotations and include `is_synced` field.

#### [MODIFY] [WasteEntity.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/WasteEntity.kt)
- Update `toDomain()` and `toEntity()` to preserve the `isSynced` flag.

#### [MODIFY] [VaccineEntity.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/VaccineEntity.kt)
- **`VaccineEntity`**: Add missing `@SerialName` for `brand_name` and `company_name`.
- **`InventoryTransactionEntity`**: Mark `failureReason`, `processedAt`, and `processedBy` as `@Transient` to exclude them from Supabase sync.

#### [MODIFY] [BorrowEntity.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/BorrowEntity.kt)
- Add `@SerialName` annotations to all fields.

#### [MODIFY] [VaccinationItemEntity.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/VaccinationItemEntity.kt)
- Add `@SerialName` annotations to all fields.

### 2. Reminder Refactoring (Split & Date Types)

#### [MODIFY] [ReminderEntity.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/ReminderEntity.kt)
- Update `ReminderEntity` fields `completionDate` and `dismissalDate` to `String?`.
- **[NEW] `RemoteReminder`**: Create a DTO that only contains the columns present in the Supabase `reminders` table.
- Add mapping functions between `ReminderEntity` and `RemoteReminder`.

#### [MODIFY] [ReminderRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/repository/ReminderRepositoryImpl.kt)
- Update code that sets `completionDate` and `dismissalDate` to use formatted date strings.
- Update `refreshReminders` to decode `RemoteReminder` and map to `ReminderEntity`.

#### [MODIFY] [SyncRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/repository/SyncRepositoryImpl.kt)
- Update `uploadEntity` to map `ReminderEntity` to `RemoteReminder` before sending to Supabase.

### 3. Borrowing Refactoring

#### [MODIFY] [BorrowedVaccine.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/core/model/BorrowedVaccine.kt)
- Rebuild to match `borrow_records` table schema (using IDs instead of names).

#### [MODIFY] [BorrowedViewModel.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/inventory/BorrowedViewModel.kt)
- Update logic to work with the ID-based `BorrowedVaccine` structure.

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to ensure all call sites are correctly updated.

### Manual Verification
- Verify Sync functionality for all modified entities.
- Check "Borrow" screen to ensure vaccine and batch names still display correctly (now resolved via IDs).
- Verify that reminders correctly show completion/dismissal dates.
