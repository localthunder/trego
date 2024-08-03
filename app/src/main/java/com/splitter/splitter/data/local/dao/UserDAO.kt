package com.splitter.splitter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.splitter.splitter.data.local.entities.UserEntity

@Dao
interface UserDao {

    @Insert
    suspend fun insert(user: UserEntity)

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<UserEntity>

    @Query("SELECT * FROM users WHERE user_id = :userId")
    suspend fun getUserById(userId: Int): UserEntity?

    @Query("DELETE FROM users WHERE user_id = :userId")
    suspend fun deleteUserById(userId: Int)
}
