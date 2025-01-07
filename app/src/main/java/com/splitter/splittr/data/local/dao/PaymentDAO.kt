package com.splitter.splittr.data.local.dao

import androidx.room.*
import com.splitter.splittr.data.local.entities.GroupMemberEntity
import com.splitter.splittr.data.local.entities.PaymentEntity
import com.splitter.splittr.data.local.entities.PaymentSplitEntity
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.utils.DateUtils
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {

    @Transaction
    open suspend fun <R> runInTransaction(block: suspend () -> R): R {
        // Room automatically handles transactions for suspend functions
        return block()
    }

    @Query("SELECT * FROM payments WHERE id = :paymentId")
    fun getPaymentById(paymentId: Int): Flow<PaymentEntity?>

    @Query("SELECT * FROM payments WHERE server_id = :serverId")
    fun getPaymentByServerId(serverId: Int): PaymentEntity

    @Query("SELECT * FROM payments WHERE transaction_id = :transactionId")
    fun getPaymentByTransactionId(transactionId: String): Flow<PaymentEntity?>

    @Query("SELECT * FROM payments WHERE group_id = :groupId")
    fun getPaymentsByGroup(groupId: Int): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE group_id = :groupId AND deleted_at IS NULL")
    suspend fun getNonArchivedPaymentsByGroup(groupId: Int): List<PaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePaymentDirect(payment: PaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePaymentsDirect(payments: List<PaymentEntity>)

    @Transaction
    suspend fun insertOrUpdatePayment(payment: PaymentEntity) {
        val timestamp = DateUtils.getCurrentTimestamp()
        val updatedPayment = payment.copy(
            updatedAt = timestamp,
            syncStatus = SyncStatus.PENDING_SYNC
        )
        insertOrUpdatePaymentDirect(updatedPayment)
    }

    @Transaction
    suspend fun insertOrUpdatePayments(payments: List<PaymentEntity>) {
        val timestamp = DateUtils.getCurrentTimestamp()
        val updatedPayments = payments.map { payment ->
            payment.copy(
                updatedAt = timestamp,
                syncStatus = SyncStatus.PENDING_SYNC
            )
        }
        insertOrUpdatePaymentsDirect(updatedPayments)
    }


    @Update
    suspend fun updatePaymentDirect(payment: PaymentEntity)

    @Transaction
    suspend fun updatePayment(payment: PaymentEntity) {
        // Create a copy of the payment with the current timestamp
        val updatedPayment = payment.copy(
            updatedAt = DateUtils.getCurrentTimestamp(),
            syncStatus = SyncStatus.PENDING_SYNC
        )
        updatePaymentDirect(updatedPayment)
    }

    @Query("UPDATE payments SET deleted_at = :deletedAt WHERE id = :paymentId")
    suspend fun archivePayment(paymentId: Int, deletedAt: String)

    @Query("UPDATE payments SET deleted_at = NULL, updated_at = CURRENT_TIMESTAMP WHERE id = :paymentId")
    suspend fun restorePayment(paymentId: Int)

    @Query("SELECT * FROM payments WHERE sync_status != 'SYNCED'")
    fun getUnsyncedPayments(): Flow<List<PaymentEntity>>

    @Query("UPDATE payments SET sync_status = :status, updated_at = CURRENT_TIMESTAMP WHERE id = :paymentId")
    suspend fun updatePaymentSyncStatus(paymentId: Int, status: SyncStatus)
}