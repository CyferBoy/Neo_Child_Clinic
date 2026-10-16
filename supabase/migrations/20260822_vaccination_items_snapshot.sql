-- 20260822_vaccination_items_snapshot.sql
-- vaccination_items previously stored only vaccine_id/batch_id (foreign keys). Any code path
-- that rebuilt a vaccination from these rows had no vaccine name or batch number to work with,
-- which is what allowed patient_visits.vaccine_names to get wiped on a routine edit. These
-- columns make each row a durable, self-contained snapshot of what was actually administered,
-- independent of whether the referenced vaccine/batch is later renamed or deleted from the
-- catalog. Must land before the app build that writes them (see MIGRATION_16_17 in
-- AppDatabase.kt / VaccinationItemEntity.kt), or sync uploads of the new columns will fail
-- against a schema that doesn't have them yet.

begin;

alter table public.vaccination_items
  add column if not exists vaccine_name text not null default '';

alter table public.vaccination_items
  add column if not exists batch_number text not null default '';

commit;
