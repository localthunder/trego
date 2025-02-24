package com.helgolabs.trego.data.local.dao

import androidx.room.*
import com.helgolabs.trego.data.local.entities.DeviceTokenEntity
import com.helgolabs.trego.data.sync.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceTokenDao {
    @Query("SELECT * FROM device_tokens WHERE user_id = :userId")
    fun getDeviceTokensByUserId(userId: Int): Flow<List<DeviceTokenEntity>>

    @Query("SELECT * FROM device_tokens WHERE token_id = :tokenId")
    suspend fun getDeviceTokenById(tokenId: Int): DeviceTokenEntity?

    @Query("SELECT * FROM device_tokens WHERE fcm_token = :fcmToken")
    suspend fun getDeviceTokenByFcmToken(fcmToken: String): DeviceTokenEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(deviceToken: DeviceTokenEntity): Long

    @Update
    suspend fun update(deviceToken: DeviceTokenEntity)

    @Delete
    suspend fun delete(deviceToken: DeviceTokenEntity)

    @Query("DELETE FROM device_tokens WHERE token_id = :tokenId")
    suspend fun deleteTokenById(tokenId: Int)

    @Query("DELETE FROM device_tokens WHERE user_id = :userId")
    suspend fun deleteTokensByUserId(userId: Int)

    @Query("SELECT * FROM device_tokens WHERE sync_status = :status")
    suspend fun getTokensByStatus(status: SyncStatus): List<DeviceTokenEntity>

    @Query("UPDATE device_tokens SET sync_status = :status WHERE token_id = :tokenId")
    suspend fun updateSyncStatus(tokenId: Int, status: SyncStatus)

    @Transaction
    suspend fun updateOrInsert(deviceToken: DeviceTokenEntity) {
        val existing = getDeviceTokenById(deviceToken.tokenId)
        if (existing != null) {
            update(deviceToken)
        } else {
            insert(deviceToken)
        }
    }
}