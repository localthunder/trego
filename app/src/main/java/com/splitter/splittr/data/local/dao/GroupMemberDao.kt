package com.splitter.splittr.data.local.dao

import androidx.room.*
import com.splitter.splittr.data.local.entities.GroupMemberEntity
import com.splitter.splittr.data.sync.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupMemberDao {
    @Query("SELECT * FROM group_members WHERE group_id = :groupId")
    fun getMembersOfGroup(groupId: Int): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_members WHERE id = :id")
    fun getGroupMemberById(id: Int): Flow<GroupMemberEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMember(groupMember: GroupMemberEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMembers(groupMember: List<GroupMemberEntity>): List<Long>

    @Update
    suspend fun updateGroupMember(groupMember: GroupMemberEntity)

    @Query("DELETE FROM group_members WHERE group_id = :groupId AND user_id = :userId")
    suspend fun removeGroupMember(groupId: Int, userId: Int)

    @Query("SELECT * FROM group_members WHERE sync_status != 'SYNCED'")
    fun getUnsyncedGroupMembers(): Flow<List<GroupMemberEntity>>

    @Query("UPDATE group_members SET sync_status = :status WHERE id = :id")
    suspend fun updateGroupMemberSyncStatus(id: Int, status: SyncStatus)
}