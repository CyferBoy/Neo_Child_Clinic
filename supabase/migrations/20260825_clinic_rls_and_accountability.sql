-- 20260825_clinic_rls_and_accountability.sql
--
-- Two fixes from the security review:
--
-- 1. patients/patient_visits/consultations/vaccination_items/vaccine_batches/
--    vaccines/finance_transactions/borrow_records/waste_records/patient_notes/
--    inventory_transactions/audit_logs/reminders had no RLS at all. The
--    SUPABASE_ANON_KEY is embedded in every APK (trivially extractable), so
--    without RLS anyone holding it - no login required - could read or write
--    every patient/financial record directly via the REST API, bypassing the
--    app and auth entirely.
--
-- 2. created_by/updated_by ("Staff Accountability Tracking") were being set by
--    the Android app itself and sent as plain strings, so any client - or
--    anyone with the anon key, given (1) - could put any name they liked into
--    who's "responsible" for a record. These are now stamped server-side from
--    the authenticated auth.uid(), and the app's client-supplied value is
--    ignored. The column stays text (a human-readable staff label), matching
--    what the Kotlin entities already send - not a hard reset to uuid, which
--    would break every existing upsert.
--
-- Access model: single-clinic app, matching the rest of the schema (is_admin()
-- etc.) - any authenticated, active, non-deleted staff member can read/write
-- the shared clinic dataset, regardless of role. audit_logs is the one
-- exception: insert + select only, no update/delete, so the trail can't be
-- edited or erased by the same staff it's tracking.

begin;

-- Any authenticated, active, non-deleted staff member (any role) - the
-- clinic-data equivalent of is_admin(), which only matches role = 'admin'.
create or replace function public.is_active_staff()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.profiles
    where id = auth.uid()
      and is_active = true
      and is_deleted = false
  );
$$;

revoke all on function public.is_active_staff() from public;
grant execute on function public.is_active_staff() to authenticated;

-- The human-readable label the app already displays and stores
-- (display_name, falling back to email) for the currently authenticated
-- staff member - mirrors SessionManager.getCurrentUserName() on the client,
-- but resolved here from auth.uid() so it can't be spoofed.
create or replace function public.neo_current_staff_label()
returns text
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(nullif(btrim(p.display_name), ''), p.email)
  from public.profiles p
  where p.id = auth.uid();
$$;

revoke all on function public.neo_current_staff_label() from public, authenticated, anon;

-- Stamps created_by/updated_by from the authenticated user, overriding
-- whatever the client sent. created_by is immutable once set (same pattern
-- as receipt_number: an edit, or a stale offline payload re-uploading an old
-- local copy, must never change who's on record as having created it).
create or replace function public.neo_stamp_accountability()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  label text := public.neo_current_staff_label();
begin
  if TG_OP = 'INSERT' then
    NEW.created_by := coalesce(label, NEW.created_by);
    NEW.updated_by := coalesce(label, NEW.created_by);
  elsif TG_OP = 'UPDATE' then
    NEW.created_by := OLD.created_by;
    NEW.updated_by := coalesce(label, NEW.updated_by);
  end if;
  return NEW;
end;
$$;

-- Tables the app collaboratively reads/writes as one shared clinic dataset,
-- with full CRUD for any active staff member.
do $$
declare
  t text;
  full_crud_tables text[] := array[
    'patients', 'patient_visits', 'vaccination_items', 'waste_records',
    'reminders', 'vaccines', 'vaccine_batches', 'inventory_transactions',
    'patient_notes', 'finance_transactions', 'borrow_records', 'consultations'
  ];
begin
  foreach t in array full_crud_tables
  loop
    execute format('alter table public.%I enable row level security', t);

    -- created_by/updated_by must stay the human-readable staff label the app
    -- already works with (not a UUID) - only made server-authoritative below.
    execute format('alter table public.%I alter column created_by type text using created_by::text', t);
    execute format('alter table public.%I alter column updated_by type text using updated_by::text', t);

    execute format('drop policy if exists %I on public.%I', t || '_staff_select', t);
    execute format(
      'create policy %I on public.%I for select to authenticated using (public.is_active_staff())',
      t || '_staff_select', t
    );

    execute format('drop policy if exists %I on public.%I', t || '_staff_insert', t);
    execute format(
      'create policy %I on public.%I for insert to authenticated with check (public.is_active_staff())',
      t || '_staff_insert', t
    );

    execute format('drop policy if exists %I on public.%I', t || '_staff_update', t);
    execute format(
      'create policy %I on public.%I for update to authenticated using (public.is_active_staff()) with check (public.is_active_staff())',
      t || '_staff_update', t
    );

    execute format('drop policy if exists %I on public.%I', t || '_staff_delete', t);
    execute format(
      'create policy %I on public.%I for delete to authenticated using (public.is_active_staff())',
      t || '_staff_delete', t
    );

    execute format('drop trigger if exists neo_stamp_accountability on public.%I', t);
    execute format(
      'create trigger neo_stamp_accountability before insert or update on public.%I for each row execute function public.neo_stamp_accountability()',
      t
    );
  end loop;
end $$;

-- audit_logs: insert + select only. No update/delete policy at all, so the
-- trail can't be edited or erased by the staff it's recording.
alter table public.audit_logs enable row level security;
alter table public.audit_logs alter column created_by type text using created_by::text;
alter table public.audit_logs alter column updated_by type text using updated_by::text;

drop policy if exists "audit_logs_staff_select" on public.audit_logs;
create policy "audit_logs_staff_select"
on public.audit_logs for select
to authenticated
using (public.is_active_staff());

drop policy if exists "audit_logs_staff_insert" on public.audit_logs;
create policy "audit_logs_staff_insert"
on public.audit_logs for insert
to authenticated
with check (public.is_active_staff());

drop trigger if exists neo_stamp_accountability on public.audit_logs;
create trigger neo_stamp_accountability
before insert on public.audit_logs
for each row execute function public.neo_stamp_accountability();

-- Defense in depth: enable RLS on every other public table too (e.g. any
-- leftover/internal table not covered above), so the default for anything
-- we haven't explicitly opened up is deny-all for anon/authenticated rather
-- than whatever the table's default grants happen to be.
do $$
declare
  t record;
  covered text[] := array[
    'patients', 'patient_visits', 'vaccination_items', 'waste_records',
    'reminders', 'vaccines', 'vaccine_batches', 'inventory_transactions',
    'patient_notes', 'finance_transactions', 'borrow_records', 'consultations',
    'audit_logs', 'profiles', 'user_devices', 'receipt_sequences'
  ];
begin
  for t in
    select tablename from pg_tables
    where schemaname = 'public'
      and tablename <> all(covered)
  loop
    execute format('alter table public.%I enable row level security', t.tablename);
  end loop;
end $$;

commit;
