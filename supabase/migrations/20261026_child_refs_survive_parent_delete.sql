-- 20261026_child_refs_survive_parent_delete.sql
--
-- SQL-021 / SQL-023: the app hard-deletes patients and visits, but the audit and stock
-- ledger rows that reference them used NO ACTION foreign keys, so a parent DELETE failed
-- with 23503 (and the device had already removed the row locally). Audit/ledger history
-- must outlive its parent: keep the row, drop the link.
--
-- Guarded: only alters FKs that already exist; refuses to run if orphaned rows would make
-- the new constraint fail. Parent deletes that should *remove* their children
-- (vaccination_items, finance, reminders) are handled by the app's delete order, not here.

begin;

do $fk$
declare
    r         record;
    v_orphans bigint;
    v_attnum  smallint;
    v_old     record;
begin
    for r in
        select * from (values
            ('inventory_transactions', 'visit_id',   'patient_visits'),
            ('inventory_transactions', 'patient_id', 'patients'),
            ('audit_logs',             'patient_id', 'patients')
        ) as t(child, col, parent)
    loop
        if to_regclass('public.' || r.child) is null
           or to_regclass('public.' || r.parent) is null
           or not exists (
                select 1 from information_schema.columns
                 where table_schema = 'public'
                   and table_name = r.child
                   and column_name = r.col
           ) then
            raise notice 'skipped %.%: table or column missing', r.child, r.col;
            continue;
        end if;

        execute format(
            'select count(*) from public.%I c left join public.%I p on p.id = c.%I
              where c.%I is not null and p.id is null',
            r.child, r.parent, r.col, r.col
        ) into v_orphans;

        if v_orphans > 0 then
            raise exception
                '%.% has % rows with no parent; clean them before adding the FK',
                r.child, r.col, v_orphans;
        end if;

        select a.attnum into v_attnum
          from pg_attribute a
         where a.attrelid = format('public.%I', r.child)::regclass
           and a.attname = r.col;

        -- Drop only the FK(s) on this exact column; leave every other constraint alone.
        for v_old in
            select c.conname
              from pg_constraint c
             where c.conrelid = format('public.%I', r.child)::regclass
               and c.contype = 'f'
               and c.confrelid = format('public.%I', r.parent)::regclass
               and c.conkey = array[v_attnum]
        loop
            execute format('alter table public.%I drop constraint %I', r.child, v_old.conname);
        end loop;

        execute format(
            'alter table public.%I add constraint %I foreign key (%I)
             references public.%I(id) on delete set null',
            r.child,
            r.child || '_' || r.col || '_fkey',
            r.col,
            r.parent
        );
    end loop;
end
$fk$;

do $verify$
declare
    v_bad text;
begin
    select string_agg(conname, ', ') into v_bad
      from pg_constraint
     where contype = 'f'
       and conname in (
            'inventory_transactions_visit_id_fkey',
            'inventory_transactions_patient_id_fkey',
            'audit_logs_patient_id_fkey'
       )
       and confdeltype <> 'n';

    if v_bad is not null then
        raise exception 'FK(s) not ON DELETE SET NULL: %', v_bad;
    end if;
end
$verify$;

commit;
