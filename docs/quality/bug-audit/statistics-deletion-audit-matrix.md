# Statistics — Deletion & Status Audit Matrix

One row per metric / contributing data path. "Deletion" = can a *deleted* record contribute.
"Status" = can a cancelled/dismissed/pending record contribute.
Verification: ✅ = auditor read the code path personally; 🟦 = independent sub-audit, spot-checked.

| Metric | Source entity / table | Source file · function | Query / DAO | Deletion field | Deletion filtering | Status filtering | Aggregation location | Finding | Verified |
|---|---|---|---|---|---|---|---|---|---|
| Total / New Patients | `patients` | `PatientDao.getAllPatients()` → `OverviewTab.kt:55-57` | `SELECT * FROM patients` | none (hard delete, migration 31→32) | N/A — deleted rows are physically gone | N/A | `OverviewTab.kt:175` | — | ✅ |
| Patients (tab) | `patients` | `PatientsTab.kt:46-50` | same | none | N/A | N/A | `PatientsTab.kt:112,120,310-347` | — | ✅ |
| Patient effective-reg bucketing | `patients` × `patient_visits` | `StatisticsDateUtils.computeEffectiveRegistrationDates` | in-memory | none | N/A | **raw visits** (correct, incl. ACTIVE consultations) | `PatientsTab.kt:36-38`, `OverviewTab.kt:50-52`, `FullReportViewModel.kt:169,264` | NCC-043 (fixed) | ✅ |
| Consulted Patients / Total Consultation | `patient_visits` (`visitType=CONSULTATION`) | `StatisticsUtils.visitTypeStatsForFilter` | `VaccinationDao.getAllVaccinationsWithItems()` (`:33-35`, no status filter) | none | N/A | **none by design** — consultation visits are always `ACTIVE` | `OverviewTab.kt:81-86` | NCC-043 (fixed; was permanently 0 after `8b3c415`) | ✅ |
| Total Vaccination (card) | `patient_visits` (`visitType=VACCINATION`) | same | same | none | N/A | counted by visit_type, **doses** stay gated | `OverviewTab.kt:181` | — | ✅ |
| Consultation series (Patient Activity chart) | `patient_visits` | `OverviewTab.kt:108-112` | same | none | N/A | raw (fixed) | `OverviewTab.kt:99-127` | NCC-043 (fixed) | ✅ |
| Vaccinated Patients / Total Doses | `patient_visits` × `vaccination_items` | `filterValidVaccinations` → `OverviewTab.kt:58-60,185` | same | none | N/A | `COMPLETED \|\| EXTERNAL` gate ✅ | `OverviewTab.kt:177,185` | — | ✅ |
| Administered doses per vaccine | `vaccination_items` | `VaccinationsTab.administeredDoses` (`:36-41`) | same | none | N/A | parent-visit gate ✅ (comments corrected) | `VaccinationsTab.kt:418-422` | NCC-043-docs | ✅ |
| Consultation/Vaccination series (Full Report) | `patient_visits` | `FullReportViewModel.kt:201-206,293-294` | same | none | N/A | consult = raw (fixed); vacc = valid ✅ | `computeMonthly`/`computeDaily` | NCC-043 (fixed) | ✅ |
| Revenue / Cash / Online | `finance_transactions` | `FinanceCalculator.effectiveIncome` (`:317-326`) | `FinanceDao.getAllTransactions()` | none locally; remote `is_deleted=false` (`FinanceRepositoryImpl.kt:385`) | ✅ remote | visit status deliberately ignored **for dating only** | `FinanceCalculator.kt:68-144`, `FinanceTab.kt:82-87` | **NCC-044 (fixed)**: pull guard let `local == null` bypass the pending-DELETE check, re-importing rows deleted locally mid-upload | ✅ |
| COGS | `finance_transactions` snapshot × valid visits | `FinanceCalculator.kt:79,125-128` | same | none | ✅ | valid-visit gate ✅ | `FinanceCalculator.kt` | safe failure: unmatched ⇒ profit "Unavailable" | ✅ |
| Expenses / Net income | `expenses` | `ExpenseCalculator.kt:20-46` | `ExpenseDao.getAllExpenses()` | none locally; remote filter ✅ | ✅ | N/A | `FinanceTab.kt:93-100` | — | 🟦 |
| Upcoming Vaccine Need | `reminders` | `DueReminderDao.getUpcomingVaccinations()` (`:130-131`) | SQL `status='ACTIVE' AND reminderEnabled=1 AND category='VACCINATION' AND is_deleted=0` | `is_deleted` ✅ in SQL | ✅ SQL-level | ✅ SQL-level | `VaccinationsTab.kt:362-416` | — | ✅ |
| Brand/type drill-down | `reminders` | `VaccineDetailViewModel.kt:61-63` | `getUpcomingVaccinationsByType/ByVaccineId` (same predicate) | `is_deleted` ✅ | ✅ | ✅ | `VaccineDetailViewModel.kt` | — | 🟦 |
| Milestone patients | `patients` | `MilestonePatientsViewModel.kt:50-67` | — | none | N/A | N/A | VM | timezone drift vs card (recorded) | 🟦 |
| Due Today / Overdue (worker) | `reminders` | `ReminderRepositoryImpl.getDueList` → `getAllReminders()` (`DueReminderDao.kt:82-83`) | SQL `is_deleted=0` | ✅ | ✅ | `ClinicStatsManager.kt:81-88` | — | ✅ |
| Top vaccines / today & monthly counts (worker) | `patient_visits` | `ClinicStatsManager.kt:54-58,107-123` | `allVaccinations` | none | N/A | `isCountedVaccination` (dedup rule fixed) | `ClinicStatsManager` | metrics dead — read nowhere | 🟦 |

## Aggregate-placement checks (Phase 5)

- Every status/deletion filter is applied **before** aggregation. No metric filters after an
  aggregate is computed; the only post-aggregate filter in the feature is a display-name filter
  in `VaccineDetailViewModel.kt:91-95` (benign).
- No post-aggregate filter can mask a bad aggregate anywhere in Statistics.
- Filters are **not** uniform by design and must not be: visits-by-type = none, administered
  doses = `COMPLETED/EXTERNAL`, reminders = SQL `ACTIVE + is_deleted=0`, finance dating = raw.
