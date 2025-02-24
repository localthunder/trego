package com.helgolabs.trego.data.sync

import com.helgolabs.trego.data.local.dao.SyncMetadataDao
import com.helgolabs.trego.data.local.dataClasses.SyncResult
import com.helgolabs.trego.utils.CoroutineDispatchers
import kotlinx.coroutines.withContext
import java.time.Duration

// BaseSyncManager.kt
abstract class BaseSyncManager<T : SyncableEntity>(
    private val syncMetadataDao: SyncMetadataDao,
    private val dispatchers: CoroutineDispatchers
) {
    private val syncThrottler = FlowThrottler<String>(
        windowDuration = Duration.ofMinutes(5),
        maxRequests = 10
    )

    protected abstract val entityType: String
    protected abstract suspend fun syncLocalToServer(entities: List<T>): SyncResult<T>
    protected abstract suspend fun syncServerToLocal(strategy: SyncStrategy): SyncResult<T>
    protected abstract suspend fun resolveConflicts(local: T, server: T): T

    suspend fun performSync(forceSync: Boolean = false): SyncResult<T> =
        withContext(dispatchers.io) {
            try {
                if (!forceSync && !shouldSync()) {
                    return@withContext SyncResult.Skipped("Sync not needed")
                }

                val metadata = syncMetadataDao.getMetadata(entityType)
                val strategy = determineSyncStrategy(metadata)

                // First sync local changes to server
                val localResult = syncLocalToServer(getUnsyncedEntities())

                // Then sync from server
                val serverResult = syncServerToLocal(strategy)

                // Update metadata
                updateSyncMetadata(localResult, serverResult)

                return@withContext combineResults(localResult, serverResult)
            } catch (e: Exception) {
                SyncResult.Error(e)
            }
        }

    private suspend fun shouldSync(): Boolean {
        if (!syncThrottler.tryAcquire(entityType)) {
            return false
        }

        val metadata = syncMetadataDao.getMetadata(entityType)
        val timeSinceLastSync = System.currentTimeMillis() - (metadata?.lastSyncTimestamp ?: 0)
        return timeSinceLastSync > SYNC_INTERVAL || metadata?.syncStatus == SyncStatus.SYNC_FAILED
    }

    private fun determineSyncStrategy(metadata: SyncMetadata?): SyncStrategy {
        return when {
            metadata == null -> SyncStrategy.FullSync
            metadata.lastEtag != null -> SyncStrategy.EtagSync(metadata.lastEtag)
            else -> SyncStrategy.IncrementalSync(metadata.lastSyncTimestamp)
        }
    }

    protected abstract suspend fun getUnsyncedEntities(): List<T>

    private suspend fun updateSyncMetadata(
        localResult: SyncResult<T>,
        serverResult: SyncResult<T>
    ) {
        when {
            localResult is SyncResult.Error || serverResult is SyncResult.Error -> {
                syncMetadataDao.update(entityType) {
                    it.copy(
                        syncStatus = SyncStatus.SYNC_FAILED,
                        lastSyncResult = "Sync failed: ${
                            (localResult as? SyncResult.Error)?.error?.message ?: (serverResult as? SyncResult.Error)?.error?.message
                        }"
                    )
                }
            }

            else -> {
                val timestamp = maxOf(
                    (localResult as? SyncResult.Success)?.timestamp ?: 0,
                    (serverResult as? SyncResult.Success)?.timestamp ?: 0
                )
                syncMetadataDao.update(entityType) {
                    it.copy(
                        lastSyncTimestamp = timestamp,
                        lastEtag = (serverResult as? SyncResult.Success)?.etag,
                        syncStatus = SyncStatus.SYNCED,
                        updateCount = it.updateCount + 1,
                        lastSyncResult = "Sync completed successfully"
                    )
                }
            }
        }
    }

    companion object {
        private const val SYNC_INTERVAL = 15 * 60 * 1000 // 15 minutes
    }

    private fun combineResults(
        localResult: SyncResult<T>,
        serverResult: SyncResult<T>
    ): SyncResult<T> {
        return when {
            // If either result is an error, return the error
            localResult is SyncResult.Error -> localResult
            serverResult is SyncResult.Error -> serverResult

            // If both are successful, combine the results
            localResult is SyncResult.Success && serverResult is SyncResult.Success -> {
                SyncResult.Success(
                    updatedItems = localResult.updatedItems + serverResult.updatedItems,
                    timestamp = maxOf(localResult.timestamp, serverResult.timestamp),
                    etag = serverResult.etag
                )
            }

            // If server sync was skipped but local sync succeeded
            localResult is SyncResult.Success && serverResult is SyncResult.Skipped -> {
                localResult
            }

            // If local sync was skipped but server sync succeeded
            localResult is SyncResult.Skipped && serverResult is SyncResult.Success -> {
                serverResult
            }

            // If both were skipped
            localResult is SyncResult.Skipped && serverResult is SyncResult.Skipped -> {
                SyncResult.Skipped("Both local and server sync were skipped: " +
                        "${localResult.reason}, ${serverResult.reason}")
            }

            // Should never happen if all SyncResult cases are handled
            else -> SyncResult.Error(
                IllegalStateException("Invalid combination of sync results: " +
                        "local=$localResult, server=$serverResult")
            )
        }
    }
}