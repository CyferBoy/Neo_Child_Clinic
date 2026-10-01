package com.neochildclinic.feature.patient.data

import com.neochildclinic.core.cache.CacheRegistry
import com.neochildclinic.core.cache.ClearableCache
import com.neochildclinic.core.cache.MemoryCache
import com.neochildclinic.domain.model.Patient
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Patient-detail cache (key `PATIENT:<id>`). Room stays the source of truth; this only
 * skips repeated `getPatientById` DAO reads.
 *
 * Race guard: a read that started BEFORE an invalidation must not write its (possibly stale)
 * result back AFTER it. Every invalidation bumps [generation]; [getOrLoad] only caches when
 * the generation is unchanged since the load began.
 */
@Singleton
class PatientCache internal constructor(
    private val cache: MemoryCache<String, Patient>
) : ClearableCache {

    @Inject
    constructor(registry: CacheRegistry) : this(MemoryCache<String, Patient>()) {
        registry.register(this)
    }

    private val generation = AtomicLong()

    /** Cache hit -> cached value. Miss -> [load] from Room, then cache a non-null result. */
    suspend fun getOrLoad(id: String, load: suspend () -> Patient?): Patient? {
        val key = key(id)
        runCatching { cache.get(key) }.getOrNull()?.let { return it }

        val startedAt = generation.get()
        val fromRoom = load()
        if (fromRoom != null && generation.get() == startedAt) {
            runCatching { cache.put(key, fromRoom) } // cache failure must never break the read
        }
        return fromRoom
    }

    /** Call after a successful Room mutation of this patient. */
    fun invalidate(id: String) {
        generation.incrementAndGet()
        cache.remove(key(id))
    }

    /** Call after bulk/unknown Room writes (sync pull, restore, migration) and on logout. */
    override fun clear() {
        generation.incrementAndGet()
        cache.clear()
    }

    internal fun contains(id: String): Boolean = cache.contains(key(id))

    companion object {
        fun key(id: String) = "PATIENT:$id"
    }
}
