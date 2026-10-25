# Database Schema

> Every table on both sides of the wire, and where the two disagree.
> Related: [[Room Database]] · [[Supabase]] · [[Row Level Security]] · [[Offline-First Architecture]]

## The single most important fact

⚠️ **There is no initial migration that creates the base tables.** The 29 files in
`supabase/migrations/` only ever `ALTER` the ~12 legacy tables; they `CREATE TABLE` only the
9 newer ones. Three independent confirmations:

1. `supabase/.gitignore:5-8` — *"a fresh `supabase db reset` applied 4 files against a
   hand-built schema."*
2. `20260909_expenses.sql:3-10` — *"supabase/migrations/ is listed in supabase/.gitignore
   in this checkout, so no prior migration files were available to inspect… Please
   reconcile this against your actual deployed schema before applying."*
3. `inventory_transactions` — the stock ledger — was created by hand-patched scratch SQL at
   `.artifacts/…/scratch/missing_tables.sql:22`, which is gitignored.

**A clean clone cannot reproduce the database.** The closest artifact is also gitignored:
`.artifacts/f21d0dfd-…/full_project_schema.sql.artifact.md`.

### Git tracking — resolved

`supabase/.gitignore:5-8` records the original incident: ignoring `migrations/` once left
18 of 22 RLS/schema migrations untracked, so a fresh `supabase db reset` applied only 4 files
against a hand-built schema.

