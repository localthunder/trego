package com.splitter.splitter.data.local.repositories

import com.splitter.splitter.data.local.dao.TransactionDao
import com.splitter.splitter.data.local.entities.TransactionEntity
import com.splitter.splitter.data.network.ApiService
import com.splitter.splitter.model.Transaction
import com.splitter.splitter.data.extensions.toEntity

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val apiService: ApiService
) {
    fun getTransactions(userId: Int): List<TransactionEntity> {
        // Fetch from Room database first
        val localTransactions = transactionDao.getTransactionsForUser(userId)
        if (localTransactions.isNotEmpty()) {
            return localTransactions
        }

        // If local data is not available, fetch from the API
        val response = apiService.getTransactionByUserId(userId).execute()
        if (response.isSuccessful) {
            response.body()?.let { transactions ->
                // Save the fetched transactions to Room
                transactionDao.insertTransactions(transactions.map { it.toEntity() })
                return transactions.map { it.toEntity() }
            }
        }

        // Return an empty list if no data is available
        return emptyList()
    }

    fun getTransactionById(transactionId: String): TransactionEntity? {
        // Fetch from Room database
        return transactionDao.getTransactionById(transactionId)
    }

    fun insertTransaction(transaction: Transaction) {
        // Insert into Room database
        transactionDao.insertTransaction(transaction.toEntity())
    }

    fun deleteTransaction(transactionId: String) {
        // Delete from Room database
        transactionDao.deleteTransaction(transactionId)
    }
}
