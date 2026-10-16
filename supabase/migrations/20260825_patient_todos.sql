-- Today's dashboard work queues. These are intentionally separate from
-- historical consultations/vaccinations and can contain the same patient.

begin;

create table if not exists public.consultation_todos (
    id uuid primary key default gen_random_uuid(),
    patient_id uuid references public.patients(id) on delete set null,
    name text not null,
    mobile text not null,
    address text not null default '',
    todo_date date not null,
    status text not null default 'PENDING',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    is_synced boolean not null default true,
    created_by text,
    updated_by text
);

create index if not exists consultation_todos_todo_date_idx on public.consultation_todos(todo_date);
create index if not exists consultation_todos_status_idx on public.consultation_todos(status);
create index if not exists consultation_todos_patient_id_idx on public.consultation_todos(patient_id);

create table if not exists public.vaccination_todos (
    id uuid primary key default gen_random_uuid(),
    patient_id uuid references public.patients(id) on delete set null,
    name text not null,
    mobile text not null,
    vaccine_names text not null,
    address text not null default '',
    todo_date date not null,
    status text not null default 'PENDING',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    is_synced boolean not null default true,
    created_by text,
    updated_by text
);

create index if not exists vaccination_todos_todo_date_idx on public.vaccination_todos(todo_date);
create index if not exists vaccination_todos_status_idx on public.vaccination_todos(status);
create index if not exists vaccination_todos_patient_id_idx on public.vaccination_todos(patient_id);

alter table public.consultation_todos enable row level security;
alter table public.vaccination_todos enable row level security;

drop policy if exists consultation_todos_staff_select on public.consultation_todos;
create policy consultation_todos_staff_select on public.consultation_todos for select to authenticated using (public.is_active_staff());
drop policy if exists consultation_todos_staff_insert on public.consultation_todos;
create policy consultation_todos_staff_insert on public.consultation_todos for insert to authenticated with check (public.is_active_staff());
drop policy if exists consultation_todos_staff_update on public.consultation_todos;
create policy consultation_todos_staff_update on public.consultation_todos for update to authenticated using (public.is_active_staff()) with check (public.is_active_staff());
drop policy if exists consultation_todos_staff_delete on public.consultation_todos;
create policy consultation_todos_staff_delete on public.consultation_todos for delete to authenticated using (public.is_active_staff());

drop policy if exists vaccination_todos_staff_select on public.vaccination_todos;
create policy vaccination_todos_staff_select on public.vaccination_todos for select to authenticated using (public.is_active_staff());
drop policy if exists vaccination_todos_staff_insert on public.vaccination_todos;
create policy vaccination_todos_staff_insert on public.vaccination_todos for insert to authenticated with check (public.is_active_staff());
drop policy if exists vaccination_todos_staff_update on public.vaccination_todos;
create policy vaccination_todos_staff_update on public.vaccination_todos for update to authenticated using (public.is_active_staff()) with check (public.is_active_staff());
drop policy if exists vaccination_todos_staff_delete on public.vaccination_todos;
create policy vaccination_todos_staff_delete on public.vaccination_todos for delete to authenticated using (public.is_active_staff());

-- Keep the existing human-readable accountability convention.
drop trigger if exists neo_stamp_accountability on public.consultation_todos;
create trigger neo_stamp_accountability before insert or update on public.consultation_todos for each row execute function public.neo_stamp_accountability();
drop trigger if exists neo_stamp_accountability on public.vaccination_todos;
create trigger neo_stamp_accountability before insert or update on public.vaccination_todos for each row execute function public.neo_stamp_accountability();

commit;
