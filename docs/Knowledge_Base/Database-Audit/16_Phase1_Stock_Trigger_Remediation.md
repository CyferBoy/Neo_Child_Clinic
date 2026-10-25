# Phase 1 — Stock Trigger and Ledger Insert-Only

- **Date:** 2026-10-22
- **Base commit:** `ca4bed406bfd246ad83878fff9a4e178ebc5670f` (`main`)
- **Scope:** DBA-01 (UPDATE arithmetic and duplicate stock trigger) plus the Phase 1 insert-only ledger CREATE upload.
- **Status:** SQL fix and regression test verified on a local PostgreSQL 18.2 instance. The `SyncUploader` change is prepared but **not compiled or tested** (no Android SDK). **Nothing deployed. No production data accessed or repaired.**
- **Approval:** user said “go ahead” after the audit. Phase 0 live verification could not run because this environment has no Supabase credentials, connection string, CLI, or backup. This document records that limitation rather than treating the gate as passed.

## 1. Defect recap

`20261017_stock_trigger_duplicate_and_update_arithmetic.sql` replaced the duplicate-trigger problem but left this UPDATE branch:

```sql
v_delta := new.quantity - old.quantity;
if v_delta <> old.quantity then
    remaining := remaining - old.quantity;   -- undo
end if;
remaining := remaining + v_delta;            -- apply
```

The undo condition is false only when `new.quantity = 2 * old.quantity`. For a retry (`new = old`) it is true, so the old movement is undone and the zero delta is then applied:

| Scenario | Before | Old result | Correct |
| --- | ---: | ---: | ---: |
| Purchase `+10`, retry same row | 10 | **0** | 10 |
| Vaccination `-2`, retry same row | 8 | **10** | 8 |
| Move `-2` from A to B as `-4` | A 48, B 50 | A 48, B **48** | A 50, B 46 |

The old function can also drive the computed value negative and roll back a legitimate edit (for example, `+10 -> +4` at 10 stock: undo to 0, apply -6, guard raises), even though the correct result is 4. Historical repair is a separate decision; this phase only stops new corruption.

## 2. Changes

| File | Purpose |
| --- | --- |
| `supabase/migrations/20261022_fix_stock_trigger_update_arithmetic.sql` | Drops both known stock-trigger names, replaces the function with a single-application version, creates exactly one trigger, and raises if another trigger calling the function survives. |
| `supabase/tests/stock_trigger_arithmetic.sql` | Rolled-back transaction with 17 assertions: trigger count, purchase insert, purchase retry, insert-only retry, purchase edit, vaccination insert, vaccination retry, vaccination edit, move undo, move apply, move retry, delete, negative guard, purchase delete, positive purchase/edit/delete. |
| `app/src/main/java/com/neochildclinic/core/sync/SyncUploader.kt` | Sends ledger CREATE uploads as `INSERT ... ON CONFLICT DO NOTHING`; UPDATE operations keep the normal upsert. |

No Gradle file, RLS policy, or existing migration was modified. The app change is additive and not compiled here.

### Corrected semantics

| Operation | Effect |
| --- | --- |
| `INSERT` | Apply `new.quantity` to `new.batch_id`. |
| `DELETE` | Apply `-old.quantity` to `old.batch_id`. |
| `UPDATE`, same batch | Apply `new.quantity - old.quantity`. Retry is a no-op. |
| `UPDATE`, moved batch | Undo `old.quantity` on the old batch, then apply `new.quantity` on the new batch. |

The destination row is locked with `SELECT ... FOR UPDATE` before the guard and update, so two concurrent movements cannot both pass the non-negative check. A missing destination batch raises instead of silently doing nothing.

### Ledger CREATE is insert-only

`SyncUploader` used a plain PostgREST upsert for every ledger operation. A CREATE retry therefore became an UPDATE on the existing row and fired the trigger's UPDATE branch. The uploader now passes `insertOnly = item.operation == CREATE` for `InventoryTransactionEntity`, which sets PostgREST's `ignoreDuplicates` and compiles to `INSERT ... ON CONFLICT DO NOTHING`. The transaction is already committed server-side on a lost-response retry, so doing nothing is the correct idempotent result. UPDATE operations keep the normal upsert.

This is defence in depth: the trigger fix makes an identical UPDATE a no-op, while insert-only prevents the UPDATE from being attempted at all. The SQL test includes an `ON CONFLICT DO NOTHING` retry assertion.

## 3. Verification actually executed

Environment: Termux aarch64, PostgreSQL 18.2 (installed locally for this check), disposable cluster on port 55432. Minimal `public.vaccine_batches` and `public.inventory_transactions` tables matching the relevant columns in the schema catalog. This is **not** Supabase and does not include RLS, grants, or the full migration chain. It validates the trigger and the `ON CONFLICT DO NOTHING` SQL path, not the Kotlin uploader.

