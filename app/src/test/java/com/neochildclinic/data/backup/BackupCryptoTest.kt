package com.neochildclinic.data.backup

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
}
