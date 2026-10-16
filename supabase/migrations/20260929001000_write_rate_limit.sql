-- Server-side write-abuse guard.
-- This is intentionally database-backed so it applies to direct PostgREST writes too;
-- Android/client-side throttling cannot protect the database from a bypassing client.
-- Service-role operations (Edge Functions / trusted backend jobs) bypass this guard.

create table if not exists public.api_write_rate_limits (
  user_id uuid not null,
  window_started_at timestamptz not null,
  request_count integer not null default 0,
  primary key (user_id, window_started_at)
);

alter table public.api_write_rate_limits enable row level security;
revoke all on table public.api_write_rate_limits from anon, authenticated;

create or replace function public.enforce_write_rate_limit()
returns trigger
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  actor uuid := auth.uid();
  window_start timestamptz := date_trunc('minute', now());
  new_count integer;
  max_writes constant integer := 600;
begin
  -- Trusted server-side operations are not subject to the client mutation limit.
  if coalesce(auth.role(), '') = 'service_role' then
    return coalesce(NEW, OLD);
  end if;

  if actor is null then
    raise exception using errcode = '42501', message = 'Authentication required';
  end if;

  insert into public.api_write_rate_limits(user_id, window_started_at, request_count)
  values (actor, window_start, 1)
  on conflict (user_id, window_started_at)
  do update set request_count = public.api_write_rate_limits.request_count + 1
  returning request_count into new_count;

  if new_count > max_writes then
    raise exception using errcode = '57014', message = 'Write rate limit exceeded; retry later';
  end if;

  return coalesce(NEW, OLD);
end;
$$;

revoke all on function public.enforce_write_rate_limit() from public;

-- Attach the guard only to client-facing business tables. Missing tables are skipped so
-- this migration remains compatible with deployments that have a smaller schema.
do $$
declare
  table_name text;
begin
  foreach table_name in array array[
    'patients','patient_visits','patient_notes','vaccination_items','vaccines','vaccine_batches',
    'inventory_transactions','waste_records','borrow_records','borrow_returns','reminders',
    'consultations','consultation_todos','vaccination_todos','personal_vaccine_reminders',
    'finance_transactions','expenses','doctor_weekly_slots','doctor_slot_exceptions','audit_logs'
  ] loop
    if to_regclass('public.' || table_name) is not null then
      execute format('drop trigger if exists trg_%I_write_rate_limit on public.%I', table_name, table_name);
      execute format(
        'create trigger trg_%I_write_rate_limit before insert or update or delete on public.%I for each row execute function public.enforce_write_rate_limit()',
        table_name, table_name
      );
    end if;
  end loop;
end $$;

-- Keep the small rate-limit table from growing forever. This cleanup is intentionally
-- opportunistic; old rows do not affect enforcement of the current minute window.
create or replace function public.cleanup_write_rate_limits()
returns integer
language sql
security definer
set search_path = public
as $$
  with deleted as (
    delete from public.api_write_rate_limits
    where window_started_at < now() - interval '2 hours'
    returning 1
  )
  select count(*)::integer from deleted;
$$;

revoke all on function public.cleanup_write_rate_limits() from public;
