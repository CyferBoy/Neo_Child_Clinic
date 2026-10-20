# Patient Management

> `feature/patient/` — CRUD, search, merge, notes, todos, documents, ID generation.
> Related: [[Vaccination Management]] · [[Offline-First Architecture]] · [[MVVM]]

## Layout

```
feature/patient/
├── data/     PatientRepositoryImpl, PatientCache, DocumentRepositoryImpl,
│             PatientTodoRepositoryImpl, PatientIdGenerator,
│             PatientClinicIdMigrationWorker
├── domain/   repository/PatientRepository.kt, MergePatientsUseCase,
│             SearchPatientsUseCase
└── presentation/  PatientListViewModel, PatientViewModel, AddPatientScreen,
                   PatientDetailsScreen, PatientForm(+Sections), PatientRecordCards,
                   VaccinationCards, ReceiptFormatter/Generator/Printer,
                   PatientHistoryUiState
```

Navigation: 10 destinations (`app/NavigationPatientGraph.kt`). All patient ids are
interpolated raw into routes — see [[Clean Architecture]].

## Identity — two ids

| Field | Source | Notes |
|---|---|---|
| `id` | `UUID.randomUUID()` | the Postgres primary key |
| `patientClinicId` | `PatientIdGenerator`, unique-indexed | the **human-facing** clinic number staff read over the phone |

`PatientRepositoryImpl.addPatient` mints the clinic id **inside** the transaction
(`:164-174`) and inserts with `OnConflictStrategy.ABORT` (`:183`) so a clinic-id collision
fails loudly instead of REPLACE-deleting another patient (`PatientDao.kt:30-31`). That is a
good decision — see [[Architecture Decisions]].

`PatientClinicIdMigrationWorker` backfills clinic ids for rows created before the field
existed, and runs on an untracked scope in `NeoChildApp.kt`.

## Write paths — all offline-safe

**Add** — `PatientRepositoryImpl.kt:157-206`:

1. `database.withTransaction { }` (`:163`) — mint/validate clinic id, insert with
   `isSynced = false`, **enqueue for sync in the same transaction** (`:187-192`).
2. `auditLogger.recordLog(...)` outside the transaction — local-only, fully try/caught.
3. `patientCache.invalidate(patient.id)` (`:205`).

**Delete** — `:208-266`. One transaction cascading reminders, personal reminders,
vaccinations and consultations, enqueueing a DELETE for each. **The patient DELETE is
enqueued last** (`:251-254`) so server-side FK order is safe.

Both paths touch no `Postgrest`, `Auth`, or `Realtime` object. See [[Offline-First
Architecture]].

## Search

`PatientDao.searchPatients` (`PatientDao.kt:44-49`):

```sql
WHERE name LIKE :q OR phone LIKE :q OR patientClinicId LIKE :q OR address LIKE :q
   OR id IN (SELECT patientId FROM patient_visits WHERE vaccineNames LIKE :q OR receiptNumber LIKE :q)
```

⚠️ Two problems, neither confirmed as a live bug:

1. `address` has **no index**.
2. The correlated subquery scans `patient_visits.vaccineNames`, which is an **unindexed
   comma-joined string column** built at `VaccinationEntity.kt:115`.

The wildcards are **not** missing: `SearchPatientsUseCase.kt:19` passes `"%$query%"`, and a
blank query returns `allPatients` (`:16-17`). [[Quality/Hypotheses]] HYP-008 was refuted on
this basis (2026-10-18).

The correct exact-match pattern already exists elsewhere —
`DueReminderDao.kt:121` matches the id wrapped in its own quotes via `instr(...)`, and
`UpcomingVaccinationDaoTest.kt:127` documents why: *"a plain `LIKE '%vac-a%'` would also
match 'vac-ab'"*.

`SearchPatientsUseCase` is the domain entry point; `PatientListViewModel` combines 5 Room
flows through a private holder type (`:221`) to work around the `combine` arity limit.

