package com.helgolabs.trego.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.helgolabs.trego.data.local.entities.GroupDefaultSplitEntity
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.utils.DateUtils
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDefaultSplitDao {
    @Query("SELECT * FROM group_default_splits WHERE group_id = :groupId AND removed_at IS NULL")
    fun getDefaultSplitsByGroup(groupId: Int): Flow<List<GroupDefaultSplitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDefaultSplit(defaultSplit: GroupDefaultSplitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDefaultSplits(defaultSplits: List<GroupDefaultSplitEntity>)

    @Transaction
    suspend fun softDeleteDefaultSplitsByGroupWithStatus(groupId: Int, timestamp: String = DateUtils.getCurrentTimestamp()) {
        // First update the timestamp
        updateDefaultSplitRemovalTimestamp(groupId, timestamp)
        // Then update the sync status
        updateDefaultSplitSyncStatus(groupId, timestamp, SyncStatus.LOCALLY_DELETED)
    }

    @Query("UPDATE group_default_splits SET removed_at = :timestamp, updated_at = :timestamp WHERE group_id = :groupId")
    suspend fun updateDefaultSplitRemovalTimestamp(groupId: Int, timestamp: String)

    @Query("UPDATE group_default_splits SET sync_status = :status, updated_at = :timestamp WHERE group_id = :groupId")
    suspend fun updateDefaultSplitSyncStatus(groupId: Int, timestamp: String, status: SyncStatus)

    // Optionally, you might want to also add a method to restore soft-deleted splits
    @Query("UPDATE group_default_splits SET removed_at = NULL WHERE group_id = :groupId")
    suspend fun restoreDefaultSplitsByGroup(groupId: Int)

    // And perhaps a method to retrieve all splits including removed ones
    @Query("SELECT * FROM group_default_splits WHERE group_id = :groupId")
    fun getAllDefaultSplitsByGroup(groupId: Int): Flow<List<GroupDefaultSplitEntity>>

    @Query("SELECT * FROM group_default_splits WHERE id = :splitId")
    suspend fun getDefaultSplitById(splitId: Int): GroupDefaultSplitEntity?

    @Query("UPDATE group_default_splits SET removed_at = :timestamp, sync_status = 'LOCALLY_DELETED' WHERE id = :splitId")
    suspend fun softDeleteDefaultSplit(splitId: Int, timestamp: String)

    @Query("SELECT * FROM group_default_splits WHERE sync_status != 'SYNCED'")
    suspend fun getUnsyncedDefaultSplits(): List<GroupDefaultSplitEntity>

    @Query("SELECT * FROM group_default_splits WHERE server_id = :serverId")
    suspend fun getDefaultSplitByServerId(serverId: Int): GroupDefaultSplitEntity?

    @Query("UPDATE group_default_splits SET sync_status = :status WHERE id = :splitId")
    suspend fun updateSyncStatus(splitId: Int, status: SyncStatus)

    @Query("SELECT * FROM group_default_splits WHERE sync_status = 'LOCALLY_DELETED'")
    suspend fun getLocallyDeletedSplits(): List<GroupDefaultSplitEntity>

    @Query("DELETE FROM group_default_splits WHERE id = :splitId")
    suspend fun hardDeleteSplit(splitId: Int)

    @Query("SELECT * FROM group_default_splits WHERE updated_at > :timestamp AND sync_status != 'LOCALLY_DELETED'")
    suspend fun getSplitsUpdatedSince(timestamp: String): List<GroupDefaultSplitEntity>
}