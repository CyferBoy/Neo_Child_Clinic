# Task 7 Report: Leftover sweep + final verification

Branch: `feature/app-metadata-roles` · Date: 2026-10-02

---

## Step 1: Authorization leftovers sweep

All four sweeps run twice — **before fixes** (results below) and **after fixes** (re-run, clean; see "Post-fix re-run").

PowerShell note: the brief's `Select-String -Path "app\src\main\java\**\*.kt"` form does not
recurse reliably under Windows PowerShell 5.1, so sweeps 1–3 were run as
`Get-ChildItem -Recurse ... | Select-String ...` (same pattern, same scope).

### Sweep 1 — remaining `user_metadata` role read/write in the app

Query:

```powershell
Get-ChildItem -Recurse -Path app\src\main\java -Filter *.kt |
  Select-String -Pattern 'get\("role"\)|userMetadata' |
  ForEach-Object { "$($_.Path -replace '.*neochildclinic\',''):$($_.LineNumber): $($_.Line.Trim())" }
```

Hits (identical before and after fixes — no change needed):

```
core\security\CurrentUserProvider.kt:23: meta?.get("role").metadataString()?.let { raw -> UserRole.entries.firstOrNull { it.name == raw } }
core\security\SessionManager.kt:51: return user.userMetadata?.get("name").metadataString() ?: user.email ?: fallback
```

Interpretation:
- `CurrentUserProvider.kt:23` is the app_metadata parser — **ALLOWED** per controller ruling 3 (it reads the session `appMetadata` JsonObject, not `user_metadata`).
- `SessionManager.kt:51` reads `user_metadata.get("name")` — a **display field**, no `role` key. **ALLOWED**.
- **Verdict: clean** — no `user_metadata` role read/write anywhere in the app.

### Sweep 2 — `profiles.role` used for a CURRENT-user authorization decision

Query:

```powershell
Get-ChildItem -Recurse -Path app\src\main\java -Filter *.kt |
  Select-String -Pattern '\.role ==|\.role !=|role in \(' |
  ForEach-Object { "$($_.Path -replace '.*java\',''):$($_.LineNumber): $($_.Line.Trim())" }
```

Hits **before** fixes:

| File:line | Hit | Verdict |
|---|---|---|
| `consultation/.../AddConsultationViewModel.kt:97` | `it.role == UserRole.doctor &&` | ALLOWED — doctor **list filter** (already routed current-user part through `CurrentUserProvider` in Task 4) |
| `dashboard/.../DashboardViewModel.kt:126` | `profiles.filter { it.role == UserRole.doctor ... }` | ALLOWED — doctor list filter |
| `doctor/.../WeeklyDoctorSlotsViewModel.kt:68` | `profiles.filter { it.role == UserRole.doctor ... }` | ALLOWED — doctor list filter |
| **`patient/.../AddConsultationViewModel.kt:95`** | `it.role == UserRole.doctor &&` | ALLOWED — doctor list filter |
| **`patient/.../AddConsultationViewModel.kt:102`** | `if (currentUserProfile?.role == UserRole.doctor) currentUserProfile else null` | **VIOLATION — current-user decision on `profiles.role`** (fix applied) |
| `staff/.../ManageStaffScreen.kt:47` | `staff.role == selectedRoleFilter` | ALLOWED — dropdown filter |
| `vaccination/.../AddVaccinationViewModel.kt:230` | `it.role == UserRole.doctor &&` | ALLOWED — doctor list filter |

**Leftover found and fixed:** `feature/patient/presentation/AddConsultationViewModel.kt:102`
decided whether to preselect the signed-in user as the default doctor by reading that
user's **`profiles.role`** from the Room profiles flow — a current-user authorization
decision on the old source. Routed through `CurrentUserProvider` (identical to how the
`feature/consultation` twin was fixed in Task 4): injected `CurrentUserProvider` and now
tests `currentUserProvider.getCurrentUserRole() == UserRole.doctor`. The profile row is
still used only as the *value* to preselect (business data, not authorization).

### Sweep 3 — SQL: any authz role check still on `profiles`?

