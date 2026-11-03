# Test and Rollback Plan

**Status:** proposal only. No test below is claimed to pass unless it is listed in §2 as executed in this pass. The Android build, instrumented tests, live RLS probes and production smoke tests have **not** been run.

**Post-approval note (2026-10-26):** two small JVM tests were added to the working tree for the visit `createdAt` preservation and IST date parsing. They have not been executed: Gradle fails before compilation on this host with `Service 'SystemInfo' is not available`. The Gradle failure is an environment blocker, not a test result.

## 1. Test strategy

| Layer | What it proves | Where |
|---|---|---|
| Static verification | The cited code/schema still contains the named mechanism | `10-findings-verification.md` |
| Disposable PostgreSQL tests | Trigger arithmetic, trigger-count guard, reconciliation view, retention behavior | Local PostgreSQL 18.2 / CI |
| Room tests | Queue uniqueness, delete ordering, merge completeness, reminder keys, finance import | JVM unit tests and instrumented tests |
| Android UI/manual tests | Offline, two-device, background sync, dialog staleness | Device/emulator |
| Staging RLS probes | Role matrix, deactivated user, stolen/expired token | Disposable Supabase project |
| Production smoke | Deployed trigger/policy/index state and data invariants | Read-only SQL + app smoke |

**Rule:** a “Fixed” claim needs evidence against the original reproduction. A code change alone is not a test. A green minimal-schema SQL test is not proof that the full migration chain or live RLS is correct.

## 2. Evidence already executed in this pass

| Test | Environment | Result |
|---|---|---|
| `supabase/tests/stock_trigger_arithmetic.sql` after the final trigger migration | Disposable PostgreSQL 18.2 | `PASS: stock trigger arithmetic` inside a rolled-back transaction |
| `supabase/tests/inventory_reconciliation_test.sql` after the final trigger and view migrations | Disposable PostgreSQL 18.2 | `PASS: inventory reconciliation view` inside a rolled-back transaction |
| Re-apply final stock migration with an extra legacy trigger | Disposable PostgreSQL 18.2, `psql -f` (autocommit) | Failed the guard and left two triggers active. This is a **failure mode demonstration**, not a passing migration test. |
| Android unit/instrumented suite | Not available | Not run; the 310/310 claim is not reproduced |
| RLS role probes | Not available | Not run; `rls_role_test.sql` still has placeholders |
| Live stock reconciliation | Not available | Not run |

## 3. Test matrix by finding

