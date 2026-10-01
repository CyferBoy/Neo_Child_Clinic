# Migrate User Roles to Supabase Auth `app_metadata` — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `Supabase Auth app_metadata.role` the single source of truth for authorization (Android UI gates + RLS), removing `profiles.role` and `user_metadata.role` from every authorization decision.

**Architecture:** A new `CurrentUserProvider` interface (core/security) reads the role from the live Supabase session's `app_metadata` on every call — no cache, no new auth state. All Android gates switch to it; the `manage-staff` edge function writes `app_metadata.role`; one SQL migration backfills `auth.users.raw_app_meta_data` from `profiles.role`, strips `user_metadata.role`, and rewrites RLS role-checks to `auth.jwt() -> 'app_metadata' ->> 'role'`.

**Tech Stack:** Kotlin / Jetpack Compose / Hilt / supabase-kt 3.8.0 (`UserInfo.appMetadata: JsonObject?`), Deno edge function, Postgres RLS migrations, JUnit4.

**Spec:** `docs/superpowers/specs/2026-10-01-supabase-app-metadata-roles-design.md`

## Global Constraints

- Role values: keep all five `UserRole` values (`admin, doctor, receptionist, nurse, inventory_manager`), unchanged.
- `user_metadata` must never carry or provide an authorization role after this change (read or write).
- `profiles.role` remains only for display/business (staff directory, doctor dropdowns, profile screens) — never an authorization decision for the *current* user.
- No `service_role`/privileged key anywhere in the Android app (already true — keep it that way).
- Unknown/missing/malformed role → `null` → deny elevated UI; never default to `admin` (or any elevated role).
- UI gates are UX only; RLS is the security mechanism — never weaken a policy's `is_active`/`is_deleted`/ownership conditions while changing its role source.
- No new dependencies, no new tables, no role caches, no unrelated refactors beyond deleting code this migration orphans.
- Deployment order (manual, final report): 1) run migration, 2) deploy edge function, 3) ship app.
- Windows/PowerShell: use `.\gradlew.bat`; capture build output via `cmd /c "... > %TEMP%\out.txt 2>&1"` to avoid line truncation.

## Review Focus

1. **Missing/malformed `app_metadata.role` must yield `null`, never a privileged role.** — Test: `CurrentUserProviderTest` parsing cases (Task 1, steps 1-6).
2. **Role must track the session (login, logout, token refresh) with zero staleness — no cache.** — Test: `roleFollowsSessionChange` mutates the session seam and re-reads (Task 1, step 4).
3. **First-login with unreachable profile must not synthesize a profile or navigate into the app.** — `AuthViewModel.fetchProfile` returns false → `login()` skips `onSuccess` and shows the error; `ProfileViewModel` errors instead of building from metadata (Task 2, steps 1-2; verified by build + grep that no `user_metadata` role read remains).
4. **`AppDrawer` displays `userRole.name` (line 82) — a nullable role must not crash it.** — Signature becomes `UserRole?` with a display fallback (Task 3, step 2); compile is the test.
5. **RLS role-source swap must preserve every other condition and actually gate on the JWT.** — `supabase/tests/rls_role_test.sql` asserts with simulated `request.jwt.claims` (Task 6); backfill completeness assertions included.

---

### Task 1: `CurrentUserProvider` + `roleFromAppMetadata` (TDD)

**Files:**
- Create: `app/src/main/java/com/neochildclinic/core/security/CurrentUserProvider.kt`
- Create: `app/src/test/java/com/neochildclinic/core/security/CurrentUserProviderTest.kt`
- Modify: `app/src/main/java/com/neochildclinic/core/di/SupabaseModule.kt` (add one `@Provides`)

**Interfaces:**
- Consumes: `com.neochildclinic.domain.model.UserRole` (existing enum, 5 values), `com.neochildclinic.core.common.metadataString()` (existing `JsonElement?.metadataString(): String?`), `Auth.currentSessionOrNull()?.user?.appMetadata: JsonObject?`.
- Produces (relied on by Tasks 2-4):
  - `interface CurrentUserProvider { fun getCurrentUserRole(): UserRole? }`
  - `fun roleFromAppMetadata(meta: JsonObject?): UserRole?`
  - `class SessionCurrentUserProvider` with `constructor(auth: Auth)` and test seam `internal constructor(appMetadata: () -> JsonObject?)`
  - Hilt binding: `CurrentUserProvider` injectable everywhere.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/neochildclinic/core/security/CurrentUserProviderTest.kt`:

```kotlin
package com.neochildclinic.core.security

