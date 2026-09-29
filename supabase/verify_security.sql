-- Read-only security verification for Neo Child Clinic.
-- Run in Supabase SQL Editor with an appropriately privileged role.

-- 1. RLS status
select n.nspname as schema_name,
       c.relname as table_name,
       c.relrowsecurity as rls_enabled,
       c.relforcerowsecurity as rls_forced
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relkind = 'r'
  and c.relname in (
    'patients','patient_visits','patient_notes','vaccination_items','vaccines','vaccine_batches',
    'inventory_transactions','waste_records','borrow_records','borrow_returns','reminders',
    'consultations','consultation_todos','vaccination_todos','personal_vaccine_reminders',
    'finance_transactions','expenses','doctor_weekly_slots','doctor_slot_exceptions','audit_logs',
    'profiles','user_devices'
  )
order by c.relname;

-- 2. Policies
select schemaname, tablename, policyname, permissive, roles, cmd, qual, with_check
from pg_policies
where schemaname = 'public'
order by tablename, policyname;

-- 3. Rate-limit triggers
select event_object_table as table_name,
       trigger_name,
       action_timing,
       event_manipulation
from information_schema.triggers
where trigger_schema = 'public'
  and trigger_name like 'trg_%_write_rate_limit'
order by event_object_table, event_manipulation;

-- 4. Important helper functions
select n.nspname as schema_name, p.proname as function_name,
       pg_get_function_identity_arguments(p.oid) as arguments
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname in ('is_active_staff','enforce_write_rate_limit','cleanup_write_rate_limits')
order by p.proname;

-- 5. Tables missing a tenant/clinic key are intentionally NOT treated as multi-tenant.
-- If this application becomes multi-clinic, add clinic_id and revise RLS before onboarding
-- another clinic. Do not rely on application-side filtering for tenant isolation.
