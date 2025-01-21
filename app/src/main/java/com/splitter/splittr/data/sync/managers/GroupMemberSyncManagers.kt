package com.splitter.splittr.data.sync.managers

import android.content.Context
import android.util.Log
import com.splitter.splittr.MyApplication
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toGroupMember
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.GroupDao
import com.splitter.splittr.data.local.dao.GroupMemberDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.local.dao.UserDao
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
    private val userDao: UserDao,
    private val apiService: ApiService,
    syncMetadataDao: SyncMetadataDao,
    dispatchers: CoroutineDispatchers,
    private val context: Context
) : OptimizedSyncManager<GroupMemberWithGroupResponse, GroupMemberWithGroupResponse>(syncMetadataDao, dispatchers) {

    override val entityType = "group_members"
    override val batchSize = 50

    val myApplication = context.applicationContext as MyApplication

    override suspend fun getLocalChanges(): List<GroupMemberWithGroupResponse> =
        groupMemberDao.getUnsyncedGroupMembers().first().mapNotNull { entity ->
            // Convert member to server format
            val serverMember = myApplication.entityServerConverter.convertGroupMemberToServer(entity).getOrNull()
                ?: return@mapNotNull null

            // Get group data
            val group = groupDao.getGroupByIdSync(entity.groupId)?.let { groupEntity ->
                myApplication.entityServerConverter.convertGroupToServer(groupEntity).getOrNull()
            }

            GroupMemberWithGroupResponse(
                id = serverMember.id,
                group_id = serverMember.groupId,
                user_id = serverMember.userId,
                created_at = serverMember.createdAt,
                updated_at = serverMember.updatedAt,
                removed_at = serverMember.removedAt,
                group = group
            )
        }

    override suspend fun syncToServer(entity: GroupMemberWithGroupResponse): Result<GroupMemberWithGroupResponse> = try {
        // Entity is already in server format from getLocalChanges()
        val serverResponse = if (entity.id > LOCAL_ID_THRESHOLD) {
            Log.d(TAG, "Creating new group member on server")
            apiService.addMemberToGroup(entity.group_id, entity.toGroupMember())
        } else {
            Log.d(TAG, "Updating existing group member ${entity.id} on server")
            apiService.updateGroupMember(entity.group_id, entity.id, entity.toGroupMember()).data
        }

        groupMemberDao.runInTransaction {
            // Convert server response back to local entity
            val localMember = myApplication.entityServerConverter.convertGroupMemberFromServer(
                serverResponse,
                groupMemberDao.getGroupMemberByIdSync(entity.id)
            ).getOrNull() ?: throw Exception("Failed to convert server member to local entity")

            groupMemberDao.updateGroupMember(localMember)
            groupMemberDao.updateGroupMemberSyncStatus(localMember.id, SyncStatus.SYNCED)
            Log.d(TAG, "Updated local group member ${localMember.id} with sync status SYNCED")
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
        // Get the server ID from the local user ID
        val localUser = userDao.getUserByIdDirect(userId)
            ?: throw IllegalStateException("User not found in local database")

        val serverUserId = localUser.serverId
            ?: throw IllegalStateException("No server ID found for user $userId")

        Log.d(TAG, "Fetching group members since $since")
        return apiService.getGroupMembersSince(since, serverUserId)
    }

    override suspend fun applyServerChange(serverEntity: GroupMemberWithGroupResponse) {
        groupMemberDao.runInTransaction {
            // First sync the embedded group if present
            serverEntity.group?.let { serverGroup ->
                val localGroup = myApplication.entityServerConverter.convertGroupFromServer(
                    serverGroup,
                    groupDao.getGroupByIdSync(serverGroup.id)
                ).getOrNull()?.copy(syncStatus = SyncStatus.SYNCED)

                localGroup?.let { groupDao.insertGroup(it) }
            }

            // Convert server member to local entity
            val localMember = myApplication.entityServerConverter.convertGroupMemberFromServer(
                serverEntity.toGroupMember(),
                groupMemberDao.getGroupMemberByServerId(serverEntity.id)  // Look up by server ID instead
            ).getOrNull() ?: throw Exception("Failed to convert server member")

            when {
                localMember.id == 0 -> {
                    Log.d(TAG, "Inserting new group member from server: ${serverEntity.id}")
                    groupMemberDao.insertGroupMember(localMember)
                }
                DateUtils.isUpdateNeeded(
                    serverEntity.updated_at,
                    localMember.updatedAt,
                    "GroupMember-${localMember.id}-Group-${serverEntity.group?.id}"
                ) -> {
                    Log.d(TAG, "Updating existing group member from server: ${serverEntity.id}")
                    groupMemberDao.updateGroupMember(localMember)
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