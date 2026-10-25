-- Disposable-database test for public.inventory_reconciliation.
--
-- Run after the landed trigger-fix pair (20261021001000 + 20261021002000)
-- and the reconciliation view migration are applied:
--   psql "$DATABASE_URL" -f supabase/tests/inventory_reconciliation_test.sql
--
-- One transaction, rolled back. Do not run against production during clinic hours.
\set ON_ERROR_STOP on
begin;

do $test$
declare
    consistent uuid := gen_random_uuid();
    drifted uuid := gen_random_uuid();
    opening uuid := gen_random_uuid();
    empty uuid := gen_random_uuid();
    n integer;
    d bigint;
    rows bigint;
begin
    -- Recreating the view without security_invoker would bypass the underlying RLS.
    if not exists (
        select 1
          from pg_class
         where oid = 'public.inventory_reconciliation'::regclass
           and 'security_invoker=true' = any(coalesce(reloptions, '{}'))
    ) then
        raise exception 'inventory_reconciliation must be created with security_invoker=true';
    end if;

    insert into public.vaccine_batches(id, batch_number, remaining_quantity) values
        (consistent, 'CONSISTENT', 0),
        (drifted,    'DRIFTED',    0),
        (opening,    'OPENING',    0),
        (empty,      'EMPTY',      0);

    -- Trigger moves consistent and drifted to 10.
    insert into public.inventory_transactions(batch_id, quantity, transaction_type) values
        (consistent, 10, 'PURCHASE'),
        (drifted,    10, 'PURCHASE');

    -- Keep consistent at 10; make drifted disagree with its ledger; give opening a
    -- positive balance with no ledger rows; leave empty at zero with no ledger rows.
    update public.vaccine_batches set remaining_quantity = 9 where id = drifted;
    update public.vaccine_batches set remaining_quantity = 5 where id = opening;

    select count(*) into n
      from public.inventory_reconciliation
     where batch_id in (consistent, drifted, opening, empty);
    if n <> 2 then
        raise exception 'expected 2 drifted batches, got %', n;
    end if;

    select drift, ledger_rows into d, rows
      from public.inventory_reconciliation where batch_id = drifted;
    if d <> -1 or rows <> 1 then
        raise exception 'drifted expected drift=-1 ledger_rows=1, got drift=% rows=%', d, rows;
    end if;

    select drift, ledger_rows into d, rows
      from public.inventory_reconciliation where batch_id = opening;
    if d <> 5 or rows <> 0 then
        raise exception 'opening expected drift=5 ledger_rows=0, got drift=% rows=%', d, rows;
    end if;

    if exists (
        select 1 from public.inventory_reconciliation
         where batch_id in (consistent, empty)
    ) then
        raise exception 'consistent/empty batches must not appear in the view';
    end if;

    raise notice 'PASS: inventory reconciliation view';
end
$test$;

rollback;
