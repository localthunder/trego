package com.helgolabs.trego.data.sync.managers

import android.content.Context
import android.util.Log
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.dao.DeviceTokenDao
import com.helgolabs.trego.data.local.dao.SyncMetadataDao
import com.helgolabs.trego.data.local.entities.DeviceTokenEntity
import com.helgolabs.trego.data.model.DeviceToken
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.sync.OptimizedSyncManager
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.ServerIdUtil.getServerId

class DeviceTokenSyncManager(
    private val deviceTokenDao: DeviceTokenDao,
    private val apiService: ApiService,
    syncMetadataDao: SyncMetadataDao,
    dispatchers: CoroutineDispatchers,
    private val context: Context
) : OptimizedSyncManager<DeviceTokenEntity, DeviceToken>(syncMetadataDao, dispatchers) {

    override val entityType = "device_tokens"
    override val batchSize = 50

    val myApplication = context.applicationContext as MyApplication

    override suspend fun getLocalChanges(): List<DeviceTokenEntity> =
        deviceTokenDao.getPendingSyncTokens()

    override suspend fun syncToServer(entity: DeviceTokenEntity): Result<DeviceTokenEntity> {
        return try {
            Log.d(TAG, "Syncing device token to server: ${entity.tokenId}")

            when (entity.syncStatus) {
                SyncStatus.LOCALLY_DELETED -> {
                    // Handle deletion
                    if (entity.serverId != null) {
                        apiService.unregisterDeviceToken(entity.serverId)
                        deviceTokenDao.deleteTokenById(entity.tokenId)
                    }
                    Result.success(entity)
                }
                else -> {
                    // Get the local entity to check server ID
                    val localEntity = deviceTokenDao.getDeviceTokenById(entity.tokenId)
                        ?: return Result.failure(Exception("Local entity not found"))

                    // Convert local entity to server model
                    val serverModel = myApplication.entityServerConverter.convertDeviceTokenToServer(localEntity)
                        .getOrThrow()

                    val response = if (localEntity.serverId == null) {
                        Log.d(TAG, "Creating new device token on server")
                        apiService.registerDeviceToken(serverModel)
                    } else {
                        // Note: Most APIs don't support updating device tokens
                        // You might need to delete and re-create instead
                        Log.d(TAG, "Device token already has server ID: ${localEntity.serverId}")
                        return Result.success(localEntity)
                    }

                    // Convert response back to entity with server ID
                    val updatedEntity = localEntity.copy(
                        serverId = response.id,
                        syncStatus = SyncStatus.SYNCED
                    )

                    // Update local database
                    deviceTokenDao.update(updatedEntity)
                    Log.d(TAG, "Successfully synced device token to server with ID: ${response.id}")
                    Result.success(updatedEntity)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing device token to server", e)
            deviceTokenDao.updateSyncStatus(entity.tokenId, SyncStatus.SYNC_FAILED)
            Result.failure(e)
        }
    }

    override suspend fun getServerChanges(since: Long): List<DeviceToken> {
        // Device tokens are usually not synced from server to client
        // as they are device-specific. Each device manages its own tokens.
        return emptyList()
    }

    override suspend fun applyServerChange(serverEntity: DeviceToken) {
        // Usually not needed for device tokens as they are device-specific
        // But implement if you need bi-directional sync
    }

    companion object {
        private const val TAG = "DeviceTokenSyncManager"
    }
}