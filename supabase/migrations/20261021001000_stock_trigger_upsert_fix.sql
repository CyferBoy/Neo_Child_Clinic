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
               + CASE WHEN TG_OP = 'INSERT' THEN NEW.quantity ELSE NEW.quantity - OLD.quantity END
          INTO v_remaining
          FROM public.vaccine_batches
         WHERE id = v_batch_id;

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
               SET remaining_quantity = remaining_quantity - OLD.quantity + NEW.quantity,
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