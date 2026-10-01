# Spec: Migrate User Roles to Supabase Auth `app_metadata`

Status: approved design (2026-10-01)
Decisions: keep all 5 `UserRole` values; strip `role` from `user_metadata`; no metadata-based profile fallback (first login requires network).

## Goal

Supabase Auth `app_metadata.role` becomes the single source of truth for authorization:

```
Supabase Auth → app_metadata.role ("admin" | "doctor" | "receptionist" | "nurse" | "inventory_manager")
      → JWT → Android UI gates (UX only)
            → RLS policies (security)
```

`profiles.role` remains as **business/display data only** (staff directory, doctor dropdowns,
profile display). No authorization decision may read it after this migration.

## Non-goals

- No role-value changes (all 5 enum values stay).
- No schema changes beyond one migration file (no new tables/columns).
- No UI redesign; no changes to `is_active_staff()` or activation/soft-delete logic.
- No new role framework, no role caches.

## Current state (audit summary)

- Android reads current-user role from Room `profiles.role` (via `AuthViewModel.profile`,
  `PatientListViewModel.currentStaff`, `ExpenseListViewModel`, `WeeklyDoctorSlotsViewModel`)
  with a `user_metadata.role` fallback in `AuthViewModel:142` and `ProfileViewModel:58`
  that persists into Room.
- RLS reads `profiles.role`: `is_admin()` (used by profiles policy + privileged-fields
  trigger), expenses update/delete policies (`in ('admin','doctor')`), doctor slot write
  policies (`='admin'`).
- Role assignment: `manage-staff` edge function (caller's own session token; service key
  server-side only) writes `user_metadata.role` + `profiles.role`; never `app_metadata`.
- `appMetadata` is read nowhere in the app today.

## Design

### 1. Domain: `CurrentUserProvider`

```kotlin
// core/security (data-facing domain abstraction; consumed by ViewModels only)
interface CurrentUserProvider {
    /** Role from the current session's app_metadata JWT claims. Null = signed out,
     *  session unresolved, or missing/unknown/malformed role. Never defaults. */
    fun getCurrentUserRole(): UserRole?
}
```

- Pure parsing helper `fun roleFromAppMetadata(meta: JsonObject?): UserRole?`:
  `meta?.get("role").metadataString()` → matched case-sensitively against
  `UserRole.entries` → value or **null**. Unknown/missing/malformed → null (never admin).
- No `getCurrentUser()`/`CurrentUser` class — YAGNI; `SessionManager` already covers user info.

### 2. Data layer: `SessionCurrentUserProvider`

- `@Singleton`, injects `Auth`; reads `auth.currentSessionOrNull()?.user?.appMetadata` on
  every call. Zero caching: token refresh, login, logout, and expiry are reflected
  immediately at the next read. No new auth state, no flow needed (UI already gates on
  `sessionStatus` / `isProfileLoading`).

### 3. UI consumption (role = current user only)

Rule: **current-user authorization reads come from `CurrentUserProvider` only**;
`profile.role` stays only for display and for *other people's* profiles (staff lists,
doctor dropdowns).

| Site | Change |
|---|---|
| `Navigation.kt:43` | `userRole = provider role`; `isProfileLoading` gate kept; missing-after-load → `null` (not `nurse`) |
| `NavigationDashboardGraph` `AdminGuard`/`RoleGate`/`isAdmin` | receive provider-derived role (signatures unchanged) |
| `NavigationStatisticsGraph:21` | same input change |
| `DashboardScreen:175`, `AppDrawer:126/136` | role input from provider |
| `PatientListScreen:53-54` | `canEditOrDelete` from role passed via `PatientListViewModel` state (provider) |
| `PatientDetailsScreen:43-44` | same via its ViewModel/provider |
| `ExpenseListViewModel:76-77` | `canManage` from provider (not `profileRepository`) |
| `WeeklyDoctorSlotsViewModel:68/81/107` | `me?.role` checks → provider; doctor *dropdown filtering* stays `profiles.role` |
| `AddConsultationViewModel:95/102`, `AddVaccinationViewModel:228/234` | "am I the default doctor" → provider; `it.role == doctor` over all profiles stays (business) |
| `ManageStaffScreen`/`EditStaffScreen`/`AddStaffScreen`/`StaffDetailsScreen`/`ProfileScreen` role display | unchanged (display of `profiles.role`) |

Unknown/null role: gated routes render `AccessDeniedScreen`; drawer/statistics items hidden.
UI is UX only — RLS enforces independently.

### 4. Remove `user_metadata` role reads and first-login fallback

- `AuthViewModel.fetchProfile`: delete the synthetic-`Profile` fallback block (lines
  128-148). Flow becomes: Room row → else `fetchProfileFromRemote` → else **error state
  ("Unable to load your profile. Check your connection and try again.") and no profile;
  `login()` does not call `onSuccess` in that case. Existing offline users (Room row
  present) keep full offline access — unchanged.
- `ProfileViewModel.loadProfile`: same removal — no metadata-built profile; surface error
  if neither local nor remote profile exists.
- `metadataString()` stays (still used for display-name/phone reads).

### 5. Edge function `manage-staff`

