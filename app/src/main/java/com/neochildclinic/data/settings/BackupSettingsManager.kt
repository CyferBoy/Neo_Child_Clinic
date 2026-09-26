package com.neochildclinic.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.neochildclinic.domain.model.AutoBackupSettings
import com.neochildclinic.domain.model.BackupFrequency
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Separate DataStore file from NotificationSettingsManager's "app_settings" - each
// `preferencesDataStore` delegate must be the only one for its file name in the process, so
// Backup & Restore gets its own file rather than sharing/duplicating that one.
private val Context.backupDataStore: DataStore<Preferences> by preferencesDataStore(name = "backup_settings")

@Singleton
class BackupSettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ENABLED = booleanPreferencesKey("auto_backup_enabled")
    private val FREQUENCY = stringPreferencesKey("auto_backup_frequency")
    private val CLOUD_ENABLED = booleanPreferencesKey("auto_backup_cloud_enabled")
    private val RETAIN_COUNT = intPreferencesKey("auto_backup_retain_count")
    private val LAST_RUN_AT = stringPreferencesKey("auto_backup_last_run_at")
    private val LAST_RUN_STATUS = stringPreferencesKey("auto_backup_last_run_status")

    val settingsFlow: Flow<AutoBackupSettings> = context.backupDataStore.data.map { prefs ->
        AutoBackupSettings(
            enabled = prefs[ENABLED] ?: false,
            frequency = prefs[FREQUENCY]?.let { runCatching { BackupFrequency.valueOf(it) }.getOrNull() } ?: BackupFrequency.DAILY,
            cloudEnabled = prefs[CLOUD_ENABLED] ?: false,
            retainCount = prefs[RETAIN_COUNT] ?: 5,
            lastRunAt = prefs[LAST_RUN_AT],
            lastRunStatus = prefs[LAST_RUN_STATUS]
        )
    }

    suspend fun getSettings(): AutoBackupSettings = settingsFlow.first()

    suspend fun updateSettings(settings: AutoBackupSettings) {
        context.backupDataStore.edit { prefs ->
            prefs[ENABLED] = settings.enabled
            prefs[FREQUENCY] = settings.frequency.name
            prefs[CLOUD_ENABLED] = settings.cloudEnabled
            prefs[RETAIN_COUNT] = settings.retainCount.coerceIn(1, 30)
        }
    }

    suspend fun recordRun(atIso: String, status: String) {
        context.backupDataStore.edit { prefs ->
            prefs[LAST_RUN_AT] = atIso
            prefs[LAST_RUN_STATUS] = status
        }
    }
}

