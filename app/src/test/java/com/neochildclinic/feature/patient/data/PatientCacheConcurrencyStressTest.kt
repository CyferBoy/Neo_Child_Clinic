package com.neochildclinic.feature.patient.data

import com.neochildclinic.core.cache.CacheRegistry
import com.neochildclinic.core.cache.MemoryCache
import com.neochildclinic.domain.model.Patient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Empirical Adversarial Challenger for PatientCache & MemoryCache Concurrency (P2-03).
 *
 * Verifies:
 * 1. 50 concurrent coroutines reading, updating, and invalidating patient IDs.
 * 2. Zero race conditions, zero ConcurrentModificationExceptions.
 * 3. Granular key invalidation: invalidating patient X does not evict patient Y.
 * 4. High cache hit rate (>= 75%) maintained under concurrent read/invalidate workloads.
 * 5. MemoryCache bounded eviction safety under 50 concurrent threads.
 * 6. In-flight load race safety: stale data rejected on invalidation of same key, while other keys cache normally.
 */
class PatientCacheConcurrencyStressTest {

    private fun createPatient(id: String, version: Int = 1): Patient =
        Patient(id = id, name = "Patient-$id-v$version", phone = "0987654321", dob = "2020-01-01", gender = "F")

    @Test
    fun `50 concurrent coroutines reading and invalidating different patients maintain consistency and high hit rate`() = runBlocking(Dispatchers.Default) {
        val cache = PatientCache(CacheRegistry())
        val roomDb = ConcurrentHashMap<String, Pair<Patient, AtomicInteger>>()
        val totalPatients = 100
        val coroutineCount = 50
        val operationsPerCoroutine = 100

        // Pre-populate Room DB
        for (i in 0 until totalPatients) {
            val id = "p-$i"
            roomDb[id] = Pair(createPatient(id, 1), AtomicInteger(0))
        }

        val cacheHits = AtomicInteger(0)
        val cacheMisses = AtomicInteger(0)
        val invalidations = AtomicInteger(0)

        // Launch 50 concurrent coroutines
        coroutineScope {
            val jobs = (0 until coroutineCount).map { workerId ->
                launch {
                    for (op in 0 until operationsPerCoroutine) {
                        val patientNum = (workerId * 7 + op) % totalPatients
                        val patientId = "p-$patientNum"

                        // Every 10th op, simulate a mutation + invalidation of this specific patient
                        if (op % 10 == 0) {
                            val newVersion = op + 2
                            val updated = createPatient(patientId, newVersion)
                            roomDb[patientId] = Pair(updated, roomDb[patientId]?.second ?: AtomicInteger(0))
                            cache.invalidate(patientId)
                            invalidations.incrementAndGet()
                        } else {
                            // Read path
                            var roomLoaded = false
                            val patient = cache.getOrLoad(patientId) {
                                roomLoaded = true
                                val entry = roomDb[patientId]
                                entry?.second?.incrementAndGet()
                                entry?.first
                            }

                            assertNotNull("Patient must not be null", patient)
                            if (roomLoaded) {
                                cacheMisses.incrementAndGet()
                            } else {
                                cacheHits.incrementAndGet()
                            }
                        }
                    }
                }
            }
            jobs.forEach { it.join() }
        }

        val hits = cacheHits.get()
        val misses = cacheMisses.get()
        val totalReads = hits + misses
        val hitRate = hits.toDouble() / totalReads.toDouble()

        println("PatientCache Concurrency Stress Test:")
        println("  Total reads: $totalReads, Hits: $hits, Misses: $misses")
        println("  Hit rate: ${"%.2f".format(hitRate * 100)}%")
        println("  Invalidations: ${invalidations.get()}")

        assertTrue("Cache hit rate must exceed 75% under concurrent operations", hitRate >= 0.75)
        // Verify final read of all patients is consistent with Room
        for (i in 0 until totalPatients) {
            val id = "p-$i"
            val cachedOrFresh = cache.getOrLoad(id) { roomDb[id]?.first }
            assertEquals(roomDb[id]?.first?.name, cachedOrFresh?.name)
        }
    }

