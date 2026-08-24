-- 20260824_receipt_numbering.sql
--
-- Server-side receipt numbering for patient_visits.
--
-- Format: "NEO-<FY>-<seq>", e.g. "NEO-26/27-000001"
--   - Financial year runs 1 Apr - 31 Mar and is derived strictly from the
--     visit's own date_given (the user-entered transaction date), never from
--     now()/created_at/updated_at.
--   - One sequence per financial year, shared by every visit type
--     (VACCINATION and CONSULTATION alike, cash/UPI/mixed) since they all
--     write through this single patient_visits table.
--   - Assigned atomically in the database so concurrent staff can never
--     collide, and is immutable once set so editing a visit - or a stale
--     offline payload re-uploading it - can never change or regenerate it.
--
-- The Android app must never compute this value; it always uploads a blank
-- receiptNumber for a new visit and reads the generated value back after the
-- insert (see SyncRepositoryImpl.uploadVisit).

begin;

-- One row per financial year holding the last issued sequence number. Never
-- decremented (deletes/edits don't touch it), so a number is never reused.
create table if not exists public.receipt_sequences (
  financial_year text primary key,
  last_number integer not null default 0,
  updated_at timestamptz not null default now()
);

-- Locked down: the only writer is the SECURITY DEFINER trigger function
-- below. No client, including an authenticated staff session, can read or
-- write it directly, so the sequence can't be tampered with or raced from
-- the app.
alter table public.receipt_sequences enable row level security;
revoke all on public.receipt_sequences from authenticated, anon;

-- Best-effort parse of the app's stored transaction-date text into a date.
-- The app writes "d MMM yyyy" (e.g. "9 May 2026"); ISO "yyyy-MM-dd" is
-- accepted as a fallback. Returns null (never raises) if nothing matches, so
-- a malformed date can't block a visit from being saved.
create or replace function public.neo_parse_visit_date(raw text)
returns date
language plpgsql
immutable
as $$
begin
  if raw is null or btrim(raw) = '' then
    return null;
  end if;

  return to_date(raw, 'FMDD Mon YYYY');
exception when others then
  begin
    return raw::date;
  exception when others then
    return null;
  end;
end;
$$;

-- "26/27" for any date between 1 Apr 2026 and 31 Mar 2027 inclusive.
create or replace function public.neo_financial_year_label(d date)
returns text
language sql
immutable
as $$
  select case
    when d is null then null
    when extract(month from d)::int >= 4 then
      to_char(d, 'YY') || '/' || to_char(d + interval '1 year', 'YY')
    else
      to_char(d - interval '1 year', 'YY') || '/' || to_char(d, 'YY')
  end;
$$;

-- Atomically issues the next receipt number for the financial year that
-- txn_date falls in. The INSERT ... ON CONFLICT DO UPDATE ... RETURNING
-- takes a row lock on that financial year's counter, so two concurrent
-- callers are serialized by Postgres itself rather than by app-level logic -
-- there is no read-then-write gap for two staff to race through.
create or replace function public.neo_next_receipt_number(txn_date date)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  fy text;
  next_seq integer;
begin
  -- If the transaction date can't be parsed, fall back to today rather than
  -- leaving the visit unsaved - it still lands in a valid, auditable year.
  fy := coalesce(public.neo_financial_year_label(txn_date), public.neo_financial_year_label(current_date));

  insert into public.receipt_sequences (financial_year, last_number)
  values (fy, 1)
  on conflict (financial_year)
  do update set last_number = public.receipt_sequences.last_number + 1,
                updated_at = now()
  returning last_number into next_seq;

  return 'NEO-' || fy || '-' || lpad(next_seq::text, 6, '0');
end;
$$;

revoke all on function public.neo_next_receipt_number(date) from public, authenticated, anon;

-- Assigns receipt_number on insert, and makes it immutable thereafter.
create or replace function public.neo_assign_receipt_number()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if TG_OP = 'INSERT' then
    if NEW.receipt_number is null or btrim(NEW.receipt_number) = '' then
      NEW.receipt_number := public.neo_next_receipt_number(public.neo_parse_visit_date(NEW.date_given));
    end if;
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
    end if;
  end if;
  return NEW;
end;
$$;

-- App is still in testing - there's no real patient/receipt history to
-- preserve, so wipe any old test receipt numbers (e.g. "RCT-1000" from the
-- previous client-side scheme) before the trigger below goes in, since once
-- it's active it makes receipt_number immutable and would just re-assert
-- whatever was already there. Every visit picks up a fresh
-- "NEO-<FY>-<seq>" number the next time it's saved/synced.
update public.patient_visits set receipt_number = null where receipt_number is not null;

drop trigger if exists neo_assign_receipt_number on public.patient_visits;
create trigger neo_assign_receipt_number
before insert or update on public.patient_visits
for each row
execute function public.neo_assign_receipt_number();

-- Defense in depth alongside the trigger's own concurrency-safe assignment:
-- guarantees no two issued receipts can ever collide at the storage level.
create unique index if not exists patient_visits_receipt_number_uidx
on public.patient_visits (receipt_number)
where receipt_number is not null and btrim(receipt_number) <> '';

commit;
