begin;

-- ── NCC-AUDIT-001: Correct stock trigger batch reassignment on UPDATE ──────────────────
-- The previous upsert fix handled same-batch quantity deltas correctly, but if an
-- inventory transaction row was updated to reassign its `batch_id` to a different batch,
-- the trigger only adjusted the old batch and failed to credit/debit the new batch.
-- This migration updates `update_batch_stock()` to explicitly check if `batch_id` changed,
-- fully reversing the old batch and correctly validating/applying the movement on the new batch.

create or replace function public.update_batch_stock()
returns trigger as $body$
declare
    v_remaining public.vaccine_batches.remaining_quantity%type;
begin
    if tg_op = 'INSERT' then
        -- INSERT: apply new quantity to new batch
        select remaining_quantity + new.quantity
          into v_remaining
          from public.vaccine_batches
         where id = new.batch_id;

        if v_remaining is not null and v_remaining < 0 then
            raise exception
                'stock would go negative for batch % (remaining would be %)',
                new.batch_id, v_remaining
                using errcode = 'check_violation';
        end if;

        update public.vaccine_batches
           set remaining_quantity = remaining_quantity + new.quantity,
               updated_at = now()
         where id = new.batch_id;

    elsif tg_op = 'UPDATE' then
        if old.batch_id is distinct from new.batch_id then
            -- BATCH REASSIGNMENT: completely reverse old batch, apply to new batch
            update public.vaccine_batches
               set remaining_quantity = remaining_quantity - old.quantity,
                   updated_at = now()
             where id = old.batch_id;

            select remaining_quantity + new.quantity
              into v_remaining
              from public.vaccine_batches
             where id = new.batch_id;

            if v_remaining is not null and v_remaining < 0 then
                raise exception
                    'stock would go negative for batch % (remaining would be %)',
                    new.batch_id, v_remaining
                    using errcode = 'check_violation';
            end if;

            update public.vaccine_batches
               set remaining_quantity = remaining_quantity + new.quantity,
                   updated_at = now()
             where id = new.batch_id;
        else
            -- SAME BATCH: apply net quantity delta
            declare
                v_delta integer := new.quantity - old.quantity;
            begin
                select remaining_quantity + v_delta
                  into v_remaining
                  from public.vaccine_batches
                 where id = new.batch_id;

                if v_remaining is not null and v_remaining < 0 then
                    raise exception
                        'stock would go negative for batch % (remaining would be %)',
                        new.batch_id, v_remaining
                        using errcode = 'check_violation';
                end if;

                update public.vaccine_batches
                   set remaining_quantity = remaining_quantity + v_delta,
                       updated_at = now()
                 where id = new.batch_id;
            end;
        end if;

    elsif tg_op = 'DELETE' then
        -- DELETE: reverse old quantity on old batch
        update public.vaccine_batches
           set remaining_quantity = remaining_quantity - old.quantity,
               updated_at = now()
         where id = old.batch_id;
    end if;

    return null;
end;
$body$ language plpgsql;

commit;