-- 20260919_waste_records_snake_case.sql
--
-- waste_records is the lone table whose live columns are still camelCase
-- (vaccineId, batchId, brandName, batchNumber, expiryDate, dateWasted,
-- updatedAt, isSynced). It was created from the original Kotlin entity before
-- the app moved to snake_case @SerialName. Every other table, the repository
-- upload path, and refreshWaste() (decodeList<WasteRecord>) now use
-- snake_case, so waste inserts fail with:
--   could not find the 'batch_id' column of 'waste_records' in the schema cache
--
-- Rename the 8 camelCase columns to snake_case. created_at/created_by/
-- updated_by are already snake_case. A rename is a catalog-only operation:
-- the primary key, both foreign keys (waste_records_vaccine_id_fkey,
-- waste_records_batchId_fkey) and all RLS policies are preserved. Idempotent:
-- it skips any column already renamed, so re-running is a no-op.

do $$
declare
  r record;
begin
  for r in
    select * from (values
      ('vaccineId',   'vaccine_id'),
      ('batchId',     'batch_id'),
      ('brandName',   'brand_name'),
      ('batchNumber', 'batch_number'),
      ('expiryDate',  'expiry_date'),
      ('dateWasted',  'date_wasted'),
      ('updatedAt',   'updated_at'),
      ('isSynced',    'is_synced')
    ) as t(old_name, new_name)
  loop
    if exists (
      select 1 from information_schema.columns
      where table_schema = 'public'
        and table_name = 'waste_records'
        and column_name = r.old_name
    ) and not exists (
      select 1 from information_schema.columns
      where table_schema = 'public'
        and table_name = 'waste_records'
        and column_name = r.new_name
    ) then
      execute format(
        'alter table public.waste_records rename column %I to %I',
        r.old_name, r.new_name
      );
      raise notice 'waste_records: renamed % -> %', r.old_name, r.new_name;
    end if;
  end loop;
end $$;

-- PostgREST caches the table schema; without this the running API keeps
-- reporting the old camelCase columns until it restarts.
notify pgrst, 'reload schema';
