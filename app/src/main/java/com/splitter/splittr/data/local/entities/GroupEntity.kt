package com.splitter.splittr.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.splitter.splittr.data.sync.SyncStatus

@Entity(
    tableName = "groups",
    indices = [
        Index(value = ["server_id"], unique = true)
    ])
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Int = 0,
    @ColumnInfo(name = "server_id") val serverId: Int? = null,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "group_img") val groupImg: String?,
    @ColumnInfo(name = "local_image_path") val localImagePath: String?,
    @ColumnInfo(name = "image_last_modified") val imageLastModified: String?, // For sync checking
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "invite_link") val inviteLink: String?,
    @ColumnInfo(name = "sync_status") var syncStatus: SyncStatus = SyncStatus.PENDING_SYNC,
    @ColumnInfo(name = "archived_at") var archivedAt: String? = null
)
