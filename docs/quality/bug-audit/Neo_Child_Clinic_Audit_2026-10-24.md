# Neo Child Clinic — Uploaded ZIP Audit

**Artifact reviewed:** `Neo_Child_Clinic-main(2).zip`  
**Archive root:** `Neo_Child_Clinic-main/`  
**Audit date:** 2026-10-24  
**Scope:** Existing audit/documentation review, targeted source inspection of Statistics (All), status/deletion handling, inventory stock-trigger migration, test/build verification attempt, and project-wide bug-register consistency.  
**Safety:** No application source, SQL, database, or project files were modified during this review.

## Executive summary

The archive contains substantial prior audit documentation and a verified Statistics regression fix, but the repository's own status documents are not consistent with each other. The current source applies status filtering to the affected Statistics paths, but the filter is a lifecycle-status filter—not a general soft-delete detector. There is also a business-rule contradiction around whether `ACTIVE` visits should contribute to visit counts.

A separate high-impact SQL concern exists in the latest stock-trigger migration: an UPDATE that changes an inventory transaction's `batch_id` is not reconciled across both batches. Same-batch quantity updates are handled by the latest function, but a batch transfer is not.

The project documentation claims 310/310 tests and a clean debug build. I could not independently reproduce those results in this environment because the Gradle wrapper needed to download Gradle 9.7.1 and the environment could not resolve `services.gradle.org`. The remote Supabase migration state, live RLS policies, and live trigger installation are also not verifiable from this ZIP.

**Release conclusion from this independent review: CONDITIONALLY READY at best, not independently verified for production.** Do not deploy the pending migrations or treat the current reports as a complete, reconciled bug register until the outstanding items below are addressed.

---

## 1. Statistics (All): current source review

### 1.1 The reported status-filter fix is present

- `app/src/main/java/com/neochildclinic/feature/statistics/domain/StatisticsUtils.kt:43-47` defines `isCountedVaccination()` and `filterValidVaccinations()`.
- `OverviewTab.kt:58-60` filters the collection before current-period vaccination aggregation.
- `OverviewTab.kt:73-75` filters before previous-period aggregation.
- `OverviewTab.kt:80-86` filters before `visitTypeStats()` for current and previous periods.
- `VaccinationsTab.kt` must be considered alongside its current source and the regression test; the audit report claims it applies the same helper to administered-dose aggregation.
- `StatisticsRegressionTest.kt` tests one completed, one active, and one dismissed example, and asserts that only the completed example survives.

This confirms that the source includes the reported filter application. It does **not** independently prove the app's behavior against Room, cached state, Supabase, or production records.

### 1.2 Important distinction: status filtering is not soft-delete filtering

`StatisticsUtils.filterValidVaccinations()` only checks the `Vaccination.status` and `Vaccination.source` fields. The domain model in `app/src/main/java/com/neochildclinic/domain/model/Vaccination.kt:21-47` has no deletion flag. The corresponding Room entity `app/src/main/java/com/neochildclinic/data/local/entity/VaccinationEntity.kt:40-74` (class `VisitEntity`) also has no `isDeleted`/`is_deleted` property.

Therefore:

- A `DISMISSED` or `ACTIVE` row is excluded by this helper unless `source == EXTERNAL`.
- A row that is truly deleted should normally disappear through the delete/sync/reconciliation path; this helper cannot identify a stale **COMPLETED** row as deleted.
- The phrase “deleted records are excluded” is stronger than what this predicate alone proves. The original symptom must be classified as either invalid lifecycle status or an actual deleted row that survives in a source/cache/sync path.

The deletion workflow in `app/src/main/java/com/neochildclinic/feature/vaccination/data/VaccinationRepositoryImpl.kt:406-509` performs a local hard delete inside a transaction and enqueues a remote DELETE. Its correctness after remote failure depends on queue/retry and subsequent reconciliation, not on `filterValidVaccinations()`.

### 1.3 Business-rule contradiction around ACTIVE visits — needs clarification

`StatisticsUtils.kt:144-149` has a KDoc comment saying `visitTypeStats()` counts every non-deleted visit, and that visits are recorded with `ACTIVE` status and must still count. However, the actual callers in `OverviewTab.kt:80-86` pass the list through `filterValidVaccinations()`, and that helper excludes `ACTIVE` unless `source` is `EXTERNAL`.

The regression test explicitly asserts that `ACTIVE` visits are excluded. The code comment, caller, and test therefore encode conflicting expectations.

