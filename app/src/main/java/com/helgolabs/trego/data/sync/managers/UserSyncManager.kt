package com.helgolabs.trego.data.sync.managers

import android.content.Context
import android.util.Log
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.extensions.toEntity
import com.helgolabs.trego.data.local.dao.UserDao
import com.helgolabs.trego.data.local.dao.SyncMetadataDao
import com.helgolabs.trego.data.local.entities.UserEntity
import com.helgolabs.trego.data.model.User
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.sync.OptimizedSyncManager
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first

class UserSyncManager(
    private val userDao: UserDao,
    private val apiService: ApiService,
    syncMetadataDao: SyncMetadataDao,
    dispatchers: CoroutineDispatchers,
    private val context: Context
) : OptimizedSyncManager<UserEntity, User>(syncMetadataDao, dispatchers) {

    override val entityType = "users"
    override val batchSize = 50

    val myApplication = context.applicationContext as MyApplication

    override suspend fun getLocalChanges(): List<UserEntity> =
        userDao.getUnsyncedUsers().first()

    override suspend fun syncToServer(entity: UserEntity): Result<UserEntity> {
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

            val serverUserModel = myApplication.entityServerConverter.convertUserToServer(localEntity).getOrThrow()

            val serverUser = if (localEntity.serverId == null) {
                Log.d(TAG, "Creating new user on server")
                apiService.createUser(serverUserModel)
            } else {
                Log.d(TAG, "Updating existing user on server: ${localEntity.serverId}")
                apiService.updateUser(localEntity.serverId, serverUserModel)
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

            Result.success(serverUser.toEntity())
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing user to server", e)
            userDao.updateUserSyncStatus(entity.userId, SyncStatus.SYNC_FAILED)
            Result.failure(e)
        }
    }

    override suspend fun getServerChanges(since: Long): List<User> {
        Log.d(TAG, "Fetching users since $since")
        return apiService.getUsersSince(since)
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