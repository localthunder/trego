package com.splitter.splitter.data.local.repositories

import com.splitter.splitter.data.extensions.toEntity
import com.splitter.splitter.data.local.dao.GroupDao
import com.splitter.splitter.model.Group


class GroupRepository(private val groupDao: GroupDao) {

    suspend fun insert(group: Group) {
        groupDao.insert(group.toEntity())
    }

    suspend fun getAllGroups(): List<Group> {
        return groupDao.getAllGroups()
    }

    suspend fun getGroupById(id: Int): Group? {
        return groupDao.getGroupById(id)
    }
}


