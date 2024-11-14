package com.splitter.splittr.data.sync.managers

import android.content.Context
import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.UserDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.model.GroupMember
import com.splitter.splittr.data.model.User
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.GroupSyncManager
import com.splitter.splittr.data.sync.OptimizedSyncManager
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.utils.ConflictResolution
import com.splitter.splittr.utils.ConflictResolver
import com.splitter.splittr.utils.CoroutineDispatchers
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

    override suspend fun getLocalChanges(): List<User> =
        userDao.getUnsyncedUsers().first().map { it.toModel() }

    override suspend fun syncToServer(entity: User): Result<User> = try {
        Log.d(TAG, "Syncing user to server: ${entity.userId}")

        val result = if (entity.userId == null) {
            Log.d(TAG, "Creating new user on server")
            apiService.createUser(entity)
        } else {
            Log.d(TAG, "Updating existing user on server: ${entity.userId}")
            apiService.updateUser(entity.userId, entity)
        }

        // Update local sync status after successful server sync
        result.userId?.let {
            userDao.updateUser(result.toEntity(SyncStatus.SYNCED))
            userDao.updateUserSyncStatus(it, SyncStatus.SYNCED)
        }

        Result.success(result)
    } catch (e: Exception) {
        Log.e(TAG, "Error syncing user to server: ${entity.userId}", e)
        entity.userId?.let { userDao.updateUserSyncStatus(it, SyncStatus.SYNC_FAILED) }
        Result.failure(e)
    }

    override suspend fun getServerChanges(since: Long): List<User> {
        val userId = getUserIdFromPreferences(context) ?: throw IllegalStateException("User ID not found")
        Log.d(TAG, "Fetching users since $since")
        return apiService.getUsersSince(since, userId)
    }

    override suspend fun applyServerChange(serverEntity: User) {
        userDao.runInTransaction {
            val localEntity = userDao.getUserById(serverEntity.userId).first()

            when {
                localEntity == null -> {
                    Log.d(TAG, "Inserting new user from server: ${serverEntity.userId}")
                    userDao.insertUser(serverEntity.toEntity(SyncStatus.SYNCED))
                    userDao.updateUserSyncStatus(serverEntity.userId, SyncStatus.SYNCED)
                }
                serverEntity.updatedAt > localEntity.updatedAt -> {
                    Log.d(TAG, "Updating existing user from server: ${serverEntity.userId}")
                    userDao.updateUser(serverEntity.toEntity(SyncStatus.SYNCED))
                    userDao.updateUserSyncStatus(serverEntity.userId, SyncStatus.SYNCED)
                }
                else -> {
                    Log.d(TAG, "Local user ${serverEntity.userId} is up to date")
                }
            }
        }
    }

    suspend fun handleUserUpdate(user: User): Result<User> = try {
        Log.d(TAG, "Handling user update: ${user.userId}")

        // Update local DB with PENDING_SYNC status
        userDao.updateUser(user.toEntity(SyncStatus.PENDING_SYNC))
        user.userId?.let { userDao.updateUserSyncStatus(it, SyncStatus.PENDING_SYNC) }

        // Try to sync immediately if possible
        if (NetworkUtils.isOnline()) {
            val serverUser = apiService.updateUser(user.userId, user)
            userDao.updateUser(serverUser.toEntity(SyncStatus.SYNCED))
            serverUser.userId?.let { userDao.updateUserSyncStatus(it, SyncStatus.SYNCED) }
            Result.success(serverUser)
        } else {
            Result.success(user)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error handling user update", e)
        Result.failure(e)
    }

    suspend fun handleUserRegistration(user: User): Result<User> = try {
        Log.d(TAG, "Handling user registration")

        if (NetworkUtils.isOnline()) {
            val serverUser = apiService.createUser(user)
            userDao.insertUser(serverUser.toEntity(SyncStatus.SYNCED))
            serverUser.userId?.let { userDao.updateUserSyncStatus(it, SyncStatus.SYNCED) }
            Result.success(serverUser)
        } else {
            userDao.insertUser(user.toEntity(SyncStatus.PENDING_SYNC))
            Result.success(user)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error handling user registration", e)
        Result.failure(e)
    }

    companion object {
        private const val TAG = "UserSyncManager"
    }
}