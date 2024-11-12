package com.splitter.splittr.data.local.dao

import androidx.room.*
import com.splitter.splittr.data.local.entities.GroupEntity
import com.splitter.splittr.data.local.entities.GroupMemberEntity
import com.splitter.splittr.data.sync.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupMemberDao {
    @Transaction
    open suspend fun <R> runInTransaction(block: suspend () -> R): R {
        // Room automatically handles transactions for suspend functions
        return block()
    }

    @Query("SELECT * FROM group_members WHERE group_id = :groupId")
    fun getMembersOfGroup(groupId: Int): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_members WHERE id = :id")
    fun getGroupMemberById(id: Int): Flow<GroupMemberEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMember(groupMember: GroupMemberEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMembers(groupMember: List<GroupMemberEntity>): List<Long>

    @Update
    suspend fun updateGroupMemberDirect(groupMember: GroupMemberEntity)

    @Transaction
    suspend fun updateGroupMember(groupMember: GroupMemberEntity) {
        // Create a copy of the group member with the current timestamp
        val updatedGroupMember = groupMember.copy(
            updatedAt = System.currentTimeMillis().toString(),
            syncStatus = SyncStatus.PENDING_SYNC
        )
        updateGroupMemberDirect(updatedGroupMember)
    }

    @Query("UPDATE group_members SET removed_at = CURRENT_TIMESTAMP WHERE id = :memberId")
    suspend fun removeGroupMember(memberId: Int)

    @Query("DELETE FROM group_members WHERE id = :memberId")
    suspend fun deleteGroupMember(memberId: Int)

    @Query("SELECT * FROM group_members WHERE sync_status != 'SYNCED'")
    fun getUnsyncedGroupMembers(): Flow<List<GroupMemberEntity>>

    @Query("UPDATE group_members SET sync_status = :status, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
    suspend fun updateGroupMemberSyncStatus(id: Int, status: SyncStatus)
}