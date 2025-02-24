package com.helgolabs.trego.data.extensions

import com.helgolabs.trego.data.local.dataClasses.GroupMemberWithGroupResponse
import com.helgolabs.trego.data.local.entities.GroupMemberEntity
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.data.model.GroupMember

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

fun GroupMemberWithGroupResponse.toEntity(syncStatus: SyncStatus = SyncStatus.PENDING_SYNC): GroupMemberEntity {
    return GroupMemberEntity(
        id = id,
        serverId = id,
        groupId = group_id,
        userId = user_id,
        createdAt = created_at,
        updatedAt = updated_at,
        removedAt = removed_at,
        syncStatus = syncStatus
    )
}

fun GroupMemberWithGroupResponse.toGroupMember(): GroupMember {
    return GroupMember(
        id = this.id,
        groupId = this.group_id,
        userId = this.user_id,
        createdAt = this.created_at,
        updatedAt = this.updated_at,
        removedAt = this.removed_at
    )
}