Query:

```powershell
Get-ChildItem supabase\migrations -Filter *.sql | Select-String -Pattern "p\.role|profiles.*role = " ...
```

Hits before/after (SQL is unchanged by this task):

```
20260909_expenses.sql:111: and p.role in ('admin', 'doctor')
20260909_expenses.sql:120: and p.role in ('admin', 'doctor')
20260911_doctor_availability_slots.sql:59: ... p.role = 'admin'
20260911_doctor_availability_slots.sql:63: ... p.role = 'admin'
20260911_doctor_availability_slots.sql:108: ... p.role = 'admin'
20260911_doctor_availability_slots.sql:112: ... p.role = 'admin'
20260919_remove_soft_delete.sql:16: and p.role in ('admin', 'doctor')
20261001_app_metadata_roles.sql:13,16,17  (backfill update)
rls_role_test.sql:9,10                    (backfill-completeness assertion)
```

Interpretation (each hit traced to its end state after `20261001` runs):

- `20260909:111/120` → policy **"Admin or doctor can update expenses"** — dropped and re-created by `20261001` §4 on `(auth.jwt() -> 'app_metadata' ->> 'role')`. **ALLOWED (superseded).**
- `20260911:59/63` → policy **"doctor_weekly_slots_write_admin_or_own"** — replaced by `20261001` §6. **ALLOWED (superseded).**
- `20260911:108/112` → policy **"doctor_slot_exceptions_write_admin_or_own"** — replaced by `20261001` §7. **ALLOWED (superseded).**
- `20260919:16` → policy **"expenses_delete_admin_or_own"** — replaced by `20261001` §5. **ALLOWED (superseded).**
- `20261001:13/16/17` → the backfill `update auth.users ... from profiles` itself. **ALLOWED.**
- `rls_role_test.sql:9/10` → the test's "backfill completeness" assertion (EXPECT 0), not a policy. **ALLOWED.**

Supplementary checks beyond the brief's pattern (done to make sure nothing hid behind a
different qualifier):

- `20260816_security_hardening.sql:86 and role = 'admin'` — inside the *original*
  `is_admin()` body; `20261001` replaces `is_admin()` **in place**, so the final state
  reads `app_metadata`. **ALLOWED (superseded).**
- `20260816:105 or new.role <> old.role` — inside `protect_profile_privileged_fields()`
  (a write-protection trigger preventing self-escalation of the `profiles.role` column),
  not an authorization read. **ALLOWED** (and still correct: `profiles.role` stays a
  write-protected display/reporting column).
- No migration and no policy anywhere reads `raw_user_meta_data ->> 'role'` for
  authorization (verified by grep: the only `raw_user_meta_data` hits are `20261001`'s
  strip step).
- No policy uses an unqualified `auth.jwt() ->> 'role'`-style shortcut.

**Verdict: clean** — every remaining `profiles.role` reference in SQL is either the
backfill, a test assertion, a superseded historical migration body, or the write-guard
trigger.

### Sweep 4 — edge function writes role to `user_metadata`?

Query: `Select-String -Path "supabase\functions\manage-staff\index.ts" -Pattern "user_metadata"`

Hits:

```
55: // dashboard actually shows in its Phone column. Previously only user_metadata.
60: // client-writable surface). user_metadata is display data - never a role.
64: user_metadata: {
122: user_metadata: Record<string, unknown>
126: // in sync with edits made here, not just the user_metadata copy.
128: user_metadata: {
151: // Authorization role lives in app_metadata only; user_metadata carries no role.
```

Interpretation: the two `user_metadata` payloads (CREATE lines 64–69, UPDATE_PROFILE
lines 128–132) contain only `display_name`, `name`, `employee_id`, `phone_number` — no
`role` key. Role is written to `app_metadata` only (CREATE line 61, CHANGE_ROLE line 152,
UPDATE_PROFILE-when-role-present line 135). **Verdict: clean.**

### Post-fix re-run (sweeps 1 + 2)

