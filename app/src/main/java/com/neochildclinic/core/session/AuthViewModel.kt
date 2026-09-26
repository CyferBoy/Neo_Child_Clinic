package com.neochildclinic.core.session

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.neochildclinic.data.manager.SyncManagerImpl
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.data.repository.ProfileRepositoryImpl
import com.neochildclinic.data.repository.DeviceRepositoryImpl
import com.neochildclinic.core.utils.metadataString
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import androidx.compose.runtime.State
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: Auth,
    private val profileRepository: ProfileRepositoryImpl,
    private val deviceRepository: DeviceRepositoryImpl,
    private val syncManager: SyncManagerImpl
) : ViewModel() {

    companion object {
        private const val SESSION_STATUS_TIMEOUT_MS = 4_000L
    }

    val currentUser: UserInfo? get() = auth.currentSessionOrNull()?.user

    // The SDK resolves any session saved to disk asynchronously (autoLoadFromStorage).
    // Callers that need to know "is there really a session" on cold start should wait
    // for this to leave Initializing rather than reading currentUser synchronously,
    // which races the storage load and returns null before it's had a chance to finish.
    val sessionStatus: StateFlow<SessionStatus> = auth.sessionStatus

    /**
     * Waits for sessionStatus to leave Initializing, but never indefinitely. With
     * autoLoadFromStorage + the SDK's default alwaysAutoRefresh, a session that's expired
     * or close to it on disk triggers a network token-refresh attempt before the status
     * resolves - and that refresh call has no bounded timeout of its own. This app is
     * offline-first (Room is the source of truth), so a cold start with no network must
     * never sit on a loading screen forever waiting for a refresh that can't complete -
     * that was exactly this bug.
     *
     * Returns null on timeout (status is still Initializing). Callers should treat a null
     * result the same as "fall back to whatever's already cached in memory" via
     * [currentUser], since a previously logged-in user should keep full offline access to
     * their local data rather than being bounced to Login just because there's no signal.
     */
    suspend fun awaitResolvedSessionStatus(): SessionStatus? =
        kotlinx.coroutines.withTimeoutOrNull(SESSION_STATUS_TIMEOUT_MS) {
            sessionStatus.first { it !is SessionStatus.Initializing }
        }

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    // True while the authoritative profile (and therefore role) is being resolved.
    // Screens that gate UI on role (drawer menu, Manage Staff, Statistics, etc.) must
    // wait for this to go false rather than reading `profile?.role ?: UserRole.nurse` -
    // treating "not loaded yet" as "nurse" is what caused admin accounts to intermittently
    // flash the nurse view on cold start / fast reopen.
    private val _isProfileLoading = MutableStateFlow(false)
    val isProfileLoading: StateFlow<Boolean> = _isProfileLoading.asStateFlow()

    init {
        // Mark loading immediately so the UI doesn't fall through to UserRole.nurse
        // before the profile has been fetched. On cold start, auth.currentSessionOrNull()
        // can return null briefly while Supabase resolves the stored session, causing
        // the ?.let below to skip fetchProfile entirely — leaving isProfileLoading=false
        // and the UI defaulting to nurse.
        _isProfileLoading.value = true
        viewModelScope.launch {
            val userId = auth.currentSessionOrNull()?.user?.id
            if (userId != null) {
                fetchProfile(userId)
            } else {
                // Session not yet resolved from storage — wait for it
                val status = awaitResolvedSessionStatus()
                val resolvedUserId = auth.currentSessionOrNull()?.user?.id
                if (status is SessionStatus.Authenticated && resolvedUserId != null) {
                    fetchProfile(resolvedUserId)
                } else {
                    // Offline or timed out — no profile available
                    _isProfileLoading.value = false
                }
            }
        }
    }

    private suspend fun fetchProfile(userId: String) {
        _isProfileLoading.value = true
        try {
            // Get from repository (handles local fallback and remote sync)
            var p = profileRepository.getProfileById(userId)
            val authLastLogin = auth.currentSessionOrNull()?.user?.lastSignInAt?.toString()

            if (p == null) {
                // No local cache row for this user yet (fresh install, cleared app data,
                // or first login on this device before the initial sync has pulled the
                // profiles table down). The profiles table - not Supabase Auth
                // user_metadata - is the source of truth for role, so fetch this user's
                // row directly before ever guessing. Previously this jumped straight to
                // building a profile from user_metadata and defaulting role to "nurse"
                // whenever that metadata was missing/unset (the common case, since role
                // is normally managed via Manage Staff, not auth metadata) - silently
                // persisting the wrong role until a slower background refresh corrected
                // it, which is exactly the intermittent "opens as nurse" bug.
                p = profileRepository.fetchProfileFromRemote(userId)
            }

            if (p == null) {
                // Still nothing - genuinely offline with no cache, or a brand-new signup
                // whose profile row hasn't been provisioned server-side yet. Fall back to
                // a synthetic profile from auth metadata only as a last resort.
                val currentUser = auth.currentSessionOrNull()?.user
                if (currentUser != null) {
                    p = Profile(
                        id = currentUser.id,
                        email = currentUser.email ?: "",
                        displayName = currentUser.userMetadata?.get("display_name").metadataString()
                            ?: currentUser.userMetadata?.get("name").metadataString()
                            ?: currentUser.email?.substringBefore("@") ?: "User",
                        phoneNumber = currentUser.userMetadata?.get("phone_number").metadataString() ?: "",
                        employeeId = currentUser.userMetadata?.get("employee_id").metadataString(),
                        role = try {
                            com.neochildclinic.domain.model.UserRole.valueOf(currentUser.userMetadata?.get("role").metadataString() ?: "nurse")
                        } catch (_: Exception) { com.neochildclinic.domain.model.UserRole.nurse },
                        lastLogin = authLastLogin
                    )
                    profileRepository.saveLocalProfile(p)
                }
            } else if (p.lastLogin != authLastLogin) {
                p = p.copy(lastLogin = authLastLogin)
                profileRepository.updateProfile(p)
            }

            if (p != null && !p.isActive) {
                logout()
                _error.value = "Account Disabled: Please contact administrator."
                return
            }

            _profile.value = p

            // Background refresh
            profileRepository.refreshProfiles()
            profileRepository.getProfileById(userId)?.let { refreshed ->
                if (!refreshed.isActive) {
                    logout()
                    _error.value = "Account Disabled: Please contact administrator."
                } else {
                    _profile.value = refreshed
                }
            }
        } catch (e: Exception) {
            _error.value = "Failed to load profile: ${e.message}"
        } finally {
            _isProfileLoading.value = false
        }
    }

    fun login(email: String, pass: String, onSuccess: () -> Unit) {
        if (email.isBlank() || pass.isBlank()) {
            _error.value = "Please fill all fields"
            return
        }
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                auth.signInWith(Email) {
                    this.email = email
                    this.password = pass
                }

                auth.currentSessionOrNull()?.user?.id?.let {
                    fetchProfile(it)
                    // Device registration is best-effort and must not delay or block
                    // the initial application data sync if Supabase/user_devices times out.
                    launch { deviceRepository.registerCurrentDevice() }
                }

                _isLoading.value = false
                syncManager.scheduleImmediateSync()
                onSuccess()
            } catch (e: Exception) {
                _isLoading.value = false
                _error.value = e.message
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            deviceRepository.deactivateCurrentDevice()
            auth.signOut()
            _profile.value = null
        }
    }

    fun refreshSessionStatus() {
        val userId = auth.currentSessionOrNull()?.user?.id ?: return
        viewModelScope.launch {
            fetchProfile(userId)
        }
    }

    /**
     * Re-authenticates the currently signed-in account by re-entering its password, used
     * by the biometric/app-lock fallback (MainActivity's account-password unlock). A wrong
     * password or a missing account email surfaces as a failed Result - the caller decides
     * how to present it.
     */
    suspend fun reauthenticateWithPassword(password: String): Result<Unit> = runCatching {
        val email = auth.currentSessionOrNull()?.user?.email
        if (email.isNullOrBlank()) {
            throw IllegalStateException("No account email is available.")
        }
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }
}