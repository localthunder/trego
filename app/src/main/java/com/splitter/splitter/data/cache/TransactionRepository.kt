package com.splitter.splitter.data.cache

import com.splitter.splitter.model.Transaction
import com.splitter.splitter.data.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.await
import java.text.SimpleDateFormat
import java.util.*

class TransactionRepository(private val apiService: ApiService) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    suspend fun fetchRecentTransactions(userId: Int): List<Transaction>? = withContext(Dispatchers.IO) {
        val cachedTransactions = TransactionCache.getRecentTransactions()
        if (cachedTransactions != null) {
            return@withContext cachedTransactions
        }

        return@withContext try {
            val dateFrom = dateFormat.format(Calendar.getInstance().apply {
                add(Calendar.MONTH, -1) // Fetch recent transactions from the past month
            }.time)
            val response = apiService.getRecentTransactions(userId, dateFrom).await()
            TransactionCache.saveRecentTransactions(response)
            response
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchNonRecentTransactions(userId: Int): List<Transaction>? = withContext(Dispatchers.IO) {
        val cachedTransactions = TransactionCache.getNonRecentTransactions()
        if (cachedTransactions != null) {
            return@withContext cachedTransactions
        }

        return@withContext try {
            val dateTo = dateFormat.format(Calendar.getInstance().apply {
                add(Calendar.MONTH, -1) // Fetch non-recent transactions up to the past month
            }.time)
            val response = apiService.getNonRecentTransactions(userId, dateTo).await()
            TransactionCache.saveNonRecentTransactions(response)
            response
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveTransaction(transaction: Transaction): Transaction? = withContext(Dispatchers.IO) {
        return@withContext try {
            val response = apiService.createTransaction(transaction).await()
            response
        } catch (e: Exception) {
            null
        }
    }
}
