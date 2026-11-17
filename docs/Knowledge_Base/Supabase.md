# Supabase

> Backend: migrations, triggers, edge functions, and rate limiting. Companion notes:
> [[Database Schema]] · [[Row Level Security]] · [[Offline-First Architecture]]

## Shape

```
supabase/
├── config.toml                       425+ lines; JWT config per function
├── migrations/                       29 .sql + 1 stale CSV catalog
│                                    ✅ all 29 tracked in git (fixed in f8e5d8a)
├── functions/
│   ├── manage-staff/index.ts         257 lines — privileged `profiles` writer; app self-edits via `SyncUploader.kt:572`
│   └── notify-update/index.ts        166 lines — FCM fan-out
├── tests/rls_role_test.sql
├── verify_security.sql
└── .gitignore
```

⚠️ **Tracked, but not replayable from a clean clone.** The 29 migrations contain only 9
`create table` statements; 18 tables are referenced and altered but never created — the 15
legacy base tables (`patients`, `patient_visits`, `patient_notes`, `vaccination_items`,
`vaccines`, `vaccine_batches`, `inventory_transactions`, `waste_records`, `borrow_records`,
`reminders`, `consultations`, `finance_transactions`, `audit_logs`, `profiles`,
`user_devices`) plus `sync_queue`, `widget_due_cache` and `todo_notification_events`. A clean
clone cannot rebuild this schema; the full DDL exists only outside the repository, in an
untracked local artifact (`.artifacts/f21d0dfd-…/full_project_schema.sql.artifact.md`).

Client wiring: `core/di/SupabaseModule.kt` provides 7 singletons — client, `Auth`,
`CurrentUserProvider`, `Functions`, `Postgrest`, `Realtime`, `Storage`.

Credentials come from `.env.local` → `BuildConfig` (`app/build.gradle.kts:13-22`).
✅ `.env.local` is gitignored, untracked, and has **never** been committed in any branch —
verified with `git log --all -- .env.local` (empty) and `git ls-files | grep -iE "env|key|secret"`
(3 benign matches — no secrets). No `eyJ…` JWT or `supabase.co` literal appears anywhere in
`app/src/main`.
The anon key shipping in the APK is by design; RLS is the control.

## Security posture — read this first

✅ **Every `SECURITY DEFINER` function authored inside `supabase/migrations/` pins
`search_path`.** Verified exhaustively across all ten:

| Function | Defined | `set search_path` | Grants |
|---|---|---|---|
| `is_admin()` | 08-16, replaced 10-01 | `public` | `revoke all from public`; grant to `authenticated` |
| `protect_profile_privileged_fields()` | 08-16, replaced 10-13 | `public` | trigger-only |
| `is_active_staff()` | 08-25, replaced 09-29 | `public, auth` | `revoke all from public` |
| `neo_current_staff_label()` | 08-25 | `public` | `revoke all from public, authenticated, anon` |
| `neo_stamp_accountability()` | 08-25 | `public` | trigger-only |
| `neo_next_receipt_number(date)` | 08-24 | `public` | `revoke all from public, authenticated, anon` |
| `neo_assign_receipt_number()` | 08-24 | `public` | trigger-only |
| `notify_todays_patient()` | 09-11 | `public, extensions, net` | **never triggered** |
| `enforce_write_rate_limit()` | 09-29 | `public, auth` | `revoke all from public` |
| `cleanup_write_rate_limits()` | 09-29 | `public` | `revoke all from public` — **nothing calls it** |

All internal references are schema-qualified (`auth.uid()`, `public.profiles`), so there is
no unqualified-name shadowing surface even if a schema were writable.

⚠️ **The one unpinned `SECURITY DEFINER` is not in this repository.**
`.artifacts/f21d0dfd-…/full_project_schema.sql.artifact.md:13-21` defines
`public.get_my_role()` with **no `set search_path`**, and **14** legacy policies reference it
(`.artifacts/f21d0dfd-…/full_project_schema.sql.artifact.md:303-345`).
It is gitignored, so its live status is unverifiable from this repo. See [[Known Bugs]].

## Triggers

| Trigger | Table | Timing | Effect |
|---|---|---|---|
| `set_updated_at()` | every table that existed at 08-16 | BEFORE UPDATE | sets `NEW.updated_at = now()` — `security invoker`, unpinned, but body uses only `pg_catalog.now()` |
| `protect_profile_privileged_fields()` | `profiles` | BEFORE UPDATE | **raises** if a non-admin self-update changes `id`, `role`, `is_active`, `is_deleted`, `deleted_by`, `deleted_at`, or `email` |
| `neo_assign_receipt_number()` | `patient_visits` | BEFORE INSERT/UPDATE | assigns when blank; **pins the old value on UPDATE**. Race-free via `ON CONFLICT … RETURNING` |
| `neo_stamp_accountability()` | 16 business tables + `audit_logs` (INSERT only) — 17 triggers | BEFORE INSERT/UPDATE | overwrites client-supplied `created_by`/`updated_by`; `created_by` immutable once set |
| `trg_%_write_rate_limit` | 20 tables | BEFORE INSERT/UPDATE/DELETE | see rate limiting below |
| `update_batch_stock()` | `inventory_transactions` | **AFTER** INSERT/UPDATE/DELETE | maintains `vaccine_batches.remaining_quantity`; raises on negative |

