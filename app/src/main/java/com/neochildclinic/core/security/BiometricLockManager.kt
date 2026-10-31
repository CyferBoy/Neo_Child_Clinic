package com.neochildclinic.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BiometricLockManager {
    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    /**
     * Whether [isAppLocked] is meaningful yet.
     *
     * [isAppLocked] is `false` both when the app is genuinely unlocked and when nobody has
     * checked yet - and at launch it is the latter, because deciding requires awaiting the
     * session status, which takes seconds when offline. A caller that treats that initial
     * `false` as "unlocked" composes the whole patient-data tree during the gap.
     *
     * This separates the two. Until [markLockStateResolved] is called, the answer is unknown
     * and callers must not render protected content. Lives on the object rather than in
     * MainActivity so it survives configuration change without a second unlock round-trip.
     */
    private val _lockStateResolved = MutableStateFlow(false)
    val lockStateResolved: StateFlow<Boolean> = _lockStateResolved.asStateFlow()

    fun markLockStateResolved() {
        _lockStateResolved.value = true
    }

    /** Test-only: restores the unknown state so each test starts from the same place. */
    internal fun resetLockStateResolvedForTest() {
        _lockStateResolved.value = false
    }

    private var screenWasOff = false
    private var protectionEnabled = true
    private var lastActiveTime: Long = System.currentTimeMillis()
    private const val INACTIVITY_TIMEOUT = 5 * 60 * 1000L
    private var suppressLockUntil: Long = 0L

    fun suppressLock(durationMs: Long = 120_000L) {
        suppressLockUntil = System.currentTimeMillis() + durationMs
    }

    fun isLockSuppressed(): Boolean {
        val suppressed = System.currentTimeMillis() < suppressLockUntil
        if (!suppressed) {
            suppressLockUntil = 0L
        }
        return suppressed
    }

    fun clearLockSuppression() {
        suppressLockUntil = 0L
    }

    /**
     * Persists the arm flag (NotificationSettingsManager's `needs_auth`) so the lock
     * survives process death - the in-memory [screenWasOff] flag above does not.
     * Installed by NeoChildApp; null before that (e.g. in unit tests).
     */
    var onNeedsAuthChanged: ((Boolean) -> Unit)? = null

    fun onScreenOff() {
        if (protectionEnabled) {
            screenWasOff = true
            onNeedsAuthChanged?.invoke(true)
        }
    }

    fun onAppResume() {
        if (!protectionEnabled || isLockSuppressed()) {
            _isAppLocked.value = false
            screenWasOff = false
            onUserActive()
            return
        }

        val currentTime = System.currentTimeMillis()
        val inactiveTooLong = (currentTime - lastActiveTime) > INACTIVITY_TIMEOUT
        // lock() (not a direct value set) so the arm flag is persisted too.
        if (screenWasOff || inactiveTooLong) lock()
        screenWasOff = false
    }

    fun setProtectionEnabled(enabled: Boolean) {
        protectionEnabled = enabled
        if (!enabled) {
            _isAppLocked.value = false
            screenWasOff = false
            onUserActive()
        }
    }

    fun onUserActive() {
        lastActiveTime = System.currentTimeMillis()
    }

    /** Called only after BiometricAuthenticator completes the Keystore operation. */
    fun unlockAfterKeystoreVerification() {
        _isAppLocked.value = false
        // The persisted arm flag is cleared only here - a real unlock, never on resume.
        onNeedsAuthChanged?.invoke(false)
        onUserActive()
    }

    /** Protection is intentionally disabled by the user after authentication. */
    fun unlockBecauseProtectionIsDisabled() {
        _isAppLocked.value = false
        onUserActive()
    }

    fun lock() {
        _isAppLocked.value = true
        onNeedsAuthChanged?.invoke(true)
    }
}
