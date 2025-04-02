package com.helgolabs.trego.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.helgolabs.trego.data.local.entities.UserPreferenceEntity
import com.helgolabs.trego.data.sync.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPreferenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreference(preference: UserPreferenceEntity): Long

    @Update
    suspend fun updatePreference(preference: UserPreferenceEntity)

    @Transaction
    suspend fun upsertPreference(preference: UserPreferenceEntity) {
        val id = insertPreference(preference)
        if (id == -1L) {
            updatePreference(preference)
        }
    }

    @Transaction
    suspend fun <R> runInTransaction(block: suspend () -> R): R {
        return block()
    }

    @Query("SELECT * FROM user_preferences WHERE user_id = :userId AND preference_key = :preferenceKey")
    suspend fun getPreference(userId: Int, preferenceKey: String): UserPreferenceEntity?

    @Query("SELECT * FROM user_preferences WHERE user_id = :userId AND preference_key = :preferenceKey")
    fun getPreferenceFlow(userId: Int, preferenceKey: String): Flow<UserPreferenceEntity?>

    @Query("SELECT * FROM user_preferences WHERE user_id = :userId")
    suspend fun getAllPreferencesForUser(userId: Int): List<UserPreferenceEntity>

    @Query("SELECT * FROM user_preferences WHERE user_id = :userId")
    fun getAllPreferencesForUserFlow(userId: Int): Flow<List<UserPreferenceEntity>>

    @Query("DELETE FROM user_preferences WHERE user_id = :userId AND preference_key = :preferenceKey")
    suspend fun deletePreference(userId: Int, preferenceKey: String)

    @Query("DELETE FROM user_preferences WHERE user_id = :userId")
    suspend fun deleteAllPreferencesForUser(userId: Int)

    @Query("SELECT preference_value FROM user_preferences WHERE user_id = :userId AND preference_key = :preferenceKey")
    suspend fun getPreferenceValue(userId: Int, preferenceKey: String): String?

    // Additional methods for sync functionality

    @Query("SELECT * FROM user_preferences WHERE sync_status IN (:statuses)")
    suspend fun getPreferencesByStatuses(statuses: List<SyncStatus>): List<UserPreferenceEntity>

    @Query("SELECT * FROM user_preferences WHERE sync_status = :status")
    suspend fun getPreferencesByStatus(status: SyncStatus): List<UserPreferenceEntity>

    @Query("SELECT * FROM user_preferences WHERE sync_status IN ('PENDING_SYNC', 'LOCAL_ONLY', 'SYNC_FAILED')")
    suspend fun getPendingSyncPreferences(): List<UserPreferenceEntity>

    @Query("UPDATE user_preferences SET sync_status = :newStatus WHERE id = :id")
    suspend fun updateSyncStatus(id: Int, newStatus: SyncStatus)

    @Query("SELECT DISTINCT user_id FROM user_preferences")
    suspend fun getAllUserIds(): List<Int>

    @Query("SELECT * FROM user_preferences WHERE user_id = :userId AND updated_at > :timestamp")
    suspend fun getPreferencesUpdatedAfter(userId: Int, timestamp: String): List<UserPreferenceEntity>

    @Query("SELECT COUNT(*) FROM user_preferences WHERE sync_status IN ('PENDING_SYNC', 'LOCAL_ONLY')")
    suspend fun countPendingSyncItems(): Int
}