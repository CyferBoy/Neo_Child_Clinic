-- Fix the double-applied stock trigger and its UPDATE arithmetic (NCC-002, NCC-003).
--
-- WHY THIS IS URGENT: both triggers below were live on the same table at the same time.
-- `20261016` installed inventory_transactions_update_batch_stock after checking only for
-- its OWN name; the hand-applied legacy trigger was named tr_update_batch_stock, so the
-- guard did not see it. Both are AFTER triggers on public.inventory_transactions calling
-- the same update_batch_stock(), so every INSERT and DELETE applied the movement TWICE:
--
--   stock 10, vaccination quantity -2:
--     trigger 1: guard 10 + (-2) = 8  ok -> remaining = 8
--     trigger 2: guard  8 + (-2) = 6  ok -> remaining = 6      <- 2 doses lost
--
--   stock 2, vaccination quantity -2:
--     trigger 1: guard 2 + (-2) = 0  ok -> remaining = 0
--     trigger 2: guard 0 + (-2) = -2 -> RAISES check_violation
--     an AFTER-trigger RAISE rolls back the whole transaction, so the ledger row was never
--     written even though the dose really was administered.
--
-- So the corruption is a mix of over-deduction and silently dropped ledger rows. This
-- migration stops the bleeding; it does NOT repair history. See the repair section at the
-- bottom - that is a separate, deliberate step.

-- ── NCC-003: remove the duplicate trigger ────────────────────────────────────────────
-- Dropped unconditionally and by exact name. If the legacy trigger is not present this is
-- a no-op, so the migration is safe to run on a database where it was already removed.
drop trigger if exists tr_update_batch_stock on public.inventory_transactions;

-- Guard against a silent regression: a second AFTER trigger on this table that calls
-- update_batch_stock() would double-apply again. Fail loudly here rather than at the next
-- stock movement.
do $guard$
declare
    v_count integer;
begin
    select count(*) into v_count
      from pg_trigger
     where tgrelid = 'public.inventory_transactions'::regclass
       and not tgisinternal
       and tgname <> 'inventory_transactions_update_batch_stock';
    if v_count > 0 then
        raise exception
            'inventory_transactions still has % unexpected trigger(s); stock would be applied more than once',
            v_count;
    end if;
end
$guard$;

-- ── NCC-002: correct the UPDATE branch ───────────────────────────────────────────────
-- The previous ELSE branch applied - OLD.quantity with no + NEW.quantity, and only ever
-- debited OLD.batch_id. Since the client uploads ledger rows with upsert, any retry after
-- a lost response took the UPDATE path and corrupted stock:
--   VACCINATION quantity -2  -> -(-2) = +2 doses invented
--   PURCHASE   quantity +10 -> -(+10) = 10 doses destroyed
create or replace function public.update_batch_stock()
returns trigger as $body$
declare
    v_old_batch public.vaccine_batches.id%type;
    v_new_batch public.vaccine_batches.id%type;
    v_delta integer;
    v_remaining public.vaccine_batches.remaining_quantity%type;
begin
    if tg_op = 'INSERT' then
        v_old_batch := null;
        v_new_batch := new.batch_id;
        v_delta     := new.quantity;
    elsif tg_op = 'UPDATE' then
        v_old_batch := old.batch_id;
        v_new_batch := new.batch_id;
        -- The ledger is append-only in practice, but an UPDATE that moves a row to another
        -- batch must undo the old batch's movement and apply the new one.
        v_delta     := new.quantity - old.quantity;
    else
        v_old_batch := old.batch_id;
        v_new_batch := null;
        v_delta     := -old.quantity;
    end if;

    -- Undo the old batch's effect first so the guard cannot fire spuriously while the new
    -- batch is still short.
    if v_old_batch is not null and v_delta <> old.quantity then
        update public.vaccine_batches
           set remaining_quantity = remaining_quantity - old.quantity,
               updated_at = now()
         where id = v_old_batch;
    end if;

    if v_new_batch is not null then
        select remaining_quantity + v_delta
          into v_remaining
          from public.vaccine_batches
         where id = v_new_batch;

        if v_remaining is not null and v_remaining < 0 then
            raise exception
                'stock would go negative for batch % (remaining would be %)',
                v_new_batch, v_remaining
                using errcode = 'check_violation';
        end if;

        update public.vaccine_batches
           set remaining_quantity = remaining_quantity + v_delta,
               updated_at = now()
         where id = v_new_batch;
    end if;

    return null;
end;
$body$ language plpgsql;

-- ── Verification: run after applying ─────────────────────────────────────────────────
-- Expect exactly one row. If two come back, stock is being applied more than once again.
--
--   select tgname, pg_get_triggerdef(oid)
--     from pg_trigger
--    where tgrelid = 'public.inventory_transactions'::regclass and not tgisinternal;
--
-- Confirm the guard is armed and the trigger is single:
--
--   select count(*) as stock_triggers
--     from pg_trigger
--    where tgrelid = 'public.inventory_transactions'::regclass
--      and not tgisinternal
--      and pg_get_triggerdef(oid) ilike '%update_batch_stock%';
--
-- ── REPAIR OF EXISTING DATA - DELIBERATELY NOT DONE HERE ────────────────────────────
-- Applying this migration stops new corruption. It does NOT correct
-- vaccine_batches.remaining_quantity for movements that already went through the double
-- trigger, and it cannot recover ledger rows that the negative guard rejected outright.
--
-- The ledger is the source of truth, so a rebuild is possible in principle:
--
--   update vaccine_batches b
--      set remaining_quantity = coalesce((
--              select sum(it.quantity) from inventory_transactions it
--               where it.batch_id = b.id), 0);
--
-- Do NOT run that blindly. It is only correct if the ledger is complete, and the rejected
-- rows described above are exactly the ones missing from it - so it will UNDER-count every
-- batch that was ever driven to zero. Reconcile against a device (or physical stock)
-- first, and decide per batch whether the ledger or the current value is closer.
