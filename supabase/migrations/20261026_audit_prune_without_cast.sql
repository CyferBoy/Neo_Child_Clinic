-- 20261026_audit_prune_without_cast.sql
--
-- SQL-020: prune_audit_logs() filtered with a regex and then cast the text column to
-- timestamptz. The regex admits impossible dates ("2025-13-45"), and the cast is applied
-- before the date comparison can exclude them, so one malformed row raised and aborted
-- the whole nightly delete - the prune then never ran again.
--
-- Compare the ISO date prefix as text instead. No cast, so no row can abort the prune.
-- shortcut: retention is date-granular and malformed dates that sort after the cutoff
-- are kept instead of deleted; upgrade to a typed column if audit timestamps ever matter
-- to the day.

begin;

create or replace function public.prune_audit_logs()
returns integer
language sql
security definer
set search_path = public
as $$
  with deleted as (
    delete from public.audit_logs
    where timestamp ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}'
      and substring(timestamp from 1 for 10)
          < to_char((now() at time zone 'UTC') - interval '1 year', 'YYYY-MM-DD')
    returning 1
  )
  select count(*)::integer from deleted;
$$;

revoke all on function public.prune_audit_logs() from public;

commit;
