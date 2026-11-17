package com.neochildclinic.data.backup

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Empirical Adversarial Challenger for BackupCrypto (P2-05).
 *
 * Verifies that:
 * 1. Every single byte in the 39-byte header is authenticated.
 * 2. Any 1-byte mutation at ANY offset in [0..38] causes immediate rejection.
 * 3. Salt and IV byte corruptions specifically fail with AEADBadTagException (wrapped in BackupException.WrongPassword).
 * 4. Header tampering cannot be bypassed by any single-byte change.
 */
class BackupCryptoAdversarialTest {

    private val testPassword = "AdversarialMasterKey#2026".toCharArray()
    private val plaintext = "{\"patient_id\":\"p-12345\",\"phi\":\"Sensitive Medical Records\"}".toByteArray(Charsets.UTF_8)

    @Test
    fun `tampering with ANY single byte in header (offsets 0 to 38) fails safely`() {
        val container = BackupCrypto.encrypt(plaintext, testPassword)
        val headerLength = 39 // 4 magic + 1 version + 1 saltLen + 16 salt + 1 ivLen + 12 iv + 4 iterations

        // Verify valid container decrypts cleanly
        val validDecrypted = BackupCrypto.decrypt(container, testPassword)
        assertEquals(String(plaintext, Charsets.UTF_8), String(validDecrypted, Charsets.UTF_8))

        var totalOffsetsTested = 0
        var totalCorruptedRejections = 0
        var totalUnsupportedVersionRejections = 0
        var totalWrongPasswordAeadRejections = 0

        for (offset in 0 until headerLength) {
            totalOffsetsTested++
            // Flip least-significant bit
            val tampered = container.copyOf()
            tampered[offset] = (tampered[offset].toInt() xor 0x01).toByte()

            val thrown = assertThrows("Offset $offset failed to throw on 1-bit corruption", BackupException::class.java) {
                BackupCrypto.decrypt(tampered, testPassword)
            }

            when (thrown) {
                is BackupException.Corrupted -> totalCorruptedRejections++
                is BackupException.UnsupportedVersion -> totalUnsupportedVersionRejections++
                is BackupException.WrongPassword -> {
                    totalWrongPasswordAeadRejections++
                    assertTrue(
                        "Expected cause to be AEADBadTagException at offset $offset, got ${thrown.cause?.javaClass?.simpleName}",
                        thrown.cause is AEADBadTagException
                    )
                }
                else -> org.junit.Assert.fail("Unexpected exception type: ${thrown::class.java.simpleName} at offset $offset")
            }
        }

        assertEquals("All 39 header offsets must be tested", 39, totalOffsetsTested)
        // Assert distribution of safe rejections:
        // Magic (0..3) -> Corrupted
        // Version (4) -> UnsupportedVersion (1 xor 1 = 0 != 1)
        // Salt length (5) -> Corrupted / WrongPassword
        // Salt bytes (6..21) -> WrongPassword wrapping AEADBadTagException
        // IV length (22) -> Corrupted
        // IV bytes (23..34) -> WrongPassword wrapping AEADBadTagException
        // Iterations (35..38) -> WrongPassword wrapping AEADBadTagException or Corrupted
        assertTrue("Magic bytes must be caught by Corrupted", totalCorruptedRejections >= 4)
        assertTrue("Version must be caught by UnsupportedVersion", totalUnsupportedVersionRejections >= 1)
        assertTrue("Salt/IV tampering must be caught by AEADBadTagException", totalWrongPasswordAeadRejections >= 28)
    }

    @Test
    fun `tampering with magic byte 0 fails with BackupException Corrupted`() {
        val container = BackupCrypto.encrypt(plaintext, testPassword)
        val tampered = container.copyOf()
        tampered[0] = 'X'.code.toByte()

        val thrown = assertThrows(BackupException.Corrupted::class.java) {
            BackupCrypto.decrypt(tampered, testPassword)
        }
        assertTrue(thrown.message?.contains("Bad magic header") == true)
    }

    @Test
    fun `tampering with version byte 4 fails with BackupException UnsupportedVersion`() {
        val container = BackupCrypto.encrypt(plaintext, testPassword)
        val tampered = container.copyOf()
        tampered[4] = 2.toByte()

        val thrown = assertThrows(BackupException.UnsupportedVersion::class.java) {
            BackupCrypto.decrypt(tampered, testPassword)
        }
        assertTrue(thrown.logDetail.contains("backupVersion=2"))
    }

