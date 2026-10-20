# Implementation Plan - Fix JsonDecodingException in Reminder Refresh

The application fails to refresh reminders from Supabase because it expects `reminders.id` to be a `Long` (BIGSERIAL), but the backend is returning UUID strings. This results in a `JsonDecodingException: Unexpected symbol 'f' in numeric literal`.

## User Review Required

> [!IMPORTANT]
> This change modifies the Room database schema for `reminders`. Since `AppDatabase` is configured with `fallbackToDestructiveMigration()`, bumping the database version will **wipe all local data** on the device. This is necessary to change the `serverId` column type from `INTEGER` to `TEXT`.

## Proposed Changes

### Data Layer

#### [MODIFY] [ReminderEntity.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/ReminderEntity.kt)
- Update `ReminderEntity` to change `serverId` type from `Long?` to `String?`.
- Update `RemoteReminder` to change `id` type from `Long?` to `String?`.
- Update `toLocal` and `toRemote` mapping functions to reflect these type changes.

#### [MODIFY] [DueReminderDao.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/dao/DueReminderDao.kt)
- Update `updateServerId` function signature to accept `serverId: String`.

#### [MODIFY] [SyncRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/repository/SyncRepositoryImpl.kt)
- Update `uploadEntity` to handle `serverId` as `String`.
- Remove `toLongOrNull()` when retrieving `serverId` for delete operations, as it is now a UUID string.

#### [MODIFY] [AppDatabase.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/database/AppDatabase.kt)
- Increment database version from `15` to `16` to trigger the schema update.

## Verification Plan

### Automated Tests
- Build the project to ensure type safety across all repositories and DAOs.
- Run `app:assembleDebug` to verify compilation.

### Manual Verification
- Deploy the app and trigger "Refresh Reminders".
- Verify that the `JsonDecodingException` no longer occurs.
- Verify that sync (Create/Update/Delete) still works correctly with UUID `serverId`.
