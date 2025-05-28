package com.helgolabs.trego.utils

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A utility class for debouncing network operations
 */
object NetworkDebouncer {
    private val mutex = Mutex()
    private val operationTimestamps = mutableMapOf<String, Long>()
    private const val DEFAULT_DEBOUNCE_PERIOD = 5000L // 5 seconds

    /**
     * Checks if an operation should be debounced based on a key and debounce period
     * @param operationKey A unique key identifying the operation
     * @param debounceMs The debounce period in milliseconds
     * @return true if the operation should proceed, false if it should be debounced
     */
    suspend fun shouldProceed(operationKey: String, debounceMs: Long = DEFAULT_DEBOUNCE_PERIOD): Boolean {
        val currentTime = System.currentTimeMillis()

        return mutex.withLock {
            val lastOperationTime = operationTimestamps[operationKey] ?: 0L
            val shouldProceed = currentTime - lastOperationTime > debounceMs

            if (shouldProceed) {
                operationTimestamps[operationKey] = currentTime
                Log.d("NetworkDebouncer", "Operation $operationKey proceeding")
            } else {
                Log.d("NetworkDebouncer", "Operation $operationKey debounced")
            }

            shouldProceed
        }
    }

    /**
     * Runs the given block only if it passes the debounce check
     * @param operationKey A unique key identifying the operation
     * @param debounceMs The debounce period in milliseconds
     * @param block The code block to execute if not debounced
     * @return true if the operation was executed, false if it was debounced
     */
    suspend fun runIfNotDebounced(
        operationKey: String,
        debounceMs: Long = DEFAULT_DEBOUNCE_PERIOD,
        block: suspend () -> Unit
    ): Boolean {
        return if (shouldProceed(operationKey, debounceMs)) {
            block()
            true
        } else {
            false
        }
    }

    /**
     * Resets the debounce timer for an operation
     * @param operationKey The operation key to reset
     */
    suspend fun reset(operationKey: String) {
        mutex.withLock {
            operationTimestamps.remove(operationKey)
        }
    }
}