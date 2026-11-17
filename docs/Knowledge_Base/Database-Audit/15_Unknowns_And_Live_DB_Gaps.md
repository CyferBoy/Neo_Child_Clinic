# Unknowns and Live-DB Gaps

Everything here requires either live Supabase access or an Android SDK/device. None of it was executed during this audit. Queries are read-only; run them against a **read replica or a maintenance snapshot** where possible, and redact results before sharing.

## 1. Environment limits

| Limit | Consequence |
| --- | --- |
| No live Supabase credentials/connection | trigger installation, FKs, policies, data drift unverified |
| No Android SDK/emulator | no Gradle unit or instrumentation test executed |
| No Supabase CLI/Docker | no disposable Supabase run of migrations/policies. During the 2026-10-22 remediation a local PostgreSQL 18.2 instance was used for the DBA-01 trigger test only; it did not include Supabase schemas, grants or RLS. |
| Only Room schema v33 exported | v31/v32 migration tests cannot run from the repository |
| No production data access | findings are code/schema-file confirmed, not data-confirmed |

## 2. Blocking unknowns

| ID | Unknown | Why it matters | How to resolve |
| --- | --- | --- | --- |
| U-01 | Is `update_batch_stock()` installed, and which body/trigger count is live? | DBA-01 and double-application | trigger/function queries below |
| U-02 | Does `inventory_transactions.visit_id` have an FK to `patient_visits`? | DBA-02 severity and delete behavior | `pg_constraint` query |
| U-03 | Which migrations have actually been applied? | every RLS/trigger finding | migration ledger query |
| U-04 | Are all RLS policies exactly as the migrations describe? | DBA-08 | `pg_policies` export |
| U-05 | How much historical stock drift exists? | repair scope | reconciliation query |
| U-06 | How many finance rows have a wrong `transaction_date`? | repair scope, timezone impact | type-aware finance query |
| U-07 | Are there orphan reminders/consultations/items? | cleanup scope | orphan queries |
| U-08 | What is the device queue backlog? | retry/corruption exposure | app debug screen or queue export |
| U-09 | Are soft-delete columns still present and populated? | pull filters/tombstones | column + count queries |
| U-10 | What are the storage policies for patient documents? | DBA-08 exposure | `storage.buckets` + policies |
| U-11 | Are edge functions deployed from the same commit? | staff provisioning behavior | Supabase dashboard/CLI |
| U-12 | Do duplicate reminders violate the intended server natural key? | DBA-19 | duplicate query |
| U-13 | Is the Cloudflare backup Worker/R2 config consistent with `CloudBackupApi` (auth, retention, size limits)? | backup confidentiality/availability | Worker source + R2 config review |

## 3. Read-only verification SQL

### 3.1 Applied migrations

```sql
-- Supabase CLI migration ledger (if present)
select version, name
from supabase_migrations.schema_migrations
order by version;
```

If the ledger is missing, compare migration filenames against deployed objects instead of assuming.

### 3.2 Trigger state

```sql
select c.relname              as table_name,
       t.tgname               as trigger_name,
       p.proname              as function_name,
       t.tgenabled            as enabled,
       pg_get_triggerdef(t.oid) as definition
from pg_trigger t
join pg_class c on c.oid = t.tgrelid
join pg_proc  p on p.oid = t.tgfoid
where c.relname in ('vaccine_batches', 'inventory_transactions')
  and not t.tgisinternal
order by c.relname, t.tgname;

select pg_get_functiondef('public.update_batch_stock()'::regprocedure);
```

Expected for the audited code: **exactly one** non-internal trigger on `vaccine_batches`. More than one means double application; zero means stock never moves.

### 3.3 Foreign keys

```sql
select conrelid::regclass as table_name,
       conname,
       contype,
       pg_get_constraintdef(oid) as definition
from pg_constraint
where conrelid in ('public.inventory_transactions'::regclass,
                   'public.patient_visits'::regclass,
                   'public.reminders'::regclass,
                   'public.consultations'::regclass)
order by table_name, conname;
```

