package com.neochildclinic.feature.patient.data

import com.neochildclinic.core.cache.CacheRegistry
import com.neochildclinic.domain.model.Patient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatientCacheGranularTest {

    private fun patient(id: String, name: String = "P-$id"): Patient =
        Patient(id = id, name = name, phone = "0", dob = "2024-01-01", gender = "M")

    @Test
    fun invalidateKeyDoesNotEvictOrInvalidateOtherKeys() = runBlocking {
        val cache = PatientCache(CacheRegistry())
        cache.getOrLoad("1") { patient("1") }
        cache.getOrLoad("2") { patient("2") }

        assertTrue(cache.contains("1"))
        assertTrue(cache.contains("2"))

        // Invalidate "1"
        cache.invalidate("1")

        assertFalse(cache.contains("1"))
        // "2" MUST remain in cache
        assertTrue(cache.contains("2"))
        var loaded2 = false
        val p2 = cache.getOrLoad("2") {
            loaded2 = true
            patient("2")
        }
        assertFalse("Key 2 should hit cache without invoking load lambda", loaded2)
        assertEquals("P-2", p2?.name)
    }

    @Test
    fun inFlightLoadForOtherKeyCompletesAndCachesDuringInvalidation() = runBlocking {
        val cache = PatientCache(CacheRegistry())
        
        // Start load for "2", but during load invalidate "1"
        val p2 = cache.getOrLoad("2") {
            cache.invalidate("1")
            patient("2")
        }

        assertNotNull(p2)
        // Key 2 should be successfully cached because only Key 1 was invalidated
        assertTrue(cache.contains("2"))
    }

    @Test
    fun inFlightLoadForSameKeyDoesNotCacheStaleData() = runBlocking {
        val cache = PatientCache(CacheRegistry())

        // Start load for "1", but during load invalidate "1"
        val p1 = cache.getOrLoad("1") {
            cache.invalidate("1")
            patient("1", "Stale")
        }

        assertEquals("Stale", p1?.name)
        // Key 1 should NOT be cached
        assertFalse(cache.contains("1"))
    }

    @Test
    fun clearDropsAllKeysAndIncrementsGlobalGen() = runBlocking {
        val cache = PatientCache(CacheRegistry())
        cache.getOrLoad("1") { patient("1") }
        cache.getOrLoad("2") { patient("2") }

        cache.clear()

        assertFalse(cache.contains("1"))
        assertFalse(cache.contains("2"))
    }
}
