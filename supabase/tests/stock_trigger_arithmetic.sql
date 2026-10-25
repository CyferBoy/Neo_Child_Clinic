-- Disposable-database regression test for public.update_batch_stock().
--
-- Run against a database that has the schema and the landed trigger fix
-- (20261022_fix_stock_trigger_update_arithmetic.sql) applied:
--   psql "$DATABASE_URL" -f supabase/tests/stock_trigger_arithmetic.sql
--
-- Everything runs in one transaction and is rolled back. Do not run this against
-- production during clinic hours; use a disposable project or a restored snapshot.
\set ON_ERROR_STOP on
begin;

do $test$
declare
    a uuid := gen_random_uuid();
    b uuid := gen_random_uuid();
    c uuid := gen_random_uuid();
    purchase uuid := gen_random_uuid();
    purchase2 uuid := gen_random_uuid();
    vaccination uuid := gen_random_uuid();
    q integer;
    n integer;
begin
    select count(*) into n
      from pg_trigger
     where tgrelid = 'public.inventory_transactions'::regclass
       and not tgisinternal
       and tgfoid = 'public.update_batch_stock()'::regprocedure;
    if n <> 1 then
        raise exception 'expected 1 stock trigger, found %', n;
    end if;

    insert into public.vaccine_batches(id, remaining_quantity) values (a, 0), (b, 50), (c, 100);

    -- INSERT: purchase creates stock.
    insert into public.inventory_transactions(id, batch_id, quantity, transaction_type)
      values (purchase, a, 10, 'PURCHASE');
    select remaining_quantity into q from public.vaccine_batches where id = a;
    if q <> 10 then raise exception 'purchase: expected 10, got %', q; end if;

    -- UPDATE retry with identical values: no-op (old bug made this 0).
    update public.inventory_transactions set quantity = 10 where id = purchase;
    select remaining_quantity into q from public.vaccine_batches where id = a;
    if q <> 10 then raise exception 'purchase retry: expected 10, got %', q; end if;

    -- UPDATE edit: 10 -> 4 applies only the delta.
    update public.inventory_transactions set quantity = 4 where id = purchase;
    select remaining_quantity into q from public.vaccine_batches where id = a;
    if q <> 4 then raise exception 'purchase edit: expected 4, got %', q; end if;
    update public.inventory_transactions set quantity = 10 where id = purchase;

    -- CREATE retry as INSERT ... ON CONFLICT DO NOTHING: the UPDATE trigger must not fire.
    insert into public.inventory_transactions(id, batch_id, quantity, transaction_type)
      values (purchase, a, 10, 'PURCHASE')
      on conflict (id) do nothing;
    select remaining_quantity into q from public.vaccine_batches where id = a;
    if q <> 10 then raise exception 'insert-only retry: expected 10, got %', q; end if;

    -- INSERT: vaccination deducts.
    insert into public.inventory_transactions(id, batch_id, quantity, transaction_type)
      values (vaccination, a, -2, 'VACCINATION');
    select remaining_quantity into q from public.vaccine_batches where id = a;
    if q <> 8 then raise exception 'vaccination: expected 8, got %', q; end if;

    -- UPDATE retry of the vaccination: no-op (old bug made this 10).
    update public.inventory_transactions set quantity = -2 where id = vaccination;
    select remaining_quantity into q from public.vaccine_batches where id = a;
    if q <> 8 then raise exception 'vaccination retry: expected 8, got %', q; end if;

    -- UPDATE edit -2 -> -4: applies the -2 delta.
    update public.inventory_transactions set quantity = -4 where id = vaccination;
    select remaining_quantity into q from public.vaccine_batches where id = a;
    if q <> 6 then raise exception 'vaccination edit: expected 6, got %', q; end if;

    -- UPDATE move to another batch: undo old, apply new.
    update public.inventory_transactions set batch_id = b where id = vaccination;
    select remaining_quantity into q from public.vaccine_batches where id = a;
    if q <> 10 then raise exception 'move undo: expected A=10, got %', q; end if;
    select remaining_quantity into q from public.vaccine_batches where id = b;
    if q <> 46 then raise exception 'move apply: expected B=46, got %', q; end if;

    -- UPDATE retry after the move: no-op.
    update public.inventory_transactions set quantity = -4 where id = vaccination;
    select remaining_quantity into q from public.vaccine_batches where id = b;
    if q <> 46 then raise exception 'move retry: expected B=46, got %', q; end if;

    -- DELETE: undo the movement.
    delete from public.inventory_transactions where id = vaccination;
    select remaining_quantity into q from public.vaccine_batches where id = b;
    if q <> 50 then raise exception 'delete: expected B=50, got %', q; end if;

    -- Negative guard: reject and leave stock untouched.
    begin
        insert into public.inventory_transactions(batch_id, quantity, transaction_type)
          values (a, -11, 'VACCINATION');
        raise exception 'negative guard did not fire';
    exception when check_violation then
        null;
    end;
    select remaining_quantity into q from public.vaccine_batches where id = a;
    if q <> 10 then raise exception 'negative guard: expected A=10, got %', q; end if;

    -- DELETE of the purchase returns the batch to zero.
    delete from public.inventory_transactions where id = purchase;
    select remaining_quantity into q from public.vaccine_batches where id = a;
    if q <> 0 then raise exception 'purchase delete: expected A=0, got %', q; end if;

    -- Positive edit on a non-zero baseline: 100 + 10, edit to +4, then delete.
    insert into public.inventory_transactions(id, batch_id, quantity, transaction_type)
      values (purchase2, c, 10, 'PURCHASE');
    select remaining_quantity into q from public.vaccine_batches where id = c;
    if q <> 110 then raise exception 'positive purchase: expected C=110, got %', q; end if;
    update public.inventory_transactions set quantity = 4 where id = purchase2;
    select remaining_quantity into q from public.vaccine_batches where id = c;
    if q <> 104 then raise exception 'positive edit: expected C=104, got %', q; end if;
    delete from public.inventory_transactions where id = purchase2;
    select remaining_quantity into q from public.vaccine_batches where id = c;
    if q <> 100 then raise exception 'positive delete: expected C=100, got %', q; end if;

    raise notice 'PASS: stock trigger arithmetic';
end
$test$;

rollback;
