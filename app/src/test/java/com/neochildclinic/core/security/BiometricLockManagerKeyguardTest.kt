package com.neochildclinic.core.security

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Empirical Adversarial Challenge Test for Milestone 1 / P1-01:
 * - KeyguardManager.isKeyguardLocked simulation and verification
 * - BiometricLockManager.isLocked(context) state matrix
 */
class BiometricLockManagerKeyguardTest {

    private class KeyguardTestContext(
        private val keyguardManager: KeyguardManager?
    ) : ContextWrapper(null) {
        override fun getSystemService(name: String): Any? {
            if (name == Context.KEYGUARD_SERVICE) {
                return keyguardManager
            }
            return null
        }
        override fun getApplicationContext(): Context = this
    }

    private lateinit var fakeKeyguardManager: android.app.FakeKeyguardManager
    private lateinit var contextWithKeyguard: Context
    private lateinit var contextWithoutKeyguard: Context

    @Before
    fun setUp() {
        fakeKeyguardManager = android.app.FakeKeyguardManager()
        contextWithKeyguard = KeyguardTestContext(fakeKeyguardManager)
        contextWithoutKeyguard = KeyguardTestContext(null)

        BiometricLockManager.clearLockSuppression()
        BiometricLockManager.setProtectionEnabled(true)
        BiometricLockManager.resetNeedsAuthForTest()
        BiometricLockManager.resetLockStateResolvedForTest()
    }

    @After
    fun tearDown() {
        BiometricLockManager.clearLockSuppression()
        BiometricLockManager.setProtectionEnabled(true)
        BiometricLockManager.unlockAfterKeystoreVerification()
        BiometricLockManager.markLockStateResolved()
    }

    @Test
    fun isLockedReturnsTrueWhenKeyguardIsLocked() {
        // Given: Biometric lock is resolved and internally unlocked
        BiometricLockManager.markLockStateResolved()
        BiometricLockManager.unlockAfterKeystoreVerification()
        assertFalse("Internal app lock must be false", BiometricLockManager.isAppLocked.value)
        assertFalse("needsAuth must be false", BiometricLockManager.needsAuth.value)

        // When: Device keyguard is locked
        fakeKeyguardManager.setLocked(true)

        // Then: isLocked(context) must return true due to device keyguard
        assertTrue(
            "BiometricLockManager.isLocked must return true when KeyguardManager.isKeyguardLocked is true",
            BiometricLockManager.isLocked(contextWithKeyguard)
        )
    }

    @Test
    fun isLockedReturnsFalseWhenKeyguardIsUnlockedAndAppIsUnlocked() {
        // Given: Biometric lock is resolved, internally unlocked, keyguard unlocked
        BiometricLockManager.markLockStateResolved()
        BiometricLockManager.unlockAfterKeystoreVerification()
        fakeKeyguardManager.setLocked(false)

        // Then: isLocked(context) must return false
        assertFalse(
            "BiometricLockManager.isLocked must return false when both app and keyguard are unlocked",
            BiometricLockManager.isLocked(contextWithKeyguard)
        )
    }

    @Test
    fun isLockedReturnsFalseWhenProtectionIsDisabledEvenIfKeyguardIsLocked() {
        // Given: Protection disabled by user
        BiometricLockManager.setProtectionEnabled(false)
        fakeKeyguardManager.setLocked(true)

        // Then: isLocked must be false because protection is disabled
        assertFalse(
            "isLocked must return false when protection is disabled regardless of keyguard state",
            BiometricLockManager.isLocked(contextWithKeyguard)
        )
    }

    @Test
    fun isLockedFailsClosedWhenLockStateNotResolvedEvenIfKeyguardUnlocked() {
        // Given: Lock state has NOT been resolved yet (e.g. cold launch)
        BiometricLockManager.resetLockStateResolvedForTest()
        BiometricLockManager.unlockAfterKeystoreVerification()
        fakeKeyguardManager.setLocked(false)

        // Then: isLocked must return true to fail-closed and protect PHI
        assertTrue(
            "isLocked must fail-closed (return true) when lockStateResolved is false",
            BiometricLockManager.isLocked(contextWithKeyguard)
        )
    }

    @Test
    fun isLockedReturnsTrueWhenAppIsLockedEvenIfKeyguardIsUnlocked() {
        // Given: App internally locked (timeout, screen off, or explicit lock)
        BiometricLockManager.markLockStateResolved()
        BiometricLockManager.lock()
        fakeKeyguardManager.setLocked(false)

        // Then: isLocked must return true
        assertTrue(
            "isLocked must return true when app is internally locked",
            BiometricLockManager.isLocked(contextWithKeyguard)
        )
    }

    @Test
    fun isLockedHandlesNullContextGracefully() {
        BiometricLockManager.markLockStateResolved()
        BiometricLockManager.unlockAfterKeystoreVerification()

        // When context is null and app is unlocked, keyguard is not checked -> false
        assertFalse(BiometricLockManager.isLocked(null))

        // When context is null and app is locked -> true
        BiometricLockManager.lock()
        assertTrue(BiometricLockManager.isLocked(null))
    }

    @Test
    fun isLockedHandlesNullKeyguardServiceGracefully() {
        BiometricLockManager.markLockStateResolved()
        BiometricLockManager.unlockAfterKeystoreVerification()

        // When KEYGUARD_SERVICE returns null, keyguardLocked is false
        assertFalse(BiometricLockManager.isLocked(contextWithoutKeyguard))

        BiometricLockManager.lock()
        assertTrue(BiometricLockManager.isLocked(contextWithoutKeyguard))
    }

}
