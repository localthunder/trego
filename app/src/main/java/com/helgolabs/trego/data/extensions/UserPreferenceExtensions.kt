package com.helgolabs.trego.data.extensions

import com.helgolabs.trego.data.local.entities.UserPreferenceEntity
import com.helgolabs.trego.data.model.UserPreference
import com.helgolabs.trego.data.sync.SyncStatus

fun UserPreference.toEntity(): UserPreferenceEntity {
    return UserPreferenceEntity(
        id = 0, // Let Room assign a new ID
        userId = this.userId,
        preferenceKey = this.preferenceKey,
        preferenceValue = this.preferenceValue,
        updatedAt = this.updatedAt ?: "",
        syncStatus = SyncStatus.SYNCED,
        serverId = this.id
    )
}

fun UserPreferenceEntity.toModel(): UserPreference {
    return UserPreference(
        id = this.serverId,
        userId = this.userId,
        preferenceKey = this.preferenceKey,
        preferenceValue = this.preferenceValue,
        updatedAt = this.updatedAt
    )
}