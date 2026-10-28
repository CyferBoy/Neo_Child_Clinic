-- 20261026_schedule_rate_limit_cleanup.sql
--
-- SQL-032: cleanup_write_rate_limits() existed but nothing ever called it, so
-- api_write_rate_limits grew one row per (user, minute) forever. Schedule it when
-- pg_cron is available; the migration stays deployable when it is not.

begin;

do $$
begin
  if not exists (select 1 from pg_extension where extname = 'pg_cron') then
    begin
      execute 'create extension if not exists pg_cron';
    exception when others then
      raise notice 'pg_cron not enabled; schedule public.cleanup_write_rate_limits() manually: %', sqlerrm;
    end;
  end if;

  if exists (select 1 from pg_extension where extname = 'pg_cron') then
    begin
      perform cron.schedule(
        'cleanup-write-rate-limits',
        '15 * * * *',
        'select public.cleanup_write_rate_limits()'
      );
    exception when others then
      raise notice 'pg_cron present but scheduling failed; schedule public.cleanup_write_rate_limits() manually: %', sqlerrm;
    end;
  end if;
end
$$;

commit;