### 3.1 Fixed migration passes

```bash
psql ... -f min_schema.sql
psql ... -f supabase/migrations/20261022_fix_stock_trigger_update_arithmetic.sql
psql ... -f supabase/tests/stock_trigger_arithmetic.sql
```

Result:

```text
CREATE FUNCTION
CREATE TRIGGER
DO
BEGIN
NOTICE:  PASS: stock trigger arithmetic
DO
ROLLBACK
exit=0
```

### 3.2 Old function fails the same test (negative control)

The test was run against the exact `20261017` function body. It failed before any later assertion:

```text
ERROR:  purchase retry: expected 10, got 0
CONTEXT:  PL/pgSQL function inline_code_block line 30 at RAISE
exit=3
```

This proves the test detects the defect rather than merely exercising the fixed code.

### 3.3 Upgrade with both trigger names passes

A database with both `inventory_transactions_update_batch_stock` and the legacy `tr_update_batch_stock` was migrated with the new file. Both were dropped, exactly one was recreated, and the test passed:

```text
DROP TRIGGER
DROP TRIGGER
CREATE FUNCTION
CREATE TRIGGER
DO
BEGIN
NOTICE:  PASS: stock trigger arithmetic
DO
ROLLBACK
```

Post-check: `select count(*) ...` returned `1`.

## 4. What was not done

- No live Supabase query, deployment, migration-ledger check, backup, or restore test. Phase 0 remains open.
- No historical `remaining_quantity` repair, reconciliation, or drift quantification. The ledger may be missing rows rejected by the old guard, so a blind rebuild is unsafe.
- No Android/Gradle/instrumentation test. This environment has no Android SDK or emulator. The `SyncUploader` change is therefore uncompiled; run `./gradlew :app:compileDebugKotlin` and the sync tests before merging.
- No RLS/grant test for the trigger function. The `SELECT ... FOR UPDATE` requires the same table privileges the trigger already needed for its update; this should be confirmed on staging.
- No correction-row redesign or reconciliation view yet.

## 5. Deployment runbook (not executed)

1. **Phase 0 first:** run the read-only queries in `15_Unknowns_And_Live_DB_Gaps.md`, especially U-01 (installed function/trigger count) and U-05 (stock drift). Record the migration ledger.
2. **Backup:** take a restorable snapshot and perform a restore test. Do not proceed without it.
3. **Staging:** apply the migration to a disposable/staging Supabase project; run `supabase/tests/stock_trigger_arithmetic.sql`; confirm exactly one trigger and the new function body.
4. **Production window:** apply during low traffic. The migration is transactional and short, but it takes an exclusive lock while dropping/recreating the trigger.
5. **Post-check:** run the trigger-count query, then re-run the reconciliation query. A non-zero drift is expected until Phase 2; do not “fix” it automatically.
6. **Rollback:** restore the database snapshot, or restore the `20261017` function and trigger and then re-apply the corrected version. Rolling back to the old function reintroduces the defect; prefer forward-fix.

## 6. Remaining Phase 1 work

- Decide and implement correction semantics (compensating rows preferred over editing posted ledger rows).
- Schedule an alarm on `public.inventory_reconciliation` and quantify historical drift after Phase 0; see `17_Inventory_Reconciliation_View.md`.
- Run the SQL tests on a disposable Supabase project, not only local PostgreSQL.
- Compile and test the `SyncUploader` change on a machine with the Android SDK.

## 7. Files changed

```text
A  supabase/migrations/20261022_fix_stock_trigger_update_arithmetic.sql
A  supabase/tests/stock_trigger_arithmetic.sql
M  app/src/main/java/com/neochildclinic/core/sync/SyncUploader.kt
M  docs/Knowledge_Base/Database-Audit/01_Executive_Summary.md
M  docs/Knowledge_Base/Database-Audit/10_Data_Integrity_And_Business_Logic.md
M  docs/Knowledge_Base/Database-Audit/11_Test_Coverage_And_Plan.md
M  docs/Knowledge_Base/Database-Audit/12_Prioritized_Issue_Register.md
M  docs/Knowledge_Base/Database-Audit/14_Phased_Implementation_Roadmap.md
M  docs/Knowledge_Base/Database-Audit/15_Unknowns_And_Live_DB_Gaps.md
A  docs/Knowledge_Base/Database-Audit/16_Phase1_Stock_Trigger_Remediation.md
```

All changes are uncommitted. The audit directory remains untracked; the two SQL files are new tracked-path candidates, and `SyncUploader.kt` is a tracked modification.
