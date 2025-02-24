package com.helgolabs.trego.data.repositories

import android.content.Context
import android.util.Log
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.extensions.toEntity
import com.helgolabs.trego.data.extensions.toModel
import com.helgolabs.trego.data.local.dao.DeviceTokenDao
import com.helgolabs.trego.data.local.entities.DeviceTokenEntity
import com.helgolabs.trego.data.model.DeviceToken
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.NetworkUtils
import kotlinx.coroutines.withContext

class NotificationRepository(
    private val deviceTokenDao: DeviceTokenDao,
    private val apiService: ApiService,
    private val dispatchers: CoroutineDispatchers,
    private val context: Context
) {

    val myApplication = context.applicationContext as MyApplication
    val entityServerConverter = myApplication.entityServerConverter

    suspend fun registerDeviceToken(token: String, userId: Int) = withContext(dispatchers.io) {
        try {
            // Create initial entity
            val initialEntity = DeviceTokenEntity(
                tokenId = 0,
                userId = userId,
                fcmToken = token,
                deviceType = "android",
                createdAt = DateUtils.getCurrentTimestamp(),
                updatedAt = DateUtils.getCurrentTimestamp(),
                syncStatus = SyncStatus.PENDING_SYNC
            )

            if (NetworkUtils.isOnline()) {
                // Convert to server model
                val serverModel = entityServerConverter.convertDeviceTokenToServer(initialEntity)
                    .getOrThrow()

                // Send to server
                val response = apiService.registerDeviceToken(serverModel)

                // Convert response back to entity
                val finalEntity = entityServerConverter.convertDeviceTokenFromServer(
                    response,
                    initialEntity
                ).getOrThrow()

                // Save to local database
                deviceTokenDao.insert(finalEntity)
                Result.success(response)
            } else {
                // Save locally only
                deviceTokenDao.insert(initialEntity)
                Result.success(initialEntity.toModel())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unregisterDeviceToken(tokenId: Int) = withContext(dispatchers.io) {
        try {
            // Get existing token
            val existingToken = deviceTokenDao.getDeviceTokenById(tokenId)
                ?: return@withContext Result.failure(Exception("Device token not found"))

            // Mark for deletion locally
            val updatedToken = existingToken.copy(
                syncStatus = SyncStatus.LOCALLY_DELETED,
                updatedAt = DateUtils.getCurrentTimestamp()
            )
            deviceTokenDao.update(updatedToken)

            // If online, attempt server sync
            if (NetworkUtils.isOnline()) {
                try {
                    // Only attempt server deletion if we have a server ID
                    updatedToken.tokenId?.let { serverTokenId ->
                        apiService.unregisterDeviceToken(serverTokenId)
                        // On successful server deletion, remove from local DB
                        deviceTokenDao.deleteTokenById(tokenId)
                    }
                } catch (e: Exception) {
                    Log.e("NotificationRepository", "Failed to sync token deletion with server", e)
                    // Keep local deletion marker for later sync
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("NotificationRepository", "Error unregistering device token", e)
            Result.failure(e)
        }
    }
}