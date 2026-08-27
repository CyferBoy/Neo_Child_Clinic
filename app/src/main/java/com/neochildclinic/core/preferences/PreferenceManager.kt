package com.neochildclinic.core.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val MIGRATION_COMPLETED = booleanPreferencesKey("patient_id_migration_completed")
    private val THEME_MODE = stringPreferencesKey("theme_mode")

    val isPatientIdMigrationCompleted: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[MIGRATION_COMPLETED] ?: false
        }

    val themeMode: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[THEME_MODE] ?: "system"
        }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    suspend fun setPatientIdMigrationCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MIGRATION_COMPLETED] = completed
        }
    }
}
