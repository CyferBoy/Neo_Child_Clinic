package com.neochildclinic.core.security

import kotlinx.coroutines.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Concurrency and thread-safety stress test for [BiometricLockManager.needsAuth] StateFlow.
 * Verifies that under high-frequency interleaved calls from multiple concurrent threads,
 * the StateFlow does not deadlock, throw exceptions, or enter an inconsistent state.
 */
class BiometricLockManagerConcurrencyTest {

    @Before
    fun setUp() {
        BiometricLockManager.clearLockSuppression()
        BiometricLockManager.setProtectionEnabled(false)
        BiometricLockManager.setProtectionEnabled(true)
        BiometricLockManager.resetNeedsAuthForTest()
        BiometricLockManager.resetLockStateResolvedForTest()
    }

    @Test
    fun concurrentLockAndUnlockStressTest() = runBlocking {
        val threadCount = 30
        val iterationsPerThread = 100
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val exceptions = AtomicInteger(0)

        // Multiple concurrent collectors to verify StateFlow delivery
        val collectedCount = AtomicInteger(0)
        val collectorJob = launch(Dispatchers.Default) {
            BiometricLockManager.needsAuth.collect {
                collectedCount.incrementAndGet()
            }
        }

        val threads = (1..threadCount).map { i ->
            Thread {
                try {
                    startLatch.await()
                    for (j in 1..iterationsPerThread) {
                        when ((i + j) % 3) {
                            0 -> BiometricLockManager.onScreenOff()
                            1 -> BiometricLockManager.lock()
                            2 -> BiometricLockManager.unlockAfterKeystoreVerification()
                        }
                    }
                } catch (e: Throwable) {
                    exceptions.incrementAndGet()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        startLatch.countDown()

        val completed = doneLatch.await(10, TimeUnit.SECONDS)
        assertTrue("All concurrent threads must finish within timeout", completed)
        org.junit.Assert.assertEquals("No exceptions must occur during concurrent mutations", 0, exceptions.get())
        assertTrue("Collectors must observe transitions", collectedCount.get() > 0)

        // Settling phase: ensure deterministic final state
        BiometricLockManager.unlockAfterKeystoreVerification()
        assertFalse("needsAuth must be false after explicit unlock", BiometricLockManager.needsAuth.value)
        assertFalse("isAppLocked must be false after explicit unlock", BiometricLockManager.isAppLocked.value)

        BiometricLockManager.lock()
        assertTrue("needsAuth must be true after explicit lock", BiometricLockManager.needsAuth.value)
        assertTrue("isAppLocked must be true after explicit lock", BiometricLockManager.isAppLocked.value)

        collectorJob.cancel()
    }
}
