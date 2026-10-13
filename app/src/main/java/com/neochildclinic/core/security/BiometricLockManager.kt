package com.neochildclinic.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BiometricLockManager {
    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private var screenWasOff = false
    private var protectionEnabled = true
    private var lastActiveTime: Long = System.currentTimeMillis()
    private const val INACTIVITY_TIMEOUT = 5 * 60 * 1000L

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
        if (!protectionEnabled) {
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
