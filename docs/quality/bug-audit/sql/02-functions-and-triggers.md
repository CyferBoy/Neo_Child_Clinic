# 02 — Functions and Triggers

**Scope:** every function and trigger defined in `supabase/migrations/`, plus the behaviour they impose on the Android sync path.
**Method:** read the final definition of each object in migration order; earlier definitions are superseded. No function was executed.
**Status (2026-10-26):** SQL-006, SQL-018 and SQL-020 are fixed in the working tree and verified on PostgreSQL 16.15 — see `14-fix-log.md`. The analysis below is `b91142a`.

---

## 1. Effective function set

| Function | Final definition | Volatility / security | Called by |
|---|---|---|---|
| `set_updated_at()` | `20260816_security_hardening.sql:46` | invoker, default volatile | `set_updated_at` trigger on every `public` table |
| `is_admin()` | `20260816_security_hardening.sql:75` | `stable`, `security definer`, `search_path=public` | profile trigger, policies |
| `protect_profile_privileged_fields()` | `20261013_profiles_privileged_fields_guard.sql:28` | `security definer`, `search_path=public` | `profiles` before-update |
| `neo_parse_visit_date(text)` | `20260824_receipt_numbering.sql:41` | `immutable` | receipt trigger |
| `neo_financial_year_label(date)` | `20260824_receipt_numbering.sql:62` | `immutable` | receipt allocator |
| `neo_next_receipt_number(date)` | `20260824_receipt_numbering.sql:81` | `security definer`, `search_path=public` | receipt trigger only |
| `neo_assign_receipt_number()` | `20260824_receipt_numbering.sql:109` | `security definer`, `search_path=public` | `patient_visits` before insert/update |
| `is_active_staff()` | `20260929000000_security_baseline.sql:15` | `stable`, `security definer`, `search_path=public, auth` | 20 tables × 4 policies |
| `neo_current_staff_label()` | `20260825_clinic_rls_and_accountability.sql:55` | `security definer`, `search_path=public` | accountability trigger |
| `neo_stamp_accountability()` | `20260825_clinic_rls_and_accountability.sql:73` | `security definer`, `search_path=public` | 18 tables |
| `notify_todays_patient()` | `20260911_doctor_availability_slots.sql:153` | `security definer`, `search_path=public, extensions, net` | two todo triggers |
| `enforce_write_rate_limit()` | `20260929001000_write_rate_limit.sql:16` | `security definer`, `search_path=public, auth` | 20 tables |
| `cleanup_write_rate_limits()` | `20260929001000_write_rate_limit.sql:77` | `security definer` | **never called or scheduled** |
| `prune_audit_logs()` | `20261025_audit_log_retention.sql:7` | `security definer`, `search_path=public` | nightly `pg_cron` (optional) |
| `update_batch_stock()` | `20261022_fix_stock_trigger_update_arithmetic.sql:33` | invoker (default) | one after-trigger on `inventory_transactions` |

Every `security definer` function sets an explicit `search_path` — good. The two exceptions (`update_batch_stock`, `set_updated_at`) are invoker functions, so they inherit the caller’s path; both reference `public.`-qualified tables, so the risk is low.

---

## 2. `update_batch_stock()` — the highest-risk function

### 2.1 Final algorithm (`20261022…:33-95`)

| Event | Branch | Effect |
|---|---|---|
| `INSERT` | `:40-42` | `delta = new.quantity` on `new.batch_id` |
| `UPDATE`, same batch | `:45-48` | `delta = new.quantity - old.quantity` |
| `UPDATE`, batch moved | `:49-58` | **undo** `old.quantity` on `old.batch_id` inline, then `delta = new.quantity` on `new.batch_id` |
| `DELETE` | `:61-63` | `delta = -old.quantity` on `old.batch_id` |
| No-op guard | `:66-68` | returns when `v_new_batch is null or v_delta = 0` |
| Destination lock | `:71-75` | `select … for update` |
| Missing batch | `:77-79` | raises |
| Negative guard | `:81-86` | raises `check_violation` |
| Apply | `:88-91` | `remaining_quantity = v_remaining + v_delta` |

