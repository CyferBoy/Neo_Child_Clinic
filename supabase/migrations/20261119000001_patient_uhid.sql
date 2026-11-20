-- Patient-level UHID, format "26-27/001".
--
-- One sequence per financial year (1 Apr - 31 Mar), assigned at first insert only.
-- Unlike receipt numbers, a staff-entered UHID is never overwritten: the trigger
-- only fills the field when it is blank, and UPDATEs never touch it.

begin;

alter table public.patients add column if not exists uhid text;

create table if not exists public.uhid_sequences (
  financial_year text primary key,
  last_number integer not null default 0,
  updated_at timestamptz not null default now()
);

alter table public.uhid_sequences enable row level security;
revoke all on public.uhid_sequences from authenticated, anon;

create or replace function public.neo_next_uhid(reg_date date)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  fy text;
  next_seq integer;
begin
  fy := coalesce(
    replace(public.neo_financial_year_label(coalesce(reg_date, current_date)), '/', '-'),
    replace(public.neo_financial_year_label(current_date), '/', '-')
  );

  insert into public.uhid_sequences (financial_year, last_number)
  values (fy, 1)
  on conflict (financial_year)
  do update set last_number = public.uhid_sequences.last_number + 1,
                updated_at = now()
  returning last_number into next_seq;

  return fy || '/' || lpad(next_seq::text, 3, '0');
end;
$$;

revoke all on function public.neo_next_uhid(date) from public, authenticated, anon;

create or replace function public.neo_assign_patient_uhid()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if TG_OP = 'INSERT' and (NEW.uhid is null or btrim(NEW.uhid) = '') then
    NEW.uhid := public.neo_next_uhid(NEW.registration_date::date);
  end if;
  return NEW;
end;
$$;

drop trigger if exists neo_assign_patient_uhid on public.patients;
create trigger neo_assign_patient_uhid
before insert on public.patients
for each row
execute function public.neo_assign_patient_uhid();

-- Backfill: existing patients get a UHID from their registration financial year.
update public.patients p
set uhid = public.neo_next_uhid(p.registration_date::date)
where p.uhid is null or btrim(p.uhid) = '';

-- Best-effort uniqueness index. Skipped with a notice if existing data has duplicates
-- (e.g. staff already hand-wrote the same UHID on two records) - de-duplicate first.
do $$
begin
  if not exists (
    select 1 from public.patients
    where uhid is not null and btrim(uhid) <> ''
    group by uhid having count(*) > 1
  ) then
    create unique index if not exists patients_uhid_key
      on public.patients (uhid) where uhid is not null and btrim(uhid) <> '';
  else
    raise notice 'SKIPPED unique index on patients(uhid): duplicate UHIDs exist. De-duplicate first.';
  end if;
end $$;

commit;
