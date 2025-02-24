package com.helgolabs.trego.data.local.entities

import androidx.room.*
import com.helgolabs.trego.data.sync.SyncStatus

@Entity(
    tableName = "device_tokens",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("user_id")]
)
data class DeviceTokenEntity(
    @PrimaryKey @ColumnInfo(name = "token_id") val tokenId: Int,
    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "fcm_token") val fcmToken: String,
    @ColumnInfo(name = "device_type") val deviceType: String = "android",
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "sync_status") var syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
)