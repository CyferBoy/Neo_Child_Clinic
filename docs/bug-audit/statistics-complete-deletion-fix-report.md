# Statistics — Complete Deletion Fix Report (Final)

Date: 2026-10-24 · Auditor: exhaustive repository-wide audit (12 phases)
Baseline: commit `8b3c415` ("fix(statistics): exclude cancelled and dismissed visits…"), dirty tree preserved.

## 1. Source files and data paths identified

**25 inventory entries** covering **~40 Kotlin files** + 4 SQL migrations:
3 presentation clusters (StatisticsScreen/VM + 4 tabs + 6 drill-down/report screens),
8 domain files, 9 DAOs, 6 repository implementations, 2 sync files,
`supabase/migrations/` (soft-delete columns, RLS, business tables), and 5 test files.
Full listing: `statistics-complete-source-inventory.md`; per-metric paths: `statistics-deletion-audit-matrix.md`;
relationships: `statistics-dependency-map.md`.

## 2. Coverage and remaining gaps

**The read-side deleted-record defect ("deleted records still contribute to Statistics")
does not reproduce against the current tree** — with one exception found only after a
user report: the **write/pull race** in NCC-044 (§3), where a *freshly deleted* finance row
was re-imported by the next pull. The historical root cause of the original report was real
under the pre-migration-32 schema (old-era DAOs soft-deleted rows and HEAD DAOs no longer
filter them), and migration 31→32 (`AppDatabase.kt:385-415`) hard-deletes every soft-deleted
row on **18 tables** before dropping the column. Today:

- Locally, only `reminders` is soft-deleted, and **every** `DueReminderDao` read filters
  `is_deleted = 0` in SQL (instrumented coverage exists: `ReminderSoftDeleteFilterTest`).
- Every remote pull path filters `eq("is_deleted", false)` (verified at 8 call sites).
- Deleted visits cascade-delete their items, reminders and income rows (all enqueued as
  real remote DELETEs — verified in visit, consultation, patient and expense flows).
- Gap now closed: finance's pull guard had a pending-DELETE hole (NCC-044, fixed).

Gaps (documented, not defects in Statistics): RLS does not filter soft-deleted business rows
for authenticated clients (client-side filters carry the whole load);
`SyncUploader.fetchRemoteConflictData:422` reads a business table without an `is_deleted`
filter; `RemoteReminder.toLocal()` hard-codes `isDeleted = false`.

## 3. Confirmed root causes

