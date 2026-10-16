-- Doctor assignment + doctor availability slots for Today's Patient / Add Consultation /
-- Add Vaccination.
--
-- This migration is additive only:
--   - two new tables (doctor_weekly_slots, doctor_slot_exceptions)
--   - nullable columns added to the four existing tables that need to reference a slot or
--     carry doctor attribution for Today's Patient (consultations, patient_visits,
--     consultation_todos, vaccination_todos)
-- No existing table, column, trigger, or policy is altered or dropped. Existing rows are
-- left with availability_slot_id = NULL (req. 20) - they remain valid and readable exactly
-- as before.
--
-- Doctor identity: this schema already uses profiles.id (the Supabase auth uid) as the
-- doctor_id stored on consultations/patient_visits (see AddConsultationViewModel /
-- AddVaccinationViewModel: `selectedDoctor.employeeId ?: selectedDoctor.id`). The new
-- tables below use the same profiles.id as their doctor_id FK for consistency, and the
-- RLS policies compare it directly against auth.uid().

-- ==========================================================================
-- 1. doctor_weekly_slots - a doctor's normal recurring weekly availability.
-- ==========================================================================
create table if not exists public.doctor_weekly_slots (
    id uuid primary key default gen_random_uuid(),
    doctor_id uuid not null references public.profiles(id) on delete cascade,
    -- 1 = Sunday .. 7 = Saturday (matches java.util.Calendar.DAY_OF_WEEK on the Android
    -- client, so no day-index translation is needed anywhere in the sync path).
    day_of_week smallint not null check (day_of_week between 1 and 7),
    start_minute smallint not null check (start_minute >= 0 and start_minute < 1440),
    end_minute smallint not null check (end_minute > start_minute and end_minute <= 1440),
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by text,
    updated_by text
);

create index if not exists idx_doctor_weekly_slots_doctor_id on public.doctor_weekly_slots(doctor_id);
create index if not exists idx_doctor_weekly_slots_day on public.doctor_weekly_slots(day_of_week);
create index if not exists idx_doctor_weekly_slots_active on public.doctor_weekly_slots(is_active);

alter table public.doctor_weekly_slots enable row level security;

-- Everyone authenticated can READ every doctor's weekly slots - receptionists need this
-- to build the Add Consultation/Add Vaccination slot picker for any doctor (req. 8), and
-- admins need to see every doctor's schedule (req. 6). Only the write side is restricted.
drop policy if exists "doctor_weekly_slots_select_authenticated" on public.doctor_weekly_slots;
create policy "doctor_weekly_slots_select_authenticated"
    on public.doctor_weekly_slots for select
    to authenticated
    using (true);

-- Write access: admin (any doctor) or the doctor themself (own rows only) - req. 6/7/19.
drop policy if exists "doctor_weekly_slots_write_admin_or_own" on public.doctor_weekly_slots;
create policy "doctor_weekly_slots_write_admin_or_own"
    on public.doctor_weekly_slots for all
    to authenticated
    using (
        doctor_id = auth.uid()
        or exists (select 1 from public.profiles p where p.id = auth.uid() and p.role = 'admin')
    )
    with check (
        doctor_id = auth.uid()
        or exists (select 1 from public.profiles p where p.id = auth.uid() and p.role = 'admin')
    );

-- ==========================================================================
-- 2. doctor_slot_exceptions - date-specific overrides of the weekly schedule.
-- ==========================================================================
create table if not exists public.doctor_slot_exceptions (
    id uuid primary key default gen_random_uuid(),
    doctor_id uuid not null references public.profiles(id) on delete cascade,
    exception_date date not null,
    exception_type text not null check (exception_type in ('FULL_DAY', 'SLOT')),
    -- Required (and only meaningful) when exception_type = 'SLOT'; must stay null for
    -- FULL_DAY exceptions - enforced below rather than relying on client discipline.
    weekly_slot_id uuid references public.doctor_weekly_slots(id) on delete cascade,
    reason text,
    is_deleted boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by text,
    updated_by text,
    constraint doctor_slot_exceptions_slot_id_matches_type check (
        (exception_type = 'FULL_DAY' and weekly_slot_id is null)
        or (exception_type = 'SLOT' and weekly_slot_id is not null)
    )
);

create index if not exists idx_doctor_slot_exceptions_doctor_id on public.doctor_slot_exceptions(doctor_id);
create index if not exists idx_doctor_slot_exceptions_date on public.doctor_slot_exceptions(exception_date);
create index if not exists idx_doctor_slot_exceptions_weekly_slot_id on public.doctor_slot_exceptions(weekly_slot_id);
create index if not exists idx_doctor_slot_exceptions_deleted on public.doctor_slot_exceptions(is_deleted);

