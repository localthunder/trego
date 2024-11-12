package com.splitter.splittr.data.repositories

import android.accounts.Account
import android.content.Context
import android.util.Log
import com.splitter.splittr.data.local.dao.TransactionDao
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.model.Transaction
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.BankAccountDao
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.ui.viewmodels.TransactionViewModel
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
    private val bankAccountDao: BankAccountDao,
    private val apiService: ApiService,
    private val dispatchers: CoroutineDispatchers,
    private val context: Context
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    data class TransactionsApiResponse(
        val transactions: List<Transaction>,
        val accountsNeedingReauthentication: List<TransactionViewModel.AccountReauthState>
    )

    fun getTransactionsByUserId(userId: Int) = transactionDao.getTransactionsByUserId(userId)

    fun getTransactionById(transactionId: String) = transactionDao.getTransactionById(transactionId)

    suspend fun fetchTransactions(userId: Int): List<Transaction>? = withContext(dispatchers.io) {
        Log.d("TransactionRepository", "Fetching transactions for user $userId")

        // Check if cache is fresh
        if (TransactionCache.isCacheFresh()) {
            Log.d("TransactionRepository", "Using fresh cache")
            return@withContext TransactionCache.getTransactions()
        }

        // If cache isn't fresh, fetch from API
        return@withContext try {
            Log.d("TransactionRepository", "Cache not fresh, fetching from API")
            val response = apiService.getTransactionsByUserId(userId)

            // Save to cache if we got transactions
            if (response.transactions.isNotEmpty()) {
                TransactionCache.saveTransactions(response.transactions)
                Log.d("TransactionRepository", "Saved ${response.transactions.size} transactions to cache")
            } else {
                Log.d("TransactionRepository", "No transactions received from API")
            }

            response.transactions
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error fetching transactions", e)
            // If API fails, return cached data even if stale
            TransactionCache.getTransactions() ?: null
        }
    }

    suspend fun fetchRecentTransactions(userId: Int): List<Transaction>? =
        withContext(dispatchers.io) {
            val cachedTransactions = TransactionCache.getTransactions()
            if (cachedTransactions != null) {
                return@withContext cachedTransactions
            }

            return@withContext try {
                val dateFrom = dateFormat.format(Calendar.getInstance().apply {
                    add(Calendar.MONTH, -1) // Fetch recent transactions from the past month
                }.time)
                val response = apiService.getRecentTransactions(userId, dateFrom)
                TransactionCache.saveTransactions(response)
                response
            } catch (e: Exception) {
                null
            }
        }

    suspend fun fetchNonRecentTransactions(userId: Int): List<Transaction>? =
        withContext(dispatchers.io) {
            val cachedTransactions = TransactionCache.getTransactions()
            if (cachedTransactions != null) {
                return@withContext cachedTransactions
            }

            return@withContext try {
                val dateTo = dateFormat.format(Calendar.getInstance().apply {
                    add(Calendar.MONTH, -1) // Fetch non-recent transactions up to the past month
                }.time)
                val response = apiService.getNonRecentTransactions(userId, dateTo)
                TransactionCache.saveTransactions(response)
                response
            } catch (e: Exception) {
                null
            }
        }

    suspend fun getAccountsNeedingReauth(userId: Int): List<TransactionViewModel.AccountReauthState> = withContext(dispatchers.io) {
        try {
            val localAccounts = bankAccountDao.getAccountsNeedingReauthByUserId(userId).first()
            Log.d("TransactionRepository", "Found ${localAccounts.size} accounts needing reauth in local DB")

            return@withContext localAccounts.map { account ->
                TransactionViewModel.AccountReauthState(
                    accountId = account.accountId,
                    institutionId = account.institutionId ?: "Unknown Bank"
                )
            }
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error getting reauth accounts from local DB", e)
            emptyList()
        }
    }

    suspend fun createTransaction(transaction: Transaction): Result<Transaction> =
        withContext(dispatchers.io) {
            try {
                transactionDao.insertTransaction(transaction.toEntity(SyncStatus.PENDING_SYNC))
                val serverTransaction = apiService.createTransaction(transaction)
                transactionDao.insertTransaction(serverTransaction.toEntity(SyncStatus.SYNCED))
                Result.success(serverTransaction)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun saveTransaction(transaction: Transaction): Result<Transaction> = withContext(dispatchers.io) {
        try {
            // Save to local DB with pending sync status
            transactionDao.insertTransaction(transaction.toEntity(SyncStatus.PENDING_SYNC))

            // Try to sync immediately if online
            if (NetworkUtils.isOnline()) {
                try {
                    val serverTransaction = apiService.createTransaction(transaction)
                    transactionDao.insertTransaction(serverTransaction.toEntity(SyncStatus.SYNCED))
                    Result.success(serverTransaction)
                } catch (e: Exception) {
                    Log.e("TransactionRepository", "Failed to sync new transaction", e)
                    Result.success(transaction) // Return local transaction if sync fails
                }
            } else {
                Result.success(transaction)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchAndCacheTransactions(userId: Int?) {
        if (userId == null) return

        try {
            Log.d("TransactionRepository", "Fetching fresh transactions from GoCardless")

            val response = apiService.getTransactionsByUserId(userId)
            val transactions = response.transactions
            val accountsNeedingReauth = response.accountsNeedingReauthentication

            // Cache the transactions
            TransactionCache.saveTransactions(transactions)
            Log.d("TransactionRepository", "Cached ${transactions.size} transactions")

            // Handle reauth accounts
            accountsNeedingReauth.forEach { reauthAccount ->
                try {
                    Log.d("TransactionRepository",
                        "Account ${reauthAccount.accountId} needs reauthorization")
                    bankAccountDao.updateNeedsReauthentication(reauthAccount.accountId, true)
                } catch (e: Exception) {
                    Log.e("TransactionRepository",
                        "Failed to mark account ${reauthAccount.accountId} for reauth", e)
                }
            }
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error fetching transactions from GoCardless", e)
            TransactionCache.setError("Failed to fetch transactions: ${e.message}")
        }
    }

    suspend fun syncTransactions() = withContext(dispatchers.io) {
        val userId = getUserIdFromPreferences(context)

        if (!NetworkUtils.isOnline()) {
            Log.e("TransactionRepository", "No internet connection available")
            return@withContext
        }

        // 1. First sync saved transactions with server
        syncSavedTransactions(userId)

        // 2. Then fetch fresh transactions from GoCardless (via server) and cache them
        fetchAndCacheTransactions(userId)
    }

    private suspend fun syncSavedTransactions(userId: Int?) {
        if (userId == null) return

        try {
            // Sync local unsaved changes to server
            val unsyncedTransactions = transactionDao.getUnsyncedTransactions().first()
            Log.d("TransactionRepository", "Found ${unsyncedTransactions.size} unsynced transactions")

            unsyncedTransactions.forEach { transactionEntity ->
                try {
                    val serverTransaction = apiService.createTransaction(transactionEntity.toModel())
                    transactionDao.insertTransaction(serverTransaction.toEntity(SyncStatus.SYNCED))
                    Log.d("TransactionRepository", "Successfully synced transaction ${transactionEntity.transactionId}")
                } catch (e: Exception) {
                    transactionDao.updateTransactionSyncStatus(
                        transactionEntity.transactionId,
                        SyncStatus.SYNC_FAILED
                    )
                    Log.e("TransactionRepository", "Failed to sync transaction ${transactionEntity.transactionId}", e)
                }
            }
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error syncing saved transactions", e)
        }
    }

//    suspend fun syncTransactions() = withContext(dispatchers.io) {
//        val userId = getUserIdFromPreferences(context)
//
//        if (NetworkUtils.isOnline()) {
//            Log.d("TransactionRepository", "Starting transaction sync process")
//
//            // 1. Sync local unsaved changes to the server
//            try {
//                val unsyncedTransactions = transactionDao.getUnsyncedTransactions().first()
//                Log.d(
//                    "TransactionRepository",
//                    "Found ${unsyncedTransactions.size} unsynced transactions"
//                )
//
//                unsyncedTransactions.forEach { transactionEntity ->
//                    try {
//                        val serverTransaction =
//                            apiService.createTransaction(transactionEntity.toModel())
//                        transactionDao.insertTransaction(serverTransaction.toEntity(SyncStatus.SYNCED))
//                        Log.d(
//                            "TransactionRepository",
//                            "Successfully synced transaction ${transactionEntity.transactionId}"
//                        )
//                    } catch (e: Exception) {
//                        transactionDao.updateTransactionSyncStatus(
//                            transactionEntity.transactionId,
//                            SyncStatus.SYNC_FAILED.name
//                        )
//                        Log.e(
//                            "TransactionRepository",
//                            "Failed to sync transaction ${transactionEntity.transactionId}",
//                            e
//                        )
//                    }
//                }
//            } catch (e: Exception) {
//                Log.e("TransactionRepository", "Error syncing local transactions", e)
//            }
//
//            // 2. Fetch transactions from the server
//            try {
//                if (userId != null) {
//                    Log.d(
//                        "TransactionRepository",
//                        "Fetching transactions from server for user $userId"
//                    )
//
//                    val response = apiService.getTransactionsByUserId(userId)
//                    val transactions = response.transactions
//                    val accountsNeedingReauth = response.accountsNeedingReauthentication
//
//                    Log.d(
//                        "TransactionRepository",
//                        "Received ${transactions.size} transactions from server and " +
//                                "${accountsNeedingReauth.size} accounts need reauth"
//                    )
//
//                    // Handle transactions
//                    transactions.forEach { serverTransaction ->
//                        try {
//                            val localTransaction =
//                                transactionDao.getTransactionById(serverTransaction.transactionId)
//                                    .first()
//                            if (localTransaction == null) {
//                                // New transaction from server, insert it
//                                transactionDao.insertTransaction(
//                                    serverTransaction.toEntity(
//                                        SyncStatus.SYNCED
//                                    )
//                                )
//                                Log.d(
//                                    "TransactionRepository",
//                                    "Inserted new transaction ${serverTransaction.transactionId}"
//                                )
//                            } else {
//                                // Update existing transaction
//                                transactionDao.updateTransaction(
//                                    serverTransaction.toEntity(
//                                        SyncStatus.SYNCED
//                                    )
//                                )
//                                Log.d(
//                                    "TransactionRepository",
//                                    "Updated existing transaction ${serverTransaction.transactionId}"
//                                )
//                            }
//                        } catch (e: Exception) {
//                            Log.e(
//                                "TransactionRepository",
//                                "Error processing transaction ${serverTransaction.transactionId}", e
//                            )
//                        }
//                    }
//
//                    // Handle accounts needing reauthorization
//                    accountsNeedingReauth.forEach { reauthAccount ->
//                        Log.d(
//                            "TransactionRepository",
//                            "Account ${reauthAccount.accountId} (${reauthAccount.institutionId}) "
//                        )
//
//                        try {
//                            bankAccountDao.updateNeedsReauthentication(reauthAccount.accountId, true)
//                        } catch (e: Exception) {
//                            Log.e(
//                                "TransactionRepository",
//                                "Failed to mark account ${reauthAccount.accountId} for reauth", e
//                            )
//                        }
//                    }
//                } else {
//                    Log.w("TransactionRepository", "Cannot sync with server - userId is null")
//                }
//            } catch (e: Exception) {
//                Log.e("TransactionRepository", "Failed to fetch transactions from server", e)
//            }
//        } else {
//            Log.e(
//                "TransactionRepository",
//                "No internet connection available for syncing transactions"
//            )
//        }
//    }
}