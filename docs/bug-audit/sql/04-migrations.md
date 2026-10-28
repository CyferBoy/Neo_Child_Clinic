# 04 — Migrations

**Scope:** ordering, idempotency, transaction safety, conditional DDL, replayability, and application/schema agreement.
**Files:** 29 migrations, 2026-08-16 → 2026-10-25. Full line counts are in `00-sql-inventory.md §2`.

---

## 1. Ordering

Supabase applies migrations in filename order (lexicographic). The intended order is the filename order, and that order is mostly coherent:

- hardening → base-column additions → receipt → RLS/accountability → feature tables → policy rewrites → stock trigger → retention.
- `20261022_fix_stock_trigger_update_arithmetic.sql` sorts before `20261022_inventory_reconciliation_view.sql` (`f` < `i`), so the final trigger is installed before the view is created. Correct.
- `20261025_audit_log_retention.sql` is last. Correct.

**Hygiene issue, not a defect:** two naming schemes coexist — `20261013_…` (8-digit date) and `20261013120000_…` (14-digit timestamp). Lexicographic order puts both `20261013120000` and `20261013140000` **before** `20261013_profiles_privileged_fields_guard.sql`, because `1` (0x31) < `_` (0x5F) at the first differing position. The three files are independent, so the result is still correct, but the pattern is fragile. Use 14-digit timestamps consistently.

**Duplicate work:** `20260822_patient_visit_backup_and_timestamps.sql` and `20260822_vaccination_items_snapshot.sql` both add `vaccination_items.vaccine_name` and `batch_number`. Both are `if not exists`, so replay is safe, but one is redundant.

**Contradiction:** `20260919_remove_soft_delete.sql:20-22` drops `doctor_slot_exceptions.is_deleted`, then `20260925_business_soft_delete_columns.sql:35` adds it back. The final state keeps the column. The drop is dead work and obscures intent. Part of `SQL-028`.

---

## 2. Transaction wrappers

Nine migrations have no explicit `begin;`:

1. `20260909_expenses.sql`
2. `20260911_doctor_availability_slots.sql`
3. `20260919_remove_soft_delete.sql`
4. `20260919_waste_records_snake_case.sql`
5. `20260929000000_security_baseline.sql`
6. `20260929001000_write_rate_limit.sql`
7. `20261017_stock_trigger_duplicate_and_update_arithmetic.sql`
8. `20261022_fix_stock_trigger_update_arithmetic.sql`
9. `20261022_inventory_reconciliation_view.sql`

Some tools wrap each migration file in a transaction; the Supabase CLI does not guarantee it for arbitrary SQL, and `psql -f` does not unless `--single-transaction` is passed. The file comments sometimes say “transactional” (`20261022…:29`) but the file has no `begin`, so that claim depends on the runner.

### SQL-004 — Non-transactional migrations can leave partial state

- **Severity:** Medium
- **Confidence:** Confirmed (file content); impact depends on the runner
- **Object:** the nine files above, especially `20261022_fix_stock_trigger_update_arithmetic.sql` and `20260929000000_security_baseline.sql`
- **Workflow:** migration apply, failed deploy, retry
- **Root cause:** no `begin`/`commit`. Each statement autocommits.
- **Failure scenario (stock):** `20261022` drops both stock triggers (`:30-31`), creates the function (`:33`), creates the trigger (`:97-99`), then runs the single-trigger guard (`:103-118`). If the guard raises — for example a third trigger calling the same function exists — the trigger has already been created and the old ones dropped, leaving a state the author explicitly did not want. Re-running is safe because the drops are `if exists`, but the intermediate window has no/duplicate enforcement.
- **Failure scenario (RLS):** `20260929000000` creates policies in a loop. A failure halfway (e.g. a missing column referenced by `is_active_staff`) leaves some tables with new policies and others with the old ones. The app’s authorization surface is inconsistent until a manual repair.
- **Impact:** partial schema/RLS state that is hard to diagnose; retries may mask which step failed.
- **Evidence:** the nine files; `20261022…:28-31,97-118`.
- **Correction:** wrap every migration in `begin; … commit;`. For `20261022`, move the guard **before** the drop/create, or wrap the whole file. Add a lint step that rejects a migration without `begin`.
- **Regression test:** replay on an empty database with `ON_ERROR_STOP=1`; inject a failure in the guard and assert the transaction rolls back (trigger set unchanged).
- **Live check:** no.

