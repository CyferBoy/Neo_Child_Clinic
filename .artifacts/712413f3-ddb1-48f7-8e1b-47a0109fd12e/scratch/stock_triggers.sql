-- Function to calculate and update batch remaining quantity
CREATE OR REPLACE FUNCTION public.update_batch_stock()
RETURNS TRIGGER AS $$
BEGIN
    -- Only update if quantity is non-zero
    IF (TG_OP = 'INSERT') THEN
        UPDATE public.vaccine_batches
        SET remaining_quantity = remaining_quantity + NEW.quantity,
            updated_at = now()
        WHERE id = NEW.batch_id;
    ELSIF (TG_OP = 'DELETE') THEN
        UPDATE public.vaccine_batches
        SET remaining_quantity = remaining_quantity - OLD.quantity,
            updated_at = now()
        WHERE id = OLD.batch_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Trigger to run the function after any movement in inventory_transactions
DROP TRIGGER IF EXISTS tr_update_batch_stock ON public.inventory_transactions;
CREATE TRIGGER tr_update_batch_stock
AFTER INSERT OR DELETE ON public.inventory_transactions
FOR EACH ROW EXECUTE FUNCTION public.update_batch_stock();

-- Optional: Recalculate everything to ensure consistency
CREATE OR REPLACE FUNCTION public.recalculate_all_stocks()
RETURNS void AS $$
BEGIN
    UPDATE public.vaccine_batches b
    SET remaining_quantity = purchase_quantity + (
        SELECT COALESCE(SUM(quantity), 0)
        FROM public.inventory_transactions
        WHERE batch_id = b.id AND transaction_type != 'PURCHASE'
    );
END;
$$ LANGUAGE plpgsql;