| Test ID | Finding | Scenario | Expected | Evidence |
|---|---|---|---|---|
| **T-01** | DBA-01 | Insert purchase, retry same row, edit purchase, retry vaccination, move batch, delete, negative guard | Stock changes exactly once; negative rejected | `supabase/tests/stock_trigger_arithmetic.sql` |
| **T-02** | DBA-01 / VER-01 | Apply final migration twice; apply with one stray trigger; inspect trigger count | Transaction rolls back or exactly one trigger remains; no partial state | New SQL test; local failure reproduction recorded |
| **T-03** | DBA-01 | Add drifted batch, run repair pattern, verify `inventory_reconciliation` | Recorded and ledger quantities agree; one adjustment row | New SQL test on disposable DB |
| **T-04** | DBA-02 | Create visit + ledger; delete visit | Visit gone, ledger retained, `visit_id` null or documented | New SQL/transaction test; live FK required |
| **T-05** | DBA-03 | Pending local finance edit + remote row needing heal | Local amount/date unchanged; no UPDATE enqueued | Extend `FinanceRefreshImportTest` |
| **T-06** | DBA-04 | Device zones UTC/America/New_York; visit `9 May 2026` IST | Finance date `2026-05-09`; totals match | New unit test around `resolveTransactionDate` |
| **T-07** | DBA-05 / DBA-23 | Pulled reminder (`serverId != id`), delete patient; child delete fails mid-group | Remote reminder delete uses `serverId`; parent waits/retries; no orphan | Room/DAO + sync ordering test |
| **T-08** | DBA-08 | JWT probes for admin/doctor/receptionist/nurse/inventory_manager; deactivated user; missing role | Matrix allows/denies exactly as approved; deactivated fails | Replace placeholders in `supabase/tests/rls_role_test.sql`; disposable project |
| **T-09** | DBA-09 / DBA-17 | Two coroutines enqueue same entity/operation; concurrent save | One PENDING row; one upload; no full queue scan | Room concurrency test + `EXPLAIN QUERY PLAN` |
| **T-10** | DBA-10 | Return 2+3 on quantity 5; return 3+3; stale dialog | First marks returned; second over-return rejected; status follows fresh values | Repository/unit test |
| **T-11** | DBA-11 | Two simultaneous refreshes of the same table | One network call; both waiters see result | Fake repository count |
| **T-12** | DBA-12 | Remote personal reminder for deleted patient | Refresh completes; orphan skipped/logged; valid row imports | Room/integration test |
| **T-13** | DBA-13 | Create visit, edit after delay; inspect local and upload payload | `createdAt` unchanged | Room/unit test |
| **T-14** | DBA-14 | Default zone UTC-8; dashboard after IST midnight | Clinic day is IST; stock/finance filters match | Unit test with fixed clock |
| **T-15** | DBA-15 | Offline audit screen; fresh install | Explicit online-only state or cached rows; never a silent empty list | UI/manual |
| **T-16** | DBA-16 | Audit insert fails inside a business transaction | Business row rolls back; no audit row | Room/transaction test |
| **T-17** | DBA-18 | Fake remote-newer ledger row; descriptor has no downloader | Queue item is not silently deleted; conflict visible or downloaded | Uploader unit test |
| **T-18** | DBA-19 | Same event, two due dates; two devices | One row per event; due date updates; server accepts | Room test + SQL unique violation |
| **T-19** | DBA-20 / VER-04 | Concurrent insert/delete during full pull; prune | No local row deleted on an incomplete snapshot; next refresh converges | Pagination/RemotePrune integration test |
| **T-20** | DBA-21 | `EXPLAIN QUERY PLAN` for finance ordering | Uses index; no full scan/temp sort | SQLite plan test |
| **T-21** | DBA-22 | Two-character search; 20k patients | No full scan per keystroke; debounce/min length | SQLite benchmark |
| **T-22** | DBA-25 | Two devices offline create `NEO-1000`; forced suffix collision | Different IDs; collision retried or surfaced once | Two-device manual + unit test |
| **T-23** | DBA-26 | Merge fully populated duplicate | All child rows moved; none deleted; totals unchanged; duplicate DELETE last | Instrumented/integration test |
| **T-24** | VER-03 | Count/export audit rows older than one year; run prune on copy | Exact rows identified; archive restore works | Disposable DB + backup restore |
| **T-25** | VER-06 | Preflight `DROP FUNCTION get_my_role() CASCADE` dependents | No unexpected view/function dropped; policies restored on rollback | Staging preflight |

## 4. Disposable PostgreSQL test commands

These are the commands used for the executed tests. Adapt the data directory and port; never point them at production.

```bash
BASE=/data/data/com.termux/files/usr/tmp/opencode/ncc_pg_verify
initdb -D "$BASE/data" -U postgres --no-locale -E UTF8
pg_ctl -D "$BASE/data" -o "-p 55432 -k $BASE/sock -c listen_addresses=''" -l "$BASE/postgres.log" start
createdb -h "$BASE/sock" -p 55432 -U postgres ncc_test
psql -h "$BASE/sock" -p 55432 -U postgres -d ncc_test -v ON_ERROR_STOP=1 \
  -f supabase/migrations/20261022_fix_stock_trigger_update_arithmetic.sql
psql -h "$BASE/sock" -p 55432 -U postgres -d ncc_test -v ON_ERROR_STOP=1 \
  -f supabase/tests/stock_trigger_arithmetic.sql
```

The stock and view tests both wrap their assertions in a `DO` block and roll back. That is why the terminal shows `ROLLBACK` after `PASS`.

