package com.neochildclinic.data.backup

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable, password-based encryption for exported/cloud backup files.
 *
 * This is intentionally independent of the local database's SQLCipher passphrase
 * (AppDatabase.kt / SecurityUtils.kt): that passphrase lives in Android Keystore-backed
 * EncryptedSharedPreferences and is *not* meant to ever leave the device, so it cannot be
 * the backup's key - a backup must be restorable on a different device/install. Instead the
 * user supplies a password at export time and must supply the same password at restore
 * time; Neo Child Clinic never stores it anywhere. If the password is lost, the backup
 * cannot be recovered - this is stated in the UI (BackupSettingsScreen) and in
 * docs/BACKUP_RESTORE.md.
 *
 * Container format (all integers big-endian):
 *   4 bytes  magic "NCCB"
 *   1 byte   container format version (currently 1)
 *   1 byte   salt length (SALT_LEN)
 *   N bytes  salt
 *   1 byte   iv length (IV_LEN)
 *   N bytes  iv
 *   4 bytes  PBKDF2 iteration count
 *   N bytes  AES-256-GCM ciphertext (includes the 16-byte auth tag, as produced by the JCE)
 *
 * A wrong password produces a key that fails GCM's own authentication tag check
 * (AEADBadTagException) - decryption never partially applies, and the caller never touches
 * the live database until decryption + checksum verification both succeed, so a wrong
 * password is guaranteed to fail safely without touching existing data.
 */
object BackupCrypto {

    private const val MAGIC = "NCCB"
    private const val CONTAINER_FORMAT_VERSION: Byte = 1
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 210_000
    private const val KEY_BITS = 256

    private val secureRandom = SecureRandom()

    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_LEN).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { secureRandom.nextBytes(it) }
        val key = deriveKey(password, salt, PBKDF2_ITERATIONS)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        val header = ByteBuffer.allocate(4 + 1 + 1 + SALT_LEN + 1 + IV_LEN + 4)
            .order(ByteOrder.BIG_ENDIAN)
            .put(MAGIC.toByteArray(Charsets.US_ASCII))
            .put(CONTAINER_FORMAT_VERSION)
            .put(SALT_LEN.toByte()).put(salt)
            .put(IV_LEN.toByte()).put(iv)
            .putInt(PBKDF2_ITERATIONS)
            .array()

        return header + ciphertext
    }

    fun decrypt(container: ByteArray, password: CharArray): ByteArray {
        if (container.size < 4 + 1 + 1 + SALT_LEN + 1 + IV_LEN + 4) {
            throw BackupException.Corrupted("Container too small: ${container.size} bytes")
        }
        val buffer = ByteBuffer.wrap(container).order(ByteOrder.BIG_ENDIAN)

        val magicBytes = ByteArray(4).also { buffer.get(it) }
        if (String(magicBytes, Charsets.US_ASCII) != MAGIC) {
            throw BackupException.Corrupted("Bad magic header - not a Neo Child Clinic backup file")
        }
        val formatVersion = buffer.get()
        if (formatVersion != CONTAINER_FORMAT_VERSION) {
            throw BackupException.UnsupportedVersion(formatVersion.toInt())
        }
        val saltLen = buffer.get().toInt().let { if (it < 0) it + 256 else it }
        val salt = ByteArray(saltLen).also { buffer.get(it) }
        val ivLen = buffer.get().toInt().let { if (it < 0) it + 256 else it }
        val iv = ByteArray(ivLen).also { buffer.get(it) }
        val iterations = buffer.int
        val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }

        val key = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            return cipher.doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            // Either the wrong password, or the file was tampered with/corrupted in transit -
            // GCM cannot distinguish the two, so we report it as a password/integrity failure
            // rather than guessing which.
            throw BackupException.WrongPassword(cause = e)
        } catch (e: Exception) {
            throw BackupException.Corrupted("Decryption failed: ${e.javaClass.simpleName}", e)
        }
    }

    /** SHA-256 of [bytes] as a lowercase hex string, used as the envelope checksum. */
    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        val keyBytes = try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return SecretKeySpec(keyBytes, "AES")
    }
}
