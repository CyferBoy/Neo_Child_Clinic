-- consultation_todos.visit_type was added to Room (v35) but never to Supabase.
-- The app uploads the column, so PostgREST rejected every todo row with
-- "could not find the visit_type column ... in the schema cache" and the sync
-- item stayed FAILED. Local rows already carry the value; this only catches the
-- remote table up. Blank means a legacy todo, which the dashboard treats as New.
alter table public.consultation_todos
  add column if not exists visit_type text not null default '';

notify pgrst, 'reload schema';
