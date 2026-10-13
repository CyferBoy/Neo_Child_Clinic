-- 20261013120000_role_policy_carveouts.sql
--
-- Carve-out for the three role-sensitive tables that
-- 20260929000000_security_baseline.sql blanket-covered. Permissive policies OR together,
-- so the blanket "active staff insert/update/delete" policies the baseline created on
-- expenses, doctor_weekly_slots and doctor_slot_exceptions defeated the narrower role
-- policies: any active staff member could update/delete any expense and write any
-- doctor's schedule. Same pattern the baseline itself already applies to audit_logs (its
-- lines 93-104): drop the blanket write policies, keep role-restricted ones.
--
-- Role checks copy the auth.jwt() app_metadata expressions that
-- 20261001_app_metadata_roles.sql already defines for these exact policies (it moved
-- role checks from profiles.role to the JWT claim; there is no is_doctor() helper, and
-- is_admin() alone cannot express the owning-doctor branch), so the recreated policies
-- behave identically to the ones being restored.
--
-- Select policies are untouched: reads stay open as originally intended (active staff
-- for expenses via 20260909, any authenticated user for the doctor-slot tables via
-- 20260911).

begin;

-- expenses: update/delete restricted to admin/doctor (20260909 update policy +
-- 20260919 delete policy, JWT form from 20261001). Insert stays open to active staff
-- through 20260909's own "Active staff can add expenses" policy, which the baseline
-- never dropped - so only the blanket insert/update/delete policies go.
do $$
begin
  if to_regclass('public.expenses') is not null then
    drop policy if exists "active staff insert" on public.expenses;
    drop policy if exists "active staff update" on public.expenses;
    drop policy if exists "active staff delete" on public.expenses;

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
  end if;
end $$;

-- doctor_weekly_slots: write = the owning doctor or an admin (20260911 req. 6/7/19,
-- JWT form from 20261001). Reads stay open via "doctor_weekly_slots_select_authenticated".
do $$
begin
  if to_regclass('public.doctor_weekly_slots') is not null then
    drop policy if exists "active staff insert" on public.doctor_weekly_slots;
    drop policy if exists "active staff update" on public.doctor_weekly_slots;
    drop policy if exists "active staff delete" on public.doctor_weekly_slots;

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
  end if;
end $$;

-- doctor_slot_exceptions: same write rule as doctor_weekly_slots (20260911, JWT form
-- from 20261001).
do $$
begin
  if to_regclass('public.doctor_slot_exceptions') is not null then
    drop policy if exists "active staff insert" on public.doctor_slot_exceptions;
    drop policy if exists "active staff update" on public.doctor_slot_exceptions;
    drop policy if exists "active staff delete" on public.doctor_slot_exceptions;

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
  end if;
end $$;

-- user_devices: 20260816's owner policies are gated only on user_id = auth.uid(), and the
-- baseline only dropped its own "owner ... devices" names before recreating them WITH the
-- active-staff check. Permissive policies OR, so a deactivated staff member could still
-- PATCH their own row back to is_active = true (and INSERT a fresh active device row),
-- putting it straight back on the notify-update push list. The baseline's owner policies
-- already grant active staff the same insert/update on their own rows, so dropping these
-- removes no legitimate access. select/delete stay: read-and-remove of your own row is no
-- privilege, and the deactivated-device UX reads its own row to show the locked state.
do $$
begin
  if to_regclass('public.user_devices') is not null then
    drop policy if exists "user_devices_insert_own" on public.user_devices;
    drop policy if exists "user_devices_update_own" on public.user_devices;
  end if;
end $$;

commit;
