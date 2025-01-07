package com.splitter.splittr.data.sync.managers

import android.content.Context
import android.util.Log
import com.splitter.splittr.MyApplication
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.RequisitionDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.local.dao.UserDao
import com.splitter.splittr.data.model.Requisition
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.OptimizedSyncManager
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.utils.ConflictResolution
import com.splitter.splittr.utils.ConflictResolver
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.DateUtils
import com.splitter.splittr.utils.NetworkUtils
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first
class RequisitionSyncManager(
    private val requisitionDao: RequisitionDao,
    private val userDao: UserDao,
    private val apiService: ApiService,
    syncMetadataDao: SyncMetadataDao,
    dispatchers: CoroutineDispatchers,
    private val context: Context
) : OptimizedSyncManager<Requisition>(syncMetadataDao, dispatchers) {

    override val entityType = "requisitions"
    override val batchSize = 20

    val myApplication = context.applicationContext as MyApplication

    override suspend fun getLocalChanges(): List<Requisition> {
        // Since requisitions are server-driven, we don't need to sync local changes
        return emptyList()
    }

    override suspend fun syncToServer(entity: Requisition): Result<Requisition> {
        // This won't be called since getLocalChanges() returns empty list
        throw UnsupportedOperationException("Requisitions cannot be modified locally")
    }

    override suspend fun getServerChanges(since: Long): List<Requisition> {
        val userId = getUserIdFromPreferences(context)
            ?: throw IllegalStateException("User ID not found")

        // Get the server ID from the local user ID
        val localUser = userDao.getUserByIdDirect(userId)
            ?: throw IllegalStateException("User not found in local database")

        val serverUserId = localUser.serverId
            ?: throw IllegalStateException("No server ID found for user $userId")

        Log.d(TAG, "Fetching requisitions for user $userId")
        return apiService.getRequisitionsSince(since, serverUserId)
    }

    override suspend fun applyServerChange(serverEntity: Requisition) {
        try {
            val localEntity = requisitionDao.getRequisitionById(serverEntity.requisitionId)

            // Convert server requisition to local entity
            val convertedRequisition = myApplication.entityServerConverter.convertRequisitionFromServer(
                serverEntity,
                localEntity
            ).getOrNull() ?: throw Exception("Failed to convert server requisition")

            when {
                localEntity == null -> {
                    Log.d(TAG, "Inserting new requisition from server: ${serverEntity.requisitionId}")
                    requisitionDao.insert(convertedRequisition)
                }
                DateUtils.isUpdateNeeded(
                    serverEntity.updatedAt,
                    localEntity.updatedAt,
                    "Requisition-${serverEntity.requisitionId}-Institution-${serverEntity.institutionId}"
                ) -> {
                    Log.d(TAG, "Updating existing requisition from server: ${serverEntity.requisitionId}")
                    requisitionDao.updateRequisition(convertedRequisition)
                    requisitionDao.updateRequisitionSyncStatus(
                        serverEntity.requisitionId,
                        SyncStatus.SYNCED
                    )
                }
                else -> {
                    Log.d(TAG, "Local requisition ${serverEntity.requisitionId} is up to date")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, """
                Error applying server requisition: ${serverEntity.requisitionId}
                Server timestamp: ${serverEntity.updatedAt}
                Error: ${e.message}
            """.trimIndent(), e)

            requisitionDao.updateRequisitionSyncStatus(
                serverEntity.requisitionId,
                SyncStatus.SYNC_FAILED
            )
            throw e
        }
    }

    suspend fun handleNewRequisition(requisition: Requisition) {
        try {
            Log.d(TAG, "Handling new requisition: ${requisition.requisitionId}")
            myApplication.entityServerConverter.convertRequisitionFromServer(requisition)
                .onSuccess { localRequisition ->
                    requisitionDao.insert(localRequisition)
                }
                .onFailure { error ->
                    Log.e(TAG, "Error converting new requisition", error)
                    throw error
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling new requisition", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "RequisitionSyncManager"
    }
}