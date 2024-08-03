package com.splitter.splitter.data.extensions

import com.splitter.splitter.data.local.entities.GroupEntity
import com.splitter.splitter.model.Group


fun Group.toEntity(): GroupEntity {
    return GroupEntity(
        id = this.id,
        name = this.name,
        description = this.description,
        groupImg = this.groupImg,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        inviteLink = this.inviteLink
    )
}

fun GroupEntity.toModel(): Group {
    return Group(
        id = this.id,
        name = this.name,
        description = this.description,
        groupImg = this.groupImg,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        inviteLink = this.inviteLink
    )
}
