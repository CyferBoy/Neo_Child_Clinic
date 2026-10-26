# Migration and Data Repair Plan

**Status:** proposal only. Nothing in this document has been applied. No production table, policy, trigger, grant or row was changed.

This plan assumes the verification result in `10-findings-verification.md` and the fix ordering in `11-remediation-plan.md`. The existing Supabase schema and migrations remain the compatibility baseline.

## 1. Migration inventory and order

The repository has 29 migration files under `supabase/migrations/`. The applied order in the live project is unknown. Do not infer it from filenames alone; verify the migration ledger and the actual catalog.

| Order group | Files | Why it matters |
|---|---|---|
| Base schema | `20260816_security_hardening.sql` through `20260925_business_soft_delete_columns.sql` | Creates/extends the tables and audit fields. Not re-audited line by line in this pass. |
| Security baseline | `20260929000000_security_baseline.sql`, `20260929001000_write_rate_limit.sql`, `20261001_app_metadata_roles.sql`, `20261013_profiles_privileged_fields_guard.sql`, `20261013120000_role_policy_carveouts.sql` | RLS, JWT role source, privileged-profile trigger, role carve-outs. DBA-08 lives here. |
| Stock/index hardening | `20261016_rls_index_and_stock_trigger_hardening.sql` | Conditional unique indexes and the first stock-trigger replacement. Its “skip with notice” blocks can leave invariants absent. |
| Stock trigger chain | `20261017_stock_trigger_duplicate_and_update_arithmetic.sql`, `20261018_ensure_stock_trigger_installed.sql`, `20261021001000_stock_trigger_upsert_fix.sql`, `20261021002000_fix_stock_trigger_batch_reassignment.sql`, `20261022_fix_stock_trigger_update_arithmetic.sql` | The final `20261022` is the only function body that should remain. Older files must never be re-applied after it. |
| Ledger diagnostic | `20261022_inventory_reconciliation_view.sql` | Read-only drift view; `security_invoker=true`. |
| Legacy policy cleanup | `20261021000000_drop_legacy_profiles_policy.sql` | Uses `DROP FUNCTION … CASCADE`; preflight dependents (VER-06). |
| Audit retention | `20261025_audit_log_retention.sql` | Permanently deletes rows older than one year and schedules pg_cron; owner decision required (VER-03). |

**Filename-order warning.** `20261013120000_role_policy_carveouts.sql` sorts before `20261013_profiles_privileged_fields_guard.sql` because `1` sorts before `_`. Confirm the applied order and dependencies in the ledger; do not assume the dates imply order.

**Final stock function rule.** If the live function body is not the final `20261022` body, or there is not exactly one stock trigger, stop. Do not apply `20261017`, `20261021001000` or `20261021002000` after the final file. They are historical steps, not repair tools.

## 2. Read-only preflight (run before any write)

Run these as a read-only role. Save the output with the release ticket.

