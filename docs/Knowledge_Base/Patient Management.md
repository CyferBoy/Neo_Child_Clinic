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
(`:149-159`) and inserts with `OnConflictStrategy.ABORT` (`:168`) so a clinic-id collision
fails loudly instead of REPLACE-deleting another patient (`PatientDao.kt:29-30`). That is a
good decision — see [[Architecture Decisions]].

`PatientClinicIdMigrationWorker` backfills clinic ids for rows created before the field
existed, and runs on an untracked scope in `NeoChildApp.kt`.

## Write paths — all offline-safe

**Add** — `PatientRepositoryImpl.kt:142-191`:

1. `database.withTransaction { }` (`:148`) — mint/validate clinic id, insert with
   `isSynced = false`, **enqueue for sync in the same transaction** (`:172-177`).
2. `auditLogger.recordLog(...)` outside the transaction — local-only, fully try/caught.
3. `patientCache.invalidate(patient.id)` (`:190`).

**Delete** — `:193-251`. One transaction cascading reminders, personal reminders,
vaccinations and consultations, enqueueing a DELETE for each. **The patient DELETE is
enqueued last** (`:236-239`) so server-side FK order is safe.

Both paths touch no `Postgrest`, `Auth`, or `Realtime` object. See [[Offline-First
Architecture]].

## Search

`PatientDao.searchPatients` (`PatientDao.kt:36-41`):

```sql
WHERE name LIKE :q OR phone LIKE :q OR patientClinicId LIKE :q OR address LIKE :q
   OR id IN (SELECT patientId FROM patient_visits WHERE vaccineNames LIKE :q OR receiptNumber LIKE :q)
```

⚠️ Three problems, none confirmed as a live bug:

1. `address` has **no index**.
2. `:q` arrives bare — no `%` added in Kotlin. The caller must supply wildcards. *(Not
   traced — this may be intentional.)*
3. The correlated subquery scans `patient_visits.vaccineNames`, which is an **unindexed
   comma-joined string column** built at `VaccinationEntity.kt:115`.

The correct delimited-match pattern already exists elsewhere —
`DueReminderDao.kt:101` wraps the value in commas, and
`UpcomingVaccinationDaoTest.kt:103` documents why: *"a plain `LIKE '%vac-a%'` would also
match 'vac-ab'"*.

`SearchPatientsUseCase` is the domain entry point; `PatientListViewModel` combines 5 Room
flows through a private holder type (`:221`) to work around the `combine` arity limit.

## Merge patients

`feature/patient/domain/MergePatientsUseCase.kt` — moves records from a duplicate patient
into a survivor and deletes the duplicate. Runs in one transaction (`:31`) but calls
`auditLogger.log(...)` — the **fire-and-forget** variant — from inside it (`:47`), so the
audit row is written on a separate coroutine and **survives a rollback**. See
[[Error Handling]] and [[Known Bugs]].

## History and the vaccination card

`PatientHistoryUiState` (`presentation/PatientHistoryUiState.kt:19-24`) carries an explicit
`isLoaded: Boolean` and is the **only** screen in the app that distinguishes "still loading"
from "loaded and empty" (`:8-18` documents why). `PatientViewModel.kt:122-125` uses
`SharingStarted.Eagerly` with `initialValue = null` to make that possible.

`PatientVaccinationCardEntity` uses Room `@Relation` to pull reminders for a visit. ⚠️
**`@Relation` cannot filter**, so `getRemindersByVisitId` (`DueReminderDao.kt:39`) returning
soft-deleted rows puts them on the history screen regardless of any predicate. See
[[Room Database]] and [[Known Bugs]].

## Receipts

`ReceiptFormatter` → `ReceiptGenerator` → `ReceiptPrinter`. The receipt number itself is
**server-generated**: `20260824_receipt_numbering.sql` installs a `BEFORE INSERT` trigger
that assigns from `receipt_sequences`, and the client always uploads a blank value
(`20260824:16-18`). The uploader reads the trigger-generated value back after upsert
(`SyncUploader.kt:650-673`).

## Cache interaction

`PatientCache` is invalidated per-id on write, and cleared wholesale on Room invalidation
of table `patients` (`PatientRepositoryImpl.kt:66-68`) and at the end of `refreshPatients`
(`:139`). ⚠️ Because the Room observer is scoped to `patients` only, **editing a vaccination
does not clear it** — a cached `Patient` can hold a stale history for up to 5 minutes. See
[[Caching]].

## Confirmed findings

| # | Finding | Severity | Evidence |
|---|---|---|---|
| 1 | `PatientViewModel.savePatient` catches its **own validation exception** and discards it — a blank name gives the user zero feedback and no navigation | High | `PatientViewModel.kt:198-204` |
| 2 | `MergePatientsUseCase` writes its audit row outside the transaction | Medium | `MergePatientsUseCase.kt:47` + `AuditLogger.kt:40-42` |
| 3 | `PatientRepositoryImpl.deletePatient` calls `getDueRemindersForPatient`, which omits the `is_deleted` filter | Low | `DueReminderDao.kt:109`, `:200` |
| 4 | `searchPatients` cannot use any of its indices; `address` is unindexed | Low | `PatientDao.kt:36-41` |
| 5 | N+1 in `refreshPatients` — uses the bulk `unsyncedIds` ✅ but still one query per remote row elsewhere | Info | `PatientRepositoryImpl.kt:97` |

All ranked in [[Known Bugs]].

## Related

- [[Vaccination Management]] — where a patient record usually changes
- [[Due Vaccination]] — reminders hang off the patient
- [[Statistics Dashboard]] — `effectiveRegistrationDate` derives from patient data
- [[Backup and Restore]] — patients are table 6 of 22 in the export
