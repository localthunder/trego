package com.splitter.splittr.data.sync

import android.content.Context
import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.GroupDao
import com.splitter.splittr.data.local.dao.GroupMemberDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.model.Group
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.managers.GroupMemberSyncManager
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.DateUtils
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first

class GroupSyncManager(
    private val groupDao: GroupDao,
    private val apiService: ApiService,
    syncMetadataDao: SyncMetadataDao,
    dispatchers: CoroutineDispatchers,
    private val context: Context,
    private val groupMemberSyncManager: GroupMemberSyncManager

) : OptimizedSyncManager<Group>(syncMetadataDao, dispatchers) {

    override val entityType = "groups"
    override val batchSize = 20

    override suspend fun getLocalChanges(): List<Group> =
        groupDao.getUnsyncedGroups().first().map { it.toModel() }

    override suspend fun syncToServer(entity: Group): Result<Group> = try {
        val result = if (entity.id > LOCAL_ID_THRESHOLD) {
            Log.d(TAG, "Creating new group on server")
            apiService.createGroup(entity)
        } else {
            Log.d(TAG, "Updating existing group ${entity.id} on server")
            apiService.updateGroup(entity.id, entity)
        }
        Result.success(result)
    } catch (e: Exception) {
        Log.e(TAG, "Error syncing group to server", e)
        Result.failure(e)
    }

    override suspend fun getServerChanges(since: Long): List<Group> {
        val userId = getUserIdFromPreferences(context) ?: throw IllegalStateException("User ID not found")
        Log.d(TAG, "Fetching groups since $since")
        return apiService.getGroupsSince(since, userId)
    }

    override suspend fun applyServerChange(serverEntity: Group) {
        val localEntity = groupDao.getGroupByIdSync(serverEntity.id)  // Add this sync query method

        when {
            localEntity == null -> {
                Log.d(TAG, "Inserting new group from server: ${serverEntity.id}")
                groupDao.insertGroup(
                    serverEntity
                        .copy(updatedAt = DateUtils.standardizeTimestamp(serverEntity.updatedAt))
                        .toEntity(SyncStatus.SYNCED)
                )
                groupMemberSyncManager.performSync()
            }
                DateUtils.isUpdateNeeded(
                    serverEntity.updatedAt,
                    localEntity.updatedAt,
                    "Group-${serverEntity.id}"
                ) -> {

                    groupDao.updateGroup(
                        serverEntity
                            .copy(updatedAt = DateUtils.standardizeTimestamp(serverEntity.updatedAt))
                            .toEntity(SyncStatus.SYNCED)
                    )

                    // Trigger member sync after updating group
                    Log.d(TAG, "Triggering member sync for updated group: ${serverEntity.id}")
                    groupMemberSyncManager.performSync()
                }
                else -> {
                    Log.d(TAG, "Local group ${serverEntity.id} is up to date")
                }
            }
        }

    companion object {
        const val TAG = "GroupSyncManager"
        private const val LOCAL_ID_THRESHOLD = 90000000
    }
}