## Merge patients

`feature/patient/domain/MergePatientsUseCase.kt` — moves records from a duplicate patient
into a survivor and deletes the duplicate. Runs in one transaction (`:31`). It used to call
`auditLogger.log(...)` — the **fire-and-forget** variant — from inside that transaction
(`:47`), so the audit row was written on a separate coroutine and **survived a rollback**.
✅ Resolved: it now calls the suspendable `auditLogger.recordLog(...)` (`:47`), inside the
transaction. Status: [[Quality/Bug Register]] — `NCC-022`.

## History and the vaccination card

`PatientHistoryUiState` (`presentation/PatientHistoryUiState.kt:19-24`) carries an explicit
`isLoaded: Boolean` and is the **only** screen in the app that distinguishes "still loading"
from "loaded and empty" (`:8-18` documents why). `PatientViewModel.kt:122-125` uses
`SharingStarted.Eagerly` with `initialValue = null` to make that possible.

`PatientVaccinationCardEntity` uses Room `@Relation` to pull reminders for a visit. ⚠️
**`@Relation` cannot filter** — but it no longer has to: `getRemindersByVisitId`
(`DueReminderDao.kt:42-43`) now filters `is_deleted = 0` in SQL. ✅ That closes the
soft-deleted-row leak on the history screen (`NCC-006`). Status:
[[Quality/Bug Register]].

## Receipts

`ReceiptFormatter` → `ReceiptGenerator` → `ReceiptPrinter`. The receipt number itself is
**server-generated**: `20260824_receipt_numbering.sql` installs a `BEFORE INSERT` trigger
that assigns from `receipt_sequences`, and the client always uploads a blank value
(`20260824:16-18`). The uploader reads the trigger-generated value back after upsert
(`SyncUploader.kt:650-673`).

## Cache interaction

`PatientCache` is invalidated per-id on write, and cleared wholesale on Room invalidation
of table `patients` (`PatientRepositoryImpl.kt:66-68`) and at the end of `refreshPatients`
(`:154`). ✅ The `patients`-only scope is sufficient: `Patient` is a typealias for
`PatientEntity` (`Patient.kt:3`), which carries scalar patient fields only
(`PatientEntity.kt:21-38`), so a vaccination edit leaves nothing stale behind. See
[[Caching]] and [[Quality/Hypotheses]] HYP-001.

## Confirmed findings

| # | Finding | Severity | Evidence |
|---|---|---|---|
| 1 | ✅ `savePatient` used to catch its **own validation exception** and discard it — a blank name gave zero feedback and no navigation. Now takes a required `onError`, invokes it (`:215`) and rethrows `CancellationException` (`:212`) | was High | `PatientViewModel.kt:200-215` — `NCC-015` |
| 2 | ✅ `MergePatientsUseCase` used to write its audit row outside the transaction (`log()` = fire-and-forget). Now calls the suspendable `recordLog()` inside it | was Medium | `MergePatientsUseCase.kt:47` — `NCC-022` |
| 3 | ✅ `deletePatient` used to pull reminders through a query with no `is_deleted` filter. The DAO now filters in SQL | was Low | `DueReminderDao.kt:129-130` — `NCC-006` |
| 4 | `searchPatients` cannot use any of its indices; `address` is unindexed | Low | `PatientDao.kt:44-49` |
| 5 | N+1 in `refreshPatients` — uses the bulk `unsyncedIds` ✅ but still one query per remote row elsewhere | Info | `PatientRepositoryImpl.kt:97` |

Status lives in [[Quality/Bug Register]]; [[Known Bugs]] is the 2026-10-16 audit record.

## Related

- [[Vaccination Management]] — where a patient record usually changes
- [[Due Vaccination]] — reminders hang off the patient
- [[Statistics Dashboard]] — `effectiveRegistrationDate` derives from patient data
- [[Backup and Restore]] — patients are table 6 of 22 in the export
