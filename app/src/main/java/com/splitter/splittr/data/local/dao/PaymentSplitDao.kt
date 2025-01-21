package com.splitter.splittr.data.local.dao

import android.util.Log
import androidx.room.*
import com.splitter.splittr.data.local.entities.PaymentEntity
import com.splitter.splittr.data.local.entities.PaymentSplitEntity
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.utils.DateUtils
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

    @Query("SELECT * FROM payment_splits WHERE server_id = :serverId")
    fun getPaymentSplitByServerId(serverId: Int): PaymentSplitEntity

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
            updatedAt = DateUtils.getCurrentTimestamp(),
            syncStatus = SyncStatus.PENDING_SYNC
        )
        updatePaymentSplitDirect(updatedPaymentSplit)
    }

    @Transaction
    suspend fun updatePaymentSplitWithTimestamp(
        split: PaymentSplitEntity,
        syncStatus: SyncStatus
    ) {
        val updatedSplit = split.copy(
            updatedAt = DateUtils.getCurrentTimestamp()
        )
        updatePaymentSplitDirect(updatedSplit.copy(syncStatus = syncStatus))
    }

    @Query("SELECT * FROM payment_splits WHERE sync_status != 'SYNCED'")
    fun getUnsyncedPaymentSplits(): Flow<List<PaymentSplitEntity>>

    @Query("UPDATE payment_splits SET sync_status = :status, updated_at = :timestamp WHERE id = :splitId")
    suspend fun updatePaymentSplitSyncStatus(splitId: Int, status: SyncStatus, timestamp: String = DateUtils.getCurrentTimestamp())

    @Query("UPDATE payment_splits SET sync_status = :status, updated_at = :timestamp WHERE payment_id = :paymentId")
    suspend fun updatePaymentSplitsSyncStatus(
        paymentId: Int,
        status: SyncStatus,
        timestamp: String = DateUtils.getCurrentTimestamp()
    )

    @Transaction
    suspend fun updatePaymentSplitWithSync(split: PaymentSplitEntity, syncStatus: SyncStatus) {
        updatePaymentSplitDirect(split)
        updatePaymentSplitSyncStatus(split.id, syncStatus)
    }


    @Transaction
    suspend fun insertOrUpdatePaymentSplit(split: PaymentSplitEntity) {
        // Try to find existing split by server ID if it exists
        val existingSplit = split.serverId?.let { serverId ->
            getPaymentSplitByServerId(serverId)
        }

        if (existingSplit != null) {
            // Update existing split while preserving its local ID
            val updatedSplit = split.copy(
                id = existingSplit.id,
                updatedAt = DateUtils.getCurrentTimestamp()
            )
            updatePaymentSplitDirect(updatedSplit)
        } else {
            // Insert new split
            insertPaymentSplit(split)
        }
    }

    @Transaction
    suspend fun insertOrUpdatePaymentSplits(splits: List<PaymentSplitEntity>) {
        splits.forEach { split ->
            insertOrUpdatePaymentSplit(split)
        }
    }

    @Query("SELECT * FROM payment_splits WHERE payment_id = :paymentId AND user_id = :userId LIMIT 1")
    suspend fun getPaymentSplitByPaymentIdAndUserId(paymentId: Int, userId: Int): PaymentSplitEntity?
}