```sql
-- 0. Version and migration ledger.
select version();
select to_regclass('supabase_migrations.schema_migrations') as ledger_exists;
-- If the ledger exists:
select version, name, inserted_at
from supabase_migrations.schema_migrations
order by version;

-- 1. Stock triggers and function identity.
select t.tgname,
       p.oid::regprocedure as function,
       pg_get_triggerdef(t.oid) as definition
from pg_trigger t
join pg_proc p on p.oid = t.tgfoid
where t.tgrelid = 'public.inventory_transactions'::regclass
  and not t.tgisinternal
order by t.tgname;

select count(*) as stock_trigger_count
from pg_trigger
where tgrelid = 'public.inventory_transactions'::regclass
  and not tgisinternal
  and tgfoid = 'public.update_batch_stock()'::regprocedure;

select md5(pg_get_functiondef('public.update_batch_stock()'::regprocedure)) as function_md5;

-- 2. The exact FK that DBA-02 depends on.
select conname, conrelid::regclass, confrelid::regclass, confdeltype,
       pg_get_constraintdef(oid)
from pg_constraint
where contype = 'f'
  and conrelid = 'public.inventory_transactions'::regclass
  and confrelid = 'public.patient_visits'::regclass;

-- 3. RLS policies (DBA-08). Save the full result before changing anything.
select schemaname, tablename, policyname, cmd, roles, qual, with_check
from pg_policies
where schemaname = 'public'
order by tablename, policyname;

-- 4. Unique indexes that may have been skipped (DBA-19).
select indexname, indexdef
from pg_indexes
where schemaname = 'public'
  and tablename in ('profiles', 'reminders', 'patients')
order by tablename, indexname;

-- 5. Duplicate reminders before any unique-index work.
select patient_id, original_visit_id, vaccine_name, type, count(*) as duplicates,
       array_agg(id order by updated_at desc) as ids
from public.reminders
group by patient_id, original_visit_id, vaccine_name, type
having count(*) > 1;

-- 6. Queue and finance diagnostics are local Room data. Export them from the app,
--    not from Postgres. On the server, check only the rows that exist there.

-- 7. Stock drift. If the view does not exist yet, use the equivalent aggregate.
select batch_id, vaccine_id, batch_number, recorded_quantity, ledger_quantity,
       drift, ledger_rows, last_ledger_at
from public.inventory_reconciliation
order by abs(drift) desc, batch_id;

-- 8. Audit-retention impact (VER-03). Count and export before deleting.
select count(*) as rows_older_than_one_year,
       min(timestamp) as oldest,
       max(timestamp) as newest
from public.audit_logs
where timestamp ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}T'
  and timestamp::timestamptz < now() - interval '1 year';
```

Also record row counts for the affected tables, the newest backup timestamp, and the current Supabase project ref. Do not proceed if the backup cannot be restored.

## 3. Migration failure modes and guards

| File / area | Failure mode | Evidence | Guard |
|---|---|---|---|
| `20261022_fix_stock_trigger_update_arithmetic.sql` | Not self-transactional. A stray trigger makes the final guard fail after the known trigger was dropped/recreated; with autocommit both remain active. | Local PostgreSQL 18.2 reproduction: `expected exactly one update_batch_stock() trigger, found 2`; both `inventory_transactions_update_batch_stock` and `legacy_stock_trigger` remained. | Wrap in `begin; … commit;`. Preflight the count **before** DDL. Prefer dropping every trigger that calls `update_batch_stock()` in a reviewed migration, then creating one. Apply with a transactional runner. |
| `20261016_rls_index_and_stock_trigger_hardening.sql` | Unique indexes are skipped when duplicates exist; the migration still succeeds with only a `NOTICE`. | `supabase/migrations/20261016_rls_index_and_stock_trigger_hardening.sql:94-111` | Treat a skipped index as a deployment failure, not a notice. Query `pg_indexes` after apply. De-duplicate first. |
| `20261021000000_drop_legacy_profiles_policy.sql` | `DROP FUNCTION … CASCADE` can drop dependent views/functions beyond the named policies. | `supabase/migrations/20261021000000_drop_legacy_profiles_policy.sql:18-25` | Snapshot `pg_policies` and `pg_depend`; drop the known policies explicitly; drop the function with `RESTRICT`. |
| `20261025_audit_log_retention.sql` | Permanently deletes audit rows; malformed timestamps that match the regex can abort the cast; cron may be absent. | `supabase/migrations/20261025_audit_log_retention.sql:1-54` | Owner-approved retention; export count/rows; test on a copy; validate timestamps; confirm `cron.unschedule('prune-audit-logs')` is available before enabling. |
| Stock chain out of order | Applying an older stock migration after the final one can replace the final function with an older body. | `20261017`, `20261021001000`, `20261021002000` all replace `public.update_batch_stock()`. | Ledger-enforced order; final file last; never paste old SQL into the editor. |
| Fresh project | `20261018_ensure_stock_trigger_installed.sql` and the final trigger expect the base tables/function to exist. | `20261018` is an install guard, not a schema builder. | Apply the full base chain first. Do not treat CI’s minimal schema as a production bootstrap. |
| RLS carve-outs | The new policies may deny a workflow the app still uses. | DBA-08 | JWT role matrix on a disposable project; ship app gating first if needed. |

