-- Inventory stock must never go negative.
--
-- `update_batch_stock()` was applied to production by hand (it lives only in a scratch
-- file, never in a migration), so this versions it and adds the guard: two offline
-- devices can both spend the last dose, and the second upload used to drive
-- `vaccine_batches.remaining_quantity` below zero with no error anywhere.
--
-- The whole block is skipped on databases where the function does not exist: there the
-- client's absolute `vaccine_batches` sync is the only writer and it clamps at 0, and
-- installing the trigger on top of that would double-apply every transaction.
begin;

do $do$
begin
  if exists (
    select 1
      from pg_proc p
      join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public'
       and p.proname = 'update_batch_stock'
  ) then
    execute $fn$
    CREATE OR REPLACE FUNCTION public.update_batch_stock()
    RETURNS TRIGGER AS $body$
    DECLARE
        v_batch_id public.vaccine_batches.id%TYPE;
        v_remaining public.vaccine_batches.remaining_quantity%TYPE;
    BEGIN
        IF (TG_OP = 'INSERT') THEN
            v_batch_id := NEW.batch_id;
        ELSE
            v_batch_id := OLD.batch_id;
        END IF;

        SELECT remaining_quantity
               + CASE WHEN TG_OP = 'INSERT' THEN NEW.quantity ELSE -OLD.quantity END
          INTO v_remaining
          FROM public.vaccine_batches
         WHERE id = v_batch_id;

        -- After trigger, so raising rolls back the transaction that inserted the
        -- stock movement: the losing device's row fails loudly instead of silently
        -- booking a dose the clinic does not have.
        IF v_remaining IS NOT NULL AND v_remaining < 0 THEN
            RAISE EXCEPTION
                'stock would go negative for batch % (remaining would be %)',
                v_batch_id, v_remaining
                USING ERRCODE = 'check_violation';
        END IF;

        IF (TG_OP = 'INSERT') THEN
            UPDATE public.vaccine_batches
               SET remaining_quantity = remaining_quantity + NEW.quantity,
                   updated_at = now()
             WHERE id = NEW.batch_id;
        ELSE
            UPDATE public.vaccine_batches
               SET remaining_quantity = remaining_quantity - OLD.quantity,
                   updated_at = now()
             WHERE id = OLD.batch_id;
        END IF;

        RETURN NULL;
    END;
    $body$ LANGUAGE plpgsql;
    $fn$;
  end if;
end $do$;

commit;
