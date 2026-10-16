-- 20260816_security_hardening.sql
-- Supabase security hardening for the single-clinic Android application.

begin;

-- Standard audit timestamps on all existing public tables.
do $$
declare
  t record;
begin
  for t in
    select tablename
    from pg_tables
    where schemaname = 'public'
  loop
    execute format('alter table public.%I add column if not exists created_at timestamptz not null default now()', t.tablename);
    execute format('alter table public.%I add column if not exists updated_at timestamptz not null default now()', t.tablename);
  end loop;
end $$;

-- Profile soft-delete fields.
alter table public.profiles
  add column if not exists is_deleted boolean not null default false;
alter table public.profiles
  add column if not exists deleted_at timestamptz;
alter table public.profiles
  add column if not exists deleted_by uuid;

-- Accountability fields where supported by the application schema.
do $$
declare
  t record;
begin
  for t in
    select tablename
    from pg_tables
    where schemaname = 'public'
      and tablename not in ('sync_queue', 'widget_due_cache')
  loop
    execute format('alter table public.%I add column if not exists created_by uuid', t.tablename);
    execute format('alter table public.%I add column if not exists updated_by uuid', t.tablename);
  end loop;
end $$;

-- Keep updated_at current.
create or replace function public.set_updated_at()
returns trigger
language plpgsql
security invoker
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

do $$
declare
  t record;
begin
  for t in
    select tablename
    from pg_tables
    where schemaname = 'public'
  loop
    execute format('drop trigger if exists set_updated_at on public.%I', t.tablename);
    execute format(
      'create trigger set_updated_at before update on public.%I for each row execute function public.set_updated_at()',
      t.tablename
    );
  end loop;
end $$;

-- Admin helper used by RLS. SECURITY DEFINER prevents recursive profile-policy evaluation.
create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.profiles
    where id = auth.uid()
      and role = 'admin'
      and is_active = true
      and is_deleted = false
  );
$$;

revoke all on function public.is_admin() from public;
grant execute on function public.is_admin() to authenticated;

-- Prevent a normal user from escalating privileges through their own profile row.
create or replace function public.protect_profile_privileged_fields()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is not null and not public.is_admin() and auth.uid() = old.id then
    if new.id <> old.id
       or new.role <> old.role
       or new.is_active <> old.is_active
       or new.is_deleted <> old.is_deleted
       or coalesce(new.deleted_by::text, '') <> coalesce(old.deleted_by::text, '')
       or new.email <> old.email then
      raise exception 'Only an administrator may change privileged profile fields';
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists protect_profile_privileged_fields on public.profiles;
create trigger protect_profile_privileged_fields
before update on public.profiles
for each row execute function public.protect_profile_privileged_fields();

-- Profiles RLS.
alter table public.profiles enable row level security;

drop policy if exists "profiles_select_own_or_admin" on public.profiles;
create policy "profiles_select_own_or_admin"
on public.profiles for select
to authenticated
using (
  (id = auth.uid() and is_deleted = false)
  or public.is_admin()
);

drop policy if exists "profiles_update_own" on public.profiles;
create policy "profiles_update_own"
on public.profiles for update
to authenticated
using (id = auth.uid() and is_deleted = false)
with check (id = auth.uid() and is_deleted = false);

-- Staff creation/deletion/role changes are performed by the manage-staff Edge Function
-- with the service role and therefore intentionally have no client insert/delete policy.

-- User device RLS.
alter table public.user_devices enable row level security;

-- Remove duplicate FCM tokens before adding the uniqueness guarantee.
with ranked as (
  select ctid,
         row_number() over (
           partition by fcm_token
           order by coalesce(last_seen_at, created_at) desc nulls last, ctid desc
         ) as rn
  from public.user_devices
  where fcm_token is not null
)
delete from public.user_devices d
using ranked r
where d.ctid = r.ctid
  and r.rn > 1;

create unique index if not exists user_devices_fcm_token_uidx
on public.user_devices(fcm_token);

drop policy if exists "user_devices_select_own" on public.user_devices;
create policy "user_devices_select_own"
on public.user_devices for select
to authenticated
using (user_id = auth.uid());

drop policy if exists "user_devices_insert_own" on public.user_devices;
create policy "user_devices_insert_own"
on public.user_devices for insert
to authenticated
with check (user_id = auth.uid());

drop policy if exists "user_devices_update_own" on public.user_devices;
create policy "user_devices_update_own"
on public.user_devices for update
to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

drop policy if exists "user_devices_delete_own" on public.user_devices;
create policy "user_devices_delete_own"
on public.user_devices for delete
to authenticated
using (user_id = auth.uid());

commit;
