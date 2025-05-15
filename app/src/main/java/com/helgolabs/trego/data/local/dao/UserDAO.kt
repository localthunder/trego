package com.helgolabs.trego.data.local.dao

import android.util.Log
import androidx.room.*
import com.helgolabs.trego.data.local.entities.TransactionEntity
import com.helgolabs.trego.data.local.entities.UserEntity
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.utils.DateUtils
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Transaction
    open suspend fun <R> runInTransaction(block: suspend () -> R): R {
        // Room automatically handles transactions for suspend functions
        return block()
    }

    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE user_id = :userId")
    fun getUserById(userId: Int): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE user_id = :userId")
    fun getUserByIdSync(userId: Int): UserEntity?

    @Query("SELECT * FROM users WHERE server_id = :serverId")
    fun getUserByServerId(serverId: Int): UserEntity?

    @Query("SELECT * FROM users WHERE server_id = :serverId LIMIT 1")
    suspend fun getUserByServerIdSync(serverId: Int): UserEntity?

    @Query("SELECT * FROM users WHERE user_id = :userId")
    suspend fun getUserByIdDirect(userId: Int): UserEntity?

    @Query("SELECT * FROM users WHERE user_id IN (:userIds)")
    fun getUsersByIds(userIds: List<Int>): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUserDirect(user: UserEntity)

    @Transaction
    suspend fun updateUser(user: UserEntity) {
        // Create a copy of the transaction with the current timestamp
        val updatedUser = user.copy(
            updatedAt = DateUtils.getCurrentTimestamp(),
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
    suspend fun updateUserSyncStatus(userId: Int, status: SyncStatus)

    @Query("UPDATE users SET invitation_email = :email, updated_at = :timestamp, sync_status = :syncStatus WHERE user_id = :userId")
    suspend fun updateInvitationEmail(
        userId: Int,
        email: String,
        timestamp: String = DateUtils.getCurrentTimestamp(),
        syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
    )

    @Query("UPDATE users SET username = :newUsername, updated_at = :timestamp, sync_status = :syncStatus WHERE user_id = :userId")
    suspend fun updateUsername(
        userId: Int,
        newUsername: String,
        timestamp: String = DateUtils.getCurrentTimestamp(),
        syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
    )

}