## 4. Stock data repair (DBA-01)

### 4.1 What is known and what is not

The trigger fix prevents new arithmetic corruption. It does **not** repair history. The ledger is the best available record of movements, but it may be missing rows that the old negative guard rejected. Therefore:

- Do not blindly set every batch to `sum(ledger.quantity)`.
- Do not assume every batch had an opening ledger row.
- Do not use current `remaining_quantity` as the physical truth; it is the corrupted value.
- Require a physical count `T` for every drifted batch before changing it.

### 4.2 Detection and classification

```sql
select batch_id, vaccine_id, batch_number,
       recorded_quantity as r, ledger_quantity as l, drift,
       ledger_rows,
       case
         when ledger_rows = 0 then 'no ledger / opening balance'
         when ledger_rows > 0 and drift <> 0 then 'ledger present / drift'
         else 'reconciles'
       end as classification
from public.inventory_reconciliation
order by classification, abs(drift) desc;
```

For each drift row, export: batch id, vaccine, batch number, current R, ledger sum L, ledger row count, first/last ledger timestamps, transaction types, and the physical count column to be filled by staff. Do not edit R in this step.

**Stop conditions:** negative L; missing batch/vaccine; duplicate transaction IDs; ledger rows with impossible timestamps; a batch whose first ledger row is not a purchase/opening/return; a physical count that cannot be obtained. Quarantine those batches.

### 4.3 Repair pattern

Use one transaction per batch or one reviewed batch of batches. The pattern below makes the ledger and `remaining_quantity` agree **and** records the physical correction as a ledger movement. It works for complete-ledger and opening-balance batches.

```sql
begin;

-- 1. Snapshot the old value and the ledger sum. Keep this table until the repair is signed off.
create table if not exists public._stock_repair_20261026 (
  batch_id uuid primary key,
  old_remaining integer not null,
  ledger_sum integer not null,
  physical_count integer,
  reason text,
  repaired_at timestamptz,
  adjustment_id uuid
);

insert into public._stock_repair_20261026
  (batch_id, old_remaining, ledger_sum, reason)
select batch_id, recorded_quantity, ledger_quantity, 'DBA-01 drift review'
from public.inventory_reconciliation
where drift <> 0
on conflict (batch_id) do nothing;

-- 2. Staff fill physical_count from a physical count / device review. Do not infer it.
--    The repair refuses to run while any target is missing a count or is negative.
do $$
begin
  if exists (
    select 1 from public._stock_repair_20261026
    where physical_count is null or physical_count < 0 or ledger_sum < 0
  ) then
    raise exception 'repair blocked: fill every physical_count; no negative values';
  end if;
end $$;

-- 3. Make recorded stock equal the ledger first. This bypasses the inventory trigger,
--    which is intentional: no ledger movement happened here.
update public.vaccine_batches b
set remaining_quantity = s.ledger_sum,
    updated_at = now()
from public._stock_repair_20261026 s
where b.id = s.batch_id;

-- 4. Record the physical difference as one explicit ledger adjustment. The fixed
--    INSERT trigger applies it once, so R becomes T and L becomes T.
with ins as (
  insert into public.inventory_transactions
    (id, vaccine_id, batch_id, transaction_type, quantity,
     previous_quantity, current_quantity, "user", notes, timestamp)
  select gen_random_uuid(), b.vaccine_id, s.batch_id, 'MANUAL_ADJUSTMENT',
         s.physical_count - s.ledger_sum,
         s.ledger_sum, s.physical_count, 'repair-20261026',
         'DBA-01 repair 2026-10-26; physical count ' || s.physical_count,
         now()
  from public._stock_repair_20261026 s
  join public.vaccine_batches b on b.id = s.batch_id
  where s.physical_count <> s.ledger_sum
  returning id, batch_id
)
update public._stock_repair_20261026 s
set adjustment_id = ins.id, repaired_at = now()
from ins
where s.batch_id = ins.batch_id;

-- 5. Verify inside the transaction. Any drift left for a repaired batch = rollback.
do $$
begin
  if exists (
    select 1
    from public.inventory_reconciliation r
    join public._stock_repair_20261026 s on s.batch_id = r.batch_id
    where r.drift <> 0
  ) then
    raise exception 'repair verification failed: drift remains';
  end if;
end $$;

commit;
```