---

## 3. Conditional DDL that can silently do nothing

### SQL-003 — Conditional unique indexes may never exist

- **Severity:** High
- **Confidence:** Confirmed (logic); `Probable` for any given production database
- **Object:** `profiles_email_lower_key`, `reminders_natural_key`, `patients_clinic_id_key`
- **Workflow:** upsert conflict resolution, duplicate prevention, sync idempotency
- **Root cause:** `20261016_rls_index_and_stock_trigger_hardening.sql:92-124` checks for duplicates and **skips** index creation with a `raise notice` if any exist. A notice is easy to miss and does not fail the deploy.
- **Failure scenario:** production has two profiles with the same case-insensitive email; the unique index is skipped. `SyncUploader` and the Edge Function rely on the index for conflict handling and duplicate prevention. A later upsert inserts a second row, and `refreshProfiles` returns duplicates; the app shows two staff records and role checks become ambiguous. The same applies to reminder natural keys (duplicate reminders, double notifications) and clinic IDs (duplicate patient identifiers).
- **Impact:** silent absence of a correctness constraint; duplicate data accumulates; no signal after the migration.
- **Evidence:** `20261016…:96-100,102-111,113-122`.
- **Correction:** do not skip. Fail the migration with the duplicate query as the error message, or create the index `NOT VALID`-style is not available for unique indexes — so: clean the data first in the same migration (with a reviewed mapping), then create the index unconditionally. At minimum, write the skip to a table (`schema_warnings`) and add a CI check that fails if any expected index is missing.
- **Regression test:** seed a duplicate, run the migration, assert it fails (not silently skips); seed clean data, run, assert the index exists.
- **Live check:** yes — run the duplicate queries and `select indexname from pg_indexes where schemaname='public'`.

### 3.1 Other conditional DDL

- The baseline skips missing tables (`20260929000000…:62-64`). This is why `SQL-001` is dangerous: a fresh environment silently gets **no RLS** on the 15 missing tables instead of failing loudly. The `continue` should be a hard error on a non-empty deployment.
- `20260911_doctor_availability_slots.sql:120-138` adds FK columns with `if not exists`; if the column exists with a non-UUID type (it does: `text`), the `add column` is skipped and the FK is **not** created. The migration comment implies the FK exists. `SQL-005`/`SQL-001`.
- `20261016…:126-144` installs the stock trigger only if no trigger with that exact name exists. A legacy trigger with a different name (`tr_update_batch_stock`) would not be detected, so this migration could create a second stock trigger. `20261022` later removes both names and guards on the function OID, so the final state is correct — but replay up to `20261018` is not.

---

## 4. Migration/application agreement

| Application expectation | Migration reality | Verdict |
|---|---|---|
| `vaccine_batches.remaining_quantity` is authoritative server-side | final trigger maintains it; app sends `0` on create | agreed, but retry hazard (`SQL-008`) |
| `patient_visits.receipt_number` is server-generated | trigger allocates only when blank | **disagreed** (`SQL-018`) |
| `audit_logs` is append-only | SELECT+INSERT only | agreed |
| `finance_transactions.transaction_date` non-null | `date NOT NULL` | agreed at DB; client nullable (`SQL-027`) |
| hard delete everywhere except reminders | server still has soft-delete columns on 18 tables | **disagreed** (`SQL-028`) |
| `inventory_transactions.visit_id` is cleared before visit delete | `deleteVaccination` explicitly does **not** clear it (`VaccinationRepositoryImpl.kt:452-453`) | **disagreed** (`SQL-023`) |
| sync group is atomic | one PostgREST request per item | **disagreed** (`SQL-009`) |
| `profiles` self-update is allowed for non-privileged fields | `profiles_update_own` retained + trigger | agreed |
| deactivated staff cannot write | doctor-slot policies lack `is_active_staff()` | **disagreed** (`SQL-013`) |

