package com.neochildclinic.core.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Centralized biometric authentication backed by Android Keystore.
 * The biometric callback alone is never trusted. A Keystore-protected
 * ciphertext must be successfully decrypted before a protected action is allowed.
 */
object BiometricAuthenticator {
    private const val TAG = "BIOMETRIC"
    private const val KEY_ALIAS = "neochild_biometric_key_v3"
    private const val PREFS = "biometric_protected_state"
    private const val IV_KEY = "iv"
    private const val CIPHERTEXT_KEY = "ciphertext"
    private const val SECRET_HASH_KEY = "secret_hash"
    private const val SECRET_SIZE = 32

    private val protectedSecretLabel = "Neo Child Clinic biometric protected secret v3"

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onVerified: () -> Unit
    ) {
        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

        when (BiometricManager.from(activity).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                val cipher = try {
                    createCipherForAuthentication(activity)
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to initialize secure authentication", e)
                    Toast.makeText(activity, "Unable to initialize secure authentication.", Toast.LENGTH_SHORT).show()
                    return
                }

                val prompt = BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            try {
                                val authenticatedCipher = result.cryptoObject?.cipher
                                    ?: throw IllegalStateException("Missing authenticated cipher")
                                verifyProtectedSecret(activity, authenticatedCipher)
                                onVerified()
                            } catch (e: Exception) {
                                Log.e(TAG, "Keystore verification failed", e)
                                BiometricLockManager.lock()
                                Toast.makeText(activity, "Authentication failed: secure verification error.", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                            ) {
                                Toast.makeText(activity, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(authenticators)
                    .build()

                prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Toast.makeText(activity, "Set up fingerprint, face unlock, or a device PIN/password to continue.", Toast.LENGTH_LONG).show()
            }

            else -> {
                Toast.makeText(activity, "Secure authentication is unavailable on this device.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
            @Suppress("DEPRECATION")
            builder.setInvalidatedByBiometricEnrollment(true)
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    private fun createCipherForAuthentication(context: Context): Cipher {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val iv = prefs.getString(IV_KEY, null)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        if (iv == null) {
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        } else {
            val ivBytes = Base64.decode(iv, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, ivBytes))
        }
        return cipher
    }

    private fun verifyProtectedSecret(context: Context, authenticatedCipher: Cipher) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existingHash = prefs.getString(SECRET_HASH_KEY, null)
        val existingCiphertext = prefs.getString(CIPHERTEXT_KEY, null)

        if (existingHash == null || existingCiphertext == null) {
            // First successful authentication establishes a random secret that is
            // thereafter stored only as Keystore-encrypted ciphertext plus a hash.
            val secret = ByteArray(SECRET_SIZE).also { SecureRandom().nextBytes(it) }
            val ciphertext = authenticatedCipher.doFinal(secret)
            val iv = authenticatedCipher.iv
            val hash = MessageDigest.getInstance("SHA-256").digest(secret)

            prefs.edit()
                .putString(IV_KEY, Base64.encodeToString(iv, Base64.NO_WRAP))
                .putString(CIPHERTEXT_KEY, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .putString(SECRET_HASH_KEY, Base64.encodeToString(hash, Base64.NO_WRAP))
                .putString("label", protectedSecretLabel)
                .apply()
            return
        }

        val plaintext = authenticatedCipher.doFinal(
            Base64.decode(existingCiphertext, Base64.NO_WRAP)
        )
        val actualHash = MessageDigest.getInstance("SHA-256").digest(plaintext)
        val expectedHash = Base64.decode(existingHash, Base64.NO_WRAP)
        if (!MessageDigest.isEqual(actualHash, expectedHash)) {
            throw SecurityException("Protected biometric secret verification failed")
        }
    }
}
