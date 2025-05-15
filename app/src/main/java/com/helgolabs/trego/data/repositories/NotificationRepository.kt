package com.helgolabs.trego.data.repositories

import android.content.Context
import android.util.Log
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.extensions.toModel
import com.helgolabs.trego.data.local.dao.DeviceTokenDao
import com.helgolabs.trego.data.local.dao.SyncMetadataDao
import com.helgolabs.trego.data.local.entities.DeviceTokenEntity
import com.helgolabs.trego.data.model.DeviceToken
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.data.sync.SyncableRepository
import com.helgolabs.trego.data.sync.managers.DeviceTokenSyncManager
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.NetworkUtils
import kotlinx.coroutines.withContext

class NotificationRepository(
    private val deviceTokenDao: DeviceTokenDao,
    private val apiService: ApiService,
    private val dispatchers: CoroutineDispatchers,
    private val syncMetadataDao: SyncMetadataDao,
    private val deviceTokenSyncManager: DeviceTokenSyncManager,
    private val context: Context
) : SyncableRepository {

    override val entityType = "device_tokens"
    override val syncPriority = 3

    val myApplication = context.applicationContext as MyApplication
    val entityServerConverter = myApplication.entityServerConverter

    suspend fun registerDeviceToken(token: String, userId: Int) = withContext(dispatchers.io) {
        try {
            // Check if we already have an active token for this user
            val existingToken = deviceTokenDao.getActiveDeviceTokenForUser(userId)

            if (existingToken != null) {
                // Check if it's the same token
                if (existingToken.fcmToken == token) {
                    Log.d(TAG, "Token unchanged for user $userId")
                    return@withContext Result.success(existingToken.toModel())
                } else {
                    // Token has changed - update the existing record
                    Log.d(TAG, "Updating FCM token for user $userId")
                    val updatedToken = existingToken.copy(
                        fcmToken = token,
                        updatedAt = DateUtils.getCurrentTimestamp(),
                        syncStatus = if (NetworkUtils.isOnline()) SyncStatus.PENDING_SYNC else SyncStatus.LOCAL_ONLY
                    )

                    deviceTokenDao.update(updatedToken)

                    // Try to sync immediately if online
                    if (NetworkUtils.isOnline()) {
                        syncTokenToServer(updatedToken)
                    }

                    return@withContext Result.success(updatedToken.toModel())
                }
            }

            // No existing token - create new one
            Log.d(TAG, "Creating new device token for user $userId")
            val newToken = DeviceTokenEntity(
                tokenId = 0, // Let Room auto-generate
                serverId = null,
                userId = userId,
                fcmToken = token,
                deviceType = "android",
                createdAt = DateUtils.getCurrentTimestamp(),
                updatedAt = DateUtils.getCurrentTimestamp(),
                syncStatus = if (NetworkUtils.isOnline()) SyncStatus.PENDING_SYNC else SyncStatus.LOCAL_ONLY
            )

            val localId = deviceTokenDao.insert(newToken)
            val tokenWithId = newToken.copy(tokenId = localId.toInt())

            // Try to sync immediately if online
            if (NetworkUtils.isOnline()) {
                syncTokenToServer(tokenWithId)
            }

            Result.success(tokenWithId.toModel())
        } catch (e: Exception) {
            Log.e(TAG, "Error registering device token", e)
            Result.failure(e)
        }
    }

    private suspend fun syncTokenToServer(token: DeviceTokenEntity): DeviceTokenEntity {
        return try {
            val serverModel = entityServerConverter.convertDeviceTokenToServer(token)
                .getOrThrow()

            val response = if (token.serverId == null) {
                // Create new token on server
                apiService.registerDeviceToken(serverModel)
            } else {
                // Update existing token on server
                // Note: You might need to add an update endpoint to your API
                apiService.registerDeviceToken(serverModel)
            }

            // Update local record with server ID and synced status
            val syncedToken = token.copy(
                serverId = response.id,
                syncStatus = SyncStatus.SYNCED
            )

            deviceTokenDao.update(syncedToken)
            Log.d(TAG, "Successfully synced token to server with ID: ${response.id}")
            syncedToken
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync token to server", e)
            token
        }
    }

    override suspend fun sync(): Unit = withContext(dispatchers.io) {
        try {
            Log.d(TAG, "Starting device token sync")
            deviceTokenSyncManager.performSync()
            Log.d(TAG, "Device token sync completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during device token sync", e)
            throw e
        }
    }

    suspend fun unregisterDeviceToken(tokenId: Int) = withContext(dispatchers.io) {
        try {
            val existingToken = deviceTokenDao.getDeviceTokenById(tokenId)
                ?: return@withContext Result.failure(Exception("Device token not found"))

            // Mark for deletion
            val deletedToken = existingToken.copy(
                syncStatus = SyncStatus.LOCALLY_DELETED,
                updatedAt = DateUtils.getCurrentTimestamp()
            )
            deviceTokenDao.update(deletedToken)

            // Try to delete from server if online
            if (NetworkUtils.isOnline() && existingToken.serverId != null) {
                try {
                    apiService.unregisterDeviceToken(existingToken.serverId)
                    // If server deletion successful, remove from local DB
                    deviceTokenDao.deleteTokenById(tokenId)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete token from server", e)
                    // Keep the deletion marker for later sync
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering device token", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "NotificationRepository"
    }
}