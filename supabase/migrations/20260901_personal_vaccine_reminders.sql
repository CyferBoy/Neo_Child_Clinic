-- Personal Vaccine Reminders: standalone personal follow-up reminders about a
-- vaccine requirement for a patient (e.g. patient requested a vaccine that
-- wasn't in stock and left an advance). Intentionally independent of the
-- `reminders` table (Next Vaccination), `patient_visits` (actual vaccination
-- records), and `finance_transactions` - no trigger here ever touches those
-- tables, and nothing in those tables should ever write to this one either.

begin;

create table if not exists public.personal_vaccine_reminders (
    id uuid primary key default gen_random_uuid(),
    patient_id uuid not null references public.patients(id) on delete cascade,
    vaccine_id uuid references public.vaccines(id) on delete set null,
    vaccine_label text,
    note text,
    advance_received boolean not null default false,
    advance_amount numeric,
    advance_date date,
    reminder_date date not null,
    status text not null default 'PENDING',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    completed_at timestamptz,
    cancelled_at timestamptz,
    is_synced boolean not null default true,
    created_by text,
    updated_by text,
    constraint personal_vaccine_reminders_status_check
        check (status in ('PENDING', 'READY', 'COMPLETED', 'CANCELLED')),
    constraint personal_vaccine_reminders_advance_amount_check
        check (advance_amount is null or advance_amount >= 0)
);

create index if not exists personal_vaccine_reminders_patient_id_idx on public.personal_vaccine_reminders(patient_id);
create index if not exists personal_vaccine_reminders_vaccine_id_idx on public.personal_vaccine_reminders(vaccine_id);
create index if not exists personal_vaccine_reminders_status_idx on public.personal_vaccine_reminders(status);
create index if not exists personal_vaccine_reminders_reminder_date_idx on public.personal_vaccine_reminders(reminder_date);

alter table public.personal_vaccine_reminders enable row level security;

drop policy if exists personal_vaccine_reminders_staff_select on public.personal_vaccine_reminders;
create policy personal_vaccine_reminders_staff_select on public.personal_vaccine_reminders for select to authenticated using (public.is_active_staff());
drop policy if exists personal_vaccine_reminders_staff_insert on public.personal_vaccine_reminders;
create policy personal_vaccine_reminders_staff_insert on public.personal_vaccine_reminders for insert to authenticated with check (public.is_active_staff());
drop policy if exists personal_vaccine_reminders_staff_update on public.personal_vaccine_reminders;
create policy personal_vaccine_reminders_staff_update on public.personal_vaccine_reminders for update to authenticated using (public.is_active_staff()) with check (public.is_active_staff());
drop policy if exists personal_vaccine_reminders_staff_delete on public.personal_vaccine_reminders;
create policy personal_vaccine_reminders_staff_delete on public.personal_vaccine_reminders for delete to authenticated using (public.is_active_staff());

-- Keep the existing human-readable accountability convention.
drop trigger if exists neo_stamp_accountability on public.personal_vaccine_reminders;
create trigger neo_stamp_accountability before insert or update on public.personal_vaccine_reminders for each row execute function public.neo_stamp_accountability();

commit;
