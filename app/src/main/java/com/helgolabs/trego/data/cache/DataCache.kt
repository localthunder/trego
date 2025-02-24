package com.helgolabs.trego.data.cache


// Add a cache utility for holding temporary data
object DataCache {
    private val cache = mutableMapOf<String, CacheEntry>()
    private const val CACHE_DURATION = 5 * 60 * 1000 // 5 minutes

    data class CacheEntry(
        val data: Any,
        val timestamp: Long
    )

    fun <T> get(key: String): T? {
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > CACHE_DURATION) {
            cache.remove(key)
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return entry.data as T
    }

    fun set(key: String, value: Any) {
        cache[key] = CacheEntry(value, System.currentTimeMillis())
    }

    fun clear() {
        cache.clear()
    }
}