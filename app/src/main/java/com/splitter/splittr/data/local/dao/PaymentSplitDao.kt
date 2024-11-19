package com.splitter.splittr.data.local.dao

import android.util.Log
import androidx.room.*
import com.splitter.splittr.data.local.entities.PaymentEntity
import com.splitter.splittr.data.local.entities.PaymentSplitEntity
import com.splitter.splittr.data.sync.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentSplitDao {

    @Transaction
    open suspend fun <R> runInTransaction(block: suspend () -> R): R {
        // Room automatically handles transactions for suspend functions
        return block()
    }

    @Query("SELECT * FROM payment_splits WHERE id = :paymentSplitId")
    fun getPaymentSplitById(paymentSplitId: Int): PaymentSplitEntity

    @Query("SELECT * FROM payment_splits WHERE id = :paymentSplitId")
    fun getPaymentSplitsById(paymentSplitId: Int): List<PaymentSplitEntity>

    @Query("SELECT * FROM payment_splits WHERE payment_id = :paymentId")
    fun getPaymentSplitsByPayment(paymentId: Int): Flow<List<PaymentSplitEntity>>

    @Query("SELECT * FROM payment_splits WHERE payment_id = :paymentId AND deleted_at IS NULL")
    suspend fun getNonArchivedSplitsByPayment(paymentId: Int): List<PaymentSplitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentSplit(paymentSplit: PaymentSplitEntity): Long

    @Transaction
    suspend fun insertPaymentSplitWithLogging(paymentSplit: PaymentSplitEntity): Long {
        Log.d("PaymentSplitDao", "Attempting to insert split: " +
                "id=${paymentSplit.id}, " +
                "serverId=${paymentSplit.serverId}, " +
                "paymentId=${paymentSplit.paymentId}, " +
                "userId=${paymentSplit.userId}, " +
                "amount=${paymentSplit.amount}, " +
                "currency=${paymentSplit.currency}")

        try {
            val result = insertPaymentSplit(paymentSplit)
            Log.d("PaymentSplitDao", "Successfully inserted split, returned id: $result")

            // Verify the insert
            val inserted = getPaymentSplitById(paymentSplit.id)
            Log.d("PaymentSplitDao", "Verification query result: $inserted")

            return result
        } catch (e: Exception) {
            Log.e("PaymentSplitDao", "Failed to insert split", e)
            throw e
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPaymentSplits(paymentSplits: List<PaymentSplitEntity>): List<Long>

    @Update
    suspend fun updatePaymentSplitDirect(paymentSplit: PaymentSplitEntity)

    @Transaction
    suspend fun updatePaymentSplit(paymentSplit: PaymentSplitEntity) {
        // Create a copy of the payment split with the current timestamp
        val updatedPaymentSplit = paymentSplit.copy(
            updatedAt = System.currentTimeMillis().toString(),
            syncStatus = SyncStatus.PENDING_SYNC
        )
        updatePaymentSplitDirect(updatedPaymentSplit)
    }

    @Query("SELECT * FROM payment_splits WHERE sync_status != 'SYNCED'")
    fun getUnsyncedPaymentSplits(): Flow<List<PaymentSplitEntity>>

    @Query("UPDATE payment_splits SET sync_status = :status, updated_at = :timestamp WHERE id = :splitId")
    suspend fun updatePaymentSplitSyncStatus(splitId: Int, status: SyncStatus, timestamp: String = System.currentTimeMillis().toString())

    @Transaction
    suspend fun updatePaymentSplitWithSync(split: PaymentSplitEntity, syncStatus: SyncStatus) {
        updatePaymentSplitDirect(split)
        updatePaymentSplitSyncStatus(split.id, syncStatus)
    }
}