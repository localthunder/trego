package com.splitter.splittr.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.splitter.splittr.data.sync.SyncStatus

@Entity(
    tableName = "user_group_archives",
    primaryKeys = ["user_id", "group_id"],
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("user_id"),
        Index("group_id")
    ]
)
data class UserGroupArchiveEntity(
    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "group_id") val groupId: Int,
    @ColumnInfo(name = "archived_at") val archivedAt: String,
    @ColumnInfo(name = "sync_status") var syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
)