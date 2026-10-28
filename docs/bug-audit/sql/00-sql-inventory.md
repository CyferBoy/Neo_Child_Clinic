# 00 — SQL Inventory

**Audit type:** source-first, read-only. No SQL, migration, policy, trigger, or database state was changed.
**Repository revision:** `b91142a` (worktree clean at audit start).
**Date:** 2026-10-25.
**Finding IDs used across this set:** `SQL-001` … `SQL-037`. The canonical register with severity and confidence is in `09-final-sql-audit-report.md`.

---

## 1. What was counted

| Measure | Value | Method |
|---|---:|---|
| `.sql` files in repo (excluding `.git`) | 36 | `find . -name '*.sql'` |
| Total SQL lines | 3,147 | `wc -l` |
| Supabase migrations | 29 | `ls supabase/migrations/*.sql` |
| Supabase SQL test scripts | 3 | `ls supabase/tests/*.sql` |
| Security verification scripts | 1 | `supabase/verify_security.sql` |
| Cloudflare D1 schema | 1 | `cloudflare/backup-worker/schema.sql` |
| Hand-applied scratch SQL | 2 | `.artifacts/.../scratch/*.sql` |
| `CREATE TABLE` statements in migrations | 9 | literal grep |
| `CREATE POLICY` statements in migrations | 56 | literal grep |
| `DROP POLICY` statements in migrations | 68 | literal grep |
| `CREATE [OR REPLACE] FUNCTION` in migrations | 21 | literal grep |
| `CREATE TRIGGER` in migrations | 14 | literal grep |

The 36 files break down as **29 migrations + 3 tests + 1 verification + 1 D1 schema + 2 scratch files**. The two scratch files are build artefacts, not deployable migrations, but they are the only source in the repo for 15 core table definitions and must be treated as evidence of what was hand-applied.

---

## 2. Migration inventory

Ordered by filename (the effective apply order). “Tx” means the file opens an explicit `begin;`; nine files do not.

| # | File | Lines | Tx | Primary objects |
|---:|---|---:|:--:|---|
| 1 | `20260816_security_hardening.sql` | 138 | yes | `set_updated_at`, `is_admin`, `protect_profile_privileged_fields`, profile RLS, audit columns |
| 2 | `20260822_patient_visit_backup_and_timestamps.sql` | 82 | yes | `vaccination_items` snapshot columns + backfill |
| 3 | `20260822_vaccination_items_snapshot.sql` | 60 | yes | `vaccination_items` snapshot columns |
| 4 | `20260824_receipt_numbering.sql` | 156 | yes | `receipt_sequences`, `neo_*` receipt functions, receipt trigger |
| 5 | `20260825_clinic_rls_and_accountability.sql` | 176 | yes | `is_active_staff`, `neo_current_staff_label`, `neo_stamp_accountability`, baseline CRUD policies |
| 6 | `20260825_patient_todos.sql` | 60 | yes | `consultation_todos`, `vaccination_todos`, notify triggers |
| 7 | `20260901_personal_vaccine_reminders.sql` | 61 | yes | `personal_vaccine_reminders` |
| 8 | `20260902_borrow_returns.sql` | 55 | yes | `borrow_returns` |
| 9 | `20260902_personal_vaccine_reminders_optional_patient.sql` | 25 | yes | nullable `patient_id` |
| 10 | `20260909_expenses.sql` | 98 | **no** | `expenses`, policies |
| 11 | `20260911_doctor_availability_slots.sql` | 236 | **no** | `doctor_weekly_slots`, `doctor_slot_exceptions`, FK columns, notify function |
| 12 | `20260919_remove_soft_delete.sql` | 22 | **no** | expenses DELETE policy, drop exception soft-delete column |
| 13 | `20260919_waste_records_snake_case.sql` | 60 | **no** | `waste_records` column renames |
| 14 | `20260925_business_soft_delete_columns.sql` | 59 | yes | adds `is_deleted`/`deleted_at`/`deleted_by` to 18 tables |
| 15 | `20260929000000_security_baseline.sql` | 135 | **no** | `is_active_staff` rewrite, RLS enable + CRUD policies for 20 tables |
| 16 | `20260929001000_write_rate_limit.sql` | 79 | **no** | `api_write_rate_limits`, `enforce_write_rate_limit` |
| 17 | `20261001_app_metadata_roles.sql` | 169 | yes | JWT `app_metadata.role` role source, policy rewrites |
| 18 | `20261013120000_role_policy_carveouts.sql` | 130 | yes | expenses and doctor-slot policy carve-outs |
| 19 | `20261013140000_stock_trigger_negative_guard.sql` | 51 | yes | stock negative guard |
| 20 | `20261013_profiles_privileged_fields_guard.sql` | 77 | yes | profile privileged-field trigger |
| 21 | `20261016_rls_index_and_stock_trigger_hardening.sql` | 164 | yes | indexes, conditional unique keys, RLS force, bucket policies |
| 22 | `20261017_stock_trigger_duplicate_and_update_arithmetic.sql` | 124 | **no** | stock trigger replacement |
| 23 | `20261018_ensure_stock_trigger_installed.sql` | 67 | yes | stock trigger presence |
| 24 | `20261021000000_drop_legacy_profiles_policy.sql` | 63 | yes | drops legacy `get_my_role()` CASCADE |
| 25 | `20261021001000_stock_trigger_upsert_fix.sql` | 92 | yes | stock trigger upsert branch |
| 26 | `20261021002000_fix_stock_trigger_batch_reassignment.sql` | 72 | yes | stock trigger moved-batch branch |
| 27 | `20261022_fix_stock_trigger_update_arithmetic.sql` | 131 | **no** | final stock trigger + single-trigger guard |
| 28 | `20261022_inventory_reconciliation_view.sql` | 31 | **no** | `inventory_reconciliation` view |
| 29 | `20261025_audit_log_retention.sql` | 54 | yes | `prune_audit_logs`, cron schedule |

