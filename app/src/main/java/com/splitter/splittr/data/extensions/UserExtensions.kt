package com.splitter.splittr.data.extensions

import com.splitter.splittr.data.local.entities.UserEntity
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.data.model.User


fun User.toEntity(syncStatus: SyncStatus = SyncStatus.PENDING_SYNC): UserEntity {
    return UserEntity(
        userId = this.userId,
        serverId = userId,
        username = this.username,
        email = this.email,
        passwordHash = this.passwordHash,
        googleId = this.googleId,
        appleId = this.appleId,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        defaultCurrency = this.defaultCurrency ?:  "GBP",
        lastLoginDate = this.lastLoginDate,
        syncStatus = syncStatus
    )
}

fun UserEntity.toModel(): User {
    return User(
        userId = this.serverId ?: 0,
        username = this.username,
        email = this.email,
        passwordHash = this.passwordHash,
        googleId = this.googleId,
        appleId = this.appleId,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        lastLoginDate = if (this.lastLoginDate == "null") null else this.lastLoginDate,
        defaultCurrency = this.defaultCurrency
    )
}
