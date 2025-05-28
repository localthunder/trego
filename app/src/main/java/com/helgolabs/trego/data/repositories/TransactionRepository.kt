package com.helgolabs.trego.data.repositories

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
import com.helgolabs.trego.data.local.dataClasses.AccountReauthState
import com.helgolabs.trego.data.local.dataClasses.RateLimitInfo
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.data.sync.SyncableRepository
import com.helgolabs.trego.data.sync.managers.TransactionSyncManager
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.NetworkUtils
import com.helgolabs.trego.utils.ServerIdUtil
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

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

    // Add state for tracking rate limit info
    private val _rateLimitInfo = MutableStateFlow(RateLimitInfo(
        remainingCalls = cacheManager.getRemainingApiCalls(),
        maxCalls = 4,
        cooldownMinutesRemaining = cacheManager.getCooldownTimeRemaining(),
        timeUntilReset = cacheManager.getTimeUntilApiReset()
    ))
    val rateLimitInfo: StateFlow<RateLimitInfo> = _rateLimitInfo

    // Flag to indicate if a refresh is in progress
    private val isRefreshing = AtomicBoolean(false)

    // Flag to track if we're in a refreshing state
    private val _refreshingState = MutableStateFlow(false)
    val refreshingState: StateFlow<Boolean> = _refreshingState

    init {
        // Initialize rate limit info
        updateRateLimitInfo()
    }

    private fun updateRateLimitInfo() {
        _rateLimitInfo.value = RateLimitInfo(
            remainingCalls = cacheManager.getRemainingApiCalls(),
            maxCalls = 4,
            cooldownMinutesRemaining = cacheManager.getCooldownTimeRemaining(),
            timeUntilReset = cacheManager.getTimeUntilApiReset()
        )
    }

    fun getTransactionsByUserId(userId: Int) = transactionDao.getTransactionsByUserId(userId)

    fun getTransactionById(transactionId: String) = transactionDao.getTransactionById(transactionId)

    suspend fun fetchTransactions(userId: Int): List<Transaction>? = withContext(dispatchers.io) {
        try {
            // Update rate limit info first
            updateRateLimitInfo()

            // Check cache first
            val cachedTransactions = cachedTransactionDao.getValidCachedTransactions(userId)
                .first()
                .map { it.toTransaction() }

            if (cachedTransactions.isNotEmpty()) {
                return@withContext cachedTransactions
            }

            // If cache is empty or stale, check if we can refresh
            if (cacheManager.shouldRefreshCache(userId)) {
                _refreshingState.value = true

                try {
                    val serverTransactions = fetchFromServer(userId)
                    if (serverTransactions != null) {
                        cacheManager.cacheTransactions(userId, serverTransactions)
                        updateRateLimitInfo() // Update rate limit info after fetch
                    }
                    return@withContext serverTransactions
                } finally {
                    _refreshingState.value = false
                }
            }

            // If we can't refresh due to rate limits, return cached data even if stale
            return@withContext cachedTransactions
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error fetching transactions", e)
            _refreshingState.value = false
            throw e
        }
    }

    suspend fun manualRefreshTransactions(userId: Int, forceCooldownOverride: Boolean = false): ManualRefreshResult = withContext(dispatchers.io) {
        // If already refreshing, don't start another refresh
        if (isRefreshing.getAndSet(true)) {
            return@withContext ManualRefreshResult.AlreadyRefreshing
        }

        try {
            _refreshingState.value = true
            updateRateLimitInfo()

            // Check rate limits
            val rateLimitInfo = _rateLimitInfo.value

            // If in cooldown period and not forcing override, inform the user to confirm
            if (rateLimitInfo.cooldownMinutesRemaining > 0 && !forceCooldownOverride) {
                return@withContext ManualRefreshResult.InCooldown(rateLimitInfo.cooldownMinutesRemaining)
            }

            // If at rate limit, try to refresh if near reset time
            if (rateLimitInfo.remainingCalls == 0) {
                val timeUntilReset = rateLimitInfo.timeUntilReset

                // If within 30 minutes of reset, try anyway
                if (timeUntilReset != null && timeUntilReset.toMinutes() < 30) {
                    val serverTransactions = fetchFromServer(userId)
                    if (serverTransactions != null) {
                        cacheManager.cacheTransactions(userId, serverTransactions)
                        updateRateLimitInfo()
                        return@withContext ManualRefreshResult.Success(serverTransactions.size)
                    }
                    return@withContext ManualRefreshResult.Error("Failed to fetch transactions")
                }

                return@withContext ManualRefreshResult.RateLimited(timeUntilReset)
            }

            // We have calls remaining or are forcing a refresh, proceed with refresh
            val serverTransactions = fetchFromServer(userId)
            if (serverTransactions != null) {
                cacheManager.cacheTransactions(userId, serverTransactions)
                updateRateLimitInfo()
                return@withContext ManualRefreshResult.Success(serverTransactions.size)
            }

            return@withContext ManualRefreshResult.Error("Failed to fetch transactions")
        } catch (e: Exception) {
            Log.e(TAG, "Error in manual refresh", e)
            return@withContext ManualRefreshResult.Error(e.message ?: "Unknown error")
        } finally {
            _refreshingState.value = false
            isRefreshing.set(false)
        }
    }

    suspend fun fetchAccountTransactions(accountId: String, userId: Int): List<Transaction>? = withContext(dispatchers.io) {
        try {
            _refreshingState.value = true
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

                // Update the cache with merged transactions, marking as account-specific
                cacheManager.cacheAccountTransactions(userId, mergedTransactions, isAccountSpecific = true)

                Log.d(TAG, "Successfully fetched and cached ${localizedTransactions.size} transactions from server")
                return@withContext localizedTransactions
            } else {
                Log.d(TAG, "No transactions found on server for account $accountId")
                return@withContext emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from server", e)
            return@withContext null
        } finally {
            _refreshingState.value = false
        }
    }

    private suspend fun fetchFromServer(userId: Int): List<Transaction>? {
        return try {
            Log.d(TAG, "Fetching transactions from server for user $userId")

            val response = apiService.getMyTransactions()

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
                val response = apiService.getMyRecentTransactions(dateFrom)
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
                val response = apiService.getMyNonRecentTransactions(dateTo)
                TransactionCache.saveTransactions(response)
                response
            } catch (e: Exception) {
                null
            }
        }

    suspend fun getAccountsNeedingReauth(userId: Int): List<AccountReauthState> = withContext(dispatchers.io) {
        try {
            val localAccounts = bankAccountDao.getAccountsNeedingReauthByUserId(userId).first()
            Log.d("TransactionRepository", "Found ${localAccounts.size} accounts needing reauth in local DB")

            return@withContext localAccounts.map { account ->
                AccountReauthState(
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
                    val serverTransactionModel = myApplication.entityServerConverter
                        .convertTransactionToServer(transaction.toEntity())
                        .getOrElse { return@withContext Result.failure(it) }

                    val serverTransaction = apiService.createTransaction(serverTransactionModel)
                    val syncedTransaction = myApplication.entityServerConverter.convertTransactionFromServer(serverTransaction)

                    transactionDao.insertTransaction(syncedTransaction.getOrElse { return@withContext Result.failure(it) }.copy(syncStatus = SyncStatus.SYNCED))
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

    sealed class ManualRefreshResult {
        data class Success(val count: Int) : ManualRefreshResult()
        data class Error(val message: String) : ManualRefreshResult()
        data class RateLimited(val timeUntilReset: Duration?) : ManualRefreshResult()
        data class InCooldown(val cooldownMinutesRemaining: Long) : ManualRefreshResult()
        object AlreadyRefreshing : ManualRefreshResult()
    }

    companion object {
        private const val TAG = "TransactionRepository"
    }
}