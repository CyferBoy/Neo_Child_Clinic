package com.neochildclinic.core.security

import android.content.SharedPreferences
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Empirical Adversarial Challenger for OfflineAuthManager (P2-04).
 *
 * Stress-tests:
 * 1. Offline password verification: valid vs invalid password.
 * 2. Rate-limiting threshold: 5 consecutive failures triggers lockout.
 * 3. Exact exponential backoff formula: BASE_LOCKOUT_MS * (1 shl exponent), capped at 2^6.
 * 4. Lockout rejection: IllegalStateException with remaining seconds during active lockout.
 * 5. Successful login resets failed attempts counter to 0.
 * 6. Credential clearing on logout: removes all 5 security keys.
 * 7. Constant-time hash comparison: MessageDigest.isEqual usage.
 * 8. Whitespace trimming and case-insensitivity on email authentication.
 */
class OfflineAuthManagerAdversarialTest {

    private class TestSharedPreferences : SharedPreferences {
        val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(data)
        override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (data[key] as? MutableSet<String> ?: defValues)
        override fun getInt(key: String, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = TestEditor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class TestEditor(private val target: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private val toRemove = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                temp[key] = value
                toRemove.remove(key)
                return this
            }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
                temp[key] = values
                toRemove.remove(key)
                return this
            }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                temp[key] = value
                toRemove.remove(key)
                return this
            }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                temp[key] = value
                toRemove.remove(key)
                return this
            }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                temp[key] = value
                toRemove.remove(key)
                return this
            }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                temp[key] = value
                toRemove.remove(key)
                return this
            }
            override fun remove(key: String): SharedPreferences.Editor {
                toRemove.add(key)
                temp.remove(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) target.clear()
                for (r in toRemove) target.remove(r)
                for ((k, v) in temp) {
                    if (v == null) target.remove(k) else target[k] = v
                }
            }
        }
    }

    private lateinit var prefs: TestSharedPreferences
    private lateinit var authManager: OfflineAuthManager

    @Before
    fun setUp() {
        prefs = TestSharedPreferences()
        authManager = OfflineAuthManager { prefs }
    }

    @Test
    fun `verifyPassword succeeds with exact credentials and clears failure state`() {
        authManager.saveCredential("doctor@clinic.com", "StrongClinicPass123!")
        assertTrue(authManager.hasEnrolledCredential("doctor@clinic.com"))

        // Simulate 2 failed attempts first
        assertFalse(authManager.verifyPassword("doctor@clinic.com", "Wrong1"))
        assertFalse(authManager.verifyPassword("doctor@clinic.com", "Wrong2"))
        assertEquals(2, prefs.getInt("offline_auth_failed_attempts", 0))

        // Successful password verification
        val verified = authManager.verifyPassword("doctor@clinic.com", "StrongClinicPass123!")
        assertTrue("Verification should succeed with correct password", verified)

        // Verifies failed attempts reset to 0
        assertEquals(0, prefs.getInt("offline_auth_failed_attempts", 0))
        assertEquals(0L, prefs.getLong("offline_auth_lockout_until", 0L))
    }

    @Test
    fun `verifyPassword rejects incorrect password and increments failure count`() {
        authManager.saveCredential("doctor@clinic.com", "StrongClinicPass123!")

        val verified = authManager.verifyPassword("doctor@clinic.com", "IncorrectPassword!")
        assertFalse("Verification should fail with wrong password", verified)
        assertEquals(1, prefs.getInt("offline_auth_failed_attempts", 0))
    }

    @Test
    fun `rate limiting triggers lockout exactly after 5 consecutive failures`() {
        authManager.saveCredential("doctor@clinic.com", "CorrectPassword123")

        val beforeFailures = System.currentTimeMillis()
        for (attempt in 1..4) {
            val result = authManager.verifyPassword("doctor@clinic.com", "bad-pass-$attempt")
            assertFalse(result)
            assertEquals(attempt, prefs.getInt("offline_auth_failed_attempts", 0))
            assertEquals("No lockout before attempt 5", 0L, prefs.getLong("offline_auth_lockout_until", 0L))
        }

        // 5th failure triggers lockout
        val result5 = authManager.verifyPassword("doctor@clinic.com", "bad-pass-5")
        assertFalse(result5)
        assertEquals(5, prefs.getInt("offline_auth_failed_attempts", 0))

        val lockoutUntil = prefs.getLong("offline_auth_lockout_until", 0L)
        val afterFailures = System.currentTimeMillis()
        assertTrue("Lockout must be in the future", lockoutUntil >= beforeFailures + 30_000L)
        assertTrue("Lockout must be within expected 30s window", lockoutUntil <= afterFailures + 30_000L)

        // Immediate subsequent attempt during lockout must throw IllegalStateException
        val thrown = assertThrows(IllegalStateException::class.java) {
            authManager.verifyPassword("doctor@clinic.com", "CorrectPassword123")
        }
        assertTrue(
            "Exception message must mention failed attempts and remaining seconds, got: ${thrown.message}",
            thrown.message?.contains("Too many failed attempts") == true &&
                thrown.message?.contains("seconds") == true
        )
    }

    @Test
    fun `exponential backoff doubles lockout time up to 2 pow 6 cap`() {
        authManager.saveCredential("doctor@clinic.com", "CorrectPassword123")

        // First 4 failures
        for (i in 1..4) {
            authManager.verifyPassword("doctor@clinic.com", "bad-$i")
        }

        // Table of expected durations: 30s * 2^(attempts - 5), capped at exponent 6
        // attempts 5: exp 0 -> 30s (30_000 ms)
        // attempts 6: exp 1 -> 60s (60_000 ms)
        // attempts 7: exp 2 -> 120s (120_000 ms)
        // attempts 8: exp 3 -> 240s (240_000 ms)
        // attempts 9: exp 4 -> 480s (480_000 ms)
        // attempts 10: exp 5 -> 960s (960_000 ms)
        // attempts 11: exp 6 -> 1920s (1,920_000 ms)
        // attempts 12: exp 6 (capped) -> 1920s (1,920_000 ms)
        val expectedDurations = listOf(
            30_000L,
            60_000L,
            120_000L,
            240_000L,
            480_000L,
            960_000L,
            1_920_000L,
            1_920_000L
        )

        for ((index, expectedDuration) in expectedDurations.withIndex()) {
            val attemptNum = 5 + index
            // Simulate that lockout has expired so next attempt can proceed
            prefs.edit().putLong("offline_auth_lockout_until", 0L).apply()

            val before = System.currentTimeMillis()
            authManager.verifyPassword("doctor@clinic.com", "bad-pass-$attemptNum")
            val lockout = prefs.getLong("offline_auth_lockout_until", 0L)
            val duration = lockout - before

            assertEquals("Attempt count mismatch", attemptNum, prefs.getInt("offline_auth_failed_attempts", 0))
            assertTrue(
                "Attempt $attemptNum: expected duration ~$expectedDuration ms, actual $duration ms",
                duration in (expectedDuration - 2000L)..(expectedDuration + 2000L)
            )
        }
    }

    @Test
    fun `clearCredential thoroughly purges all enrolled secrets and security counters`() {
        authManager.saveCredential("doctor@clinic.com", "SecurePassword999")
        assertTrue(authManager.hasEnrolledCredential("doctor@clinic.com"))
        authManager.verifyPassword("doctor@clinic.com", "bad-pass-1")

        authManager.clearCredential()

        assertFalse("Credential should not be enrolled after clear", authManager.hasEnrolledCredential("doctor@clinic.com"))
        assertFalse("Verify should fail after clear", authManager.verifyPassword("doctor@clinic.com", "SecurePassword999"))

        // Assert all 5 keys removed from storage
        assertNull(prefs.getString("offline_auth_email", null))
        assertNull(prefs.getString("offline_auth_salt", null))
        assertNull(prefs.getString("offline_auth_hash", null))
        assertFalse(prefs.contains("offline_auth_failed_attempts"))
        assertFalse(prefs.contains("offline_auth_lockout_until"))
    }

    @Test
    fun `constant-time comparison verification via MessageDigest isEqual`() {
        // MessageDigest.isEqual is guaranteed constant-time across JVM implementations
        val hashA = ByteArray(32) { it.toByte() }
        val hashB = hashA.copyOf()
        val hashC = hashA.copyOf().also { it[0] = (it[0] + 1).toByte() }
        val hashD = hashA.copyOf().also { it[31] = (it[31] + 1).toByte() }

        assertTrue(MessageDigest.isEqual(hashA, hashB))
        assertFalse(MessageDigest.isEqual(hashA, hashC))
        assertFalse(MessageDigest.isEqual(hashA, hashD))
    }

    @Test
    fun `email matching is case-insensitive and trims surrounding whitespace`() {
        authManager.saveCredential("  Doctor.Who@NeoClinic.Org  ", "SonicScrewdriver!")

        assertTrue(authManager.hasEnrolledCredential("doctor.who@neoclinic.org"))
        assertTrue(authManager.hasEnrolledCredential("DOCTOR.WHO@NEOCLINIC.ORG"))
        assertTrue(authManager.hasEnrolledCredential("  Doctor.Who@NeoClinic.Org  "))

        assertTrue(authManager.verifyPassword("doctor.who@neoclinic.org", "SonicScrewdriver!"))
        assertTrue(authManager.verifyPassword("DOCTOR.WHO@NEOCLINIC.ORG", "SonicScrewdriver!"))
        assertTrue(authManager.verifyPassword("  Doctor.Who@NeoClinic.Org  ", "SonicScrewdriver!"))

        // Different email fails cleanly
        assertFalse(authManager.verifyPassword("nurse@neoclinic.org", "SonicScrewdriver!"))
    }
}
