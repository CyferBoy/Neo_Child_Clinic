# 03 — RLS and Security

**Scope:** authorization model, policy coverage, privilege boundaries, storage, and client-controlled fields.
**Limitation:** no live Supabase instance, so no policy was exercised. Findings are static unless marked otherwise.
**Status (2026-10-26):** SQL-013 is fixed in the working tree (policies require `is_active_staff()`). SQL-014/015/016/017/019 remain open decisions — see `14-fix-log.md`.

---

## 1. Authorization model

There are **two role sources**, and they do not always agree:

| Source | Used by | Evidence |
|---|---|---|
| `profiles.role` / `profiles.is_active` / `profiles.is_deleted` | `is_admin()`, `is_active_staff()` | `20260816…:75-90`; `20260929000000…:15-29` |
| JWT `auth.jwt() -> 'app_metadata' ->> 'role'` | `expenses` and doctor-slot carve-outs | `20261001_app_metadata_roles.sql:47-84`; `20261013120000…:83-118` |

`manage-staff` writes **both** `app_metadata.role` and `profiles.role` (`supabase/functions/manage-staff/index.ts:66-69,150-159`), so they normally agree. `is_active`/`is_deleted` are only in `profiles` — the JWT has no activation claim. That asymmetry is the root of `SQL-013`.

The Android client never chooses a role; staff accounts are created by the Edge Function with the service role.

---

## 2. Policy coverage

### 2.1 Tables with RLS enabled and four active-staff policies

`20260929000000_security_baseline.sql:40-88` creates `select`/`insert`/`update`/`delete` policies for **20 tables**:

`patients`, `patient_visits`, `patient_notes`, `vaccination_items`, `vaccines`, `vaccine_batches`, `inventory_transactions`, `waste_records`, `borrow_records`, `borrow_returns`, `reminders`, `consultations`, `consultation_todos`, `vaccination_todos`, `personal_vaccine_reminders`, `finance_transactions`, `expenses`, `doctor_weekly_slots`, `doctor_slot_exceptions`, `audit_logs`.

`audit_logs` is handled correctly at the end of the baseline: the loop creates all four policies, then `:96-98` immediately drops `"active staff insert/update/delete"` and recreates only INSERT (`:100-102`). Together with the SELECT policy from `20260825…:151-155`, the effective set is SELECT + INSERT. The append-only intent holds. The remaining audit weakness is content trust, not editability (`SQL-019`).

### 2.2 Tables with narrower policies

| Table | Policy | Evidence |
|---|---|---|
| `profiles` | SELECT own-or-admin; UPDATE own (guarded by trigger) | `20260816…:125-139` |
| `user_devices` | owner-only CRUD + `is_active_staff()` | `20260929000000…:130-144`; `20261013120000…:131-140` |
| `expenses` | SELECT staff; INSERT/UPDATE/DELETE admin-or-doctor | `20260909_expenses.sql`; `20261013120000…:23-80` |
| `doctor_weekly_slots` | write admin-or-own; select staff | `20261013120000…:83-95` |
| `doctor_slot_exceptions` | write admin-or-own; select staff | `20261013120000…:99-118` |
| `receipt_sequences` | RLS + revoke all from clients | `20260824…:34-35` |
| `api_write_rate_limits` | RLS + revoke all from clients | `20260929001000…:13-14` |
| `todo_notification_events` | RLS forced + revoke all; no read policy | `20261016…:53-62` |
| `storage.objects` (`patient-docs`) | active staff for all operations | `20261016…:67-78` |

### 2.3 Deny-by-default sweep

`20260825…:168-189` enables RLS on every `public` table not in a covered list. This is good hygiene, but it is bypassed for tables that do not exist at migration time (`SQL-001`), and it does not add policies — a table enabled here without policies is deny-all, which can break a legitimate client path silently.

---

## 3. Findings

### SQL-013 — Doctor-slot writes ignore account deactivation

