package com.splitter.splittr.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.splitter.splittr.data.local.converters.LocalIdGenerator
import com.splitter.splittr.data.sync.SyncStatus

@Entity(
    tableName = "groups",
    indices = [Index("server_id")]
)
data class GroupEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int,
    @ColumnInfo(name = "server_id") val serverId: Int? = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "group_img") val groupImg: String?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "invite_link") val inviteLink: String?,
    @ColumnInfo(name = "sync_status") var syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
)
