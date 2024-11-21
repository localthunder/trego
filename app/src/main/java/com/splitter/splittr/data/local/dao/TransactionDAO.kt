package com.splitter.splittr.data.local.dao

import androidx.room.*
import com.splitter.splittr.data.local.entities.RequisitionEntity
import com.splitter.splittr.data.local.entities.TransactionEntity
import com.splitter.splittr.data.sync.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Transaction
    open suspend fun <R> runInTransaction(block: suspend () -> R): R {
        // Room automatically handles transactions for suspend functions
        return block()
    }

    @Query("SELECT * FROM transactions WHERE user_id = :userId")
    fun getTransactionsByUserId(userId: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE transaction_id = :transactionId")
    fun getTransactionById(transactionId: String): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE user_id = :userId AND booking_date >= :dateFrom")
    fun getRecentTransactions(userId: Int, dateFrom: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE user_id = :userId AND booking_date < :dateTo")
    fun getNonRecentTransactions(userId: Int, dateTo: String): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Update
    suspend fun updateTransactionDirect(transaction: TransactionEntity)

    @Transaction
    suspend fun updateTransaction(transaction: TransactionEntity) {
        // Create a copy of the transaction with the current timestamp
        val updatedTransaction = transaction.copy(
            updatedAt = System.currentTimeMillis().toString(),
            syncStatus = SyncStatus.PENDING_SYNC
        )
        updateTransactionDirect(updatedTransaction)
    }

    @Query("SELECT * FROM transactions WHERE sync_status != 'SYNCED'")
    fun getUnsyncedTransactions(): Flow<List<TransactionEntity>>

    @Query("UPDATE transactions SET sync_status = :status, updated_at = CURRENT_TIMESTAMP WHERE transaction_id = :transactionId")
    suspend fun updateTransactionSyncStatus(transactionId: String, status: SyncStatus)
}