package com.helgolabs.trego.data.local.dao

import androidx.room.*
import com.helgolabs.trego.data.local.entities.GroupEntity
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.utils.DateUtils
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    @Transaction
    open suspend fun <R> runInTransaction(block: suspend () -> R): R {
        // Room automatically handles transactions for suspend functions
        return block()
    }
    @Query("SELECT * FROM groups")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :groupId")
    fun getGroupById(groupId: Int): Flow<GroupEntity?>

    @Query("SELECT * FROM groups WHERE id = :groupId")
    suspend fun getGroupByIdSync(groupId: Int): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroup(group: GroupEntity): Long

    @Transaction
    suspend fun insertGroups(groups: List<GroupEntity>) {
        for (group in groups) {
            val existingGroup = group.serverId?.let { getGroupByServerId(it) }
            if (existingGroup == null) {
                insertGroup(group)
            } else {
                // Optionally, you can update the group if needed
                updateGroup(group)
            }
        }
    }

    @Update
    suspend fun updateGroupDirect(group: GroupEntity)

    @Transaction
    suspend fun updateGroup(group: GroupEntity) {
        // Only set timestamp and sync status if they weren't explicitly provided
        val updatedGroup = if (group.updatedAt.isEmpty() || group.syncStatus == SyncStatus.SYNCED) {
            group.copy(
                updatedAt = DateUtils.getCurrentTimestamp(),
                syncStatus = SyncStatus.PENDING_SYNC
            )
        } else {
            // Use the provided values - they were set intentionally
            group
        }
        updateGroupDirect(updatedGroup)
    }

    @Query("DELETE FROM groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: Int)


    @Query("""
    SELECT DISTINCT g.* 
    FROM groups g
    INNER JOIN group_members gm ON g.id = gm.group_id
    WHERE gm.user_id = :userId 
    AND gm.removed_at IS NULL
    ORDER BY g.updated_at DESC
""")
    fun getGroupsByUserId(userId: Int): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE server_id = :serverId")
    suspend fun getGroupByServerId(serverId: Int): GroupEntity?

    @Query("SELECT * FROM groups WHERE sync_status != 'SYNCED'")
    fun getUnsyncedGroups(): Flow<List<GroupEntity>>

    @Query("UPDATE groups SET sync_status = :status, updated_at = CURRENT_TIMESTAMP WHERE id = :groupId")
    suspend fun updateGroupSyncStatus(groupId: kotlin.Int, status: SyncStatus)

    @Query("UPDATE groups SET updated_at = :timestamp WHERE id = :groupId")
    suspend fun updateGroupTimestamp(groupId: Int, timestamp: String)

    @Query("UPDATE groups SET invite_link = :inviteLink, updated_at = CURRENT_TIMESTAMP WHERE id = :groupId")
    suspend fun updateGroupInviteLink(groupId: Int, inviteLink: String)

    @Query("UPDATE groups SET group_img = :imagePath, updated_at = :timestamp, sync_status = :syncStatus WHERE id = :groupId")
    suspend fun updateGroupImage(
        groupId: Int,
        imagePath: String,
        timestamp: String = DateUtils.getCurrentTimestamp(),
        syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
    )

    @Query("UPDATE groups SET sync_status = :status, updated_at = :timestamp WHERE id = :groupId")
    suspend fun updateGroupImageSyncStatus(
        groupId: Int,
        status: SyncStatus,
        timestamp: String = DateUtils.getCurrentTimestamp()
    )

    // Sync-related queries
    @Query("SELECT * FROM groups WHERE group_img IS NOT NULL AND sync_status IN (:pendingStatuses)")
    suspend fun getGroupsWithPendingImageSync(
        pendingStatuses: List<SyncStatus> = listOf(SyncStatus.PENDING_SYNC, SyncStatus.SYNC_FAILED)): List<GroupEntity>

    @Query("UPDATE groups SET local_image_path = :localPath, image_last_modified = :lastModified WHERE id = :groupId")
    suspend fun updateLocalImageInfo(groupId: Int, localPath: String?, lastModified: String?)

    @Query("SELECT * FROM groups WHERE group_img IS NOT NULL AND (local_image_path IS NULL OR image_last_modified != :lastModified)")
    suspend fun getGroupsNeedingImageDownload(lastModified: String): List<GroupEntity>

    @Query("""
        SELECT DISTINCT g.* 
        FROM groups g
        INNER JOIN group_members gm ON g.id = gm.group_id
        WHERE gm.user_id = :userId 
        AND gm.removed_at IS NULL
        AND NOT EXISTS (
            SELECT 1 
            FROM user_group_archives uga
            WHERE uga.group_id = g.id AND uga.user_id = :userId
        )
        ORDER BY g.updated_at DESC
    """)
    fun getNonArchivedGroupsByUserId(userId: Int): Flow<List<GroupEntity>>



    @Query("""
        SELECT DISTINCT g.* 
        FROM groups g
        INNER JOIN group_members gm ON g.id = gm.group_id
        INNER JOIN user_group_archives uga ON g.id = uga.group_id AND uga.user_id = :userId
        WHERE gm.user_id = :userId 
        AND gm.removed_at IS NULL
        ORDER BY g.updated_at DESC
    """)
    fun getArchivedGroupsByUserId(userId: Int): Flow<List<GroupEntity>>

}