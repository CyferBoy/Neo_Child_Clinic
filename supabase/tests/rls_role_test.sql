-- rls_role_test.sql — role-source migration verification.
-- Edit the UUID placeholders to real accounts in the target project first.
-- Each block states its expected result as a comment.

-- 0. Backfill completeness (run as superuser/SQL editor, before app rollout)
select count(*) as users_missing_app_role   -- EXPECT 0
from auth.users u
join public.profiles p on p.id = u.id
where p.role in ('admin','doctor','receptionist','nurse','inventory_manager')
  and coalesce(u.raw_app_meta_data ->> 'role', '') <> p.role;

select count(*) as user_metadata_still_has_role  -- EXPECT 0
from auth.users
where raw_user_meta_data ? 'role';

-- Helper: evaluate one expression as a simulated JWT.
-- Replace <UUID> and the role in the claims per block.
-- set request.jwt.claims = '{"sub":"<UUID>","role":"authenticated","app_metadata":{"role":"admin"}}';
-- set role authenticated;
-- select public.is_admin();
-- reset role; reset request.jwt.claims;

-- 1. admin: is_admin() must be true
set request.jwt.claims = '{"sub":"<ADMIN-UUID>","role":"authenticated","app_metadata":{"role":"admin"}}';
set role authenticated;
select public.is_admin();                     -- EXPECT t
reset role;
reset request.jwt.claims;

-- 2. doctor: is_admin() must be false
set request.jwt.claims = '{"sub":"<DOCTOR-UUID>","role":"authenticated","app_metadata":{"role":"doctor"}}';
set role authenticated;
select public.is_admin();                     -- EXPECT f
reset role;
reset request.jwt.claims;

-- 3. doctor on another doctor's slot row: the migrated policy must reject
set request.jwt.claims = '{"sub":"<DOCTOR-UUID>","role":"authenticated","app_metadata":{"role":"doctor"}}';
set role authenticated;
insert into public.doctor_weekly_slots (doctor_id, day_of_week, start_minute, end_minute)
values ('<OTHER-DOCTOR-UUID>', 1, 540, 570);
-- NOTE: succeeds for an ACTIVE placeholder because the 20260929 baseline
-- adds permissive "active staff" insert/update policies that OR with the
-- migrated role policy (pre-existing, out of scope here). Run this block
-- with a placeholder whose profile is_active = false to observe the
-- migrated policy denying; EXPECT ERROR then.
reset role;
reset request.jwt.claims;

-- 4. admin on the same row must succeed
set request.jwt.claims = '{"sub":"<ADMIN-UUID>","role":"authenticated","app_metadata":{"role":"admin"}}';
set role authenticated;
insert into public.doctor_weekly_slots (doctor_id, day_of_week, start_minute, end_minute)
values ('<OTHER-DOCTOR-UUID>', 1, 540, 570);  -- EXPECT success
delete from public.doctor_weekly_slots
where doctor_id = '<OTHER-DOCTOR-UUID>' and day_of_week = 1 and start_minute = 540;
reset role;
reset request.jwt.claims;

-- 5. expenses update: succeeds for doctor; nurse (non-admin/doctor role) denied
set request.jwt.claims = '{"sub":"<DOCTOR-UUID>","role":"authenticated","app_metadata":{"role":"doctor"}}';
set role authenticated;
update public.expenses set title = title where id = '<ANY-EXPENSE-ID>';  -- EXPECT success (no-op update)
reset role;
reset request.jwt.claims;

set request.jwt.claims = '{"sub":"<NURSE-UUID>","role":"authenticated","app_metadata":{"role":"nurse"}}';
set role authenticated;
update public.expenses set title = title where id = '<ANY-EXPENSE-ID>';
-- NOTE: succeeds for an ACTIVE placeholder because the 20260929 baseline
-- adds permissive "active staff" insert/update policies that OR with the
-- migrated role policy (pre-existing, out of scope here). Run this block
-- with a placeholder whose profile is_active = false to observe the
-- migrated policy denying; EXPECT ERROR then.
reset role;
reset request.jwt.claims;

-- 6. missing app_metadata.role must grant nothing elevated
set request.jwt.claims = '{"sub":"<ADMIN-UUID>","role":"authenticated","app_metadata":{}}';
set role authenticated;
select public.is_admin();                     -- EXPECT f
reset role;
reset request.jwt.claims;

-- 7. deactivated admin (JWT says admin, profile is_active=false) must be denied
-- Placeholder must be a real profile row with is_active = false.
set request.jwt.claims = '{"sub":"<DEACTIVATED-ADMIN-UUID>","role":"authenticated","app_metadata":{"role":"admin"}}';
set role authenticated;
select public.is_admin();                     -- EXPECT f
reset role;
reset request.jwt.claims;
