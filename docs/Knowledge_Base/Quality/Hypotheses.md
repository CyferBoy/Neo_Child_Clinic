# Hypotheses

> Suspected defects that **cannot be confirmed from source alone**. Kept separate from
> [[Quality/Bug Register]] so an unproven claim is never mistaken for a verified one.
> Companion: [[Quality/Security Concerns]] · [[Testing]]

## Status vocabulary

| Status | Meaning |
|---|---|
| **Hypothesis** | A real smell; impact or reachability not proven |
| **Needs runtime** | Provable only by running the app or a build |
| **Needs live DB** | Provable only against the hosted Supabase project |
| **Refuted** | Checked and found not to apply — kept for provenance |

⚠️ **Nothing here may be promoted to Confirmed without the stated evidence.** Moving an item
out of this file requires recording what was run and what it showed.

## Index

IDs are section headings below — jump to them with the outline pane (`Ctrl/Cmd+Shift+O`).
They stay plain text here because a wikilink alias needs a `|`, which would end the table cell.

| ID | Hypothesis | Status | Needs |
|---|---|---|---|
| HYP-001 | ~~`PatientCache` serves stale data after a vaccination edit~~ | ✅ **Refuted** 2026-10-19 | cache holds scalar patient rows only |
| HYP-002 | ~~`Result.failure()` cancels appended WorkManager dependents~~ | ✅ **Refuted** 2026-10-18 | `SyncManagerImpl` uses `REPLACE` |
| HYP-003 | `_syncState` strands on `SYNCING` after an exception | Needs runtime | injected DAO failure |
| HYP-004 | `VaccineDetailViewModel` hides rows the card counts | Needs runtime | legacy `vaccineName` data |
| HYP-005 | `vaccination_items.batchId = ""` — a blank batch is recorded FAILED, not a rollback | Needs runtime | legacy rows |
| HYP-006 | Raw `execSQL` in `markUploaded` may not fire Room invalidation | Needs runtime | Flow observation |
| HYP-007 | ISO string comparison is wrong at sub-second boundaries | Hypothesis | a boundary test |
| HYP-008 | ~~`searchPatients` is passed `:q` without wildcards~~ | ✅ **Refuted** 2026-10-18 | caller builds `"%$query%"` |
| HYP-009 | `AuthViewModel` runs a DAO query on the main thread | Needs runtime | StrictMode / trace |

## Refuted

| Item | Why refuted |
|---|---|
| Audit #43 — nested scrollable-in-scrollable | Both hits (`WasteScreen.kt:279`, `BorrowedScreen.kt:288`) are `verticalScroll` inside an **`AlertDialog`** text slot, not nested in a `LazyColumn`. Correct Material behaviour. |
| Audit #39 third claim — `SimpleGenderChart` inaccessible | It renders labelled text rows with percentages, not a Canvas. Already accessible. |
| Soft-deleted rows leaking into **statistics** | `expenses` and `finance_transactions` dropped `is_deleted`; `reminders` is the only table that kept it and **nothing writes it**. Every aggregate is clean by construction. See [[Statistics Dashboard]]. |
| `AutoBackupWorker` runs without network | The constraint is conditional on `cloudEnabled` by design (`BackupAutoScheduler.kt:36-37`) — local backups need no network. |
| `BackupDao`'s lack of `@Transaction` is a bug | Atomicity comes from `BackupRestorer.kt:50`'s `database.withTransaction`. Works, but is invisible from the DAO. Recorded as a readability concern, not a defect. |
| HYP-002 — `Result.failure()` cancels appended WorkManager dependents | **Premise is dead (2026-10-18).** There is no appended chain: `SyncManagerImpl.kt:39` and `:61` both use `ExistingWorkPolicy.REPLACE`, not `APPEND_OR_REPLACE`, so a failed element has no dependents to cancel. `:32-36` is now the explanatory comment describing the switch. [[Quality/Bug Register]] NCC-019 is Fixed on that basis. |
| HYP-008 — `searchPatients` receives `:q` without wildcards | **Refuted by source (2026-10-18).** `SearchPatientsUseCase.kt:19` calls `patientRepository.searchPatients("%$query%")`, and `:16-17` returns `allPatients` for a blank query. Reached from `PatientListViewModel.kt:67`. The DAO takes a fully-formed pattern, exactly as `PatientDao.kt:36-41` expects. |
| HYP-001 — `PatientCache` serves stale data after a vaccination edit | **Refuted by source (2026-10-19).** `Patient` is a typealias for `PatientEntity` (`Patient.kt:3`), which carries scalar patient fields only (`PatientEntity.kt:21-38`) — no history, no vaccination counts. The cache is populated only by `getPatientById` (`PatientRepositoryImpl.kt:88-89`; `allPatients` is an uncached Room Flow, `:85-86`) and cleared by any write to the `patients` table (`:66-68`). Editing a vaccination cannot make the cached value user-visibly stale. |

