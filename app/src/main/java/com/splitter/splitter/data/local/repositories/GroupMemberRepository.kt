package com.splitter.splitter.data.local.repositories

import com.splitter.splitter.data.extensions.toEntity
import com.splitter.splitter.data.extensions.toModel
import com.splitter.splitter.data.local.dao.GroupMemberDao
import com.splitter.splitter.model.GroupMember

class GroupMemberRepository(private val groupMemberDao: GroupMemberDao) {

    suspend fun insert(groupMember: GroupMember) {
        groupMemberDao.insert(groupMember.toEntity())
    }

    suspend fun getAllGroupMembers(): List<GroupMember> {
        return groupMemberDao.getAllGroupMembers().map { it.toModel() }
    }

    suspend fun getGroupMemberById(id: Int): GroupMember? {
        return groupMemberDao.getGroupMemberById(id)?.toModel()
    }

    suspend fun getGroupMembersByGroupId(groupId: Int): List<GroupMember> {
        return groupMemberDao.getGroupMembersByGroupId(groupId).map { it.toModel() }
    }

    suspend fun deleteGroupMemberById(id: Int) {
        groupMemberDao.deleteGroupMemberById(id)
    }
}
