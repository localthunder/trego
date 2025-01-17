package com.splitter.splittr.data.sync.managers

import android.content.Context
import android.util.Log
import com.splitter.splittr.MyApplication
import com.splitter.splittr.data.local.dao.UserGroupArchiveDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.local.dao.UserDao
import com.splitter.splittr.data.local.dao.GroupDao
import com.splitter.splittr.data.local.entities.UserGroupArchiveEntity
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.OptimizedSyncManager
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.DateUtils
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first

class UserGroupArchiveSyncManager(
    private val userGroupArchiveDao: UserGroupArchiveDao,
    private val userDao: UserDao,
    private val groupDao: GroupDao,
    private val apiService: ApiService,
    syncMetadataDao: SyncMetadataDao,
    dispatchers: CoroutineDispatchers,
    private val context: Context
) : OptimizedSyncManager<UserGroupArchiveEntity>(syncMetadataDao, dispatchers) {

    override val entityType = "user_group_archives"
    override val batchSize = 50

    val myApplication = context.applicationContext as MyApplication

    override suspend fun getLocalChanges(): List<UserGroupArchiveEntity> =
        userGroupArchiveDao.getUnsyncedArchives().first()

    override suspend fun syncToServer(entity: UserGroupArchiveEntity): Result<UserGroupArchiveEntity> = try {
        // Convert local IDs to server IDs
        val serverIds = myApplication.entityServerConverter.convertUserGroupArchiveToServer(entity)
            .getOrElse {
                Log.e(TAG, "Failed to convert IDs for server sync", it)
                return Result.failure(it)
            }

        // Attempt server sync
        when (entity.syncStatus) {
            SyncStatus.PENDING_SYNC -> {
                apiService.archiveGroup(
                    groupId = serverIds["group_id"] as Int,
                    userId = serverIds["user_id"] as Int
                )
            }
            SyncStatus.SYNC_FAILED -> {
                // If sync failed previously, check if the archive exists on server
                // If not, create it. If yes, ensure it's in sync
                apiService.archiveGroup(
                    groupId = serverIds["group_id"] as Int,
                    userId = serverIds["user_id"] as Int
                )
            }
            else -> { /* No action needed for SYNCED status */ }
        }

        // Update local sync status
        userGroupArchiveDao.updateSyncStatus(entity.userId, entity.groupId, SyncStatus.SYNCED)
        Result.success(entity.copy(syncStatus = SyncStatus.SYNCED))
    } catch (e: Exception) {
        Log.e(TAG, "Error syncing archive to server", e)
        userGroupArchiveDao.updateSyncStatus(entity.userId, entity.groupId, SyncStatus.SYNC_FAILED)
        Result.failure(e)
    }

    override suspend fun getServerChanges(since: Long): List<UserGroupArchiveEntity> {
        val userId = getUserIdFromPreferences(context)
            ?: throw IllegalStateException("User ID not found")

        // Get the server ID from the local user ID
        val localUser = userDao.getUserByIdDirect(userId)
            ?: throw IllegalStateException("User not found in local database")

        val serverUserId = localUser.serverId
            ?: throw IllegalStateException("No server ID found for user $userId")

        // Get all archives for the user
        val serverArchives = mutableListOf<UserGroupArchiveEntity>()

        // Get all groups and check their archive status
        val groups = groupDao.getAllGroups().first()
        for (group in groups) {
            try {
                val isArchived = apiService.isGroupArchived(groupId = group.id, userId = serverUserId)
                if (isArchived) {
                    serverArchives.add(
                        UserGroupArchiveEntity(
                            userId = userId,
                            groupId = group.id,
                            archivedAt = DateUtils.getCurrentTimestamp(),
                            syncStatus = SyncStatus.SYNCED
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking archive status for group ${group.id}", e)
            }
        }

        return serverArchives
    }

    override suspend fun applyServerChange(serverEntity: UserGroupArchiveEntity) {
        try {
            val existingArchive = userGroupArchiveDao.getArchive(
                serverEntity.userId,
                serverEntity.groupId
            ).first()

            when {
                existingArchive == null -> {
                    Log.d(TAG, "Inserting new archive from server")
                    userGroupArchiveDao.insertArchive(serverEntity)
                }
                DateUtils.isUpdateNeeded(
                    serverEntity.archivedAt,
                    existingArchive.archivedAt,
                    "Archive-${serverEntity.userId}-${serverEntity.groupId}"
                ) -> {
                    Log.d(TAG, "Updating existing archive from server")
                    userGroupArchiveDao.insertArchive(serverEntity)
                }
                else -> {
                    Log.d(TAG, "Local archive is up to date")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying server archive", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "UserGroupArchiveSyncMgr"
    }
}