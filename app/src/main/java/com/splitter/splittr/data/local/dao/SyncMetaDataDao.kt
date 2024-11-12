package com.splitter.splittr.data.local.dao

// SyncMetadataDao.kt

import androidx.room.*
import com.splitter.splittr.data.sync.SyncMetadata
import com.splitter.splittr.data.sync.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE entityType = :entityType")
    suspend fun getMetadata(entityType: String): SyncMetadata?

    @Query("SELECT * FROM sync_metadata")
    fun getAllMetadata(): Flow<List<SyncMetadata>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: SyncMetadata)

    @Update
    suspend fun update(metadata: SyncMetadata)

    @Query("DELETE FROM sync_metadata WHERE entityType = :entityType")
    suspend fun delete(entityType: String)

    @Transaction
    suspend fun update(entityType: String, updateBlock: (SyncMetadata) -> SyncMetadata) {
        val existing = getMetadata(entityType)
        val updated = if (existing != null) {
            updateBlock(existing)
        } else {
            updateBlock(
                SyncMetadata(
                    entityType = entityType,
                    lastSyncTimestamp = 0L,
                    lastEtag = null,
                    syncStatus = SyncStatus.PENDING_SYNC
                )
            )
        }
        insert(updated)
    }

    @Query("""
        UPDATE sync_metadata 
        SET lastSyncTimestamp = :timestamp,
            syncStatus = :status
        WHERE entityType = :entityType
    """)
    suspend fun updateSyncStatus(
        entityType: String,
        timestamp: Long = System.currentTimeMillis(),
        status: SyncStatus
    )

    @Query("""
        SELECT COUNT(*) 
        FROM sync_metadata 
        WHERE entityType = :entityType 
        AND lastSyncTimestamp > :since
    """)
    suspend fun hasSyncedSince(entityType: String, since: Long): Boolean

    @Query("""
        SELECT *
        FROM sync_metadata
        WHERE syncStatus = 'SYNC_FAILED'
    """)
    fun getFailedSyncs(): Flow<List<SyncMetadata>>
}