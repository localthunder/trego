package com.splitter.splittr.data.sync.managers

import android.content.Context
import android.util.Log
import com.splitter.splittr.MyApplication
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.UserDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.local.entities.UserEntity
import com.splitter.splittr.data.model.GroupMember
import com.splitter.splittr.data.model.User
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.GroupSyncManager
import com.splitter.splittr.data.sync.OptimizedSyncManager
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.utils.ConflictResolution
import com.splitter.splittr.utils.ConflictResolver
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.DateUtils
import com.splitter.splittr.utils.NetworkUtils
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first

class UserSyncManager(
    private val userDao: UserDao,
    private val apiService: ApiService,
    syncMetadataDao: SyncMetadataDao,
    dispatchers: CoroutineDispatchers,
    private val context: Context
) : OptimizedSyncManager<User>(syncMetadataDao, dispatchers) {

    override val entityType = "users"
    override val batchSize = 50

    val myApplication = context.applicationContext as MyApplication

    override suspend fun getLocalChanges(): List<User> =
        userDao.getUnsyncedUsers().first().mapNotNull { userEntity ->
            myApplication.entityServerConverter.convertUserToServer(userEntity).getOrNull()
        }

    override suspend fun syncToServer(entity: User): Result<User> {
        return try {
            Log.d(TAG, "Syncing user to server")
            val currentUserId = getUserIdFromPreferences(context)
            val isCurrentUser = entity.userId == currentUserId

            // Get the local entity to check server ID
            val localEntity = userDao.getUserByIdDirect(entity.userId)
                ?: return Result.failure(Exception("Local entity not found"))

            Log.d(TAG, """
                Syncing user:
                Local ID: ${entity.userId}
                Server ID: ${localEntity.serverId}
                Is new user: ${localEntity.serverId == null}
                Is current user: $isCurrentUser
            """.trimIndent())

            val serverUser = if (localEntity.serverId == null) {
                Log.d(TAG, "Creating new user on server")
                apiService.createUser(entity)
            } else {
                Log.d(TAG, "Updating existing user on server: ${localEntity.serverId}")
                apiService.updateUser(localEntity.serverId, entity)
            }

            // Convert server response back to local entity
            myApplication.entityServerConverter.convertUserFromServer(
                serverUser,
                localEntity,
                isCurrentUser
            ).onSuccess { updatedLocalEntity ->
                userDao.runInTransaction {
                    userDao.updateUserDirect(updatedLocalEntity)
                    userDao.updateUserSyncStatus(entity.userId, SyncStatus.SYNCED)
                }
            }

            Result.success(serverUser)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing user to server", e)
            userDao.updateUserSyncStatus(entity.userId, SyncStatus.SYNC_FAILED)
            Result.failure(e)
        }
    }

    override suspend fun getServerChanges(since: Long): List<User> {
        val userId = getUserIdFromPreferences(context)
            ?: throw IllegalStateException("User ID not found")

        // Get the server ID from the local user ID
        val localUser = userDao.getUserByIdDirect(userId)
            ?: throw IllegalStateException("User not found in local database")

        val serverUserId = localUser.serverId
            ?: throw IllegalStateException("No server ID found for user $userId")

        Log.d(TAG, "Fetching users since $since")
        return apiService.getUsersSince(since, serverUserId)
    }

    override suspend fun applyServerChange(serverEntity: User) {
        userDao.runInTransaction {
            val currentUserId = getUserIdFromPreferences(context)
            val isCurrentUser = serverEntity.userId == currentUserId

            // Convert server user to local entity
            val localUser = myApplication.entityServerConverter.convertUserFromServer(
                serverEntity,
                userDao.getUserByIdDirect(serverEntity.userId),
                isCurrentUser
            ).getOrNull() ?: throw Exception("Failed to convert server user")

            when {
                localUser.userId == 0 -> {
                    Log.d(TAG, "Inserting new user from server: ${serverEntity.userId}")
                    userDao.insertUser(localUser.copy(syncStatus = SyncStatus.SYNCED))
                }
                DateUtils.isUpdateNeeded(
                    serverEntity.updatedAt,
                    localUser.updatedAt,
                    "User-${serverEntity.userId}"
                ) -> {
                    Log.d(TAG, "Updating existing user from server: ${serverEntity.userId}")
                    userDao.updateUser(localUser.copy(syncStatus = SyncStatus.SYNCED))
                }
                else -> {
                    Log.d(TAG, "Local user ${serverEntity.userId} is up to date")
                }
            }
        }
    }

    companion object {
        private const val TAG = "UserSyncManager"
    }
}