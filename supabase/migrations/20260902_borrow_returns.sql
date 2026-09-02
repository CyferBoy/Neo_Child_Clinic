-- Borrow Returns: append-only history of return transactions against a
-- borrow_records row. Supports multiple partial returns per borrow record.
-- The original borrow_records row (quantity, batch, type, etc.) is never
-- modified or deleted by a return - status (Borrowed / Partially Returned /
-- Returned) is always derived client-side by comparing borrow_records.quantity
-- against the SUM of borrow_returns.quantity for that record.

begin;

create table if not exists public.borrow_returns (
    id uuid primary key default gen_random_uuid(),
    borrow_record_id uuid not null references public.borrow_records(id) on delete cascade,
    batch_id uuid not null references public.vaccine_batches(id) on delete cascade,
    quantity integer not null,
    returned_date date not null,
    notes text,
    created_at timestamptz not null default now(),
    is_synced boolean not null default true,
    created_by text,
    updated_by text,
    constraint borrow_returns_quantity_positive check (quantity > 0)
);

create index if not exists borrow_returns_borrow_record_id_idx on public.borrow_returns(borrow_record_id);
create index if not exists borrow_returns_batch_id_idx on public.borrow_returns(batch_id);

alter table public.borrow_returns enable row level security;

drop policy if exists borrow_returns_staff_select on public.borrow_returns;
create policy borrow_returns_staff_select on public.borrow_returns for select to authenticated using (public.is_active_staff());
drop policy if exists borrow_returns_staff_insert on public.borrow_returns;
create policy borrow_returns_staff_insert on public.borrow_returns for insert to authenticated with check (public.is_active_staff());
drop policy if exists borrow_returns_staff_update on public.borrow_returns;
create policy borrow_returns_staff_update on public.borrow_returns for update to authenticated using (public.is_active_staff()) with check (public.is_active_staff());
drop policy if exists borrow_returns_staff_delete on public.borrow_returns;
create policy borrow_returns_staff_delete on public.borrow_returns for delete to authenticated using (public.is_active_staff());

drop trigger if exists neo_stamp_accountability on public.borrow_returns;
create trigger neo_stamp_accountability before insert or update on public.borrow_returns for each row execute function public.neo_stamp_accountability();

commit;
