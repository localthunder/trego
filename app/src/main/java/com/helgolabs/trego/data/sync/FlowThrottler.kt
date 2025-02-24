package com.helgolabs.trego.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration

// FlowThrottler.kt
class FlowThrottler<T>(
    private val windowDuration: Duration,
    private val maxRequests: Int
) {
    private val requests = mutableMapOf<T, MutableList<Long>>()

    suspend fun tryAcquire(key: T): Boolean = withContext(Dispatchers.IO) {
        synchronized(this) {
            val now = System.currentTimeMillis()
            val windowStart = now - windowDuration.toMillis()

            // Get or create request list for this key
            val keyRequests = requests.getOrPut(key) { mutableListOf() }

            // Remove old requests
            keyRequests.removeAll { it < windowStart }

            // Check if we can make a new request
            if (keyRequests.size < maxRequests) {
                keyRequests.add(now)
                true
            } else {
                false
            }
        }
    }
}