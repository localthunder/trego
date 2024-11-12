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
    override val batchSize = 50  // Users can be processed in larger batches

    override suspend fun getLocalChanges(): List<User> =
        userDao.getUnsyncedUsers().first().map { it.toModel() }

    override suspend fun syncToServer(entity: User): Result<User> = try {
        val result = if (entity.userId == null) {
            Log.d(TAG, "Creating new user on server: ${entity.userId}")
            apiService.createUser(entity)
        } else {
            Log.d(TAG, "Updating existing user on server: ${entity.userId}")
            apiService.updateUser(entity.userId, entity)
        }
        Result.success(result)
    } catch (e: Exception) {
        Log.e(TAG, "Error syncing user to server: ${entity.userId}", e)
        Result.failure(e)
    }


    override suspend fun getServerChanges(since: Long): List<User> {
        val userId = getUserIdFromPreferences(context) ?: throw IllegalStateException("User ID not found")
        Log.d(GroupSyncManager.TAG, "Fetching group members since $since")
        return apiService.getUsersSince(since, userId)
    }

    override suspend fun applyServerChange(serverEntity: User) {
        val localEntity = userDao.getUserById(serverEntity.userId).first()?.toModel()

        if (localEntity == null) {
            Log.d(TAG, "Inserting new user from server: ${serverEntity.userId}")
            userDao.insertUser(serverEntity.toEntity(SyncStatus.SYNCED))
        } else {
            when (val resolution = ConflictResolver.resolve(localEntity, serverEntity)) {
                is ConflictResolution.ServerWins -> {
                    Log.d(TAG, "Updating existing user from server: ${serverEntity.userId}")
                    userDao.updateUser(serverEntity.toEntity(SyncStatus.SYNCED))
                }
                is ConflictResolution.LocalWins -> {
                    Log.d(TAG, "Keeping local user version: ${localEntity.userId}")
                    userDao.updateUserSyncStatus(localEntity.userId, SyncStatus.PENDING_SYNC.name)
                }
            }
        }
    }

    suspend fun handleUserUpdate(user: User): Result<User> = try {
        Log.d(TAG, "Handling user update: ${user.userId}")

        // Update local DB
        userDao.updateUser(user.toEntity(SyncStatus.PENDING_SYNC))

        // Try to sync immediately if possible
        if (NetworkUtils.isOnline()) {
            val serverUser = apiService.updateUser(user.userId, user)
            userDao.updateUser(serverUser.toEntity(SyncStatus.SYNCED))
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

        // Try server first since this is a new user
        if (NetworkUtils.isOnline()) {
            val serverUser = apiService.createUser(user)
            userDao.insertUser(serverUser.toEntity(SyncStatus.SYNCED))
            Result.success(serverUser)
        } else {
            // Store locally if offline
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