- **Severity:** High
- **Confidence:** Confirmed (static); exploitation requires a still-valid JWT
- **Object:** `doctor_weekly_slots_write_admin_or_own`, `doctor_slot_exceptions_write_admin_or_own`
- **Workflow:** a deactivated doctor or admin keeps a valid access token until expiry; RLS is the only server-side gate
- **Root cause:** both policies check only `doctor_id = auth.uid()` or the JWT `app_metadata.role = 'admin'` (`20261013120000…:83-95,107-118`). They do **not** call `public.is_active_staff()`. Every other table’s baseline policy does.
- **Failure scenario:** an admin sets `profiles.is_active = false` for a doctor. The doctor’s existing JWT remains valid for its lifetime (typically 1 hour; refresh tokens longer). During that window the doctor can still INSERT/UPDATE/DELETE their own `doctor_weekly_slots` and `doctor_slot_exceptions`, changing clinic availability. A deactivated admin can edit any doctor’s slots. Neither can read patient data (other tables gate on `is_active_staff()`), but the scheduling surface remains writable.
- **Impact:** authorization bypass after deactivation; tampered availability; no audit distinction between active and deactivated writers.
- **Evidence:** `20261013120000…:86-93,110-117`; contrast `20260929000000…:76-87`.
- **Correction:** add `and public.is_active_staff()` to both `using` and `with check` clauses (or wrap the whole predicate). Keep the JWT admin check for the admin branch. Do not rely on token revocation.
- **Regression test:** set the doctor inactive, set a JWT claim with that `sub`, attempt an insert into `doctor_weekly_slots`, expect `42501`. Add the active-admin and active-doctor positive cases.
- **Live check:** yes — confirm whether any inactive profile still has a live session at deploy time.

### SQL-014 — Baseline is active-staff-wide CRUD, not least privilege

- **Severity:** Medium (accepted design risk)
- **Confidence:** Confirmed
- **Object:** 20 tables × 4 policies
- **Workflow:** any active staff account
- **Root cause:** the product is a single-clinic shared dataset, so the baseline grants every active staff member full CRUD on every business table (`20260929000000…:72-87`). Role distinctions (doctor vs nurse vs admin) exist only for `expenses` and doctor slots.
- **Failure scenario:** a nurse can delete `patients`, `inventory_transactions`, `finance_transactions`, or `audit_logs` via a direct PostgREST call. The Android UI may hide those actions, but RLS does not enforce the UI’s intent.
- **Impact:** an authenticated low-privilege account can destroy clinical and financial records. This is a design decision, not a bug, but it must be documented as an accepted risk or narrowed.
- **Evidence:** `20260929000000…:72-87`; no role check beyond `is_active_staff()`.
- **Correction:** if the clinic accepts “all staff are trusted”, record it in the threat model and add compensating controls (audit trail, backups, rate limit). If not, introduce role-specific policies for destructive operations on `patients`, `finance_transactions`, `inventory_transactions`, and `audit_logs`.
- **Regression test:** a policy matrix test per role × table × operation (see `08-regression-test-plan.md`).
- **Live check:** no.

### SQL-015 — Patient documents are readable by every active staff member

- **Severity:** Medium
- **Confidence:** Confirmed
- **Object:** `storage.objects` policy `"patient docs staff access"`; bucket `patient-docs`
- **Workflow:** document upload/download for a patient
- **Root cause:** the policy is `for all to authenticated using (bucket_id = 'patient-docs' and public.is_active_staff())` (`20261016…:73-76`). Object paths are `<patient_id>/<file>` with **no tenant or clinic segment** (`:22-24`). Any active staff member can list, read, overwrite, or delete any patient’s documents.
- **Failure scenario:** a staff member enumerates objects under another patient’s prefix (the bucket is private, but the policy grants SELECT on all objects in it) and downloads scans. There is no per-patient assignment model to scope against.
- **Impact:** PHI exposure across staff roles; document tampering/deletion.
- **Evidence:** `20261016…:19-24,67-78`.
- **Correction:** if document access is meant to be clinic-wide, accept and document it. Otherwise add a `patient_documents` metadata table with an owner/assignment column and a policy that joins on it; keep the object path as `<clinic>/<patient>/<file>` to make prefix scoping possible.
- **Regression test:** as a staff user with no assignment, `select from storage.objects where bucket_id='patient-docs'` must return 0 rows; as the assigned doctor, 1.
- **Live check:** yes — list current object prefixes and confirm no cross-clinic data.

