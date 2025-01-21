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
import com.splitter.splittr.data.model.Group
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.managers.GroupMemberSyncManager
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.DateUtils
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
) : OptimizedSyncManager<Group, Group>(syncMetadataDao, dispatchers) {

    override val entityType = "groups"
    override val batchSize = 20

    val myApplication = context.applicationContext as MyApplication

    override suspend fun getLocalChanges(): List<Group> =
        groupDao.getUnsyncedGroups().first().mapNotNull { groupEntity ->
            myApplication.entityServerConverter.convertGroupToServer(groupEntity).getOrNull()
        }

    override suspend fun syncToServer(entity: Group): Result<Group> = try {
        val result = if (entity.id > LOCAL_ID_THRESHOLD) {
            Log.d(TAG, "Creating new group on server")
            apiService.createGroup(entity)
        } else {
            Log.d(TAG, "Updating existing group ${entity.id} on server")
            apiService.updateGroup(entity.id, entity)
        }

        // Convert server response back to local entity and save
        myApplication.entityServerConverter.convertGroupFromServer(
            result,
            groupDao.getGroupByIdSync(entity.id)
        ).onSuccess { localGroup ->
            groupDao.insertGroup(localGroup.copy(syncStatus = SyncStatus.SYNCED))
        }

        Result.success(result)
    } catch (e: Exception) {
        Log.e(TAG, "Error syncing group to server", e)
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
        // Convert server group to local entity
        val localGroup = myApplication.entityServerConverter.convertGroupFromServer(
            serverEntity,
            groupDao.getGroupByIdSync(serverEntity.id)
        ).getOrNull() ?: throw Exception("Failed to convert server group")

        when {
            localGroup.id == 0 -> {
                Log.d(TAG, "Inserting new group from server: ${serverEntity.id}")
                groupDao.insertGroup(
                    localGroup.copy(
                        updatedAt = DateUtils.standardizeTimestamp(serverEntity.updatedAt),
                        syncStatus = SyncStatus.SYNCED
                    )
                )
                groupMemberSyncManager.performSync()
            }
            DateUtils.isUpdateNeeded(
                serverEntity.updatedAt,
                localGroup.updatedAt,
                "Group-${serverEntity.id}"
            ) -> {
                groupDao.updateGroup(
                    localGroup.copy(
                        updatedAt = DateUtils.standardizeTimestamp(serverEntity.updatedAt),
                        syncStatus = SyncStatus.SYNCED
                    )
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