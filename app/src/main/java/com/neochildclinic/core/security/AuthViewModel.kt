package com.neochildclinic.core.security

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.neochildclinic.core.cache.CacheRegistry
import com.neochildclinic.core.sync.SyncManagerImpl
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.feature.profile.data.ProfileRepositoryImpl
import com.neochildclinic.feature.profile.domain.repository.DeviceRepository
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import androidx.compose.runtime.State
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: Auth,
    private val profileRepository: com.neochildclinic.feature.profile.domain.repository.ProfileRepository,
    private val deviceRepository: DeviceRepository,
    private val syncManager: SyncManagerImpl,
    private val cacheRegistry: CacheRegistry,
    private val currentUserProvider: CurrentUserProvider,
    private val syncRepository: com.neochildclinic.feature.sync.domain.repository.SyncRepository,
    private val syncQueueDao: com.neochildclinic.data.local.dao.SyncQueueDao,
    private val appDatabase: com.neochildclinic.data.local.database.AppDatabase,
    private val networkMonitor: com.neochildclinic.core.network.NetworkMonitor,
    @ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    companion object {
        private const val SESSION_STATUS_TIMEOUT_MS = 4_000L

        // Bounded flush pass before sign-out: enough time for one queue run over a
        // normal connection, short enough that Logout never feels hung up.
        private const val SIGN_OUT_FLUSH_TIMEOUT_MS = 20_000L

        // Which account the local Room database currently belongs to. Written on every
        // completed sign-in, cleared whenever the local data is erased. This is what
        // lets a sign-in detect "the cached rows on this device belong to someone else"
        // without adding an owner column to every table (no schema change).
        private const val ACCOUNT_PREFS = "account_prefs"
        private const val KEY_LAST_ACCOUNT = "last_account_id"
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

    // True while the profile is being resolved. Post-migration this gates only the
    // display skeleton — authorization role comes from `currentUserRole` (session
    // app_metadata), not from the profile.
    private val _isProfileLoading = MutableStateFlow(false)
    val isProfileLoading: StateFlow<Boolean> = _isProfileLoading.asStateFlow()

    /** Authorization role from the session's app_metadata — the single source of truth. */
    val currentUserRole: UserRole? get() = currentUserProvider.getCurrentUserRole()

    private val accountPrefs =
        appContext.getSharedPreferences(ACCOUNT_PREFS, android.content.Context.MODE_PRIVATE)

    // --- Sign-out / account-switch isolation -----------------------------------
    // Sign-out must never silently erase unsynced local work, and must never leave one
    // account's rows readable by the next. Flow: flush (bounded) -> if anything is
    // still unacknowledged, block and ask; only after sync or explicit consent is the
    // local database erased.

    /** Non-null while a sign-out is waiting for the user to confirm erasing N unsynced changes. */
    private val _logoutBlocked = MutableStateFlow<Int?>(null)
    val logoutBlocked: StateFlow<Int?> = _logoutBlocked.asStateFlow()
    private var pendingSignOutCallback: (() -> Unit)? = null

    private data class PendingAccountSwitch(val userId: String, val onSuccess: () -> Unit)

    /** Non-null while a sign-in is waiting for consent to erase the previous account's data. */
    private val _loginSwitchBlocked = MutableStateFlow<Int?>(null)
    val loginSwitchBlocked: StateFlow<Int?> = _loginSwitchBlocked.asStateFlow()
    private var pendingSwitch: PendingAccountSwitch? = null

    init {
        // Mark loading immediately so the profile skeleton shows instead of stale data.
        // On cold start, auth.currentSessionOrNull() can return null briefly while
        // Supabase resolves the stored session, causing the ?.let below to skip
        // fetchProfile entirely — leaving isProfileLoading=false prematurely.
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

                val userId = auth.currentSessionOrNull()?.user?.id
                val lastAccount = accountPrefs.getString(KEY_LAST_ACCOUNT, null)
                if (userId != null && lastAccount != null && lastAccount != userId) {
                    // The Room database on this device belongs to a different account.
                    val unacknowledged = syncQueueDao.getUnacknowledgedCountSync()
                    if (unacknowledged > 0) {
                        // Never erase another account's unsynced work silently - ask.
                        pendingSwitch = PendingAccountSwitch(userId, onSuccess)
                        _loginSwitchBlocked.value = unacknowledged
                        _isLoading.value = false
                        return@launch
                    }
                    // Everything is already acknowledged: dropping the cached rows is a
                    // cache invalidation, not data loss, and keeps one clinic's patients
                    // out of another account's screens.
                    runCatching { appDatabase.clearAllTables() }
                }
                finishLogin(userId, onSuccess)
            } catch (e: Exception) {
                _isLoading.value = false
                _error.value = e.message
            }
        }
    }

    private suspend fun finishLogin(userId: String?, onSuccess: () -> Unit) {
        if (userId != null) {
            accountPrefs.edit().putString(KEY_LAST_ACCOUNT, userId).apply()
            if (!fetchProfile(userId)) {
                _isLoading.value = false
                return // stay on the login screen; error is already set
            }
            // Device registration is best-effort and must not delay or block
            // the initial application data sync if Supabase/user_devices times out.
            viewModelScope.launch { deviceRepository.registerCurrentDevice() }
        }

        _isLoading.value = false
        syncManager.scheduleImmediateSync()
        onSuccess()
    }

    /** User consented to erase the previous account's local rows and continue signing in. */
    fun confirmAccountSwitchReset() {
        val pending = pendingSwitch ?: return
        viewModelScope.launch {
            runCatching { appDatabase.clearAllTables() }
            pendingSwitch = null
            _loginSwitchBlocked.value = null
            finishLogin(pending.userId, pending.onSuccess)
        }
    }

    /** User declined: drop the new session so nothing is written under it, keep old data. */
    fun cancelAccountSwitch() {
        pendingSwitch = null
        _loginSwitchBlocked.value = null
        viewModelScope.launch {
            runCatching { auth.signOut() }
            // signOut() only clears the local session after its network call resolves;
            // clearSession() guarantees the new session is gone even offline, so a
            // declined switch never leaves the new account signed in over old data.
            runCatching { auth.clearSession() }
            _isLoading.value = false
            _error.value = "Sign-in cancelled. The previous account's unsynced changes were kept."
        }
    }

    /**
     * Sign-out with the safety gate: flush the sync queue (bounded, online only), then
     * - if everything reached the server: sign out and erase local data (nothing pending
     *   is lost, so nothing is erased "silently");
     * - if changes remain unacknowledged: block and wait for an explicit decision via
     *   [confirmSignOutErasingData] / [cancelSignOut].
     *
     * [onSignedOut] runs only after an actual sign-out, so navigation callbacks never
     * fire while the gate is still up.
     */
    fun logout(onSignedOut: () -> Unit = {}) {
        viewModelScope.launch {
            if (networkMonitor.isOnline.first()) {
                withTimeoutOrNull(SIGN_OUT_FLUSH_TIMEOUT_MS) {
                    runCatching { syncRepository.processNextItems() }
                }
            }
            val unacknowledged = syncQueueDao.getUnacknowledgedCountSync()
            if (unacknowledged > 0) {
                pendingSignOutCallback = onSignedOut
                _logoutBlocked.value = unacknowledged
                return@launch
            }
            completeSignOut()
            onSignedOut()
        }
    }

    fun confirmSignOutErasingData() {
        val continuation = pendingSignOutCallback
        viewModelScope.launch {
            pendingSignOutCallback = null
            _logoutBlocked.value = null
            completeSignOut()
            continuation?.invoke()
        }
    }

    fun cancelSignOut() {
        pendingSignOutCallback = null
        _logoutBlocked.value = null
    }

    private suspend fun completeSignOut() {
        // Each step is best-effort: a failed device deactivation or a failed server
        // revocation must not leave a half-signed-out session pointing at another
        // account's (now erased) data.
        runCatching { deviceRepository.deactivateCurrentDevice() }
        runCatching { auth.signOut() }
        // signOut() clears the local session only after its network call resolves; this
        // guarantees the session is gone even when signing out offline.
        runCatching { auth.clearSession() }
        // Session isolation: in-memory caches AND the Room database must never carry
        // User A's data to User B. Safe here: only reached when the queue has no
        // unacknowledged rows or the user explicitly confirmed the erase.
        cacheRegistry.clearAll()
        _profile.value = null
        runCatching { appDatabase.clearAllTables() }
        accountPrefs.edit().remove(KEY_LAST_ACCOUNT).apply()
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