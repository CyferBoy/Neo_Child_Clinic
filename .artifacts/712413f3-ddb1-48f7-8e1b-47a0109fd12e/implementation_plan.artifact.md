# Implementation Plan - Fix Profile Entity Missing Fields

The `ProfileEntity` is missing several fields (`phoneNumber`, `employeeId`, `lastLogin`) that are present in the `Profile` domain model. This prevents these values from being persisted locally or correctly mapped. This plan adds the missing fields and updates the database schema.

## Proposed Changes

### Data Layer

#### [MODIFY] [ProfileEntity.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/ProfileEntity.kt)
- Add `phoneNumber`, `employeeId`, and `lastLogin` fields to `ProfileEntity`.
- Update `toDomain()` and `toEntity()` mapping functions to include these new fields.

#### [MODIFY] [AppDatabase.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/database/AppDatabase.kt)
- Increment database version from `2` to `3`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to verify the changes.
- Launch the app and verify that the "schema mismatch" error is not present (handled by destructive migration).
