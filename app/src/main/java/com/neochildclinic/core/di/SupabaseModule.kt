package com.neochildclinic.core.di

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.ktor.client.engine.cio.CIO
import com.russhwolf.settings.SharedPreferencesSettings
import com.neochildclinic.core.security.CurrentUserProvider
import com.neochildclinic.core.security.SecurityUtils
import com.neochildclinic.core.security.SessionCurrentUserProvider
import kotlinx.serialization.json.Json
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    private const val SUPABASE_URL = com.neochildclinic.BuildConfig.SUPABASE_URL
    private const val SUPABASE_ANON_KEY = com.neochildclinic.BuildConfig.SUPABASE_ANON_KEY

    @Provides
    @Singleton
    fun provideSupabaseClient(@ApplicationContext context: Context): SupabaseClient {
        val securePrefs = SecurityUtils.getSecurePrefs(context)
        migrateLegacySession(context, securePrefs)
        return createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            httpEngine = CIO.create()
            defaultSerializer = KotlinXSerializer(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                encodeDefaults = true
            })
            install(Postgrest)
            install(Auth) {
                autoSaveToStorage = true
                autoLoadFromStorage = true
                // The default session storage is this app's plaintext SharedPreferences;
                // keep the refresh token in the Keystore-backed encrypted prefs instead
                // (SecurityUtils, same mechanism as the SQLCipher passphrase).
                sessionManager = SettingsSessionManager(SharedPreferencesSettings(securePrefs, commit = true))
            }
            install(Functions)
            install(Realtime)
            install(Storage)
        }
    }

    /**
     * supabase-kt's default session storage is the app's plaintext SharedPreferences
     * ("<pkg>_preferences", key "sb-<url>-session"). Move any session already stored
     * there into the encrypted prefs once, then drop the plaintext copy so the refresh
     * token is not left readable on disk. If the encrypted write fails the plaintext
     * copy is kept: losing the session would sign the user out for nothing.
     */
    private fun migrateLegacySession(context: Context, securePrefs: SharedPreferences) {
        val defaultPrefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        val legacyKey = "sb-${SUPABASE_URL.removeSuffix("/").replace('/', '-').replace('.', '-')}-${SettingsSessionManager.SETTINGS_KEY}"
        val legacySession = defaultPrefs.getString(legacyKey, null) ?: return
        val alreadyStored = securePrefs.getString(SettingsSessionManager.SETTINGS_KEY, null) != null
        if (!alreadyStored && !securePrefs.edit().putString(SettingsSessionManager.SETTINGS_KEY, legacySession).commit()) return
        defaultPrefs.edit().remove(legacyKey).apply()
    }

    @Provides
    @Singleton
    fun provideSupabaseAuth(client: SupabaseClient): Auth = client.auth

    @Provides
    @Singleton
    fun provideCurrentUserProvider(auth: Auth): CurrentUserProvider =
        SessionCurrentUserProvider(auth)

    @Provides
    @Singleton
    fun provideSupabaseFunctions(client: SupabaseClient): Functions = client.functions

    @Provides
    @Singleton
    fun provideSupabasePostgrest(client: SupabaseClient): Postgrest = client.postgrest

    @Provides
    @Singleton
    fun provideSupabaseRealtime(client: SupabaseClient): Realtime = client.realtime

    @Provides
    @Singleton
    fun provideSupabaseStorage(client: SupabaseClient): Storage = client.storage
}
