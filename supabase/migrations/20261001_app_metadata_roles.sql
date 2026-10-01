-- 20261001_app_metadata_roles.sql
-- Single source of truth for authorization roles: Supabase Auth app_metadata.
--   1. Backfill auth.users.raw_app_meta_data.role from profiles.role (one-time).
--   2. Strip role from user_metadata (display data must never authorize).
--   3. Rewrite RLS role checks from profiles.role to auth.jwt() app_metadata claim.
-- Business gating (is_active/is_deleted, ownership) is preserved verbatim.

begin;

-- 1. Backfill app_metadata.role for every provisioned user.
update auth.users u
set raw_app_meta_data = coalesce(u.raw_app_meta_data, '{}'::jsonb)
    || jsonb_build_object('role', p.role)
from public.profiles p
where p.id = u.id
  and p.role in ('admin', 'doctor', 'receptionist', 'nurse', 'inventory_manager')
  and coalesce(u.raw_app_meta_data ->> 'role', '') is distinct from p.role;

-- 2. user_metadata no longer carries a role for anyone.
update auth.users
set raw_user_meta_data = raw_user_meta_data - 'role'
where raw_user_meta_data ? 'role';

-- 3. is_admin(): JWT app_metadata instead of a profiles table read.
--    No longer touches profiles, so SECURITY DEFINER is dropped (it existed to
--    avoid recursive profile-policy evaluation). Grants are preserved.
drop function if exists public.is_admin();
create function public.is_admin()
returns boolean
language sql
stable
set search_path = public, auth
as $$
  select coalesce(auth.jwt() -> 'app_metadata' ->> 'role', '') = 'admin';
$$;

revoke all on function public.is_admin() from public;
grant execute on function public.is_admin() to authenticated;

-- 4. expenses update policy (20260909): role source swap, conditions preserved.
drop policy if exists "Admin or doctor can update expenses" on public.expenses;
create policy "Admin or doctor can update expenses"
    on public.expenses for update
    to authenticated
    using (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid()
              and p.is_active = true
              and p.is_deleted = false
        )
        and (auth.jwt() -> 'app_metadata' ->> 'role') in ('admin', 'doctor')
    )
    with check (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid()
              and p.is_active = true
              and p.is_deleted = false
        )
        and (auth.jwt() -> 'app_metadata' ->> 'role') in ('admin', 'doctor')
    );

-- 5. expenses delete policy (20260919): same swap.
drop policy if exists "expenses_delete_admin_or_own" on public.expenses;
create policy "expenses_delete_admin_or_own"
    on public.expenses for delete
    to authenticated
    using (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid()
              and p.is_active = true
              and p.is_deleted = false
        )
        and (auth.jwt() -> 'app_metadata' ->> 'role') in ('admin', 'doctor')
    );

-- 6. doctor_weekly_slots write policy (20260911): admin override via JWT,
--    the doctor's own-row branch is untouched.
drop policy if exists "doctor_weekly_slots_write_admin_or_own" on public.doctor_weekly_slots;
create policy "doctor_weekly_slots_write_admin_or_own"
    on public.doctor_weekly_slots for all
    to authenticated
    using (
        doctor_id = auth.uid()
        or (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin'
    )
    with check (
        doctor_id = auth.uid()
        or (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin'
    );

-- 7. doctor_slot_exceptions write policy (20260911): same swap.
drop policy if exists "doctor_slot_exceptions_write_admin_or_own" on public.doctor_slot_exceptions;
create policy "doctor_slot_exceptions_write_admin_or_own"
    on public.doctor_slot_exceptions for all
    to authenticated
    using (
        doctor_id = auth.uid()
        or (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin'
    )
    with check (
        doctor_id = auth.uid()
        or (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin'
    );

-- Notes:
--   * profiles_select_own_or_admin and protect_profile_privileged_fields call
--     is_admin() - they migrate automatically.
--   * is_active_staff() is activation, not role - intentionally unchanged.
--   * Run BEFORE deploying the app update; role changes propagate to existing
--     sessions on their next token refresh (<= 1h).

commit;
