package com.splitter.splittr.data.sync

import android.util.Log
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.utils.CoroutineDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

abstract class OptimizedSyncManager<LocalType : Any, ServerType : Any>(
    val syncMetadataDao: SyncMetadataDao,
    val dispatchers: CoroutineDispatchers
) {
    protected abstract val entityType: String
    protected abstract val batchSize: Int

    // Abstract methods that each entity sync manager must implement
    protected abstract suspend fun getLocalChanges(): List<LocalType>
    protected abstract suspend fun syncToServer(entity: LocalType): Result<LocalType>
    protected abstract suspend fun getServerChanges(since: Long): List<ServerType>
    protected abstract suspend fun applyServerChange(serverEntity: ServerType)

    suspend fun performSync() = withContext(dispatchers.io) {
        try {
            Log.d(TAG, "Starting sync for $entityType")
            val syncFromTimestamp = syncMetadataDao.getMetadata(entityType)?.lastSyncTimestamp ?: 0
            Log.d(TAG, "Will sync changes since: $syncFromTimestamp")

            var successCount = 0
            var failureCount = 0

            // Sync local changes
            getLocalChanges().chunked(batchSize).forEach { batch ->
                batch.forEach { entity ->
                    try {
                        withRetry {
                            val result = syncToServer(entity)
                            if (result.isSuccess) successCount++ else failureCount++
                        }
                    } catch (e: Exception) {
                        failureCount++
                        Log.e(TAG, "Error syncing entity to server", e)
                    }
                }
                delay(100)
            }

            // Sync server changes
            getServerChanges(syncFromTimestamp).chunked(batchSize).forEach { batch ->
                batch.forEach { serverEntity ->
                    try {
                        withRetry {
                            applyServerChange(serverEntity)
                            successCount++
                        }
                    } catch (e: Exception) {
                        failureCount++
                        Log.e(TAG, "Error applying server change", e)
                    }
                }
                delay(100)
            }

            // Only update metadata on successful sync
            if (failureCount == 0) {
                syncMetadataDao.update(entityType) {
                    it.copy(
                        lastSyncTimestamp = System.currentTimeMillis(),
                        syncStatus = SyncStatus.SYNCED,
                        lastSyncResult = "Sync completed. Successes: $successCount",
                        updateCount = it.updateCount + 1
                    )
                }
            } else {
                syncMetadataDao.update(entityType) {
                    it.copy(
                        syncStatus = SyncStatus.SYNC_FAILED,
                        lastSyncResult = "Sync completed with failures: $failureCount"
                    )
                }
            }
        } catch (e: Exception) {
            syncMetadataDao.update(entityType) {
                it.copy(
                    syncStatus = SyncStatus.SYNC_FAILED,
                    lastSyncResult = "Sync failed: ${e.message}"
                )
            }
            throw e
        }
    }

    private suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelay: Long = 1000,
        maxDelay: Long = 5000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(maxAttempts - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                Log.e(TAG, "Attempt ${attempt + 1} failed", e)
                if (e is IOException || e.cause is IOException) {
                    delay(currentDelay)
                    currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
                } else {
                    throw e  // Don't retry non-IO exceptions
                }
            }
        }
        return block() // last attempt
    }

    companion object {
        private const val TAG = "OptimizedSyncManager"
    }
}