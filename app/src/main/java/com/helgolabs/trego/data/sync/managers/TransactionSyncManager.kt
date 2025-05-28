package com.helgolabs.trego.data.sync.managers

import android.content.Context
import android.util.Log
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.cache.TransactionCacheManager
import com.helgolabs.trego.data.extensions.toEntity
import com.helgolabs.trego.data.extensions.toModel
import com.helgolabs.trego.data.local.dao.BankAccountDao
import com.helgolabs.trego.data.local.dao.TransactionDao
import com.helgolabs.trego.data.local.dao.SyncMetadataDao
import com.helgolabs.trego.data.local.dao.UserDao
import com.helgolabs.trego.data.local.dataClasses.AccountReauthState
import com.helgolabs.trego.data.local.entities.TransactionEntity
import com.helgolabs.trego.data.model.Transaction
import com.helgolabs.trego.data.model.User
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.sync.OptimizedSyncManager
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.ui.viewmodels.TransactionViewModel
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.NetworkUtils
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first

class TransactionSyncManager(
    private val transactionDao: TransactionDao,
    private val userDao: UserDao,
    private val apiService: ApiService,
    private val bankAccountDao: BankAccountDao,
    private val transactionCacheManager: TransactionCacheManager,
    syncMetadataDao: SyncMetadataDao,
    dispatchers: CoroutineDispatchers,
    private val context: Context
) : OptimizedSyncManager<Transaction, Transaction>(syncMetadataDao, dispatchers) {

    override val entityType = "transactions"
    override val batchSize = 50

    val myApplication = context.applicationContext as MyApplication

    override suspend fun getLocalChanges(): List<Transaction> =
        transactionDao.getUnsyncedTransactions().first().mapNotNull { transactionEntity ->
            myApplication.entityServerConverter.convertTransactionToServer(transactionEntity).getOrNull()
        }

    override suspend fun syncToServer(entity: Transaction): Result<Transaction> = try {
        val result = apiService.createTransaction(entity)

        // Convert server response back to local entity and update
        myApplication.entityServerConverter.convertTransactionFromServer(
            result,
            transactionDao.getTransactionById(entity.transactionId).first()
        ).onSuccess { localTransaction ->
            transactionDao.insertTransaction(localTransaction)
            transactionDao.updateTransactionSyncStatus(entity.transactionId, SyncStatus.SYNCED)
        }

        Log.d(TAG, "Successfully synced transaction ${result.transactionId}")
        Result.success(result)
    } catch (e: Exception) {
        Log.e(TAG, "Error syncing transaction to server: ${entity.transactionId}", e)
        transactionDao.updateTransactionSyncStatus(entity.transactionId, SyncStatus.SYNC_FAILED)
        Result.failure(e)
    }


    override suspend fun getServerChanges(since: Long): List<Transaction> {
        val userId = getUserIdFromPreferences(context) ?: throw IllegalStateException("User ID not found")
        Log.d(TransactionSyncManager.TAG, "Fetching transactions since $since")

        // Get the server ID from the local user ID
        val localUser = userDao.getUserByIdDirect(userId)
            ?: throw IllegalStateException("User not found in local database")

        val serverUserId = localUser.serverId
            ?: throw IllegalStateException("No server ID found for user $userId")

        return apiService.getTransactionsSince(since)
    }

    override suspend fun applyServerChange(serverEntity: Transaction) {
        try {
            val localTransaction = transactionDao.getTransactionById(serverEntity.transactionId).first()

            // Don't overwrite unsynced local changes
            if (localTransaction?.syncStatus == SyncStatus.PENDING_SYNC) {
                Log.d(TAG, "Skipping server transaction ${serverEntity.transactionId} due to pending local changes")
                return
            }

            // Convert server transaction to local entity
            val convertedTransaction = myApplication.entityServerConverter.convertTransactionFromServer(
                serverEntity,
                localTransaction
            ).getOrNull() ?: throw Exception("Failed to convert server transaction")

            when {
                localTransaction == null -> {
                    Log.d(TAG, "Inserting new transaction from server: ${serverEntity.transactionId}")
                    transactionDao.insertTransaction(convertedTransaction)
                }
                shouldUpdateLocalTransaction(localTransaction, serverEntity) -> {
                    Log.d(TAG, "Updating existing transaction from server: ${serverEntity.transactionId}")
                    transactionDao.insertTransaction(convertedTransaction)
                }
                else -> {
                    Log.d(TAG, "Local transaction ${serverEntity.transactionId} is up to date")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying server transaction ${serverEntity.transactionId}", e)
            throw e
        }
    }

    suspend fun performFullSync(): TransactionSyncResult {
        if (!NetworkUtils.isOnline()) {
            return TransactionSyncResult.Error("No network connection available")
        }

        val userId = getUserIdFromPreferences(context) ?: return TransactionSyncResult.Error(
            "User ID not found"
        )

        return try {
            // First sync local unsaved transactions to server
            val localSyncResult = syncLocalTransactions(userId)

            // Then fetch fresh transactions from GoCardless and update local cache
            val cachingResult = fetchAndCacheTransactions(userId)

            // Combine results
            when {
                localSyncResult is TransactionSyncResult.Error -> localSyncResult
                cachingResult is TransactionSyncResult.Error -> cachingResult
                else -> TransactionSyncResult.Success(
                    (localSyncResult as TransactionSyncResult.Success).syncedCount +
                            (cachingResult as TransactionSyncResult.Success).syncedCount
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during full transaction sync", e)
            TransactionSyncResult.Error(e.message ?: "Unknown error during sync")
        }
    }

    private suspend fun syncLocalTransactions(userId: Int): TransactionSyncResult {
        var syncedCount = 0
        var failedCount = 0

        try {
            val unsyncedTransactions = getLocalChanges()
            Log.d(TAG, "Found ${unsyncedTransactions.size} unsynced transactions")

            unsyncedTransactions.forEach { transaction ->
                try {
                    syncToServer(transaction).fold(
                        onSuccess = { serverTransaction ->
                            transactionDao.insertTransaction(
                                serverTransaction.toEntity(SyncStatus.SYNCED)
                            )
                            syncedCount++
                            Log.d(TAG, "Successfully synced transaction ${transaction.transactionId}")
                        },
                        onFailure = { error ->
                            failedCount++
                            transactionDao.updateTransactionSyncStatus(
                                transaction.transactionId,
                                SyncStatus.SYNC_FAILED
                            )
                            Log.e(TAG, "Failed to sync transaction ${transaction.transactionId}", error)
                        }
                    )
                } catch (e: Exception) {
                    failedCount++
                    Log.e(TAG, "Error processing transaction ${transaction.transactionId}", e)
                }
            }

            return if (failedCount == 0) {
                TransactionSyncResult.Success(syncedCount)
            } else {
                TransactionSyncResult.PartialSuccess(syncedCount, failedCount)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing local transactions", e)
            return TransactionSyncResult.Error("Error syncing local transactions: ${e.message}")
        }
    }

    suspend fun fetchAndCacheTransactions(userId: Int, forceRefresh: Boolean = false): TransactionSyncResult {
        // Check cooldown period first
        if (!forceRefresh && transactionCacheManager.getCooldownTimeRemaining() > 0) {
            Log.d(TAG, "Skipping fetch due to cooldown period")
            return TransactionSyncResult.Error("In cooldown period")
        }

        return try {
            Log.d(TAG, "Fetching fresh transactions from GoCardless")

            val response = apiService.getMyTransactions()
            val transactions = response.transactions
            val accountsNeedingReauth = response.accountsNeedingReauthentication


            // Use the new cache manager instead of the singleton
            transactionCacheManager.cacheTransactions(userId, transactions)
            Log.d(TAG, "Cached ${transactions.size} transactions")

            // Handle reauth accounts
            handleReauthAccounts(accountsNeedingReauth)

            TransactionSyncResult.Success(transactions.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching and caching transactions", e)
            TransactionCache.setError("Failed to fetch transactions: ${e.message}")
            TransactionSyncResult.Error("Error caching GoCardless transactions: ${e.message}")
        }
    }

    private suspend fun handleReauthAccounts(accountsNeedingReauth: List<AccountReauthState>) {
        accountsNeedingReauth.forEach { account ->
            try {
                Log.d(TAG, "Account ${account.accountId} needs reauthorization")
                bankAccountDao.updateNeedsReauthentication(account.accountId, true)
            } catch (e: Exception) {
                Log.e(TAG,
                    "Failed to mark account ${account.accountId} for reauth",
                    e
                )
            }
        }
    }

    private fun shouldUpdateLocalTransaction(local: TransactionEntity, server: Transaction): Boolean {
        if (local.syncStatus == SyncStatus.PENDING_SYNC) return false

        return local.updatedAt != server.updatedAt ||
                local.amount != server.transactionAmount?.amount ||
                local.creditorName != server.creditorName ||
                local.debtorName != server.debtorName ||
                local.bookingDateTime != server.bookingDateTime ||
                local.remittanceInformationUnstructured != server.remittanceInformationUnstructured
    }

    sealed class TransactionSyncResult {
        data class Success(val syncedCount: Int) : TransactionSyncResult()
        data class PartialSuccess(val syncedCount: Int, val failedCount: Int) : TransactionSyncResult()
        data class Error(val message: String) : TransactionSyncResult()
    }

    companion object {
        private const val TAG = "TransactionSyncManager"
    }
}