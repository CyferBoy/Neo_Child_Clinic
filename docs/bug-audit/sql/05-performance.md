# 05 — Performance

**Scope:** indexes declared in the repo, query shapes issued by the Android client, RLS evaluation, and unbounded-growth paths.
**Limitation:** no `EXPLAIN`, no row counts, no `pg_stat_statements`. Findings here are structural; every “missing index” claim is `Requires Verification` until measured on a clone.

---

## 1. Index inventory declared in migrations

| Index | Table | Columns | Source |
|---|---|---|---|
| `inventory_transactions_timestamp_desc_idx` | `inventory_transactions` | `(timestamp desc)` | `20261016…:81-82` |
| `inventory_transactions_vaccine_timestamp_idx` | `inventory_transactions` | `(vaccine_id, timestamp)` | `:83-84` |
| `inventory_transactions_batch_timestamp_idx` | `inventory_transactions` | `(batch_id, timestamp)` | `:85-86` |
| `inventory_transactions_type_idx` | `inventory_transactions` | `(transaction_type)` | `:87-88` |
| `audit_logs_patient_timestamp_idx` | `audit_logs` | `(patient_id, timestamp desc)` | `:89-90` |
| `audit_logs_timestamp_idx` | `audit_logs` | `(timestamp)` | `20261025…:24-25` |
| `profiles_email_lower_key` | `profiles` | `lower(email)` unique, conditional | `20261016…:96-100` |
| `reminders_natural_key` | `reminders` | `(patient_id, original_visit_id, vaccine_name, type)` unique, conditional | `:102-111` |
| `patients_clinic_id_key` | `patients` | `(patient_clinic_id) where not null` unique, conditional | `:113-122` |
| `user_devices_fcm_token_uidx` | `user_devices` | `(fcm_token)` unique | `20260816…:162-163` |
| `patient_visits_receipt_number_uidx` | `patient_visits` | `(receipt_number) where non-blank` unique | `20260824…:152-154` |
| `idx_doctor_weekly_slots_doctor_id` | `doctor_weekly_slots` | `(doctor_id)` | `20260911…:37` |
| `idx_doctor_weekly_slots_day` | `doctor_weekly_slots` | `(day_of_week)` | `:38` |
| `idx_doctor_slot_exceptions_doctor_id` | `doctor_slot_exceptions` | `(doctor_id)` | `:88` |
| `idx_doctor_slot_exceptions_date` | `doctor_slot_exceptions` | `(exception_date)` | `:89` |
| `idx_doctor_slot_exceptions_weekly_slot_id` | `doctor_slot_exceptions` | `(weekly_slot_id)` | `:90` |
| `idx_consultation_todos_doctor_id` | `consultation_todos` | `(doctor_id)` | `20260911…:141` |
| `idx_vaccination_todos_doctor_id` | `vaccination_todos` | `(doctor_id)` | `:142` |
| `idx_<table>_is_deleted` ×18 | 18 tables | `(is_deleted)` | `20260925…:53-55` |
| PK indexes | all tables | `id` / `user_id+window` | implicit |

**Not in the repo:** any index on `patients`, `patient_visits`, `vaccination_items`, `vaccines`, `vaccine_batches`, `consultations`, `finance_transactions`, `expenses`, `borrow_records`, `borrow_returns`, `waste_records`, `patient_notes`, `profiles` (other than the conditional email key), or `reminders` (other than the conditional natural key). Those tables were created outside the repo, so their indexes may exist but are invisible here.

---

## 2. Query shapes from the client

| Path | Query | Expected index need |
|---|---|---|
| Pull patients | `select * from patients where is_deleted=false order by id range(...)` | PK `id`; `is_deleted` filter is low-selectivity |
| Pull visits | `select * from patient_visits where is_deleted=false order by id range(...)` | PK `id` |
| Pull items | `select * from vaccination_items where is_deleted=false order by id range(...)` | PK `id` |
| Pull ledger | not pulled (`refreshInventory` reads vaccines + batches only) | n/a; `SQL-011` |
| Audit screen | `select * from audit_logs order by timestamp desc range(...)` | `audit_logs_timestamp_idx` (text) — supports the sort, but the sort is lexicographic (`SQL-029`) |
| Patient audit | `where patient_id = ? order by timestamp desc` | `audit_logs_patient_timestamp_idx` |
| Low stock | local Room query; server has no equivalent | n/a |
| Finance pull | `where is_deleted=false order by id range(...)` | PK `id` |
| Reconciliation | `select … from vaccine_batches b left join inventory_transactions t on … group by b.id` | `inventory_transactions_batch_timestamp_idx` helps the join; the view has no date predicate, so it aggregates the **entire** ledger every time |

`fetchAllPages` uses a 1,000-row page size and offset paging (`Pagination.kt:14-22`). On a table with 100k+ rows, deep offsets cost `O(offset)`; a keyset (`id > last_id`) would be cheaper. Clinic-scale data (tens of thousands) is unlikely to care. `Requires Verification`.

---

## 3. RLS evaluation cost

The baseline policies call `public.is_active_staff()` directly:

```sql
create policy "active staff select" on public.patients
  for select to authenticated using (public.is_active_staff());
```

`is_active_staff()` is `stable security definer` and runs a `select exists` against `profiles` (`20260929000000…:15-29`). Without wrapping the call in a scalar subquery — `(select public.is_active_staff())` — Postgres may evaluate it **per row** rather than once per query. On a 50k-row `audit_logs` scan that is 50k index lookups into `profiles`.