The same-batch and insert/delete paths are correct and concurrency-safe: the row lock plus the guard serialise competing movements.

### SQL-006 — Moved-batch UPDATE bypasses the lock and the negative guard

- **Severity:** Critical
- **Confidence:** Confirmed (code); live exploitation depends on a move operation
- **Object:** `public.update_batch_stock()`, `20261022_fix_stock_trigger_update_arithmetic.sql:49-56`
- **Workflow:** editing a ledger row’s `batch_id` (vaccination edit that reassigns a batch, borrow return to a different batch, manual correction)
- **Root cause:** the “undo” on the old batch is a bare `UPDATE … SET remaining_quantity = remaining_quantity - old.quantity` executed **before** the `SELECT … FOR UPDATE` and **without** a negative check:
  ```sql
  if old.batch_id is not null then
      update public.vaccine_batches
         set remaining_quantity = coalesce(remaining_quantity, 0) - old.quantity,
             updated_at = now()
       where id = old.batch_id;
  end if;
  ```
- **Failure scenario:** batch A has 3 doses left; ledger row is a purchase of `+10` (or a return of `+5`) and is moved to batch B. The undo computes `3 - 10 = -7` on A, commits, then the destination path checks B only. Stock A is now negative. A concurrent move against the same old batch has no row lock, so two transactions read the same `remaining_quantity` and one update is lost.
- **Impact:** negative stock, lost updates, wrong low-stock/expiry counts, and a batch that can no longer satisfy the negative guard for legitimate deductions.
- **Evidence:** `20261022…:51-56` (inline update), `:70-86` (lock and guard apply only to `v_new_batch`); the test file never moves a **positive** row off a batch with insufficient stock (`stock_trigger_arithmetic.sql:74-84` moves a `-4` vaccination only).
- **Correction:** resolve the old batch through the same locked/guarded path:
  ```sql
  -- inside the moved branch
  v_old_batch := old.batch_id;
  v_old_delta := -old.quantity;   -- then lock + guard + update, same as destination
  ```
  Implement as two guarded applications (old then new), or compute both in one statement with a `case`. Do not leave the inline update.
- **Regression test:** batch A = 3, insert purchase `+10`, move it to B, expect A = 3 or an explicit `check_violation`; never `-7`. Add a concurrent two-session test.
- **Live check:** yes — query `vaccine_batches` for `remaining_quantity < 0` before and after deploying the fix.

### SQL-007 — DELETE branch can block legitimate ledger corrections

- **Severity:** High
- **Confidence:** Probable (logic proven; frequency depends on data)
- **Object:** `update_batch_stock()` DELETE branch, `:61-63` and `:81-86`
- **Workflow:** deleting a purchase/return/adjustment ledger row after stock has been consumed
- **Root cause:** deleting any row applies `-old.quantity`. For a positive row (purchase `+10`) on a batch that has since been dispensed down to 3, the guard sees `3 - 10 = -7` and raises. The delete is blocked even though the batch legitimately no longer holds those doses.
- **Failure scenario:** staff corrects a mis-entered purchase by deleting it; the delete fails with `check_violation`, the app surfaces an opaque sync failure, and the row stays. The same applies to deleting a vaccination `-2` row when the batch has been manually adjusted below 2.
- **Impact:** corrections cannot be applied; ledger and physical stock diverge; operators work around it with ad-hoc SQL.
- **Evidence:** `:61-63`, `:81-86`; no compensating “allow negative on delete” flag or reconciliation path in any migration.
- **Correction:** decide the invariant explicitly. Either (a) allow the DELETE to drive `remaining_quantity` negative and surface it in `inventory_reconciliation` (audit-first), or (b) require a compensating adjustment row before deleting and return a typed, actionable error. Option (a) is the smaller change and matches the view’s purpose.
- **Regression test:** batch 3, purchase row `+10`, delete it, assert either `-7` with a reconciliation row or a documented, typed rejection.
- **Live check:** yes — count how many deletes currently fail (PostgREST logs) before choosing.

