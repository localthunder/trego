package com.splitter.splitter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.splitter.splitter.data.local.entities.GroupMemberEntity

@Dao
interface GroupMemberDao {

    @Insert
    suspend fun insert(groupMember: GroupMemberEntity)

    @Query("SELECT * FROM group_members")
    suspend fun getAllGroupMembers(): List<GroupMemberEntity>

    @Query("SELECT * FROM group_members WHERE id = :id")
    suspend fun getGroupMemberById(id: Int): GroupMemberEntity?

    @Query("SELECT * FROM group_members WHERE group_id = :groupId")
    suspend fun getGroupMembersByGroupId(groupId: Int): List<GroupMemberEntity>

    @Query("DELETE FROM group_members WHERE id = :id")
    suspend fun deleteGroupMemberById(id: Int)
}