```
sweep 1: same two allowed hits (CurrentUserProvider parser, SessionManager display name)
sweep 2: 6 hits, all doctor-list filters / dropdown filter — the patient-ViewModel
         current-user hit at former line 102 is gone
saveLocalProfile: 0 hits repo-wide (app\src, including tests)
```

---

## Carried-item dispositions

1. **`ProfileRepository.saveLocalProfile` deletion — DONE.** Verified zero callers first
   (`grep -r saveLocalProfile app\src` → only the interface declaration
   `ProfileRepository.kt:13` and the impl `ProfileRepositoryImpl.kt:84`; no other class
   implements `ProfileRepository` besides `ProfileRepositoryImpl`). Deleted both the
   interface method and the impl. Re-run: 0 hits.
2. **`feature/patient/presentation/AddConsultationViewModel.kt` — INVESTIGATED: it is
   LIVE, not a duplicate orphan.** It is referenced by
   `feature/patient/presentation/AddConsultationScreen.kt:32` (`hiltViewModel()`),
   same-package resolution — the `feature/consultation` twin serves its own screen.
   Both screens exist; neither file is dead. Because it was live, the ruling's second
   branch applies: it carried a leftover current-user authorization decision
   (`currentUserProfile?.role == doctor` at line 102), **fixed by routing through
   `CurrentUserProvider`** (per Step 1 rules), then rebuilt.
3. **Grep allowance** applied: only `CurrentUserProvider.kt`'s `get("role")` matched sweep 1
   as an allowed parser hit; every other sweep 1–2 hit was reviewed against the
   ALLOWED list above.
4. **`.gitignore`** `.superpowers/` entry committed with this task (see commits below).
5. **Authorization-matrix reconciliation paragraph** included in the tests section of the
   final report below.

---

## Step 2: Full verification

Command:

```
cmd /c "gradlew.bat assembleDebug testDebugUnitTest --console=plain > %TEMP%\tfinal.txt 2>&1"; echo exit=$LASTEXITCODE; Select-String -Path $env:TEMP\tfinal.txt -Pattern "^e: |FAILED|BUILD " | Select-Object -First 20
```

Output:

```
exit=0
BUILD SUCCESSFUL in 3m 3s
```

- Compiler errors (`^e: `): **none**. `FAILED`: **none**.
- `> Task :app:testDebugUnitTest` executed.
- Test results: **19 suites, 144 tests, 0 failures, 0 errors** (parsed from
  `app\build\test-results\testDebugUnitTest\*.xml`), including
  `CurrentUserProviderTest: tests=11 failures=0 errors=0`.

---

## Step 3: Final verification report

