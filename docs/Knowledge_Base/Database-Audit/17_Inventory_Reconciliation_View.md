# Phase 1c — Inventory Reconciliation View

- **Date:** 2026-10-22
- **Migration:** `supabase/migrations/20261022_inventory_reconciliation_view.sql`
- **Test:** `supabase/tests/inventory_reconciliation_test.sql`
- **Status:** view and test verified on local PostgreSQL 18.2. **Not deployed. No data repaired.**

## 1. Purpose

`public.inventory_reconciliation` lists batches where `vaccine_batches.remaining_quantity` differs from the sum of that batch's `inventory_transactions.quantity`. It is a diagnostic surface for the DBA-01 corruption and for any future drift; it does not change stock.

## 2. Columns

| Column | Meaning |
| --- | --- |
| `batch_id` | `vaccine_batches.id` |
| `vaccine_id` | `vaccine_batches.vaccine_id` |
| `batch_number` | human-readable batch label |
| `recorded_quantity` | current `remaining_quantity` (coalesced to 0) |
| `ledger_quantity` | sum of that batch's ledger quantities (coalesced to 0) |
| `drift` | `recorded_quantity - ledger_quantity` |
| `ledger_rows` | number of ledger rows for the batch |
| `last_ledger_at` | latest `inventory_transactions.created_at` |

Only rows with `drift <> 0` are returned. `ledger_rows = 0` with a positive drift is usually an opening balance or a batch created outside the app, not necessarily corruption. `ledger_rows > 0` is the higher-priority case.

Query:

```sql
select *
from public.inventory_reconciliation
order by abs(drift) desc;
```

A monitor can alert on `select count(*) from public.inventory_reconciliation` without reading patient data.

## 3. Security

- Created with `security_invoker = true`, so the querying role's RLS policies on `vaccine_batches` and `inventory_transactions` apply. Without this option the view would read as its owner and bypass RLS.
- `anon` is explicitly revoked. `authenticated` and `service_role` receive `select` when those roles exist.
- The view contains no patient identifiers.
- The test asserts the `security_invoker=true` reloption, so a later `create or replace view` that drops it fails the test.

## 4. Verification executed

Local PostgreSQL 18.2, minimal schema matching the relevant columns, after applying both 2026-10-22 migrations:

```bash
psql ... -f supabase/migrations/20261022_fix_stock_trigger_update_arithmetic.sql
psql ... -f supabase/migrations/20261022_inventory_reconciliation_view.sql
psql ... -f supabase/tests/stock_trigger_arithmetic.sql
psql ... -f supabase/tests/inventory_reconciliation_test.sql
```

Results:

```text
CREATE VIEW
COMMENT
DO
NOTICE:  PASS: stock trigger arithmetic
NOTICE:  PASS: inventory reconciliation view
exit=0
```

The view test creates four batches:

| Batch | Recorded | Ledger | Expected view row |
| --- | ---: | ---: | --- |
| consistent | 10 | 10 | absent |
| drifted | 9 | 10 | `drift=-1`, `ledger_rows=1` |
| opening | 5 | 0 | `drift=5`, `ledger_rows=0` |
| empty | 0 | 0 | absent |

All assertions passed; the transaction was rolled back.

## 5. Limits

- This is an on-demand view, not a scheduled alarm. Supabase `pg_cron`/external monitoring is a deployment decision.
- It cannot tell a legitimate opening balance from corruption when `ledger_rows = 0`; physical stock or a device snapshot is needed.
- It does not repair anything. Phase 2 remains a separate approval gate after Phase 0 backup and review.
- No Supabase/RLS live test; only local PostgreSQL with no RLS policies.
- No `EXPLAIN` benchmark. The existing `(batch_id, timestamp)` index supports the join; the view scans all batches on each query, which is acceptable at clinic scale but not for a hot path.

## 6. Files changed

```text
A  supabase/migrations/20261022_inventory_reconciliation_view.sql
A  supabase/tests/inventory_reconciliation_test.sql
A  docs/Knowledge_Base/Database-Audit/17_Inventory_Reconciliation_View.md
```

No app code changed for this step. The migration is additive and unapplied.
