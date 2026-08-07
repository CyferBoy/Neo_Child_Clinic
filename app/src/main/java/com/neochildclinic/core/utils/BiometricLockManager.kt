package com.neochildclinic.core.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BiometricLockManager {
    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private var screenWasOff = false
    private var lastActiveTime: Long = System.currentTimeMillis()
    
    // Inactivity timeout in milliseconds (e.g., 5 minutes)
    private const val INACTIVITY_TIMEOUT = 5 * 60 * 1000L

    fun onScreenOff() {
        screenWasOff = true
    }

    fun onAppResume() {
        // Determine if we should lock based on screen state or inactivity
        val currentTime = System.currentTimeMillis()
        val inactiveTooLong = (currentTime - lastActiveTime) > INACTIVITY_TIMEOUT
        
        if (screenWasOff || inactiveTooLong) {
            _isAppLocked.value = true
        }
        
        // Reset screenWasOff once checked
        screenWasOff = false
    }

    fun onUserActive() {
        lastActiveTime = System.currentTimeMillis()
    }

    fun unlock() {
        _isAppLocked.value = false
        onUserActive()
    }
    
    fun lock() {
        _isAppLocked.value = true
    }
}
