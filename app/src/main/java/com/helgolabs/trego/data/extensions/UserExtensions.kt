package com.helgolabs.trego.data.extensions

import com.helgolabs.trego.data.local.entities.UserEntity
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.data.model.User


fun User.toEntity(syncStatus: SyncStatus = SyncStatus.PENDING_SYNC): UserEntity {
    return UserEntity(
        serverId = this.userId,
        username = this.username,
        email = this.email,
        passwordHash = this.passwordHash,
        googleId = this.googleId,
        appleId = this.appleId,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        defaultCurrency = this.defaultCurrency ?:  "GBP",
        lastLoginDate = this.lastLoginDate,
        syncStatus = syncStatus,
        isProvisional = this.isProvisional,
        invitedBy = this.invitedBy,
        invitationEmail = this.invitationEmail,
        mergedIntoUserId = this.mergedIntoUserId,
        passwordResetExpires = this.passwordResetExpires,
        passwordResetToken = this.passwordResetToken,
        lastPasswordChange = this.lastPasswordChange
    )
}

fun UserEntity.toModel(): User {
    return User(
        userId = this.userId,
        username = this.username,
        email = this.email,
        passwordHash = this.passwordHash,
        googleId = this.googleId,
        appleId = this.appleId,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        lastLoginDate = if (this.lastLoginDate == "null") null else this.lastLoginDate,
        defaultCurrency = this.defaultCurrency,
        isProvisional = this.isProvisional,
        invitedBy = this.invitedBy,
        invitationEmail = this.invitationEmail,
        mergedIntoUserId = this.mergedIntoUserId,
        passwordResetExpires = this.passwordResetExpires,
        passwordResetToken = this.passwordResetToken,
        lastPasswordChange = this.lastPasswordChange
    )
}