The planner can sometimes hoist a stable function; this needs `EXPLAIN (ANALYZE, BUFFERS)` on a clone. The fix is cheap and safe:

```sql
using ((select public.is_active_staff()))
```

`SQL-031` records this as part of the index/plan review.

---

## 4. Findings

### SQL-031 — Hot-path indexes and RLS plans are unverified

- **Severity:** Medium
- **Confidence:** Requires Verification
- **Object:** base-table indexes; RLS predicate evaluation; `inventory_reconciliation`
- **Workflow:** pull sync, audit listing, reconciliation, low-stock reporting
- **Root cause:** 15 tables were created outside the repo, so their indexes are unknown; the CSV has no index rows. The repo only indexes the tables added by migrations.
- **Failure scenario:** `patient_visits` has no `(patient_id, date_given)` index; a patient chart query scans the whole table. `vaccination_items` has no `(vaccination_id)` index; pulling a visit’s items scans. `vaccine_batches` has no `(vaccine_id, expiry_date)`; low-stock/expiry aggregation scans. RLS adds a per-row `profiles` lookup. None of this is proven without `EXPLAIN`.
- **Impact:** slow pulls on low-end devices and a large clinic dataset; sync timeouts that trigger partial groups (`SQL-009`).
- **Evidence:** index inventory above; CSV has no index rows; `20261016…:80-90` only covers ledger/audit.
- **Correction:** run the index audit query in §5 on a clone, add only the indexes the plans actually use, and wrap the RLS helper in a scalar subquery. Candidate indexes, in order:
  1. `patient_visits (patient_id, date_given desc)`
  2. `vaccination_items (vaccination_id)`
  3. `vaccine_batches (vaccine_id, expiry_date)`
  4. `finance_transactions (visit_id)` and `(transaction_date)`
  5. `inventory_transactions (patient_id)`
  6. `consultations (patient_id, date desc)`, `(visit_id)`
  7. `reminders (patient_id, due_date)`
  8. `borrow_records (batch_id)`, `borrow_returns (borrow_id)`
- **Regression test:** `EXPLAIN` snapshots for the top 10 queries; fail if a seq scan appears on a table above 10k rows without a stated reason.
- **Live check:** yes — `pg_stat_user_indexes`, `pg_stat_statements`, and the unused-index query.

### SQL-032 — Rate-limit table grows without bound

- **Severity:** Medium
- **Confidence:** Confirmed
- **Object:** `public.api_write_rate_limits`; `public.cleanup_write_rate_limits()`
- **Workflow:** every authenticated write
- **Root cause:** `cleanup_write_rate_limits()` is defined (`20260929001000…:77-92`) but **never called or scheduled**. A repo-wide grep finds only the definition. The insert path is one row per `(user_id, minute)` with activity (`:37-41`).
- **Failure scenario:** with 10 active staff writing for 8 hours, that is ~4,800 rows/day, ~1.75M rows/year, plus the PK index. The table is small per row but never vacuumed of old data, and every write upserts into it. Over years it becomes a measurable write amplifier and a backup-size issue.
- **Impact:** unnecessary growth; slower rate-limit upserts over time; backup bloat.
- **Evidence:** `:37-41,77-92`; no `cron.schedule('cleanup-write-rate-limits', …)` anywhere.
- **Correction:** add the cleanup to the existing `pg_cron` block in `20261025_audit_log_retention.sql`, e.g. `select cron.schedule('cleanup-write-rate-limits','*/30 * * * *','select public.cleanup_write_rate_limits()')`, guarded like the prune schedule. Or delete old rows inside `enforce_write_rate_limit()` opportunistically (1% of calls) — worse, because it adds work to the hot path.
- **Regression test:** insert a row with `window_started_at = now() - interval '3 hours'`, call the cleanup, assert 0 rows remain; insert a current row, assert it survives.
- **Live check:** yes — `select count(*), min(window_started_at) from api_write_rate_limits;`

---

## 5. Read-only queries to run on a clone

```sql
-- Table sizes and seq-scan ratios.
select relname, n_live_tup, seq_scan, idx_scan,
       round(100.0*seq_scan/nullif(seq_scan+idx_scan,0),1) as seq_pct
from pg_stat_user_tables
where schemaname='public'
order by n_live_tup desc;

-- Unused indexes (candidates for removal).
select relname as table, indexrelname as index, idx_scan
from pg_stat_user_indexes
where schemaname='public' and idx_scan = 0
order by relname;

-- Missing FK indexes: a FK whose child columns are not a prefix of any index.
select conrelid::regclass as child, conname, pg_get_constraintdef(oid)
from pg_constraint
where contype='f' and connamespace='public'::regnamespace
order by 1;

-- RLS plan check.
explain (analyze, buffers)
select * from public.audit_logs order by timestamp desc limit 100;

-- Reconciliation view cost.
explain (analyze, buffers) select * from public.inventory_reconciliation;
```

No query was run; there is no database client in this environment.

---

## 6. Performance-adjacent correctness issues

These are not counted as separate findings because the correctness finding already owns them:

- `prune_audit_logs` is non-sargable and can fail: `SQL-020`.
- Text timestamps sort lexicographically: `SQL-029`.
- `refreshInventory` omits the ledger, so local history guards are blind: `SQL-011`.
- Sync groups are per-item HTTP requests, so a large group is also a latency and rate-limit problem: `SQL-009`.
- The reconciliation view aggregates the full ledger with no time bound: included in `SQL-031`.
