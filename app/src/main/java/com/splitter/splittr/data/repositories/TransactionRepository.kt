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
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.data.sync.SyncableRepository
import com.splitter.splittr.data.sync.managers.TransactionSyncManager
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
    private val context: Context,
    private val syncMetadataDao: SyncMetadataDao,
    private val transactionSyncManager: TransactionSyncManager
) : SyncableRepository {

    override val entityType = "transactions"
    override val syncPriority = 3
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

    override suspend fun sync() = withContext(dispatchers.io) {
        try {
            Log.d(TAG, "Starting transaction sync process")

            // Perform the specialized transaction sync
            when (val result = transactionSyncManager.performFullSync()) {
                is TransactionSyncManager.TransactionSyncResult.Success -> {
                    Log.d(TAG, "Transaction sync completed successfully. Synced: ${result.syncedCount}")

                    // Update sync metadata
                    syncMetadataDao.update(entityType) {
                        it.copy(
                            lastSyncTimestamp = System.currentTimeMillis(),
                            syncStatus = SyncStatus.SYNCED,
                            lastSyncResult = "Successfully synced ${result.syncedCount} transactions"
                        )
                    }
                }

                is TransactionSyncManager.TransactionSyncResult.PartialSuccess -> {
                    Log.w(TAG, "Transaction sync partially completed. " +
                            "Synced: ${result.syncedCount}, Failed: ${result.failedCount}")

                    syncMetadataDao.update(entityType) {
                        it.copy(
                            lastSyncTimestamp = System.currentTimeMillis(),
                            syncStatus = SyncStatus.SYNC_FAILED,
                            lastSyncResult = "Partial sync: ${result.syncedCount} succeeded, " +
                                    "${result.failedCount} failed"
                        )
                    }
                }

                is TransactionSyncManager.TransactionSyncResult.Error -> {
                    Log.e(TAG, "Transaction sync failed: ${result.message}")

                    syncMetadataDao.update(entityType) {
                        it.copy(
                            syncStatus = SyncStatus.SYNC_FAILED,
                            lastSyncResult = "Sync failed: ${result.message}"
                        )
                    }
                    throw Exception(result.message)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during transaction sync", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "TransactionRepository"
    }
}