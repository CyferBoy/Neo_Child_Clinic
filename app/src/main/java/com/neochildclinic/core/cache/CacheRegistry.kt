package com.neochildclinic.core.cache

import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks every user-scoped in-memory cache so logout / session change can empty all of them
 * in one call. Caches register themselves; the registry holds only [ClearableCache]
 * references (application-scoped singletons), never Context/View/ViewModel objects.
 *
 * Isolation between users is enforced by clearing, NOT by TTL.
 */
@Singleton
class CacheRegistry @Inject constructor() {
    private val caches = CopyOnWriteArrayList<ClearableCache>()

    fun register(cache: ClearableCache) {
        caches.addIfAbsent(cache)
    }

    /** Clears every registered cache. One failing cache never blocks the rest. */
    fun clearAll() {
        caches.forEach { runCatching { it.clear() } }
    }
}
