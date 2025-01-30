package com.splitter.splittr.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.splitter.splittr.data.local.entities.CachedTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedTransactionDao {
    @Query("SELECT * FROM cached_transactions WHERE userId = :userId AND expiryTimestamp > :currentTime")
    fun getValidCachedTransactions(userId: Int, currentTime: Long = System.currentTimeMillis()): Flow<List<CachedTransactionEntity>>

    @Query("DELETE FROM cached_transactions WHERE expiryTimestamp <= :currentTime")
    suspend fun clearExpiredTransactions(currentTime: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedTransactions(transactions: List<CachedTransactionEntity>)

    @Query("SELECT fetchTimestamp FROM cached_transactions WHERE userId = :userId ORDER BY fetchTimestamp DESC LIMIT 1")
    suspend fun getLastFetchTimestamp(userId: Int): Long?

    @Query("DELETE FROM cached_transactions WHERE userId = :userId")
    suspend fun clearUserTransactions(userId: Int)
}