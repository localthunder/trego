package com.splitter.splitter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.splitter.splitter.data.local.entities.TransactionEntity

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE userId = :userId")
    fun getTransactionsForUser(userId: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE transactionId = :transactionId")
    fun getTransactionById(transactionId: String): TransactionEntity?

    @Insert
    fun insertTransaction(transaction: TransactionEntity)

    @Insert
    fun insertTransactions(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE transactionId = :transactionId")
    fun deleteTransaction(transactionId: String)

    @Query("DELETE FROM transactions WHERE userId = :userId")
    fun deleteTransactionsForUser(userId: Int)
}
