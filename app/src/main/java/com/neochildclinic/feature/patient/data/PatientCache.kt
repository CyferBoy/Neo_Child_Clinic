package com.neochildclinic.feature.patient.data

import com.neochildclinic.core.cache.CacheRegistry
import com.neochildclinic.core.cache.ClearableCache
import com.neochildclinic.core.cache.MemoryCache
import com.neochildclinic.domain.model.Patient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Patient-detail cache (key `PATIENT:<id>`). Room stays the source of truth; this only
 * skips repeated `getPatientById` DAO reads.
 *
 * Race guard: tracks generation per key plus a global generation counter. An in-flight
 * load for patient A is invalidated only if patient A is invalidated or the entire cache is cleared,
 * eliminating false-positive generation races when unrelated patients are updated.
 */
@Singleton
class PatientCache internal constructor(
    private val cache: MemoryCache<String, Patient>
) : ClearableCache {

    @Inject
    constructor(registry: CacheRegistry) : this(MemoryCache<String, Patient>()) {
        registry.register(this)
    }

    private val globalGeneration = AtomicLong()
    private val keyGenerations = ConcurrentHashMap<String, AtomicLong>()
    private val keyLocks = ConcurrentHashMap<String, Any>()

    private fun getKeyLock(key: String): Any =
        keyLocks.computeIfAbsent(key) { Any() }

    private fun getKeyGen(key: String): AtomicLong =
        keyGenerations.computeIfAbsent(key) { AtomicLong() }

    /** Cache hit -> cached value. Miss -> [load] from Room, then cache a non-null result. */
    suspend fun getOrLoad(id: String, load: suspend () -> Patient?): Patient? {
        val key = key(id)
        cache.get(key)?.let { return it }

        val startedGlobal = globalGeneration.get()
        val startedKeyGen = getKeyGen(key).get()

        val fromRoom = load()
        if (fromRoom != null) {
            synchronized(getKeyLock(key)) {
                if (globalGeneration.get() == startedGlobal && getKeyGen(key).get() == startedKeyGen) {
                    cache.put(key, fromRoom)
                }
            }
        }
        return fromRoom
    }

    /** Call after a successful Room mutation of this patient. */
    fun invalidate(id: String) {
        val key = key(id)
        synchronized(getKeyLock(key)) {
            getKeyGen(key).incrementAndGet()
            cache.remove(key)
        }
    }

    /** Call after bulk/unknown Room writes (sync pull, restore, migration) and on logout. */
    override fun clear() {
        globalGeneration.incrementAndGet()
        keyGenerations.clear()
        keyLocks.clear()
        cache.clear()
    }

    internal fun contains(id: String): Boolean = cache.contains(key(id))

    companion object {
        fun key(id: String) = "PATIENT:$id"
    }
}