### 2.2 Interaction with the sync path

The trigger is correct only if exactly one ledger row describes each movement. The Android path does not always guarantee that:

- `SyncUploader.uploadBatch` sends `remaining_quantity = 0` on CREATE (`SyncUploader.kt:635-636`). The paired PURCHASE row is supposed to restore it. On a lost-response retry, the batch CREATE is retried as an **UPDATE** (the row exists), so `fields.remove("remaining_quantity")` (`:637-638`) leaves the column untouched — but only if the first attempt committed. If the first attempt did **not** commit and a retry is treated as a create, stock is reset to 0. See `SQL-008` in `06-data-integrity-and-concurrency.md`.
- The ledger CREATE is uploaded with `ignoreDuplicates = true` (`SyncUploader.kt:564-568,665-667`). A retry after a committed insert is a no-op — correct. But a **corrected** ledger row re-sent as CREATE is also a no-op, so a correction that should change `quantity` is silently dropped.
- Sync groups are not transactions (`SyncRepositoryImpl.kt:272-333`). A group can commit the batch CREATE, fail on the transaction, and leave stock at 0 until a later retry.

---

## 3. Receipt numbering

### 3.1 Behaviour

- `neo_assign_receipt_number()` runs before insert/update on `patient_visits`.
- On INSERT, it only allocates when `NEW.receipt_number` is null or blank (`20260824…:116-119`). A non-blank client value is **kept**.
- On UPDATE, an existing non-blank value is preserved (`:120-124`); a blank one is allocated (`:125-130`).
- `neo_next_receipt_number` uses `insert … on conflict do update … returning`, which is atomic per financial year (`:95-100`).
- A partial unique index enforces uniqueness of non-blank values (`:152-154`).

### SQL-018 — Client can choose or forge a receipt number

- **Severity:** High
- **Confidence:** Confirmed
- **Object:** `public.neo_assign_receipt_number()`, `20260824_receipt_numbering.sql:116-119`
- **Workflow:** visit create/update via PostgREST
- **Root cause:** the trigger treats any non-blank client value as authoritative. Uniqueness is enforced, but format, sequence continuity, and financial-year membership are not.
- **Failure scenario:** a client (or a crafted PostgREST request from a valid staff token) inserts `receipt_number = 'NEO-2025/26-999999'` or `'REFUND-001'`. The row is accepted. The next legitimate allocation continues from the sequence’s own counter, so the receipt series now has a gap and an out-of-series value. A duplicate is rejected by the unique index, which reveals whether a number exists (an oracle).
- **Impact:** financial audit trail integrity; receipts can be backdated/forwarded or made to collide deliberately; reconciliation with `receipt_sequences` is no longer possible.
- **Evidence:** `:116-119`; unique index `:152-154`; no `check` constraint on the format.
- **Correction:** on INSERT, always overwrite `NEW.receipt_number := public.neo_next_receipt_number(...)`, ignoring client input. If an import path genuinely needs to preserve historical numbers, gate it behind a separate `security definer` function callable only by `service_role`, not a client-writable column.
- **Regression test:** insert with `receipt_number = 'FORGED'`, assert the stored value matches `^NEO-[0-9]{4}/[0-9]{2}-[0-9]{6}$` and equals the next sequence value; assert the sequence advanced once.
- **Live check:** yes — find existing non-conforming values before enforcing a format check.

### 3.2 Sequence monotonicity

`receipt_sequences.last_number` is only incremented; it is never decremented on delete. That is intentional (`:22-23`) and correct for “no reuse”. But because a client can bypass allocation (`SQL-018`), the sequence can lag the set of issued numbers, and the “never reused” property is only true for trigger-allocated numbers.

---

## 4. Audit retention

### SQL-020 — `prune_audit_logs()` can fail permanently on malformed timestamps

