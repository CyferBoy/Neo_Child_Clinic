-- 20260925_business_soft_delete_columns.sql
--
-- The Android app now soft-deletes every business entity (application-wide
-- soft-delete, Room migration v31) and syncs the result as an UPDATE whose
-- payload carries is_deleted / deleted_at / deleted_by. Tables that do not
-- have those columns reject the upsert with PGRST107 ("Could not find the
-- 'is_deleted' column ... in the schema cache"), which fails the whole sync
-- group on the FIRST device that deletes a row of that type.
--
-- State this migration reconciles (idempotent - safe to run against any of
-- them):
--   - most business tables never had the columns at all
--   - expenses has is_deleted but no deleted_at / deleted_by
--   - doctor_slot_exceptions lost its columns when
--     20260919_remove_soft_delete.sql dropped is_deleted
--   - profiles is already complete and is left untouched
--
-- deleted_by is text (a staff display-name label), matching how created_by /
-- updated_by were converted to text by 20260825_clinic_rls_and_accountability.sql
-- for the very same reason: the app client sends human-readable labels, not
-- auth UIDs. Only profiles.deleted_by stays uuid, because profiles deletes are
-- performed exclusively by the manage-staff edge function (service role),
-- never by the app client.

begin;

do $$
declare
  t text;
  business_tables text[] := array[
    'patients', 'patient_visits', 'vaccination_items', 'consultations',
    'reminders', 'personal_vaccine_reminders', 'finance_transactions',
    'expenses', 'borrow_records', 'borrow_returns', 'waste_records',
    'consultation_todos', 'vaccination_todos', 'vaccines', 'vaccine_batches',
    'doctor_weekly_slots', 'doctor_slot_exceptions', 'patient_notes'
  ];
begin
  foreach t in array business_tables
  loop
    execute format(
      'alter table public.%I add column if not exists is_deleted boolean not null default false',
      t
    );
    execute format(
      'alter table public.%I add column if not exists deleted_at timestamptz',
      t
    );
    execute format(
      'alter table public.%I add column if not exists deleted_by text',
      t
    );
    execute format(
      'create index if not exists idx_%I_is_deleted on public.%I(is_deleted)',
      t, t
    );
  end loop;
end $$;

commit;