package com.helgolabs.trego.data.sync.managers

import android.content.Context
import android.util.Log
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.dao.GroupDefaultSplitDao
import com.helgolabs.trego.data.local.dao.SyncMetadataDao
import com.helgolabs.trego.data.local.dao.UserDao
import com.helgolabs.trego.data.local.entities.GroupDefaultSplitEntity
import com.helgolabs.trego.data.model.GroupDefaultSplit
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.sync.OptimizedSyncManager
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.ServerIdUtil
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first

class GroupDefaultSplitSyncManager(
    private val groupDefaultSplitDao: GroupDefaultSplitDao,
    private val userDao: UserDao,
    private val apiService: ApiService,
    syncMetadataDao: SyncMetadataDao,
    dispatchers: CoroutineDispatchers,
    private val context: Context
) : OptimizedSyncManager<GroupDefaultSplitEntity, GroupDefaultSplit>(syncMetadataDao, dispatchers) {

    override val entityType = "group_default_splits"
    override val batchSize = 20

    val myApplication = context.applicationContext as MyApplication

    override suspend fun getLocalChanges(): List<GroupDefaultSplitEntity> {
        return groupDefaultSplitDao.getUnsyncedDefaultSplits()
    }
    override suspend fun syncToServer(entity: GroupDefaultSplitEntity): Result<GroupDefaultSplitEntity> {
        try {
            Log.d(TAG, "Starting syncToServer for default split: ${entity.id}")

            // First convert local entity to server model for API call
            val serverModel = myApplication.entityServerConverter.convertGroupDefaultSplitToServer(entity)
                .getOrElse {
                    Log.e(TAG, "Failed to convert local entity to server model", it)
                    return Result.failure(it)
                }

            val groupServerId = ServerIdUtil.getServerId(entity.groupId, "groups", context) ?: 0

            // Make the API call based on whether it's a new or existing split
            val serverResult = if (entity.serverId == null) {
                Log.d(TAG, "Creating new default split on server for group $groupServerId")
                val result = apiService.createGroupDefaultSplit(groupServerId, serverModel)
                Log.d(TAG, "Server create response: ID=${result.id}")
                result
            } else {
                Log.d(TAG, "Updating existing default split ${entity.serverId} on server")
                val result = apiService.updateGroupDefaultSplit(entity.serverId, entity.serverId, serverModel)
                Log.d(TAG, "Server update response: ID=${result.id}")
                result
            }

            // Handle deleted splits
            if (entity.removedAt != null) {
                if (entity.serverId != null) {
                    try {
                        apiService.deleteGroupDefaultSplit(entity.groupId, entity.serverId)
                        Log.d(TAG, "Successfully deleted split ${entity.serverId} from server")

                        // Update local entity status
                        groupDefaultSplitDao.updateSyncStatus(entity.id, SyncStatus.LOCALLY_DELETED)

                        return Result.success(entity.copy(syncStatus = SyncStatus.LOCALLY_DELETED))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting split from server", e)
                        return Result.failure(e)
                    }
                } else {
                    // For splits that were created and deleted locally without ever reaching the server
                    groupDefaultSplitDao.updateSyncStatus(entity.id, SyncStatus.LOCALLY_DELETED)
                    return Result.success(entity.copy(syncStatus = SyncStatus.LOCALLY_DELETED))
                }
            }

            // Convert server response back to local entity
            val conversionResult = myApplication.entityServerConverter.convertGroupDefaultSplitFromServer(
                serverResult,
                entity
            )

            return when {
                conversionResult.isSuccess -> {
                    val localEntity = conversionResult.getOrThrow()
                    Log.d(TAG, "Successfully converted server response to local entity")

                    // Update the local entity
                    val updatedEntity = localEntity.copy(
                        id = entity.id,
                        serverId = serverResult.id,
                        syncStatus = SyncStatus.SYNCED
                    )

                    groupDefaultSplitDao.insertOrUpdateDefaultSplit(updatedEntity)
                    Log.d(TAG, "Successfully updated local entity with server data")

                    Result.success(updatedEntity)
                }
                else -> {
                    val error = conversionResult.exceptionOrNull()
                        ?: Exception("Unknown error converting server response")
                    Log.e(TAG, "Error converting server result to local entity", error)
                    // Mark as failed sync
                    groupDefaultSplitDao.updateSyncStatus(entity.id, SyncStatus.SYNC_FAILED)
                    Result.failure(error)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing default split to server", e)
            groupDefaultSplitDao.updateSyncStatus(entity.id, SyncStatus.SYNC_FAILED)
            return Result.failure(e)
        }
    }

    override suspend fun getServerChanges(since: Long): List<GroupDefaultSplit> {
        try {
            Log.d(TAG, "Fetching group default splits since $since")
            return apiService.getGroupDefaultSplitsSince(since)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching server changes", e)
            throw e
        }
    }

    override suspend fun applyServerChange(serverEntity: GroupDefaultSplit) {
        try {
            Log.d(TAG, "Processing server default split: ${serverEntity.id}")

            // Check if we already have this entity
            val existingEntity = groupDefaultSplitDao.getDefaultSplitByServerId(serverEntity.id)

            // Convert server entity to local entity
            val conversionResult = myApplication.entityServerConverter.convertGroupDefaultSplitFromServer(
                serverEntity,
                existingEntity
            )

            if (conversionResult.isFailure) {
                Log.e(TAG, "Failed to convert server entity", conversionResult.exceptionOrNull())
                return
            }

            val localEntity = conversionResult.getOrThrow()

            when {
                existingEntity == null -> {
                    // New split - insert
                    Log.d(TAG, "Inserting new split from server")
                    groupDefaultSplitDao.insertOrUpdateDefaultSplit(
                        localEntity.copy(syncStatus = SyncStatus.SYNCED)
                    )
                }
                DateUtils.isUpdateNeeded(
                    serverEntity.updatedAt,
                    existingEntity.updatedAt,
                    "DefaultSplit-${serverEntity.id}"
                ) -> {
                    // Update existing
                    Log.d(TAG, "Updating existing split from server")
                    groupDefaultSplitDao.insertOrUpdateDefaultSplit(
                        localEntity.copy(
                            id = existingEntity.id,
                            syncStatus = SyncStatus.SYNCED
                        )
                    )
                }
                else -> {
                    Log.d(TAG, "Local split is up to date")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying server change", e)
        }
    }

    companion object {
        private const val TAG = "GroupDefaultSplitSyncManager"
    }
}