Specifically check whether `inventory_transactions.visit_id` references `patient_visits(id)` and whether it is `ON DELETE SET NULL`, `CASCADE`, `RESTRICT`, or absent.

### 3.4 Columns and types

```sql
select table_name, column_name, data_type, is_nullable, column_default
from information_schema.columns
where table_schema = 'public'
  and table_name in (
    'patients','patient_visits','vaccination_items','reminders',
    'personal_vaccine_reminders','consultations','finance_transactions',
    'expenses','vaccines','vaccine_batches','inventory_transactions',
    'waste_records','borrow_records','borrow_returns','audit_logs',
    'profiles','receipt_sequences'
  )
order by table_name, ordinal_position;
```

Use this to confirm date column types (`text` vs `date`) before running any date-comparison query.

### 3.5 Policies and RLS status

```sql
select schemaname, tablename, policyname, permissive, roles, cmd, qual, with_check
from pg_policies
where schemaname = 'public'
order by tablename, policyname;

select c.relname, c.relrowsecurity, c.relforcerowsecurity
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind = 'r'
order by c.relname;
```

Compare the output with `08_Security_And_RLS.md`. Pay particular attention to surviving `active staff insert/update/delete` policies on clinical/finance tables.

### 3.6 Stock reconciliation (diagnostic)

```sql
-- Diagnostic only: initial stock may have been set without a ledger row.
select b.id,
       b.batch_number,
       b.remaining_quantity                         as recorded,
       coalesce(sum(t.quantity), 0)                  as ledger_total,
       b.remaining_quantity - coalesce(sum(t.quantity), 0) as drift
from vaccine_batches b
left join inventory_transactions t on t.batch_id = b.id
group by b.id, b.batch_number, b.remaining_quantity
having b.remaining_quantity <> coalesce(sum(t.quantity), 0)
order by abs(b.remaining_quantity - coalesce(sum(t.quantity), 0)) desc;
```

If legitimate opening balances exist without ledger rows, adjust the baseline in a reviewed query rather than treating every drift as corruption.

The same diagnostic is available as `public.inventory_reconciliation` after the 2026-10-22 migration (`17_Inventory_Reconciliation_View.md`). It lists only mismatched batches and includes `ledger_rows` so a zero-ledger opening balance is distinguishable from a real mismatch.

### 3.7 Finance date mismatch

Run only after 3.4 confirms the types.

```sql
-- Adapt the date expression to the actual storage format.
select f.id, f.visit_id, f.transaction_date, v.date_given
from finance_transactions f
join patient_visits v on v.id = f.visit_id
where f.transaction_date is distinct from
      to_char((v.date_given::timestamptz at time zone 'Asia/Kolkata')::date, 'YYYY-MM-DD')
limit 200;
```

If `date_given` is stored as text in `d MMM yyyy`, parse it explicitly (or export the rows and evaluate in Kotlin) instead of casting blindly.

### 3.8 Orphans

```sql
select count(*) as reminders_without_patient
from reminders r
left join patients p on p.id = r.patient_id
where p.id is null;

select count(*) as reminders_without_visit
from reminders r
left join patient_visits v on v.id = r.original_visit_id
where r.original_visit_id is not null and v.id is null;

select count(*) as consultations_without_patient
from consultations c
left join patients p on p.id = c.patient_id
where p.id is null;

select count(*) as items_without_visit
from vaccination_items i
left join patient_visits v on v.id = i.visit_id
where v.id is null;
```

### 3.9 Duplicate reminders (DBA-19)

```sql
select patient_id, original_visit_id, vaccine_name, type, count(*) as n
from reminders
group by patient_id, original_visit_id, vaccine_name, type
having count(*) > 1
order by n desc;
```

