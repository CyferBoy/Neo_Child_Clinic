# Row Level Security

> The complete policy matrix after all 22 migrations, plus the gaps. Companion:
> [[Supabase]] · [[Database Schema]]

## Roles

Only two Postgres roles are ever granted a policy: **`anon`** and **`authenticated`**.
✅ **No policy anywhere is granted to `anon`** — the anon key in the APK can read nothing.

App-level roles (`admin`, `doctor`, `receptionist`, `nurse`, `inventory_manager`) live in
`auth.jwt() -> 'app_metadata' ->> 'role'` since `20261001_app_metadata_roles.sql`, which
moved them **out of** `profiles.role`. The client reads the same claim in
`core/security/CurrentUserProvider.kt:22-23` with an exact case-sensitive match and no
default — null on anything unrecognised.

## The matrix

✅ = allowed · 🔒 = blocked (no policy for that command) · ❓ = needs live-DB verification

| Table | anon S/I/U/D | auth SELECT | INSERT | UPDATE | DELETE | Predicate |
|---|---|---|---|---|---|---|
| `patients` | 🔒🔒🔒🔒 | ✅ | ✅ | ✅ | ✅ | `is_active_staff()` |
| `patient_visits` | 🔒🔒🔒🔒 | ✅ | ✅ | ✅ | ✅ | `is_active_staff()` |
| `patient_notes` | 🔒🔒🔒🔒 | ✅ | ✅ | ✅ | ✅ | `is_active_staff()` |
| `vaccination_items` | 🔒🔒🔒🔒 | ✅ | ✅ | ✅ | ✅ | `is_active_staff()` |
| `waste_records` | 🔒🔒🔒🔒 | ✅ | ✅ | ✅ | ✅ | `is_active_staff()` |
| `reminders` | 🔒🔒🔒🔒 | ✅ | ✅ | ✅ | ✅ | `is_active_staff()` |
| `vaccines` | 🔒🔒🔒🔒 | ✅ | ✅ | ✅ | ✅ | `is_active_staff()` |
| `vaccine_batches` | 🔒🔒🔒🔒 | ✅ | ✅ | ✅ | ✅ | `is_active_staff()` |
| `inventory_transactions` | 🔒🔒🔒🔒 | ✅ | ✅ | ✅ | ✅ | `is_active_staff()` |
| `finance_transactions` | 🔒🔒🔒🔒 | ✅ | ✅ | ✅ | ✅ | `is_active_staff()` |
| `borrow_records` | 🔒🔒🔒🔒 | ✅ | ✅ | ✅ | ✅ | `is_active_staff()` |
| `consultations` | 🔒🔒🔒🔒 | ✅ | ✅ | ✅ | ✅ | `is_active_staff()` |
| `consultation_todos` | 🔒🔒🔒🔒 | ✅ | ✅ | ✅ | ✅ | `is_active_staff()` |
| `vaccination_todos` | 🔒🔒🔒🔒 | ✅ | ✅ | ✅ | ✅ | `is_active_staff()` |
| `personal_vaccine_reminders` | 🔒🔒🔒🔒 | ✅ | ✅ | ✅ | ✅ | `is_active_staff()` |
| `borrow_returns` | 🔒🔒🔒🔒 | ✅ | ✅ | ✅ | ✅ | `is_active_staff()` |
| `audit_logs` | 🔒🔒🔒🔒 | ✅ | ✅ | 🔒 | 🔒 | **append-only** ✅ |
| `expenses` | 🔒🔒🔒🔒 | ✅ staff | ✅ staff | ✅ admin/doctor | ✅ admin/doctor | JWT role |
| `doctor_weekly_slots` | 🔒🔒🔒🔒 | ✅ staff | ✅ own **or** admin | ✅ own **or** admin | ✅ own **or** admin | `doctor_id = auth.uid()` |
| `doctor_slot_exceptions` | 🔒🔒🔒🔒 | ✅ staff | ✅ own **or** admin | ✅ own **or** admin | ✅ own **or** admin | same |
| `profiles` | 🔒🔒🔒🔒 | ⚠️ **all staff, all rows** | 🔒 | ✅ own row | 🔒 | `is_active_staff()` + trigger guard |
| `user_devices` | 🔒🔒🔒🔒 | ✅ own | ✅ own + staff | ✅ own + staff | ✅ own | `user_id = auth.uid()` |
| `receipt_sequences` | 🔒🔒🔒🔒 | 🔒 | 🔒 | 🔒 | 🔒 | **zero policies + `revoke all`** ✅ by design |
| `api_write_rate_limits` | 🔒🔒🔒🔒 | 🔒 | 🔒 | 🔒 | 🔒 | **zero policies + `revoke all`** ✅ by design |
| `todo_notification_events` | 🔒🔒🔒🔒 | 🔒 | 🔒 | 🔒 | 🔒 | zero policies, but see finding #5 |
| `sync_queue` | 🔒🔒🔒🔒 | 🔒 | 🔒 | 🔒 | 🔒 | ⚠️ **zero policies — unexplained** |
| `widget_due_cache` | 🔒🔒🔒🔒 | 🔒 | 🔒 | 🔒 | 🔒 | ⚠️ **zero policies — unexplained** |