import com.neochildclinic.domain.model.UserRole
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrentUserProviderTest {

    private fun meta(role: String): JsonObject = buildJsonObject { put("role", role) }

    // --- roleFromAppMetadata: parsing ---
    @Test fun parsesAdmin() {
        assertEquals(UserRole.admin, roleFromAppMetadata(meta("admin")))
    }

    @Test fun parsesDoctor() {
        assertEquals(UserRole.doctor, roleFromAppMetadata(meta("doctor")))
    }

    @Test fun parsesAllFiveRoles() {
        UserRole.entries.forEach { role ->
            assertEquals(role, roleFromAppMetadata(meta(role.name)))
        }
    }

    @Test fun missingRoleKeyReturnsNull() {
        assertNull(roleFromAppMetadata(buildJsonObject { put("display_name", "x") }))
    }

    @Test fun nullMetadataReturnsNull() {
        assertNull(roleFromAppMetadata(null))
    }

    @Test fun unknownRoleReturnsNull() {
        assertNull(roleFromAppMetadata(meta("superuser")))
    }

    @Test fun nonStringRoleReturnsNull() {
        assertNull(roleFromAppMetadata(buildJsonObject { put("role", 42) }))
    }

    @Test fun quotedJsonStringRoleReturnsNull() {
        // Historical bug: reading a JsonElement with .toString() produced `"admin"`
        // (with literal quote characters). That must never map to a role.
        assertNull(roleFromAppMetadata(buildJsonObject { put("role", "\"admin\"") }))
    }

    // --- SessionCurrentUserProvider: session-derived, uncached ---
    @Test fun signedOutYieldsNull() {
        val provider = SessionCurrentUserProvider { null }
        assertNull(provider.getCurrentUserRole())
    }

    @Test fun roleReadFromCurrentSession() {
        val provider = SessionCurrentUserProvider { meta("admin") }
        assertEquals(UserRole.admin, provider.getCurrentUserRole())
    }

    @Test fun roleFollowsSessionChange() {
        var sessionMeta: JsonObject? = meta("doctor")
        val provider = SessionCurrentUserProvider { sessionMeta }
        assertEquals(UserRole.doctor, provider.getCurrentUserRole())

        sessionMeta = meta("admin")          // token refresh picked up a role change
        assertEquals(UserRole.admin, provider.getCurrentUserRole())

        sessionMeta = null                   // logout / expired session
        assertNull(provider.getCurrentUserRole())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cmd /c "gradlew.bat :app:testDebugUnitTest --tests "*CurrentUserProviderTest*" --console=plain > %TEMP%\t1.txt 2>&1"; Select-String -Path $env:TEMP\t1.txt -Pattern "^e: |FAILED|BUILD " | Select-Object -First 10`
Expected: FAIL — `Unresolved reference: roleFromAppMetadata` / `SessionCurrentUserProvider`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/neochildclinic/core/security/CurrentUserProvider.kt`:

```kotlin
package com.neochildclinic.core.security

import com.neochildclinic.core.common.metadataString
import com.neochildclinic.domain.model.UserRole
import io.github.jan.supabase.auth.Auth
import kotlinx.serialization.json.JsonObject

/**
 * Single source of truth for the signed-in user's authorization role.
 * Authorization only; UI gates built on it are UX — RLS enforces independently.
 */
interface CurrentUserProvider {
    /**
     * Role from the current session's `app_metadata` — the same claim RLS reads via
     * `auth.jwt() -> 'app_metadata' ->> 'role'`. Null when signed out, the session is
     * not yet resolved, or the value is missing/unknown/malformed. Never defaults.
     */
    fun getCurrentUserRole(): UserRole?
}

/** `app_metadata.role` -> [UserRole]; case-sensitive exact match, null for anything else. */
fun roleFromAppMetadata(meta: JsonObject?): UserRole? =
    meta?.get("role").metadataString()?.let { raw -> UserRole.entries.firstOrNull { it.name == raw } }

/**
 * Reads the live Supabase session on every call — deliberately uncached, so login,
 * logout, token refresh and expiry are reflected at the next read. Never introduces
 * an authentication state of its own.
 */
class SessionCurrentUserProvider internal constructor(
    private val appMetadata: () -> JsonObject?
) : CurrentUserProvider {

    constructor(auth: Auth) : this({ auth.currentSessionOrNull()?.user?.appMetadata })

    override fun getCurrentUserRole(): UserRole? = roleFromAppMetadata(appMetadata())
}
```

Add to `SupabaseModule.kt` (after `provideSupabaseAuth`):

```kotlin
    @Provides
    @Singleton
    fun provideCurrentUserProvider(auth: Auth): CurrentUserProvider =
        SessionCurrentUserProvider(auth)
```

Plus import `com.neochildclinic.core.security.CurrentUserProvider`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cmd /c "gradlew.bat :app:testDebugUnitTest --tests "*CurrentUserProviderTest*" --console=plain > %TEMP%\t1.txt 2>&1"; Select-String -Path $env:TEMP\t1.txt -Pattern "^e: |FAILED|BUILD |tests completed" | Select-Object -First 10"`
Expected: BUILD SUCCESSFUL, 11 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/neochildclinic/core/security/CurrentUserProvider.kt app/src/test/java/com/neochildclinic/core/security/CurrentUserProviderTest.kt app/src/main/java/com/neochildclinic/core/di/SupabaseModule.kt
git commit -m "Add CurrentUserProvider reading role from session app_metadata"
```

---

### Task 2: `AuthViewModel` / `ProfileViewModel` — expose role, delete metadata fallback

**Files:**
- Modify: `app/src/main/java/com/neochildclinic/core/security/AuthViewModel.kt` (constructor, new property, `fetchProfile`, `login`)
- Modify: `app/src/main/java/com/neochildclinic/feature/profile/presentation/ProfileViewModel.kt` (`loadProfile` fallback removal)
- Modify: `app/src/main/java/com/neochildclinic/core/security/SessionManager.kt` (delete now-unused `getCurrentUserMetadata()`)

**Interfaces:**
- Consumes: `CurrentUserProvider.getCurrentUserRole()` (Task 1).
- Produces: `AuthViewModel.currentUserRole: UserRole?` (getter) used by Tasks 3-4; `fetchProfile(userId): Boolean` (true = profile resolved).

- [ ] **Step 1: Implement `AuthViewModel` changes**

1. Constructor: add `private val currentUserProvider: CurrentUserProvider` (import `com.neochildclinic.core.security.CurrentUserProvider` is same package — no import needed; import `com.neochildclinic.domain.model.UserRole`).

2. Add property next to `profile`/`isProfileLoading`:

```kotlin
    /** Authorization role from the session's app_metadata — the single source of truth. */
    val currentUserRole: UserRole? get() = currentUserProvider.getCurrentUserRole()
```

3. `fetchProfile` — change signature to `private suspend fun fetchProfile(userId: String): Boolean` and replace the synthetic-profile block (currently `if (p == null) { ... Profile(...) ... saveLocalProfile }`) with:

```kotlin
            if (p == null) {
                // First login / fresh install must happen online: there is deliberately
                // no metadata-based fallback (user_metadata no longer carries a role;
                // app_metadata.role is read separately via CurrentUserProvider for
                // authorization). Fail loudly instead of guessing a profile/role.
                _error.value = "Unable to load your profile. Check your connection and try again."
                return false
            }
```

Keep everything else in the method (remote fetch, `lastLogin` update, active check, background refresh). Every `return` in the success path becomes `return true` (add `return true` at the end); the `catch` block returns `false`; `finally` unchanged. The `isActive` logout path returns `true` (it handled the profile).

4. Update the now-stale comment block at the old line 114-126 (the one saying "The profiles table — not Supabase Auth user_metadata — is the source of truth for role") to:

```kotlin
            // The profiles table is business/display data (name, phone, activation).
            // The authorization role is NOT read here - it comes from app_metadata via
            // CurrentUserProvider.getCurrentUserRole(). user_metadata is never consulted.
```

5. `login()` — gate navigation on profile resolution:

```kotlin
                auth.currentSessionOrNull()?.user?.id?.let {
                    if (!fetchProfile(it)) {
                        _isLoading.value = false
                        return@launch // stay on the login screen; error is already set
                    }
                    // Device registration is best-effort and must not delay or block
                    // the initial application data sync if Supabase/user_devices times out.
                    launch { deviceRepository.registerCurrentDevice() }
                }
```

6. Remove now-unused import `com.neochildclinic.core.common.metadataString` (only the deleted block used it).

- [ ] **Step 2: Implement `ProfileViewModel.loadProfile` fallback removal**

Replace the `if (profile == null) { ...metadata fallback... }` block (old lines 46-63) with:

```kotlin
                if (profile == null) {
                    // First load requires network: pull once, then re-read Room.
                    // No metadata-based fallback - user_metadata never carries a role.
                    profileRepository.refreshProfiles()
                    profile = profileRepository.getProfileById(currentUserId)
                }

                if (profile == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Unable to load your profile. Check your connection and try again."
                    )
                    return
                }
```

Then: remove the now-unused imports `com.neochildclinic.core.common.metadataString` and `com.neochildclinic.domain.model.UserRole` (verify `UserRole` has no other use in the file first), and remove `SessionManager.getCurrentUserMetadata()` from `SessionManager.kt` (its only caller was this block — verify with `grep getCurrentUserMetadata` first).

- [ ] **Step 3: Build and verify no `user_metadata` role read remains**

Run: `cmd /c "gradlew.bat assembleDebug --console=plain > %TEMP%\t2.txt 2>&1"; echo exit=$LASTEXITCODE; Select-String -Path $env:TEMP\t2.txt -Pattern "^e: |BUILD |FAILURE" | Select-Object -First 15"`
Expected: `exit=0`, BUILD SUCCESSFUL.

Run: `Select-String -Path "app\src\main\java\com\neochildclinic\**\*.kt" -Pattern 'userMetadata.*role|get\("role"\)'`
Expected: no matches.

- [ ] **Step 4: Run full unit tests**

Run: `cmd /c "gradlew.bat testDebugUnitTest --console=plain > %TEMP%\t2b.txt 2>&1"; echo exit=$LASTEXITCODE; Select-String -Path $env:TEMP\t2b.txt -Pattern "FAILED|BUILD " | Select-Object -First 10"`
Expected: exit=0.

- [ ] **Step 5: Commit**

```bash
git add -A app/src/main/java/com/neochildclinic/core/security/ app/src/main/java/com/neochildclinic/feature/profile/presentation/ProfileViewModel.kt
git commit -m "Expose session-derived role in AuthViewModel; remove user_metadata profile fallback"
```

---

### Task 3: Navigation, Dashboard, Drawer — role wiring

**Files:**
- Modify: `app/src/main/java/com/neochildclinic/app/Navigation.kt:36-44`
- Modify: `app/src/main/java/com/neochildclinic/feature/dashboard/presentation/DashboardScreen.kt:66-69,175`
- Modify: `app/src/main/java/com/neochildclinic/feature/dashboard/presentation/component/AppDrawer.kt:27,82`

**Interfaces:**
- Consumes: `AuthViewModel.currentUserRole` (Task 2).
- Produces: nullable `userRole: UserRole?` flowing into `dashboardNavGraph(...)`/`statisticsGraph(...)` (signatures already take `UserRole?` — unchanged); `AppDrawer(userRole: UserRole?)`.

- [ ] **Step 1: Rewire `Navigation.kt`**

Replace lines 37-43:

```kotlin
    val authProfile by authViewModel.profile.collectAsState()
    // Recompose when the session (and therefore app_metadata.role) changes: this is
    // the only invalidation source the role getter depends on.
    val sessionStatus by authViewModel.sessionStatus.collectAsState()
    val userRole = authViewModel.currentUserRole
```

Delete the `isProfileLoading` collect and the old nurse-default comment (the `sessionStatus` read exists purely to recompose; it is intentionally not otherwise used). If the `UserRole` import in `Navigation.kt` becomes unused, remove it (same for `DashboardScreen.kt` after Step 2 — build gives warnings, not errors, so check by grep).

- [ ] **Step 2: Make `AppDrawer` role nullable**

`AppDrawer.kt`:
- Line 27: `userRole: UserRole?,`
- Line 82: `text = userRole?.name?.replace("_", " ")?.uppercase() ?: "STAFF",`
- Lines 126/136 unchanged (`==` works on nullable).

`DashboardScreen.kt` line 175:

```kotlin
    // Authorization role: session app_metadata (via AuthViewModel), never profile.role.
    val role = authViewModel.currentUserRole
```

Update the stale comment at lines 66-68 to note the skeleton gate now covers profile *display* data while the role comes from the session. Line 201 `userRole = role` unchanged (now nullable, matches new param).

- [ ] **Step 3: Build**

Run: `cmd /c "gradlew.bat assembleDebug --console=plain > %TEMP%\t3.txt 2>&1"; echo exit=$LASTEXITCODE; Select-String -Path $env:TEMP\t3.txt -Pattern "^e: |BUILD " | Select-Object -First 15`
Expected: exit=0. (Compile proves every `AdminGuard`/`statisticsGraph` call site still type-checks.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/neochildclinic/app/Navigation.kt app/src/main/java/com/neochildclinic/feature/dashboard/presentation/
git commit -m "Source navigation/dashboard role from session app_metadata"
```

---

### Task 4: Feature-level gates — PatientList, PatientDetails, Expenses, Doctor Slots, default-doctor

**Files:**
- Modify: `app/src/main/java/com/neochildclinic/feature/patient/presentation/PatientListScreen.kt:52-54`
- Modify: `app/src/main/java/com/neochildclinic/feature/patient/presentation/PatientListViewModel.kt` (add role, delete orphaned staff-profile loading)
- Modify: `app/src/main/java/com/neochildclinic/feature/patient/presentation/PatientDetailsScreen.kt:42-44`
- Modify: `app/src/main/java/com/neochildclinic/feature/finance/presentation/ExpenseListViewModel.kt:70-79`
- Modify: `app/src/main/java/com/neochildclinic/feature/doctor/presentation/WeeklyDoctorSlotsViewModel.kt` (init + `selectDoctor`)
- Modify: `app/src/main/java/com/neochildclinic/feature/consultation/presentation/AddConsultationViewModel.kt:101-102`
- Modify: `app/src/main/java/com/neochildclinic/feature/vaccination/presentation/AddVaccinationViewModel.kt:233-234`

**Interfaces:**
- Consumes: `CurrentUserProvider.getCurrentUserRole()` (inject directly into ViewModels), `AuthViewModel.currentUserRole` (for screens that already have `authViewModel`).

- [ ] **Step 1: `PatientListViewModel` — role in, orphaned staff loading out**

1. Add constructor param `private val currentUserProvider: CurrentUserProvider` and property:

```kotlin
    /** Read once per screen-open; gates are UX-only, RLS enforces independently. */
    val currentUserRole: UserRole? = currentUserProvider.getCurrentUserRole()
```

(import `com.neochildclinic.core.security.CurrentUserProvider` and `com.neochildclinic.domain.model.UserRole`.)

2. Delete `_staff`, `currentStaff`, `fetchStaffProfile()`, and the `init { fetchStaffProfile() }` line — they existed only to source the role.
3. If `profileRepository` and `sessionManager` then have no remaining uses in this file (verify by search), delete both constructor params and their imports. If any use remains, keep.

`PatientListScreen.kt` lines 52-54 become:

```kotlin
    val role = viewModel.currentUserRole
    val isAdmin = role == UserRole.admin
    val canEditOrDelete = isAdmin || role == UserRole.doctor
```

(delete the `val staff by ...collectAsState()` line; if `staff` appears nowhere else — verified it does not — leave `currentStaff` removed.)

- [ ] **Step 2: `PatientDetailsScreen` — role from `authViewModel`**

Lines 42-44 become:

```kotlin
    val role = authViewModel.currentUserRole
    val isAdmin = role == UserRole.admin
    val canEditOrDelete = isAdmin || role == UserRole.doctor
```

Delete line 42's `val profile by authViewModel.profile.collectAsState()` (its only uses were the role checks — verified). Keep the `authViewModel` declaration.

- [ ] **Step 3: `ExpenseListViewModel.loadPermission`**

```kotlin
    private fun loadPermission() {
        // UX mirror of the RLS split: expenses update/delete are admin-or-doctor
        // (see 20260909/20260919 policies). RLS enforces independently.
        val role = currentUserProvider.getCurrentUserRole()
        _uiState.update { it.copy(canManage = role == UserRole.admin || role == UserRole.doctor) }
    }
```

- Add constructor param `private val currentUserProvider: CurrentUserProvider`; drop the `viewModelScope.launch` wrapper in this method (the provider is synchronous).
- `profileRepository` (params lines 53): delete param + import if its only use was line 76 (verify). **Keep `sessionManager`** — still used at line 181 (`getCurrentUserName`).

- [ ] **Step 4: `WeeklyDoctorSlotsViewModel`**

1. Add constructor param `private val currentUserProvider: CurrentUserProvider` (import `com.neochildclinic.core.security.CurrentUserProvider`; `UserRole` already imported).
2. In `init`, before `profileRepository.allProfiles.collect`, capture:

```kotlin
            val currentUserId = sessionManager.getCurrentUserId()
            val myRole = currentUserProvider.getCurrentUserRole()
```

3. Inside the collect lambda (replace every `me?.role`):
   - delete `val me = profiles.find { it.id == currentUserId }`
   - `val visibleDoctors = if (myRole == UserRole.doctor) { doctors.filter { it.id == currentUserId } } else { doctors }`
   - `currentUserRole = myRole,`
   - `canManageSelectedDoctor = myRole == UserRole.admin || (myRole == UserRole.doctor && defaultDoctor?.id == currentUserId),`
4. In `selectDoctor`, replace the `state.currentUserRole` guard with:

```kotlin
        val myRole = currentUserProvider.getCurrentUserRole()
        if (myRole == UserRole.doctor && doctor.id != sessionManager.getCurrentUserId()) {
            return
        }
        _uiState.update {
            it.copy(
                selectedDoctor = doctor,
                canManageSelectedDoctor = myRole == UserRole.admin || myRole == UserRole.doctor,
                isWeeklySlotsEditMode = false
            )
        }
```

(`currentUserRole` stays in `UiState` for display; it is now populated from `myRole`. Doctor *dropdown filtering* by `it.role == UserRole.doctor` is business logic — untouched.)

- [ ] **Step 5: Default-doctor checks in AddConsultation/AddVaccination**

`AddConsultationViewModel` (line 101-102) — add constructor param `private val currentUserProvider: CurrentUserProvider` (import `com.neochildclinic.core.security.CurrentUserProvider` in both VMs), then:

```kotlin
                val currentUserId = sessionManager.getCurrentUserId()
                val currentUserProfile = profiles.find { it.id == currentUserId }
                val defaultDoctor =
                    if (currentUserProvider.getCurrentUserRole() == com.neochildclinic.domain.model.UserRole.doctor) currentUserProfile
                    else null
```

`AddVaccinationViewModel` (line 233-234) — same change with `UserRole.doctor` (already imported).

The `it.role == UserRole.doctor` filters over *all* profiles in both files are business logic (who is a doctor) — untouched.

- [ ] **Step 6: Build + full tests**

Run: `cmd /c "gradlew.bat assembleDebug testDebugUnitTest --console=plain > %TEMP%\t4.txt 2>&1"; echo exit=$LASTEXITCODE; Select-String -Path $env:TEMP\t4.txt -Pattern "^e: |FAILED|BUILD " | Select-Object -First 15`
Expected: exit=0.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/neochildclinic/feature/
git commit -m "Route feature UI role gates through CurrentUserProvider"
```

---

### Task 5: `manage-staff` edge function — authority + `app_metadata` writes

**Files:**
- Modify: `supabase/functions/manage-staff/index.ts` (lines 29-37, 47-63, 100-125, 128-141)

**Interfaces:**
- Consumes: GoTrue admin API (`createUser` / `updateUserById` accept `app_metadata`).
- Produces: `app_metadata.role` set for every user creation/role change — the value Tasks 1-4 and Task 6 depend on.

- [ ] **Step 1: Switch the actor authority check to `app_metadata`**

Replace lines 29-37:

```ts
    const { data: actor, error: actorError } = await admin
      .from("profiles")
      .select("is_active,is_deleted")
      .eq("id", user.id)
      .single()

    // Authorization authority = Supabase Auth app_metadata.role (the JWT claim RLS
    // reads). profiles.is_active/is_deleted remain business gating (account status).
    const actorRole = (user.app_metadata as { role?: string } | null)?.role
    if (actorError || actorRole !== "admin" || actor.is_active !== true || actor.is_deleted === true) {
      return json({ error: "Unauthorized: only active administrators can manage staff" }, 403)
    }
```

- [ ] **Step 2: `CREATE` — write `app_metadata.role`, stop writing `user_metadata.role`**

In `createUser`, change the metadata block to:

```ts
        // role is authorization: app_metadata only (GoTrue keeps this out of the
        // client-writable surface). user_metadata is display data - never a role.
        app_metadata: {
          role,
        },
        user_metadata: {
          display_name: name,
          name,
          employee_id: employeeId ?? null,
          phone_number: phoneNumber ?? null,
        },
```

- [ ] **Step 3: `UPDATE_PROFILE` — role moves to `app_metadata`, conditional**

Replace the `admin.auth.admin.updateUserById(staffId, {...})` call (lines 113-124):

```ts
        const profileUpdate: {
          phone?: string
          user_metadata: Record<string, unknown>
          app_metadata?: { role: string }
        } = {
          // Same top-level `phone` field as CREATE - keeps Auth > Users' Phone column
          // in sync with edits made here, not just the user_metadata copy.
          ...(body.phoneNumber !== undefined ? { phone: toE164Phone(body.phoneNumber) } : {}),
          user_metadata: {
            display_name: body.name,
            name: body.name,
            phone_number: body.phoneNumber,
          },
        }
        if (body.role !== undefined) {
          profileUpdate.app_metadata = { role: body.role }
        }
        const { error: authUpdateError } = await admin.auth.admin.updateUserById(staffId, profileUpdate)
        if (authUpdateError) throw authUpdateError
```

- [ ] **Step 4: `CHANGE_ROLE` — `app_metadata` only**

Replace lines 136-139:

```ts
        const { error: authUpdateError } = await admin.auth.admin.updateUserById(staffId, {
          // Authorization role lives in app_metadata only; user_metadata carries no role.
          app_metadata: { role: body.role },
        })
        if (authUpdateError) throw authUpdateError
```

(`profiles.role` update above it stays — business mirror.)

- [ ] **Step 5: Review pass (no Deno runtime in this environment)**

Run: `Select-String -Path "supabase\functions\manage-staff\index.ts" -Pattern "user_metadata.*role|role.*user_metadata" | ForEach-Object Line`
Expected: no matches. Manually re-read the file for balanced braces/type errors (it will be deployed manually).

- [ ] **Step 6: Commit**

```bash
git add supabase/functions/manage-staff/index.ts
git commit -m "manage-staff: authorize via app_metadata.role and write role to app_metadata"
```

---

### Task 6: SQL migration + RLS test script

**Files:**
- Create: `supabase/migrations/20261001_app_metadata_roles.sql`
- Create: `supabase/tests/rls_role_test.sql`

**Interfaces:**
- Consumes: schema from `20260816_security_hardening.sql` (`is_admin`, profiles policies/trigger), `20260909_expenses.sql`, `20260919_remove_soft_delete.sql`, `20260911_doctor_availability_slots.sql`.
- Produces: `auth.users.raw_app_meta_data.role` backfilled; RLS reads `auth.jwt()`; test script asserts both.

- [ ] **Step 1: Write the migration**

Create `supabase/migrations/20261001_app_metadata_roles.sql`:

```sql
-- 20261001_app_metadata_roles.sql
-- Single source of truth for authorization roles: Supabase Auth app_metadata.
--   1. Backfill auth.users.raw_app_meta_data.role from profiles.role (one-time).
--   2. Strip role from user_metadata (display data must never authorize).
--   3. Rewrite RLS role checks from profiles.role to auth.jwt() app_metadata claim.
-- Business gating (is_active/is_deleted, ownership) is preserved verbatim.

begin;

-- 1. Backfill app_metadata.role for every provisioned user.
update auth.users u
set raw_app_meta_data = coalesce(u.raw_app_meta_data, '{}'::jsonb)
    || jsonb_build_object('role', p.role)
from public.profiles p
where p.id = u.id
  and p.role in ('admin', 'doctor', 'receptionist', 'nurse', 'inventory_manager')
  and coalesce(u.raw_app_meta_data ->> 'role', '') is distinct from p.role;

-- 2. user_metadata no longer carries a role for anyone.
update auth.users
set raw_user_meta_data = raw_user_meta_data - 'role'
where raw_user_meta_data ? 'role';

-- 3. is_admin(): JWT app_metadata instead of a profiles table read.
--    No longer touches profiles, so SECURITY DEFINER is dropped (it existed to
--    avoid recursive profile-policy evaluation). Grants are preserved.
drop function if exists public.is_admin();
create function public.is_admin()
returns boolean
language sql
stable
set search_path = public, auth
as $$
  select coalesce(auth.jwt() -> 'app_metadata' ->> 'role', '') = 'admin';
$$;

revoke all on function public.is_admin() from public;
grant execute on function public.is_admin() to authenticated;

-- 4. expenses update policy (20260909): role source swap, conditions preserved.
drop policy if exists "Admin or doctor can update expenses" on public.expenses;
create policy "Admin or doctor can update expenses"
    on public.expenses for update
    to authenticated
    using (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid()
              and p.is_active = true
              and p.is_deleted = false
        )
        and (auth.jwt() -> 'app_metadata' ->> 'role') in ('admin', 'doctor')
    )
    with check (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid()
              and p.is_active = true
              and p.is_deleted = false
        )
        and (auth.jwt() -> 'app_metadata' ->> 'role') in ('admin', 'doctor')
    );

-- 5. expenses delete policy (20260919): same swap.
drop policy if exists "expenses_delete_admin_or_own" on public.expenses;
create policy "expenses_delete_admin_or_own"
    on public.expenses for delete
    to authenticated
    using (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid()
              and p.is_active = true
              and p.is_deleted = false
        )
        and (auth.jwt() -> 'app_metadata' ->> 'role') in ('admin', 'doctor')
    );

-- 6. doctor_weekly_slots write policy (20260911): admin override via JWT,
--    the doctor's own-row branch is untouched.
drop policy if exists "doctor_weekly_slots_write_admin_or_own" on public.doctor_weekly_slots;
create policy "doctor_weekly_slots_write_admin_or_own"
    on public.doctor_weekly_slots for all
    to authenticated
    using (
        doctor_id = auth.uid()
        or (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin'
    )
    with check (
        doctor_id = auth.uid()
        or (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin'
    );

-- 7. doctor_slot_exceptions write policy (20260911): same swap.
drop policy if exists "doctor_slot_exceptions_write_admin_or_own" on public.doctor_slot_exceptions;
create policy "doctor_slot_exceptions_write_admin_or_own"
    on public.doctor_slot_exceptions for all
    to authenticated
    using (
        doctor_id = auth.uid()
        or (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin'
    )
    with check (
        doctor_id = auth.uid()
        or (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin'
    );

-- Notes:
--   * profiles_select_own_or_admin and protect_profile_privileged_fields call
--     is_admin() - they migrate automatically.
--   * is_active_staff() is activation, not role - intentionally unchanged.
--   * Run BEFORE deploying the app update; role changes propagate to existing
--     sessions on their next token refresh (<= 1h).

commit;
```

- [ ] **Step 2: Write the RLS test script**

Create `supabase/tests/rls_role_test.sql` (run in SQL editor or psql; edit the two UUIDs):

```sql
-- rls_role_test.sql — role-source migration verification.
-- Edit the two UUIDs to real accounts in the target project first.
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

-- 3. doctor on another doctor's slot row must be rejected by RLS
set request.jwt.claims = '{"sub":"<DOCTOR-UUID>","role":"authenticated","app_metadata":{"role":"doctor"}}';
set role authenticated;
insert into public.doctor_weekly_slots (doctor_id, weekday, start_time, end_time)
values ('<OTHER-DOCTOR-UUID>', 1, '09:00', '09:30');   -- EXPECT ERROR: row-level security
reset role;
reset request.jwt.claims;

-- 4. admin on the same row must succeed
set request.jwt.claims = '{"sub":"<ADMIN-UUID>","role":"authenticated","app_metadata":{"role":"admin"}}';
set role authenticated;
insert into public.doctor_weekly_slots (doctor_id, weekday, start_time, end_time)
values ('<OTHER-DOCTOR-UUID>', 1, '09:00', '09:30');   -- EXPECT success
delete from public.doctor_weekly_slots
where doctor_id = '<OTHER-DOCTOR-UUID>' and weekday = 1 and start_time = '09:00';
reset role;
reset request.jwt.claims;

-- 5. expenses update must succeed for doctor and fail for a role-less session
set request.jwt.claims = '{"sub":"<DOCTOR-UUID>","role":"authenticated","app_metadata":{"role":"doctor"}}';
set role authenticated;
update public.expenses set title = title where id = '<ANY-EXPENSE-ID>';  -- EXPECT success (no-op update)
reset role;
reset request.jwt.claims;

set request.jwt.claims = '{"sub":"<NURSE-UUID>","role":"authenticated","app_metadata":{"role":"nurse"}}';
set role authenticated;
update public.expenses set title = title where id = '<ANY-EXPENSE-ID>';  -- EXPECT ERROR: row-level security
reset role;
reset request.jwt.claims;

-- 6. missing app_metadata.role must grant nothing elevated
set request.jwt.claims = '{"sub":"<ADMIN-UUID>","role":"authenticated","app_metadata":{}}';
set role authenticated;
select public.is_admin();                     -- EXPECT f
reset role;
reset request.jwt.claims;
```

- [ ] **Step 3: Sanity-check SQL**

Verify quotes/parens balance by eye; confirm every policy name and table matches the quoted originals in Tasks' source files (20260909, 20260911, 20260919 — exact names: `"Admin or doctor can update expenses"`, `"expenses_delete_admin_or_own"`, `"doctor_weekly_slots_write_admin_or_own"`, `"doctor_slot_exceptions_write_admin_or_own"`).

Note: `supabase/migrations/` is gitignored (no migration is tracked today). Force-add this file so the deliverable survives:

- [ ] **Step 4: Commit**

```bash
git add supabase/tests/rls_role_test.sql
git add -f supabase/migrations/20261001_app_metadata_roles.sql
git commit -m "Add app_metadata role migration and RLS verification script"
```

---

### Task 7: Leftover sweep + final verification

**Files:** none created; audit only (plus fixes if a leftover is found).

- [ ] **Step 1: Authorization leftovers sweep**

Run each and record results:

```powershell
# 1. Any remaining user_metadata role read/write in the app?
Select-String -Path "app\src\main\java\**\*.kt" -Pattern 'get\("role"\)|userMetadata' | ForEach-Object { "$($_.Path):$($_.LineNumber): $($_.Line.Trim())" }
# ALLOWED: none mentioning role.

# 2. profiles-table role used for a CURRENT-user authorization decision?
Select-String -Path "app\src\main\java\**\*.kt" -Pattern '\.role ==|\.role !=|role in \(' | ForEach-Object { "$($_.Path -replace '.*neochildclinic\\',''):$($_.LineNumber): $($_.Line.Trim())" }
# REVIEW every hit: current-user authz -> must be CurrentUserProvider.
# ALLOWED: doctor/staff lists (it.role == UserRole.doctor), display (staff.role.name), dropdown filters.

# 3. SQL: any authz role check still on profiles?
Select-String -Path "supabase\*.sql","supabase\migrations\*.sql" -Pattern "p\.role|profiles.*role = " | ForEach-Object { "$($_.Filename):$($_.LineNumber): $($_.Line.Trim())" }
# ALLOWED: only inside the backfill update (20261001) and comments.

# 4. Edge function writes role to user_metadata?
Select-String -Path "supabase\functions\manage-staff\index.ts" -Pattern "user_metadata"
# ALLOWED: display fields only, no role key.
```

If a hit is an actual current-user authorization decision on `profiles.role`, fix it (route through `CurrentUserProvider`) and re-run the build — do not paper over it.

- [ ] **Step 2: Full verification**

Run: `cmd /c "gradlew.bat assembleDebug testDebugUnitTest --console=plain > %TEMP%\tfinal.txt 2>&1"; echo exit=$LASTEXITCODE; Select-String -Path $env:TEMP\tfinal.txt -Pattern "^e: |FAILED|BUILD " | Select-Object -First 20`
Expected: exit=0.

- [ ] **Step 3: Write the final report**

Produce the spec's 14-point final verification report (section "Final verification report" of the spec): current architecture found, files changed, SQL changes, RLS policies changed, role retrieval, role assignment, retained/removed old sources, session implications, tests, residual security concerns, manual Supabase dashboard steps (migrate → deploy `manage-staff` → run `rls_role_test.sql` → smoke test admin/doctor accounts).

In the tests section, explicitly reconcile with spec section 8's "authorization matrix": UI gates are inline `role == UserRole.admin`-style equality inside Composables/ViewModels — not unit-testable without adding a Compose test dependency (forbidden by Global Constraints). The matrix's real enforcement is asserted by `rls_role_test.sql` (admin succeeds / doctor denied / nurse denied / role-less denied), and role derivation for every matrix row is asserted by `CurrentUserProviderTest` (parses all five roles, unknown → null). State this as a deliberate scope decision in the report.
