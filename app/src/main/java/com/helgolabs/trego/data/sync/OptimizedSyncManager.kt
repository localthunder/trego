package com.helgolabs.trego.data.sync

import android.util.Log
import com.helgolabs.trego.data.local.dao.SyncMetadataDao
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.DateUtils
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

    // Optional: Override this to provide entity-specific filtering logic
    protected open fun shouldSyncEntity(entity: LocalType): Boolean = true

    // Optional: Override this to extract timestamp from entity for anti-loop protection
    protected open fun getEntityTimestamp(entity: LocalType): Long = System.currentTimeMillis()

    // Optional: Override this to extract sync status from entity
    protected open fun getEntitySyncStatus(entity: LocalType): SyncStatus = SyncStatus.PENDING_SYNC

    suspend fun performSync() = withContext(dispatchers.io) {
        try {
            Log.d(TAG, "Starting sync for $entityType")
            val syncFromTimestamp = syncMetadataDao.getMetadata(entityType)?.lastSyncTimestamp ?: 0
            Log.d(TAG, "Will sync changes since: $syncFromTimestamp")

            var successCount = 0
            var failureCount = 0

            // Get local changes and apply anti-loop filtering
            val localChanges = getLocalChanges()
            val filteredChanges = filterRecentChanges(localChanges)

            Log.d(TAG, "Found ${localChanges.size} local changes, ${filteredChanges.size} after anti-loop filtering")

            // Sync local changes
            filteredChanges.chunked(batchSize).forEach { batch ->
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

    /**
     * Filters out entities that were recently updated to prevent sync loops
     */
    private fun filterRecentChanges(entities: List<LocalType>): List<LocalType> {
        val currentTime = System.currentTimeMillis()

        return entities.filter { entity ->
            try {
                // Apply custom entity filtering first
                if (!shouldSyncEntity(entity)) {
                    Log.d(TAG, "Entity filtered out by shouldSyncEntity check")
                    return@filter false
                }

                val entityTimestamp = getEntityTimestamp(entity)
                val syncStatus = getEntitySyncStatus(entity)
                val timeDiff = currentTime - entityTimestamp

                // Skip entities that were recently synced successfully (within 5 seconds)
                if (syncStatus == SyncStatus.SYNCED && timeDiff < RECENT_SYNC_THRESHOLD_MS) {
                    Log.d(TAG, "Skipping recently synced $entityType (${timeDiff}ms ago, status: $syncStatus)")
                    return@filter false
                }

                // Skip entities that were very recently updated (within 2 seconds)
                // This prevents sync loops from rapid successive updates
                if (syncStatus == SyncStatus.PENDING_SYNC && timeDiff < RAPID_UPDATE_THRESHOLD_MS) {
                    Log.d(TAG, "Skipping very recent $entityType update (${timeDiff}ms ago)")
                    return@filter false
                }

                true
            } catch (e: Exception) {
                Log.w(TAG, "Error filtering entity, including in sync", e)
                true // Include in sync if we can't determine
            }
        }
    }

    /**
     * Enhanced syncToServer wrapper that provides additional safety checks
     */
    protected suspend fun safeSyncToServer(entity: LocalType): Result<LocalType> {
        return try {
            // Double-check entity should still be synced
            if (!shouldSyncEntity(entity)) {
                Log.d(TAG, "Entity no longer needs sync, skipping")
                return Result.success(entity)
            }

            val syncStatus = getEntitySyncStatus(entity)
            val entityTimestamp = getEntityTimestamp(entity)
            val currentTime = System.currentTimeMillis()
            val timeDiff = currentTime - entityTimestamp

            // Additional safety check for very recent syncs
            if (syncStatus == SyncStatus.SYNCED && timeDiff < RECENT_SYNC_THRESHOLD_MS) {
                Log.d(TAG, "Entity was recently synced, skipping duplicate sync")
                return Result.success(entity)
            }

            // Proceed with actual sync
            syncToServer(entity)
        } catch (e: Exception) {
            Log.e(TAG, "Error in safeSyncToServer", e)
            Result.failure(e)
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
        private const val RECENT_SYNC_THRESHOLD_MS = 5000L // 5 seconds
        private const val RAPID_UPDATE_THRESHOLD_MS = 2000L // 2 seconds
    }
}