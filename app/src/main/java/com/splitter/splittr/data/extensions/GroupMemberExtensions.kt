package com.splitter.splittr.data.extensions

import com.splitter.splittr.data.local.entities.GroupMemberEntity
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.data.model.GroupMember

fun GroupMember.toEntity(syncStatus: SyncStatus): GroupMemberEntity {
    return GroupMemberEntity(
        id = this.id,
        serverId = this.id,
        groupId = this.groupId,
        userId = this.userId,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        removedAt = this.removedAt,
        syncStatus = syncStatus
    )
}

fun GroupMemberEntity.toModel(): GroupMember {
    return GroupMember(
        id = this.serverId ?: 0,
        groupId = this.groupId,
        userId = this.userId,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        removedAt = this.removedAt
    )
}
