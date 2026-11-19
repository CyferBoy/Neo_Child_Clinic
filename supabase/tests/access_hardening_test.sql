-- Disposable-database regression test for 20261118000000_access_hardening.sql.
--
-- Single-command invocation (works with the existing minimal-schema CI harness):
--   psql -v ON_ERROR_STOP=1 -f supabase/tests/access_hardening_test.sql
--
-- Self-contained: builds the few tables/functions the migration touches, applies the
-- migration with \ir, then asserts. The migration commits its own transaction, so this
-- script is NOT wrapped in one: run it only against a disposable database.
\set ON_ERROR_STOP on

-- ----------------------------------------------------------------------------------
-- Minimal schema (drop first so this can follow the other steps in supabase-sql.yml).
drop table if exists public.inventory_transactions cascade;
drop table if exists public.vaccine_batches cascade;
drop table if exists public.patients cascade;
drop table if exists public.profiles cascade;

create extension if not exists pgcrypto;

create table public.profiles (
  id uuid primary key,
  is_active boolean not null default true,
  is_deleted boolean not null default false,
  display_name text,
  email text
);
create table public.vaccine_batches (
  id uuid primary key,
  remaining_quantity integer not null default 0,
  updated_at timestamptz not null default now(),
  created_by text,
  updated_by text,
  is_deleted boolean not null default false,
  deleted_by text
);
create table public.inventory_transactions (
  id uuid primary key default gen_random_uuid(),
  batch_id uuid references public.vaccine_batches(id),
  quantity integer not null
);
create table public.patients (
  id uuid primary key,
  is_deleted boolean not null default false,
  created_by text,
  updated_by text,
  deleted_by text
);
alter table public.patients enable row level security;

do $roles$
begin
  if not exists (select 1 from pg_roles where rolname = 'authenticated') then
    create role authenticated;
  end if;
  if not exists (select 1 from pg_roles where rolname = 'anon') then
    create role anon;
  end if;
end
$roles$;

create schema if not exists auth;
create or replace function auth.jwt() returns jsonb language sql stable as $$
  select coalesce(nullif(current_setting('request.jwt.claims', true), '')::jsonb, '{}'::jsonb)
$$;
create or replace function auth.uid() returns uuid language sql stable as $$
  select nullif(auth.jwt() ->> 'sub', '')::uuid
$$;
create or replace function public.is_active_staff() returns boolean language sql stable
security definer set search_path = public, auth as $$
  select exists (
    select 1 from public.profiles
    where id = auth.uid() and is_active = true and is_deleted = false
  )
$$;
create or replace function public.neo_current_staff_label() returns text language sql stable
security definer set search_path = public, auth as $$
  select coalesce(nullif(btrim(p.display_name), ''), p.email)
  from public.profiles p where p.id = auth.uid()
$$;

grant usage on schema public to authenticated;
grant select, update on public.patients to authenticated;
grant select, insert, update on public.inventory_transactions to authenticated;
grant select on public.vaccine_batches to authenticated;

-- UPDATE needs a visible row: the real schema has patients_staff_select from 20260825.
create policy patients_test_select on public.patients for select to authenticated using (true);

-- Apply the migration under test.
\ir ../migrations/20261118000000_access_hardening.sql

-- 20260825 attaches neo_stamp_accountability() to vaccine_batches in the real schema;
-- the minimal harness needs the same trigger to exercise the deleted_by branch (b).
drop trigger if exists neo_stamp_accountability on public.vaccine_batches;
create trigger neo_stamp_accountability before insert or update on public.vaccine_batches
for each row execute function public.neo_stamp_accountability();

-- A minimal stand-in for update_batch_stock(): the real trigger is an AFTER trigger on
-- inventory_transactions, so its inner UPDATE fires the guard at pg_trigger_depth()=2.
create or replace function public.test_update_batch_stock() returns trigger language plpgsql as $$
begin
  update public.vaccine_batches
     set remaining_quantity = remaining_quantity + new.quantity
   where id = new.batch_id;
  return null;
