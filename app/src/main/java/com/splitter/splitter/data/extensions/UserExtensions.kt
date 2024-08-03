package com.splitter.splitter.data.extensions

import com.splitter.splitter.data.local.entities.UserEntity
import com.splitter.splitter.model.User

fun User.toEntity(): UserEntity {
    return UserEntity(
        userId = this.userId,
        username = this.username,
        email = this.email,
        passwordHash = this.passwordHash,
        googleId = this.googleId,
        appleId = this.appleId,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        defaultCurrency = this.defaultCurrency
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
        defaultCurrency = this.defaultCurrency
    )
}
