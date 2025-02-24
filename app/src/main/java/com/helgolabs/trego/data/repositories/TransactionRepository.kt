package com.helgolabs.trego.data.repositories

import android.accounts.Account
import android.content.Context
import android.util.Log
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.cache.TransactionCacheManager
import com.helgolabs.trego.data.local.dao.TransactionDao
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.model.Transaction
import com.helgolabs.trego.data.extensions.toEntity
import com.helgolabs.trego.data.extensions.toModel
import com.helgolabs.trego.data.extensions.toTransaction
import com.helgolabs.trego.data.local.dao.BankAccountDao
import com.helgolabs.trego.data.local.dao.CachedTransactionDao
import com.helgolabs.trego.data.local.dao.SyncMetadataDao
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.data.sync.SyncableRepository
import com.helgolabs.trego.data.sync.managers.TransactionSyncManager
import com.helgolabs.trego.ui.viewmodels.TransactionViewModel
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.NetworkUtils
import com.helgolabs.trego.utils.ServerIdUtil
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.PrivateKey
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val bankAccountDao: BankAccountDao,
    private val cachedTransactionDao: CachedTransactionDao,
    private val apiService: ApiService,
    private val dispatchers: CoroutineDispatchers,
    private val context: Context,
    private val syncMetadataDao: SyncMetadataDao,
    val transactionSyncManager: TransactionSyncManager,
    private val cacheManager: TransactionCacheManager
) : SyncableRepository {

    override val entityType = "transactions"
    override val syncPriority = 3
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val myApplication = context.applicationContext as MyApplication

    data class TransactionsApiResponse(
        val transactions: List<Transaction>,
        val accountsNeedingReauthentication: List<TransactionViewModel.AccountReauthState>
    )

    fun getTransactionsByUserId(userId: Int) = transactionDao.getTransactionsByUserId(userId)

    fun getTransactionById(transactionId: String) = transactionDao.getTransactionById(transactionId)

    suspend fun fetchTransactions(userId: Int): List<Transaction>? = withContext(dispatchers.io) {
        try {
            // Check cache first
            val cachedTransactions = cachedTransactionDao.getValidCachedTransactions(userId)
                .first()
                .map { it.toTransaction() }

            if (cachedTransactions.isNotEmpty()) {
                return@withContext cachedTransactions
            }

            val cacheManager = myApplication.transactionCacheManager
            // If cache is empty or stale, check if we can refresh
            if (cacheManager.shouldRefreshCache(userId)) {
                val serverTransactions = fetchFromServer(userId)
                if (serverTransactions != null) {
                    cacheManager.cacheTransactions(userId, serverTransactions)
                }
                return@withContext serverTransactions
            }

            // If we can't refresh due to rate limits, return cached data even if stale
            return@withContext cachedTransactions
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error fetching transactions", e)
            throw e
        }
    }

    suspend fun fetchAccountTransactions(accountId: String, userId: Int): List<Transaction>? = withContext(dispatchers.io) {
        try {
            Log.d(TAG, "Fetching transactions from server for account $accountId")

            val response = apiService.getAccountTransactions(accountId)

            if (response.isNotEmpty()) {
                // Convert the server response transactions to use local IDs
                val localizedTransactions = response.map { transaction ->
                    transaction.copy(userId = userId)  // Use the local user ID
                }

                // Get existing cached transactions
                val existingTransactions = cachedTransactionDao.getValidCachedTransactions(userId)
                    .first()
                    .map { it.toTransaction() }

                // Merge new transactions with existing ones
                val mergedTransactions = (existingTransactions + localizedTransactions)
                    .distinctBy { it.transactionId }
                    .sortedByDescending { it.bookingDateTime }

                // Update the cache with merged transactions
                cacheManager.cacheTransactions(userId, mergedTransactions)

                Log.d(TAG, "Successfully fetched and cached ${localizedTransactions.size} transactions from server")
                return@withContext localizedTransactions
            } else {
                Log.d(TAG, "No transactions found on server for account $accountId")
                return@withContext emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from server", e)
            return@withContext null
        }
    }

    private suspend fun fetchFromServer(userId: Int): List<Transaction>? {
        return try {
            Log.d(TAG, "Fetching transactions from server for user $userId")

            // Convert local user ID to server ID
            val serverUserId = ServerIdUtil.getServerId(userId, "users", context)
                ?: throw Exception("Could not resolve server user ID for $userId")

            val response = apiService.getTransactionsByUserId(serverUserId)

            if (response.transactions.isNotEmpty()) {
                // Convert the server response transactions to use local user IDs
                val localizedTransactions = response.transactions.map { transaction ->
                    transaction.copy(userId = userId)  // Use the local user ID
                }

                Log.d(TAG, "Successfully fetched ${localizedTransactions.size} transactions from server")
                localizedTransactions
            } else {
                Log.d(TAG, "No transactions found on server")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from server", e)
            null
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

    suspend fun createTransaction(transaction: Transaction): Result<Transaction> = withContext(dispatchers.io) {
        try {
            // Save locally first
            val localTransaction = transaction.copy(
                updatedAt = DateUtils.getCurrentTimestamp()
            ).toEntity(SyncStatus.PENDING_SYNC)

            transactionDao.insertTransaction(localTransaction)

            // Try to sync if online
            if (NetworkUtils.isOnline()) {
                try {
                    val serverTransactionModel = myApplication.entityServerConverter.convertTransactionToServer(transaction.toEntity())
                    val serverTransaction = apiService.createTransaction(serverTransactionModel.getOrThrow())

                    // Update local with server data while preserving local fields
                    val syncedTransaction = serverTransaction.copy(
                        transactionId = transaction.transactionId,
                        updatedAt = DateUtils.getCurrentTimestamp()
                    ).toEntity(SyncStatus.SYNCED)

                    transactionDao.insertTransaction(syncedTransaction)
                    Result.success(syncedTransaction.toModel())
                } catch (e: Exception) {
                    Log.e("TransactionRepository", "Failed to sync with server", e)
                    // Return local transaction if sync fails
                    Result.success(localTransaction.toModel())
                }
            } else {
                Result.success(localTransaction.toModel())
            }
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Failed to create transaction", e)
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

    override suspend fun sync() = withContext(dispatchers.io) {
        try {
            Log.d(TAG, "Starting transaction sync process")

            // First do GoCardless fetch and cache
            val userId = getUserIdFromPreferences(context) ?: throw IllegalStateException("User ID not found")

//            // Launch fetch and cache on a separate coroutine - currently commented out because it's called in SyncWorker
//            val fetchJob = launch {
//                try {
//                    Log.d(TAG, "Starting fetch and cache operation")
//                    transactionSyncManager.fetchAndCacheTransactions(userId)
//                    Log.d(TAG, "Completed fetch and cache operation")
//                } catch (e: Exception) {
//                    Log.e(TAG, "Error in fetch and cache operation", e)
//                    // Don't throw here - we'll continue with the rest of the sync
//                }
//            }

            // Then do database sync
            Log.d(TAG, "Starting local transaction sync")
            transactionSyncManager.performSync()
            Log.d(TAG, "Completed local transaction sync")


//            // Wait for fetch job to complete before finalizing
//            fetchJob.join()

            syncMetadataDao.update(entityType) {
                it.copy(
                    lastSyncTimestamp = System.currentTimeMillis(),
                    syncStatus = SyncStatus.SYNCED,
                    lastSyncResult = "Successfully synced transactions"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during transaction sync", e)
            syncMetadataDao.update(entityType) {
                it.copy(
                    syncStatus = SyncStatus.SYNC_FAILED,
                    lastSyncResult = "Sync failed: ${e.message}"
                )
            }
            throw e
        }
    }

    companion object {
        private const val TAG = "TransactionRepository"
    }
}