- **Severity:** High
- **Confidence:** Confirmed
- **Object:** `public.prune_audit_logs()`, `20261025_audit_log_retention.sql:7-20`
- **Workflow:** nightly `pg_cron` job `prune-audit-logs`
- **Root cause:** the predicate filters with a regex and then casts the same text column:
  ```sql
  where timestamp ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}[ T]'
    and timestamp::timestamptz < now() - interval '1 year'
  ```
  The regex accepts `2025-13-45 99:99:99`, which passes the filter and then raises `22007`/`22008` on cast. SQL does not guarantee the order of predicate evaluation, so even a “valid” regex match can be cast before the filter is applied.
- **Failure scenario:** one malformed audit row (possible because `timestamp` is client-supplied text, `20260824`-era writes, or a manual edit) makes the nightly job raise. `pg_cron` logs the failure and retries the same statement the next night; the row is never deleted, and pruning stops for the whole table.
- **Impact:** unbounded `audit_logs` growth; retention policy not met; repeated cron failures; the `audit_logs_timestamp_idx` (`:24-25`) cannot be used because the cast is not sargable.
- **Evidence:** `20261025…:13-19`; `audit_logs.timestamp` is `text` (CSV) with default `(now())::text`; no `check` constraint; the function is `sql`, not `plpgsql`, so it cannot catch the cast error.
- **Correction:** make the function `plpgsql` and delete in a guarded loop, or normalise at write time:
  ```sql
  -- preferred: store a real timestamptz
  alter table public.audit_logs add column occurred_at timestamptz;
  update public.audit_logs set occurred_at = ... where ...;  -- guarded
  -- then prune on occurred_at, with an index
  ```
  Short term, use `pg_input_is_valid(timestamp, 'timestamptz')` (Postgres 16+) or an exception block per row. Add a `check` constraint after cleaning the data.
- **Regression test:** insert `timestamp = '2025-13-45 99:99:99'`, call `prune_audit_logs()`, assert it returns without error and the malformed row is either deleted or retained deterministically.
- **Live check:** yes — `select timestamp from audit_logs where not pg_input_is_valid(timestamp,'timestamptz') limit 20;` (Postgres 16+).

### 4.1 Cron availability is silent

`20261025…:30-52` wraps `create extension pg_cron` and `cron.schedule` in exception blocks that only `raise notice`. If pg_cron is unavailable, the migration succeeds and **nothing prunes**. There is no health check. This is recorded under `SQL-020`’s impact, not as a separate finding.

---

## 5. Accountability triggers

`neo_stamp_accountability()` (`20260825…:73-91`):

- INSERT: `created_by = coalesce(label, client value)`, `updated_by = coalesce(label, client value)`.
- UPDATE: `created_by = OLD.created_by` (immutable), `updated_by = coalesce(label, client value)`.

Triggers are attached to:

- the 12 full-CRUD tables (`20260825…:137-141`),
- `audit_logs` (`20260825…:163-166`),
- `consultation_todos`, `vaccination_todos` (`20260825_patient_todos.sql:67-70`),
- `personal_vaccine_reminders` (`20260901…:51-52`),
- `borrow_returns` (`20260902…:38-39`).

**Not attached to:** `expenses`, `doctor_weekly_slots`, `doctor_slot_exceptions`. Those tables have `created_by`/`updated_by` columns (migrations 10 and 11) but accept client-supplied values. This is part of `SQL-019` in `03-rls-and-security.md`.

Because the function uses `coalesce(label, NEW.updated_by)`, a client-supplied non-null `updated_by` survives even on stamped tables when the authenticated label is null (service role). For `authenticated` writes the label is normally non-null, so the client value is overwritten. Fine for now; worth a test.

---

## 6. Profile privilege guard

`protect_profile_privileged_fields()` (`20261013…:28-48`) blocks a non-admin updating their own `id`, `role`, `is_active`, `is_deleted`, `deleted_by`, `deleted_at`, `email`. It explicitly does not block:

- any column added later (comment at `:15-18` admits this),
- an admin changing another user’s profile (intended),
- service-role writes (intended).

`SQL-017` records the allowlist fragility. The function is `security definer` and calls `is_admin()`, which is also definer; no recursion because `is_admin()` reads `profiles` under definer rights.

