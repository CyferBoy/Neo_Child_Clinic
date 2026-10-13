package com.neochildclinic.data.backup

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {

    @Test
    fun `encrypt then decrypt with the correct password returns the original bytes`() {
        val plaintext = "{\"hello\":\"world\"}".toByteArray(Charsets.UTF_8)
        val container = BackupCrypto.encrypt(plaintext, "correct-horse-battery-staple".toCharArray())

        val decrypted = BackupCrypto.decrypt(container, "correct-horse-battery-staple".toCharArray())

        assertArrayEquals(plaintext, decrypted)
    }

    @Test(expected = BackupException.WrongPassword::class)
    fun `decrypting with the wrong password fails safely instead of returning garbage`() {
        val plaintext = "some backup content".toByteArray(Charsets.UTF_8)
        val container = BackupCrypto.encrypt(plaintext, "right-password".toCharArray())

        BackupCrypto.decrypt(container, "wrong-password".toCharArray())
    }

    @Test
    fun `a tampered container is rejected rather than silently returning bad data`() {
        val plaintext = "some backup content".toByteArray(Charsets.UTF_8)
        val container = BackupCrypto.encrypt(plaintext, "a-password".toCharArray())

        // Flip a byte inside the ciphertext (well past the header).
        val tampered = container.copyOf()
        val flipIndex = tampered.size - 5
        tampered[flipIndex] = (tampered[flipIndex].toInt() xor 0xFF).toByte()

        // AES-GCM cannot distinguish a wrong password from tampered ciphertext - both fail
        // the auth tag - so decryption must fail closed with a safe BackupException and never
        // partially apply data. The WrongPassword/Corrupted split is informational only;
        // tampering that survives GCM is additionally caught by the envelope checksum in
        // BackupSerializer (reported as Corrupted at the restore layer).
        val thrown = assertThrows(BackupException::class.java) {
            BackupCrypto.decrypt(tampered, "a-password".toCharArray())
        }
        assertTrue(
            "Expected a password-or-integrity failure, got ${thrown::class.simpleName}",
            thrown is BackupException.WrongPassword || thrown is BackupException.Corrupted
        )
    }

    @Test(expected = BackupException.Corrupted::class)
    fun `garbage input that is not a Neo Child Clinic backup is rejected`() {
        BackupCrypto.decrypt("not a real backup file".toByteArray(Charsets.UTF_8), "anything".toCharArray())
    }

    @Test
    fun `two encryptions of the same plaintext produce different ciphertext (random salt and iv)`() {
        val plaintext = "same content every time".toByteArray(Charsets.UTF_8)
        val a = BackupCrypto.encrypt(plaintext, "pw".toCharArray())
        val b = BackupCrypto.encrypt(plaintext, "pw".toCharArray())

        assertNotEquals(a.toList(), b.toList())
        // But both still decrypt correctly with the same password.
        assertArrayEquals(plaintext, BackupCrypto.decrypt(a, "pw".toCharArray()))
        assertArrayEquals(plaintext, BackupCrypto.decrypt(b, "pw".toCharArray()))
    }

    @Test
    fun `sha256Hex is deterministic for the same input`() {
        val bytes = "checksum me".toByteArray(Charsets.UTF_8)
        assertTrue(BackupCrypto.sha256Hex(bytes) == BackupCrypto.sha256Hex(bytes))
    }

    @Test
    fun `a container whose header demands an impossible iteration count is rejected immediately`() {
        val container = BackupCrypto.encrypt("payload".toByteArray(Charsets.UTF_8), "a-password".toCharArray())

        for (absurd in intArrayOf(1, Int.MAX_VALUE)) {
            // Patch the iteration count in the container header (offset = magic 4 + version 1
            // + saltLen 1 + salt 16 + ivLen 1 + iv 12). A crafted file could otherwise
            // demand billions of PBKDF2 iterations (app looks frozen) or one (no work
            // factor); either way the file is not a legitimate backup and must be
            // rejected before key derivation starts.
            val crafted = container.copyOf()
            ByteBuffer.wrap(crafted).order(ByteOrder.BIG_ENDIAN).putInt(ITERATIONS_OFFSET, absurd)

            val thrown = assertThrows(BackupException::class.java) {
                BackupCrypto.decrypt(crafted, "a-password".toCharArray())
            }
            assertTrue(
                "Expected Corrupted for $absurd iterations, got ${thrown::class.simpleName}",
                thrown is BackupException.Corrupted
            )
        }
    }

    companion object {
        // 4 (magic) + 1 (format version) + 1 (salt length) + 16 (salt) + 1 (iv length) + 12 (iv)
        private const val ITERATIONS_OFFSET = 35
    }
}
