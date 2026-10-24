# Statistics — Dependency Map

Complete data paths from storage to pixels, traced in the working tree on 2026-10-24.

```
Room (local source of truth, reactive Flow)                    Supabase (remote, filtered pull)
├─ patients ────────────── PatientDao.getAllPatients() ─┐      patients / patient_visits / vaccination_items
├─ patient_visits ──┬──── VaccinationDao                 │      / reminders / finance_transactions / expenses /
│   └─ vaccination_items   .getAllVaccinationsWithItems() │      vaccines / vaccine_batches …
│                    │      (no status filter, by design)│      every pull: eq("is_deleted", false)
├─ reminders ────────┴──── DueReminderDao                │        │
│                         .getUpcomingVaccinations() ────┤        │ refresh + RemotePrune (hard local
│                           (SQL: ACTIVE AND is_deleted=0)│       │ delete of ids no longer returned)
├─ finance_transactions ── FinanceDao.getAllTransactions()│       ▼
├─ expenses ─────────────── ExpenseDao.getAllExpenses() ─┤   RemotePrune.computeBatches /
└─ vaccines + batches ───── VaccineDao (2 queries) ──────┘   computeRemindersBapters
                                    │
                                    ▼
      RepositoryImpls (Patient · Vaccination · Reminder · Finance · Expense · Inventory)
                                    │
        ┌───────────────────────────┴──────────────────────────────┐
        ▼                                                          ▼
StatisticsViewModel (combine ×6)                         ClinicStatsManager (combine ×4)
  → StatisticsUiState                                     → ClinicStats
        │                                                   └──► DailySummaryWorker ONLY
        ▼                                                         (dueToday, overdue, lowStock)
  ┌─────┴──────────────────────────────────────────────┐
  │              shared domain rules                    │
  │ StatisticsUtils.isCountedVaccination / filterValid…│  ← the visit status gate
  │ StatisticsUtils.visitTypeStatsForFilter (NCC-043)  │  ← date filter ONLY, never status
  │ StatisticsDateUtils.computeEffectiveRegistration…  │  ← takes RAW visits (its contract)
  │ FinanceCalculator.effectiveIncome (dedup rule)     │
  │ FinanceCalculator.calculateFinanceStats / …Grouped │
  │ ExpenseCalculator                                   │
  └─────┬──────────────────────────────────────────────┘
        ▼
  OverviewTab · PatientsTab · VaccinationsTab · FinanceTab · FullReportViewModel
  VaccineDetailViewModel · FinanceDetailsViewModel · MilestonePatientsViewModel
        ▼
  StatisticsScreen / charts / summary cards / drill-down screens
```

## Rule separation (must not be unified)

| Rule | Applies to | Where enforced |
|---|---|---|
| Deleted ⇒ absent | everything | local hard delete + remote `is_deleted=false` pull + `RemotePrune` |
| `is_deleted = 0` (soft) | reminders only | SQL, every `DueReminderDao` read |
| `COMPLETED \|\| EXTERNAL` (administered) | doses, COGS, vaccination counts | `StatisticsUtils.isCountedVaccination` |
| **No status gate** | visit-type counts, effective-reg dating, finance *dating* | `visitTypeStatsForFilter`, raw `allVisits` |
| `ACTIVE AND reminderEnabled AND category=VACCINATION` | Upcoming Vaccine Need | SQL |
| Dedup per visit | all revenue | `FinanceCalculator.effectiveIncome` |

## Realtime / cache

- No aggregate cache exists; every metric recomputes from Room flows on emission.
- Realtime (`RealtimeChangeSubscriptions.kt:39-65`) carries no payload — it only triggers the
  filtered refresh paths, so it cannot reintroduce deleted rows.
- `StatisticsViewModel` uses `refreshDataUseCase` on pull-to-refresh only; `stateIn` holds UI
  state but recomputes on every upstream emission, so a deletion propagates on the next emission.
