package com.splitter.splittr.data.sync

import android.content.Context
import android.util.Log
import com.splitter.splittr.MyApplication
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.GroupDao
import com.splitter.splittr.data.local.dao.GroupMemberDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.local.dao.UserDao
import com.splitter.splittr.data.local.entities.GroupEntity
import com.splitter.splittr.data.model.Group
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.managers.GroupMemberSyncManager
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.DateUtils
import com.splitter.splittr.utils.ImageUtils
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first

class GroupSyncManager(
    private val groupDao: GroupDao,
    private val userDao: UserDao,
    private val apiService: ApiService,
    syncMetadataDao: SyncMetadataDao,
    dispatchers: CoroutineDispatchers,
    private val context: Context,
    private val groupMemberSyncManager: GroupMemberSyncManager
) : OptimizedSyncManager<GroupEntity, Group>(syncMetadataDao, dispatchers) {

    override val entityType = "groups"
    override val batchSize = 20

    val myApplication = context.applicationContext as MyApplication

    override suspend fun getLocalChanges(): List<GroupEntity> =
        groupDao.getUnsyncedGroups().first()

    override suspend fun syncToServer(entity: GroupEntity): Result<GroupEntity> = try {
        Log.d(TAG, "Starting syncToServer for group: ${entity.id}")

        // First convert local entity to server model for API call
        val serverModel = myApplication.entityServerConverter.convertGroupToServer(entity)
            .getOrElse {
                Log.e(TAG, "Failed to convert local entity to server model", it)
                return Result.failure(it)
            }

        // Make the appropriate API call based on server ID
        val serverResult = if (entity.serverId == null) {
            Log.d(TAG, "Creating new group on server")
            val result = apiService.createGroup(serverModel)
            Log.d(TAG, "Server create response: ID=${result.id}")
            result
        } else {
            Log.d(TAG, "Updating existing group ${entity.id} on server")
            val result = apiService.updateGroup(entity.serverId, serverModel)
            Log.d(TAG, "Server update response: ID=${result.id}")
            result
        }

        // Convert server response back to local entity
        myApplication.entityServerConverter.convertGroupFromServer(
            serverResult,
            groupDao.getGroupByIdSync(entity.id)
        ).onSuccess { localGroup ->
            Log.d(TAG, """
            About to update database with:
            ID: ${localGroup.id}
            Server ID: ${localGroup.serverId}
            Sync Status: SYNCED
        """.trimIndent())

            groupDao.runInTransaction {
                // First ensure we have the latest state
                val currentEntity = groupDao.getGroupByIdSync(entity.id)
                    ?: throw IllegalStateException("Group ${entity.id} not found")

                // Create updated entity with all necessary fields
                val updatedEntity = currentEntity.copy(
                    serverId = serverResult.id,
                    name = serverResult.name,
                    description = serverResult.description,
                    syncStatus = SyncStatus.SYNCED,
                    updatedAt = serverResult.updatedAt,
                    inviteLink = serverResult.inviteLink,
                    defaultCurrency = serverResult.defaultCurrency
                )

                // Use updateGroup instead of insertGroup to ensure we update the existing record
                groupDao.updateGroup(updatedEntity)
                groupDao.updateGroupSyncStatus(entity.id, SyncStatus.SYNCED)

                // Verify the update immediately within the transaction
                val verifiedEntity = groupDao.getGroupByIdSync(entity.id)
                Log.d(TAG, """
                Verification within transaction:
                ID: ${verifiedEntity?.id}
                Server ID: ${verifiedEntity?.serverId}
                Sync Status: ${verifiedEntity?.syncStatus}
            """.trimIndent())
            }
        }.map { localGroup ->
            // Do one final verification after transaction
            val finalEntity = groupDao.getGroupByIdSync(entity.id)
            Log.d(TAG, """
            Final verification:
            ID: ${finalEntity?.id}
            Server ID: ${finalEntity?.serverId}
            Sync Status: ${finalEntity?.syncStatus}
        """.trimIndent())

            localGroup
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error syncing group to server", e)
        groupDao.updateGroupSyncStatus(entity.id, SyncStatus.SYNC_FAILED)
        Result.failure(e)
    }

    override suspend fun getServerChanges(since: Long): List<Group> {
        try {
            val userId = getUserIdFromPreferences(context)
                ?: throw IllegalStateException("User ID not found")

            // Get the server ID from the local user ID
            val localUser = userDao.getUserByIdDirect(userId)
                ?: throw IllegalStateException("User not found in local database")

            val serverUserId = localUser.serverId
                ?: throw IllegalStateException("No server ID found for user $userId")

            Log.d(TAG, "Fetching groups since $since for server user ID: $serverUserId")
            return apiService.getGroupsSince(since, serverUserId)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching server changes", e)
            throw e
        }
    }

    override suspend fun applyServerChange(serverEntity: Group) {
        // First get the existing local group BEFORE any updates
        val existingGroup = groupDao.getGroupByServerId(serverEntity.id)

        // Convert server group to local entity
        val localGroup = myApplication.entityServerConverter.convertGroupFromServer(
            serverEntity,
            existingGroup
        ).getOrNull() ?: throw Exception("Failed to convert server group")

        when {
            existingGroup == null -> {
                Log.d(TAG, "Inserting new group from server: ${serverEntity.id}")
                val insertedGroup = localGroup.copy(syncStatus = SyncStatus.SYNCED)
                groupDao.insertGroup(insertedGroup)

                // Download image for new group if it exists
                if (serverEntity.groupImg != null) {
                    downloadAndSaveGroupImage(insertedGroup)
                }

                groupMemberSyncManager.performSync()
            }
            DateUtils.isUpdateNeeded(
                serverEntity.updatedAt,
                existingGroup.updatedAt,  // Use existingGroup instead of localGroup
                "Group-${serverEntity.id}"
            ) -> {
                Log.d(TAG, "Updating existing group from server: ${serverEntity.id}")

                // Check if image has changed
                val needsImageUpdate = existingGroup.groupImg != serverEntity.groupImg ||
                        existingGroup.localImagePath == null

                val updatedGroup = localGroup.copy(
                    id = existingGroup.id,
                    syncStatus = SyncStatus.SYNCED
                )
                groupDao.updateGroup(updatedGroup)

                if (needsImageUpdate && serverEntity.groupImg != null) {
                    downloadAndSaveGroupImage(updatedGroup)
                }

                groupMemberSyncManager.performSync()
            }
            else -> {
                Log.d(TAG, "Local group ${serverEntity.id} is up to date")
                // Still check if we need to download the image
                if (serverEntity.groupImg != null && existingGroup.localImagePath == null) {
                    downloadAndSaveGroupImage(existingGroup)
                }
            }
        }
    }

    private suspend fun downloadAndSaveGroupImage(group: GroupEntity) {
        try {
            Log.d(TAG, "Downloading image for group ${group.id}")
            val imageUrl = ImageUtils.getFullImageUrl(group.groupImg) ?: return
            val localFileName = ImageUtils.downloadAndSaveImage(context, imageUrl) ?: return
            val localPath = ImageUtils.getLocalImagePath(context, localFileName)

            groupDao.updateLocalImageInfo(
                groupId = group.id,
                localPath = localPath,
                lastModified = DateUtils.getCurrentTimestamp()
            )
            Log.d(TAG, "Successfully downloaded and saved image for group ${group.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading group image for group ${group.id}", e)
        }
    }

    companion object {
        const val TAG = "GroupSyncManager"
        private const val LOCAL_ID_THRESHOLD = 1000000
    }
}