package com.helgolabs.trego.data.sync

import com.helgolabs.trego.data.local.dao.SyncMetadataDao
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

class SyncCoordinator(
    private val dispatchers: CoroutineDispatchers,
    private val repositories: List<SyncableRepository>,
    private val networkUtils: NetworkUtils,
    private val syncMetadataDao: SyncMetadataDao
) {
    private val syncScope = CoroutineScope(dispatchers.io + SupervisorJob())
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    sealed class SyncState {
        object Idle : SyncState()
        object InProgress : SyncState()
        data class Completed(val timestamp: Long) : SyncState()
        data class Failed(val error: String) : SyncState()
    }

    fun startSync(forceSync: Boolean = false) {
        if (_syncState.value is SyncState.InProgress) return

        syncScope.launch {
            _syncState.value = SyncState.InProgress
            try {
                if (!networkUtils.isOnline()) {
                    throw IOException("No network connection available")
                }

                // Sync in specific order to maintain data consistency
                val syncOrder = repositories.sortedBy { it.syncPriority }

                syncOrder.forEach { repository ->
                    try {
                        val lastSync = syncMetadataDao.getMetadata(repository.entityType)
                        if (forceSync || shouldSync(lastSync)) {
                            repository.sync()
                            updateSyncMetadata(repository.entityType, SyncStatus.SYNCED)
                        }
                    } catch (e: Exception) {
                        updateSyncMetadata(repository.entityType, SyncStatus.SYNC_FAILED)
                        throw e
                    }
                }

                _syncState.value = SyncState.Completed(System.currentTimeMillis())
            } catch (e: Exception) {
                _syncState.value = SyncState.Failed(e.message ?: "Unknown error occurred")
            }
        }
    }

    private suspend fun shouldSync(metadata: SyncMetadata?): Boolean {
        if (metadata == null) return true
        if (metadata.syncStatus == SyncStatus.SYNC_FAILED) return true

        val timeSinceLastSync = System.currentTimeMillis() - metadata.lastSyncTimestamp
        return timeSinceLastSync > SYNC_INTERVAL
    }

    private suspend fun updateSyncMetadata(
        entityType: String,
        status: SyncStatus,
        error: String? = null
    ) {
        syncMetadataDao.update(entityType) {
            it.copy(
                lastSyncTimestamp = System.currentTimeMillis(),
                syncStatus = status,
                lastSyncResult = error
            )
        }
    }

    companion object {
        private const val SYNC_INTERVAL = 15 * 60 * 1000 // 15 minutes
    }
}

// Interface that all syncable repositories should implement
interface SyncableRepository {
    val entityType: String
    val syncPriority: Int // Lower numbers sync first

    suspend fun sync()
}