package com.splitter.splittr.data.local.dao

import androidx.room.*
import com.splitter.splittr.data.local.entities.PaymentSplitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentSplitDao {

    @Query("SELECT * FROM payment_splits WHERE id = :paymentSplitId")
    fun getPaymentSplitById(paymentSplitId: Int): PaymentSplitEntity

    @Query("SELECT * FROM payment_splits WHERE id = :paymentSplitId")
    fun getPaymentSplitsById(paymentSplitId: Int): List<PaymentSplitEntity>

    @Query("SELECT * FROM payment_splits WHERE payment_id = :paymentId")
    fun getPaymentSplitsByPayment(paymentId: Int): Flow<List<PaymentSplitEntity>>

    @Query("""
        SELECT * FROM payment_splits 
        WHERE payment_id = :paymentId 
        AND deleted_at IS NULL
    """)
    suspend fun getNonArchivedSplitsByPayment(paymentId: Int): List<PaymentSplitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentSplit(paymentSplit: PaymentSplitEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPaymentSplits(paymentSplits: List<PaymentSplitEntity>): List<Long>

    @Update
    suspend fun updatePaymentSplit(paymentSplit: PaymentSplitEntity)

    @Query("SELECT * FROM payment_splits WHERE sync_status != 'SYNCED'")
    fun getUnsyncedPaymentSplits(): Flow<List<PaymentSplitEntity>>

    @Query("UPDATE payment_splits SET sync_status = :status WHERE id = :splitId")
    suspend fun updatePaymentSplitSyncStatus(splitId: Int, status: String)
}