Source note: the spec (`docs/superpowers/specs/2026-10-01-supabase-app-metadata-roles-design.md`)
contains no section literally titled "Final verification report" — the 14-point content is
the enumerated list in plan Task 7 ("current architecture found, files changed, SQL changes,
RLS policies changed, role retrieval, role assignment, retained/removed old sources, session
implications, tests, residual security concerns, manual steps…"), expanded below into
numbered points, with the manual-steps material split out as the plan enumerates it.

### 1. Current architecture found (as left by this migration)

Authorization role has a single source: Supabase Auth `app_metadata.role` on the signed-in
session's user. The Android app reads it exclusively through `CurrentUserProvider`
(`core/security/CurrentUserProvider.kt`), whose implementation `SessionCurrentUserProvider`
re-reads `auth.currentSessionOrNull()?.user?.appMetadata` on every call (no cache, no
independent auth state). `AuthViewModel.currentUserRole` is the UI-facing accessor; every
UI gate (Navigation graphs, AppDrawer, Dashboard, PatientList/Details, WeeklyDoctorSlots,
ExpenseList, AddVaccination, both AddConsultation ViewModels) derives from it. RLS reads the
identical claim server-side via `auth.jwt() -> 'app_metadata' ->> 'role'`. `profiles.role`
survives as a business/display/reporting column (write-protected by
`protect_profile_privileged_fields`), never as an authorization input. The `manage-staff`
edge function authorizes the actor by `user.app_metadata.role` and writes role changes to
`app_metadata` through the admin API.

### 2. Files changed (branch vs `main`, incl. this task)

App:
- `core/security/CurrentUserProvider.kt` (new), `core/security/SessionManager.kt`,
  `core/security/AuthViewModel.kt`, `core/di/SupabaseModule.kt`
- `app/Navigation.kt`, `feature/dashboard/presentation/DashboardScreen.kt`,
  `feature/dashboard/presentation/component/AppDrawer.kt`
- `feature/consultation/presentation/AddConsultationViewModel.kt`,
  `feature/patient/presentation/AddConsultationViewModel.kt` (Task 7 fix),
  `feature/vaccination/presentation/AddVaccinationViewModel.kt`,
  `feature/doctor/presentation/WeeklyDoctorSlotsViewModel.kt`,
  `feature/finance/presentation/ExpenseListViewModel.kt`,
  `feature/patient/presentation/PatientListViewModel.kt`,
  `feature/patient/presentation/PatientListScreen.kt`,
  `feature/patient/presentation/PatientDetailsScreen.kt`,
  `feature/profile/presentation/ProfileViewModel.kt`
- Tests: `app/src/test/.../core/security/CurrentUserProviderTest.kt`
- Task 7 deletions: `feature/profile/domain/repository/ProfileRepository.kt`
  (removed `saveLocalProfile`), `feature/profile/data/ProfileRepositoryImpl.kt`
  (removed its impl)
- `.gitignore` (adds `.superpowers/`)

Supabase:
- `supabase/migrations/20261001_app_metadata_roles.sql` (new)
- `supabase/functions/manage-staff/index.ts`
- `supabase/tests/rls_role_test.sql` (new)

### 3. SQL changes (`20261001_app_metadata_roles.sql`, single transaction)

1. **Backfill**: `auth.users.raw_app_meta_data.role` set from `profiles.role` for every
   provisioned user whose profile role is one of the five valid roles and whose
   `app_metadata.role` differs/missing.
2. **Strip**: `raw_user_meta_data - 'role'` for every user still carrying one.
3. **`is_admin()` replaced in place** (`create or replace`, not dropped —
   `profiles_select_own_or_admin` depends on it): role source becomes
   `coalesce(auth.jwt() -> 'app_metadata' ->> 'role','') = 'admin'`; the
   `is_active = true and is_deleted = false` activation check, `SECURITY DEFINER`,
   `search_path` and grants are preserved verbatim from `20260816`.
4–7. **Policy rewrites** (see point 4): expenses update, expenses delete, weekly-slots
write, slot-exceptions write — each `drop policy if exists` + `create policy` with the
conditions copied verbatim except the role test swaps `p.role ...` → the `app_metadata`
JWT claim.

### 4. RLS policies changed

| Policy | Table | Change |
|---|---|---|
| `is_admin()` (function) | `profiles` (via dependents) | role source → `auth.jwt() -> 'app_metadata' ->> 'role'`; activation check kept |
| `Admin or doctor can update expenses` | `expenses` | `p.role in ('admin','doctor')` → jwt `app_metadata` claim in (both USING and WITH CHECK); active-profile condition kept |
| `expenses_delete_admin_or_own` | `expenses` | same swap (USING) |
| `doctor_weekly_slots_write_admin_or_own` | `doctor_weekly_slots` | admin branch → jwt claim; `doctor_id = auth.uid()` branch untouched |
| `doctor_slot_exceptions_write_admin_or_own` | `doctor_slot_exceptions` | same |

Untouched by design: everything gated by `is_admin()` (`profiles_select_own_or_admin`,
`protect_profile_privileged_fields`) migrates automatically through the function;
`is_active_staff()` policies are activation, not role; ownership policies
(`auth.uid()`-based) are unchanged.

### 5. Role retrieval

`SessionCurrentUserProvider.getCurrentUserUserRole()` → `roleFromAppMetadata(session.user
.appMetadata)`: exact, case-sensitive match against the five `UserRole` names; `null` when
signed out / session unresolved / key missing / unknown / non-string / quoted-JSON-string.
Read live from the session on every call — login, logout, refresh and expiry are reflected
at next read. **No cache, no fallback to profile rows or `user_metadata` anywhere.**

### 6. Role assignment

Only the `manage-staff` edge function (service-role) writes role: CREATE →
`app_metadata: { role }` on `auth.admin.createUser` (+ `profiles.role` as business data);
CHANGE_ROLE → `auth.admin.updateUserById({ app_metadata: { role } })` + profiles row;
UPDATE_PROFILE with a `role` in the body → `app_metadata` set; **without** `role` →
`app_metadata` untouched (GoTrue's `updateUserById` merges, so a name/phone edit cannot
clobber it). Client-side: no code attempts to modify `app_metadata` (GoTrue restricts it
to the admin API); `profiles.role` writes by non-admins are blocked by the
`protect_profile_privileged_fields` trigger.

### 7. Retained / removed old sources

- **Removed:** all `user_metadata.role` reads in the app (first-login fallback in
  `AuthViewModel`/`ProfileViewModel` gone — profile must come from Room or remote, else
  error); `user_metadata.role` writes in `manage-staff`; every `profiles.role`
  authorization read in SQL (superseded as traced in sweep 3); the current-user
  `profiles.role` decision in `feature/patient/.../AddConsultationViewModel` (Task 7).
- **Retained:** `profiles.role` as display/reporting data and as the *backfill source*;
  `user_metadata` display fields (`display_name`, `name`, `employee_id`, `phone_number`);
  `is_active`/`is_deleted` business gating; `SessionManager`'s
  `user_metadata.name` for display only.

### 8. Session implications

- Role is derived from the in-memory session on each read; there is no second auth state.
- SDK token refresh (≤1h) picks up role changes — RLS enforcement of a role change is
  therefore ≤1h stale for an existing session; UI gates update at the next read.
- Cold start offline: the stored session JWT carries `app_metadata`, so gates work
  offline; expired/missing session → role `null` → gates deny.
- `cacheRegistry.clearAll()` before sign-in is retained (no cross-account bleed).

### 9. Tests

- `CurrentUserProviderTest` (11 tests, green): parses admin/doctor + all five roles,
  missing key → null, null metadata → null, unknown → null, non-string primitive → null,
  quoted-JSON-string → null, signed out → null, role read from current session, role
  follows session change — plus a pinned five-role set assertion so enum drift against
  the SQL-literals/edge-fn `validateRole` contract fails the suite.
- Full suite: **19 suites / 144 tests / 0 failures / 0 errors**; `assembleDebug` +
  `testDebugUnitTest` exit 0 (`BUILD SUCCESSFUL in 3m 3s`).
- RLS assertions live in `supabase/tests/rls_role_test.sql` (manual, needs DB access).

**Authorization-matrix reconciliation (spec §8 vs what is actually asserted here):**
the spec's authorization matrix (admin passes admin-gates; doctor fails the admin-gate but
passes admin|doctor gates; null/unknown fails all elevated gates) is enforced in the app as
inline `role == UserRole.admin`-style equality inside Composables/ViewModels — it is not
unit-testable without adding a Compose UI-test dependency, which the Global Constraints
forbid. This is a **deliberate scope decision**: the matrix's real enforcement is asserted
server-side by `rls_role_test.sql` (admin succeeds / doctor denied / nurse denied /
role-less denied), and role *derivation* for every matrix row — i.e. that each of the five
roles and the null/unknown cases map exactly as the matrix assumes — is asserted by
`CurrentUserProviderTest` (parses all five roles, unknown → null). Together those two
cover both halves of the matrix (the value derivation and the enforcement) without
importing a forbidden test dependency; the UI equality expressions themselves are covered
by this task's code review + the sweep above. (The nurse-deny block must be run with an
inactive placeholder — see §10.1/§11.4.)