---

## HYP-001 — `PatientCache` serves stale data after a vaccination edit

✅ **Refuted by source 2026-10-19.** The `patients`-only invalidation observer is real
(`PatientRepositoryImpl.kt:66-68`), but there is nothing in the cached object to go stale.

`Patient` is a typealias for `PatientEntity` (`Patient.kt:3`), and `PatientEntity` carries
scalar patient fields only (`PatientEntity.kt:21-38`) — no history, no vaccination rows, no
counts. The cache is populated only by `getPatientById` (`PatientRepositoryImpl.kt:88-89`);
`allPatients` is an uncached Room `Flow` (`:85-86`), and any write to the `patients` table
clears the cache (`:66-68`). Editing a vaccination cannot make the cached value
user-visibly stale, because the cached value does not embed vaccination data.

The original reasoning stays here for provenance; do not re-raise it.

**Links.** [[Caching]] · [[Patient Management]]

---

## HYP-002 — `Result.failure()` cancels appended WorkManager dependents

✅ **Refuted 2026-10-18.** ⚠️ This hypothesis rested on a policy that no longer exists.

**Why it was believed.** `SyncWorker.kt:36` returns `Result.failure()`; if it were an
element of an `APPEND_OR_REPLACE` chain, WorkManager would cancel its dependents.