### SQL-016 — `DROP FUNCTION get_my_role() CASCADE` removed 14 policies

- **Severity:** Medium
- **Confidence:** Requires Verification
- **Object:** `20261021000000_drop_legacy_profiles_policy.sql:17-27`
- **Workflow:** policy availability after migration
- **Root cause:** `DROP FUNCTION … CASCADE` drops every dependent object, including policies that referenced the function. The migration comments say the policies were “legacy permissive”, but it does not enumerate or assert what was dropped.
- **Failure scenario:** a table that relied on a dropped policy now has **no** policy and therefore denies all access (if RLS is enabled) or allows nothing. The app path fails with `42501` or returns empty sets; `RemotePrune` then treats the empty set as authoritative and may delete local rows (it has an empty-set guard, but a partial policy regression is harder to detect).
- **Impact:** silent loss of access on an unknown table; or, if the dropped policy was the only permissive one and another permissive policy remains, broader access than intended.
- **Evidence:** `20261021000000…:5-27`; the exact 14 dependents are not listed. `verify_security.sql` can list policies but is not run in CI.
- **Correction:** before dropping, capture `pg_depend` for the function; after dropping, assert the expected policy set per table. Replace `CASCADE` with explicit `DROP POLICY` statements in a migration so the diff is reviewable.
- **Regression test:** policy-count assertions per table; a `pg_policies` snapshot diff in CI.
- **Live check:** yes — run `verify_security.sql` and compare policy counts to the expected matrix.

### SQL-017 — Profile self-update allowlist is fragile

- **Severity:** Medium
- **Confidence:** Confirmed
- **Object:** `profiles_update_own`; `protect_profile_privileged_fields()`
- **Workflow:** staff edits their own profile
- **Root cause:** the UPDATE policy is intentionally retained (`20261013…:5-11`), and the trigger blocks a fixed list of columns (`:35-44`). Any column added to `profiles` later is self-writable unless someone remembers to add it to the trigger (`:15-18`).
- **Failure scenario:** a future `profiles.clinic_id`, `profiles.permissions`, or `profiles.employee_id` is added; a staff member can set it on their own row. If a policy later trusts that column, privilege escalation follows.
- **Impact:** latent escalation surface; no test enforces the allowlist.
- **Evidence:** `20261013…:15-18,35-44`.
- **Correction:** replace the denylist with an allowlist: compute the set of columns a self-updater may change (`display_name`, `phone`, maybe `avatar`) and raise if any other column differs. Or move profile edits to an Edge Function and drop the client UPDATE policy.
- **Regression test:** for every column in `information_schema.columns` for `profiles`, attempt a self-update and assert only the allowlisted ones succeed. Generate the test from the schema so new columns are covered automatically.
- **Live check:** no.

### SQL-019 — Audit trail content is client-authored

- **Severity:** Medium
- **Confidence:** Confirmed
- **Object:** `audit_logs` INSERT policy and columns
- **Workflow:** every audited action; audit review; retention
- **Root cause:**
  1. `timestamp`, `user`, `entity_id`, `module`, `action`, `old_value`, `new_value`, `device` are client-supplied. Only `created_by`/`updated_by` are stamped (`20260825…:163-166`).
  2. `timestamp` is `text` with a client-provided default; the retention job trusts it (`20261025…:13-19`).
  3. The accountability trigger on `audit_logs` runs `before insert` only; it does not validate that `user` matches the authenticated staff label.