**Duplicate-purpose migrations:** `20260822_patient_visit_backup_and_timestamps.sql` and `20260822_vaccination_items_snapshot.sql` both add the same snapshot columns (`vaccine_name`, `batch_number`); both use `add column if not exists`, so they are idempotent but redundant. Five stock-trigger migrations (`20261013…`, `20261016`, `20261017`, `20261018`, `20261021001000`, `20261021002000`, `20261022`) define the same function repeatedly; only the last one is authoritative.

---

## 3. Table inventory

The schema catalogue CSV (`supabase/migrations/Supabase Snippet Full Schema Catalog (Tables, Keys, Indexes).csv`) is a partial snapshot: 23 tables, 374 column rows, **no constraint or index rows**. It is not a substitute for a schema dump.

| Table | Columns in CSV | Created by a migration? | Source of definition |
|---|---:|:--:|---|
| `patients` | 15 | **no** | hand-applied / dashboard |
| `patient_visits` | 30 | **no** | hand-applied / dashboard |
| `patient_notes` | 10 | **no** | hand-applied / dashboard |
| `vaccination_items` | 14 | **no** | hand-applied; altered by migrations 2–3 |
| `vaccines` | 15 | **no** | hand-applied / dashboard |
| `vaccine_batches` | 20 | **no** | hand-applied; altered by scratch SQL |
| `inventory_transactions` | 18 | **no** | hand-applied |
| `waste_records` | 14 | **no** | hand-applied; renamed by migration 13 |
| `borrow_records` | 15 | **no** | hand-applied |
| `reminders` | 21 | **no** | hand-applied |
| `consultations` | 18 | **no** | hand-applied |
| `finance_transactions` | 20 | **no** | hand-applied |
| `audit_logs` | 17 | **no** | hand-applied |
| `profiles` | 16 | **no** | hand-applied / auth trigger |
| `user_devices` | 12 | **no** | hand-applied |
| `receipt_sequences` | 6 | yes | migration 4 |
| `consultation_todos` | 15 | yes | migration 6 |
| `vaccination_todos` | 16 | yes | migration 6 |
| `personal_vaccine_reminders` | 19 | yes | migration 7 |
| `borrow_returns` | 11 | yes | migration 8 |
| `expenses` | 16 | yes | migration 10 |
| `doctor_weekly_slots` | 11 | yes | migration 11 |
| `doctor_slot_exceptions` | 12 | yes | migration 11 |
| `api_write_rate_limits` | **absent** | yes | migration 16 |
| `todo_notification_events` | **absent** | **no** | created outside the repo (stated in `20261016…:13`) |

