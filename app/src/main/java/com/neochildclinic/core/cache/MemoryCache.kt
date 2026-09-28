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
 * @param K Cache key type
 * @param V Cache value type
 * @param defaultTtlMs Default time-to-live in milliseconds (default: 5 minutes)
 * @param maxSize Maximum entries (default: 1000)
 */
class MemoryCache<K, V>(
    private val defaultTtlMs: Long = 5 * 60 * 1000,
    private val maxSize: Int = 1000
) {
    private val data = ConcurrentHashMap<K, CacheEntry<V>>()
    private val accessOrder = ConcurrentHashMap<K, AtomicLong>()
    
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cleanupJob = cleanupScope.launch {
        while (true) {
            delay(60_000)
            evictExpired()
            if (data.size > maxSize) {
                evictOldest(data.size - maxSize)
            }
        }
    }

    private data class CacheEntry<V>(
        val value: V,
        val expiresAt: Long
    )

    suspend fun get(key: K): V? {
        val entry = data[key]
        if (entry == null) return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            data.remove(key)
            accessOrder.remove(key)
            return null
        }
        accessOrder[key]?.set(System.currentTimeMillis())
        return entry.value
    }

    suspend fun put(key: K, value: V, ttlMs: Long = defaultTtlMs) {
        if (data.size >= maxSize && !data.containsKey(key)) {
            evictOldest(1)
        }
        val expiresAt = System.currentTimeMillis() + ttlMs
        data[key] = CacheEntry(value, expiresAt)
        accessOrder[key] = AtomicLong(System.currentTimeMillis())
    }

    suspend fun invalidate(key: K) {
        data.remove(key)
        accessOrder.remove(key)
    }

    suspend fun invalidateAll(keys: Iterable<K>) {
        keys.forEach { data.remove(it); accessOrder.remove(it) }
    }

    suspend fun clear() {
        data.clear()
        accessOrder.clear()
    }

    fun size(): Int = data.size

    fun containsKey(key: K): Boolean {
        val entry = data[key]
        return if (entry != null && System.currentTimeMillis() <= entry.expiresAt) {
            true
        } else {
            if (entry != null) {
                data.remove(key)
                accessOrder.remove(key)
            }
            false
        }
    }

    private fun evictExpired() {
        val now = System.currentTimeMillis()
        val keysToRemove = mutableSetOf<K>()
        data.forEach { (key, entry) ->
            if (now > entry.expiresAt) {
                keysToRemove.add(key)
            }
        }
        keysToRemove.forEach { data.remove(it); accessOrder.remove(it) }
    }

    private fun evictOldest(count: Int) {
        val sorted = accessOrder.entries
            .sortedBy { it.value.get() }
            .take(count)
        sorted.forEach { data.remove(it.key); accessOrder.remove(it.key) }
    }

    fun shutdown() {
        cleanupJob.cancel()
        cleanupScope.coroutineContext[Job]?.cancel()
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