package com.helgolabs.trego.data.extensions

import com.helgolabs.trego.data.local.entities.DeviceTokenEntity
import com.helgolabs.trego.data.model.DeviceToken
import com.helgolabs.trego.data.sync.SyncStatus

fun DeviceToken.toEntity(): DeviceTokenEntity {
    return DeviceTokenEntity(
        tokenId = tokenId,
        userId = userId,
        fcmToken = fcmToken,
        deviceType = deviceType,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = SyncStatus.SYNCED
    )
}

fun DeviceTokenEntity.toModel(): DeviceToken {
    return DeviceToken(
        tokenId = tokenId,
        userId = userId,
        fcmToken = fcmToken,
        deviceType = deviceType,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}