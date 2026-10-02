package com.neochildclinic.core.cache

import java.util.concurrent.TimeUnit

/** Anything that can be emptied wholesale (used for logout / session teardown). */
interface ClearableCache {
    fun clear()
}

/**
 * Generic in-memory cache: a temporary performance optimization ONLY.
 *
 * Room is the local source of truth. This class answers a single question - "do we already
 * hold a recently-read copy of this value in memory?" - and never "what is the correct data?".
 * An empty, expired or cleared cache must always be safe: callers fall back to Room.
 *
 * - Thread-safe: every operation is guarded by one lock. The critical sections are O(1)
 *   except eviction, which is O(n) over at most [maxSize] entries and only runs when the
 *   cache is full (ponytail: fine for a few hundred entries; switch to LinkedHashMap
 *   access-order if maxSize ever grows into the thousands).
 * - TTL is a memory-retention policy, NOT a consistency mechanism. Writers must invalidate
 *   explicitly; TTL just stops entries living in memory forever.
 * - Lazy expiration: expired entries are dropped on [get]/[contains]/[put]; there is no
 *   background cleanup coroutine.
 * - Bounded: when full, [put] evicts the entry with the EARLIEST EXPIRY (not the least
 *   recently used), so size never exceeds [maxSize].
 * - TTL uses a monotonic clock ([System.nanoTime]) so wall-clock changes cannot make
 *   entries live longer or die earlier.
 * - Holds only the values it is given: no Context, View, ViewModel, DB or network client.
 *
 * @param defaultTtlMs default time-to-live in milliseconds (5 minutes)
 * @param maxSize hard upper bound on the number of entries
 * @param nanoClock monotonic time source in nanoseconds; injectable for tests
 */
class MemoryCache<K : Any, V : Any>(
    private val defaultTtlMs: Long = 5 * 60 * 1000L,
    private val maxSize: Int = 1000,
    private val nanoClock: () -> Long = System::nanoTime
) : ClearableCache {

    init {
        require(maxSize > 0) { "maxSize must be positive" }
        require(defaultTtlMs > 0) { "defaultTtlMs must be positive" }
    }

    private class Entry<V>(val value: V, val expiresAtNanos: Long)

    private val lock = Any()
    private val data = HashMap<K, Entry<V>>()

    fun get(key: K): V? = synchronized(lock) {
        val entry = data[key] ?: return null
        if (isExpired(entry)) {
            data.remove(key)
            return null
        }
        entry.value
    }

    fun put(key: K, value: V, ttlMs: Long = defaultTtlMs) {
        synchronized(lock) {
            if (!data.containsKey(key) && data.size >= maxSize) {
                dropExpired()
                if (data.size >= maxSize) evictEarliestExpiry()
            }
            data[key] = Entry(value, nanoClock() + TimeUnit.MILLISECONDS.toNanos(ttlMs))
        }
    }

    fun contains(key: K): Boolean = get(key) != null

    /** Removes one entry. Also the name callers use for "invalidate this key". */
    fun remove(key: K) {
        synchronized(lock) { data.remove(key) }
    }

    override fun clear() {
        synchronized(lock) { data.clear() }
    }

    fun size(): Int = synchronized(lock) { data.size }

    private fun isExpired(entry: Entry<V>): Boolean = nanoClock() - entry.expiresAtNanos >= 0

    private fun dropExpired() {
        val now = nanoClock()
        data.values.removeAll { now - it.expiresAtNanos >= 0 }
    }

    private fun evictEarliestExpiry() {
        val victim = data.entries.minByOrNull { it.value.expiresAtNanos }?.key ?: return
        data.remove(victim)
    }
}