This is not safely resolvable from code alone: confirm whether `ACTIVE` means a pending reminder or an actual clinic visit state in this model. If active rows represent real visits, current filtering may undercount consultations/vaccinations; if they represent incomplete/cancelable encounters, the test may be correct and the KDoc is stale.

### 1.4 External-source override has an untested combination

`StatisticsUtils.kt:43-44` returns true when `source.equals("EXTERNAL", true)`, regardless of status. Consequently, a record with `source = EXTERNAL` and `status = DISMISSED` would pass the predicate. Whether that combination is valid or should be excluded is not tested. Add a test after defining the intended precedence between source and status.

### 1.5 Statistics tests are narrower than the report implies

`StatisticsRegressionTest.kt` tests the pure helper's list filtering; it does not itself demonstrate a Room query, Supabase response, cached aggregate, actual Compose tab rendering, or deletion followed by sync. The reported reactive Flow behavior is plausible, but it does not establish remote reconciliation or stale-state correctness by itself.

**Statistics assessment:** The presentation-layer status-filter fix is present. The claim that all forms of deleted records are excluded is not proven by this helper alone. Clarify the ACTIVE lifecycle semantics and test external-source/status combinations before treating the behavior as fully settled.

---

## 2. High-impact SQL finding: stock-trigger batch changes

### Finding NCC-AUDIT-001 — Stock trigger does not correctly handle UPDATE changing `batch_id`

**Severity:** P1 if transaction rows can be updated to a different batch; otherwise conditional/unreachable under an enforced append-only contract.  
**Confidence:** Confirmed SQL behavior; reachability through ordinary app workflows not established from this review.

**Evidence:** `supabase/migrations/20261021001000_stock_trigger_upsert_fix.sql:19-47` selects `OLD.batch_id` for every non-INSERT operation. For UPDATE it computes `remaining_quantity - OLD.quantity + NEW.quantity` against the old batch only (`:44-47`). It never credits `NEW.batch_id` if that differs from `OLD.batch_id`.

For a row moved from batch A to batch B, correct accounting should reverse the old row's effect on A and apply the new row's effect on B. The current function instead applies the quantity delta to A and leaves B unchanged. Its negative-stock precheck at `:25-35` also evaluates the old batch with the quantity delta, not the actual two-batch movement.

The earlier migration `supabase/migrations/20261017_stock_trigger_duplicate_and_update_arithmetic.sql:66-103` explicitly models `v_old_batch` and `v_new_batch`, showing that moving a row to another batch was contemplated. The later `20261021001000` migration replaced that function with a simpler implementation that only operates on the old batch for UPDATE.

**Recommended action:** Before deploying, decide whether inventory transaction rows are strictly immutable/append-only. If they are, enforce that contract on the server and test it. If UPDATEs are supported, correct the function to validate and update both batches atomically, and add PostgreSQL integration tests for same-batch retry, quantity edit, batch reassignment, and negative-stock rejection. Do not rebuild existing stock totals from the ledger blindly; the repository's own prior migration warns that historical ledger rows may be missing.

### Existing same-batch retry behavior

For an UPDATE that keeps the same `batch_id`, the latest function's `remaining_quantity - OLD.quantity + NEW.quantity` arithmetic is equivalent to applying the quantity delta. This is a correction relative to the earlier broken function. The old bug-register claim that all retries remain broken is stale relative to the later migration for this same-batch case, but the batch-reassignment case remains a defect in the current SQL.

---

## 3. Audit documentation and status inconsistencies

### Finding NCC-AUDIT-002 — Workflow completion claims conflict with the bug register

**Severity:** P2 process/release-control risk.  
**Confidence:** Confirmed from included documentation.

- `docs/bug-audit/workflow-status.md` says the workflow is complete and “All 16 findings verified, planned, and reconciled.”
- `docs/bug-audit/16-release-readiness.md` says 16 core findings were addressed and the Android client is 100% verified.
- `docs/Knowledge_Base/Quality/Bug Register.md` contains findings through NCC-042 and explicitly still lists NCC-002 as Open, plus multiple open findings including NCC-025, NCC-027–031, NCC-036, and NCC-039.
- The bug register itself contains dated corrections and stale test counts (e.g. an older 270-test baseline), while the newer bug-audit reports claim 310 tests.
- The latest stock-trigger migration changes the status of the old NCC-002 concern for same-batch updates, but the bug register has not been reconciled to that newer SQL.

**Impact:** A reader cannot reliably tell which issues remain open or which evidence is current. This weakens the release gate even if individual code changes are correct.

**Recommended action:** Reconcile the bug register against current source and migrations. Give every finding one canonical status and link to the latest verification evidence. Do not claim the entire project is complete while the register retains open issues without an explicit accepted/deferred disposition.

