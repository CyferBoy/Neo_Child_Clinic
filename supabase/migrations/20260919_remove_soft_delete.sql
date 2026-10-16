-- Remove soft delete from expenses and doctor_slot_exceptions.
-- Expenses now use hard DELETE (previously blocked by RLS).
-- Doctor slot exceptions already had a permissive RLS policy that allows DELETE.

-- 1. Add DELETE policy for expenses (was previously blocked).
drop policy if exists "expenses_delete_admin_or_own" on public.expenses;
create policy "expenses_delete_admin_or_own"
    on public.expenses for delete
    to authenticated
    using (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid()
              and p.is_active = true
              and p.is_deleted = false
              and p.role in ('admin', 'doctor')
        )
    );

-- 2. Drop is_deleted column from doctor_slot_exceptions.
alter table public.doctor_slot_exceptions drop column if exists is_deleted;
drop index if exists public.idx_doctor_slot_exceptions_deleted;
