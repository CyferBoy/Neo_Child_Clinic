package com.neochildclinic.feature.patient.data

import com.neochildclinic.core.cache.CacheRegistry
import com.neochildclinic.domain.model.Patient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatientCacheTest {

    // Stand-in for Room: the source of truth the cache must always defer to.
    private class FakeRoom(val rows: MutableMap<String, Patient> = mutableMapOf()) {
        var reads = 0
        suspend fun load(id: String): Patient? { reads++; return rows[id] }
    }

    private fun patient(id: String, name: String = "P-$id"): Patient =
        Patient(id = id, name = name, phone = "0", dob = "2024-01-01", gender = "M")

    @Test fun cacheMissThenHit() = runBlocking {
        val room = FakeRoom(mutableMapOf("1" to patient("1")))
        val cache = PatientCache(CacheRegistry())

        assertEquals("P-1", cache.getOrLoad("1") { room.load("1") }?.name) // miss -> Room
        assertEquals("P-1", cache.getOrLoad("1") { room.load("1") }?.name) // hit
        assertEquals(1, room.reads)
        assertTrue(cache.contains("1"))
    }

    @Test fun emptyCacheFallsBackToRoom() = runBlocking {
        val room = FakeRoom(mutableMapOf("7" to patient("7")))
        val cache = PatientCache(CacheRegistry())
        cache.clear()
        assertEquals("7", cache.getOrLoad("7") { room.load("7") }?.id)
    }

    @Test fun missingPatientIsNotCached() = runBlocking {
        val room = FakeRoom()
        val cache = PatientCache(CacheRegistry())
        assertNull(cache.getOrLoad("x") { room.load("x") })
        assertFalse(cache.contains("x"))
        room.rows["x"] = patient("x") // appears later in Room
        assertEquals("x", cache.getOrLoad("x") { room.load("x") }?.id)
    }

    @Test fun updateInvalidatesDetail() = runBlocking {
        val room = FakeRoom(mutableMapOf("1" to patient("1", "Old")))
        val cache = PatientCache(CacheRegistry())
        cache.getOrLoad("1") { room.load("1") }

        room.rows["1"] = patient("1", "New") // Room mutation succeeded
        cache.invalidate("1")

        assertEquals("New", cache.getOrLoad("1") { room.load("1") }?.name)
    }

    @Test fun deleteInvalidatesDetail() = runBlocking {
        val room = FakeRoom(mutableMapOf("1" to patient("1")))
        val cache = PatientCache(CacheRegistry())
        cache.getOrLoad("1") { room.load("1") }

        room.rows.remove("1")
        cache.invalidate("1")

        assertNull(cache.getOrLoad("1") { room.load("1") })
    }

    @Test fun bulkWriteClearDropsAllDetails() = runBlocking {
        val room = FakeRoom(mutableMapOf("1" to patient("1"), "2" to patient("2")))
        val cache = PatientCache(CacheRegistry())
        cache.getOrLoad("1") { room.load("1") }
        cache.getOrLoad("2") { room.load("2") }
        cache.clear() // what the Room InvalidationTracker observer calls after sync/restore
        assertFalse(cache.contains("1"))
        assertFalse(cache.contains("2"))
    }

    @Test fun readStartedBeforeInvalidationDoesNotRepopulateStaleData() = runBlocking {
        val cache = PatientCache(CacheRegistry())
        val stale = patient("1", "Stale")
        // The load is "in flight" while a mutation invalidates; its result must not be cached.
        val result = cache.getOrLoad("1") {
            cache.invalidate("1")
            stale
        }
        assertEquals("Stale", result?.name) // caller still gets data
        assertFalse(cache.contains("1"))    // but it was not stored
    }

    @Test fun loadFailureDoesNotPoisonCache() = runBlocking {
        val cache = PatientCache(CacheRegistry())
        try {
            cache.getOrLoad("1") { error("Room down") }
        } catch (_: IllegalStateException) { }
        assertFalse(cache.contains("1"))
    }

    @Test fun logoutClearsCacheSoNextUserCannotSeeUserAData() = runBlocking {
        val registry = CacheRegistry()
        val cache = PatientCache(registry) // registers itself
        val roomUserA = FakeRoom(mutableMapOf("1" to patient("1", "UserA-patient")))
        cache.getOrLoad("1") { roomUserA.load("1") }
        assertTrue(cache.contains("1"))

        registry.clearAll() // AuthViewModel.logout()

        assertFalse(cache.contains("1"))
        val roomUserB = FakeRoom() // User B's Room has no such patient
        assertNull(cache.getOrLoad("1") { roomUserB.load("1") })
    }

    @Test fun registryClearAllSurvivesAFailingCache() {
        val registry = CacheRegistry()
        registry.register(object : com.neochildclinic.core.cache.ClearableCache {
            override fun clear() = throw RuntimeException("boom")
        })
        val cache = PatientCache(registry)
        registry.clearAll() // must not throw, and must still reach later caches
        assertFalse(cache.contains("none"))
    }
}
