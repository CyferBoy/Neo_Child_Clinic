package com.neochildclinic.core.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BiometricLockManager {
    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private var screenWasOff = false
    private var lastActiveTime: Long = System.currentTimeMillis()
    private const val INACTIVITY_TIMEOUT = 5 * 60 * 1000L

    fun onScreenOff() {
        screenWasOff = true
    }

    fun onAppResume() {
        val currentTime = System.currentTimeMillis()
        val inactiveTooLong = (currentTime - lastActiveTime) > INACTIVITY_TIMEOUT
        if (screenWasOff || inactiveTooLong) _isAppLocked.value = true
        screenWasOff = false
    }

    fun onUserActive() {
        lastActiveTime = System.currentTimeMillis()
    }

    /** Called only after BiometricAuthenticator completes the Keystore operation. */
    fun unlockAfterKeystoreVerification() {
        _isAppLocked.value = false
        onUserActive()
    }

    /** Protection is intentionally disabled by the user after authentication. */
    fun unlockBecauseProtectionIsDisabled() {
        _isAppLocked.value = false
        onUserActive()
    }

    fun lock() {
        _isAppLocked.value = true
    }
}
