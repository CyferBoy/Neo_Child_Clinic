# Walkthrough - Fix Compilation Error in ReminderRepositoryImpl

I have fixed the compilation error in `ReminderRepositoryImpl.kt` where a non-existent parameter name `newDate` was being used in a function call.

## Changes

### [app](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app)

#### [ReminderRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/repository/ReminderRepositoryImpl.kt)

Modified the `reschedule` function to use the correct parameter name `newValue` instead of `newDate` when calling `logReminderUndoableChange`.

```diff
-                logReminderUndoableChange(updated, "RESCHEDULED", "Rescheduled: $reason", newDate = newDate)
+                logReminderUndoableChange(updated, "RESCHEDULED", "Rescheduled: $reason", newValue = newDate)
```

## Verification Results

### Automated Tests
- Executed `./gradlew :app:compileDebugKotlin`
- **Result**: Build finished successfully.
