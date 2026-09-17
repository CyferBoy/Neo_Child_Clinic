package com.neochildclinic.data.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores exactly one "safety snapshot" - the state of the database immediately before the
 * most recent destructive restore - in app-private internal storage, encrypted with an
 * Android Keystore key that never leaves the device and is never used for anything else.
 *
 * This is deliberately separate from [BackupCrypto] (which is password-based and portable):
 * the safety snapshot only ever needs to be readable on *this* device, right after *this*
 * restore, so a device-bound key that requires no password prompt is both simpler and safer
 * here (nothing for the user to forget, no password floating around in memory longer than
 * necessary).
 */
class SafetyBackupStore(private val context: Context) {

    private val file: File
        get() = File(context.filesDir, "backup_safety/safety_snapshot.bin")

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun write(plaintextJson: ByteArray) {
        file.parentFile?.mkdirs()
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintextJson)
        file.writeBytes(iv.size.toByte().let { byteArrayOf(it) } + iv + ciphertext)
    }

    fun read(): ByteArray? {
        if (!file.exists()) return null
        val bytes = file.readBytes()
        if (bytes.isEmpty()) return null
        val ivLen = bytes[0].toInt().let { if (it < 0) it + 256 else it }
        val iv = bytes.copyOfRange(1, 1 + ivLen)
        val ciphertext = bytes.copyOfRange(1 + ivLen, bytes.size)
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    fun exists(): Boolean = file.exists()

    fun clear() {
        if (file.exists()) file.delete()
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "neochildclinic_backup_safety_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
