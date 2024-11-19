package com.splitter.splittr.data.extensions

import com.splitter.splittr.data.local.dataClasses.UserGroupListItem
import com.splitter.splittr.data.local.entities.GroupEntity
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.data.model.Group


fun Group.toEntity(syncStatus: SyncStatus = SyncStatus.PENDING_SYNC): GroupEntity {
    return GroupEntity(
        id = this.id,
        serverId = this.id, // This will be set after syncing with the server
        name = this.name,
        description = this.description,
        groupImg = this.groupImg,
        localImagePath = null,
        imageLastModified = null,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        inviteLink = this.inviteLink,
        syncStatus = syncStatus,
    )
}

fun GroupEntity.toModel(): Group {
    return Group(
        id = this.serverId ?: 0,
        name = this.name,
        description = this.description,
        groupImg = this.groupImg,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        inviteLink = this.inviteLink
    )
}

fun GroupEntity.toListItem(): UserGroupListItem {
    return UserGroupListItem(
        id = this.id,
        name = this.name,
        description = this.description,
        groupImg = this.localImagePath ?: this.groupImg // Prefer local path over server path
    )
}

// Optional: Add an extension property to Group for easy access to sync status
val Group.syncStatus: SyncStatus
    get() = when {
        id <= 0 -> SyncStatus.PENDING_SYNC
        else -> SyncStatus.SYNCED
    }

