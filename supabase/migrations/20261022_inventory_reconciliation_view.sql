-- Diagnostic view for stock drift. It does NOT repair anything.
--
-- remaining_quantity is maintained by the inventory_transactions trigger (see
-- 20261021001000 + 20261021002000). If every movement is in the ledger,
-- remaining_quantity equals the sum of that batch's ledger quantities. This view lists
-- only the batches where the two disagree, with enough context to tell a real mismatch
-- from a legitimate opening balance that never had a ledger row.
--
-- security_invoker makes the underlying RLS policies apply to the querying role. Do not
-- recreate this view without it, or it will read as its owner and bypass RLS.
create or replace view public.inventory_reconciliation
with (security_invoker = true)
as
select
    b.id                                                   as batch_id,
    b.vaccine_id,
    b.batch_number,
    coalesce(b.remaining_quantity, 0)                      as recorded_quantity,
    coalesce(sum(t.quantity), 0)                           as ledger_quantity,
    coalesce(b.remaining_quantity, 0) - coalesce(sum(t.quantity), 0) as drift,
    count(t.id)                                            as ledger_rows,
    max(t.created_at)                                      as last_ledger_at
from public.vaccine_batches b
left join public.inventory_transactions t on t.batch_id = b.id
group by b.id, b.vaccine_id, b.batch_number, b.remaining_quantity
having coalesce(b.remaining_quantity, 0) <> coalesce(sum(t.quantity), 0);

comment on view public.inventory_reconciliation is
    'Batches whose remaining_quantity differs from the sum of their inventory ledger rows. Diagnostic only; opening balances without ledger rows appear as drift.';

-- The view carries no patient data, but keep anon out. Active staff already have SELECT
-- on both underlying tables; security_invoker applies their RLS policies.
do $grants$
begin
    if exists (select 1 from pg_roles where rolname = 'anon') then
        execute 'revoke all on public.inventory_reconciliation from anon';
    end if;
    if exists (select 1 from pg_roles where rolname = 'authenticated') then
        execute 'grant select on public.inventory_reconciliation to authenticated';
    end if;
    if exists (select 1 from pg_roles where rolname = 'service_role') then
        execute 'grant select on public.inventory_reconciliation to service_role';
    end if;
end
$grants$;

-- Usage:
--
--   select * from public.inventory_reconciliation order by abs(drift) desc;
--
-- A row with ledger_rows = 0 and positive drift is probably an opening balance or a
-- batch created outside the app, not necessarily corruption. A row with ledger_rows > 0
-- is the higher-priority case: reconcile it against physical stock before changing it.
