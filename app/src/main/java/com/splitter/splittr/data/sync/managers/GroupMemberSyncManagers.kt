package com.splitter.splittr.data.sync.managers

import android.content.Context
import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.GroupMemberDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.model.GroupMember
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.GroupSyncManager
import com.splitter.splittr.data.sync.OptimizedSyncManager
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first

class GroupMemberSyncManager(
    private val groupMemberDao: GroupMemberDao,
    private val apiService: ApiService,
    syncMetadataDao: SyncMetadataDao,
    dispatchers: CoroutineDispatchers,
    private val context: Context
) : OptimizedSyncManager<GroupMember>(syncMetadataDao, dispatchers) {

    override val entityType = "group_members"
    override val batchSize = 50

    override suspend fun getLocalChanges(): List<GroupMember> =
        groupMemberDao.getUnsyncedGroupMembers().first().map { it.toModel() }

    override suspend fun syncToServer(entity: GroupMember): Result<GroupMember> = try {
        val result = if (entity.id > LOCAL_ID_THRESHOLD) {
            Log.d(TAG, "Creating new group member on server")
            apiService.addMemberToGroup(entity.groupId, entity)
        } else {
            Log.d(TAG, "Updating existing group member ${entity.id} on server")
            apiService.updateGroupMember(entity.id, entity)
        }
        Result.success(result)
    } catch (e: Exception) {
        Log.e(TAG, "Error syncing group member to server", e)
        Result.failure(e)
    }

    override suspend fun getServerChanges(since: Long): List<GroupMember> {
        val userId = getUserIdFromPreferences(context) ?: throw IllegalStateException("User ID not found")
        Log.d(GroupSyncManager.TAG, "Fetching group members since $since")
        return apiService.getGroupMembersSince(since, userId)
    }

    override suspend fun applyServerChange(serverEntity: GroupMember) {
        groupMemberDao.runInTransaction {
            val localEntity = groupMemberDao.getGroupMemberById(serverEntity.id).first()
            when {
                localEntity == null -> {
                    Log.d(TAG, "Inserting new group member from server: ${serverEntity.id}")
                    groupMemberDao.insertGroupMember(serverEntity.toEntity(SyncStatus.SYNCED))
                }
                serverEntity.updatedAt > localEntity.updatedAt -> {
                    Log.d(TAG, "Updating existing group member from server: ${serverEntity.id}")
                    groupMemberDao.updateGroupMember(serverEntity.toEntity(SyncStatus.SYNCED))
                }
                else -> {
                    Log.d(TAG, "Local group member ${serverEntity.id} is up to date")
                }
            }
        }
    }

    companion object {
        private const val TAG = "GroupMemberSyncManager"
        private const val LOCAL_ID_THRESHOLD = 90000000
    }
}