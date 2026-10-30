package com.neochildclinic.core.preferences

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
    private val EXPIRY_WARNINGS_ENABLED = booleanPreferencesKey("expiry_warnings_enabled")
    private val WARN_DAYS_BEFORE_EXPIRY = intPreferencesKey("warn_days_before_expiry")
    private val HIDE_EXPIRED_EMPTY = booleanPreferencesKey("hide_expired_empty")
    private val SHOW_STOCK_VALUE_ENABLED = booleanPreferencesKey("show_stock_value_enabled")
    private val INVENTORY_SORT_METHOD = stringPreferencesKey("inventory_sort_method")
    
    // Security Settings
    private val BIOMETRIC_LOCK_ENABLED = booleanPreferencesKey("biometric_lock_enabled")
    private val INACTIVITY_DAYS_THRESHOLD = intPreferencesKey("inactivity_days_threshold")
    private val AUTH_ON_EVERY_OPEN = booleanPreferencesKey("auth_on_every_open")
    
    // Internal Tracking
    private val LAST_APP_OPEN_TIMESTAMP = longPreferencesKey("last_app_open_timestamp")
    private val NEEDS_AUTH = booleanPreferencesKey("needs_auth")
    private val LAST_SUMMARY_SENT_DATE = stringPreferencesKey("last_summary_sent_date")
    private val NOTIFIED_LOW_STOCK_VACCINES = stringSetPreferencesKey("notified_low_stock_vaccines")

    val settingsFlow: Flow<NotificationSettings> = context.dataStore.data.map { preferences ->
        NotificationSettings(
            dailySummaryEnabled = preferences[DAILY_SUMMARY_ENABLED] ?: true,
            lowStockEnabled = preferences[LOW_STOCK_ENABLED] ?: true,
            syncAlertsEnabled = preferences[SYNC_ALERTS_ENABLED] ?: true,
            reminderTime = preferences[REMINDER_TIME] ?: "08:00",
            lowStockThreshold = preferences[LOW_STOCK_THRESHOLD] ?: 5,
            expiryWarningsEnabled = preferences[EXPIRY_WARNINGS_ENABLED] ?: true,
            warnDaysBeforeExpiry = preferences[WARN_DAYS_BEFORE_EXPIRY] ?: 30,
            hideExpiredEmpty = preferences[HIDE_EXPIRED_EMPTY] ?: true,
            showStockValueEnabled = preferences[SHOW_STOCK_VALUE_ENABLED] ?: true,
            sortMethod = preferences[INVENTORY_SORT_METHOD] ?: "Alphabetical",
            
            biometricLockEnabled = preferences[BIOMETRIC_LOCK_ENABLED] ?: true,
            inactivityDaysThreshold = preferences[INACTIVITY_DAYS_THRESHOLD] ?: 10,
            authOnEveryOpen = preferences[AUTH_ON_EVERY_OPEN] ?: false,
            
            lastAppOpenTimestamp = preferences[LAST_APP_OPEN_TIMESTAMP] ?: System.currentTimeMillis(),
            needsAuth = preferences[NEEDS_AUTH] ?: false,
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
            preferences[EXPIRY_WARNINGS_ENABLED] = settings.expiryWarningsEnabled
            preferences[WARN_DAYS_BEFORE_EXPIRY] = settings.warnDaysBeforeExpiry
            preferences[HIDE_EXPIRED_EMPTY] = settings.hideExpiredEmpty
            preferences[SHOW_STOCK_VALUE_ENABLED] = settings.showStockValueEnabled
            preferences[INVENTORY_SORT_METHOD] = settings.sortMethod
            
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

    /**
     * Set when the app lock arms (screen off / lock), cleared only after a successful
     * unlock. Unlike BiometricLockManager's in-memory state this survives process death.
     */
    suspend fun setNeedsAuth(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NEEDS_AUTH] = value
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
    val dailySummaryEnabled: Boolean = true,
    val lowStockEnabled: Boolean = true,
    val syncAlertsEnabled: Boolean = true,
    val reminderTime: String = "08:00",
    val lowStockThreshold: Int = 5,
    val expiryWarningsEnabled: Boolean = true,
    val warnDaysBeforeExpiry: Int = 30,
    val hideExpiredEmpty: Boolean = true,
    val showStockValueEnabled: Boolean = true,
    val sortMethod: String = "Alphabetical",
    
    val biometricLockEnabled: Boolean = true,
    val inactivityDaysThreshold: Int = 10,
    val authOnEveryOpen: Boolean = false,
    
    val lastAppOpenTimestamp: Long = 0L,
    val needsAuth: Boolean = false,
    val lastSummarySentDate: String = "",
    val notifiedLowStockVaccines: Set<String> = emptySet()
)

