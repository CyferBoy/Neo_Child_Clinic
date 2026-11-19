-- 20261116_revoke_legacy_staff_delete_policies.sql
--
-- ROADMAP-P0-04 / FIND-05 / RLS-01: Revoke surviving legacy permissive staff delete policies.
-- Migration 20261115_restrict_clinical_delete_to_admin.sql dropped "active staff delete",
-- but omitted the legacy "<table_name>_staff_delete" policies originally created in
-- 20260825_clinic_rls_and_accountability.sql, 20260825_patient_todos.sql,
-- 20260901_personal_vaccine_reminders.sql, and 20260902_borrow_returns.sql.
-- Because PostgreSQL combines permissive RLS policies using boolean OR, these surviving
-- policies allowed non-admin active staff to hard-delete clinical and financial records.
-- The 16 core clinical tables covered below (explicit drops in section 1 and the
-- safety-loop array in section 2) mirror 20261115_restrict_clinical_delete_to_admin.sql;
-- keep both files in sync.

begin;

-- 1. Explicit drops on all core clinical, financial, inventory, and task tables
drop policy if exists patients_staff_delete on public.patients;
drop policy if exists patient_visits_staff_delete on public.patient_visits;
drop policy if exists patient_notes_staff_delete on public.patient_notes;
drop policy if exists vaccination_items_staff_delete on public.vaccination_items;
drop policy if exists finance_transactions_staff_delete on public.finance_transactions;
drop policy if exists vaccines_staff_delete on public.vaccines;
drop policy if exists vaccine_batches_staff_delete on public.vaccine_batches;
drop policy if exists inventory_transactions_staff_delete on public.inventory_transactions;
drop policy if exists waste_records_staff_delete on public.waste_records;
drop policy if exists borrow_records_staff_delete on public.borrow_records;
drop policy if exists borrow_returns_staff_delete on public.borrow_returns;
drop policy if exists reminders_staff_delete on public.reminders;
drop policy if exists consultations_staff_delete on public.consultations;
drop policy if exists consultation_todos_staff_delete on public.consultation_todos;
drop policy if exists vaccination_todos_staff_delete on public.vaccination_todos;
drop policy if exists personal_vaccine_reminders_staff_delete on public.personal_vaccine_reminders;

-- 2. Dynamic safety loop covering canonical tables and any potential alias relations
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
    'personal_vaccine_reminders',
    -- Potential aliases if present in any legacy environment
    'visits',
    'vaccinations',
    'vials',
    'billing_transactions'
  ];
begin
  foreach t in array tables loop
    if to_regclass('public.' || t) is not null then
      execute format('drop policy if exists %I on public.%I', t || '_staff_delete', t);
      execute format('drop policy if exists "active staff delete" on public.%I', t);
    end if;
  end loop;
end $$;

commit;