**Result:** **15 of 23 catalogue tables (65%) have no `CREATE TABLE` anywhere in the repository.** A fresh `supabase db reset` cannot build this schema. This is `SQL-001`.

`receipt_sequences` is present in the CSV and created by migration 4 — the earlier assumption that it was catalogue-absent is incorrect; only `api_write_rate_limits` is migration-created and catalogue-absent.

---

## 4. Runtime object inventory

### 4.1 Functions defined in migrations

| Function | Defined in | Security | Notes |
|---|---|---|---|
| `set_updated_at()` | `20260816…:46` | invoker | before-update timestamp |
| `is_admin()` | `20260816…:75` | definer, `search_path=public` | reads `profiles` |
| `protect_profile_privileged_fields()` | `20260816…:96`, redefined `20261013…` | invoker | blocks self-escalation |
| `neo_parse_visit_date(text)` | `20260824…:41` | invoker, immutable | best-effort date parse |
| `neo_financial_year_label(date)` | `20260824…:62` | invoker, immutable | FY label |
| `neo_next_receipt_number(date)` | `20260824…:81` | definer, `search_path=public` | sequence allocator |
| `neo_assign_receipt_number()` | `20260824…:109` | definer, `search_path=public` | receipt trigger |
| `is_active_staff()` | `20260825…:32`, rewritten `20260929…:15` | definer, `search_path=public, auth` | RLS predicate |
| `neo_current_staff_label()` | `20260825…:55` | definer, `search_path=public` | display label |
| `neo_stamp_accountability()` | `20260825…:73` | definer, `search_path=public` | stamps `created_by`/`updated_by` |
| `notify_todays_patient()` | `20260911…:153` | definer, `search_path=public, extensions, net` | inserts event + HTTP POST |
| `enforce_write_rate_limit()` | `20260929001000…:16` | definer, `search_path=public, auth` | 600 writes/min/user |
| `prune_audit_logs()` | `20261025…:7` | definer, `search_path=public` | deletes > 1 year |
| `update_batch_stock()` | latest `20261022…:33` | invoker (default) | stock arithmetic |

### 4.2 Triggers that matter

| Trigger | Table | Timing | Function |
|---|---|---|---|
| `set_updated_at` | every `public` table (loop) | before update | `set_updated_at()` |
| `protect_profile_privileged_fields` | `profiles` | before update | `protect_profile_privileged_fields()` |
| `neo_stamp_accountability` | loop over tables | before insert/update | `neo_stamp_accountability()` |
| `neo_assign_receipt_number` | `patient_visits` | before insert/update | `neo_assign_receipt_number()` |
| `inventory_transactions_update_batch_stock` | `inventory_transactions` | after insert/update/delete | `update_batch_stock()` |
| `notify_todays_patient` (×2) | `consultation_todos`, `vaccination_todos` | after insert | `notify_todays_patient()` |
| `enforce_write_rate_limit` | loop over tables | before insert/update/delete | `enforce_write_rate_limit()` |
| `profiles_updated_at` style triggers | `profiles` | before update | `set_updated_at()` (duplicate paths) |

### 4.3 RLS policies

- 56 `create policy` and 68 `drop policy` statements across migrations; the effective policy set is the result of the last write per `(table, policy name)`.
- Baseline (`20260929…:36-110`) enables RLS and creates four policies (`active staff select/insert/update/delete`) for **20 tables** using `public.is_active_staff()`.
- `20261001_app_metadata_roles.sql` rewrites role checks to read `auth.jwt() -> 'app_metadata' ->> 'role'`.
- `20261013120000_role_policy_carveouts.sql` narrows `expenses` and doctor-slot tables.
- `20261016…` forces RLS and revokes client access on `todo_notification_events`, and creates storage policies for `patient-docs`.