end;
$$;
drop trigger if exists test_stock on public.inventory_transactions;
create trigger test_stock after insert on public.inventory_transactions
for each row execute function public.test_update_batch_stock();

-- (a) direct remaining_quantity writes rejected, stock-trigger writes allowed --------
do $guard$
declare
  v_id uuid := gen_random_uuid();
begin
  insert into public.vaccine_batches(id, remaining_quantity) values (v_id, 10);

  begin
    update public.vaccine_batches set remaining_quantity = 99 where id = v_id;
    raise exception 'FAIL: direct remaining_quantity update was not rejected';
  exception when check_violation then
    null; -- expected: neo_guard_batch_stock_write() raises with errcode check_violation
  end;

  -- No-op and non-stock updates must still work (idempotent sync retries).
  update public.vaccine_batches set remaining_quantity = remaining_quantity where id = v_id;
  update public.vaccine_batches set updated_at = now() where id = v_id;
end
$guard$;

do $stock$
declare
  v_id uuid := gen_random_uuid();
begin
  insert into public.vaccine_batches(id, remaining_quantity) values (v_id, 0);
  insert into public.inventory_transactions(batch_id, quantity) values (v_id, 7);
  if (select remaining_quantity from public.vaccine_batches where id = v_id) <> 7 then
    raise exception 'FAIL: stock trigger write was blocked by the guard';
  end if;
end
$stock$;

-- (b) deleted_by stamped server-side on the is_deleted flip ------------------------
do $stamp$
declare
  v_user uuid := gen_random_uuid();
  v_id uuid := gen_random_uuid();
  v_label text;
begin
  insert into public.profiles(id, display_name, email) values (v_user, 'Dr Who', 'dr@example.test');
  perform set_config(
    'request.jwt.claims',
    jsonb_build_object('sub', v_user::text, 'role', 'authenticated',
                       'app_metadata', jsonb_build_object('role', 'admin'))::text,
    true
  );

  insert into public.vaccine_batches(id, remaining_quantity) values (v_id, 0);
  update public.vaccine_batches
     set is_deleted = true, deleted_by = 'FORGED-BY-CLIENT'
   where id = v_id;
  select deleted_by into v_label from public.vaccine_batches where id = v_id;
  if v_label is distinct from 'Dr Who' then
    raise exception 'FAIL: deleted_by was not stamped from the staff label (got %)', v_label;
  end if;

  update public.vaccine_batches set is_deleted = false where id = v_id;
  select deleted_by into v_label from public.vaccine_batches where id = v_id;
  if v_label is not null then
    raise exception 'FAIL: deleted_by was not cleared on undelete (got %)', v_label;
  end if;
end
$stamp$;

-- (c) RLS UPDATE narrowed to active admin/doctor ----------------------------------
insert into public.profiles(id, display_name, email) values
  ('00000000-0000-0000-0000-000000000101', 'Admin', 'admin@example.test'),
  ('00000000-0000-0000-0000-000000000102', 'Nurse', 'nurse@example.test');
insert into public.patients(id) values ('00000000-0000-0000-0000-000000000201');

-- Nurse (non-admin/doctor): zero rows updated.
set role authenticated;
set request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000102","role":"authenticated","app_metadata":{"role":"nurse"}}';
update public.patients set updated_by = 'nurse' where id = '00000000-0000-0000-0000-000000000201';
reset role;
do $rls_nurse$
begin
  if (select updated_by from public.patients where id = '00000000-0000-0000-0000-000000000201') is not null then
    raise exception 'FAIL: nurse was allowed to update a clinical row';
  end if;
end
$rls_nurse$;

-- Admin: update succeeds.
set role authenticated;
set request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000101","role":"authenticated","app_metadata":{"role":"admin"}}';
update public.patients set updated_by = 'admin' where id = '00000000-0000-0000-0000-000000000201';
reset role;
do $rls_admin$
begin
  if (select updated_by from public.patients where id = '00000000-0000-0000-0000-000000000201') is distinct from 'admin' then
    raise exception 'FAIL: admin update of a clinical row was blocked';
  end if;
end
$rls_admin$;

reset role;
reset request.jwt.claims;
