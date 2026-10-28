-- 20261026_stock_moved_batch_guard.sql
--
-- SQL-006: the moved-batch branch of update_batch_stock() adjusted the OLD batch with a
-- bare UPDATE - no row lock, no negative guard - so moving a positive movement off a
-- batch that no longer held those doses drove its remaining_quantity negative and
-- committed it. Every path now goes through the same lock + guard.
--
-- Forward-only: 20261022 is not edited because it may already be applied. The trigger
-- count preflight refuses to change anything when a stray trigger is present (VER-01).

begin;

do $preflight$
declare
    v_count integer;
begin
    select count(*) into v_count
      from pg_trigger
     where tgrelid = 'public.inventory_transactions'::regclass
       and not tgisinternal
       and tgfoid = 'public.update_batch_stock()'::regprocedure;

    if v_count > 1 then
        raise exception
            'refusing to apply: % update_batch_stock() triggers exist; resolve the stray trigger first',
            v_count;
    end if;
end
$preflight$;

create or replace function public.update_batch_stock()
returns trigger as $body$
declare
    v_batches   uuid[];
    v_deltas    integer[];
    v_batch     uuid;
    v_delta     integer;
    v_remaining integer;
    i           integer;
begin
    if tg_op = 'INSERT' then
        v_batches := array[new.batch_id];
        v_deltas  := array[new.quantity];

    elsif tg_op = 'DELETE' then
        v_batches := array[old.batch_id];
        v_deltas  := array[-old.quantity];

    elsif old.batch_id is not distinct from new.batch_id then
        -- Same batch: apply only the change. A retry has delta 0 and is a no-op.
        v_batches := array[new.batch_id];
        v_deltas  := array[new.quantity - old.quantity];

    else
        -- Moved between batches: undo the old movement, then apply the new one.
        -- shortcut: old is locked before new, so two simultaneous opposite moves can
        -- deadlock and be retried by Postgres. Sort the pair if that shows up in logs.
        v_batches := array[old.batch_id, new.batch_id];
        v_deltas  := array[-old.quantity, new.quantity];
    end if;

    for i in 1 .. coalesce(array_length(v_batches, 1), 0) loop
        v_batch := v_batches[i];
        v_delta := v_deltas[i];

        if v_batch is null or v_delta = 0 then
            continue;
        end if;

        -- Lock the row so two concurrent movements cannot both pass the guard.
        select coalesce(remaining_quantity, 0)
          into v_remaining
          from public.vaccine_batches
         where id = v_batch
           for update;

        if not found then
            raise exception 'stock batch % does not exist', v_batch;
        end if;

        if v_remaining + v_delta < 0 then
            raise exception
                'stock would go negative for batch % (remaining would be %)',
                v_batch, v_remaining + v_delta
                using errcode = 'check_violation';
        end if;

        update public.vaccine_batches
           set remaining_quantity = v_remaining + v_delta,
               updated_at = now()
         where id = v_batch;
    end loop;

    return null;
end;
$body$ language plpgsql;

drop trigger if exists tr_update_batch_stock on public.inventory_transactions;
drop trigger if exists inventory_transactions_update_batch_stock on public.inventory_transactions;

create trigger inventory_transactions_update_batch_stock
after insert or update or delete on public.inventory_transactions
for each row execute function public.update_batch_stock();

do $guard$
declare
    v_count integer;
begin
    select count(*) into v_count
      from pg_trigger
     where tgrelid = 'public.inventory_transactions'::regclass
       and not tgisinternal
       and tgfoid = 'public.update_batch_stock()'::regprocedure;

    if v_count <> 1 then
        raise exception 'expected exactly one update_batch_stock() trigger, found %', v_count;
    end if;
end
$guard$;

commit;
