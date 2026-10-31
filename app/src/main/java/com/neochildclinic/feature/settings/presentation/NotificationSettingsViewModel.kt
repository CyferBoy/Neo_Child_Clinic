package com.neochildclinic.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.notification.ReminderScheduler
import com.neochildclinic.core.preferences.NotificationSettings
import com.neochildclinic.core.preferences.NotificationSettingsManager
import com.neochildclinic.core.security.BiometricLockManager
import com.neochildclinic.feature.profile.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.Auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val settingsManager: NotificationSettingsManager,
    // ReminderScheduler's two schedule methods had exactly two call sites, both in
    // NeoChildApp.onCreate, so changing the reminder time did nothing until the next
    // process start (NCC-010).
    private val reminderScheduler: ReminderScheduler,
    private val profileRepository: ProfileRepository,
    private val auth: Auth
) : ViewModel() {

    val settings: StateFlow<NotificationSettings?> = settingsManager.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun updateSettings(settings: NotificationSettings) {
        viewModelScope.launch {
            settingsManager.updateSettings(settings)
            // ExistingPeriodicWorkPolicy.UPDATE recomputes the delay, so re-arming here
            // makes the new time take effect immediately instead of after a restart.
            reminderScheduler.scheduleDailySummary()
            reminderScheduler.schedulePersonalReminderNotifications()
        }
    }

    fun setBiometricLock(enabled: Boolean) {
        BiometricLockManager.setProtectionEnabled(enabled)
    }

    fun changePassword(newPassword: String, onResult: (Result<Unit>) -> Unit) {
        if (newPassword.length < 12) {
            onResult(Result.failure(IllegalArgumentException("Password must be at least 12 characters")))
            return
        }
        viewModelScope.launch {
            try {
                profileRepository.updateAuthPassword(newPassword)
                onResult(Result.success(Unit))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun signOutAllDevices(onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            try {
                auth.signOut()
                onResult(Result.success(Unit))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }
}
