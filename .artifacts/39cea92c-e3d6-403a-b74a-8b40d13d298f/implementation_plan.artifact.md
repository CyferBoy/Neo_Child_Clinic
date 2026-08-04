# Fix All Compilation Errors (Vaccination & Staff Model Refactoring)

The project is currently failing to build due to a large number of unresolved references following a refactor of the `Vaccination` model and a likely rename of `Staff` to `Profile`. This plan aims to harmonize the codebase by adding necessary legacy support to the domain models and updating consumers to use the new data structures.

## User Review Required

> [!IMPORTANT]
> I am adding several "Legacy Support" computed properties to the `Vaccination` domain model. This allows existing code to work while we transition to the new `items` and `followUps` structure.
> I will also be renaming `Staff` to `Profile` in files that were missed during the initial rename.

## Proposed Changes

### [Component] Domain Models

#### [MODIFY] [Vaccination.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/domain/model/Vaccination.kt)
- Add computed properties for legacy support:
    - `vaccineNames: List<String>` (from `items`)
    - `batchNumbers: List<String>` (from `items`)
    - `expiryDates: List<String>` (from `items`)
    - `nxtVaccineNames: List<String>` (from `followUps`)
    - `nextDueDate: String` (from `followUps`)
- Add properties that are missing but used in UI/Logic:
    - `isDone: Boolean`
    - `status: ReminderStatus`
    - `cost: Double` (mapping to `totalPaid` or a specific legacy field)
    - `withFees: Boolean`
    - `doctorsAcc: Boolean`

### [Component] Data Layer

#### [MODIFY] [VaccinationEntity.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/local/entity/VaccinationEntity.kt)
- Update `toVaccination()` mapping to include the new fields (`isDone`, `status`, etc.).
- Ensure `toEntity()` correctly maps the domain model back to `VisitEntity`.

#### [MODIFY] [VaccinationRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/repository/VaccinationRepositoryImpl.kt)
- Fix any remaining `toDomain()` or mapping errors.

#### [MODIFY] [ReminderRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/repository/ReminderRepositoryImpl.kt)
- Fix `processDueListInternal` where it uses `copy()` on `Vaccination` with non-existent fields.

### [Component] Utilities

#### [MODIFY] [ReceiptFormatter.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/core/utils/ReceiptFormatter.kt)
- Update to use the new properties or computed properties from `Vaccination`.

#### [MODIFY] [ReminderEngine.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/domain/logic/ReminderEngine.kt)
- Update to use `items` and `followUps` if appropriate, or use the new computed properties.

#### [MODIFY] [PatientUtils.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/core/utils/PatientUtils.kt)
- Fix references to `isDone` and `nextDueDate`.

### [Component] Features (Staff to Profile Rename)

#### [MODIFY] [DashboardTopBar.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/dashboard/DashboardTopBar.kt)
- Rename `Staff` to `Profile`.

#### [MODIFY] [PatientListViewModel.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/patient/PatientListViewModel.kt)
- Rename `Staff` to `Profile`.

#### [MODIFY] [PatientListScreen.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/features/patient/PatientListScreen.kt)
- Fix references to `Staff` and `role` (if needed).

### [Component] Other Fixes

- Fix `VaccinationValidator.kt` (No parameter with name 'receiptNumber' etc.).
- Fix `WidgetWorker.kt` (Unresolved reference 'nextDueDate' etc.).
- Fix `DueViewModel.kt`, `DueTab.kt`, `ReminderCards.kt`, `VaccinationCards.kt` etc.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` until it succeeds.

### Manual Verification
- Deploy the app.
- Check "Due" screen to ensure reminders are showing.
- Check "Patient Details" and "Vaccination History".
- Generate a Receipt to ensure `ReceiptFormatter` works with the new structure.