⚠️ **`set_updated_at()` is not on every table.** It is defined at
`20260816_security_hardening.sql:46-55` and attached only to the tables that existed at the
time (`:57-72`); no later migration attaches it — `consultation_todos`, `vaccination_todos`,
`personal_vaccine_reminders`, `borrow_returns`, `doctor_weekly_slots` and
`doctor_slot_exceptions` all go without. `expenses` has its own trigger instead
(`20260909_expenses.sql:59-63`).

⚠️ **Three migration-created business tables have no accountability trigger:** `expenses`,
`doctor_weekly_slots` and `doctor_slot_exceptions` are never added to the
`neo_stamp_accountability` loop. Their `created_by` / `updated_by` are client-supplied and
unverified — contradicting `20260825:14-20`, which exists precisely to stop that. Every
other financial table (`finance_transactions`) *is* stamped.

### The stock trigger

Two generations exist. This matters.

**Version A — the hand-applied original**, gitignored at
`.artifacts/712413f3-…/scratch/stock_triggers.sql:2-25`. Installed as
`AFTER INSERT OR DELETE` — **no UPDATE branch**, no negative guard.
`20261013140000:4-6` asserts this is what is running in production ("applied to production
by hand").

**Version B — `20261013140000_stock_trigger_negative_guard.sql`**, applied only where the
function already exists:

```sql
-- :44-49 — raises, does NOT clamp
IF v_remaining IS NOT NULL AND v_remaining < 0 THEN
    RAISE EXCEPTION 'stock would go negative for batch % (remaining would be %)'
        USING ERRCODE = 'check_violation';
END IF;
```

✅ AFTER, and raises — the comment at `:41-43` is correct that raising rolls back the
transaction that inserted the movement. 🔍 Note this contradicts the comment at `:9-10`,
which claims the client sync "clamps at 0" — it does not; `SyncUploader.kt:620`
*omits* the column on update instead.

⚠️ **The `UPDATE` branch is wrong** (`:56-61`): it applies `- OLD.quantity` with no
`+ NEW.quantity`, and only ever debits `OLD.batch_id`. Since `20261016:140` widened the
trigger to `after insert or update or delete`, and the client uploads transaction rows with
a PostgREST `upsert` (`SyncUploader.kt:551`, `:646`), **any retried upsert of an existing
ledger row corrupts stock.** See [[Known Bugs]] and [[Inventory Management]].

⚠️ **Both triggers may now be installed.** `20261016:134-138` gates on the absence of
`inventory_transactions_update_batch_stock` — but the legacy trigger is named
`tr_update_batch_stock` and is **never dropped by any migration**. If the legacy trigger is
live, every stock movement is applied **twice**. See [[Known Bugs]].

✅ **The duplicate-trigger half is history.**
`20261017_stock_trigger_duplicate_and_update_arithmetic.sql` drops `tr_update_batch_stock`
unconditionally (`:26`) and raises at migration time if any other trigger remains on the
table (`:31-46`).

⚠️ **The arithmetic half is not.** The rewritten `update_batch_stock()` still corrupts stock
on any UPDATE: the undo guard `v_delta <> old.quantity` (`:80`) is true for an idempotent
retry (delta 0 against a non-zero quantity), so it subtracts `OLD.quantity` from the batch
and *then* applies `v_delta := new.quantity - old.quantity` (`:71`, `:101`) — a net
`new - 2 * old`, i.e. `-Q` for a retry of a row with quantity `Q`. A retried `VACCINATION`
upsert (−2) still invents 2 doses, the exact symptom the paragraph above records. The client
retries with the same `id` (`InventoryTransactionEntity.transactionId`,
`data/local/entity/VaccineEntity.kt:80`), so a retry *is* an UPDATE. Tracked as **NCC-002**
in [[Quality/Bug Register]]. 🔍 The live effect needs the pgTAP/psql delta assertion; the
defect itself is readable in the migration.

## Write rate limiting

`20260929001000_write_rate_limit.sql` is **not** a request rate limiter — it is a
**per-row, per-user, per-minute write counter.**

```sql
-- :16-49
actor uuid := auth.uid();
window_start timestamptz := date_trunc('minute', now());
max_writes constant integer := 600;
...
insert into public.api_write_rate_limits(user_id, window_started_at, request_count)
values (actor, window_start, 1)
on conflict (user_id, window_started_at)
do update set request_count = public.api_write_rate_limits.request_count + 1
returning request_count into new_count;
if new_count > max_writes then
    raise exception using errcode = '57014', message = 'Write rate limit exceeded; retry later';
end if;
```

Attached to 20 tables as `before insert or update or delete … for each row`.

| Behaviour | Detail |
|---|---|
| Limit | 600 **row mutations** per user per calendar minute |
| Bypass | `service_role` returns early (`:29`) |
| Unauthenticated | raises `42501`, not `57014` |
| Counter table | RLS ON, full `revoke` — only the trigger can reach it ✅ |

Three problems:

1. ⚠️ **It counts rows, not requests.** One `DELETE FROM patient_visits WHERE …` matching
   5 000 rows costs 5 000 of the 600 budget. The header comment describes a *request*
   limiter. A legitimate first-run sync after a long offline period trips `57014` mid-group,
   and `SyncRepositoryImpl.kt:297-303` has no client-side pacing and no `57014` retry — so
   the whole group is marked `FAILED`.
2. ⚠️ **BEFORE-row triggers fire before RLS `WITH CHECK`.** A row RLS would reject still
   consumes budget, so a policy-boundary probe spends the victim's quota.
3. ⚠️ **`cleanup_write_rate_limits()` is never scheduled** and is `revoke`d from `public`,
   so only the table owner or `pg_cron` can call it. The table grows unboundedly — one row
   per active user per minute, forever.

## Edge functions

### `manage-staff` — 257 lines

`verify_jwt = true` (`config.toml:428`), and it **re-verifies itself** server-side:

```ts
// index.ts:24-29
const token = authHeader.substring("Bearer ".length)
const { data: { user }, error } = await admin.auth.getUser(token)   // not an unverified decode
if (error || !user) return json({ error: "Unauthorized" }, 401)
```

✅ **Authorization reads `app_metadata.role`, not `profiles.role`** (`:37-42`) — consistent
with `20261001_app_metadata_roles.sql`. `is_active`/`is_deleted` remain business gating.

Actions: `CREATE`, `UPDATE_PROFILE`, `CHANGE_ROLE`, `SET_STATUS`, `SOFT_DELETE`,
`RESET_PASSWORD_EMAIL`. `validateRole` (`:232-235`) allow-lists exactly
`["admin","doctor","receptionist","nurse","inventory_manager"]`.

Hardening already applied: `MIN_PASSWORD_LENGTH = 12`; role written to `app_metadata` only,
never `user_metadata`; phone normalized to E.164; PostgREST error bodies suppressed
(`:226-228`); orphaned Auth user deleted on profile-insert failure (`:91-95`); deactivation
and soft-delete **ban the GoTrue user** so refresh tokens stop (`:176-178`, `:201-204`).

Two findings:

- ⚠️ **`UPDATE_PROFILE` is missing from the self-guard** at `:102-104`, which lists only
  `["CHANGE_ROLE","SET_STATUS","SOFT_DELETE"]`. An admin can change their **own** role via
  `UPDATE_PROFILE` instead of `CHANGE_ROLE`. No escalation (already admin), but the guard
  list reads as exhaustive and is not.
- ⚠️ **`RESET_PASSWORD_EMAIL` fires on a soft-deleted profile** (`:209-213`) with no
  `is_deleted`/`is_active` check, so a deleted account can still receive reset mail.

### `notify-update` — 166 lines

`verify_jwt = false` (`config.toml:423`) — the caller is a GitHub Actions workflow with a
shared secret, not a user. It authenticates itself, and ✅ **fails closed**:

```ts
// index.ts:114-121
const a = new Uint8Array(await digest(header.slice('Bearer '.length)))
const b = new Uint8Array(await digest(secret))
return timingSafeEqual(a, b)
```

Both sides are SHA-256'd to 32 bytes first so `timingSafeEqual` cannot throw on a length
mismatch, and an unset secret is rejected rather than becoming the literal
`"Bearer undefined"` (`:22-25`). This is the fix for the fail-open bug named in commit
`787b8e6`.

Body: reads every active `user_devices.fcm_token` (`:42-45`), mints an RS256 assertion from
`FIREBASE_SERVICE_ACCOUNT` (`:123-156`), posts one FCM v1 message per token (`:71-98`).

Findings:

- ⚠️ **N+1 FCM calls, fully serial** — no batching, no concurrency cap. 200 devices means
  200 sequential round-trips; the function will hit its subrequest limit and time out
  mid-list, leaving some devices un-notified with no record of which.
- ℹ️ Selects `.eq('is_active', true)` on `user_devices` only — it never joins
  `profiles.is_active`/`is_deleted`. Correct *only* because `manage-staff:194-196` sets
  `user_devices.is_active = false` on `SOFT_DELETE`. Any account deactivated outside that
  path stays on the push list. 🔍 Needs live verification.

## Dead backend code

`notify_todays_patient()` is **defined but never fired**. `grep -c "create trigger"` on
`20260911_doctor_availability_slots.sql` returns **0**. The function inserts into
`todo_notification_events` — **a table no migration in this repository creates**. Its own
comment at `:147` cites `20260911_todays_patient_notifications.sql`, which does not exist.

The entire doctor's Today's-Patient push path is dead in the repo: no trigger, no table, no
edge function.

## Related

- [[Row Level Security]] — the policy matrix and its gaps
- [[Database Schema]] — tables, drift, and migration ordering
- [[Inventory Management]] — what the stock trigger is protecting
- [[Offline-First Architecture]] — the client half of this conversation
- [[Known Bugs]] — security and correctness defects ranked
