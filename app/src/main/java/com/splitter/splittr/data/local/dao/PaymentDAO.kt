package com.splitter.splittr.data.local.dao

import androidx.room.*
import com.splitter.splittr.data.local.entities.PaymentEntity
import com.splitter.splittr.data.sync.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE id = :paymentId")
    fun getPaymentById(paymentId: Int): Flow<PaymentEntity?>

    @Query("SELECT * FROM payments WHERE transaction_id = :transactionId")
    fun getPaymentByTransactionId(transactionId: String): Flow<PaymentEntity?>

    @Query("SELECT * FROM payments WHERE group_id = :groupId")
    fun getPaymentsByGroup(groupId: Int): Flow<List<PaymentEntity>>

    @Query("""
        SELECT * FROM payments 
        WHERE group_id = :groupId 
        AND deleted_at IS NULL
    """)
    suspend fun getNonArchivedPaymentsByGroup(groupId: Int): List<PaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePayment(payment: PaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePayments(payments: List<PaymentEntity>)

    @Update
    suspend fun updatePayment(payment: PaymentEntity)

    @Query("UPDATE payments SET deleted_at = :deletedAt WHERE id = :paymentId")
    suspend fun archivePayment(paymentId: Int, deletedAt: String)

    @Query("UPDATE payments SET deleted_at = NULL WHERE id = :paymentId")
    suspend fun restorePayment(paymentId: Int)

    @Query("SELECT * FROM payments WHERE sync_status != 'SYNCED'")
    fun getUnsyncedPayments(): Flow<List<PaymentEntity>>

    @Query("UPDATE payments SET sync_status = :status WHERE id = :paymentId")
    suspend fun updatePaymentSyncStatus(paymentId: Int, status: SyncStatus)
}