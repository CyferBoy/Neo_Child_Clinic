-- 20261013_profiles_privileged_fields_guard.sql
--
-- Closes the one gap left by the profiles self-update path, and corrects the record.
--
-- Context: 20260929000000_security_baseline.sql states "No authenticated
-- INSERT/UPDATE/DELETE policy is intentionally created for profiles", but it only drops
-- two *select* policies. The permissive "profiles_update_own" UPDATE policy created by
-- 20260816_security_hardening.sql is still live (permissive policies are additive).
-- It is deliberately NOT dropped here: the Android app legitimately depends on it -
-- SyncUploader pushes staff profile edits through PostgREST as the signed-in user, and
-- dropping the policy would silently break every profile edit in the field.
--
-- What actually protects privileged fields is the BEFORE UPDATE trigger below, and it
-- had a blind spot: deleted_by was guarded but deleted_at was not, so any active staff
-- member could set their own deleted_at. More generally, every column added to profiles
-- in the future is self-writable unless someone remembers to extend this trigger.
-- This migration closes the known gap; treat the trigger as the allowlist to extend
-- alongside any new privileged column.
--
-- Behavior otherwise preserved verbatim from 20260816:
--   * only non-admin actors updating their OWN row are restricted;
--   * service-role / Edge Function writes (auth.uid() = null, e.g. manage-staff) are
--     unaffected - they are the intended way to delete or deactivate a staff account;
--   * admins (JWT app_metadata.role = 'admin') are unaffected.

begin;

create or replace function public.protect_profile_privileged_fields()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is not null and not public.is_admin() and auth.uid() = old.id then
    if new.id <> old.id
       or new.role <> old.role
       or new.is_active <> old.is_active
       or new.is_deleted <> old.is_deleted
       or coalesce(new.deleted_by::text, '') <> coalesce(old.deleted_by::text, '')
       or new.deleted_at is distinct from old.deleted_at
       or new.email <> old.email then
      raise exception 'Only an administrator may change privileged profile fields';
    end if;
  end if;
  return new;
end;
$$;

commit;