alter table public.doctor_slot_exceptions enable row level security;

drop policy if exists "doctor_slot_exceptions_select_authenticated" on public.doctor_slot_exceptions;
create policy "doctor_slot_exceptions_select_authenticated"
    on public.doctor_slot_exceptions for select
    to authenticated
    using (true);

drop policy if exists "doctor_slot_exceptions_write_admin_or_own" on public.doctor_slot_exceptions;
create policy "doctor_slot_exceptions_write_admin_or_own"
    on public.doctor_slot_exceptions for all
    to authenticated
    using (
        doctor_id = auth.uid()
        or exists (select 1 from public.profiles p where p.id = auth.uid() and p.role = 'admin')
    )
    with check (
        doctor_id = auth.uid()
        or exists (select 1 from public.profiles p where p.id = auth.uid() and p.role = 'admin')
    );

-- ==========================================================================
-- 3. Doctor + slot attribution on existing tables (req. 12/15).
-- ==========================================================================

-- consultations / patient_visits already carry doctor_id + a doctor name column; only the
-- slot reference is new.
alter table public.consultations
    add column if not exists availability_slot_id uuid references public.doctor_weekly_slots(id) on delete set null;

alter table public.patient_visits
    add column if not exists availability_slot_id uuid references public.doctor_weekly_slots(id) on delete set null;

-- Today's Patient queue rows had no doctor attribution at all before this migration -
-- required for both the slot picker on the quick-add dialog and for doctor-specific
-- notification targeting (req. 16). Nullable/no backfill: existing queue rows remain valid
-- and simply have no assigned doctor until a device recreates/edits them (req. 20).
alter table public.consultation_todos
    add column if not exists doctor_id uuid references public.profiles(id) on delete set null,
    add column if not exists doctor_name text,
    add column if not exists availability_slot_id uuid references public.doctor_weekly_slots(id) on delete set null;

alter table public.vaccination_todos
    add column if not exists doctor_id uuid references public.profiles(id) on delete set null,
    add column if not exists doctor_name text,
    add column if not exists availability_slot_id uuid references public.doctor_weekly_slots(id) on delete set null;

create index if not exists idx_consultation_todos_doctor_id on public.consultation_todos(doctor_id);
create index if not exists idx_vaccination_todos_doctor_id on public.vaccination_todos(doctor_id);

-- ==========================================================================
-- 4. Doctor-specific Today's Patient notification targeting (req. 16).
-- ==========================================================================
-- notify_todays_patient() (see 20260911_todays_patient_notifications.sql) previously sent
-- no doctor identity at all - the edge function broadcast to every active doctor because
-- Today's Patient had no assignment concept yet. Now that consultation_todos/
-- vaccination_todos carry doctor_id, forward it so the edge function can target only that
-- doctor's device(s) and fall back to the old broadcast-to-all-doctors behavior only when
-- a row genuinely has no doctor assigned (e.g. a pre-migration queue entry).
create or replace function public.notify_todays_patient() returns trigger
language plpgsql
security definer
set search_path = public, extensions, net
as $$
declare
  v_todo_type text := TG_ARGV[0];
  v_event_id uuid;
  v_function_url text;
  v_secret text;
begin
  insert into public.todo_notification_events (todo_type, todo_id, event)
  values (v_todo_type, NEW.id::text, 'created')
  on conflict (todo_type, todo_id, event) do nothing
  returning id into v_event_id;

  if v_event_id is null then
    return NEW;
  end if;

  v_function_url := current_setting('app.settings.supabase_functions_url', true);
  v_secret := current_setting('app.settings.todo_notifier_secret', true);

  if v_function_url is null or v_secret is null then
    raise warning 'notify_todays_patient: notifier not configured (app.settings.supabase_functions_url / todo_notifier_secret); skipping push for % %', v_todo_type, NEW.id;
    return NEW;
  end if;

  perform net.http_post(
    url := v_function_url || '/notify-todays-patient',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'Authorization', 'Bearer ' || v_secret
    ),
    body := jsonb_build_object(
      'todo_type', v_todo_type,
      'todo_id', NEW.id,
      'patient_id', NEW.patient_id,
      'name', NEW.name,
      'todo_date', NEW.todo_date,
      'doctor_id', NEW.doctor_id
    )
  );

  return NEW;
end;
$$;

