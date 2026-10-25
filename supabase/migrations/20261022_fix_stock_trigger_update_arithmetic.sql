-- Fix the remaining UPDATE arithmetic bug in public.update_batch_stock().
--
-- 20261017 replaced the duplicate-trigger problem but kept a conditional undo in the
-- UPDATE branch:
--
--     v_delta := new.quantity - old.quantity;
--     if v_delta <> old.quantity then
--         remaining := remaining - old.quantity;   -- undo
--     end if;
--     remaining := remaining + v_delta;            -- apply
--
-- The undo condition is true for almost every edit (it is false only when
-- new.quantity = 2 * old.quantity). A retry (new = old) therefore invents stock:
-- 8 remaining, retry a -2 vaccination -> 8 - (-2) + 0 = 10. A purchase retry
-- (10 remaining, retry +10) -> 10 - 10 + 0 = 0. Moving a row between batches also
-- applies new - old to the destination instead of new.
--
-- This migration replaces the function with a single-application version:
--   INSERT              -> apply new.quantity
--   DELETE              -> undo old.quantity
--   UPDATE same batch   -> apply new.quantity - old.quantity
--   UPDATE moved batch  -> undo old.quantity on the old batch, apply new.quantity
--
-- It also recreates exactly one stock trigger and fails if another trigger calling
-- this function survives. It does NOT repair historical stock; see the note at the
-- bottom and the Phase 2 gate in docs/Knowledge_Base/Database-Audit/.

-- Remove both names used in this repository. The legacy trigger was hand-applied;
-- 20261016 installed the second name. Dropping and recreating is transactional.
drop trigger if exists tr_update_batch_stock on public.inventory_transactions;
drop trigger if exists inventory_transactions_update_batch_stock on public.inventory_transactions;

create or replace function public.update_batch_stock()
returns trigger as $body$
declare
    v_new_batch public.vaccine_batches.id%type;
    v_delta integer;
    v_remaining public.vaccine_batches.remaining_quantity%type;
begin
    if tg_op = 'INSERT' then
        v_new_batch := new.batch_id;
        v_delta     := new.quantity;

    elsif tg_op = 'UPDATE' then
        if old.batch_id is not distinct from new.batch_id then
            -- Same batch: apply only the change. A retry has delta 0 and is a no-op.
            v_new_batch := new.batch_id;
            v_delta     := new.quantity - old.quantity;
        else
            -- Moved between batches: undo the old movement, then apply the new one.
            if old.batch_id is not null then
                update public.vaccine_batches
                   set remaining_quantity = coalesce(remaining_quantity, 0) - old.quantity,
                       updated_at = now()
                 where id = old.batch_id;
            end if;
            v_new_batch := new.batch_id;
            v_delta     := new.quantity;
        end if;

    else
        v_new_batch := old.batch_id;
        v_delta     := -old.quantity;
    end if;

    if v_new_batch is null or v_delta = 0 then
        return null;
    end if;

    -- Lock the row so two concurrent movements cannot both pass the guard.
    select coalesce(remaining_quantity, 0)
      into v_remaining
      from public.vaccine_batches
     where id = v_new_batch
       for update;

    if v_remaining is null then
        raise exception 'stock batch % does not exist', v_new_batch;
    end if;

    if v_remaining + v_delta < 0 then
        raise exception
            'stock would go negative for batch % (remaining would be %)',
            v_new_batch, v_remaining + v_delta
            using errcode = 'check_violation';
    end if;

    update public.vaccine_batches
       set remaining_quantity = v_remaining + v_delta,
           updated_at = now()
     where id = v_new_batch;

    return null;
end;
$body$ language plpgsql;

create trigger inventory_transactions_update_batch_stock
after insert or update or delete on public.inventory_transactions
for each row execute function public.update_batch_stock();

-- There must be exactly one trigger calling this function. Unrelated triggers on the
-- table are allowed; a second stock trigger is the double-application bug.
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
        raise exception
            'expected exactly one update_batch_stock() trigger, found %', v_count;
    end if;
end
$guard$;

-- Verification after apply:
--
--   select tgname, pg_get_triggerdef(oid)
--     from pg_trigger
--    where tgrelid = 'public.inventory_transactions'::regclass and not tgisinternal;
--
-- Run supabase/tests/stock_trigger_arithmetic.sql on a disposable database to check
-- insert, retry, edit, move, delete and the negative guard.
--
-- Historical repair is deliberately not done here. The ledger is the source of truth,
-- but rows rejected by the old negative guard are missing from it. Reconcile against
-- physical stock / a device before rebuilding vaccine_batches.remaining_quantity.