---

## 7. Rate-limit trigger

`enforce_write_rate_limit()` (`20260929001000…:16-48`):

- bypasses `service_role`,
- raises `42501` for anonymous,
- upserts a per-user/per-minute counter and raises `57014` above 600,
- is attached to 20 tables (`:59-71`), including `audit_logs`.

Consequences:

- A patient delete that touches dozens of child rows plus audit rows is dozens of separate PostgREST writes, all counted. A bulk sync or a large delete can hit 600 in one minute and fail mid-group. Because groups are not atomic (`SQL-009`), earlier rows stay committed.
- `api_write_rate_limits` is never cleaned: `cleanup_write_rate_limits()` is defined (`:77-92`) but no migration schedules it and no application code calls it (repo-wide grep finds only the definition). One row per active user-minute accumulates indefinitely. `SQL-032`.
- The trigger fires on `audit_logs` inserts, so writing an audit row consumes rate-limit budget. Audit logging a failed operation can itself fail under load.

**Correction:** call `cleanup_write_rate_limits()` from the same `pg_cron` schedule as `prune_audit_logs` (or add it to that function), and raise the limit or batch writes. Do not exempt `audit_logs` unless audit writes are made asynchronous.

---

## 8. Notification trigger

`notify_todays_patient()` (`20260911…:153-199`):

- inserts into `todo_notification_events` with `on conflict do nothing`; if the event already exists, returns early (idempotent),
- reads `app.settings.supabase_functions_url` and `app.settings.todo_notifier_secret` from GUCs; if unset, warns and returns,
- calls `net.http_post` synchronously (queues an async request in pg_net).

Risks:

- `todo_notification_events` has no `CREATE TABLE` in the repo (`SQL-001`/`SQL-002`). A fresh environment cannot create the trigger’s target.
- The `net` schema/extension is assumed. If `pg_net` is absent, the function compiles but `net.http_post` fails at runtime; the insert into the event table is in the same transaction, so the todo insert fails too — a todo cannot be created when the notifier is misconfigured, despite the code’s intent to degrade gracefully. **Requires Verification** on a disposable database with pg_net absent.
- The secret is read from a GUC; if it leaks via `current_setting` to a caller with SQL access, it is exposed. Only `security definer` functions read it, and the function is not granted to clients — acceptable.

This is recorded as an operational dependency under `SQL-001`; no separate finding ID.

---

## 9. Trigger ordering hazards

Postgres fires triggers of the same timing in **name order** (alphabetical). On `patient_visits`:

- `neo_assign_receipt_number` (before insert/update),
- `neo_stamp_accountability` (before insert/update),
- `set_updated_at` (before update),
- `trg_patient_visits_write_rate_limit` (before insert/update/delete).

Alphabetically: `neo_assign…`, `neo_stamp…`, `set_updated_at`, `trg_…`. The receipt trigger runs first, then accountability, then timestamp. That is the intended order. No conflict found.

On `inventory_transactions`:

- `inventory_transactions_update_batch_stock` (after insert/update/delete),
- `neo_stamp_accountability` (before),
- `set_updated_at` (before update),
- `trg_inventory_transactions_write_rate_limit` (before).

The stock trigger is AFTER, so it sees the final row. Fine.

One subtlety: `update_batch_stock` returns `null` in an AFTER trigger. The return value is ignored for statement-level effects, so this is harmless, but it is unconventional; a reader may mistake it for a BEFORE trigger cancellation.

---

## 10. Functions that should not exist / should be consolidated

- Five stock-trigger migrations define the same function. Only the last is authoritative; the intermediate ones are dead weight and make review error-prone. Keep one and mark the rest as superseded in comments.
- `cleanup_write_rate_limits()` is dead code until scheduled (`SQL-032`).
- `get_my_role()` was dropped with `CASCADE` (`20261021000000…:26`), taking 14 dependent policies with it. Whether all 14 were insecure is unverified (`SQL-016`). Do not recreate it; verify the policies that vanished were replaced by the baseline.