### 10. Residual security concerns

1. **Permissive `active staff` policies void role/ownership RLS on three tables
   (PRE-EXISTING — not introduced by this migration).**
   `20260929000000_security_baseline.sql` creates, on `expenses`, `doctor_weekly_slots`
   and `doctor_slot_exceptions` (among others), permissive policies
   `"active staff insert/update/delete"` with
   `using/with check (public.is_active_staff())`. Permissive policies OR together, so for
   any *active* authenticated staff member these policies grant insert/update/delete
   regardless of the migrated role/ownership policy — the role checks this migration
   carefully rewrote are effectively bypassed on those operations for active staff (e.g. a
   nurse can update another doctor's weekly slots). This is a property of the pre-existing
   access model; fixing it is a separate design decision and was explicitly out of scope.
   `rls_role_test.sql` blocks 3 and 5 document the same caveat inline.
2. **`user_metadata.role` persists for pre-existing users only until the migration's
   strip step runs.** The migration handles it: step 2 removes `role` from
   `raw_user_meta_data` for every user still carrying one, and the test script's
   backfill-completeness block asserts `user_metadata_still_has_role = 0`. Until the
   migration is applied, old APKs may still read the stale `user_metadata.role` and show
   nurse-tier UI (RLS no longer consults it, so this is a display-level risk only).
3. Accepted/documented (from spec §7/§9): ≤1h stale role in an existing JWT after a role
   change; `profiles.role` display can briefly disagree with the JWT until next sync;
   service-role key confined to the edge function's env (absent from the Android app).

### 11. Manual deployment steps (cannot be done from the repo)

1. **Run the migration** — apply `supabase/migrations/20261001_app_metadata_roles.sql`
   (SQL editor or `supabase db push`), first in staging. Backfills `app_metadata.role`,
   strips `user_metadata.role`, swaps `is_admin()` and the four policies in one
   transaction. If any role changed between migration (step 1) and edge-function deploy
   (step 2), re-run the migration — it is idempotent (`is distinct from` guard) — so
   `app_metadata.role` catches up.
2. **Deploy the edge function** — `supabase functions deploy manage-staff` (the old
   version still writes/authorizes the old way; deploy order matters).
3. **Ship the app** — release the APK built from this branch.
4. **Run `supabase/tests/rls_role_test.sql`** against the target project after (1)–(2),
   replacing every `<...-UUID>` placeholder with **real account UUIDs** from
   `auth.users`. Caveats: blocks **3 and 5** are *expected to succeed* for an
   **active** placeholder because of the pre-existing permissive "active staff" policies
   (residual concern 10.1) — run those blocks with a placeholder whose profile has
   `is_active = false` to observe the migrated policy denying (EXPECT ERROR). Blocks 0
   must return 0/0; block 1 `t`; block 2 `f`; block 4 success; block 6 `f`;
   block 7 needs a real deactivated-admin row and expects `f`.
5. **Smoke test admin and doctor accounts** (real logins):
   - admin: Manage Staff visible, staff CRUD works; doctor: staff UI hidden, patient edit
     visible; logout/login round-trip.
   - Deploy-time check A: edit a staff member's name/phone via **UPDATE_PROFILE without
     `role`** → verify `auth.users.raw_app_meta_data ->> 'role'` is **unchanged**
     (app_metadata not clobbered).
   - Deploy-time check B: **deactivated admin** (profile `is_active = false`, JWT still
     says admin) → `select public.is_admin()` must return `f`, and admin-only screens
     deny (block 7 of the test script).
   - Role change propagation: change a user's role, confirm the UI flips after the next
     token refresh (≤1h) without re-login.
6. **Staging verification** — after the migration, run `supabase/verify_security.sql` §2
   (`pg_policies`) and confirm each of the four rewritten policies'
   `qual`/`with_check` contain the `app_metadata` claim — mechanical proof the test
   script's active-placeholder blocks can't give.

---

## Commit

- `.gitignore` (`.superpowers/`) + Task 7 fixes (patient `AddConsultationViewModel`
  routed through `CurrentUserProvider`; `saveLocalProfile` deleted from interface and
  impl) committed as: **"Final role-source sweep: remove orphaned code, add verification
  report"**.
- This report file itself is not committed — `.superpowers/` is now gitignored
  (controller ruling 4).
