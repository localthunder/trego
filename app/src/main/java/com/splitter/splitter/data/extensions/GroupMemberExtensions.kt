package com.splitter.splitter.data.extensions

import com.splitter.splitter.data.local.entities.GroupMemberEntity
import com.splitter.splitter.model.GroupMember

fun GroupMember.toEntity(): GroupMemberEntity {
    return GroupMemberEntity(
        id = this.id,
        groupId = this.groupId,
        userId = this.userId,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        removedAt = this.removedAt
    )
}

fun GroupMemberEntity.toModel(): GroupMember {
    return GroupMember(
        id = this.id,
        groupId = this.groupId,
        userId = this.userId,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        removedAt = this.removedAt
    )
}
