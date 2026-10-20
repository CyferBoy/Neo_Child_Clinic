# Security Concerns

> Security register. Separate from [[Quality/Bug Register]] because several items need a
> live database to settle and must not be described as vulnerabilities without evidence.
> Companion: [[Row Level Security]] · [[Supabase]]

## Status vocabulary

| Status | Meaning |
|---|---|
| **Confirmed** | Provable from a migration in this repository |
| **Needs live DB** | The code implies a problem, but only the hosted project settles it |
| **Design decision** | Deliberate, documented, accepted |

⚠️ **Nothing here is a claim about the live database.** Everything except NSC-011 and
NSC-013 is inferred from migration SQL. The verification queries are given per item.

## Index

IDs are section headings below — jump to them with the outline pane (`Ctrl/Cmd+Shift+O`).
They stay plain text here because a wikilink alias needs a `|`, which would end the table cell.

| ID | Title | Sev | Status | Area |
|---|---|---|---|---|
| NSC-001 | Every active staff member can `select *` from `profiles` | High | Confirmed | RLS |
| NSC-002 | Legacy `get_my_role()` policies may defeat the role migration | Critical | Needs live DB | RLS |
| NSC-003 | `FORCE ROW LEVEL SECURITY` breaks its own documented writer | Medium | Confirmed | RLS |
| NSC-004 | `todo_notification_events` had no RLS for a window | High | Needs live DB | RLS |
| NSC-005 | No migration issues a `GRANT`; `auto_expose_new_tables` removed 2026-10-30 | High | Needs live DB | Grants |
| NSC-006 | `user_devices(user_id)` unindexed despite every policy filtering on it | Low | Confirmed | Performance |
| NSC-007 | `expenses` has no accountability trigger | Medium | Confirmed | Accountability |
| NSC-008 | 4 routes have no navigation guard | Low | Confirmed | UI gate |
| NSC-009 | `manage-staff`: `UPDATE_PROFILE` missing from the self-guard | Low | Confirmed | Edge function |
| NSC-010 | `notify-update`: serial FCM fan-out | Low | Confirmed | Availability |
| NSC-011 | `notify-update` does not join `profiles.is_active` | Low | Needs live DB | Authz |
| NSC-012 | Write rate limiter counts rows, not requests | Medium | Confirmed | DoS |
| NSC-013 | `get_my_role()` is `SECURITY DEFINER` with no `search_path` | High | Needs live DB | SQLi surface |

---

## NSC-001 — `profiles` is readable by every active staff member

**Confirmed · High.** `20260929000000_security_baseline.sql:119-121`:

```sql
create policy "active staff select profiles" on public.profiles
  for select to authenticated
  using (public.is_active_staff());
```

Permissive policies OR together, so this **widens** the earlier
`profiles_select_own_or_admin` (`20260816:126-132`). Any nurse or receptionist can read every
colleague's `email`, `phone_number`, `role`, `employee_id`, **`fcm_token`** (a push-token
identifier), and the soft-delete audit fields.

**Root cause.** A permissive SELECT with no column restriction, introduced by the migration
named "security baseline".

**Fix.** Replace with `(id = auth.uid() or public.is_admin())`, plus column-level GRANTs for
anything staff genuinely need (e.g. `display_name`).
**Verification.** As two different roles:
```sql
set local role authenticated;
select count(*) from public.profiles;          -- expect 1 row, not all
```

**Links.** [[Row Level Security]] · [[Supabase]]

---

## NSC-002 — Legacy `get_my_role()` policies may defeat the role migration

**Needs live DB · Critical.** Two problems compound:

1. `.artifacts/…/scratch/missing_tables.sql:95-99` creates
   `CREATE POLICY "Staff can record audit logs" ON public.audit_logs FOR INSERT WITH CHECK (true);`
   — permissive policies OR, so **any authenticated user, including a deactivated one, can
   forge audit entries**.
2. `full_project_schema.sql.artifact.md:337` creates
   `CREATE POLICY "Admins can manage all profiles" ON public.profiles FOR ALL USING (public.get_my_role() = 'admin');`
   — `get_my_role()` reads **`profiles.role`**, which is exactly what
   `20261001_app_metadata_roles.sql` stopped trusting. If live, any user whose
   `profiles.role = 'admin'` retains INSERT and DELETE on `profiles`: full privilege escalation.

⚠️ No migration drops either policy. Both are gitignored scratch files.

**Verification.** 🔍 Run against the live DB:
```sql
select policyname, tablename, cmd, qual, with_check from pg_policies
where schemaname='public' and tablename in ('profiles','audit_logs')
order by tablename, policyname;

select proname, prosecdef, proconfig from pg_proc
where pronamespace='public'::regnamespace and proname='get_my_role';
```

**Fix if present.** `drop policy "Staff can record audit logs" on public.audit_logs;`
`drop policy "Admins can manage all profiles" on public.profiles;`