### Finding NCC-AUDIT-003 — Production migration state is still unverified

**Severity:** P1 release gate for inventory/security.  
**Confidence:** Confirmed documentation limitation.

`docs/bug-audit/16-release-readiness.md` explicitly says deployment of `20261021000000_drop_legacy_profiles_policy.sql` and `20261021001000_stock_trigger_upsert_fix.sql` remains pending. The final reports also say live Supabase RLS and Realtime resilience were not verified.

**Impact:** Source migrations do not prove that production currently has the intended policies/function body/one trigger. The migration history also documents historical double-trigger corruption and missing ledger rows; applying the migration will not repair historical stock values automatically.

**Recommended action:** In a controlled staging/production deployment process, verify migration history, exact trigger definitions, number of triggers, relevant RLS policies, and stock reconciliation before declaring release-ready. Reconcile physical stock and ledger history before any corrective data update.

---

## 4. Other known unresolved items from the included bug register

The following remain explicitly marked Open in `docs/Knowledge_Base/Quality/Bug Register.md`; this audit did not fully re-prove every one of them against runtime behavior, so treat these as unresolved registered issues requiring validation rather than newly confirmed defects:

- **NCC-025:** `SyncQueueDao` text priority ordering.
- **NCC-027:** low-stock threshold uses `<=` and flags exactly-at-threshold stock.
- **NCC-028:** consultation `visitId` defaults to an empty string despite a foreign-key relationship.
- **NCC-029:** audit DAO documentation claims a composite index that is absent.
- **NCC-030:** audit-count guards query the wrong audit column; the register says the relevant call sites are unused.
- **NCC-031:** Finance DAO sorting expression may prevent use of declared indices.
- **NCC-036:** multiple ViewModel coroutine launch bodies may lack exception handling; the register says the precise unprotected count was not recomputed.
- **NCC-039:** database singleton initialization duplicates locking mechanisms.
- **NCC-002:** the registered wording is stale for same-batch retry arithmetic but must be reconciled with the current migration and batch reassignment case.

The bug register also contains historical findings that are marked fixed or reclassified. Those statuses should be preserved unless current-source evidence disproves them.

---

## 5. Build/test verification performed in this environment

Attempted command:

```text
./gradlew app:testDebugUnitTest --no-daemon
```

**Result:** Could not run. The wrapper attempted to download Gradle 9.7.1 from `services.gradle.org`, but DNS/network access failed with `java.net.UnknownHostException: services.gradle.org`.

Therefore, this review does **not** independently validate the claimed 310/310 test result or `app:assembleDebug`. The archive does contain `StatisticsRegressionTest.kt` and the related source. No test result is being fabricated or inferred from the existing report.

The ZIP does not contain a `.git` directory, so I could not compare this archive against a prior commit or determine which changes were uncommitted before it was exported.

---

## 6. Recommended next actions, in priority order

1. **Resolve the stock-trigger UPDATE contract.** Either enforce append-only inventory ledger rows or support batch reassignment correctly in the SQL function. Add database-level tests before deploying.
2. **Clarify Statistics lifecycle semantics.** Confirm whether `ACTIVE` visits are pending reminders or valid visits. Align `StatisticsUtils.kt` comments, `OverviewTab.kt` callers, and tests. Add an external-source + dismissed-status case.
3. **Separate true deletion from status filtering.** Reproduce deletion using synthetic local/staging data and inspect the raw Room row, remote row, sync queue, cache, and displayed aggregate after deletion. The status helper alone cannot detect a stale completed record.
4. **Reconcile the bug register and workflow tracker.** Update the “16 findings complete” claims against all NCC-001 through NCC-042 entries and current migrations.
5. **Run verification in an environment with Gradle 9.7.1 and dependencies available.** Execute unit tests, debug build, Room migration tests, and relevant instrumentation/database tests.
6. **Verify Supabase staging before production.** Check applied migration versions, actual trigger definition/count, RLS policies, and inventory reconciliation. Do not run a blind stock rebuild.
7. **Revisit open low/medium items** in the bug register and explicitly fix, defer with rationale, or mark not-a-bug with evidence.

## Final conclusion

The Statistics presentation-layer filter is present, but the stronger claim that all deleted records are excluded is not independently proven; the lifecycle semantics are internally contradictory. The stock-trigger migration has a confirmed batch-reassignment accounting defect if UPDATEs can change `batch_id`. Audit documents are inconsistent about project completion, and build/tests could not be re-run in this environment. Production release should remain conditional until these issues and the live Supabase deployment state are reconciled.
