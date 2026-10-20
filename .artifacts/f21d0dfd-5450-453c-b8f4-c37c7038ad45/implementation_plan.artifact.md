# Implementation Plan: Fix Room Table Name Mismatch in ReminderDao

Fix the Room compilation error where `ReminderDao` references the non-existent `reminder_states` table, which was renamed to `reminders` in a previous schema update.

## Proposed Changes

### 1. DAO Updates

#### [MODIFY] [ReminderDao.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/dao/ReminderDao.kt)
- Update all `@Query` annotations to reference `reminders` instead of `reminder_states`.

---

### 2. Consistency & Documentation Cleanup

#### [MODIFY] [ReminderRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/repository/ReminderRepositoryImpl.kt)
- Update KDoc/comments that refer to the legacy `reminder_states` table name to use `reminders`.

#### [MODIFY] [VaccinationCards.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/patient/VaccinationCards.kt)
- Update comments that refer to `reminder_states` to use `reminders`.

---

## User Review Required

> [!NOTE]
> This is a surgical fix to resolve the `[SQLITE_ERROR] no such table: reminder_states` build error. It aligns the DAO with the already-updated `ReminderEntity` definition.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:kspDebugKotlin` to verify that Room compilation succeeds.
- Run `./gradlew assembleDebug` to ensure the entire project builds correctly.

### Manual Verification
- Not required for this build fix, but regular app functionality should be verified by the user to ensure data is correctly flowing through the `reminders` table.