- **Failure scenario:** a staff member inserts an audit row claiming another user performed an action (`user = 'Dr X'`), or backdates `timestamp` so the retention job deletes evidence early / future-dates it so it survives. They cannot edit or delete an existing row (the baseline’s SELECT+INSERT-only set is correct), but they can pollute the trail and manipulate retention.
- **Impact:** audit trail is not trustworthy for attribution or retention; incident reconstruction can be misled.
- **Evidence:** `20260825…:145-166`; `20260929000000…:91-104`; CSV shows `timestamp text`; `20261025…:13-19`.
- **Correction:** on INSERT, overwrite `NEW.timestamp := now()::text` (or a real `timestamptz`) and `NEW.user := public.neo_current_staff_label()` in a before-insert trigger, ignoring client values. Keep the append-only policy set. If imports need historical timestamps, use a service-role-only path.
- **Regression test:** as an authenticated staff user, INSERT `timestamp = '2000-01-01'` and `user = 'forged'`; assert the stored `timestamp` is within the last minute and `user` equals the caller’s label.
- **Live check:** yes — count rows whose `timestamp` is outside a sane range.

### SQL-018 — Receipt number accepted from the client

Cross-referenced from `02-functions-and-triggers.md §3`. The same trust boundary applies here: receipt numbers are financial identifiers, and the trigger lets a client choose any unique value.

---

## 4. Function privilege review

| Function | Grant | Risk |
|---|---|---|
| `is_active_staff()` | `authenticated` | intended |
| `is_admin()` | `authenticated` | intended |
| `neo_next_receipt_number(date)` | revoked from public/authenticated/anon (`20260824…:106`) | only the definer trigger calls it; correct |
| `prune_audit_logs()` | revoked from public (`20261025…:22`) | only cron/service role; correct |
| `cleanup_write_rate_limits()` | revoked from public (`20260929001000…:91`) | never scheduled (`SQL-032`) |
| `update_batch_stock()` | default EXECUTE to public | invoker function; an authenticated user could call it directly? It is a trigger function returning `trigger`; calling it outside a trigger raises. Low risk, but revoke EXECUTE from `public` for hygiene. |
| `neo_assign_receipt_number()` | default EXECUTE to public | same trigger-function shape; low risk |
| `neo_stamp_accountability()` | default EXECUTE to public | same |
| `notify_todays_patient()` | default EXECUTE to public | trigger function; it performs an HTTP POST and reads a secret GUC. A direct call is not possible outside a trigger context, but revoke for defense in depth. |

`set_updated_at()` is an invoker trigger function with no `search_path`; it only assigns `now()`, so no injection surface.

---

## 5. Secrets and configuration

- `notify_todays_patient()` reads `app.settings.todo_notifier_secret` (`20260911…:174`). The value is not in the repo. If the GUC is set at database level, any role that can run `show` may read it; restrict `ALTER DATABASE … SET` to the owner and prefer Vault.
- No service-role key, JWT secret, or connection string appears in any SQL file. `cloudflare/backup-worker/.dev.vars.example` is an example only.
- `supabase/.temp` exists locally but is gitignored; not inspected for secrets and not part of this audit.

---

## 6. Security checks to run before any remediation

```sql
-- 1. Policy inventory per table/command.
select schemaname, tablename, policyname, cmd, roles, qual, with_check
from pg_policies
where schemaname in ('public','storage')
order by 1,2,3;

-- 2. RLS coverage.
select c.relname, c.relrowsecurity, c.relforcerowsecurity
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where n.nspname in ('public','storage') and c.relkind = 'r'
order by 1;

-- 3. Functions with definer rights and their search_path.
select p.proname, p.prosecdef, p.proconfig
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
order by 1;

-- 4. Grants that let clients write.
select grantee, table_name, privilege_type
from information_schema.role_table_grants
where table_schema = 'public' and grantee in ('authenticated','anon')
order by 2, 1, 3;
```

None of these was executed. `supabase/verify_security.sql` already implements most of (1)–(3) and should be the starting point.
