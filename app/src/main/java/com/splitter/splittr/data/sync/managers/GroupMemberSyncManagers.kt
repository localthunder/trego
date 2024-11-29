package com.splitter.splittr.data.sync.managers

import android.content.Context
import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toGroupMember
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.GroupDao
import com.splitter.splittr.data.local.dao.GroupMemberDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.local.dataClasses.GroupMemberWithGroupResponse
import com.splitter.splittr.data.model.GroupMember
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.GroupSyncManager
import com.splitter.splittr.data.sync.OptimizedSyncManager
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.DateUtils
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first

class GroupMemberSyncManager(
    private val groupMemberDao: GroupMemberDao,
    private val groupDao: GroupDao,
    private val apiService: ApiService,
    syncMetadataDao: SyncMetadataDao,
    dispatchers: CoroutineDispatchers,
    private val context: Context
) : OptimizedSyncManager<GroupMemberWithGroupResponse>(syncMetadataDao, dispatchers) {

    override val entityType = "group_members"
    override val batchSize = 50

    override suspend fun getLocalChanges(): List<GroupMemberWithGroupResponse> =
        groupMemberDao.getUnsyncedGroupMembers().first().map { entity ->
            // Convert GroupMemberEntity to GroupMemberWithGroupResponse
            GroupMemberWithGroupResponse(
                id = entity.id,
                group_id = entity.groupId,
                user_id = entity.userId,
                created_at = entity.createdAt,
                updated_at = entity.updatedAt,
                removed_at = entity.removedAt,
                group = groupDao.getGroupByIdSync(entity.groupId)?.toModel()
            )
        }

    override suspend fun syncToServer(entity: GroupMemberWithGroupResponse): Result<GroupMemberWithGroupResponse> = try {
        val serverResponse = if (entity.id > LOCAL_ID_THRESHOLD) {
            Log.d(TAG, "Creating new group member on server")
            apiService.addMemberToGroup(entity.group_id, entity.toGroupMember())
        } else {
            Log.d(TAG, "Updating existing group member ${entity.id} on server")
            apiService.updateGroupMember(entity.group_id, entity.id, entity.toGroupMember()).data
        }

        groupMemberDao.runInTransaction {
            // Convert the server response back to GroupMemberWithGroupResponse format
            val syncedEntity = GroupMemberWithGroupResponse(
                id = serverResponse.id,
                group_id = serverResponse.groupId,
                user_id = serverResponse.userId,
                created_at = serverResponse.createdAt,
                updated_at = serverResponse.updatedAt,
                removed_at = serverResponse.removedAt,
                group = entity.group // Keep the original group data
            ).toEntity(SyncStatus.SYNCED)

            groupMemberDao.updateGroupMember(syncedEntity)
            groupMemberDao.updateGroupMemberSyncStatus(syncedEntity.id, SyncStatus.SYNCED)
            Log.d(TAG, "Updated local group member ${syncedEntity.id} with sync status SYNCED")
        }

        Result.success(entity)
    } catch (e: Exception) {
        Log.e(TAG, "Error syncing group member to server", e)
        entity.id.let { id ->
            try {
                groupMemberDao.updateGroupMemberSyncStatus(id, SyncStatus.SYNC_FAILED)
            } catch (e2: Exception) {
                Log.e(TAG, "Error updating sync status for group member $id", e2)
            }
        }
        Result.failure(e)
    }

    override suspend fun getServerChanges(since: Long): List<GroupMemberWithGroupResponse> {
        val userId = getUserIdFromPreferences(context) ?: throw IllegalStateException("User ID not found")
        Log.d(GroupSyncManager.TAG, "Fetching group members since $since")
        return apiService.getGroupMembersSince(since, userId)
    }

    override suspend fun applyServerChange(serverEntity: GroupMemberWithGroupResponse) {
        groupMemberDao.runInTransaction {
            // First sync the embedded group
            serverEntity.group?.let { group ->
                val groupEntity = group
                    .copy(updatedAt = DateUtils.standardizeTimestamp(group.updatedAt))
                    .toEntity(SyncStatus.SYNCED)
                groupDao.insertGroup(groupEntity)
            }

            // Then handle the member
            val localEntity = groupMemberDao.getGroupMemberByIdSync(serverEntity.id)
            when {
                localEntity == null -> {
                    Log.d(TAG, "Inserting new group member from server: ${serverEntity.id}")
                    groupMemberDao.insertGroupMember(
                        serverEntity
                            .copy(updated_at = DateUtils.standardizeTimestamp(serverEntity.updated_at))
                            .toEntity(SyncStatus.SYNCED)
                    )
                }
                DateUtils.isUpdateNeeded(
                    serverEntity.updated_at,
                    localEntity.updatedAt,
                    "GroupMember-${serverEntity.id}-Group-${serverEntity.group?.id}"
                ) -> {
                    Log.d(TAG, "Updating existing group member from server: ${serverEntity.id}")
                    groupMemberDao.updateGroupMember(
                        serverEntity
                            .copy(updated_at = DateUtils.standardizeTimestamp(serverEntity.updated_at))
                            .toEntity(SyncStatus.SYNCED)
                    )
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