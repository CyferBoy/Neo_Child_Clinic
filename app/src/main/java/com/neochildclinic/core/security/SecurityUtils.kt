package com.neochildclinic.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.GeneralSecurityException
import java.util.*

object SecurityUtils {

    private const val TAG = "SecurityUtils"
    private const val PREFS_NAME = "secure_prefs"
    private const val DB_PASSPHRASE_KEY = "db_passphrase"

    /**
     * Retrieves the secure random passphrase for the database.
     * If it doesn't exist, it generates it only if the database doesn't exist yet (handled by AppDatabase).
     * Stores it in EncryptedSharedPreferences (backed by Android Keystore).
     */
    @Throws(GeneralSecurityException::class)
    fun getDatabasePassphrase(context: Context): ByteArray {
        Log.d(TAG, "Loading database passphrase...")
        
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPreferences = try {
            createEncryptedPrefs(context, masterKey)
        } catch (e: Exception) {
            // BUG-003: never delete the prefs here. Deleting them destroys the stored
            // passphrase, a fresh one gets minted, the old database then fails to open
            // and the open path wipes it. Retry once; if this still fails, propagate
            // (app shows an error) instead of silently destroying the clinic's data.
            Log.e(TAG, "EncryptedSharedPreferences init failed; retrying once: ${e.message}")
            createEncryptedPrefs(context, masterKey)
        }

        var passphrase = sharedPreferences.getString(DB_PASSPHRASE_KEY, null)
        
        if (passphrase == null) {
            Log.i(TAG, "Generating new database passphrase...")
            passphrase = UUID.randomUUID().toString()
            sharedPreferences.edit().putString(DB_PASSPHRASE_KEY, passphrase).apply()
        }

        return passphrase.toByteArray()
    }

    private fun createEncryptedPrefs(context: Context, masterKey: MasterKey) = 
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    /**
     * The Keystore-backed encrypted prefs (same file as the database passphrase above).
     * Exposed so the Supabase session token can be persisted here instead of in
     * supabase-kt's default plaintext SharedPreferences - see SupabaseModule.
     */
    fun getSecurePrefs(context: Context): SharedPreferences =
        createEncryptedPrefs(
            context,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        )

    // ---- Backup & Restore: automatic-backup password storage ----
    //
    // Manual "Export Backup" / "Backup Now" always prompt the user for a password fresh
    // and never persist it. Automatic Backup is the one flow that must run unattended
    // (a nightly WorkManager job with no one present to type a password), so - and only if
    // the user turns Automatic Backup on - the password they set is stored here, wrapped by
    // the same Android Keystore-backed EncryptedSharedPreferences mechanism already used for
    // the database passphrase above. Turning Automatic Backup back off clears it immediately.
    private const val BACKUP_PREFS_NAME = "backup_secure_prefs"
    private const val BACKUP_PASSWORD_KEY = "backup_password"

    fun saveBackupPassword(context: Context, password: String) {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs = EncryptedSharedPreferences.create(
            context, BACKUP_PREFS_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        prefs.edit().putString(BACKUP_PASSWORD_KEY, password).apply()
    }

    fun getBackupPassword(context: Context): String? {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs = EncryptedSharedPreferences.create(
            context, BACKUP_PREFS_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        return prefs.getString(BACKUP_PASSWORD_KEY, null)
    }

    fun clearBackupPassword(context: Context) {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs = EncryptedSharedPreferences.create(
            context, BACKUP_PREFS_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        prefs.edit().remove(BACKUP_PASSWORD_KEY).apply()
    }
}
