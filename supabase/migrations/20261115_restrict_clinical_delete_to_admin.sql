-- 20261115_restrict_clinical_delete_to_admin.sql
--
-- RLS-01: Restrict DELETE operations on core clinical and financial tables
-- to authenticated active staff who hold the 'admin' role in app_metadata.
-- Drops blanket "active staff delete" on clinical and inventory tables.

begin;

do $$
declare
  t text;
  tables text[] := array[
    'patients',
    'patient_visits',
    'patient_notes',
    'vaccination_items',
    'finance_transactions',
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
    'personal_vaccine_reminders'
  ];
begin
  foreach t in array tables loop
    if to_regclass('public.' || t) is not null then
      execute format('drop policy if exists "active staff delete" on public.%I', t);
      execute format('drop policy if exists "admin staff delete" on public.%I', t);
      execute format(
        'create policy "admin staff delete" on public.%I for delete to authenticated using (public.is_active_staff() and (auth.jwt() -> ''app_metadata'' ->> ''role'') = ''admin'')',
        t
      );
    end if;
  end loop;
end $$;

commit;