### 3.10 Soft-delete state

```sql
select table_name, column_name
from information_schema.columns
where table_schema='public' and column_name in ('is_deleted','deleted_at','deleted_by')
order by table_name, column_name;

-- Example counts; repeat per table that still has the columns.
select count(*) filter (where is_deleted) as soft_deleted,
       count(*)                          as total
from reminders;
```

### 3.11 Profiles/roles

```sql
select role, is_active, is_deleted, count(*)
from profiles
group by role, is_active, is_deleted
order by role, is_active, is_deleted;
```

Redact names/emails; role counts are enough.

### 3.12 Table size and growth (estimates)

```sql
select relname, n_live_tup, n_dead_tup,
       pg_size_pretty(pg_total_relation_size(relid)) as total_size
from pg_stat_user_tables
where schemaname='public'
order by pg_total_relation_size(relid) desc;
```

### 3.13 Patient-document bucket

```sql
select id, name, public, file_size_limit, allowed_mime_types
from storage.buckets;

select bucket_id, name, owner, created_at, updated_at, last_accessed_at
from storage.objects
where bucket_id = 'patient-docs'
order by created_at desc
limit 20;
```

Object paths/metadata may contain PII; redact before sharing. Cloud backups are not in Supabase Storage; inspect `cloudflare/backup-worker/` and the R2 binding configuration separately.

## 4. Device-side unknowns

Export from a debug build (no production mutation):

- `sync_queue` grouped by `entityName`, `status`, `retryCount`;
- oldest `createdAt` per status;
- failed items with redacted `lastError`;
- per-table row counts;
- `vaccine_batches` local vs server `remaining_quantity`;
- last successful pull timestamp per repository (currently not stored; derive from logs if available).

Do not clear a queue to “fix” a device before exporting it; the queue is evidence.

## 5. Test-environment unknowns

- `ANDROID_HOME` / SDK location on a machine that can run `./gradlew testDebugUnitTest` and `connectedDebugAndroidTest`.
- Availability of a disposable Supabase/Postgres instance for trigger and RLS tests.
- Whether CI currently runs any SQL tests from `supabase/tests`.
- Whether the `@Ignore`d `ExpenseMigrationTest` can be enabled by adding the missing schema.

## 6. Credential/PII redaction checklist

Before attaching any result to a report or issue:

- Remove `apikey`, `Authorization`, JWT, service-role keys, database URLs and passwords.
- Remove patient names, phone numbers, addresses, clinic IDs, receipt numbers and free-text notes.
- Replace UUIDs with stable placeholders (`patient-1`, `visit-1`) when the mapping is not needed.
- Strip storage object paths that contain names.
- Keep counts, roles, policies, constraint definitions and function bodies; they are the evidence.

## 7. Decision log (to fill during Phase 0)

| Decision | Options | Chosen | Date | Owner |
| --- | --- | --- | --- | --- |
| Inventory ledger strategy | fix UPDATE vs append-only compensating rows | | | |
| Historical stock repair | repair / accept / partial | | | |
| Delete convergence | tombstones vs full ID diff | | | |
| Reminder identity | keep dual id vs client-generated single id | | | |
| Write roles | matrix in `13_Target_Architecture.md` | | | |
| Stock history | pull ledger vs online-only label | | | |
| Audit history | cache vs online-only label | | | |

## 8. What would change the conclusions

- A live trigger body already different from `20261017` would invalidate DBA-01's live severity (but not the repository defect).
- Absence of the visit FK would reduce DBA-02 to orphan-ledger hygiene.
- Additional live RLS policies could narrow DBA-08; missing policies could widen it.
- Device evidence of an empty/healthy queue would reduce exposure to DBA-01/DBA-09 but not remove the defect.

Until these are checked, treat DBA-01 as **confirmed in code and potentially active in production**, and DBA-02/DBA-08/DBA-24 as **confirmed in migrations, live state unverified**.
