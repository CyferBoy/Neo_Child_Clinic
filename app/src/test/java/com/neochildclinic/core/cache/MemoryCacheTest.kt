package com.neochildclinic.core.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MemoryCacheTest {

    private var nowNanos = 0L
    private fun advanceMs(ms: Long) { nowNanos += TimeUnit.MILLISECONDS.toNanos(ms) }
    private fun cache(ttlMs: Long = 1_000, maxSize: Int = 3) =
        MemoryCache<String, String>(ttlMs, maxSize) { nowNanos }

    @Test fun cacheHit() {
        val c = cache()
        c.put("a", "1")
        assertEquals("1", c.get("a"))
        assertTrue(c.contains("a"))
    }

    @Test fun cacheMiss() {
        assertNull(cache().get("missing"))
    }

    @Test fun ttlExpiration() {
        val c = cache(ttlMs = 1_000)
        c.put("a", "1")
        advanceMs(999)
        assertEquals("1", c.get("a"))
        advanceMs(1)
        assertNull(c.get("a"))
        assertEquals(0, c.size())
    }

    @Test fun perEntryTtlOverridesDefault() {
        val c = cache(ttlMs = 10_000)
        c.put("a", "1", ttlMs = 100)
        advanceMs(100)
        assertNull(c.get("a"))
    }

    @Test fun manualInvalidation() {
        val c = cache()
        c.put("a", "1"); c.put("b", "2")
        c.invalidate("a")
        assertNull(c.get("a"))
        assertEquals("2", c.get("b"))
    }

    @Test fun invalidateWhereRemovesMatchingKeys() {
        val c = cache(maxSize = 10)
        c.put("PATIENT:1", "x"); c.put("PATIENT:2", "y"); c.put("OTHER:1", "z")
        c.invalidateWhere { it.startsWith("PATIENT:") }
        assertFalse(c.contains("PATIENT:1"))
        assertFalse(c.contains("PATIENT:2"))
        assertTrue(c.contains("OTHER:1"))
    }

    @Test fun clearRemovesEverything() {
        val c = cache()
        c.put("a", "1"); c.put("b", "2")
        c.clear()
        assertEquals(0, c.size())
        assertNull(c.get("a"))
    }

    @Test fun maxSizeEvictsEarliestExpiryAndStaysBounded() {
        val c = cache(ttlMs = 10_000, maxSize = 3)
        c.put("a", "1"); advanceMs(1)
        c.put("b", "2"); advanceMs(1)
        c.put("c", "3"); advanceMs(1)
        c.put("d", "4") // full -> "a" (earliest expiry) is evicted
        assertEquals(3, c.size())
        assertNull(c.get("a"))
        assertEquals("4", c.get("d"))
    }

    @Test fun overwritingExistingKeyDoesNotEvict() {
        val c = cache(maxSize = 2)
        c.put("a", "1"); c.put("b", "2")
        c.put("a", "1b")
        assertEquals(2, c.size())
        assertEquals("2", c.get("b"))
        assertEquals("1b", c.get("a"))
    }

    @Test fun expiredEntriesAreDroppedBeforeEvictingLiveOnes() {
        val c = cache(ttlMs = 1_000, maxSize = 2)
        c.put("old", "1", ttlMs = 10)
        c.put("live", "2", ttlMs = 10_000)
        advanceMs(50)
        c.put("new", "3")
        assertEquals("2", c.get("live"))
        assertEquals("3", c.get("new"))
    }

    @Test fun concurrentAccessStaysBoundedAndConsistent() {
        val maxSize = 50
        val c = MemoryCache<Int, Int>(60_000, maxSize) // real monotonic clock
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        repeat(threads) { t ->
            pool.execute {
                start.await()
                repeat(2_000) { i ->
                    val k = (t * 2_000 + i) % 500
                    c.put(k, k)
                    c.get(k)?.let { assertEquals(k, it) }
                    if (i % 7 == 0) c.invalidate(k)
                }
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS))
        pool.shutdown()
        assertTrue("size=${c.size()}", c.size() <= maxSize)
    }
}
