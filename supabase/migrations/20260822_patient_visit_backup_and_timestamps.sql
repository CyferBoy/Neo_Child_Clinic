-- Make vaccination_items a usable, self-contained backup for patient_visits and
-- repair legacy rows whose audit timestamps were written as NULL.
--
-- Each item is linked to exactly one visit by vaccination_id.  The backfill only
-- takes details from the visit having that exact ID; it never attempts to match by
-- vaccine name, patient name, or any other non-unique value.

begin;

alter table public.vaccination_items
  add column if not exists vaccine_name text not null default '';

alter table public.vaccination_items
  add column if not exists batch_number text not null default '';

-- Preserve the historical name and batch number for item rows that predate the
-- snapshot columns.  Array positions mirror the comma-separated values written
-- by the app into patient_visits.  If a legacy visit has no usable value, leave
-- the snapshot blank rather than inventing a match.
update public.vaccination_items as item
set
  vaccine_name = coalesce(
    nullif(item.vaccine_name, ''),
    nullif(trim((string_to_array(visit.vaccine_names, ','))[array_position(string_to_array(visit.vaccine_ids, ','), item.vaccine_id::text)]), ''),
    ''
  ),
  batch_number = coalesce(
    nullif(item.batch_number, ''),
    nullif(trim((string_to_array(visit.batch_numbers, ','))[array_position(string_to_array(visit.batch_ids, ','), item.batch_id::text)]), ''),
    ''
  )
from public.patient_visits as visit
where visit.id = item.vaccination_id
  and (item.vaccine_name = '' or item.batch_number = '');

-- An earlier client could explicitly upsert NULL timestamps.  Defaults only
-- apply to omitted values, so repair existing data first, then enforce the
-- invariant for all future writes.
alter table public.patient_visits
  add column if not exists created_at timestamptz,
  add column if not exists updated_at timestamptz;

-- Older deployments may have created one timestamp column as text and the other
-- as timestamptz. Normalize both before comparing or repairing their values.
alter table public.patient_visits
  alter column created_at type timestamptz using nullif(btrim(created_at::text), '')::timestamptz,
  alter column updated_at type timestamptz using nullif(btrim(updated_at::text), '')::timestamptz;

update public.patient_visits
set
  created_at = coalesce(created_at, updated_at, now()),
  updated_at = coalesce(updated_at, created_at, now())
where created_at is null or updated_at is null;

alter table public.patient_visits
  alter column created_at set default now(),
  alter column updated_at set default now(),
  alter column created_at set not null,
  alter column updated_at set not null;

commit;