---

## 5. Non-migration SQL

| File | Role | Assessment |
|---|---|---|
| `supabase/tests/stock_trigger_arithmetic.sql` | trigger regression | real assertions; misses the moved-purchase negative case (`SQL-033`) |
| `supabase/tests/inventory_reconciliation_test.sql` | view regression | real assertions; assumes nullable `vaccine_id` (`SQL-034`) |
| `supabase/tests/rls_role_test.sql` | RLS manual script | placeholders, no assertions (`SQL-035`) |
| `supabase/verify_security.sql` | read-only inspection | useful, not automated (`SQL-037`) |
| `cloudflare/backup-worker/schema.sql` | D1 backup metadata | out of scope; no clinic data, no FK/RLS (`§7`) |
| `.artifacts/…/scratch/missing_tables.sql` | hand-applied core tables/FKs | **only** source for `inventory_transactions`, `audit_logs`, `user_devices`, etc.; not deployable |
| `.artifacts/…/scratch/*.sql` (second) | ad-hoc fix | not reviewed as authoritative |

---

## 6. Coverage: what was inspected

- All 29 migrations read line-by-line.
- All 3 SQL tests read; assertions catalogued.
- `verify_security.sql` read.
- Full 374-row schema CSV parsed with Python (columns, types, nullability, defaults).
- Android sync write path: `SyncUploader.kt`, `SyncRepositoryImpl.kt`, entity registry, conflict checks, delete paths.
- Android read/pull paths: patient, vaccination, inventory, waste, borrow, finance, expense, consultation, reminder, personal reminder, doctor availability, profile, audit.
- Room database version history (`AppDatabase.kt`, v33; 31→32 purge).
- Repository delete flows: patient, vaccination/visit, consultation, inventory batch, vaccine, borrow, expense, finance.
- Graphify index refreshed; graph queries used to locate callers and blast radius.
- Prior audit notes in `docs/Knowledge_Base/Database-Audit/` and `docs/bug-audit/` were read **only as hypotheses** and independently re-checked against source.

## 7. Coverage: what was not inspected / cannot be verified statically

| Area | Why | Consequence |
|---|---|---|
| Live `pg_constraint`, `pg_policies`, `pg_trigger` | no database, `psql`, Docker, or Supabase CLI installed | FK/RLS/trigger findings are `Requires Verification` where they depend on live state |
| RLS behaviour under real JWTs | no Supabase instance | policy logic is reviewed; enforcement is untested |
| Migration replay | no disposable database; 15 tables have no DDL | replay would fail at migration 1 |
| Trigger execution and arithmetic | not executed | proven by code reading, not by run |
| Query plans / `EXPLAIN` | no server | performance findings are structural, not measured |
| Supabase Edge Functions (`manage-staff`, `notify-update`) | TypeScript, not SQL; only cross-checked for role writes | service-role writes bypass RLS by design |
| Cloudflare Worker runtime | D1 schema only | no clinic data path found |
| pg_cron availability | environment-dependent | retention may never run |
| Actual production row counts / duplicates | no live access | conditional-index findings are `Probable` for production |
| Backup/restore of the live schema | no access | the hand-applied DDL may differ from the CSV |

---

## 8. Method notes

- Every claim below carries a `path:line` citation. Where a citation points at a comment, the comment is treated as a claim to test, not proof.
- “Confirmed” means the defect is visible in source and does not depend on unknown live state.
- “Probable” means the code path is clear but the effect depends on a live constraint, policy, or data shape.
- “Requires Verification” means a disposable database or live introspection is required before the finding can be promoted.
- No credentials, keys, patient data, or connection strings are reproduced in this set.
- No file outside `docs/bug-audit/sql/` was modified by this audit.
