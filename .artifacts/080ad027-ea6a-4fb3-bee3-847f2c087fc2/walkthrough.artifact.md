# Walkthrough - Fixed Supabase Realtime "already joined" Crash

I have fixed the `java.lang.IllegalStateException: You cannot call postgresChangeFlow after joining the channel` error in `PatientListViewModel`. This crash was occurring because the app attempted to register Postgres change listeners on a Realtime channel that was already in a "joined" state, likely due to a leaked channel from a previous ViewModel instance.

## Changes Made

### 1. Robust Realtime Channel Initialization
In [PatientListViewModel.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/patient/PatientListViewModel.kt), I refactored `observeRealtimeChanges` to ensure any existing channel with the same name is removed before attempting to re-subscribe.

```kotlin
    private fun observeRealtimeChanges() {
        viewModelScope.launch {
            try {
                // Ensure any previous subscription with the same name is removed
                realtime.subscriptions["realtime:patients-db-changes"]?.let {
                    realtime.removeChannel(it)
                }

                val channel = realtime.channel("patients-db-changes")

                // postgresChangeFlow MUST be called BEFORE channel.subscribe()
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "patients"
                }.onEach { ... }.launchIn(this)

                channel.subscribe()
            } catch (e: Exception) { ... }
        }
    }
```

### 2. Explicit Channel Cleanup
Implemented `onCleared()` to remove the Realtime channel when the ViewModel is destroyed. This prevents channel leaks and ensures that the next time the Patient List is opened, it can start with a fresh channel.

```kotlin
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCleared() {
        super.onCleared()
        val channelId = "realtime:patients-db-changes"
        val channel = realtime.subscriptions[channelId]
        if (channel != null) {
            GlobalScope.launch {
                realtime.removeChannel(channel)
            }
        }
    }
```

## Verification Results

- **Build Success**: The project compiles without errors (`:app:assembleDebug`).
- **Logic Verification**: The sequence of `postgresChangeFlow` calls now always happens before `subscribe()`, and the explicit cleanup in `onCleared` ensures the "already joined" state is avoided on subsequent navigations.

> [!NOTE]
> The use of `GlobalScope` in `onCleared` is necessary because `viewModelScope` is cancelled before the `suspend` call to `removeChannel` can complete. This ensures the cleanup happens even after the ViewModel is destroyed.
