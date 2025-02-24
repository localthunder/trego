package com.helgolabs.trego.data.sync.managers

import android.content.Context
import android.util.Log
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.dao.UserGroupArchiveDao
import com.helgolabs.trego.data.local.dao.SyncMetadataDao
import com.helgolabs.trego.data.local.dao.UserDao
import com.helgolabs.trego.data.local.dao.GroupDao
import com.helgolabs.trego.data.local.entities.UserGroupArchiveEntity
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.sync.OptimizedSyncManager
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first

class UserGroupArchiveSyncManager(
    private val userGroupArchiveDao: UserGroupArchiveDao,
    private val userDao: UserDao,
    private val groupDao: GroupDao,
    private val apiService: ApiService,
    syncMetadataDao: SyncMetadataDao,
    dispatchers: CoroutineDispatchers,
    private val context: Context
) : OptimizedSyncManager<UserGroupArchiveEntity, UserGroupArchiveEntity>(syncMetadataDao, dispatchers) {

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

        Log.d(TAG, "Local user ID: $userId")

        val localUser = userDao.getUserByIdDirect(userId)
            ?: throw IllegalStateException("User not found in local database")

        Log.d(TAG, "Found local user: ${localUser.userId}, server ID: ${localUser.serverId}")

        val serverUserId = localUser.serverId
            ?: throw IllegalStateException("No server ID found for user $userId")

        // Get all archived group server IDs for the user
        val serverArchives = apiService.getArchivedGroups(serverUserId)
        Log.d(TAG, "Received server archives: $serverArchives")

        return serverArchives.mapNotNull { serverArchive ->
            Log.d(TAG, "Converting server archive: $serverArchive")
            val result = myApplication.entityServerConverter
                .convertUserGroupArchiveFromServer(serverArchive)

            if (result.isSuccess) {
                Log.d(TAG, "Successfully converted archive: ${result.getOrNull()}")
            } else {
                Log.e(TAG, "Failed to convert archive: ${result.exceptionOrNull()}")
            }

            result.getOrNull()
        }
    }

    override suspend fun applyServerChange(serverEntity: UserGroupArchiveEntity) {
        try {
            Log.d(TAG, "Starting apply server change")
            userGroupArchiveDao.runInTransaction {
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
            }
            Log.d(TAG, "Successfully completed apply server change")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying server archive", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "UserGroupArchiveSyncMgr"
    }
}