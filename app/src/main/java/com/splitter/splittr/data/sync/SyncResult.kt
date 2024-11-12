package com.splitter.splittr.data.sync

// SyncResult.kt
sealed class SyncResult<T> {
    data class Success<T>(
        val updatedItems: List<T>,
        val timestamp: Long,
        val etag: String? = null
    ) : SyncResult<T>()

    data class Error<T>(
        val error: Exception,
        val failedItems: List<T>? = null
    ) : SyncResult<T>()

    data class Skipped<T>(val reason: String) : SyncResult<T>()
}