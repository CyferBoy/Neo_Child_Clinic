package com.neochildclinic.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
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

    @Test(expected = BackupException.Corrupted::class)
    fun `a tampered container fails integrity checking rather than silently returning bad data`() {
        val plaintext = "some backup content".toByteArray(Charsets.UTF_8)
        val container = BackupCrypto.encrypt(plaintext, "a-password".toCharArray())

        // Flip a byte inside the ciphertext (well past the header).
        val tampered = container.copyOf()
        val flipIndex = tampered.size - 5
        tampered[flipIndex] = (tampered[flipIndex].toInt() xor 0xFF).toByte()

        BackupCrypto.decrypt(tampered, "a-password".toCharArray())
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
