package com.neochildclinic.core.utils

import android.content.Context
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
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences: ${e.message}. Attempting recovery...")
            deleteCorruptedPrefs(context)
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

    private fun deleteCorruptedPrefs(context: Context) {
        try {
            // Delete the underlying XML file to clear corrupted data
            context.deleteSharedPreferences(PREFS_NAME)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete corrupted prefs: ${e.message}")
        }
    }
}
