# Implementation Plan - Fix Reminder Local Persistence and Sync

This plan fixes the issue where reminders were being recreated with new UUIDs on every save (losing their server mapping) and failing to sync due to an ID mismatch in the sync queue.

## Proposed Changes

### Domain Repository

#### [MODIFY] [ReminderRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/repository/ReminderRepositoryImpl.kt)

**1. Fix `scheduleFollowUp`**:
- Before inserting a new `ReminderEntity`, use `dueReminderDao.getReminderByStableId(...)` to check for an existing record.
- If it exists, copy its `id` (UUID) and `serverId` into the new entity.
- This ensures we **update** existing records instead of replacing them with new IDs, preserving the sync state.

**2. Fix `enqueueReminderSync`**:
- Change the signature to accept `reminderId: String` (the UUID).
- Pass the UUID as the `entityId` to the sync queue.

**3. Update Callers**:
- Update all methods in `ReminderRepositoryImpl` (like `markRequirementSatisfied`, `reschedule`, etc.) to pass the reminder's UUID to `enqueueReminderSync`.

### Sync Engine

#### [MODIFY] [SyncRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/repository/SyncRepositoryImpl.kt)
- Refine the specialized `REMINDERS` logic in `uploadEntity` to ensure it correctly fetches the reminder by UUID from the sync queue and handles the server ID generation and local update.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to verify the build.

### Manual Verification
1.  **Save New**: Create a vaccination with a reminder. Verify it saves locally and syncs to Supabase (getting a `serverId`).
2.  **Edit existing**: Re-save the same vaccination. Verify the reminder's local UUID remains the same and it retains its `serverId`.
3.  **Sync Verify**: Check that Supabase correctly receives updates using the BIGINT ID instead of creating duplicates.
