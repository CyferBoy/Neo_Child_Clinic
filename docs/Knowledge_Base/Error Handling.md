# Error Handling

> Every error idiom in the app, counted from source, with the consistency gaps.
> Related: [[MVVM]] · [[Known Bugs]] · [[Room Database]]

## Totals in `app/src/main`

| Metric | Count |
|---|---|
| `catch` sites | **161** |
| `runCatching` sites | **40** |
| `.catch { }` on Flow | **4** |
| Sealed `UiState` screen classes | **0** (1 — `SlotsUiState`) |
| `Result<T>` signatures | **3** |
| `viewModelScope.launch` bodies with **no** `try`/`catch` | **60 functions across 27 ViewModels** |

The 60 unguarded launches are the headline: `viewModelScope` is
`SupervisorJob() + Dispatchers.Main.immediate`, and an uncaught exception there goes to the
default uncaught handler — **process crash**.

## The 15 idioms

| # | Idiom | Count | Representative |
|---|---|---|---|
| 1 | `try/catch` → writes into UiState | 36 | `WasteViewModel.kt:172` |
| 2 | `try/catch` → `Log.e` **only, nothing shown** | 11 | `DashboardViewModel.kt:389` |
| 3 | `catch (_: Exception) {}` — fully swallowed | 7 | `PatientViewModel.kt:173` |
| 4 | `catch (_) { }` + a justifying comment | 2 | `PersonalReminderViewModel.kt:106` |
| 5 | `catch` whose body is **only a comment** | 1 | `PatientViewModel.kt:202-204` |
| 6 | `runCatching { }` result discarded | ~20 | `AuthViewModel.kt:233`, `:264`, `:300` |
| 7 | `runCatching{}.onSuccess{}.onFailure{}` → message state | 1 | `AppUpdateViewModel.kt:74-91` |
| 8 | `.catch { }` on a Flow | 4 | `SearchViewModel.kt:50` |
| 9 | `Result<T>` return | 3 | `BackupRepositoryImpl.kt:238`, `:295`; `AuthViewModel.kt:360` |
| 10 | **No `try`/`catch` at all** | 60 funcs | see below |
| 11 | `throw IllegalArgumentException` inside its own `try` | 3 | `PatientViewModel.kt:198` |
| 12 | Silent `?: return` leaving state half-set | 4 | `ProfileViewModel.kt:36`, `:85`, `:106` |
| 13 | Silent `?: null` on parse failure → blank/empty state | many | `DateUtils.kt:57-112` |
| 14 | `sealed class … Error(val message)` | **1** | `core/ui/Dropdowns.kt:168` |
| 15 | `sealed class BackupException` / `BackupValidationResult` | 2 | `data/backup/BackupErrors.kt:11` |

`Result<T>` appears in exactly 3 places, all below the UI boundary. **No ViewModel exposes a
`Result` to Compose.**

## Five incompatible error surfaces

| Mechanism | Screens |
|---|---|
| `ErrorState(...)` full-screen composable | 4: `ExpenseListScreen.kt:105`, `PatientListScreen.kt:179`, `StockHistoryScreen.kt:103`, `FullAuditLogScreen.kt:66` |
| `Snackbar` from `LaunchedEffect(error)` | `WasteScreen.kt:61`, `AddVaccinationScreen.kt:58`, `VaccineInventoryScreen.kt:57`, `DashboardScreen.kt:195`, `SyncScreen.kt:48` |
| Bare `Toast.makeText` | 7 screens incl. `AddBatchScreen.kt:73`, `MainActivity.kt:245` |
| Inline `Text(color = error)` | `LoginScreen.kt:119`, `AddExpenseScreen`, `AddStockScreen` |
| **Nothing** | `BorrowedScreen.kt` — 0 references to error/Snackbar/Toast |

⚠️ **The app violates its own rule.** `core/ui/States.kt:74-79` states *"Neither should use
a Toast"* — and `AddBatchScreen.kt:73` toasts **"Batch saved"** on success while failure goes
through `AddBatchUiState.error` (`:21`). Same split in `AddStockScreen.kt:42`,
`AddVaccineScreen.kt:61`, `AddExpenseScreen.kt:50`.

## The worst site

`feature/patient/presentation/PatientViewModel.kt:194-206`:

```kotlin
fun savePatient(patient: Patient, onComplete: () -> Unit) {
    viewModelScope.launch {
        try {
            if (patient.name.isBlank() || patient.dob.isBlank()) {
                throw IllegalArgumentException("Patient name and Date of Birth are required.")
            }
            patientRepository.addPatient(patient)
            onComplete()
        } catch (e: Exception) {
            // Handle validation or save error
        }
    }
}
```

It **throws its own validation error at `:198` and catches it at `:202` and discards it.**
The caller (`AddPatientScreen.kt:136`) passes only an `onComplete` lambda — no `onError` — so
a blank name or a duplicate clinic ID (`PatientRepositoryImpl.kt:155-157`) gives the user
**zero feedback and the screen never advances**.

## Error fields that were dead — both fixed

| Field | Declared | Writers | Result |
|---|---|---|---|
| `DashboardViewModel.errorMessage` | `:47` | **1** (`:278`) | ✅ `DashboardScreen.kt:195-205` shows the snackbar |
| `BorrowedViewModel.actionError` | `:61` | **3** (`:200`, `:238`, `:251`) | ✅ `BorrowedScreen.kt:48` shows the snackbar |
| `AuthViewModel._error` | `:88` | 8 | Set by background `fetchProfile` failures (`:166`, `:193`) but **cleared only inside `login()`** (`:206`) — so it persists for the rest of the process. Its only reader is `LoginScreen.kt:39`, which a user already past login never sees. |