    @Test
    fun `MemoryCache supports 50 concurrent coroutines with eviction and capacity bounds`() = runBlocking(Dispatchers.Default) {
        val maxSize = 50
        val memoryCache = MemoryCache<String, String>(defaultTtlMs = 60_000L, maxSize = maxSize)
        val coroutines = 50
        val opsPerCoroutine = 200

        coroutineScope {
            val jobs = (0 until coroutines).map { coroutineId ->
                launch {
                    for (i in 0 until opsPerCoroutine) {
                        val key = "key-${(coroutineId * 13 + i) % 200}"
                        when (i % 4) {
                            0, 1 -> memoryCache.put(key, "value-$key-$i")
                            2 -> memoryCache.get(key)
                            3 -> memoryCache.remove(key)
                        }
                        assertTrue(
                            "MemoryCache size (${memoryCache.size()}) exceeded maxSize ($maxSize)",
                            memoryCache.size() <= maxSize
                        )
                    }
                }
            }
            jobs.forEach { it.join() }
        }

        assertTrue("MemoryCache size must remain within bounds", memoryCache.size() <= maxSize)
    }

    @Test
    fun `granular invalidation ensures un-invalidated keys maintain 100 percent hit rate`() = runBlocking(Dispatchers.Default) {
        val cache = PatientCache(CacheRegistry())
        val roomLoads = ConcurrentHashMap<String, AtomicInteger>()

        // Load 10 stable patients
        for (i in 0 until 10) {
            val id = "stable-$i"
            roomLoads[id] = AtomicInteger(0)
            cache.getOrLoad(id) {
                roomLoads[id]?.incrementAndGet()
                createPatient(id)
            }
        }

        // Concurrently mutate and invalidate volatile keys while reading stable keys
        coroutineScope {
            val mutators = (0 until 25).map { workerId ->
                launch {
                    repeat(50) { iteration ->
                        val volatileId = "volatile-$workerId-$iteration"
                        cache.getOrLoad(volatileId) { createPatient(volatileId) }
                        cache.invalidate(volatileId)
                    }
                }
            }

            val readers = (0 until 25).map {
                launch {
                    repeat(50) {
                        for (i in 0 until 10) {
                            val id = "stable-$i"
                            val p = cache.getOrLoad(id) {
                                roomLoads[id]?.incrementAndGet()
                                createPatient(id)
                            }
                            assertNotNull(p)
                        }
                    }
                }
            }

            (mutators + readers).forEach { it.join() }
        }

        // Each stable patient must have been loaded from Room EXACTLY ONCE (initial load)
        for (i in 0 until 10) {
            val id = "stable-$i"
            assertEquals(
                "Stable key $id was reloaded from Room despite not being invalidated!",
                1,
                roomLoads[id]?.get()
            )
        }
    }

    @Test
    fun `in flight load race safety key generation prevents caching stale data on same key`() = runBlocking(Dispatchers.Default) {
        val cache = PatientCache(CacheRegistry())
        val loadStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val canCompleteLoad = kotlinx.coroutines.CompletableDeferred<Unit>()

        // Load P1: load starts and suspends while in-flight
        val deferred = async {
            cache.getOrLoad("P1") {
                loadStarted.complete(Unit) // Guarantee startedKeyGen is already captured
                canCompleteLoad.await()    // Suspend until invalidation occurs
                createPatient("P1", version = 1) // Stale read
            }
        }

        // Wait until getOrLoad has definitely captured startedKeyGen and entered load()
        loadStarted.await()
        cache.invalidate("P1") // Invalidates P1 while load is in flight
        canCompleteLoad.complete(Unit) // Allow load to complete

        val result = deferred.await()
        assertEquals("Patient-P1-v1", result?.name)
        // CRUCIAL: P1 must NOT be in cache because keyGen changed during in-flight load!
        assertFalse("Stale P1 data must not be cached after invalidation", cache.contains("P1"))

        // Fresh load gets new version and caches it
        val fresh = cache.getOrLoad("P1") { createPatient("P1", version = 2) }
        assertEquals("Patient-P1-v2", fresh?.name)
        assertTrue(cache.contains("P1"))
    }
}
