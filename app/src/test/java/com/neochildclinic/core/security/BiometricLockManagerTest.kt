package com.neochildclinic.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the persisted arm flag ([BiometricLockManager.onNeedsAuthChanged], backed by
 * NotificationSettingsManager's `needs_auth`) and the in-memory fast path it mirrors.
 * The object is a singleton, so [reset] returns it to a known state per test.
 */
class BiometricLockManagerTest {

    private val persisted = mutableListOf<Boolean>()

    @Before
    fun reset() {
        BiometricLockManager.onNeedsAuthChanged = null
        BiometricLockManager.setProtectionEnabled(false) // clears lock + screen-off state
        BiometricLockManager.setProtectionEnabled(true)
        BiometricLockManager.onNeedsAuthChanged = { persisted.add(it) }
        BiometricLockManager.resetLockStateResolvedForTest()
        persisted.clear()
    }

    @Test
    fun screenOffArmsPersistedNeedsAuth() {
        BiometricLockManager.onScreenOff()
        assertEquals(listOf(true), persisted)
    }

    @Test
    fun screenOffIgnoredWhileProtectionDisabled() {
        BiometricLockManager.setProtectionEnabled(false)
        BiometricLockManager.onScreenOff()
        assertTrue(persisted.isEmpty())
    }

    @Test
    fun unlockClearsPersistedNeedsAuth() {
        BiometricLockManager.onScreenOff()
        BiometricLockManager.unlockAfterKeystoreVerification()
        assertEquals(listOf(true, false), persisted)
        assertFalse(BiometricLockManager.isAppLocked.value)
    }

    @Test
    fun lockArmsPersistedNeedsAuth() {
        BiometricLockManager.lock()
        assertEquals(listOf(true), persisted)
        assertTrue(BiometricLockManager.isAppLocked.value)
    }

    @Test
    fun resumeLocksAfterScreenOff() {
        // In-memory fast path: a screen-off within this process must lock on resume
        // even before the persisted flag round-trips through DataStore.
        BiometricLockManager.onScreenOff()
        BiometricLockManager.onAppResume()
        assertTrue(BiometricLockManager.isAppLocked.value)
    }

    @Test
    fun resumeWithoutScreenOffStaysUnlocked() {
        BiometricLockManager.onAppResume()
        assertFalse(BiometricLockManager.isAppLocked.value)
    }

    // ── NCC-012: "unlocked" and "not checked yet" are different answers ───────────────

    @Test
    fun lockStateIsUnknownBeforeAnyoneChecks() {
        // The ambiguity the bug depended on: isAppLocked reads false, but nobody has
        // established that the app is unlocked - the session status takes seconds to
        // resolve and MainActivity renders immediately.
        assertFalse(BiometricLockManager.lockStateResolved.value)
        assertFalse(BiometricLockManager.isAppLocked.value)
    }

    @Test
    fun lockingBeforeResolutionDoesNotResolveTheState() {
        // NeoChildApp re-arms from the persisted needsAuth flag before any UI renders. That
        // makes the app locked but says nothing about whether the app is safe to show - only
        // MainActivity.checkAppLock, after the session resolves, may resolve the state.
        BiometricLockManager.lock()
        assertTrue(BiometricLockManager.isAppLocked.value)
        assertFalse(
            "lock() must not mark the state resolved - no authentication has happened yet",
            BiometricLockManager.lockStateResolved.value,
        )
    }

    @Test
    fun unlockingBeforeResolutionDoesNotResolveTheState() {
        BiometricLockManager.unlockAfterKeystoreVerification()
        assertFalse(BiometricLockManager.isAppLocked.value)
        assertFalse(BiometricLockManager.lockStateResolved.value)
    }

    @Test
    fun resolvingIsIndependentOfTheLockDecision() {
        BiometricLockManager.markLockStateResolved()
        assertTrue(BiometricLockManager.lockStateResolved.value)
        // Resolving says only that we know. It must not itself unlock a locked app.
        assertFalse(BiometricLockManager.isAppLocked.value)
    }
}
