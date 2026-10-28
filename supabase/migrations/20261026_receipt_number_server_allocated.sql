-- 20261026_receipt_number_server_allocated.sql
--
-- SQL-018: the trigger kept a non-blank client-supplied receipt_number on INSERT, so any
-- staff token could choose an out-of-series or backdated number; only uniqueness was
-- enforced and the audit trail could no longer prove the number came from the sequence.
-- INSERT now always allocates. UPDATE still never changes an issued number.

begin;

create or replace function public.neo_assign_receipt_number()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if TG_OP = 'INSERT' then
    -- The sequence is the only source of a receipt number. Ignore whatever the client sent.
    NEW.receipt_number := public.neo_next_receipt_number(public.neo_parse_visit_date(NEW.date_given));
  elsif TG_OP = 'UPDATE' then
    if OLD.receipt_number is not null and btrim(OLD.receipt_number) <> '' then
      -- Already issued - editing the visit (including a stale/offline
      -- upsert of an older local copy) must never change it.
      NEW.receipt_number := OLD.receipt_number;
    elsif NEW.receipt_number is null or btrim(NEW.receipt_number) = '' then
      -- The row was never actually given a number yet (e.g. an offline edit
      -- uploaded before the original create ever synced) - this is still
      -- effectively its first assignment.
      NEW.receipt_number := public.neo_next_receipt_number(public.neo_parse_visit_date(NEW.date_given));
    else
      -- A row that had no number never issued the one the client is sending; allocate.
      NEW.receipt_number := public.neo_next_receipt_number(public.neo_parse_visit_date(NEW.date_given));
    end if;
  end if;
  return NEW;
end;
$$;

commit;
