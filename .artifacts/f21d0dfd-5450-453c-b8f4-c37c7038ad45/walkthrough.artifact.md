# Walkthrough: Unified Doctor Selection

I have successfully implemented a unified Doctor Selection system across the Vaccination and Consultation modules. This change ensures that clinical records are linked to doctors using their unique `employee_id` while displaying their current `display_name` dynamically.

## Key Changes

### 1. Centralized UI Component
- **`DoctorDropdown`**: Created a reusable Material 3 `ExposedDropdownMenuBox` component in `Dropdowns.kt`. It loads doctors dynamically from the `Profile` table (filtered by role and activity status) and provides built-in validation feedback.

### 2. Standardized Data Storage
- **`VisitEntity` & `Vaccination`**: Added `doctorId` to the database entity and domain model to store the doctor's **employee_id**.
- **`Consultation`**: Updated the save logic to ensure `doctorId` strictly stores the `employee_id`.
- **`ClinicalVaccinationService`**: Updated to correctly propagate the `doctorId` to the underlying `VisitEntity` during consultations.

### 3. Smart Defaulting & Validation
- **Auto-Selection**: If the logged-in user is a Doctor, their profile is automatically selected in the dropdown.
- **Mandatory Selection**: Records cannot be saved without a doctor. A clear validation error message appears if no selection is made.

### 4. Dynamic Display & Receipts
- **History Cards**: Both `VaccinationRecordCard` and `ConsultationRecordCard` now perform a real-time lookup in a `doctorMap` (provided by `PatientViewModel`) to display the doctor's *current* name.
- **Receipts**: Updated `ReceiptFormatter`, `ReceiptManager`, and the PDF generator to use the dynamically resolved doctor name, ensuring professional and accurate printouts.

## Verification Results

- **Dynamic Loading**: Verified that only users with the `doctor` role appear in the dropdown, sorted alphabetically.
- **Persistence**: Confirmed that `employee_id` (e.g., "EMP001") is stored in the database instead of Auth UUIDs or static strings.
- **UI Consistency**: Both Add Vaccination and Add Consultation screens now share the exact same selection logic and styling.

> [!TIP]
> Since the doctor's name is now looked up dynamically by `employee_id`, updating a doctor's name in their Profile will automatically update all their previous history records and future receipts.
