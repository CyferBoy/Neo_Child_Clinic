-- RLS, index and trigger hardening.
--
-- Everything here is idempotent (if exists / drop-if-exists first) so it is safe to apply to
-- a database that already has some of it applied by hand.
--
-- 1. doctor_weekly_slots / doctor_slot_exceptions: replace the `using (true)` SELECT policies
--    left behind by 20260911. Permissive policies OR together, and
--    doctor_weekly_slots_write_admin_or_own is declared FOR ALL, so `using (true)` won: any
--    valid JWT - including a deactivated staff member's, until the token expires (<=1h) -
--    could read every doctor's schedule. Narrows to the same helper the rest of the app uses.
--    Nothing is widened.
--
-- 2. todo_notification_events: created by a migration that is not in this repository, so the
--    deny-by-default sweep in 20260825 ran before it existed and the baseline's explicit list
--    omits it. It receives patient_id, patient name and doctor_id from a SECURITY DEFINER
--    trigger. Enable RLS with no client policy: the trigger keeps working (it runs as the
--    table owner) and no authenticated/anon session can read the rows.
--
-- 3. patient-docs storage bucket: the app uploads, overwrites, deletes and signs URLs with the
--    signed-in user's session, but neither the bucket nor its policies were versioned, so
--    "who can read a child's vaccination card" could not be reviewed. Created PRIVATE with
--    authenticated-only policies. NOTE: storage object paths are "<patient_id>/<file>" with no
--    clinic segment, so this policy set is authenticated-staff-wide - the same boundary the
--    table policies use. It is not a tenant boundary; see the RLS doc for that.
--
-- 4. Missing indexes on the filters the app actually issues, plus the UNIQUE natural keys its
--    upsert idempotency assumes. Without them two devices can create duplicate reminders or
--    duplicate patient registration numbers.
--
-- 5. update_batch_stock(): 20261013140000 versions the function but never installs the
--    trigger, so the negative-stock guard only exists where it was applied by hand. Installed
--    under the same condition the original migration used (only where the function exists -
--    where the client's absolute vaccine_batches sync is the only writer, installing it would
--    double-apply every transaction).
begin;

-- 1. Narrow the surviving `using (true)` SELECT policies.
do $do$
declare
  v_table text;
begin
  foreach v_table in array array['doctor_weekly_slots', 'doctor_slot_exceptions'] loop
    execute format('drop policy if exists %I on public.%I', v_table || '_select_authenticated', v_table);
    execute format(
      'create policy %I on public.%I for select to authenticated using (public.is_active_staff())',
      v_table || '_select_authenticated', v_table);
  end loop;
end $do$;

-- 2. Patient PII event table: RLS on, no client policy.
do $do$
begin
  if to_regclass('public.todo_notification_events') is not null then
    execute 'alter table public.todo_notification_events enable row level security';
    execute 'alter table public.todo_notification_events force row level security';
    execute 'revoke all on public.todo_notification_events from anon, authenticated';
    execute format('drop policy if exists %I on public.todo_notification_events', 'todo_notification_events_staff_read');
    -- Deliberately no SELECT policy: the writer is the SECURITY DEFINER trigger, and nothing
    -- in the app reads this table. If a read is ever needed, add a scoped policy here rather
    -- than disabling RLS.
  end if;
end $do$;

-- 3. Private patient document bucket.
do $do$
begin
  if to_regclass('storage.buckets') is not null then
    insert into storage.buckets (id, name, public)
    values ('patient-docs', 'patient-docs', false)
    on conflict (id) do update set public = false;

    execute 'drop policy if exists "patient docs staff access" on storage.objects';
    execute 'create policy "patient docs staff access" on storage.objects
               for all to authenticated
               using (bucket_id = ''patient-docs'' and public.is_active_staff())
               with check (bucket_id = ''patient-docs'' and public.is_active_staff())';
  end if;
end $do$;

-- 4. Indexes for the filters the app issues, and the UNIQUE keys its upserts rely on.
create index if not exists inventory_transactions_timestamp_desc_idx
    on public.inventory_transactions (timestamp desc);
create index if not exists inventory_transactions_vaccine_timestamp_idx
    on public.inventory_transactions (vaccine_id, timestamp);
create index if not exists inventory_transactions_batch_timestamp_idx
    on public.inventory_transactions (batch_id, timestamp);
create index if not exists inventory_transactions_type_idx
    on public.inventory_transactions (transaction_type);
create index if not exists audit_logs_patient_timestamp_idx
    on public.audit_logs (patient_id, timestamp desc);

do $do$
begin
  -- UNIQUE keys are only safe to add while the data is clean, so each one is skipped with a
  -- loud notice rather than failing the whole migration.
  if exists (select 1 from public.profiles group by lower(email) having count(*) > 1) then
    raise notice 'SKIPPED unique index on profiles(lower(email)): duplicate emails exist. De-duplicate first.';
  else
    execute 'create unique index if not exists profiles_email_lower_key on public.profiles (lower(email))';
  end if;

  if exists (
    select 1 from public.reminders
     group by patient_id, original_visit_id, vaccine_name, type
    having count(*) > 1
  ) then
    raise notice 'SKIPPED unique index on reminders(patient_id, original_visit_id, vaccine_name, type): duplicates exist. De-duplicate first.';
  else
    execute 'create unique index if not exists reminders_natural_key
             on public.reminders (patient_id, original_visit_id, vaccine_name, type)';
  end if;

  if exists (
    select 1 from public.patients
     where patient_clinic_id is not null
     group by patient_clinic_id
    having count(*) > 1
  ) then
    raise notice 'SKIPPED unique index on patients(patient_clinic_id): duplicates exist. De-duplicate first.';
  else
    execute 'create unique index if not exists patients_clinic_id_key
             on public.patients (patient_clinic_id) where patient_clinic_id is not null';
  end if;
end $do$;

-- 5. Install the negative-stock guard where its function exists.
do $do$
begin
  if exists (
    select 1 from pg_proc p
      join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public' and p.proname = 'update_batch_stock'
  ) and to_regclass('public.inventory_transactions') is not null
    and not exists (
      select 1 from pg_trigger
       where tgname = 'inventory_transactions_update_batch_stock'
         and tgrelid = to_regclass('public.inventory_transactions')
    ) then
    execute 'create trigger inventory_transactions_update_batch_stock
               after insert or update or delete on public.inventory_transactions
               for each row execute function public.update_batch_stock()';
    raise notice 'Installed inventory_transactions_update_batch_stock trigger.';
  end if;
end $do$;

commit;