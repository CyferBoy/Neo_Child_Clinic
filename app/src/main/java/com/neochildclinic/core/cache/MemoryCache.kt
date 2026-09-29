package com.neochildclinic.core.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Type-safe, thread-safe in-memory cache with TTL, size limit, and explicit invalidation.
 * 
 * Uses lazy expiration: entries are checked for expiry on [get] and [put] operations,
 * avoiding the need for a background cleanup coroutine.
 * 
 * @param K Cache key type
 * @param V Cache value type
 * @param defaultTtlMs Default time-to-live in milliseconds (default: 5 minutes = 300000ms)
 * @param maxSize Maximum entries (default: 1000). Excess entries are evicted on [put].
 */
class MemoryCache<K, V>(
    private val defaultTtlMs: Long = 5 * 60 * 1000,
    private val maxSize: Int = 1000
) {
    private val data = ConcurrentHashMap<K, CacheEntry<V>>()
    
    private data class CacheEntry<V>(
        val value: V,
        val expiresAt: Long
    )

    fun get(key: K): V? {
        val entry = data[key]
        if (entry == null) return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            data.remove(key)
            return null
        }
        return entry.value
    }

    fun put(key: K, value: V, ttlMs: Long = defaultTtlMs) {
        if (data.size >= maxSize && !data.containsKey(key)) {
            evictOldest(1)
        }
        val expiresAt = System.currentTimeMillis() + ttlMs
        data[key] = CacheEntry(value, expiresAt)
    }

    fun invalidate(key: K) {
        data.remove(key)
    }

    fun invalidateAll(keys: Iterable<K>) {
        keys.forEach { data.remove(it) }
    }

    fun clear() {
        data.clear()
    }

    fun size(): Int = data.size

    fun containsKey(key: K): Boolean {
        val entry = data[key]
        return if (entry != null && System.currentTimeMillis() <= entry.expiresAt) {
            true
        } else {
            if (entry != null) {
                data.remove(key)
            }
            false
        }
    }

    private fun evictOldest(count: Int) {
        if (data.isEmpty()) return
        val sorted = data.entries
            .sortedBy { it.value.expiresAt }
            .take(count)
        sorted.forEach { (key, _) -> data.remove(key) }
    }

    fun shutdown() {
        // No background coroutine to cancel - cleanup is lazy
    }
}

/**
 * Cache key for composite queries (e.g., filtered/sorted lists)
 */
data class QueryCacheKey(
    val entityType: String,
    val query: String = "",
    val filter: String = "",
    val sort: String = ""
)

/**
 * Predefined TTL constants
 */
object CacheTtl {
    val SHORT_MS = 60_000L           // 1 minute
    val MEDIUM_MS = 5 * 60 * 1000L   // 5 minutes (default)
    val LONG_MS = 30 * 60 * 1000L    // 30 minutes
}
