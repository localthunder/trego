package com.splitter.splittr.data.sync

import android.util.Log
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.utils.CoroutineDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

abstract class OptimizedSyncManager<T : Any>(
    val syncMetadataDao: SyncMetadataDao,
    val dispatchers: CoroutineDispatchers
) {
    protected abstract val entityType: String
    protected abstract val batchSize: Int

    // Abstract methods that each entity sync manager must implement
    protected abstract suspend fun getLocalChanges(): List<T>
    protected abstract suspend fun syncToServer(entity: T): Result<T>
    protected abstract suspend fun getServerChanges(since: Long): List<T>
    protected abstract suspend fun applyServerChange(serverEntity: T)

    suspend fun performSync() = withContext(dispatchers.io) {
        try {
            Log.d(TAG, "Starting sync for $entityType")

            // Get the timestamp we'll use for this sync BEFORE updating metadata
            val syncFromTimestamp = syncMetadataDao.getMetadata(entityType)?.lastSyncTimestamp ?: 0
            Log.d(TAG, "Will sync changes since: $syncFromTimestamp")

            // Now update sync status to pending (this will update timestamp but we'll use our saved one)
            syncMetadataDao.updateSyncStatus(
                entityType = entityType,
                status = SyncStatus.PENDING_SYNC
            )

            // First sync local changes to server
            val localChanges = getLocalChanges()
            Log.d(TAG, "Found ${localChanges.size} local changes to sync")

            var successCount = 0
            var failureCount = 0

            // Process local changes in batches
            localChanges.chunked(batchSize).forEach { batch ->
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

            // Get and apply server changes using our saved timestamp
            val serverChanges = getServerChanges(syncFromTimestamp)  // Using saved timestamp
            Log.d(TAG, "Found ${serverChanges.size} server changes to apply")

            // Rest of the sync logic...

            val syncStatus = if (failureCount == 0) SyncStatus.SYNCED else SyncStatus.SYNC_FAILED
            val syncResult = buildString {
                append("Sync completed. ")
                append("Successes: $successCount, ")
                append("Failures: $failureCount")
            }

            syncMetadataDao.update(entityType) {
                it.copy(
                    lastSyncTimestamp = System.currentTimeMillis(),
                    syncStatus = syncStatus,
                    lastSyncResult = syncResult,
                    updateCount = it.updateCount + 1
                )
            }

            Log.d(TAG, syncResult)

        } catch (e: Exception) {
            Log.e(TAG, "Sync failed for $entityType", e)
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