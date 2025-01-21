package com.splitter.splittr.data.local.dao

import androidx.room.*
import com.splitter.splittr.data.local.entities.GroupEntity
import com.splitter.splittr.data.local.entities.GroupMemberEntity
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.utils.DateUtils
import com.splitter.splittr.utils.DateUtils.isUpdateNeeded
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Dao
interface GroupMemberDao {
    @Transaction
    open suspend fun <R> runInTransaction(block: suspend () -> R): R {
        // Room automatically handles transactions for suspend functions
        return block()
    }

    @Query("""
        SELECT * FROM group_members 
        WHERE group_id = :groupId 
        AND (removed_at IS NULL OR removed_at = '')
        AND sync_status != 'SYNC_FAILED'
    """)
    fun getMembersOfGroup(groupId: Int): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_members WHERE id = :id")
    fun getGroupMemberById(id: Int): Flow<GroupMemberEntity?>

    @Query("SELECT * FROM group_members WHERE id = :id")
    suspend fun getGroupMemberByIdSync(id: Int): GroupMemberEntity?

    @Query("SELECT * FROM group_members WHERE user_id = :userId")
    fun getGroupMemberByUserId(userId: Int): Flow<GroupMemberEntity?>

    @Query("SELECT * FROM group_members WHERE group_id = :groupId AND user_id IN (:userIds)")
    fun getGroupMembersByUserIds(groupId: Int, userIds: List<Int>): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_members WHERE user_id = :userId")
    fun getGroupMemberByUserIdSync(userId: Int): GroupMemberEntity?

    @Query("SELECT * FROM group_members WHERE server_id = :serverId AND (removed_at IS NULL OR removed_at = '')")
    suspend fun getGroupMemberByServerId(serverId: Int): GroupMemberEntity?


    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroupMember(groupMember: GroupMemberEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroupMembers(groupMembers: List<GroupMemberEntity>): List<Long>

    @Update
    suspend fun updateGroupMemberDirect(groupMember: GroupMemberEntity)

    @Transaction
    suspend fun updateGroupMember(groupMember: GroupMemberEntity) {
        // Create a copy of the group member with the current timestamp
        val updatedGroupMember = groupMember.copy(
            updatedAt = DateUtils.getCurrentTimestamp(),
            syncStatus = SyncStatus.PENDING_SYNC
        )
        updateGroupMemberDirect(updatedGroupMember)
    }

    @Transaction
    suspend fun insertOrUpdateGroupMembers(members: List<GroupMemberEntity>) {
        members.forEach { member ->
            // Get existing member by server ID if available, otherwise by local ID
            val existingMember = member.serverId?.let { getGroupMemberByServerId(it) }
                ?: getGroupMemberById(member.id).first()

            when {
                existingMember == null -> {
                    // New member, simply insert
                    insertGroupMember(member)
                }
                member.removedAt != existingMember.removedAt ||
                        isUpdateNeeded(member.updatedAt, existingMember.updatedAt) -> {
                    // Update existing member, preserving local fields
                    val updatedMember = member.copy(
                        id = existingMember.id,
                        syncStatus = SyncStatus.SYNCED
                    )
                    updateGroupMemberDirect(updatedMember)
                }
            }
        }
    }

    @Query("""
        UPDATE group_members 
        SET removed_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP,
            sync_status = 'PENDING_SYNC'
        WHERE id = :memberId
    """)
    suspend fun removeGroupMember(memberId: Int)

    @Query("DELETE FROM group_members WHERE id = :memberId")
    suspend fun deleteGroupMember(memberId: Int)

    @Query("SELECT * FROM group_members WHERE sync_status != 'SYNCED'")
    fun getUnsyncedGroupMembers(): Flow<List<GroupMemberEntity>>

    @Query("""
        UPDATE group_members 
        SET sync_status = :status, 
            updated_at = CURRENT_TIMESTAMP 
        WHERE id = :id
    """)
    suspend fun updateGroupMemberSyncStatus(id: Int, status: SyncStatus)


    @Query("""
        SELECT COUNT(*) 
        FROM group_members 
        WHERE group_id = :groupId 
        AND user_id = :userId 
        AND (removed_at IS NULL OR removed_at = '')
        AND sync_status != 'SYNC_FAILED'
    """)
    suspend fun isActiveMember(groupId: Int, userId: Int): Int

    @Query("""
        SELECT * FROM group_members 
        WHERE group_id = :groupId 
        AND user_id = :userId 
        AND (removed_at IS NULL OR removed_at = '')
        LIMIT 1
    """)
    suspend fun getActiveMembershipForUser(groupId: Int, userId: Int): GroupMemberEntity?
}