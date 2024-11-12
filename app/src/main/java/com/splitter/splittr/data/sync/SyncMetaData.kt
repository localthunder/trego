// SyncMetadata.kt
package com.splitter.splittr.data.sync

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_metadata")
data class SyncMetadata(
    @PrimaryKey val entityType: String,
    val lastSyncTimestamp: Long,
    val lastEtag: String?,
    val syncStatus: SyncStatus,
    val updateCount: Int = 0,
    val lastSyncResult: String? = null
)

// SyncableEntity interface
interface SyncableEntity {
    val syncStatus: SyncStatus
    val updatedAt: String
}