**NCC-044 (confirmed, fixed — user-reported after this audit's first pass: "deleted items still
shows in the stats finance and monthly stats").** `refreshTransactions`' import guard was
`local == null || (local.isSynced && id !in pendingFinanceIds)`. After an income row was
hard-deleted locally (visit cascade `VaccinationRepositoryImpl.kt:488-500`, consultation cascade
`ConsultationRepositoryImpl.kt:170-182`) with its `FINANCE` DELETE still queued, any pull that
beat the SyncWorker upload saw `local == null`, short-circuited the pending check and re-imported
the row as `isSynced=true` — which then also made `RemotePrune` skip it (pending wins on both
ends until the upload lands plus one more pull). Finance was the **only** one of ~10 pull paths
that gated the insert on the pending set conditionally (patients `:135`, visits `:126`,
consultations `:211`, expenses `:125`, todos `:141`, profiles `:75`, reminders `:493` all gate
unconditionally). Both reported screens read that one query (`FinanceDao.getAllTransactions()`),
which is exactly why the symptom was scoped to Finance + Monthly Finance Details.
Fix: extracted `financeImportAllowed(...)` in `FinanceRepositoryImpl.kt` — pending set now gates
both branches (house contract `todoUpsertsForRefresh`). Suspected, not fixed: legacy income rows
with blank/null `visitId` would escape the visit cascade; current code regenerates the UUID
(`ClinicalVaccinationService.kt:199`), so no *new* row can look like that.

**NCC-043 (confirmed, fixed — first code change of this audit).** Commit `8b3c415` wrapped the
Overview visit-type input in `StatisticsUtils.filterValidVaccinations(...)`. Consultation visits
are persisted `ReminderStatus.ACTIVE` by design (`Visit.kt:43`) and have no completion state, so
`isCountedVaccination` (`StatisticsUtils.kt:43-44`) excluded **100% of them**:

- "Consulted Patients" and "Total Consultation" cards → permanently `0`.
- Consultation series in the Patient Activity chart → permanently `0`.
- `computeEffectiveRegistrationDates` received only valid visits in Overview/FullReport while
  `PatientsTab.kt:36-38` passed raw visits → a consultation-first patient bucketed into a
  different month per tab.
- Full Report consultation series (monthly + daily) → permanently `0`.

This broke the helper's own documented contract and the original design of `a4937b0`, which fed
raw visits. The previous fix over-corrected: it gated *visit-type* metrics that must never be
status-gated.

**Deliberately NOT changed** (correct as-is): administered doses, COGS, vaccination counts and
patient counts remain status-gated — a pending vaccination visit must not count as administered.

## 4. Changed files

| File | Change |
|---|---|
| `feature/statistics/domain/StatisticsUtils.kt` | Added `visitTypeStatsForFilter(visits, filterMode, fyQuarter, selectedMonth)` — applies the **date filter only**, never the status gate; documents the contract with the NCC-043 reference. |
| `feature/statistics/presentation/OverviewTab.kt` | Visit-type cards (`:81-86`) now call the new helper; effective-reg-dates (`:50-52`) and the consultation chart series (`:108`) now use raw visits. Vaccination/dose/finance metrics untouched. |
| `feature/statistics/presentation/FullReportViewModel.kt` | `computeMonthly`/`computeDaily` thread a raw `allVisits` parameter for the consultation series and effective-reg-dates. |
| `feature/statistics/domain/ClinicStatsManager.kt` | Inline duplicate of the status rule (`:113`) replaced by `StatisticsUtils::isCountedVaccination` — removes drift risk. |
| `feature/statistics/presentation/VaccinationsTab.kt` | Two comments that claimed doses were "never gated on the parent visit's status" corrected to match the code. |
| `app/src/test/.../VisitTypeStatsTest.kt` | Two regression tests (see §5). |
| `feature/finance/data/FinanceRepositoryImpl.kt` | NCC-044: guard extracted to `financeImportAllowed(...)`; pending-DELETE set now blocks re-import when `local == null`. Heal-override branch unchanged. |
| `app/src/test/.../finance/data/FinanceRefreshImportTest.kt` | 5 tests pinning the import guard, incl. the exact defect (see §5). |

No schema, SQL, RLS, production data, or unrelated business rule was modified.

## 5. Tests

| Command | Result |
|---|---|
| `./gradlew app:testDebugUnitTest --tests 'com.neochildclinic.feature.statistics.*'` | BUILD SUCCESSFUL (37 tests, 0 failures) |
| `./gradlew app:testDebugUnitTest` | BUILD SUCCESSFUL — **317 tests, 0 failures, 0 errors** (310 baseline + 2 NCC-043 + 5 NCC-044) |
| `./gradlew app:assembleDebug` | BUILD SUCCESSFUL (1m 20s, after NCC-044) |
| `./gradlew app:lintDebug` | see §7 |

New tests (synthetic data, pure JVM):
1. `visitTypeStatsForFilter counts ACTIVE consultation visits instead of filtering them out` —
   asserts the fixed rule counts the consultation **and** asserts the pre-fix composition
   `visitTypeStats(filterValidVaccinations(...))` returned `0`, so the test provably bites the
   original defect.
2. `visitTypeStatsForFilter applies the date window before counting` — Overall, FY, FY+quarter
   windows behave.

Note: during development this second assertion initially failed against my own wrong expectation
(1 vs 2) — `visitTypeStats` counts *visits by type* regardless of status; corrected to match the
documented contract, not to make the test pass silently.

## 6. Cache and synchronization verification

No aggregate cache exists; metrics recompute from Room flows on every emission. Realtime carries
no row payload and only triggers the `is_deleted`-filtered refresh paths. `RemotePrune` hard-deletes
locally any id no longer returned by a filtered pull, so a remotely deleted row cannot survive.
`StatisticsViewModel`'s `stateIn` holds UI state but recomputes on upstream emission, so a
deletion propagates on the next emission.

**NCC-044 specifically:** the delete→pull race is closed at the insert boundary — a row with a
pending queue entry (most importantly an in-flight DELETE) is no longer re-imported, so
`RemotePrune`'s pending-wins rule can no longer shield a resurrected row. Delete ordering on
upload is group-ordered child-first (`orderPendingIntoGroups`, `SyncRepositoryImpl.kt:676-697`),
and `finance_transactions` has an RLS DELETE policy (`20260929000000_security_baseline.sql:40-67`)
— remote deletes succeed, so the queue drains and the row stays gone.

## 7. Build status

| Command | Result |
|---|---|
| `./gradlew app:lintDebug` | BUILD SUCCESSFUL (7m57s) — 62 findings: 0 errors, 59 warnings, 3 notes; **none in the six changed files** (all pre-existing elsewhere in the app) |
| `python3 scripts/validate_docs.py --changed` | `OK: 0 error(s), 0 warning(s)` (4 notes, 202 citations) |

## 8. Remaining unverified scenarios

- **On-device confirmation of NCC-044**: this environment has no device/emulator against the
  user's data, so the fix is verified by unit test + code inspection only — the reporter must
  delete income, pull to refresh Statistics, and confirm it stays gone.
- Legacy income rows with blank/null `visitId` (suspected, **not fixed**): they escape the visit
  cascade (`WHERE visitId = :id` + the `isNotBlank()` guard in `ConsultationRepositoryImpl`).
  Current code regenerates the UUID, so only pre-existing data could be affected; needs a data
  check (`SELECT id FROM finance_transactions WHERE visit_id IS NULL OR visit_id = ''`) before
  anyone calls it a defect.
- Live Supabase RLS / realtime behavior under load (unchanged scope).
- `VaccineDao.getVaccinationCountForVaccine:118` counts pending visits (suspected overcount;
  not a deletion leak; **recorded, not fixed**).
- `SyncUploader.fetchRemoteConflictData:422` missing `is_deleted` filter (fails safe today —
  the projection cannot decode; **recorded, not fixed**).
- Finance drill-down date-resolution and expense-scope mismatch vs FinanceTab; Full Report net
  profit not completeness-gated; milestone card/list timezone drift — metric-integrity items
  outside the deletion mandate; **recorded, not fixed**.

## 9. Conclusion

**PARTIALLY VERIFIED — pending on-device confirmation of NCC-044.**
Both confirmed defects are fixed, regression-tested, built and lint-clean:

1. The original read-side deleted-record defect does not exist in the current tree at any
   layer (local hard delete, SQL `is_deleted = 0`, remote filtered pulls, cascades,
   pre-aggregate filtering everywhere).
2. NCC-043 — the previous statistics fix had gated visit-type metrics on
   `filterValidVaccinations`, making consultation metrics permanently `0` (fixed, tested).
3. NCC-044 — the user-reported finance symptom: `refreshTransactions` re-imported rows deleted
   locally while their remote DELETE was in flight (fixed, 5 unit tests, 317/317 green).

Not yet VERIFIED FIXED only because no device/emulator with the reporter's data was available
to reproduce the original symptom and confirm its absence; the legacy blank-`visitId` data
scenario also needs a one-off SQL check. All other metrics: verified clean.