---

## 5. Replayability

### SQL-036 — No replay or drift test

- **Severity:** Medium
- **Confidence:** Confirmed
- **Object:** CI / developer workflow
- **Workflow:** new environment, staging refresh, disaster recovery
- **Root cause:** there is no CI job that applies migrations to an empty database or compares a live schema to the repo. `.gitignore` history shows migrations were untracked for a period (`supabase/.gitignore:5-8`), so the repo already lost the “source of truth” property once.
- **Failure scenario:** `SQL-001` reaches production as a failed staging rebuild; or a manual dashboard change drifts silently until a migration fails.
- **Impact:** the migration chain is not a reliable build artifact.
- **Evidence:** 15 missing `CREATE TABLE`s; `20261016…:13`; no `.github/workflows` SQL job (checked).
- **Correction:** add a CI job:
  1. start an empty Postgres (service container),
  2. apply migrations in order with `ON_ERROR_STOP=1`,
  3. run `verify_security.sql` and assert expected RLS/policies,
  4. run `pg_dump --schema-only` and diff against a committed baseline.
  Because step 2 cannot pass today (`SQL-001`), land the baseline schema first.
- **Regression test:** the CI job itself.
- **Live check:** no.

---

## 6. Destructive and irreversible statements

| Statement | File:line | Risk |
|---|---|---|
| `DROP FUNCTION public.get_my_role() CASCADE` | `20261021000000…:26` | drops dependent policies (`SQL-016`) |
| `DROP TABLE IF EXISTS public.audit_logs CASCADE` | scratch `missing_tables.sql:53` | destructive; not a migration, but present in the repo |
| `UPDATE patient_visits SET receipt_number = null` | `20260824…:142` | wipes existing numbers; comment says testing-only. Must not run against a real deployment. |
| `drop trigger if exists …` before create | several stock migrations | safe only inside a transaction (`SQL-004`) |
| `delete from public.user_devices` dedupe | `20260816…:147-160` | deletes duplicate FCM tokens; intended but irreversible |
| `alter table … drop column is_deleted` | `20260919…:21` | then re-added by `20260925`; data loss if the column held meaning |

**Recommendation:** gate `20260824…:142` behind a check that the table is empty or a GUC is set. As written, replaying migrations on a database with real receipts silently nulls them (though the trigger then reissues new numbers on the next save, which is worse: the historical receipt identity is lost).

---

## 7. Live checks

```sql
-- Applied migrations and their checksums (Supabase CLI table).
select version, name, inserted_at from supabase_migrations.schema_migrations order by version;

-- Objects referenced by migrations but missing.
select t.name
from (values ('patients'),('patient_visits'),('patient_notes'),('vaccination_items'),
             ('vaccines'),('vaccine_batches'),('inventory_transactions'),('waste_records'),
             ('borrow_records'),('reminders'),('consultations'),('finance_transactions'),
             ('audit_logs'),('profiles'),('user_devices'),('todo_notification_events')) t(name)
where to_regclass('public.'||t.name) is null;

-- Expected unique indexes.
select indexname from pg_indexes
where schemaname='public'
  and indexname in ('profiles_email_lower_key','reminders_natural_key','patients_clinic_id_key');

-- Stock triggers (must be exactly one).
select tgname from pg_trigger
where tgrelid='public.inventory_transactions'::regclass and not tgisinternal;
```

None was run. `supabase/.temp` exists but was not used as evidence.
