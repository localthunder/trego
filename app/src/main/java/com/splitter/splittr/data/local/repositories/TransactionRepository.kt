package com.splitter.splittr.data.local.repositories

import android.content.Context
import android.util.Log
import com.splitter.splittr.data.cache.TransactionCache
import com.splitter.splittr.data.local.dao.TransactionDao
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.model.Transaction
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.NetworkUtils
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val apiService: ApiService,
    private val dispatchers: CoroutineDispatchers,
    private val context: Context
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun getTransactionsByUserId(userId: Int) = transactionDao.getTransactionsByUserId(userId)

    fun getTransactionById(transactionId: String) = transactionDao.getTransactionById(transactionId)

    suspend fun fetchRecentTransactions(userId: Int): List<Transaction>? = withContext(dispatchers.io) {
        val cachedTransactions = TransactionCache.getRecentTransactions()
        if (cachedTransactions != null) {
            return@withContext cachedTransactions
        }

        return@withContext try {
            val dateFrom = dateFormat.format(Calendar.getInstance().apply {
                add(Calendar.MONTH, -1) // Fetch recent transactions from the past month
            }.time)
            val response = apiService.getRecentTransactions(userId, dateFrom)
            TransactionCache.saveRecentTransactions(response)
            response
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchNonRecentTransactions(userId: Int): List<Transaction>? = withContext(dispatchers.io) {
        val cachedTransactions = TransactionCache.getNonRecentTransactions()
        if (cachedTransactions != null) {
            return@withContext cachedTransactions
        }

        return@withContext try {
            val dateTo = dateFormat.format(Calendar.getInstance().apply {
                add(Calendar.MONTH, -1) // Fetch non-recent transactions up to the past month
            }.time)
            val response = apiService.getNonRecentTransactions(userId, dateTo)
            TransactionCache.saveNonRecentTransactions(response)
            response
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createTransaction(transaction: Transaction): Result<Transaction> = withContext(dispatchers.io) {
        try {
            transactionDao.insertTransaction(transaction.toEntity(SyncStatus.PENDING_SYNC))
            val serverTransaction = apiService.createTransaction(transaction)
            transactionDao.insertTransaction(serverTransaction.toEntity(SyncStatus.SYNCED))
            Result.success(serverTransaction)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveTransaction(transaction: Transaction): Transaction? = withContext(dispatchers.io) {
        return@withContext try {
            apiService.createTransaction(transaction)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun syncTransactions() = withContext(dispatchers.io) {

        val userId = getUserIdFromPreferences(context)

        if (NetworkUtils.isOnline()) {
            // 1. Sync local unsaved changes to the server
            transactionDao.getUnsyncedTransactions().first().forEach { transactionEntity ->
                try {
                    val serverTransaction = apiService.createTransaction(transactionEntity.toModel())
                    transactionDao.insertTransaction(serverTransaction.toEntity(SyncStatus.SYNCED))
                } catch (e: Exception) {
                    transactionDao.updateTransactionSyncStatus(transactionEntity.transactionId, SyncStatus.SYNC_FAILED.name)
                    Log.e("TransactionRepository", "Failed to sync transaction ${transactionEntity.transactionId}", e)
                }
            }

            // 2. Fetch transactions from the server
            try {
                val recentDate = LocalDate.now().minusDays(7).format(DateTimeFormatter.ISO_DATE)

                // Fetch recent transactions
                val recentTransactions =
                    userId?.let { apiService.getRecentTransactions(it, recentDate) }
                recentTransactions?.forEach { serverTransaction ->
                    val localTransaction = transactionDao.getTransactionById(serverTransaction.transactionId).first()
                    if (localTransaction == null) {
                        // New transaction from server, insert it
                        transactionDao.insertTransaction(serverTransaction.toEntity(SyncStatus.SYNCED))
                    } else {
                        // Update existing transaction
                        transactionDao.updateTransaction(serverTransaction.toEntity(SyncStatus.SYNCED))
                    }
                }

                // Fetch non-recent transactions
                val nonRecentTransactions =
                    userId?.let { apiService.getNonRecentTransactions(it, recentDate) }
                nonRecentTransactions?.forEach { serverTransaction ->
                    val localTransaction = transactionDao.getTransactionById(serverTransaction.transactionId).first()
                    if (localTransaction == null) {
                        // New transaction from server, insert it
                        transactionDao.insertTransaction(serverTransaction.toEntity(SyncStatus.SYNCED))
                    } else {
                        // Update existing transaction
                        transactionDao.updateTransaction(serverTransaction.toEntity(SyncStatus.SYNCED))
                    }
                }
            } catch (e: Exception) {
                Log.e("TransactionRepository", "Failed to fetch transactions from server", e)
            }
        } else {
            Log.e("TransactionRepository", "No internet connection available for syncing transactions")
        }
    }
}