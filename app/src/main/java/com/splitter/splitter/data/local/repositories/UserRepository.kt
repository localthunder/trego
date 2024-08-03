package com.splitter.splitter.data.local.repositories

import com.splitter.splitter.data.extensions.toEntity
import com.splitter.splitter.data.extensions.toModel
import com.splitter.splitter.data.local.dao.UserDao
import com.splitter.splitter.model.User

class UserRepository(private val userDao: UserDao) {

    suspend fun insert(user: User) {
        userDao.insert(user.toEntity())
    }

    suspend fun getAllUsers(): List<User> {
        return userDao.getAllUsers().map { it.toModel() }
    }

    suspend fun getUserById(userId: Int): User? {
        return userDao.getUserById(userId)?.toModel()
    }

    suspend fun deleteUserById(userId: Int) {
        userDao.deleteUserById(userId)
    }
}