### 4.1 Trigger-count guard test (must fail safely)

```sql
-- On a disposable DB, after the final migration:
create trigger legacy_stock_trigger
after insert or update or delete on public.inventory_transactions
for each row execute function public.update_batch_stock();

-- Then run the final migration through the intended transactional runner.
-- Expected: the transaction aborts and leaves exactly the pre-test trigger set.
-- Current file with psql -f (autocommit) is expected to leave two triggers; that is VER-01.
```

Do not “fix” this test by adding `legacy_stock_trigger` to production. It reproduces the failure mode.

### 4.2 RLS probe shape

`supabase/tests/rls_role_test.sql:1-80` is a manual template, not an automated test. Replace every `<UUID>`/`<ROLE>` placeholder with a disposable-project account, then run each block. Minimum probes:

```sql
set request.jwt.claims = '{"sub":"<RECEPTIONIST-UUID>","role":"authenticated","app_metadata":{"role":"receptionist"}}';
set role authenticated;
delete from public.patients where id = '<TEST-PATIENT>';
-- Expected: permission denied if the owner matrix restricts delete to admin.
reset role;
reset request.jwt.claims;
```

Also test: doctor update on a patient; nurse update on finance; inventory_manager stock adjustment; admin delete; deactivated profile with a live JWT; missing `app_metadata.role`; `service_role` unaffected.

### 4.3 Retention test

The CI test uses valid ISO timestamps and asserts one row remains. Add a copy test with:

- a row older than the retention window and a recent row (expected: old removed, recent stays);
- a malformed timestamp matching the regex (`2026-99-99T00:00:00Z`) (expected: prune aborts safely, not a partial delete);
- a timestamp with a timezone offset;
- a count/export before deletion.

Do not run the deletion on production as a test.

## 5. Room/Android tests to add

| File area | Test |
|---|---|
| `FinanceRefreshImportTest` | Pending local finance row survives a heal pull; IST date; no enqueue from pull |
| `SyncOrderingTest` / new queue test | Patient delete group order; child before parent; idempotent retry |
| `RemotePruneTest` / `PaginationTest` | Incomplete snapshot skips prune; count mismatch; next refresh converges |
| `BorrowedViewModel` / repository test | Stale remaining quantity; over-return; fully returned |
| `MergePatientsUseCase` test | Every child table populated; move counts; no orphan; final delete last |
| Reminder DAO test | Four-column key; due-date update; duplicate rejection |
| Vaccination edit test | `createdAt` preserved |
| Audit logger test | `recordLog` rolls back; `log` not used inside transaction |
| Sync uploader test | No-downloader conflict does not delete queue item |
| Finance DAO test | `EXPLAIN QUERY PLAN` uses the effective-date index |

The repository currently has `FinanceRefreshImportTest.kt`, `RemotePruneTest.kt`, `PaginationTest.kt`, `SyncOrderingTest.kt`, `BorrowBatchSelectionTest.kt` and others. It has no `*ViewModelTest.kt` files (canonical register §Evidence, `docs/Knowledge_Base/Quality/Bug Register.md:36-37`), so do not claim UI coverage from repository tests.

## 6. Manual/two-device scenarios

1. **Stock retry:** Device A edits a batch; Device B retries the same ledger upload; stock is 10, not 0/20. Verify with the reconciliation view.
2. **Finance edit/delete:** A edits an amount offline; B pulls; A comes online; the edit is not overwritten. Delete a finance row on B while A is in flight; it does not reappear.
3. **Patient delete:** Pull a reminder on B, delete the patient on B, then refresh A. No remote orphan reminder; no stuck queue group.
4. **Merge:** Duplicate has a consultation with income and a note; merge; master keeps both; no remote DELETE for the children.
5. **Borrow return:** Two partial returns; status flips exactly when the sum reaches the borrowed quantity.
6. **Offline stock history:** Open Stock History with no network; cached history or explicit online-only message; never a blank success.
7. **Timezone:** Set device to UTC-8; after IST midnight, dashboard/stock/finance use the IST day.
8. **RLS:** Log in as each role; attempt the approved and denied operations; verify the app shows a permission error, not a crash.
9. **Audit retention:** On staging, export old rows, run prune, restore archive. Never on production first.

