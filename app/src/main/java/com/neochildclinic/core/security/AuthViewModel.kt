package com.neochildclinic.core.security

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.neochildclinic.core.cache.CacheRegistry
import com.neochildclinic.core.sync.SyncManagerImpl
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.feature.profile.data.ProfileRepositoryImpl
import com.neochildclinic.feature.auth.data.DeviceRepositoryImpl
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
    private val syncManager: SyncManagerImpl,
    private val cacheRegistry: CacheRegistry,
    private val currentUserProvider: CurrentUserProvider
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

    /** Authorization role from the session's app_metadata — the single source of truth. */
    val currentUserRole: UserRole? get() = currentUserProvider.getCurrentUserRole()

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

    private suspend fun fetchProfile(userId: String): Boolean {
        _isProfileLoading.value = true
        try {
            // Get from repository (handles local fallback and remote sync)
            var p = profileRepository.getProfileById(userId)
            val authLastLogin = auth.currentSessionOrNull()?.user?.lastSignInAt?.toString()

            if (p == null) {
                // The profiles table is business/display data (name, phone, activation).
                // The authorization role is NOT read here - it comes from app_metadata via
                // CurrentUserProvider.getCurrentUserRole(). user_metadata is never consulted.
                p = profileRepository.fetchProfileFromRemote(userId)
            }

            if (p == null) {
                // First login / fresh install must happen online: there is deliberately
                // no metadata-based fallback (user_metadata no longer carries a role;
                // app_metadata.role is read separately via CurrentUserProvider for
                // authorization). Fail loudly instead of guessing a profile/role.
                _error.value = "Unable to load your profile. Check your connection and try again."
                return false
            } else if (p.lastLogin != authLastLogin) {
                p = p.copy(lastLogin = authLastLogin)
                profileRepository.updateProfile(p)
            }

            if (!p.isActive) {
                logout()
                _error.value = "Account Disabled: Please contact administrator."
                return true
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
            return true
        } catch (e: Exception) {
            _error.value = "Failed to load profile: ${e.message}"
            return false
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
                // A session can end without logout() (revoked/expired token); never let the
                // next sign-in inherit the previous user's in-memory data.
                cacheRegistry.clearAll()
                auth.signInWith(Email) {
                    this.email = email
                    this.password = pass
                }

                auth.currentSessionOrNull()?.user?.id?.let {
                    if (!fetchProfile(it)) {
                        _isLoading.value = false
                        return@launch // stay on the login screen; error is already set
                    }
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
            try {
                deviceRepository.deactivateCurrentDevice()
                auth.signOut()
            } finally {
                // Session isolation: in-memory caches must never carry User A's data to User B.
                cacheRegistry.clearAll()
                _profile.value = null
            }
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