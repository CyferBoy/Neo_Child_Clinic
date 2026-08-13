# Task: Next Vaccination Redesign

- [x] Data Layer & Repositories
    - [x] Update `ReminderRepository.kt` interface
    - [x] Redesign `ReminderRepositoryImpl.kt` to use database as source of truth
    - [x] Remove automatic calculation dependency on `ReminderEngine`
- [x] UI Layer (ViewModel)
    - [x] Refactor `AddVaccinationViewModel.kt` to handle single Next Vaccination
    - [x] Update loading/saving logic
- [x] UI Layer (Screen)
    - [x] Redesign `AddVaccinationScreen.kt` UI
- [x] Verification & Cleanup
    - [x] Verify build with `./gradlew :app:compileDebugKotlin`
    - [x] Remove/Comment out obsolete logic in `ReminderEngine.kt`
