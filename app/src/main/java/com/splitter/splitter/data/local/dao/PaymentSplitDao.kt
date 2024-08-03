package com.splitter.splitter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.splitter.splitter.data.local.entities.PaymentSplitEntity

@Dao
interface PaymentSplitDao {

    @Insert
    suspend fun insert(paymentSplit: PaymentSplitEntity)

    @Query("SELECT * FROM payment_splits")
    suspend fun getAllPaymentSplits(): List<PaymentSplitEntity>

    @Query("SELECT * FROM payment_splits WHERE id = :id")
    suspend fun getPaymentSplitById(id: Int): PaymentSplitEntity?

    @Query("DELETE FROM payment_splits WHERE id = :id")
    suspend fun deletePaymentSplitById(id: Int)
}