✅ **That gap is closed.** Commit `f8e5d8a` ("chore: track migrations and full project
documentation") added the folder, and all **29** `.sql` migrations plus the CSV catalog are
tracked today (the latest is `20261025_audit_log_retention.sql`):

```
git ls-files supabase/migrations/ | grep -c '\.sql$'   # → 29
git check-ignore supabase/migrations/*.sql             # → no output (not ignored)
```

Every `CREATE TABLE`, the RLS baseline and the accountability triggers are reviewable and
replayable from a clean checkout. ✅ Verified 2026-10-25.

⚠️ The *unreproducible base schema* above is a separate, still-open problem: these 29 files
do not create the ~12 legacy tables, so the missing baseline has not been recovered.

## Migration timeline (chronological)

| # | File | Date | Purpose |
|---|---|---|---|
| 1 | `20260816_security_hardening.sql` | 08-16 | timestamps + accountability columns on every table; `set_updated_at` on all; `is_admin()`; `profiles` RLS |
| 2 | `20260822_vaccination_items_snapshot.sql` | 08-22 | `vaccine_name`/`batch_number` NOT NULL on `vaccination_items` |
| 3 | `20260822_patient_visit_backup_and_timestamps.sql` | 08-22 | same columns idempotently; backfills by array position; repairs NULL timestamps |
| 4 | `20260824_receipt_numbering.sql` | 08-24 | `receipt_sequences` + `neo_next_receipt_number` + receipt trigger; wipes legacy numbers |
| 5 | `20260825_clinic_rls_and_accountability.sql` | 08-25 | `is_active_staff()`, `neo_stamp_accountability()`, blanket CRUD on 12 tables, deny-by-default sweep |
| 6 | `20260825_patient_todos.sql` | 08-25 | `consultation_todos`, `vaccination_todos` |
| 7 | `20260901_personal_vaccine_reminders.sql` | 09-01 | `personal_vaccine_reminders` |
| 8 | `20260902_borrow_returns.sql` | 09-02 | `borrow_returns` (append-only) |
| 9 | `20260902_personal_vaccine_reminders_optional_patient.sql` | 09-02 | patient becomes optional; adds name/phone snapshot |
| 10 | `20260909_expenses.sql` | 09-09 | `expenses` with `amount_paise bigint` |
| 11 | `20260911_doctor_availability_slots.sql` | 09-11 | doctor availability tables; **defines but never installs** `notify_todays_patient()` |
| 12 | `20260919_remove_soft_delete.sql` | 09-19 | adds an `expenses` DELETE policy; drops `doctor_slot_exceptions.is_deleted` |
| 13 | `20260919_waste_records_snake_case.sql` | 09-19 | idempotent camelCase → snake_case rename |
| 14 | `20260925_business_soft_delete_columns.sql` | 09-25 | `is_deleted`/`deleted_at`/`deleted_by` on 18 tables — **policies untouched** |
| 15 | `20260929000000_security_baseline.sql` | 09-29 | `is_active_staff()` redefined with pinned search_path; blanket policies on 20 tables |
| 16 | `20260929001000_write_rate_limit.sql` | 09-29 | `api_write_rate_limits` + per-row write counter |
| 17 | `20261001_app_metadata_roles.sql` | 10-01 | roles moved from `profiles.role` to `auth.jwt() -> 'app_metadata' ->> 'role'` |
| 18 | `20261013_profiles_privileged_fields_guard.sql` | 10-13 | guards `deleted_at`/`deleted_by` on self-update |
| 19 | `20261013120000_role_policy_carveouts.sql` | 10-13 | narrows write policies on `expenses` and doctor slots |
| 20 | `20261013140000_stock_trigger_negative_guard.sql` | 10-13 | negative-stock guard — **only if the function already exists** |
| 21 | `20261016_rls_index_and_stock_trigger_hardening.sql` | 10-16 | narrows 2 `using (true)` policies; 5 indexes; **installs** the stock trigger |
| 22 | `20261017_stock_trigger_duplicate_and_update_arithmetic.sql` | 10-17 | drops the legacy `tr_update_batch_stock` (NCC-003 ✅); rewrites `update_batch_stock()`, but its UPDATE branch still nets `new - 2*old` — superseded by `20261022` (NCC-002 ✅) |
| 23 | `20261018_ensure_stock_trigger_installed.sql` | 10-18 | installs the stock trigger if it is missing, else fails loudly (NCC-007) |
| 24 | `20261021000000_drop_legacy_profiles_policy.sql` | 10-21 | drops the legacy permissive `profiles` policy that bypassed App Metadata RBAC |
| 25 | `20261021001000_stock_trigger_upsert_fix.sql` | 10-21 | same-batch quantity deltas on retried upserts |
| 26 | `20261021002000_fix_stock_trigger_batch_reassignment.sql` | 10-21 | reverses the old batch and applies the movement to the new batch when `batch_id` changes (NCC-AUDIT-001) |
| 27 | `20261022_fix_stock_trigger_update_arithmetic.sql` | 10-22 | removes the remaining UPDATE undo arithmetic (NCC-002 ✅) |
| 28 | `20261022_inventory_reconciliation_view.sql` | 10-22 | diagnostic view for batches whose ledger sum disagrees with `remaining_quantity`; does not repair |
| 29 | `20261025_audit_log_retention.sql` | 10-25 | one-year server-side `prune_audit_logs()` plus an optional guarded `pg_cron` schedule (DA-10) |

### Ordering hazard

Lexical sort puts `20261013_profiles_privileged_fields_guard.sql` **after**
`20261013120000_…` and `20261013140000_…`, because `_` (0x5F) sorts above digits (0x30-0x39).
The date-only prefix inverts its own time-stamped siblings. Today the three files touch
disjoint tables so there is no impact, but `supabase db reset` would run them in the wrong
order. Rename to `20261013000000_profiles_…`.

### Files with no `begin`/`commit`

9 of 29 are bare DDL with no transaction wrapper. A failure mid-file leaves the database
half-migrated with no rollback. Riskiest: `20260909_expenses.sql` and
`20260929001000_write_rate_limit.sql`; the stock-trigger migration is covered by
`supabase/tests/stock_trigger_arithmetic.sql` in CI.

## Table inventory (🔍 the live table count is not derivable from the repo)

### Business tables (base, hand-built)

| Table | PK | Notable columns | RLS |
|---|---|---|---|
| `patients` | `id uuid` | `patient_clinic_id`, `name`, `phone`, `dob`, `attachments jsonb`, `registration_date` | ON, staff CRUD |
| `patient_visits` | `id uuid` | `date_given text`, `receipt_number`, `doctor_id text`, `availability_slot_id uuid` | ON, staff CRUD |
| `vaccination_items` | `id uuid` | `vaccination_id`, `vaccine_id`, `batch_id`, `quantity`, `expiry_date text`, `vaccine_name`, `batch_number` | ON, staff CRUD |
| `vaccines` | `id uuid` | `brand_name`, `company_name`, `mrp`, `net_rate` | ON, staff CRUD |
| `vaccine_batches` | `id uuid` | `purchase_quantity`, `remaining_quantity`, `reserved/used/wasted/borrowed_quantity` | ON, staff CRUD |
| `inventory_transactions` | `id uuid` | `transaction_type`, `quantity`, `previous/current_quantity` | ON, staff CRUD |
| `consultations` | `id uuid` | `visit_id`, `doctor_id text`, `amount/cash_amount/online_amount numeric` | ON, staff CRUD |
| `waste_records` | `id uuid` | 8 columns renamed to snake_case | ON, staff CRUD |
| `patient_notes` | `id uuid` | `content`, `author`, `timestamp text` | ON, staff CRUD |
| `audit_logs` | `id uuid` | `timestamp text`, `module`, `entity_type`, `action`, `old_value/new_value` | ON, **select+insert only** |
| `finance_transactions` | `id uuid` | `transaction_date date NOT NULL`, `payment_method` | ON, staff CRUD |
| `borrow_records` | `id uuid` | `batch_id`, `quantity`, `type BY/TO` | ON, staff CRUD |
| `reminders` | `id uuid` | `patient_id`, `original_visit_id`, `vaccine_name`, `nxt_vaccine_id text[]` | ON, staff CRUD |
| `user_devices` | `id uuid` | `fcm_token` NOT NULL + unique index | ON, owner-scoped |
| `profiles` | `id uuid` → `auth.users(id)` | `role`, `is_active`, `is_deleted`, `fcm_token`, `employee_id` | ON, no INSERT/DELETE policy |

### Tables created by migrations

| Table | Created in | Notes |
|---|---|---|
| `receipt_sequences` | 08-24 | **server-only** — `revoke all`; zero policies by design |
| `consultation_todos` | 08-25 | 4 policies |
| `vaccination_todos` | 08-25 | 4 policies |
| `personal_vaccine_reminders` | 09-01 | 2 CHECK constraints |
| `borrow_returns` | 09-02 | append-only, `quantity > 0` CHECK |
| `expenses` | 09-09 | `amount_paise bigint`, 15-value category CHECK, 3-value payment-method CHECK |
| `doctor_weekly_slots` | 09-11 | `doctor_id` → `profiles(id)` |
| `doctor_slot_exceptions` | 09-11 | type/weekly_slot consistency CHECK |
| `api_write_rate_limits` | 09-29 | **server-only** — `revoke all` |
| `todo_notification_events` | **not in repo** | created by a migration that was never committed |

### Referenced but absent from this repository

| Table | Status |
|---|---|
| `todo_notification_events` | no migration creates it; `20261016:13-14` says so explicitly |

## The catalog CSV is stale — do not trust it

`Supabase Snippet Full Schema Catalog (Tables, Keys, Indexes).csv` has 23 tables and
**despite its filename contains no keys and no indexes**. It is provably pre-`20260919`:

- `csv:350-359` still shows camelCase `vaccineId, batchId, brandName, expiryDate` on
  `waste_records` (renamed by `20260919_waste_records_snake_case.sql`).
- `csv:110` shows `expenses.is_deleted` with no `deleted_at`/`deleted_by` (added 09-25).
- `csv:84` shows `doctor_slot_exceptions.is_deleted` (dropped then re-added).

Snapshot state ≈ after `20260911`.

## Client ↔ server drift

Room ↔ Postgres table mapping is **1:1 for 21 tables**. The sync registry
(`core/sync/SyncUploader.kt:57-361`) declares 24 descriptors — some are aliases, e.g.
`VISIT`/`VACCINATION` both → `patient_visits`, and `TRANSACTION`/`INVENTORY_TRANSACTION`
both → `inventory_transactions`.

### Postgres tables with no Room counterpart

| Table | Why | Verdict |
|---|---|---|
| `receipt_sequences` | server-only; app always uploads a blank `receiptNumber` (`20260824:16-18`) | ✅ intended |
| `api_write_rate_limits` | server-only | ✅ intended |
| `todo_notification_events` | push dedup | ✅ intended |
| `user_devices` | PostgREST-only from `DeviceRepositoryImpl.kt:25` | ✅ intended |

### Room tables with no Postgres table

| Room table | Evidence | Consequence |
|---|---|---|
| `inventory_deductions` | `InventoryDeductionEntity.kt:19`; PK `Long` autoincrement; **no `SyncEntityDescriptor`** | ⚠️ **The stock deduction ledger never syncs.** Rows recorded here never reach the server. This is the audit trail for every dose that left a batch. |
| `sync_queue` | `SyncQueueEntity.kt:12`; no `SyncEntityDescriptor`; the Postgres RLS sweep explicitly excludes it (`20260816_security_hardening.sql:38`) | ✅ local-only by design — the upload queue is per-device |
| `widget_due_cache` | `WidgetDueEntity.kt:9`; no `SyncEntityDescriptor`; same exclusion at `20260816_security_hardening.sql:38` | ✅ local-only by design — derived cache for the home-screen widget |
| `backup_history` | `BackupHistoryEntity.kt:21` | ✅ local-only by design (cloud path is Cloudflare + R2) |

⚠️ **Naming collision that hides the above:** `InventoryDeductionEntity` maps to Room table
`inventory_deductions`, while `VaccineEntity.kt:76` declares a *separate* Room entity
`inventory_transactions` — which **is** the Postgres table name, and **is** synced
(`SyncUploader.kt:169`, `:178`). So `inventory_deductions` is a dead-end local queue.

### Naming and type inconsistencies

| Issue | Detail |
|---|---|
| `patient_visits.doctor_id` is `text` while `consultations.doctor_id` is also `text`, but `doctor_weekly_slots.doctor_id` is a `uuid` FK to `profiles(id)` | two different identity types for the same concept |
| `vaccine_batches.created_at` is `timestamptz` but `updated_at` is `text` | mixed temporal types in one table |
| `reminders.nxt_vaccine_id` is `text[]` in Postgres but a **JSON string** in Room | two different encodings of the same list |
| `expenses` PK is `id text` while every other table uses `id uuid` | |
| `profiles.deleted_by` is the **only** `deleted_by` that is `uuid`, not `text` (`20260925:20-23`) | |

## The soft-delete asymmetry

`20260925_business_soft_delete_columns.sql` adds `is_deleted`/`deleted_at`/`deleted_by` to
18 tables (`:30-36`) and **never touches a policy for those tables** — no policy gating them
mentions `is_deleted`. Policies elsewhere do (`profiles`:
`20260816_security_hardening.sql:130`, `:138`; the `expenses` staff checks:
`20261013120000_role_policy_carveouts.sql:43`, `:52`, `:66`), so a row soft-deleted there is
filtered by policy, not merely by the app. The predicate on all 16 clinic tables is
`public.is_active_staff()`, which checks the **actor's** profile, not the **row's**
soft-delete flag.

So soft delete is enforced **only in application code**. On the server, a "deleted" row is
indistinguishable from a live one for any client with SELECT access. Locally, the opposite
is true — see [[Room Database]] and [[Known Bugs]].

## Related

- [[Row Level Security]] — the policy matrix for these tables
- [[Supabase]] — triggers, edge functions, rate limiting
- [[Offline-First Architecture]] — how rows move between the two schemas
- [[Known Bugs]] — schema drift defects
- [[Backup and Restore]] — backup exports 22 of the 25 Room tables