**Column names are illustrative.** Confirm the actual `inventory_transactions` columns with `information_schema.columns` before running. The trigger only needs `batch_id` and `quantity`; the other columns are for the audit trail. If a mandatory column is missing from the insert, add it from the batch/visit, not a fake value.

### 4.4 Rollback of the stock repair

The repair leaves two artifacts: the snapshot table and the adjustment ledger row.

1. Export the snapshot and the exact `adjustment_id` values.
2. Delete only those adjustment rows first (the trigger subtracts their quantity and returns R to L).
3. Restore `remaining_quantity = old_remaining` from the snapshot.
4. Re-run the reconciliation view and confirm the pre-repair state.

Do not delete pre-existing ledger rows. Do not “fix” a bad physical count by editing the ledger row in place; export and re-do with a new reviewed adjustment.

### 4.5 Verification after commit

```sql
select count(*) as repaired_drift_rows
from public.inventory_reconciliation r
join public._stock_repair_20261026 s on s.batch_id = r.batch_id
where r.drift <> 0;  -- must be 0

select s.batch_id, s.old_remaining, s.ledger_sum, s.physical_count,
       b.remaining_quantity, r.ledger_quantity
from public._stock_repair_20261026 s
join public.vaccine_batches b on b.id = s.batch_id
left join public.inventory_reconciliation r on r.batch_id = s.batch_id
order by s.batch_id;
```

## 5. Finance data repair (DBA-03 / DBA-04)

**No automatic repair of overwritten amounts.** The heal path overwrote the local edit; the old value is generally not recoverable from the rows. Check backups and audit logs for the affected patient/visit. If a row was duplicated by a deterministic-ID mismatch, export both rows and let the owner choose the authoritative amount; do not sum them.

**Date repair.** Only rows with a linked visit and a blank/wrong date are candidates. Back up first:

```sql
create table public._finance_date_repair_20261026 as
select f.id, f.visit_id, f.transaction_date as old_date,
       v.date_given as visit_date, now() as captured_at
from public.finance_transactions f
join public.patient_visits v on v.id = f.visit_id
where f.visit_id is not null
  and (f.transaction_date is null or f.transaction_date = '');

-- Review the list; then update only blank dates, not conflicting nonblank dates.
update public.finance_transactions f
set transaction_date = left(v.date_given, 10)  -- only if date_given is ISO; otherwise convert in IST
from public.patient_visits v
where v.id = f.visit_id
  and (f.transaction_date is null or f.transaction_date = '')
  and v.date_given ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}';
```

Rows with two different nonblank dates are a manual decision: the visit date is usually authoritative, but a legitimate adjustment may not be. Do not overwrite them in bulk. After repair, re-run the finance totals and compare a week/month before and after.

## 6. Reminder/merge data repair

### 6.1 Orphan reminders (DBA-05/DBA-12)

```sql
select r.id, r.patient_id, r.original_visit_id, r.vaccine_name, r.type, r.due_date
from public.reminders r
left join public.patients p on p.id = r.patient_id
left join public.patient_visits v on v.id = r.original_visit_id
where p.id is null or (r.original_visit_id is not null and v.id is null);
```

Export the rows. If the parent is recoverable, restore the parent or re-link; if not, the owner chooses soft-delete vs hard-delete. Do not auto-delete a reminder that contains patient data; it is a privacy-relevant record. Apply the same rule to `personal_vaccine_reminders`.

### 6.2 Reminder duplicates (DBA-19)

Server duplicates:

