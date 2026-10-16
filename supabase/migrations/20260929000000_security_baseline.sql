-- Neo Child Clinic: baseline Supabase authorization boundary.
--
-- This migration intentionally does NOT invent a clinic_id/tenant_id column that is not
-- present in the current schema. The current application models staff membership through
-- public.profiles. Until an explicit tenant/clinic ownership column is introduced, the
-- safest compatible boundary is: only an authenticated, active, non-deleted staff member
-- can access clinic business tables.
--
-- IMPORTANT:
--   * RLS is authorization, not rate limiting.
--   * The Android anon/publishable key is not a security boundary.
--   * The service-role key must remain server-side.
--   * Existing Edge Functions using the service-role key bypass RLS intentionally.

create or replace function public.is_active_staff()
returns boolean
language sql
stable
security definer
set search_path = public, auth
as $$
  select exists (
    select 1
    from public.profiles p
    where p.id = auth.uid()
      and p.is_active = true
      and coalesce(p.is_deleted, false) = false
  );
$$;

revoke all on function public.is_active_staff() from public;
grant execute on function public.is_active_staff() to authenticated;

-- Business tables used by the Android client through PostgREST/sync.
-- Keep this list explicit so adding a new remote table cannot silently inherit access.
do $$
declare
  table_name text;
begin
  foreach table_name in array array[
    'patients',
    'patient_visits',
    'patient_notes',
    'vaccination_items',
    'vaccines',
    'vaccine_batches',
    'inventory_transactions',
    'waste_records',
    'borrow_records',
    'borrow_returns',
    'reminders',
    'consultations',
    'consultation_todos',
    'vaccination_todos',
    'personal_vaccine_reminders',
    'finance_transactions',
    'expenses',
    'doctor_weekly_slots',
    'doctor_slot_exceptions',
    'audit_logs'
  ] loop
    if to_regclass('public.' || table_name) is null then
      continue;
    end if;

    execute format('alter table public.%I enable row level security', table_name);
    execute format('drop policy if exists "active staff select" on public.%I', table_name);
    execute format('drop policy if exists "active staff insert" on public.%I', table_name);
    execute format('drop policy if exists "active staff update" on public.%I', table_name);
    execute format('drop policy if exists "active staff delete" on public.%I', table_name);

    execute format(
      'create policy "active staff select" on public.%I for select to authenticated using (public.is_active_staff())',
      table_name
    );
    execute format(
      'create policy "active staff insert" on public.%I for insert to authenticated with check (public.is_active_staff())',
      table_name
    );
    execute format(
      'create policy "active staff update" on public.%I for update to authenticated using (public.is_active_staff()) with check (public.is_active_staff())',
      table_name
    );
    execute format(
      'create policy "active staff delete" on public.%I for delete to authenticated using (public.is_active_staff())',
      table_name
    );
  end loop;
end $$;

-- Audit logs are append-only from the client. They may be read by active staff but cannot
-- be modified/deleted through the normal authenticated PostgREST path.
do $$
begin
  if to_regclass('public.audit_logs') is not null then
    drop policy if exists "active staff insert" on public.audit_logs;
    drop policy if exists "active staff update" on public.audit_logs;
    drop policy if exists "active staff delete" on public.audit_logs;

    create policy "active staff insert" on public.audit_logs
      for insert to authenticated
      with check (public.is_active_staff());
  end if;
end $$;

-- Profiles contain role/activation state and must not be writable through the normal
-- client path. Staff creation/role/status/deletion is already handled by manage-staff,
-- which uses the server-side service-role credential after checking the caller is an admin.
do $$
begin
  if to_regclass('public.profiles') is not null then
    alter table public.profiles enable row level security;
  end if;
end $$;

drop policy if exists "active staff select profiles" on public.profiles;
drop policy if exists "self select profile" on public.profiles;

create policy "active staff select profiles" on public.profiles
  for select to authenticated
  using (public.is_active_staff());

-- No authenticated INSERT/UPDATE/DELETE policy is intentionally created for profiles.
-- The service-role-backed manage-staff Edge Function performs privileged staff changes.

-- Devices are scoped to their authenticated owner. The service-role-backed staff function
-- may deactivate another user's device when that staff account is soft-deleted.
do $$
begin
  if to_regclass('public.user_devices') is not null then
    alter table public.user_devices enable row level security;
    drop policy if exists "owner select devices" on public.user_devices;
    drop policy if exists "owner insert devices" on public.user_devices;
    drop policy if exists "owner update devices" on public.user_devices;
    drop policy if exists "owner delete devices" on public.user_devices;

    create policy "owner select devices" on public.user_devices
      for select to authenticated using (auth.uid() = user_id and public.is_active_staff());
    create policy "owner insert devices" on public.user_devices
      for insert to authenticated with check (auth.uid() = user_id and public.is_active_staff());
    create policy "owner update devices" on public.user_devices
      for update to authenticated using (auth.uid() = user_id and public.is_active_staff())
      with check (auth.uid() = user_id and public.is_active_staff());
    create policy "owner delete devices" on public.user_devices
      for delete to authenticated using (auth.uid() = user_id and public.is_active_staff());
  end if;
end $$;
