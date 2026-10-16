-- Expenses feature (task section 11).
--
-- NOTE ON REPO STATE: supabase/migrations/ is listed in supabase/.gitignore in this
-- checkout, so no prior migration files were available to inspect when writing this one.
-- The authorization model below (profiles.role / is_active / is_deleted) is taken from
-- supabase/functions/manage-staff/index.ts, which is the one place in this repo that
-- performs its own auth check against `profiles` and is confirmed accurate. Please
-- reconcile this against your actual deployed schema (column names/types on `profiles`,
-- existing helper functions, etc.) before applying - and consider un-ignoring
-- migrations/ so future changes like this are diffable instead of invisible.

create table if not exists public.expenses (
    id text primary key,
    expense_date date not null,
    category text not null check (category in (
        'RENT', 'ELECTRICITY', 'WATER', 'INTERNET', 'STAFF_SALARY',
        'MEDICAL_SUPPLIES', 'VACCINE_PURCHASE', 'EQUIPMENT', 'MAINTENANCE',
        'CLEANING', 'STATIONERY', 'TRANSPORTATION', 'MARKETING',
        'SOFTWARE_SUBSCRIPTION', 'OTHER'
    )),
    title text not null,
    description text,
    -- Integer paise (1 rupee = 100 paise), matching ExpenseEntity.amountPaise (Long) -
    -- never a float type, per task section 2.
    amount_paise bigint not null check (amount_paise > 0),
    payment_method text not null check (payment_method in ('CASH', 'ONLINE_UPI', 'BANK')),
    reference_number text,
    attachment_path text,
    is_deleted boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by text,
    updated_by text,
    is_synced boolean not null default true,
    synced_at timestamptz
);

comment on table public.expenses is 'Clinic operating expenses (rent, salaries, supplies, etc). Kept fully separate from finance_transactions, vaccinations, and consultations - see task section 2/20.';

-- Indexes for common queries (task section 15): filter by date/category, staff
-- accountability lookups, sync/pull queries (updated_at), and every list/detail query
-- filters on is_deleted first.
create index if not exists idx_expenses_expense_date on public.expenses (expense_date);
create index if not exists idx_expenses_category on public.expenses (category);
create index if not exists idx_expenses_created_by on public.expenses (created_by);
create index if not exists idx_expenses_updated_at on public.expenses (updated_at);
create index if not exists idx_expenses_is_deleted on public.expenses (is_deleted);

-- Keep updated_at correct for any direct SQL/dashboard edits (the Android app already
-- sets it explicitly on every write - this is a safety net, not the primary mechanism).
create or replace function public.set_expenses_updated_at()
returns trigger as $$
begin
    new.updated_at = now();
    return new;
end;
$$ language plpgsql;

drop trigger if exists trg_expenses_updated_at on public.expenses;
create trigger trg_expenses_updated_at
    before update on public.expenses
    for each row
    execute function public.set_expenses_updated_at();

alter table public.expenses enable row level security;

-- Authorization model mirrors manage-staff/index.ts's own check: an authenticated user
-- may act on expenses only if their `profiles` row is active, not deleted, and their
-- role is authorized (task section 13: "Admin: full access. Other roles: follow the
-- existing financial-management permissions" - since no dedicated financial permission
-- exists elsewhere in this schema, access here matches the Android app's current,
-- effectively-open access to Financial Statistics: any active staff member).
create policy "Active staff can view expenses"
    on public.expenses for select
    to authenticated
    using (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid()
              and p.is_active = true
              and p.is_deleted = false
        )
    );

create policy "Active staff can add expenses"
    on public.expenses for insert
    to authenticated
    with check (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid()
              and p.is_active = true
              and p.is_deleted = false
        )
    );

-- Edit/delete (soft-delete is implemented as an UPDATE - see ExpenseRepositoryImpl) is
-- restricted to admin/doctor, matching the same split already used for patient records
-- (PatientDetailsScreen: isAdmin || role == doctor) - task section 13's "reuse an
-- existing permission" instruction, since no separate financial-management permission
-- exists to reuse instead.
create policy "Admin or doctor can update expenses"
    on public.expenses for update
    to authenticated
    using (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid()
              and p.is_active = true
              and p.is_deleted = false
              and p.role in ('admin', 'doctor')
        )
    )
    with check (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid()
              and p.is_active = true
              and p.is_deleted = false
              and p.role in ('admin', 'doctor')
        )
    );

-- No delete policy: expenses are soft-deleted via UPDATE (is_deleted = true), never
-- physically removed once synchronized - task section 5/20. Without a delete policy,
-- a stray DELETE from any client is rejected by RLS rather than silently succeeding.

-- Service-role credentials are never used by the Android app for this table (task
-- section 11) - normal staff insert/select directly under their own authenticated
-- session via the policies above, unlike `profiles` (which has no client INSERT policy
-- and is only ever written by manage-staff's service-role client).
