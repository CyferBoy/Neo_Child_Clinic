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

| ID | Hypothesis | Status | Needs |
|---|---|---|---|
| [[#hyp-001]] HYP-001 | `PatientCache` serves stale data after a vaccination edit | Needs runtime | an invalidation test |
| [[#hyp-002]] HYP-002 | `Result.failure()` cancels appended WorkManager dependents | Needs runtime | WorkManager version semantics |
| [[#hyp-003]] HYP-003 | `_syncState` strands on `SYNCING` after an exception | Needs runtime | injected DAO failure |
| [[#hyp-004]] HYP-004 | `VaccineDetailViewModel` hides rows the card counts | Needs runtime | legacy `vaccineName` data |
| [[#hyp-005]] HYP-005 | `vaccination_items.batchId = ""` triggers NCC-005's rollback path | Needs runtime | legacy rows |
| [[#hyp-006]] HYP-006 | Raw `execSQL` in `markUploaded` may not fire Room invalidation | Needs runtime | Flow observation |
| [[#hyp-007]] HYP-007 | ISO string comparison is wrong at sub-second boundaries | Hypothesis | a boundary test |
| [[#hyp-008]] HYP-008 | `searchPatients` is passed `:q` without wildcards | Hypothesis | caller trace |
| [[#hyp-009]] HYP-009 | `AuthViewModel` runs a DAO query on the main thread | Needs runtime | StrictMode / trace |

## Refuted

| Item | Why refuted |
|---|---|
| Audit #43 — nested scrollable-in-scrollable | Both hits (`WasteScreen.kt:279`, `BorrowedScreen.kt:288`) are `verticalScroll` inside an **`AlertDialog`** text slot, not nested in a `LazyColumn`. Correct Material behaviour. |
| Audit #39 third claim — `SimpleGenderChart` inaccessible | It renders labelled text rows with percentages, not a Canvas. Already accessible. |
| Soft-deleted rows leaking into **statistics** | `expenses` and `finance_transactions` dropped `is_deleted`; `reminders` is the only table that kept it and **nothing writes it**. Every aggregate is clean by construction. See [[Statistics Dashboard]]. |
| `AutoBackupWorker` runs without network | The constraint is conditional on `cloudEnabled` by design (`BackupAutoScheduler.kt:36-37`) — local backups need no network. |
| `BackupDao`'s lack of `@Transaction` is a bug | Atomicity comes from `BackupRestorer.kt:50`'s `database.withTransaction`. Works, but is invisible from the DAO. Recorded as a readability concern, not a defect. |

---

## HYP-001 — `PatientCache` serves stale data after a vaccination edit

**Needs runtime.** `PatientRepositoryImpl.kt:66-68` registers a Room invalidation observer
scoped to **table `patients` only**. Editing a vaccination does not touch that table, so a
cached `Patient` object can retain a stale history for up to the 5-minute TTL.

⚠️ Why unproven: whether the cached `Patient` actually embeds history or vaccination counts
depends on which fields `PatientRepositoryImpl` populates. If it carries only scalar patient
data, the impact is nil.

**To confirm.** Write a test: cache a patient, edit one of their vaccinations, read the cache
before the TTL elapses, assert the embedded history is unchanged.

**Links.** [[Caching]] · [[Patient Management]]

---

## HYP-002 — `Result.failure()` cancels appended WorkManager dependents

**Needs runtime.** `SyncWorker.kt:36` returns `Result.failure()` for an element of an
`APPEND_OR_REPLACE` chain. Per WorkManager semantics a failed parent cancels its dependents,
so every sync scheduled after it is dropped until the next periodic tick (≤15 min) or
`onResume`.

⚠️ Why unproven: the exact semantics vary by WorkManager version, and the code was not run
here. The comment at `SyncWorker.kt:31-34` only reasons about "stops this request retrying",
not about the chain.

**To confirm.** Enqueue two works, fail the first, observe whether the second runs.

**Links.** [[Offline Synchronization]]

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

**Needs runtime.** `DueReminderDao.kt:101-102` selects by delimited match on
`nxt_vaccine_id`, then `VaccineDetailViewModel.kt:91-95` drops any row whose
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
`deductStockFromBatch("")` throws `"Batch not found"` — the NCC-005 rollback path.

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

**Hypothesis.** `PatientDao.kt:36-41` receives `:q` as a bare parameter with no `%` added in
Kotlin, so the pattern is effectively an exact match unless the caller supplies wildcards.

⚠️ Why unproven: **the caller was not traced.** This may be entirely intentional — the
`SearchPatientsUseCase` may build the pattern.

**To confirm.** Read `feature/patient/domain/SearchPatientsUseCase.kt` and
`PatientListViewModel`'s search handler.

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
