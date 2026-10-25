begin;

-- DA-10: audit_logs grew without bound on both the device and the server. The app
-- prunes synced local rows after 90 days (NeoChildApp); this keeps one year remotely.
-- Audit screens read the server copy, so the local window only affects offline views
-- and the reminder audit trail.
create or replace function public.prune_audit_logs()
returns integer
language sql
security definer
set search_path = public
as $$
  with deleted as (
    delete from public.audit_logs
    where timestamp ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}[ T]'
      and timestamp::timestamptz < now() - interval '1 year'
    returning 1
  )
  select count(*)::integer from deleted;
$$;

revoke all on function public.prune_audit_logs() from public;

create index if not exists audit_logs_timestamp_idx
  on public.audit_logs (timestamp);

-- Schedule the prune when pg_cron is available. If it is not, enable it in the
-- Supabase dashboard (or call public.prune_audit_logs() from an existing scheduler);
-- the migration stays deployable either way.
do $$
begin
  if not exists (select 1 from pg_extension where extname = 'pg_cron') then
    begin
      execute 'create extension if not exists pg_cron';
    exception when others then
      raise notice 'pg_cron not enabled; schedule public.prune_audit_logs() manually: %', sqlerrm;
    end;
  end if;

  if exists (select 1 from pg_extension where extname = 'pg_cron') then
    begin
      perform cron.schedule(
        'prune-audit-logs',
        '0 3 * * *',
        'select public.prune_audit_logs()'
      );
    exception when others then
      raise notice 'pg_cron present but scheduling failed; schedule public.prune_audit_logs() manually: %', sqlerrm;
    end;
  end if;
end
$$;

commit;
