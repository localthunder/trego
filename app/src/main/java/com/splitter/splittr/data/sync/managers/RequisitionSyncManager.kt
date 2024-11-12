package com.splitter.splittr.data.sync.managers

import android.content.Context
import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.RequisitionDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.model.Requisition
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.OptimizedSyncManager
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.utils.ConflictResolution
import com.splitter.splittr.utils.ConflictResolver
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.NetworkUtils
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first
class RequisitionSyncManager(
    private val requisitionDao: RequisitionDao,
    private val apiService: ApiService,
    syncMetadataDao: SyncMetadataDao,
    dispatchers: CoroutineDispatchers,
    private val context: Context
) : OptimizedSyncManager<Requisition>(syncMetadataDao, dispatchers) {

    override val entityType = "requisitions"
    override val batchSize = 20

    override suspend fun getLocalChanges(): List<Requisition> {
        // Since requisitions are server-driven, we don't need to sync local changes
        return emptyList()
    }

    override suspend fun syncToServer(entity: Requisition): Result<Requisition> {
        // This won't be called since getLocalChanges() returns empty list
        throw UnsupportedOperationException("Requisitions cannot be modified locally")
    }

    override suspend fun getServerChanges(since: Long): List<Requisition> {
        val userId = getUserIdFromPreferences(context) ?: throw IllegalStateException("User ID not found")
        Log.d(TAG, "Fetching requisitions for user $userId")
        return apiService.getRequisitionsByUserId(userId)
    }

    override suspend fun applyServerChange(serverEntity: Requisition) {
        // Simply update or insert the server version
        try {
            val localEntity = requisitionDao.getRequisitionById(serverEntity.requisitionId)
            if (localEntity == null) {
                Log.d(TAG, "Inserting new requisition from server: ${serverEntity.requisitionId}")
                requisitionDao.insert(serverEntity.toEntity(SyncStatus.SYNCED))
            } else if (serverEntity.updatedAt > localEntity.updatedAt.toString()) {
                Log.d(TAG, "Updating existing requisition from server: ${serverEntity.requisitionId}")
                requisitionDao.updateRequisition(serverEntity.toEntity(SyncStatus.SYNCED))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying server requisition: ${serverEntity.requisitionId}", e)
            throw e
        }
    }

    /**
     * Handle new requisition from bank authentication
     */
    suspend fun handleNewRequisition(requisition: Requisition) {
        try {
            Log.d(TAG, "Handling new requisition: ${requisition.requisitionId}")
            requisitionDao.insert(requisition.toEntity(SyncStatus.SYNCED))
        } catch (e: Exception) {
            Log.e(TAG, "Error handling new requisition", e)
            throw e
        }
    }


    companion object {
        private const val TAG = "RequisitionSyncManager"
    }
}