**Why it fails.** `SyncManagerImpl.kt:39` and `:61` both use
`ExistingWorkPolicy.REPLACE` — each enqueue **replaces** the previous work rather than
appending behind it. There is no chain, so there are no dependents to cancel. `:32-36` is
now the explanatory comment recording the switch away from `APPEND_OR_REPLACE`, which is
what [[Quality/Bug Register#NCC-019 to NCC-024 — Sync and integrity|NCC-019]] (Fixed) addressed.

The original reasoning stays here for provenance; do not re-raise it.

**Links.** [[Offline Synchronization]] · [[Quality/Bug Register#NCC-019 to NCC-024 — Sync and integrity|NCC-019]]

---

## HYP-003 — `_syncState` strands on `SYNCING`

**Needs runtime.** `SyncRepositoryImpl.kt:129` sets `_syncState.value = SYNCING` with **no
`try/finally`** around `processNextItemsInternal()`. An escaping exception (a Room or DB
error) leaves the Dashboard observing "syncing" indefinitely. `SyncViewModel.processSync()`
(`:38-42`) has no catch either.

⚠️ Why unproven: no known path currently throws out of `processNextItemsInternal` — it is
written defensively. It is a robustness gap, not a demonstrated failure.

**To confirm.** Inject a DAO that throws from `getItemsByStatus`.

**Links.** [[Offline-First Architecture]]

---

## HYP-004 — `VaccineDetailViewModel` hides rows the card counts

**Needs runtime.** `DueReminderDao.kt:121` selects by quoted-id `instr()` match on
`nxt_vaccine_id` (the NCC-041 fix), then `VaccineDetailViewModel.kt:91-95` drops any row whose
`cleanVaccineName(reminder.vaccineName)` does not case-insensitively equal the catalog
`brandName`. The comment at `:57-60` promises exact card/detail parity.

⚠️ Why unproven: rows saved before `ReminderRepositoryImpl.kt:173-177` began normalizing
`vaccineName` to the catalog brand would be counted on the card and hidden in the list — but
whether such rows exist is a data question.

**To confirm.** Compare the sum of brand counts from the DAO against
`UpcomingVaccineGroupingTest`'s invariant on a real device with pre-normalization data.

**Links.** [[Statistics Dashboard]]

---

## HYP-005 — Blank `batchId` on legacy items

**Needs runtime.** `VaccinationItem.batchId` defaults to `""` (`domain/model/Vaccination.kt:12`).
`deductStockFromBatch("")` throws `StockDeductionBlockedException("Batch not found")`
(`InventoryRepositoryImpl.kt:462`), which is caught, not a rollback: the create path records
a FAILED `inventory_deductions` row and still saves the visit
(`ClinicalVaccinationService.kt:152`, row at `:162-171`), and the edit path records the batch
as a shortfall (`VaccinationEditEngine.kt:183`).

⚠️ Why unproven: this requires legacy rows written before `batchId` was populated, and
`VaccinationItemEntity.batchId` has a CASCADE FK to `vaccine_batches`, so such rows may not
be able to exist.

**To confirm.** `select count(*) from vaccination_items where batchId = '';`

**Links.** [[Inventory Management]]

---

## HYP-006 — Raw `execSQL` may not fire Room invalidation

**Needs runtime.** `SyncUploader.kt:382-385` uses
`openHelper.writableDatabase.execSQL` for `markUploaded`, bypassing the DAO layer.

⚠️ Why unproven: whether `InvalidationTracker` observes a raw write is a Room internal
behaviour. If it does not, UI `Flow`s on the affected tables (e.g. `waste_records`) would
not repaint until the next query. `PatientCache` relies only on the `patients` observer,
which is unaffected.

**To confirm.** Upload a sync that writes `waste_records` and observe whether a UI Flow
observing that table emits.

**Links.** [[Offline Synchronization]]

---

## HYP-007 — ISO timestamps compared as strings

**Hypothesis.** `requeueStaleSyncingItems` compares ISO **strings**
(`SyncQueueDao.kt:36`), as does `orderPendingIntoGroups` (`SyncRepositoryImpl.kt:694`).
All writers use `Instant.now().toString()` (`DateUtils.kt:184`), which emits **variable
fractional precision** (`…:30Z` vs `…:30.900000Z`).

⚠️ Why unproven: the practical effect is a sub-second skew against a 5-minute staleness
threshold — almost certainly harmless. Listed because it is a string compare where a numeric
compare is intended.

**To confirm.** Construct two timestamps across a precision boundary and assert the ordering.

**Links.** [[Offline Synchronization]]

---

## HYP-008 — `searchPatients` wildcards

✅ **Refuted 2026-10-18.** The card said *"the caller was not traced"* — it has now been
traced, and the behaviour is intentional.

`PatientDao.kt:36-41` matches `:q` against `name`, `phone`, `patientClinicId`, `address`,
and a `patient_visits` subquery on `vaccineNames`/`receiptNumber`. The wildcards are added
one layer up:

```kotlin
// feature/patient/domain/SearchPatientsUseCase.kt:15-21
operator fun invoke(query: String): Flow<List<Patient>> =
    if (query.isBlank()) patientRepository.allPatients
    else patientRepository.searchPatients("%$query%")
```

Reached from `PatientListViewModel.kt:67` via `flatMapLatest` on a debounced query. A blank
query deliberately returns everything rather than matching `"%%"`. The DAO takes a
fully-formed pattern — this is the intended design, not a gap.

**Links.** [[Patient Management]]

---

## HYP-009 — DAO query on the main thread during login/logout

**Needs runtime.** `AuthViewModel.kt:303` (in `logout`) and `:222` (in `login`) call
`syncQueueDao.getUnacknowledgedCountSync()`. `viewModelScope` defaults to
`Dispatchers.Main.immediate` and neither function wraps in `withContext(Dispatchers.IO)`.

⚠️ Why unproven: the query is `SELECT COUNT(*)` on a small indexed table, so it may complete
within a frame. But it is a synchronous blocking call on the main thread, which StrictMode
would flag.

**To confirm.** Enable StrictMode `detectDiskReads()` and log in/out.

**Links.** [[Architecture Map]] · [[Error Handling]]

---

## Promoting an item

1. Write down the check you will run.
2. Run it. Record the actual output.
3. If it confirms, move the item to [[Quality/Bug Register]] with a **new** ID, cite the
   check, and leave a line here under **Refuted**→**Promoted** pointing at the new ID.
4. If it refutes, record it under **Refuted** with the evidence.

**Never** promote on the strength of "it looks right".

## Related

- [[Quality/Bug Register]] — confirmed defects
- [[Quality/Security Concerns]] — items needing a live database
- [[Testing]] — which checks would prove these
- [[Documentation Maintenance]] — the convention this file enforces
