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
    indices = [Index("user_id"), Index("server_id")]
)
data class DeviceTokenEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "token_id") val tokenId: Int = 0,
    @ColumnInfo(name = "server_id") val serverId: Int? = null,
    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "fcm_token") val fcmToken: String,
    @ColumnInfo(name = "device_type") val deviceType: String = "android",
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
)