## Loading states that can never clear

Three ViewModels put `.first()` on Room flows inside `viewModelScope.launch` with no `try`
and no `finally`:

```kotlin
// feature/statistics/presentation/FinanceDetailsViewModel.kt:50-57
viewModelScope.launch {
    transactionsFlow.first()      // :52
    patientsFlow.first()          // :53
    _isLoading.value = false      // :55  never reached if any throws
}
```

Same at `FullReportViewModel.kt:66-94` and `StatisticsViewModel.kt:55-95`.

✅ The correct pattern exists in the codebase —
`VaccineDetailViewModel.kt:99-104` uses `.flowOn(...).catch { … }` — but it **discards
`e.message`**, so the user sees an empty list with no explanation.

`ProfileViewModel.kt:85` and `:106` have the same shape via a bare `?: return@launch` after
setting `isLoading = true` at `:78`, with no `finally`.

## The 60 unguarded launches

Worst offenders, because they are **writes**, not reads:

| File:line | Function |
|---|---|
| `DueViewModel.kt:126,134,142,150` | `dismissReminder`, `completeVaccination`, `rescheduleVaccination`, `restoreReminder` |
| `SyncViewModel.kt:38,58,64,70,76,82` | 6 queue mutations |
| `PersonalReminderViewModel.kt:114,115,116,120` | `markReady`, `markPending`, `markCompleted`, `cancel` |
| `BackupViewModel.kt:105,137,202,207,212,219` | 6 backup writes |
| `BorrowedViewModel.kt:178` | `saveBorrowedItem` |
| `AdminViewModel.kt:63-69` | `fetchStaff()` — a network call |

## Error handling in the data layer

### Good — narrow catches with a stated contract

`feature/vaccination/domain/ClinicalVaccinationService.kt:118-137` catches
`InsufficientStockException` only, rethrows `CancellationException` first, and documents at
`:120-127` why any other failure must propagate. ⚠️ **But two `IllegalStateException`s from
`deductStockFromBatch` bypass it.** See [[Vaccination Management]].

`data/backup/BackupCrypto.kt:111-121` maps `AEADBadTagException` → `WrongPassword` and
everything else → `Corrupted`, both typed.

### Bad — `AuditLogger` has two APIs, and the wrong one is used from inside transactions

`core/logger/AuditLogger.kt`:

- `recordLog(...)` — suspend, joins the caller's transaction.
- `log(...)` — fire-and-forget; fires `scope.launch { recordLog(...) }` on a separate
  `Dispatchers.IO` coroutine (`:40-42`).

⚠️ **`log(...)` is called from inside live transactions in 5 places** —
`WasteRepositoryImpl.kt:64`, `:102`; `MergePatientsUseCase.kt:47`;
`BorrowRepositoryImpl.kt:150`, `:185`, `:287` — so those audit rows are written outside the
transaction and **survive a rollback**.

And `recordLog` swallows everything (`:88-90`): inside a transaction, an `audit_logs` insert
that succeeds followed by a `syncRepository.enqueue` that throws leaves a committed
`isSynced = false` audit row with **no queue row** — it will never upload, and the swallowed
exception prevents the intended rollback.

**Collapsing to `recordLog` everywhere would remove an entire class of bug.** See
[[Known Bugs]].

### Repository-level swallowing

`core/sync/CloudRefresh.kt:18-31` is ✅ well designed: `cloudRefresh(tag, rethrow = true)`
**always rethrows a session-shaped failure** even when the caller asked to swallow errors,
so an expired token aborts a refresh instead of silently leaving stale data.

## Where errors are invisible

`app/NeoChildApp.kt:114-128` — two startup tasks run in an untracked scope
(`CoroutineScope(SupervisorJob() + Dispatchers.IO)`, `:76`, never cancelled) and
swallow-and-log with **no user signal**:

```kotlin
} catch (e: Exception) {
    Log.e(TAG, "Unable to migrate historical finance COGS snapshots", e)   // :119
}
} catch (e: Exception) {
    Log.e(TAG, "Unable to re-arm Automatic Backup scheduling", e)         // :126
}
```

A silently disabled Automatic Backup looks exactly like "no backups configured".

## Cancellation correctness

✅ The codebase gets the important cases right:

- `runRefreshStep` rethrows `CancellationException` (`RefreshDataUseCase.kt:138-139`).
- `VaccineDetailViewModel.kt:100` rethrows before handling.
- `ClinicalVaccinationService.kt:118-119` rethrows before catching.
- `processNextItems` breaks the group loop on `FAILED_TRANSIENT_RETRY` to preserve the retry
  budget (`SyncRepositoryImpl.kt:148-156`).

⚠️ `_syncState` has **no `try/finally`** around `= SYNCING`
(`SyncRepositoryImpl.kt:129`), so an escaping exception strands the Dashboard on "syncing".

## Testing note

`app/build.gradle.kts:91-95` sets `unitTests.isReturnDefaultValues = true` with a comment
explaining it stops `android.util.Log` calls from making assertions "pass for the wrong
reason". ✅ Correct intent — but ⚠️ it also means any `Log.e`-only error path would test green
with no assertion. There is no such test today; it is a trap for whoever writes the first.

## Related

- [[MVVM]] — the state patterns these errors should live in
- [[Known Bugs]] — the defects this page documents
- [[Room Database]] — transaction boundaries that error handling depends on
- [[Offline-First Architecture]] — `CloudRefresh` and sync error classification
- [[Testing]] — why none of this is covered
