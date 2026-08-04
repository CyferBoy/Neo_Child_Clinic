package com.neochildclinic.features.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Singleton
class NotificationSettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Notification Settings
    private val DAILY_SUMMARY_ENABLED = booleanPreferencesKey("daily_summary_enabled")
    private val LOW_STOCK_ENABLED = booleanPreferencesKey("low_stock_enabled")
    private val SYNC_ALERTS_ENABLED = booleanPreferencesKey("sync_alerts_enabled")
    private val REMINDER_TIME = stringPreferencesKey("reminder_time")
    private val LOW_STOCK_THRESHOLD = intPreferencesKey("low_stock_threshold")
    
    // Security Settings
    private val BIOMETRIC_LOCK_ENABLED = booleanPreferencesKey("biometric_lock_enabled")
    private val INACTIVITY_DAYS_THRESHOLD = intPreferencesKey("inactivity_days_threshold")
    private val AUTH_ON_EVERY_OPEN = booleanPreferencesKey("auth_on_every_open")
    
    // Internal Tracking
    private val LAST_APP_OPEN_TIMESTAMP = longPreferencesKey("last_app_open_timestamp")
    private val LAST_SUMMARY_SENT_DATE = stringPreferencesKey("last_summary_sent_date")
    private val NOTIFIED_LOW_STOCK_VACCINES = stringSetPreferencesKey("notified_low_stock_vaccines")

    val settingsFlow: Flow<NotificationSettings> = context.dataStore.data.map { preferences ->
        NotificationSettings(
            dailySummaryEnabled = preferences[DAILY_SUMMARY_ENABLED] ?: true,
            lowStockEnabled = preferences[LOW_STOCK_ENABLED] ?: true,
            syncAlertsEnabled = preferences[SYNC_ALERTS_ENABLED] ?: true,
            reminderTime = preferences[REMINDER_TIME] ?: "08:00",
            lowStockThreshold = preferences[LOW_STOCK_THRESHOLD] ?: 5,
            
            biometricLockEnabled = preferences[BIOMETRIC_LOCK_ENABLED] ?: true,
            inactivityDaysThreshold = preferences[INACTIVITY_DAYS_THRESHOLD] ?: 10,
            authOnEveryOpen = preferences[AUTH_ON_EVERY_OPEN] ?: false,
            
            lastAppOpenTimestamp = preferences[LAST_APP_OPEN_TIMESTAMP] ?: System.currentTimeMillis(),
            lastSummarySentDate = preferences[LAST_SUMMARY_SENT_DATE] ?: "",
            notifiedLowStockVaccines = preferences[NOTIFIED_LOW_STOCK_VACCINES] ?: emptySet()
        )
    }

    suspend fun updateSettings(settings: NotificationSettings) {
        context.dataStore.edit { preferences ->
            preferences[DAILY_SUMMARY_ENABLED] = settings.dailySummaryEnabled
            preferences[LOW_STOCK_ENABLED] = settings.lowStockEnabled
            preferences[SYNC_ALERTS_ENABLED] = settings.syncAlertsEnabled
            preferences[REMINDER_TIME] = settings.reminderTime
            preferences[LOW_STOCK_THRESHOLD] = settings.lowStockThreshold
            
            preferences[BIOMETRIC_LOCK_ENABLED] = settings.biometricLockEnabled
            preferences[INACTIVITY_DAYS_THRESHOLD] = settings.inactivityDaysThreshold
            preferences[AUTH_ON_EVERY_OPEN] = settings.authOnEveryOpen
        }
    }

    suspend fun updateLastOpenTimestamp() {
        context.dataStore.edit { preferences ->
            preferences[LAST_APP_OPEN_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun markSummarySent(dateStr: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SUMMARY_SENT_DATE] = dateStr
        }
    }

    suspend fun markLowStockNotified(vaccineId: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[NOTIFIED_LOW_STOCK_VACCINES] ?: emptySet()
            preferences[NOTIFIED_LOW_STOCK_VACCINES] = current + vaccineId
        }
    }

    suspend fun clearLowStockNotification(vaccineId: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[NOTIFIED_LOW_STOCK_VACCINES] ?: emptySet()
            preferences[NOTIFIED_LOW_STOCK_VACCINES] = current - vaccineId
        }
    }
}

data class NotificationSettings(
    val dailySummaryEnabled: Boolean,
    val lowStockEnabled: Boolean,
    val syncAlertsEnabled: Boolean,
    val reminderTime: String,
    val lowStockThreshold: Int,
    
    val biometricLockEnabled: Boolean,
    val inactivityDaysThreshold: Int,
    val authOnEveryOpen: Boolean,
    
    val lastAppOpenTimestamp: Long,
    val lastSummarySentDate: String,
    val notifiedLowStockVaccines: Set<String>
)