- Actor authority: replace `profiles.role !== "admin"` check with
  `user.app_metadata?.role === "admin"` (from the already-fetched `getUser(token)` result);
  **keep** the `is_active`/`is_deleted` checks from `profiles` (business gating).
- `CREATE`: add `app_metadata: { role }` to `createUser`; **remove `role` from
  `user_metadata`** (keep display_name/name/employee_id/phone_number there).
- `UPDATE_PROFILE` / `CHANGE_ROLE`: write `app_metadata: { role }` via `updateUserById`;
  remove `role` from the `user_metadata` payload (spread existing metadata carefully —
  `updateUserById` metadata objects replace wholesale, so preserve display fields).
- `validateRole()` unchanged (5 values). `profiles.role` still written (business mirror).

### 6. Database migration `20261001_app_metadata_roles.sql`

```sql
begin;

-- 1. Backfill: every existing user gets app_metadata.role from profiles.role
update auth.users u
set raw_app_meta_data = coalesce(u.raw_app_meta_data, '{}'::jsonb)
    || jsonb_build_object('role', p.role)
from public.profiles p
where p.id = u.id
  and p.role in ('admin','doctor','receptionist','nurse','inventory_manager')
  and coalesce(u.raw_app_meta_data ->> 'role', '') is distinct from p.role;

-- 2. Strip role from user_metadata (single source of truth)
update auth.users
set raw_user_meta_data = raw_user_meta_data - 'role'
where raw_user_meta_data ? 'role';

-- 3. is_admin() now reads the JWT
create or replace function public.is_admin()
returns boolean language sql stable
set search_path = public
as $$
  select coalesce(auth.jwt() -> 'app_metadata' ->> 'role', '') = 'admin';
$$;
-- (keeps grants; SECURITY DEFINER no longer needed but harmless — drop it explicitly
--  since it no longer touches tables: use plain invoker semantics as written above)

-- 4. Policy role-checks → JWT (ownership/active/deleted conditions preserved verbatim)
--    expenses "Admin or doctor can update expenses"  (using + with check):
--      and p.role in ('admin','doctor')  →  and (auth.jwt() -> 'app_metadata' ->> 'role') in ('admin','doctor')
--    expenses "expenses_delete_admin_or_own": same substitution
--    doctor_weekly_slots / doctor_slot_exceptions write policies (4 spots):
--      exists (select 1 from profiles p where p.id = auth.uid() and p.role = 'admin')
--        → (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin'
--      (the "own row" OR-branch is untouched)

commit;
```

- `is_active_staff()` untouched (activation, not role).
- `protect_profile_privileged_fields` trigger and `profiles_select_own_or_admin` policy
  call `is_admin()` → migrate automatically.
- Existing `revoke/grant` on `is_admin()` preserved.
- New helper SQL function: none (inline expressions per spec examples).

### 7. Session/token handling

- Role is derived from the in-memory session each read; no independent auth state.
- Token refresh (SDK auto) picks up role changes; **RLS role changes take effect on the
  next token refresh (≤1h)** — accepted, documented.
- Cold start offline: stored session JWT carries `app_metadata` → gates work offline;
  profile (business) comes from Room. Expired/missing session → role null → gates deny.
- Login: `cacheRegistry.clearAll()` already runs before sign-in (kept).

### 8. Tests

- `roleFromAppMetadata` parsing: admin, doctor, other roles, missing key, null metadata,
  unknown value, malformed (non-string primitive, quoted-JSON string).
- `SessionCurrentUserProvider` over a constructed `UserInfo`/session: signed out → null;
  role present → mapped; refresh semantics = re-read (new instance → new role).
- Authorization matrix (gate logic): admin passes admin-gates; doctor fails admin-gate,
  passes admin|doctor gates; null/unknown fails all elevated gates.
- First-login/no-fallback: `AuthViewModel`-level test is out of scope (concrete
  collaborators); the removed fallback is covered by construction — profile must come
  from Room or remote, else error (verified in code review + manual step below).
- RLS: `supabase/tests/rls_role_test.sql` script using
  `set request.jwt.claims to '{"sub":"<uid>","app_metadata":{"role":"..."}}'` +
  `set role authenticated` + assertions (read-only selects that should succeed/fail).
  Runnable in SQL editor or `supabase db`. **Manual step** (needs DB access).

## Manual deployment steps (cannot be done from repo)

1. Apply `20261001_app_metadata_roles.sql` (SQL editor or `supabase db push`).
2. Deploy edge function: `supabase functions deploy manage-staff`.
3. Run `supabase/tests/rls_role_test.sql` against a staging DB.
4. Smoke: login as admin (Manage Staff visible), doctor (staff hidden, patient edit
   visible), logout/login, token refresh after a role change.

## Security notes

- Service-role key stays in edge-function env only (verified: absent from Android).
- Client cannot modify own `app_metadata` (GoTrue restricts to admin API) — no code added
  that attempts it.
- Residual risks (accepted, documented): ≤1h stale role in an existing JWT after a role
  change; `profiles.role` display can briefly disagree with JWT until next sync (display
  only); old APKs will show nurse-tier UI for users whose `user_metadata.role` was stripped
  until they update (RLS still enforces server-side).
