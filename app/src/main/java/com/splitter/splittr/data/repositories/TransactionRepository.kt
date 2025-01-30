package com.splitter.splittr.data.repositories

import android.accounts.Account
import android.content.Context
import android.util.Log
import com.splitter.splittr.MyApplication
import com.splitter.splittr.data.cache.TransactionCacheManager
import com.splitter.splittr.data.local.dao.TransactionDao
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.model.Transaction
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.extensions.toTransaction
import com.splitter.splittr.data.local.dao.BankAccountDao
import com.splitter.splittr.data.local.dao.CachedTransactionDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.data.sync.SyncableRepository
import com.splitter.splittr.data.sync.managers.TransactionSyncManager
import com.splitter.splittr.ui.viewmodels.TransactionViewModel
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.DateUtils
import com.splitter.splittr.utils.NetworkUtils
import com.splitter.splittr.utils.ServerIdUtil
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first
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
    private val transactionSyncManager: TransactionSyncManager,
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
//    suspend fun fetchTransactions(userId: Int): List<Transaction>? = withContext(dispatchers.io) {
//        Log.d("TransactionRepository", "Fetching transactions for user $userId")
//        try {
//            // Return cached transactions immediately if they exist
//            val cachedTransactions = TransactionCache.getTransactions()
//
//            // If cache isn't fresh or empty, fetch new data
//            if (!TransactionCache.isCacheFresh() || cachedTransactions == null) {
//                Log.d("TransactionRepository", "Cache not fresh or empty, fetching from API")
//                try {
//                    // Convert local user ID to server ID
//                    val serverUserId = ServerIdUtil.getServerId(userId, "users", context)
//                        ?: throw Exception("Could not resolve server user ID for $userId")
//
//                    val response = apiService.getTransactionsByUserId(serverUserId)
//
//                    if (response.transactions.isNotEmpty()) {
//                        // Convert the server response transactions to use local user IDs
//                        val localizedTransactions = response.transactions.map { transaction ->
//                            transaction.copy(
//                                userId = userId  // Use the local user ID
//                            )
//                        }
//
//                        TransactionCache.saveTransactions(localizedTransactions)
//                        Log.d("TransactionRepository", "Updated cache with ${localizedTransactions.size} transactions")
//                        return@withContext localizedTransactions
//                    }
//                } catch (e: Exception) {
//                    Log.e("TransactionRepository", "Error refreshing transactions", e)
//                    if (cachedTransactions != null) {
//                        return@withContext cachedTransactions
//                    }
//                    throw e
//                }
//            }
//
//            cachedTransactions
//        } catch (e: Exception) {
//            Log.e("TransactionRepository", "Error in transaction fetch workflow", e)
//            throw e
//        }
//    }

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
            transactionSyncManager.fetchAndCacheTransactions(userId)

            // Then do database sync
            transactionSyncManager.performSync()

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