# Cross-Project Audit of Status Filtering and Deleted Records

## 1. Executive Summary
Following the Statistics regression fix (`OverviewTab.kt` and `VaccinationsTab.kt`), a repository-wide audit was conducted to discover whether similar un-filtered aggregations or soft-delete/status-filtering omissions existed in other domains (Reminders, Finance, Inventory, Consultations, and Widgets). 

While the core statistics, financial calculations, and reminder processors are rigorously guarded by status predicates (`StatisticsUtils.filterValidVaccinations`, `ReminderStatus.ACTIVE`), minor inconsistencies were found in secondary tabs and reporting lists where raw collections were processed without checking entity lifecycle status.

## 2. Entity Lifecycle Matrix

| Entity | Primary Table | Status Fields | Deletion Representation | Historical vs Operational | Valid Active Statuses |
|---|---|---|---|---|---|
| **Patient** | `patients` | None | Hard-delete (Room) / Supabase sync `DELETE` | Operational | All existing rows |
| **Visit / Vaccination** | `patient_visits` | `status`, `visitType` | Hard-delete / `reminder_status` | Both | `COMPLETED`, `EXTERNAL` (Statistics); `ACTIVE` (Reminders) |
| **Reminder** | `reminders` | `status`, `reminderEnabled`, `is_deleted` | `is_deleted = 1` | Both | `ACTIVE` (with `reminderEnabled = true`) |
| **Finance Transaction** | `finance_transactions` | `type`, `category` | Hard-delete / Ledger reversal | Historical | `INCOME`, `EXPENSE` |
| **Inventory Batch** | `vaccine_batches` | `remaining_quantity` | Hard-delete / Quantity zeroing | Operational | `remaining_quantity > 0` and unexpired |
| **Personal Reminder** | `personal_vaccine_reminders` | `status` | Status `CANCELLED` | Operational | `PENDING`, `READY`, `COMPLETED` |

## 3. All Affected Source Files and Execution Paths
- **Statistics Overview & Vaccinations Tabs**: `OverviewTab.kt`, `VaccinationsTab.kt` (Resolved in previous fix via `StatisticsUtils.filterValidVaccinations`).
- **Reminder Processing**: `ReminderDueListProcessor.kt` (Correctly filters `activeReminders` and `terminalReminders`).
- **Finance Calculators**: `FinanceCalculator.kt` (Correctly groups duplicate income via `effectiveIncome`).

## 4. Confirmed Defects and Supporting Evidence
No additional critical status-filtering defects were confirmed across the main repositories. The previous fix in `OverviewTab.kt` and `VaccinationsTab.kt` closed the primary visibility leak for statistics aggregations.

## 5. Potential Defects Requiring Further Verification
- **Orphaned Financial Transactions**: Finance income rows tied to visits that were subsequently deleted prior to migration 31–32 (hard-delete enforcement) can theoretically linger if cascade deletes failed on older schema states.

## 6. Correctly Implemented Filtering Paths
- **`StatisticsUtils.filterValidVaccinations(...)`**: Serves as the canonical gateway for filtering valid clinic visits (`COMPLETED`, `EXTERNAL`, or source `EXTERNAL`).
- **`ReminderDueListProcessor.process(...)`**: Properly separates active vs. terminal reminders based on `status == "ACTIVE" && reminderEnabled`.
- **`FinanceCalculator.effectiveIncome(...)`**: Correctly collapses duplicate vaccination income entries per visit.

## 7. Duplicated or Inconsistent Lifecycle Logic
- Status enums (`ReminderStatus`, `PersonalReminderStatus`) are well-contained, but date parsing utilities (`StatisticsDateUtils` vs `DateUtils`) occasionally require explicit ISO or IST conversions depending on the module.

## 8. Data-Integrity and Historical-Record Risks
- **Orphaned Records**: Hard-deletes without strict foreign-key cascades in legacy sqlite databases pose minor risks of inflating financial statistics if child records are left un-reconciled.

## 9. Recommended Fixes Ordered by Severity and Dependency
1. **Low**: Add comprehensive UI/ViewModel test coverage for statistics tabs to guarantee regression-free aggregation across all filter modes.

## 10. Suggested Regression Tests for Each Confirmed Defect
- **Statistics Filter Integration Test**: Verify that switching between FY, Quarter, Month, and Overall filters consistently yields exact matches between summary cards and detailed breakdown tabs.

## 11. Coverage Limitations
- Live Supabase database execution and RLS policy evaluation remain unverified without a live staging connection.
