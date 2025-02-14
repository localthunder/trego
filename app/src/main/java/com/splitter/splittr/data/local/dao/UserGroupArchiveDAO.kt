package com.splitter.splittr.data.local.dao

import androidx.room.*
import com.splitter.splittr.data.local.entities.UserGroupArchiveEntity
import com.splitter.splittr.data.sync.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface UserGroupArchiveDao {
    @Transaction
    open suspend fun <R> runInTransaction(block: suspend () -> R): R {
        // Room automatically handles transactions for suspend functions
        return block()
    }

    @Query("""
        SELECT * FROM user_group_archives 
        WHERE user_id = :userId AND group_id = :groupId
    """)
    fun getArchive(userId: Int, groupId: Int): Flow<UserGroupArchiveEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArchive(archive: UserGroupArchiveEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArchives(archives: List<UserGroupArchiveEntity>)

    @Query("""
        DELETE FROM user_group_archives 
        WHERE user_id = :userId AND group_id = :groupId
    """)
    suspend fun deleteArchive(userId: Int, groupId: Int)

    @Query("""
        UPDATE user_group_archives 
        SET sync_status = :status 
        WHERE user_id = :userId AND group_id = :groupId
    """)
    suspend fun updateSyncStatus(userId: Int, groupId: Int, status: SyncStatus)

    @Query("""
        SELECT * FROM user_group_archives 
        WHERE sync_status != 'SYNCED' || 'LOCALLY_DELETED'
    """)
    fun getUnsyncedArchives(): Flow<List<UserGroupArchiveEntity>>

    @Query("""
        SELECT * FROM user_group_archives 
        WHERE user_id = :userId
    """)
    fun getArchivesByUserId(userId: Int): Flow<List<UserGroupArchiveEntity>>

    @Query("""
        SELECT * FROM user_group_archives 
        WHERE group_id = :groupId
    """)
    fun getArchivesByGroupId(groupId: Int): Flow<List<UserGroupArchiveEntity>>

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM user_group_archives 
            WHERE user_id = :userId AND group_id = :groupId
        )
    """)
    fun isGroupArchived(userId: Int, groupId: Int): Flow<Boolean>

    @Query("""
        SELECT group_id FROM user_group_archives 
        WHERE user_id = :userId
    """)
    fun getArchivedGroupIds(userId: Int): Flow<List<Int>>
}