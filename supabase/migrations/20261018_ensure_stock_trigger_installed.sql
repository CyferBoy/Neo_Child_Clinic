-- NCC-007: a batch synced without the stock trigger lands at remaining_quantity = 0 forever.
--
-- SyncUploader.uploadBatch hard-codes remaining_quantity = 0 on CREATE and relies on
-- inventory_transactions_update_batch_stock to raise it to the purchase quantity when the
-- paired PURCHASE ledger row arrives in the same sync group. If that trigger is not installed,
-- nothing raises it: every batch stays at zero stock, silently, with no error on either side.
--
-- 20261016 installed the trigger inside a do-block that no-ops when update_batch_stock() or
-- inventory_transactions is missing. A no-op that silently breaks all stock accounting is
-- worse than a failed deployment, so this one installs the trigger if it can and fails loudly
-- if it cannot.
--
-- Written as its own migration rather than appended to 20261017: a database that already
-- applied 20261017 will not re-run it, and this is exactly the case that must be caught.

begin;

-- 1. Install the trigger if the function and table exist but the trigger does not. This
--    repairs the silent-skip case from 20261016 rather than just reporting it.
do $ensure$
begin
    if exists (
      select 1 from pg_proc p
        join pg_namespace n on n.oid = p.pronamespace
       where n.nspname = 'public' and p.proname = 'update_batch_stock'
    )
    and to_regclass('public.inventory_transactions') is not null
    and not exists (
      select 1 from pg_trigger
       where tgname = 'inventory_transactions_update_batch_stock'
         and tgrelid = to_regclass('public.inventory_transactions')
    ) then
        execute 'create trigger inventory_transactions_update_batch_stock
                   after insert or update or delete on public.inventory_transactions
                   for each row execute function public.update_batch_stock()';
        raise notice 'NCC-007: installed the missing inventory_transactions_update_batch_stock trigger.';
    end if;
end
$ensure$;

-- 2. If it is still not there, the deployment is unsound. Refuse to continue rather than let
--    every synced batch settle at zero stock.
do $guard$
declare
    v_missing text;
begin
    if to_regclass('public.inventory_transactions') is null then
        v_missing := 'public.inventory_transactions does not exist';
    elsif not exists (
      select 1 from pg_proc p
        join pg_namespace n on n.oid = p.pronamespace
       where n.nspname = 'public' and p.proname = 'update_batch_stock'
    ) then
        v_missing := 'public.update_batch_stock() does not exist';
    elsif not exists (
      select 1 from pg_trigger
       where tgname = 'inventory_transactions_update_batch_stock'
         and tgrelid = 'public.inventory_transactions'::regclass
    ) then
        v_missing := 'trigger inventory_transactions_update_batch_stock is not installed on inventory_transactions';
    end if;

    if v_missing is not null then
        raise exception
            'NCC-007: stock accounting cannot work - %. SyncUploader sends remaining_quantity = 0 on every batch CREATE and depends on this trigger to raise it, so without it every synced batch is stuck at zero stock with no error. Apply the migrations that create update_batch_stock() before syncing.', v_missing;
    end if;
end
$guard$;

commit;

-- Verify - expect one row:
--
--   select tgname, pg_get_triggerdef(oid)
--     from pg_trigger
--    where tgrelid = 'public.inventory_transactions'::regclass
--      and not tgisinternal
--      and pg_get_triggerdef(oid) ilike '%update_batch_stock%';