```sql
select patient_id, original_visit_id, vaccine_name, type, count(*),
       array_agg(id order by updated_at desc) as ids
from public.reminders
group by patient_id, original_visit_id, vaccine_name, type
having count(*) > 1;
```

Export; keep the newest per event; record what was discarded. Only then create the unique index. Do not create the index first and let the migration skip it silently. Local Room duplicates need the Room v34/v35 migration described in `11-remediation-plan.md` §5.4; export them from the app before deleting.

### 6.3 Merge loss (DBA-26)

There is no safe automatic reconstruction for a merge that already deleted consultations, notes or finance. Determine the deleted duplicate ID from the audit log (`MERGE_PATIENT` remarks) or backup; compare the master’s child rows against the backup. Re-link only when the source row still exists. Otherwise restore from backup, not from guesswork. Before any future merge, run a dry run that counts every child table for the duplicate and the master.

## 7. Audit retention (VER-03)

The migration deletes rows permanently. Before deployment:

1. Count and export the rows selected by the function to a secure archive.
2. Record the proposed retention period and owner approval.
3. Test the cast on a copy; malformed timestamps that match the regex can abort the whole prune.
4. Confirm whether pg_cron is available; if not, the migration leaves the function unscheduled and the notice may be missed.
5. If retention is not approved, do not apply the migration; if already applied, unschedule the job and revoke execute until approved.

Do not run a “dry run” that updates/deletes in production. Use `select count(*)` and an export.

## 8. Migration application procedure

1. **Freeze writes** for the repair window (stock/finance/patient changes), or at least put the app in read-only mode. A stock repair while devices are writing will race.
2. **Snapshot** the database (provider backup plus a logical `pg_dump` of affected schemas). Verify restore on a disposable project.
3. **Apply the full ordered migration chain** to staging. Do not apply a single migration to production without the preceding ledger state.
4. **Run preflight and postflight** queries. The postflight must check trigger count, function hash, index existence, policy count and drift.
5. **Run the SQL tests** in `supabase/tests/` on a disposable database. The CI workflow is not a full-chain test; do not treat a green stock test as proof for RLS, retention or the view.
6. **Deploy the app** only after server migration compatibility is confirmed. RLS changes can break old app builds; coordinate the minimum app version.
7. **Unfreeze** and watch error rates, queue failures, stock reconciliation and finance totals for at least one business day.

## 9. Deployment blockers

These must be closed or explicitly accepted before any production migration:

- No read-only live schema snapshot; trigger/FK/RLS state unknown.
- No verified backup/restore; no maintenance window.
- Owner decisions on RLS, retention, merge semantics, DBA-02 and DBA-07/15.
- Release note lists the wrong/old stock migration (VER-02).
- Final stock migration is not transaction-safe under a stray trigger (VER-01).
- `20261016` unique indexes may have been skipped with only a notice.
- `20261025` deletes audit data; no retention approval/export.
- `20261021000000` uses CASCADE; dependent-object preflight not done.
- Existing duplicates for reminders/profiles/patients prevent unique indexes.
- No staged RLS/JWT test; `rls_role_test.sql` contains placeholders.
- The 230-path post-audit drift was not fully re-audited; only the paths named by these findings were inspected.

## 10. Recovery principles

- **Migration failure:** rely on the explicit transaction; if the runner is non-transactional, restore the captured function/trigger/policy definitions from preflight. Never re-run an older stock migration to “roll back”; it reintroduces a known bug.
- **Data repair:** restore the snapshot table first, then reverse the exact adjustment rows. Never delete a pre-existing ledger row to hide a repair mistake.
- **RLS:** restore the saved `pg_policies` definitions; do not drop tables or grants outside the reviewed diff.
- **Retention:** disable the cron job immediately; restore from the exported archive. If no export exists, the deletion is irreversible.
- **Room schema:** Room is forward-only. Ship a corrective migration; keep a database backup before app upgrade. Do not downgrade the app without a tested downgrade path.

Detailed test and rollback steps are in `13-test-and-rollback-plan.md`.
