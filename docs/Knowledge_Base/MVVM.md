# MVVM

> The UI patterns actually used, counted from source. Related: [[Clean Architecture]] ·
> [[Error Handling]] · [[Known Bugs]] · [[Database Schema]]

## The headline

**There is no sealed `UiState` for screen state anywhere in this app.** Every screen
ViewModel exposes a flat `data class` with nullable fields (`error: String?`,
`isLoading: Boolean`). Exactly one sealed UI state exists —
`SlotsUiState` in `core/ui/Dropdowns.kt:162-169`.

That single fact explains most of [[Error Handling]] and several entries in
[[Known Bugs]]: with no `sealed` hierarchy, nothing forces a screen to handle every branch,
so error fields get declared and then never populated.

## Four coexisting patterns

### Pattern A — flat data class + `MutableStateFlow` (most common)

`feature/settings/presentation/BackupViewModel.kt:15-51`:

```kotlin
data class BackupUiState(
    val isBusy: Boolean = false,
    val cloudBackupsError: String? = null,   // :23
    val message: BackupUiMessage? = null     // :27
)
private val _uiState = MutableStateFlow(BackupUiState())      // :50
val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()  // :51
```

Same shape in `WasteViewModel.kt:44-72`, `AddExpenseViewModel.kt:25-51`.

### Pattern B — derived `combine(...).stateIn(...)`

`feature/dashboard/presentation/DashboardViewModel.kt:215-291` — a **4-level nested**
`combine` over 13 injected dependencies (`:91-104`) producing `DashboardUiState`,
terminated at `:291`:

```kotlin
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())
```

Same shape in `PatientListViewModel.kt:66-94`. Both work around the 5-argument `combine`
overload with a private holder type — `RefreshState<A,B,C,D,E>` at `PatientListViewModel.kt:221`,
`Quad<A,B,C,D>` at `VaccineInventoryViewModel.kt:111`, and raw `values[0] as List<T>` casts
in 5 more ViewModels. See [[Technical Debt]].

### Pattern C — the only sealed state

`core/ui/Dropdowns.kt:162-169`:

```kotlin
sealed class SlotsUiState {
    object Idle / Loading / NoScheduleConfigured
    data class FullDayUnavailable(val reason: String?)
    data class Loaded(val slots: List<AvailableSlot>)
    data class Error(val message: String)     // :168
}
```

Used by `DashboardViewModel.kt:139`, `AddVaccinationViewModel.kt:273`,
`AddConsultationViewModel.kt:134`. **This is the pattern the rest of the app should use** —
it is the only one where "error" cannot be silently forgotten.

### Pattern D — Compose `State`, not `StateFlow`

`core/security/AuthViewModel.kt:84-88`:

```kotlin
private val _isLoading = mutableStateOf(false)
val isLoading: State<Boolean> = _isLoading        // :85
private val _error = mutableStateOf<String?>(null)
val error: State<String?> = _error                // :88
```

Mixed **within the same class** with `MutableStateFlow` for `_profile` (`:90`),
`_logoutBlocked` (`:112`), `_loginSwitchBlocked` (`:119`).

## State exposure to Compose

`collectAsState()` in 35 sites. No `collectAsStateWithLifecycle()`, so state collection
does not stop when the app backgrounds.

`PatientViewModel.kt:122-125` is the **only** place that handles "loading" vs "loaded but
empty" correctly — it uses `SharingStarted.Eagerly` with a `PatientHistoryUiState` that
carries an explicit `isLoaded: Boolean`
(`feature/patient/presentation/PatientHistoryUiState.kt:19-24`). Every other list screen
infers emptiness from a null list, so a brief empty Room emission renders "no patients yet".

## Error surfaces actually wired