✅ **No table has RLS disabled within the migration set.** `20260825:172-189` sweeps RLS
onto every `public` table not on a 16-name allow-list, and every table created later enables
RLS in its own `create` block.

## The model is "single clinic", not "role-scoped"

**16 of 26 tables grant full CRUD to any authenticated, active, non-deleted staff member
regardless of role.** A receptionist can hard-DELETE `patient_visits`,
`finance_transactions` and `inventory_transactions`.

✅ This is deliberate and documented at `20260825:22-26`:

> *single-clinic app … any authenticated, active, non-deleted staff member can read/write
> the shared clinic dataset, regardless of role.*

Recorded here as a **design decision**, not a bug. Only `expenses` (writes) and the two
doctor-slot tables branch on role. See [[Architecture Decisions]].

## Confirmed issues

### 1. ⚠️ `profiles` is readable by every active staff member

`20260929000000_security_baseline.sql:119-121`:

```sql
create policy "active staff select profiles" on public.profiles
  for select to authenticated
  using (public.is_active_staff());
```

Permissive policies OR together, so this **widens** the earlier
`profiles_select_own_or_admin` (`20260816:126-132`). Net effect: **any nurse or
receptionist can `select *` from `profiles`**, including `email`, `phone_number`, `role`,
`employee_id`, **`fcm_token`**, and the soft-delete audit fields.

This was introduced by the migration named "security baseline". Fix: replace with
`(id = auth.uid() or public.is_admin())` plus a column-level grant.

### 2. ⚠️ RLS enabled with zero policies on two unexplained tables

`sync_queue` and `widget_due_cache` were swept to RLS-ON with **zero policies** by
`20260825:182-188` and never added to any policy list. No comment anywhere explains why
they exist. `widget_due_cache` **does** have a Room counterpart
(`data/local/entity/WidgetDueEntity.kt:9`), so the Room and Postgres sides disagree about
whether this table is live.

### 3. ⚠️ One table had RLS **disabled** for a window

`20261016:13-14` states it outright:

> *todo_notification_events: created by a migration that is not in this repository, so the
> deny-by-default sweep in 20260825 ran before it existed and the baseline's explicit list
> omits it.*

Between its creation (after 08-25) and `20261016`, it had **no RLS and no revocation**, and
it receives `patient_id`, patient `name` and `doctor_id` (`20260911:164-195`). Anyone with
the anon key could read it. Closed by `20261016:53-57`. 🔍 Verify the migration was actually
applied.

### 4. ⚠️ Legacy policies from unversioned scratch SQL may still exist

`.artifacts/712413f3-…/scratch/missing_tables.sql:95-100` and
`full_project_schema.sql.artifact.md:303-345` created a policy generation that **no
migration ever drops**:

```sql
-- missing_tables.sql:99
CREATE POLICY "Staff can record audit logs" ON public.audit_logs FOR INSERT WITH CHECK (true);

-- artifact:337
CREATE POLICY "Admins can manage all profiles" ON public.profiles FOR ALL
  USING (public.get_my_role() = 'admin');
```

If these are live:

- `WITH CHECK (true)` on `audit_logs` ORs with `active staff insert` → **any authenticated
  user, including a deactivated one, can forge audit entries.**
- `FOR ALL` on `profiles` gated by `get_my_role()` (which reads `profiles.role`)
  **defeats the whole `20261001_app_metadata_roles.sql` migration** — it still authorises on
  `profiles.role`, so any user with `profiles.role = 'admin'` retains INSERT and DELETE on
  `profiles`. Full privilege escalation.

