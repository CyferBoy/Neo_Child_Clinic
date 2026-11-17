package com.neochildclinic.core.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    @Before
    fun reset() {
        BiometricLockManager.clearLockSuppression()
        BiometricLockManager.setProtectionEnabled(false) // clears lock + screen-off state
        BiometricLockManager.setProtectionEnabled(true)
        BiometricLockManager.resetNeedsAuthForTest()
        BiometricLockManager.resetLockStateResolvedForTest()
    }

    @Test
    fun screenOffArmsPersistedNeedsAuth() {
        BiometricLockManager.onScreenOff()
        assertTrue(BiometricLockManager.needsAuth.value)
    }

    @Test
    fun screenOffImmediatelyLocksAppBeforeResume() {
        BiometricLockManager.onScreenOff()
        assertTrue(BiometricLockManager.isAppLocked.value)
    }

    @Test
    fun screenOffIgnoredWhileProtectionDisabled() {
        BiometricLockManager.setProtectionEnabled(false)
        BiometricLockManager.onScreenOff()
        assertFalse(BiometricLockManager.needsAuth.value)
    }

    @Test
    fun unlockClearsPersistedNeedsAuth() {
        BiometricLockManager.onScreenOff()
        assertTrue(BiometricLockManager.needsAuth.value)
        BiometricLockManager.unlockAfterKeystoreVerification()
        assertFalse(BiometricLockManager.needsAuth.value)
        assertFalse(BiometricLockManager.isAppLocked.value)
    }

    @Test
    fun lockArmsPersistedNeedsAuth() {
        BiometricLockManager.lock()
        assertTrue(BiometricLockManager.needsAuth.value)
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

    @Test
    fun coldStartDropFirstIgnoresInitialEmission() = runBlocking {
        val collected = mutableListOf<Boolean>()
        val job = launch(Dispatchers.Unconfined) {
            BiometricLockManager.needsAuth.drop(1).collect { collected.add(it) }
        }
        // Initial state (false) dropped
        assertTrue(collected.isEmpty())

        // Subsequent lock event emitted
        BiometricLockManager.lock()
        assertEquals(listOf(true), collected)

        // Unlock event emitted
        BiometricLockManager.unlockAfterKeystoreVerification()
        assertEquals(listOf(true, false), collected)

        job.cancel()
    }

    @Test
    fun isLockedReturnsFalseWhenProtectionDisabled() {
        BiometricLockManager.setProtectionEnabled(false)
        assertFalse(BiometricLockManager.isLocked(null))
    }

    @Test
    fun isLockedReturnsTrueWhenLockStateNotResolved() {
        BiometricLockManager.setProtectionEnabled(true)
        BiometricLockManager.resetLockStateResolvedForTest()
        assertTrue(BiometricLockManager.isLocked(null))
    }

    @Test
    fun isLockedReturnsFalseWhenResolvedAndUnlocked() {
        BiometricLockManager.setProtectionEnabled(true)
        BiometricLockManager.markLockStateResolved()
        BiometricLockManager.unlockAfterKeystoreVerification()
        assertFalse(BiometricLockManager.isLocked(null))
    }

    @Test
    fun isLockedReturnsTrueWhenAppLocked() {
        BiometricLockManager.setProtectionEnabled(true)
        BiometricLockManager.markLockStateResolved()
        BiometricLockManager.lock()
        assertTrue(BiometricLockManager.isLocked(null))
    }

    @Test
    fun maskPatientNameRedactsCorrectly() {
        assertEquals("Patient [Locked]", com.neochildclinic.feature.widget.presentation.maskPatientName(""))
        assertEquals("Patient [Locked]", com.neochildclinic.feature.widget.presentation.maskPatientName("   "))
        assertEquals("Patient [Locked]", com.neochildclinic.feature.widget.presentation.maskPatientName("Unknown"))
        assertEquals("Patient [Locked]", com.neochildclinic.feature.widget.presentation.maskPatientName("unknown"))
        assertEquals("A*** S***", com.neochildclinic.feature.widget.presentation.maskPatientName("Aarav Sharma"))
        assertEquals("J***", com.neochildclinic.feature.widget.presentation.maskPatientName("John"))
        assertEquals("R*** K*** D***", com.neochildclinic.feature.widget.presentation.maskPatientName("rahul kumar das"))
    }
}