| Mechanism | Screens using it |
|---|---|
| `ErrorState(...)` full-screen composable | 4 screens: `ExpenseListScreen.kt:105`, `PatientListScreen.kt:179`, `StockHistoryScreen.kt:103`, `FullAuditLogScreen.kt:66` |
| `Snackbar` from `LaunchedEffect(error)` | `WasteScreen.kt:61`, `AddVaccinationScreen.kt:58`, `VaccineInventoryScreen.kt:57`, `DashboardScreen.kt:195`, `SyncScreen.kt:48` |
| `Toast.makeText` | 7 screens incl. `AddBatchScreen.kt:73`, `MainActivity.kt:245` |
| Inline `Text(color = error)` | `LoginScreen.kt:119`, `AddExpenseScreen`, `AddStockScreen` |
| **Nothing at all** | `BorrowedScreen.kt` — zero references to error, Snackbar, or Toast |

The worst split: `AddBatchScreen.kt:73` toasts **"Batch saved"** on success while the
failure path goes through `AddBatchUiState.error` (`:21`). `core/ui/States.kt:74-79`
explicitly forbids this ("Neither should use a Toast") — and is contradicted four files
later.

## Loading / empty / error coverage, quantified

44 top-level `*Screen.kt` files. `core/ui/Skeleton.kt` exports 7 shimmer composables.

| State | Screens using the shared component | % of 44 |
|---|---|---|
| Loading (`Skeleton*`) | 21 | 48% |
| Empty (`EmptyState`) | 9 | 20% |
| Error (`ErrorState`) | 4 | 9% |

`Skeleton.kt` is genuinely well used. `States.kt` — the file that defines `ErrorState` — is
effectively ignored.

**ViewModels with no error field at all (11):** `VaccineInventoryViewModel`,
`PatientViewModel`, `PersonalReminderViewModel`, `CompletedDismissedViewModel`,
`DueViewModel`, `NotificationSettingsViewModel`, `SettingsViewModel`,
`FinanceDetailsViewModel`, `FullReportViewModel`, `MilestonePatientsViewModel`,
`VaccineDetailViewModel`.

**ViewModels whose `isLoading` can never return to false (3):** all three put `.first()` on
Room flows inside `viewModelScope.launch` with no `try`/`finally`:

```kotlin
// feature/statistics/presentation/FinanceDetailsViewModel.kt:50-57
viewModelScope.launch {
    transactionsFlow.first()    // :52  no try/catch
    patientsFlow.first()        // :53
    _isLoading.value = false    // :55  never reached if any throws
}
```

Same at `FullReportViewModel.kt:66-94` and `StatisticsViewModel.kt:55-95`. The correct
pattern exists in `VaccineDetailViewModel.kt:99-104` (`.catch { … }`) — but it discards
`e.message`, so the user sees an empty list with no explanation.

## God ViewModels

| ViewModel | Lines | Injected deps |
|---|---|---|
| `AddVaccinationViewModel.kt` | 707 | 10 |
| `DashboardViewModel.kt` | 439 | 13 |
| `AuthViewModel.kt` | 369 | 11 |
| `FullReportViewModel.kt` | 324 | 5 |

## Fixed bugs in this layer

Full detail in [[Known Bugs]]; the two MVVM-specific ones are both fixed:

- **`DashboardViewModel.errorMessage` was dead.** It is now written at `:278` and shown by
  `DashboardScreen.kt:195-205`.
- **`BorrowedViewModel.actionError` was dead.** It is now written at `:200`, `:238`, `:251`
  and shown by `BorrowedScreen.kt:48`.

## Test coverage of this layer

**Zero.** There is no `*ViewModelTest.kt` in `app/src/test` or `app/src/androidTest`. All
252 passing unit tests cover pure functions and a few repositories. See [[Testing]].

## Related

- [[Error Handling]] — every idiom, counted
- [[Clean Architecture]] — where these ViewModels sit
- [[Known Bugs]] — the dead-field and stuck-loading bugs
- [[Technical Debt]] — the `combine` workarounds and `refresh()` duplication