**Links.** [[Row Level Security]] · [[Quality/Audit Reconciliation]]

---

## NSC-005 — No table-level `GRANT`; `auto_expose_new_tables` removed 2026-10-30

**Needs live DB · High · has a deadline.** `supabase/config.toml:19-24` leaves
`auto_expose_new_tables` commented out, matching the new cloud default where new entities
are **not** auto-exposed.

✅ **No migration in this repository issues a single table-level `GRANT`.** Every table
privilege relies on the legacy default grants. Under the new default a perfect RLS policy is
irrelevant if `anon`/`authenticated` hold no table privilege — **every app write fails with
`permission denied for table …`.**

⚠️ **Correction (2026-10-18).** This note previously read *"no migration issues a single
`GRANT`"*. That was literally false: four **function-execution** grants exist, and only
those — they do not grant table access, so the security argument above is unaffected.

```
20260816_security_hardening.sql:93              grant execute on function public.is_admin() to authenticated;
20260825_clinic_rls_and_accountability.sql:49  grant execute on function public.is_active_staff() to authenticated;
20260929000000_security_baseline.sql:32         grant execute on function public.is_active_staff() to authenticated;
20261001_app_metadata_roles.sql:45             grant execute on function public.is_admin() to authenticated;
```

Verify with `grep -rin "grant " supabase/migrations/*.sql`. Anyone acting on the
over-broad wording would add a redundant table `GRANT` sweep.

`20260929:35` claims *"keep this list explicit so adding a new remote table cannot silently
inherit access"* — that list governs **policies**, not **grants**.

**Verification.** 🔍
```sql
select table_name,
       has_table_privilege('anon',         format('public.%I', table_name), 'select') as anon_sel,
       has_table_privilege('authenticated', format('public.%I', table_name), 'select') as auth_sel,
       has_table_privilege('authenticated', format('public.%I', table_name), 'insert') as auth_ins
from information_schema.tables where table_schema='public' order by table_name;
```
Any table with all three false is unreachable by the app.

**Links.** [[Row Level Security]] · [[Release Checklist]]

---

## NSC-003 — `FORCE ROW LEVEL SECURITY` breaks its own writer

**Confirmed · Medium.** `20261016_rls_index_and_stock_trigger_hardening.sql:53-60` enables
**and forces** RLS on `todo_notification_events` with zero policies, commented:

> *the writer is the SECURITY DEFINER trigger, and nothing in the app reads this table.*

The `force` on the next line inverts the following sentence of that comment: `FORCE` applies
RLS to the **table owner**, and a `SECURITY DEFINER` trigger executes as its owner. With zero
policies the insert at `20260911:164` would be rejected.

✅ Latent only because `notify_todays_patient()` has no trigger at all — see
[[Supabase]] § dead backend code.

**Fix.** `alter table public.todo_notification_events no force row level security;` and rely
on `revoke` + no policy.

---

## NSC-004 — `todo_notification_events` had no RLS for a window

**Needs live DB · High.** `20261016:13-14` states it outright:

> *created by a migration that is not in this repository, so the deny-by-default sweep in
> 20260825 ran before it existed and the baseline's explicit list omits it.*

Between its creation (after 08-25) and `20261016` it had **no RLS and no revocation**, and it
receives `patient_id`, patient `name` and `doctor_id` (`20260911:164-195`) — anyone with the
anon key could read it.

**Verification.** 🔍 Confirm `20261016` was applied to every environment:
```sql
select relname, relrowsecurity, relforcerowsecurity
from pg_class where relname = 'todo_notification_events';
```

---

## NSC-013 — `get_my_role()` is `SECURITY DEFINER` with no `search_path`

**Needs live DB · High.** `.artifacts/f21d0dfd-…/full_project_schema.sql.artifact.md:13-21`:

```sql
CREATE OR REPLACE FUNCTION public.get_my_role()
RETURNS text AS $$ ... $$ LANGUAGE plpgsql SECURITY DEFINER;
```

No `set search_path`. ✅ Its body is fully qualified so it is not directly exploitable today,
but it is the canonical anti-pattern and **14** legacy policies reference it
(`.artifacts/f21d0dfd-…/full_project_schema.sql.artifact.md:303-345`).

✅ **Every `SECURITY DEFINER` authored inside `supabase/migrations/` pins its
`search_path`** — all ten verified. See [[Supabase]].

---

## NSC-007 — `expenses` has no accountability trigger

**Confirmed · Medium.** `expenses`, `doctor_weekly_slots` and `doctor_slot_exceptions` are the
three business tables created by a migration and never added to the
`neo_stamp_accountability` loop. Their `created_by` / `updated_by` are client-supplied and
unverified — contradicting `20260825:14-20`, which exists precisely to stop that:

> *created_by/updated_by … were being set by the Android app itself and sent as plain strings,
> so any client — or anyone with the anon key — could put any name they liked into who's
> "responsible" for a record.*

Every other financial table (`finance_transactions`) **is** stamped.

**Verification.** 🔍 `select tgname from pg_trigger where tgrelid='public.expenses'::regclass;`

---

## NSC-012 — Write rate limiter counts rows, not requests

**Confirmed · Medium.** `20260929001000_write_rate_limit.sql:16-49` attaches a
`before insert or update or delete … for each row` trigger on 20 tables.

| Problem | Consequence |
|---|---|
| One `DELETE FROM patient_visits WHERE …` matching 5 000 rows costs 5 000 of the 600/minute budget | a legitimate first-run sync after a long offline period trips `57014` |
| BEFORE-row triggers fire **before** RLS `WITH CHECK` | a policy-boundary probe spends the victim's quota |
| `cleanup_write_rate_limits()` is never scheduled and is `revoke`d from `public` | the table grows one row per user per minute, forever |

`SyncRepositoryImpl.kt:297-303` has no client-side pacing and no `57014` retry — a trip marks
the **whole group** `FAILED`.

**Fix.** Rate-limit per statement (or per minute-window per user at the PostgREST layer) and
schedule the cleanup with `pg_cron`.

---

## NSC-006 — `user_devices(user_id)` unindexed

**Confirmed · Low.** `20260816:162-163` created only `user_devices_fcm_token_uidx`; no later
migration adds one. Every device policy (`20260929:137-145`) filters on `user_id = auth.uid()`.
`20261016:81-124` adds 5 indexes, **none on an RLS predicate column**.

---

## NSC-008 — Four routes have no navigation guard

**Confirmed as a UX gap · Low; authorization depends on RLS.** `AdminGuard`
(`NavigationDashboardGraph.kt:32-34`) wraps only `MANAGE_STAFF`, `STAFF_DETAILS`, `ADD_STAFF`,
`EDIT_STAFF`. `Routes.AUDIT_LOGS`, `EXPENSES`, `SYNC` and `BACKUP_SETTINGS` are ungated.

Four different authorization idioms exist across the graph (guard composable, drawer
visibility, `hasAccess`, server RLS). ⚠️ Whether this is a bypass depends entirely on the SQL.

---

## NSC-009 — `manage-staff`: `UPDATE_PROFILE` missing from the self-guard

**Confirmed · Low.** `supabase/functions/manage-staff/index.ts:102-104` guards
`["CHANGE_ROLE","SET_STATUS","SOFT_DELETE"]` against self-targeting. `UPDATE_PROFILE`
(`:118-121`) is absent, so an admin can change their **own** role through it.

✅ No escalation — they are already admin. But the guard list reads as exhaustive and is not.

---

## NSC-010 — `notify-update`: serial FCM fan-out

**Confirmed · Low.** `supabase/functions/notify-update/index.ts:71-98` posts one FCM v1 message
per token with no batching and no concurrency cap. 200 devices means 200 sequential
round-trips; the function will hit its subrequest limit and time out mid-list, leaving some
devices un-notified with no record of which.

---

## NSC-011 — `notify-update` does not join `profiles.is_active`

**Needs live DB · Low.** `notify-update/index.ts:45` selects `.eq('is_active', true)` on `user_devices`
only. It never joins `profiles.is_active` / `is_deleted`. Correct **only** because
`manage-staff:194-196` sets `user_devices.is_active = false` on `SOFT_DELETE`.

**Verification.** 🔍
```sql
select count(*) from public.user_devices d
left join public.profiles p on p.id = d.user_id
where d.is_active and (p.is_active is not true or p.is_deleted);
```

---

## Design decisions accepted, not vulnerabilities

| Item | Why accepted |
|---|---|
| **Single-clinic access model** — 16 of the 27 tables in the policy matrix grant full CRUD to any authenticated, active, non-deleted staff member regardless of role; the live table total is not derivable from the repo (🔍) | Documented at `20260825:22-26`. A receptionist **can** hard-delete `patient_visits`. Intentional. |
| No policy granted to `anon` | ✅ Good — the anon key in the APK reads nothing |
| `receipt_sequences`, `api_write_rate_limits` with zero policies + `revoke all` | ✅ Server-only by design |
| `audit_logs` select+insert only | ✅ Correctly append-only |
| Supabase anon key shipped in the APK | ✅ By design — RLS is the control |
| No secrets committed to the repository | ✅ Verified: `.env.local` gitignored, untracked, never in any branch; no `eyJ…`/`sbp_…` in `app/src/main` |

## Related

- [[Quality/Bug Register]] — non-security defects
- [[Row Level Security]] — the full policy matrix
- [[Supabase]] — triggers, edge functions, rate limiting
- [[Release Checklist]] — the security gate before shipping