🔍 Settle it with:

```sql
select policyname, tablename, cmd, qual, with_check from pg_policies
where schemaname='public' and tablename in ('profiles','audit_logs')
order by tablename, policyname;

select proname, prosecdef, proconfig from pg_proc
where pronamespace='public'::regnamespace and proname='get_my_role';
```

### 5. ⚠️ `FORCE ROW LEVEL SECURITY` breaks the documented writer

`20261016:53-60` enables **and forces** RLS on `todo_notification_events` with zero policies,
with the comment:

> *the writer is the SECURITY DEFINER trigger, and nothing in the app reads this table.*

The very next line's `force` inverts the next sentence of that comment: **`FORCE` applies
RLS to the table owner too**, and a `SECURITY DEFINER` trigger executes as its owner. With
zero policies, the `insert` at `20260911:164` would be rejected. Escape hatch:
`alter table … no force row level security` plus `revoke`.

Currently latent only because `notify_todays_patient()` has no trigger at all — see
[[Supabase]].

### 6. ⚠️ GRANT vs policy: `auto_expose_new_tables` is off

`supabase/config.toml:19-24` leaves `auto_expose_new_tables` commented out, matching the new
cloud default where new entities are **not** auto-exposed.

✅ **No migration in this repository issues a single `GRANT`.** Every migration relies on the
legacy default grants. Under the new default, a perfect RLS policy is irrelevant if
`anon`/`authenticated` hold no table privilege — **every app write fails with
`permission denied for table …`**, and the field is removed on 2026-10-30.

🔍 Verify:

```sql
select table_name,
       has_table_privilege('anon',        format('public.%I', table_name), 'select') as anon_sel,
       has_table_privilege('authenticated',format('public.%I', table_name), 'select') as auth_sel
from information_schema.tables where table_schema='public' order by table_name;
```

`20260929:35` claims *"keep this list explicit so adding a new remote table cannot silently
inherit access"* — but that list governs **policies**, not **grants**.

### 7. ⚠️ Missing index on an RLS predicate column

`user_devices(user_id)` has **no index**. `20260816:162-163` created only
`user_devices_fcm_token_uidx`; no later migration adds one. Every `owner select/insert/
update/delete devices` policy (`20260929:137-145`) filters on `user_id = auth.uid()`.
`20261016:81-124` adds 5 query indexes and 3 conditional unique indexes — **none on an RLS
predicate column**.

Other predicate columns are covered: `profiles.id` is the PK;
`idx_doctor_weekly_slots_doctor_id` and `idx_doctor_slot_exceptions_doctor_id` exist
(`20260911:37`, `:89`).

### 8. ⚠️ Soft delete is invisible to RLS

`20260925` adds `is_deleted`/`deleted_at`/`deleted_by` to 18 tables and **never touches a
policy**. No final policy contains `is_deleted`. The predicate `is_active_staff()` checks the
**actor's** profile, not the **row's** soft-delete flag. See [[Database Schema]].

## Client-side role gating is a separate, weaker system

The app has **four different authorization idioms**, none of which is the server's:

| Gate | Where | What it does |
|---|---|---|
| `AdminGuard` composable | `app/NavigationDashboardGraph.kt:32-34` | wraps `MANAGE_STAFF`, `STAFF_DETAILS`, `ADD_STAFF`, `EDIT_STAFF` — **only these 4** |
| Drawer-item visibility | `feature/dashboard/presentation/component/AppDrawer.kt:138` | hides `manage_staff` / `audit_logs` when `userRole != admin` |
| `hasAccess` | `app/NavigationStatisticsGraph.kt:21` | statistics gate |
| `AuthViewModel` role read | `core/security/CurrentUserProvider.kt:36` | JWT claim only |

⚠️ `Routes.AUDIT_LOGS`, `Routes.EXPENSES`, `Routes.SYNC` and `Routes.BACKUP_SETTINGS` have
**no navigation guard**. Whether that is an authorization bypass depends entirely on RLS —
it is at minimum a UX-gate inconsistency. 🔍 The SQL decides.

## Related

- [[Supabase]] — triggers, edge functions, the functions behind these policies
- [[Database Schema]] — the tables and the soft-delete columns RLS ignores
- [[Architecture Decisions]] — why the model is single-clinic
- [[Known Bugs]] — security findings ranked with severity
