package com.splitter.splitter.data.local.dao


import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.splitter.splitter.data.local.entities.PaymentEntity

@Dao
interface PaymentDao {

    @Insert
    suspend fun insert(payment: PaymentEntity)

    @Query("SELECT * FROM payments")
    suspend fun getAllPayments(): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getPaymentById(id: Int): PaymentEntity?

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun deletePaymentById(id: Int)
}
