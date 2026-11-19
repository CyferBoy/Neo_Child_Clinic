package com.neochildclinic.feature.auth.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.cache.CacheRegistry
import com.neochildclinic.core.network.NetworkMonitor
import com.neochildclinic.core.security.CurrentUserProvider
import com.neochildclinic.core.sync.SyncScheduler
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.domain.repository.DeviceRepository
import com.neochildclinic.domain.repository.ProfileRepository
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.feature.widget.data.WidgetUtils
import com.neochildclinic.core.security.OfflineAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.neochildclinic.feature.auth.domain.model.AuthSessionStatus
import com.neochildclinic.feature.auth.domain.model.AuthUser
import com.neochildclinic.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val deviceRepository: DeviceRepository,
    private val syncManager: SyncScheduler,
    private val cacheRegistry: CacheRegistry,
    private val currentUserProvider: CurrentUserProvider,
    private val syncRepository: SyncRepository,
    private val networkMonitor: NetworkMonitor,
    private val offlineAuthManager: OfflineAuthManager,
    @ApplicationContext private val appContext: Context
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

    val currentUser: AuthUser? get() = authRepository.currentUser

    val sessionStatus: StateFlow<AuthSessionStatus> = authRepository.sessionStatus

    suspend fun awaitResolvedSessionStatus(): AuthSessionStatus? =
        authRepository.awaitResolvedSessionStatus(SESSION_STATUS_TIMEOUT_MS)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

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
        appContext.getSharedPreferences(ACCOUNT_PREFS, Context.MODE_PRIVATE)

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
        _isProfileLoading.update { true }
        viewModelScope.launch {
            val userId = authRepository.currentUser?.id
            if (userId != null) {
                fetchProfile(userId)
            } else {
                // Session not yet resolved from storage — wait for it
                val status = awaitResolvedSessionStatus()
                val resolvedUserId = (status as? AuthSessionStatus.Authenticated)?.user?.id ?: authRepository.currentUser?.id
                if (status is AuthSessionStatus.Authenticated && resolvedUserId != null) {
                    fetchProfile(resolvedUserId)
                } else {
                    // Offline or timed out — no profile available
                    _isProfileLoading.update { false }
                }
            }
        }
    }

    private suspend fun fetchProfile(userId: String): Boolean {
        _isProfileLoading.update { true }
        try {
            // Get from repository (handles local fallback and remote sync)
            var p = profileRepository.getProfileById(userId)
            val authLastLogin = authRepository.currentUser?.lastSignInAt

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
                _error.update { "Unable to load your profile. Check your connection and try again." }
                return false
            } else if (p.lastLogin != authLastLogin) {
                p = p.copy(lastLogin = authLastLogin)
                profileRepository.updateProfile(p)
            }

            if (!p.isActive) {
                offlineAuthManager.clearCredential()
                logout()
                _error.update { "Account Disabled: Please contact administrator." }
                return false
            }

            val currentP = p
            _profile.update { currentP }

            // Background refresh
            profileRepository.refreshProfiles()
            // SE-01: refreshProfiles() is the real authenticated round-trip; only a
            // successful server pull refreshes the stamp that permits offline unlock.
            offlineAuthManager.recordOnlineVerification()
            // SE-01: the profile pull is the "next sync" that consumes the server's
            // is_active/is_deleted flag from manage-staff. A deactivated (or soft-deleted,
            // which is pruned) account must lose its device-local verifier immediately.
            val refreshed = profileRepository.getProfileById(userId)
            if (refreshed == null || !refreshed.isActive) {
                offlineAuthManager.clearCredential()
                logout()
                _error.update { "Account Disabled: Please contact administrator." }
                return false
            }
            _profile.update { refreshed }
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = "Failed to load profile: ${e.message}"
            _error.update { msg }
            return false
        } finally {
            _isProfileLoading.update { false }
        }
    }

    fun login(email: String, pass: String, onSuccess: () -> Unit) {
        if (email.isBlank() || pass.isBlank()) {
            _error.update { "Please fill all fields" }
            return
        }
        _isLoading.update { true }
        _error.update { null }

        viewModelScope.launch {
            try {
                // A session can end without logout() (revoked/expired token); never let the
                // next sign-in inherit the previous user's in-memory data.
                cacheRegistry.clearAll()
                val loginResult = authRepository.login(email, pass)
                if (loginResult.isFailure) {
                    throw loginResult.exceptionOrNull()!!
                }
                val userId = loginResult.getOrNull()?.id
                val lastAccount = accountPrefs.getString(KEY_LAST_ACCOUNT, null)
                if (userId != null && lastAccount != userId) {
                    // The Room database on this device belongs to a different account (or no previous record).
                    val unacknowledged = syncRepository.getUnacknowledgedCount()
                    if (unacknowledged > 0) {
                        // Never erase another account's unsynced work silently - ask.
                        pendingSwitch = PendingAccountSwitch(userId, onSuccess)
                        _loginSwitchBlocked.update { unacknowledged }
                        _isLoading.update { false }
                        return@launch
                    }
                    // Everything is already acknowledged: dropping the cached rows is a
                    // cache invalidation, not data loss, and keeps one clinic's patients
                    // out of another account's screens. Room's clearAllTables is a blocking
                    // @WorkerThread call, so it must never run on the main dispatcher.
                    try {
                        clearLocalDatabase()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _isLoading.update { false }
                        val clearErr = "Could not clear the previous account's local data: ${e.message}"
                        _error.update { clearErr }
                        authRepository.clearSession()
                        return@launch
                    }
                }
                finishLogin(userId, onSuccess)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _isLoading.update { false }
                val err = e.message
                _error.update { err }
            }
        }
    }

    private suspend fun finishLogin(userId: String?, onSuccess: () -> Unit) {
        if (userId != null) {
            accountPrefs.edit().putString(KEY_LAST_ACCOUNT, userId).apply()
            if (!fetchProfile(userId)) {
                _isLoading.update { false }
                return // stay on the login screen; error is already set
            }
            // Device registration is best-effort and must not delay or block
            // the initial application data sync if Supabase/user_devices times out.
            viewModelScope.launch { deviceRepository.registerCurrentDevice() }
        }

        _isLoading.update { false }
        syncManager.scheduleImmediateSync()
        WidgetUtils.updateWidget(appContext)
        onSuccess()
    }

    /** User consented to erase the previous account's local rows and continue signing in. */
    fun confirmAccountSwitchReset() {
        val pending = pendingSwitch ?: return
        viewModelScope.launch {
            try {
                clearLocalDatabase()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val clearErr = "Could not clear the previous account's local data: ${e.message}"
                _error.update { clearErr }
                return@launch
            }
            pendingSwitch = null
            _loginSwitchBlocked.update { null }
            finishLogin(pending.userId, pending.onSuccess)
        }
    }

    /** User declined: drop the new session so nothing is written under it, keep old data. */
    fun cancelAccountSwitch() {
        pendingSwitch = null
        _loginSwitchBlocked.update { null }
        viewModelScope.launch {
            authRepository.signOut()
            authRepository.clearSession()
            // SE-07: login() already wrote the new account's verifier before the switch was
            // blocked. Drop it here too (same as completeSignOut) so a cancelled switch
            // never leaves that password usable for offline unlock.
            offlineAuthManager.clearCredential()
            _isLoading.update { false }
            _error.update { "Sign-in cancelled. The previous account's unsynced changes were kept." }
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
            val unacknowledged = syncRepository.getUnacknowledgedCount()
            if (unacknowledged > 0) {
                pendingSignOutCallback = onSignedOut
                _logoutBlocked.update { unacknowledged }
                return@launch
            }
            if (!completeSignOut()) return@launch
            onSignedOut()
        }
    }

    fun confirmSignOutErasingData() {
        val continuation = pendingSignOutCallback
        viewModelScope.launch {
            pendingSignOutCallback = null
            _logoutBlocked.update { null }
            if (completeSignOut()) continuation?.invoke()
        }
    }

    fun cancelSignOut() {
        pendingSignOutCallback = null
        _logoutBlocked.update { null }
    }

    private suspend fun completeSignOut(): Boolean {
        // Each step is best-effort: a failed device deactivation or a failed server
        // revocation must not leave a half-signed-out session pointing at another
        // account's (now erased) data.
        runCatching { deviceRepository.deactivateCurrentDevice() }
        authRepository.signOut()
        authRepository.clearSession()
        // Session isolation: in-memory caches AND the Room database must never carry
        // User A's data to User B. Safe here: only reached when the queue has no
        // unacknowledged rows or the user explicitly confirmed the erase.
        cacheRegistry.clearAll()
        _profile.update { null }
        try {
            clearLocalDatabase()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep KEY_LAST_ACCOUNT so the next login still detects the account mismatch
            // and asks before exposing the old rows. Do not report a successful sign-out.
            val eraseErr = "Could not erase local data: ${e.message}"
            _error.update { eraseErr }
            return false
        }
        offlineAuthManager.clearCredential()
        accountPrefs.edit().remove(KEY_LAST_ACCOUNT).apply()
        WidgetUtils.updateWidget(appContext)
        return true
    }

    private suspend fun clearLocalDatabase() {
        syncRepository.clearLocalData()
    }

    fun refreshSessionStatus() {
        val userId = authRepository.currentUser?.id ?: return
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
    suspend fun reauthenticateWithPassword(password: String): Result<Unit> =
        authRepository.reauthenticateWithPassword(password)
}
