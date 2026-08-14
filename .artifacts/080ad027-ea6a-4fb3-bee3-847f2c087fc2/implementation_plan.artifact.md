# Implementation Plan - Fix Supabase Realtime "already joined" error

The application is crashing with `java.lang.IllegalStateException: You cannot call postgresChangeFlow after joining the channel`. This occurs because `PatientListViewModel` attempts to register Postgres change listeners on a Realtime channel that has already been joined (likely leaked from a previous ViewModel instance).

## Proposed Changes

### [Component] Patient Feature

#### [MODIFY] [PatientListViewModel.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/patient/PatientListViewModel.kt)
- Refactor `observeRealtimeChanges` to:
    1. Check for and remove any existing channel with the name `patients-db-changes` to ensure a clean state.
    2. Sequentially register `postgresChangeFlow` listeners *before* calling `subscribe()`.
    3. Use a single coroutine block to manage the setup lifecycle.
- Implement `onCleared` to explicitly remove the channel from the `Realtime` singleton when the ViewModel is destroyed.

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to verify no compilation errors.

### Manual Verification
- Launch the app and navigate to the Patient List.
- Verify in Logcat that "Subscribed to channel: patients-db-changes" appears without errors.
- Navigate away and back several times to ensure ViewModel recreation doesn't trigger the crash.
- Update a patient record in the Supabase Dashboard and verify the app refreshes automatically.
