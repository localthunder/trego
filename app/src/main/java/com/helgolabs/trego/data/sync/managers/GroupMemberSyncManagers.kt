package com.helgolabs.trego.data.sync.managers

import android.content.Context
import android.util.Log
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.extensions.toEntity
import com.helgolabs.trego.data.extensions.toGroupMember
import com.helgolabs.trego.data.extensions.toModel
import com.helgolabs.trego.data.local.dao.GroupDao
import com.helgolabs.trego.data.local.dao.GroupMemberDao
import com.helgolabs.trego.data.local.dao.SyncMetadataDao
import com.helgolabs.trego.data.local.dao.UserDao
import com.helgolabs.trego.data.local.dataClasses.GroupMemberWithGroupResponse
import com.helgolabs.trego.data.local.entities.GroupMemberEntity
import com.helgolabs.trego.data.model.GroupMember
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.sync.GroupSyncManager
import com.helgolabs.trego.data.sync.OptimizedSyncManager
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.SecureLogger
import com.helgolabs.trego.utils.getUserIdFromPreferences
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
        SecureLogger.d(TAG, "Starting syncToServer for group member: ${entity.id}")

        // First convert local entity to server model for API call
        val serverModel = myApplication.entityServerConverter.convertGroupMemberToServer(entity)
            .getOrElse {
                SecureLogger.e(TAG, "Failed to convert local entity to server model", it)
                return Result.failure(it)
            }

        // Make API call based on whether this is a new or existing member
        val serverResponse = if (entity.serverId == null) {
            SecureLogger.d(TAG, "Creating new group member on server")
            apiService.addMemberToGroup(serverModel.groupId, serverModel)
        } else {
            SecureLogger.d(TAG, "Updating existing group member ${entity.id} on server")
            apiService.updateGroupMember(serverModel.groupId, entity.serverId, serverModel).data
        }

        // Convert server response back to local entity and update database
        myApplication.entityServerConverter.convertGroupMemberFromServer(
            serverResponse,
            groupMemberDao.getGroupMemberByIdSync(entity.id)
        ).onSuccess { localMember ->
            SecureLogger.d(TAG, """
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
                SecureLogger.d(TAG, """
                    Verification within transaction:
                    ID: ${verifiedEntity?.id}
                    Server ID: ${verifiedEntity?.serverId}
                    Sync Status: ${verifiedEntity?.syncStatus}
                """.trimIndent())
            }
        }.map { localMember ->
            // Final verification after transaction
            val finalEntity = groupMemberDao.getGroupMemberByIdSync(entity.id)
            SecureLogger.d(TAG, """
                Final verification:
                ID: ${finalEntity?.id}
                Server ID: ${finalEntity?.serverId}
                Sync Status: ${finalEntity?.syncStatus}
            """.trimIndent())

            localMember
        }
    } catch (e: Exception) {
        SecureLogger.e(TAG, "Error syncing group member to server", e)
        groupMemberDao.updateGroupMemberSyncStatus(entity.id, SyncStatus.SYNC_FAILED)
        Result.failure(e)
    }

    override suspend fun getServerChanges(since: Long): List<GroupMemberWithGroupResponse> {
        SecureLogger.d(TAG, "Fetching group members since $since")
        return apiService.getGroupMembersSince(since)
    }

    override suspend fun applyServerChange(serverEntity: GroupMemberWithGroupResponse) {
        try {
            groupMemberDao.runInTransaction {
                // First ensure the group exists locally
                val group = serverEntity.group
                if (group != null) {
                    // Try to convert and insert the group first
                    try {
                        val localGroup = myApplication.entityServerConverter.convertGroupFromServer(
                            group,
                            groupDao.getGroupByServerId(group.id) // Look up by server ID
                        ).getOrNull()?.copy(syncStatus = SyncStatus.SYNCED)

                        if (localGroup != null) {
                            groupDao.insertGroup(localGroup)
                            SecureLogger.d(TAG, "Ensured group exists locally: Server ID=${group.id}, Local ID=${localGroup.id}")
                        }
                    } catch (e: Exception) {
                        SecureLogger.e(TAG, "Failed to ensure group exists locally: ${e.message}")
                        // Continue anyway - we'll try to handle the member
                    }

                    // Also ensure the user exists in the database
                    try {
                        val userId = serverEntity.user_id  // Note: using snake_case as per your class definition

                        // Try to fetch the user if not already in database
                        if (userDao.getUserByServerIdSync(userId) == null) {
                            try {
                                val serverUser = apiService.getUserById(userId)
                                val localUser = myApplication.entityServerConverter.convertUserFromServer(
                                    serverUser,
                                    null,
                                    false
                                ).getOrNull()?.copy(syncStatus = SyncStatus.SYNCED)

                                if (localUser != null) {
                                    userDao.insertUser(localUser)
                                    SecureLogger.d(TAG, "Added missing user: Server ID=$userId, Local ID=${localUser.userId}")
                                }
                            } catch (e: Exception) {
                                SecureLogger.e(TAG, "Failed to fetch user $userId: ${e.message}")
                                // Continue anyway
                            }
                        }
                    } catch (e: Exception) {
                        SecureLogger.e(TAG, "Failed to ensure users exist locally: ${e.message}")
                        // Continue anyway
                    }
                }

                // Now try to convert and insert/update the group member
                try {
                    // Convert GroupMemberWithGroupResponse to GroupMember object
                    val groupMember = serverEntity.toGroupMember()

                    val localMember = myApplication.entityServerConverter.convertGroupMemberFromServer(
                        groupMember,
                        groupMemberDao.getGroupMemberByServerId(serverEntity.id)
                    ).getOrNull()

                    if (localMember != null) {
                        if (localMember.id == 0) {
                            groupMemberDao.insertGroupMember(localMember)
                            SecureLogger.d(TAG, "Inserted new group member: Server ID=${serverEntity.id}")
                        } else {
                            groupMemberDao.updateGroupMember(localMember)
                            SecureLogger.d(TAG, "Updated existing group member: Server ID=${serverEntity.id}")
                        }
                    } else {
                        SecureLogger.e(TAG, "Couldn't convert server member to local entity")
                    }
                } catch (e: Exception) {
                    SecureLogger.e(TAG, "Error handling group member: ${e.message}")
                    throw e  // Re-throw to abort transaction
                }
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to apply server change for group member", e)
            throw Exception("Failed to convert server member", e)
        }
    }

    companion object {
        private const val TAG = "GroupMemberSyncManager"
    }
}