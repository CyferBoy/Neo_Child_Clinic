-- 20261118000000_access_hardening.sql
--
-- Forward-only hardening, idempotent and reversible (rollback notes at the bottom).
--   (a) vaccine_batches.remaining_quantity may only be changed by the inventory stock trigger.
--   (b) neo_stamp_accountability() stamps deleted_by from the authenticated staff on an
--       is_deleted flip (previously the client-supplied value passed through untouched).
--   (c) RLS UPDATE on core clinical/financial tables narrowed to active admin/doctor;
--       inventory tables are deliberately NOT narrowed because update_batch_stock()
--       runs as the invoker and its UPDATE on vaccine_batches must still pass RLS.

begin;

-- (a) --------------------------------------------------------------------------------
create or replace function public.neo_guard_batch_stock_write()
returns trigger
language plpgsql
set search_path = public
as $body$
begin
  -- pg_trigger_depth() is 1 for a direct client UPDATE and >= 2 when this fires from
  -- update_batch_stock()'s AFTER trigger on inventory_transactions. A no-op write is
  -- allowed so idempotent sync retries do not fail.
  -- shortcut: any trigger-driven write is allowed; today update_batch_stock() is the only
  -- trigger that writes remaining_quantity. Add a dedicated session flag if a second
  -- writer ever needs this exemption.
  if new.remaining_quantity is distinct from old.remaining_quantity
     and pg_trigger_depth() <= 1 then
    raise exception
      'remaining_quantity is maintained by the stock trigger; direct updates are rejected'
      using errcode = 'check_violation';
  end if;
  return new;
end;
$body$;

drop trigger if exists neo_guard_batch_stock_write on public.vaccine_batches;
create trigger neo_guard_batch_stock_write
before update on public.vaccine_batches
for each row execute function public.neo_guard_batch_stock_write();

-- (b) --------------------------------------------------------------------------------
-- 20260825's created_by/updated_by behaviour is kept verbatim; only the deleted_by
-- branch is added. All tables carrying this trigger received is_deleted/deleted_by in
-- 20260925_business_soft_delete_columns.sql. audit_logs is INSERT-only, so its trigger
-- never reaches the UPDATE branch.
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
    if NEW.is_deleted is distinct from OLD.is_deleted then
      if NEW.is_deleted then
        NEW.deleted_by := coalesce(label, NEW.deleted_by);
      else
        NEW.deleted_by := null;
      end if;
    end if;
  end if;
  return NEW;
end;
$$;

-- (c) --------------------------------------------------------------------------------
-- Permissive RLS policies OR together: the blanket "active staff update" policy from
-- 20260929 (and the per-table "<table>_staff_update" policies from 20260825 /
-- 20260825_patient_todos / 20260901) must be dropped or the new role check grants
-- nothing. Same drop-then-create pattern 20261013120000 used for expenses.
do $$
declare
  t text;
  clinical_tables text[] := array[
    'patients', 'patient_visits', 'patient_notes', 'vaccination_items',
    'consultations', 'consultation_todos', 'vaccination_todos',
    'reminders', 'personal_vaccine_reminders', 'finance_transactions'
  ];
begin
  foreach t in array clinical_tables loop
    if to_regclass('public.' || t) is null then
      continue;
    end if;

    execute format('drop policy if exists %I on public.%I', t || '_staff_update', t);
    execute format('drop policy if exists %I on public.%I', 'active staff update', t);
    execute format('drop policy if exists %I on public.%I', t || '_admin_doctor_update', t);

    execute format(
      'create policy %I on public.%I for update to authenticated using (public.is_active_staff() and (auth.jwt() -> ''app_metadata'' ->> ''role'') in (''admin'',''doctor'')) with check (public.is_active_staff() and (auth.jwt() -> ''app_metadata'' ->> ''role'') in (''admin'',''doctor''))',
      t || '_admin_doctor_update', t
    );
  end loop;
end $$;

commit;

-- Rollback (manual, reverse order):
--   drop policy if exists <table>_admin_doctor_update on public.<table>;  -- each table above
--   recreate "active staff update" per 20260929000000_security_baseline.sql if desired;
--   drop trigger if exists neo_guard_batch_stock_write on public.vaccine_batches;
--   drop function if exists public.neo_guard_batch_stock_write();
--   restore public.neo_stamp_accountability() from 20260825_clinic_rls_and_accountability.sql
--   (remove the is_deleted/deleted_by branch).
