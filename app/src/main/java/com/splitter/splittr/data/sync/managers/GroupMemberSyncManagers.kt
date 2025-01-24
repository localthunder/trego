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
import com.splitter.splittr.data.local.entities.GroupMemberEntity
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
) : OptimizedSyncManager<GroupMemberEntity, GroupMemberWithGroupResponse>(syncMetadataDao, dispatchers) {

    override val entityType = "group_members"
    override val batchSize = 50

    val myApplication = context.applicationContext as MyApplication

    override suspend fun getLocalChanges(): List<GroupMemberEntity> =
        groupMemberDao.getUnsyncedGroupMembers().first()


    override suspend fun syncToServer(entity: GroupMemberEntity): Result<GroupMemberEntity> = try {
        Log.d(TAG, "Starting syncToServer for group member: ${entity.id}")

        // First convert local entity to server model for API call
        val serverModel = myApplication.entityServerConverter.convertGroupMemberToServer(entity)
            .getOrElse {
                Log.e(TAG, "Failed to convert local entity to server model", it)
                return Result.failure(it)
            }

        // Make API call based on whether this is a new or existing member
        val serverResponse = if (entity.serverId == null) {
            Log.d(TAG, "Creating new group member on server")
            apiService.addMemberToGroup(serverModel.groupId, serverModel)
        } else {
            Log.d(TAG, "Updating existing group member ${entity.id} on server")
            apiService.updateGroupMember(serverModel.groupId, entity.serverId, serverModel).data
        }

        // Convert server response back to local entity and update database
        myApplication.entityServerConverter.convertGroupMemberFromServer(
            serverResponse,
            groupMemberDao.getGroupMemberByIdSync(entity.id)
        ).onSuccess { localMember ->
            Log.d(TAG, """
                About to update database with:
                ID: ${localMember.id}
                Server ID: ${localMember.serverId}
                Sync Status: SYNCED
            """.trimIndent())

            groupMemberDao.runInTransaction {
                // Get current state
                val currentEntity = groupMemberDao.getGroupMemberByIdSync(entity.id)
                    ?: throw IllegalStateException("Group member ${entity.id} not found")

                // Create updated entity
                val updatedEntity = currentEntity.copy(
                    serverId = serverResponse.id,
                    syncStatus = SyncStatus.SYNCED,
                    updatedAt = serverResponse.updatedAt,
                    removedAt = serverResponse.removedAt
                )

                // Update the existing record
                groupMemberDao.updateGroupMember(updatedEntity)
                groupMemberDao.updateGroupMemberSyncStatus(entity.id, SyncStatus.SYNCED)


                // Verify update within transaction
                val verifiedEntity = groupMemberDao.getGroupMemberByIdSync(entity.id)
                Log.d(TAG, """
                    Verification within transaction:
                    ID: ${verifiedEntity?.id}
                    Server ID: ${verifiedEntity?.serverId}
                    Sync Status: ${verifiedEntity?.syncStatus}
                """.trimIndent())
            }
        }.map { localMember ->
            // Final verification after transaction
            val finalEntity = groupMemberDao.getGroupMemberByIdSync(entity.id)
            Log.d(TAG, """
                Final verification:
                ID: ${finalEntity?.id}
                Server ID: ${finalEntity?.serverId}
                Sync Status: ${finalEntity?.syncStatus}
            """.trimIndent())

            localMember
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error syncing group member to server", e)
        groupMemberDao.updateGroupMemberSyncStatus(entity.id, SyncStatus.SYNC_FAILED)
        Result.failure(e)
    }

    override suspend fun getServerChanges(since: Long): List<GroupMemberWithGroupResponse> {

        val userId = getUserIdFromPreferences(context)
            ?: throw IllegalStateException("User ID not found")

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
    }
}