## 7. Rollback matrix

| Change | Rollback | Data risk |
|---|---|---|
| Stock trigger migration | If applied in a transaction: automatic. If not: restore the captured function definition and exactly one trigger. Do not re-apply an older stock migration. | No row data changed by the trigger migration itself |
| Stock repair | Delete only the recorded `adjustment_id` rows, then restore `old_remaining` from `_stock_repair_20261026`; export first | High; the adjustment rows are the only evidence |
| RLS migration | Restore saved `pg_policies` definitions; keep the new helper if harmless or drop it after policies are restored | No table data |
| Unique index / Room v34 | Drop the new index only if the app can tolerate duplicates; do not downgrade Room without a tested path | Queue rows; export duplicates |
| Reminder key migration | Restore the old index only after restoring/deduplicating rows; otherwise leave the new index | Reminder rows |
| Finance code | Revert the app build; no server schema change. Old build may reintroduce overwrite, so ship forward if possible | Finance edits |
| Merge code | No rollback after the duplicate patient is deleted; restore from backup or re-link existing rows | Clinical/financial history |
| Retention migration | `cron.unschedule('prune-audit-logs')`; restore exported rows | Irreversible without export |
| App release | Keep the previous APK and a Room backup; server RLS may reject the old build | Session/feature only if coordinated |

**Never** “roll back” the stock trigger by applying `20261017` or `20261021001000`; those are known-buggy/older bodies. Fix forward with the reviewed function and trigger.

## 8. Production smoke checks after deployment

Run read-only:

```sql
-- Exactly one stock trigger.
select count(*) from pg_trigger
where tgrelid = 'public.inventory_transactions'::regclass
  and not tgisinternal
  and tgfoid = 'public.update_batch_stock()'::regprocedure;

-- Trigger body is the final reviewed hash.
select md5(pg_get_functiondef('public.update_batch_stock()'::regprocedure));

-- Drift count; expected 0 for repaired batches, known/quarantined otherwise.
select count(*) from public.inventory_reconciliation where drift <> 0;

-- RLS policies for the security-sensitive tables.
select tablename, cmd, policyname from pg_policies
where schemaname = 'public'
  and tablename in ('patients','patient_visits','finance_transactions','inventory_transactions','vaccine_batches')
order by tablename, cmd, policyname;

-- Queue duplicates are local; export from the app.
```

App smoke:

- login as each role;
- create/edit a stock movement and retry;
- edit/delete finance;
- delete a patient with a reminder;
- merge a test duplicate;
- partial return;
- offline stock/audit screens;
- check sync screen for FAILED items and the error log for `23503`, `23505`, `42501`, `PGRST`.

## 9. Stop conditions

Stop the deployment and roll back/forward immediately if any of these occur:

1. Stock trigger count is not exactly 1, or the function hash differs from the reviewed body.
2. A repaired batch still shows drift, or `remaining_quantity` is negative.
3. RLS denies an operation the owner approved, or allows one it denied.
4. A stock repair target is missing a physical count or has a negative ledger sum.
5. The backup restore has not been verified.
6. The queue has duplicate PENDING rows after the Room v34 migration.
7. Sync errors show `23503` on visit delete, `23505` on patient/reminder upload, or `42501` on a core workflow.
8. The audit retention job runs without an approved export.
9. The release note and the applied migration ledger disagree.

## 10. What is still not tested

- The full 29-migration chain against a real Supabase project.
- Live RLS with real JWT claims and real roles.
- The Android build, 310/310 tests, Room migrations, and two-device sync.
- Historical stock repair against real data.
- The audit-retention policy and archive restore.
- The post-audit 230-path drift outside the named findings.

These gaps are the reason this document stops at a plan. Approval must be explicit, and the first approved action is the read-only preflight plus backup, not a production write.
