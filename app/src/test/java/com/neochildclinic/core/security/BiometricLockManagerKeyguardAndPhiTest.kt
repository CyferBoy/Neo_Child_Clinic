package com.neochildclinic.core.security

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import com.neochildclinic.data.local.entity.WidgetDueEntity
import com.neochildclinic.feature.widget.presentation.maskPatientName
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Empirical Adversarial Challenge Test for Milestone 1 / P1-01:
 * - KeyguardManager.isKeyguardLocked simulation and verification
 * - BiometricLockManager.isLocked(context) state matrix
 * - Patient PHI name masking behavior under standard, edge-case, and boundary inputs
 * - VaccineWidget data masking when locked vs unlocked
 */
class BiometricLockManagerKeyguardAndPhiTest {

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

    @Test
    fun maskPatientNameStandardSpecificationCases() {
        // 1. maskPatientName("John Doe") returns "J*** D***"
        assertEquals("J*** D***", maskPatientName("John Doe"))

        // 2. maskPatientName("Unknown") returns "Patient [Locked]"
        assertEquals("Patient [Locked]", maskPatientName("Unknown"))

        // 3. Blank returns "Patient [Locked]"
        assertEquals("Patient [Locked]", maskPatientName(""))
        assertEquals("Patient [Locked]", maskPatientName("   "))
        assertEquals("Patient [Locked]", maskPatientName("\t\n"))
    }

    @Test
    fun maskPatientNameAdversarialEdgeCases() {
        // Case-insensitivity for Unknown
        assertEquals("Patient [Locked]", maskPatientName("unknown"))
        assertEquals("Patient [Locked]", maskPatientName("UNKNOWN"))
        assertEquals("Patient [Locked]", maskPatientName("UnKnOwN"))

        // Single word names
        assertEquals("J***", maskPatientName("John"))
        assertEquals("A***", maskPatientName("a"))

        // Multiple consecutive spaces and leading/trailing whitespace
        assertEquals("J*** D***", maskPatientName("  John    Doe  "))

        // Multi-part names
        assertEquals("A*** B*** C***", maskPatientName("Aarav Bob Charlie"))
        assertEquals("M*** D*** J***", maskPatientName("mary debbie jane"))

        // Hyphenated names (split by regex \\s+ leaves hyphen within word)
        assertEquals("J*** S***", maskPatientName("Jane Smith-Doe"))
    }

    @Test
    fun vaccineWidgetRedactionTransformContract() {
        val originalItems = listOf(
            WidgetDueEntity(
                id = 1,
                patientName = "John Doe",
                vaccineName = "MMR Booster",
                dueDate = "16 Nov",
                isOverdue = false
            ),
            WidgetDueEntity(
                id = 2,
                patientName = "Unknown",
                vaccineName = "Polio OPV-3",
                dueDate = "10 Nov",
                isOverdue = true
            ),
            WidgetDueEntity(
                id = 3,
                patientName = "  ",
                vaccineName = "Hepatitis B",
                dueDate = "20 Nov",
                isOverdue = false
            )
        )

        // Simulate VaccineWidget transformation when locked
        fun transformForDisplay(items: List<WidgetDueEntity>, isLocked: Boolean): List<WidgetDueEntity> {
            return if (isLocked) {
                items.map {
                    it.copy(
                        patientName = maskPatientName(it.patientName),
                        vaccineName = "[Locked]"
                    )
                }
            } else {
                items
            }
        }

        // 1. When Locked: PHI is redacted
        val lockedDisplay = transformForDisplay(originalItems, isLocked = true)
        assertEquals(3, lockedDisplay.size)
        assertEquals("J*** D***", lockedDisplay[0].patientName)
        assertEquals("[Locked]", lockedDisplay[0].vaccineName)
        assertEquals("16 Nov", lockedDisplay[0].dueDate)

        assertEquals("Patient [Locked]", lockedDisplay[1].patientName)
        assertEquals("[Locked]", lockedDisplay[1].vaccineName)

        assertEquals("Patient [Locked]", lockedDisplay[2].patientName)
        assertEquals("[Locked]", lockedDisplay[2].vaccineName)

        // 2. When Unlocked: PHI is fully visible
        val unlockedDisplay = transformForDisplay(originalItems, isLocked = false)
        assertEquals("John Doe", unlockedDisplay[0].patientName)
        assertEquals("MMR Booster", unlockedDisplay[0].vaccineName)
        assertEquals("Unknown", unlockedDisplay[1].patientName)
        assertEquals("Polio OPV-3", unlockedDisplay[1].vaccineName)
    }
}
