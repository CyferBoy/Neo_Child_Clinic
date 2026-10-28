-- 20261026_rls_slot_writes_require_active_staff.sql
--
-- SQL-013: both doctor-schedule write policies checked ownership or the JWT admin claim,
-- but not is_active_staff(). A deactivated doctor could rewrite their availability until
-- the access token expired, and a deactivated admin could rewrite anyone's. The SELECT
-- policies were already narrowed in 20261016; this closes the write path.

begin;

do $slots$
begin
    if to_regclass('public.doctor_weekly_slots') is not null then
        drop policy if exists "doctor_weekly_slots_write_admin_or_own" on public.doctor_weekly_slots;
        create policy "doctor_weekly_slots_write_admin_or_own"
            on public.doctor_weekly_slots for all
            to authenticated
            using (
                public.is_active_staff()
                and (
                    doctor_id = auth.uid()
                    or (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin'
                )
            )
            with check (
                public.is_active_staff()
                and (
                    doctor_id = auth.uid()
                    or (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin'
                )
            );
    end if;

    if to_regclass('public.doctor_slot_exceptions') is not null then
        drop policy if exists "doctor_slot_exceptions_write_admin_or_own" on public.doctor_slot_exceptions;
        create policy "doctor_slot_exceptions_write_admin_or_own"
            on public.doctor_slot_exceptions for all
            to authenticated
            using (
                public.is_active_staff()
                and (
                    doctor_id = auth.uid()
                    or (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin'
                )
            )
            with check (
                public.is_active_staff()
                and (
                    doctor_id = auth.uid()
                    or (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin'
                )
            );
    end if;
end
$slots$;

-- Fail if either surviving write policy still ignores activation.
do $verify$
declare
    v_bad text;
begin
    select string_agg(tablename || '.' || policyname, ', ') into v_bad
      from pg_policies
     where schemaname = 'public'
       and policyname in (
            'doctor_weekly_slots_write_admin_or_own',
            'doctor_slot_exceptions_write_admin_or_own'
       )
       and coalesce(qual, '') || coalesce(with_check, '') not like '%is_active_staff%';

    if v_bad is not null then
        raise exception 'write policy without is_active_staff(): %', v_bad;
    end if;
end
$verify$;

commit;
