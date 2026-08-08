# Implementation Plan - Migrate `reminders` ID to UUID (String)

This plan outlines the steps to change the `reminders` table primary key from an auto-incrementing `Long` (int8) to a `String` (UUID) to maintain consistency with the rest of the project.

## User Review Required

> [!WARNING]
> This is a breaking change for the `reminders` table.
> 1. Existing local reminders will be lost if you perform a destructive migration (which the app is currently configured to do).
> 2. The Supabase `reminders` table MUST be dropped and recreated with the new UUID-based schema provided below.

## Proposed Changes

### Data Models

#### [MODIFY] [ReminderEntity.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/ReminderEntity.kt)
- Change `ReminderEntity.id` type from `Long` to `String`. Initialize with `java.util.UUID.randomUUID().toString()`.
- Remove `autoGenerate = true` from `@PrimaryKey`.
- Change `RemoteReminder.id` type from `Long?` to `String?`.
- Update `toRemote` and `toLocal` mappings.

### Data Access Objects (DAOs)

#### [MODIFY] [ReminderDao.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/dao/ReminderDao.kt)
- Update all methods using `Long` for reminder IDs to use `String`.
- Change return type of `insertOrUpdate` to `Unit` or check if the generated ID is needed.

#### [MODIFY] [DueReminderDao.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/dao/DueReminderDao.kt)
- Update all methods using `Long` for reminder IDs to use `String`.
- Update `moveDueTo...` functions to return `Unit` instead of `Long`.

### Repository Layer

#### [MODIFY] [ReminderRepository.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/domain/repository/ReminderRepository.kt)
- Update `markCompleted` and `insertReminder` signatures to use `String` and return `Unit`/`String` respectively.

#### [MODIFY] [ReminderRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/repository/ReminderRepositoryImpl.kt)
- Update implementation of repository methods to match the new interface.

#### [MODIFY] [SyncRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/repository/SyncRepositoryImpl.kt)
- Remove `toLongOrNull()` conversion when fetching reminder data by ID.

### SQL Migration (Supabase)

```sql
-- DROP the existing table if it was created with BIGINT
DROP TABLE IF EXISTS public.reminders CASCADE;

-- Recreate with UUID
CREATE TABLE public.reminders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES public.patients(id) ON DELETE CASCADE,
    original_visit_id UUID NOT NULL REFERENCES public.patient_visits(id) ON DELETE CASCADE,
    vaccine_name TEXT NOT NULL,
    due_date TEXT NOT NULL,
    status TEXT NOT NULL,
    priority TEXT DEFAULT 'NORMAL',
    reminder_enabled BOOLEAN DEFAULT true,
    category TEXT DEFAULT 'VACCINATION',
    type TEXT DEFAULT '',
    nxt_vaccine_id TEXT[] DEFAULT NULL,
    notes TEXT,
    completion_date TEXT,
    performed_by TEXT,
    dismissal_date TEXT,
    dismissal_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    is_synced BOOLEAN DEFAULT true,
    CONSTRAINT unique_reminder_event UNIQUE (patient_id, original_visit_id, vaccine_name, type)
);

-- Re-enable RLS and Policies
ALTER TABLE public.reminders ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Enable all access for authenticated staff" ON public.reminders FOR ALL TO authenticated USING (true) WITH CHECK (true);
```

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to verify the build.
- Perform a fresh install of the app to trigger Room's destructive migration.
- Verify sync by creating a new reminder and checking the Supabase dashboard.
