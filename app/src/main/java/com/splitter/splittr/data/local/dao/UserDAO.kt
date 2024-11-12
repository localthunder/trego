package com.splitter.splittr.data.local.dao

import androidx.room.*
import com.splitter.splittr.data.local.entities.TransactionEntity
import com.splitter.splittr.data.local.entities.UserEntity
import com.splitter.splittr.data.sync.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE user_id = :userId")
    fun getUserById(userId: Int): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE user_id IN (:userIds)")
    fun getUsersByIds(userIds: List<Int>): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUserDirect(user: UserEntity)

    @Transaction
    suspend fun updateUser(user: UserEntity) {
        // Create a copy of the transaction with the current timestamp
        val updatedUser = user.copy(
            updatedAt = System.currentTimeMillis().toString(),
            syncStatus = SyncStatus.PENDING_SYNC
        )
        updateUserDirect(updatedUser)
    }

    @Delete
    suspend fun deleteUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE email = :email")
    fun getUserByEmail(email: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE username = :username")
    fun getUserByUsername(username: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE sync_status != 'SYNCED'")
    fun getUnsyncedUsers(): Flow<List<UserEntity>>

    @Query("UPDATE users SET sync_status = :status, updated_at = CURRENT_TIMESTAMP WHERE user_id = :userId")
    suspend fun updateUserSyncStatus(userId: Int, status: String)
}