    @Test
    fun `tampering with salt length byte 5 causes immediate rejection`() {
        val container = BackupCrypto.encrypt(plaintext, testPassword)
        val tampered = container.copyOf()
        tampered[5] = 32.toByte() // claims salt is 32 bytes instead of 16

        val thrown = assertThrows(BackupException::class.java) {
            BackupCrypto.decrypt(tampered, testPassword)
        }
        assertTrue(
            "Expected Corrupted or WrongPassword, got ${thrown::class.simpleName}",
            thrown is BackupException.Corrupted || thrown is BackupException.WrongPassword
        )
    }

    @Test
    fun `tampering with salt bytes (offsets 6 to 21) causes AEADBadTagException`() {
        val container = BackupCrypto.encrypt(plaintext, testPassword)

        for (offset in 6..21) {
            val tampered = container.copyOf()
            tampered[offset] = (tampered[offset].toInt() xor 0xAA).toByte()

            val thrown = assertThrows(BackupException.WrongPassword::class.java) {
                BackupCrypto.decrypt(tampered, testPassword)
            }
            assertNotNull("Cause must be present", thrown.cause)
            assertTrue(
                "Cause must be AEADBadTagException for salt offset $offset, got ${thrown.cause?.javaClass?.name}",
                thrown.cause is AEADBadTagException
            )
        }
    }

    @Test
    fun `tampering with IV length byte 22 causes immediate rejection`() {
        val container = BackupCrypto.encrypt(plaintext, testPassword)
        val tampered = container.copyOf()
        tampered[22] = 16.toByte()

        val thrown = assertThrows(BackupException::class.java) {
            BackupCrypto.decrypt(tampered, testPassword)
        }
        assertTrue(thrown is BackupException.Corrupted || thrown is BackupException.WrongPassword)
    }

    @Test
    fun `tampering with IV bytes (offsets 23 to 34) causes AEADBadTagException`() {
        val container = BackupCrypto.encrypt(plaintext, testPassword)

        for (offset in 23..34) {
            val tampered = container.copyOf()
            tampered[offset] = (tampered[offset].toInt() xor 0x55).toByte()

            val thrown = assertThrows(BackupException.WrongPassword::class.java) {
                BackupCrypto.decrypt(tampered, testPassword)
            }
            assertNotNull("Cause must be present", thrown.cause)
            assertTrue(
                "Cause must be AEADBadTagException for IV offset $offset, got ${thrown.cause?.javaClass?.name}",
                thrown.cause is AEADBadTagException
            )
        }
    }

    @Test
    fun `tampering with iterations bytes within allowed range causes AEADBadTagException`() {
        val container = BackupCrypto.encrypt(plaintext, testPassword)
        val tampered = container.copyOf()
        // Change iterations from 210_000 to 210_001 (within [10_000, 1_000_000])
        ByteBuffer.wrap(tampered).order(ByteOrder.BIG_ENDIAN).putInt(35, 210_001)

        val thrown = assertThrows(BackupException.WrongPassword::class.java) {
            BackupCrypto.decrypt(tampered, testPassword)
        }
        assertTrue(
            "Cause must be AEADBadTagException when iterations modified, got ${thrown.cause?.javaClass?.name}",
            thrown.cause is AEADBadTagException
        )
    }

    @Test
    fun `tampering with iterations bytes outside bounds causes BackupException Corrupted`() {
        val container = BackupCrypto.encrypt(plaintext, testPassword)

        val badIterationCounts = intArrayOf(0, 9_999, 1_000_001, -1, Int.MAX_VALUE)
        for (badCount in badIterationCounts) {
            val tampered = container.copyOf()
            ByteBuffer.wrap(tampered).order(ByteOrder.BIG_ENDIAN).putInt(35, badCount)

            val thrown = assertThrows(BackupException.Corrupted::class.java) {
                BackupCrypto.decrypt(tampered, testPassword)
            }
            assertTrue(thrown.message?.contains("Unreasonable PBKDF2 iteration count") == true)
        }
    }
}
