package com.neochildclinic.feature.settings.presentation

import com.neochildclinic.core.preferences.NotificationSettings
import com.neochildclinic.core.preferences.NotificationSettingsManager
import com.neochildclinic.core.security.BiometricLockManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.notification.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val reminderScheduler: ReminderScheduler
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
}
