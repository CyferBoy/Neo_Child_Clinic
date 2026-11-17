package com.neochildclinic.core.security

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineAuthManager internal constructor(
    private val prefsProvider: () -> SharedPreferences
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this({ SecurityUtils.getSecurePrefs(context) })

    companion object {
        private const val KEY_OFFLINE_EMAIL = "offline_auth_email"
        private const val KEY_OFFLINE_SALT = "offline_auth_salt"
        private const val KEY_OFFLINE_HASH = "offline_auth_hash"
        private const val KEY_FAILED_ATTEMPTS = "offline_auth_failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "offline_auth_lockout_until"

        private const val SALT_BYTES = 32
        private const val PBKDF2_ITERATIONS = 210_000
        private const val KEY_BITS = 256
        private const val MAX_UNLOCKED_ATTEMPTS = 5
        private const val BASE_LOCKOUT_MS = 30_000L
    }

    private val prefs by lazy { prefsProvider() }

    fun saveCredential(email: String, password: String) {
        val salt = ByteArray(SALT_BYTES).apply { SecureRandom().nextBytes(this) }
        val hash = deriveHash(password.toCharArray(), salt)
        prefs.edit()
            .putString(KEY_OFFLINE_EMAIL, email.trim().lowercase())
            .putString(KEY_OFFLINE_SALT, Base64.getEncoder().encodeToString(salt))
            .putString(KEY_OFFLINE_HASH, Base64.getEncoder().encodeToString(hash))
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
    }

    fun verifyPassword(email: String, password: String): Boolean {
        val now = System.currentTimeMillis()
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        if (now < lockoutUntil) {
            val remainingSec = ((lockoutUntil - now) / 1000).coerceAtLeast(1)
            throw IllegalStateException("Too many failed attempts. Try again in $remainingSec seconds.")
        }

        val storedEmail = prefs.getString(KEY_OFFLINE_EMAIL, null)
        val storedSaltBase64 = prefs.getString(KEY_OFFLINE_SALT, null)
        val storedHashBase64 = prefs.getString(KEY_OFFLINE_HASH, null)

        if (storedEmail == null || storedSaltBase64 == null || storedHashBase64 == null) {
            return false
        }

        if (!storedEmail.equals(email.trim(), ignoreCase = true)) {
            return false
        }

        val salt = Base64.getDecoder().decode(storedSaltBase64)
        val storedHash = Base64.getDecoder().decode(storedHashBase64)
        val computedHash = deriveHash(password.toCharArray(), salt)

        val matches = MessageDigest.isEqual(computedHash, storedHash)
        if (matches) {
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .apply()
            return true
        } else {
            val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts)
            if (attempts >= MAX_UNLOCKED_ATTEMPTS) {
                val exponent = (attempts - MAX_UNLOCKED_ATTEMPTS).coerceAtMost(6)
                val lockoutDuration = BASE_LOCKOUT_MS * (1 shl exponent)
                editor.putLong(KEY_LOCKOUT_UNTIL, now + lockoutDuration)
            }
            editor.apply()
            return false
        }
    }

    fun clearCredential() {
        prefs.edit()
            .remove(KEY_OFFLINE_EMAIL)
            .remove(KEY_OFFLINE_SALT)
            .remove(KEY_OFFLINE_HASH)
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKOUT_UNTIL)
            .apply()
    }

    fun hasEnrolledCredential(email: String): Boolean {
        val storedEmail = prefs.getString(KEY_OFFLINE_EMAIL, null) ?: return false
        return storedEmail.equals(email.trim(), ignoreCase = true) &&
            prefs.contains(KEY_OFFLINE_HASH)
    }

    private fun deriveHash(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
