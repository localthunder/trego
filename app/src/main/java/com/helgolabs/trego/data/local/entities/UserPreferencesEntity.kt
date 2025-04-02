package com.helgolabs.trego.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.helgolabs.trego.data.sync.SyncStatus

@Entity(
    tableName = "user_preferences",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("user_id"), Index(value = ["user_id", "preference_key"], unique = true)]
)
data class UserPreferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "preference_key") val preferenceKey: String,
    @ColumnInfo(name = "preference_value") val preferenceValue: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    @ColumnInfo(name = "server_id") val serverId: Int? = null
)