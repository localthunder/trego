package com.splitter.splittr.data.sync.managers

import android.content.Context
import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.BankAccountDao
import com.splitter.splittr.data.local.dao.TransactionDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.local.entities.TransactionEntity
import com.splitter.splittr.data.model.Transaction
import com.splitter.splittr.data.model.User
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.OptimizedSyncManager
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.ui.viewmodels.TransactionViewModel
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.NetworkUtils
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first

class TransactionSyncManager(
    private val transactionDao: TransactionDao,
    private val apiService: ApiService,
    private val bankAccountDao: BankAccountDao,
    syncMetadataDao: SyncMetadataDao,
    dispatchers: CoroutineDispatchers,
    private val context: Context
) : OptimizedSyncManager<Transaction>(syncMetadataDao, dispatchers) {

    override val entityType = "transactions"
    override val batchSize = 50

    override suspend fun getLocalChanges(): List<Transaction> =
        transactionDao.getUnsyncedTransactions().first().map { it.toModel() }

    override suspend fun syncToServer(entity: Transaction): Result<Transaction> = try {
        val result = apiService.createTransaction(entity)

        // Update local sync status after successful server sync
        transactionDao.updateTransactionSyncStatus(entity.transactionId, SyncStatus.SYNCED)

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
        return apiService.getTransactionsSince(since, userId)
    }

    override suspend fun applyServerChange(serverEntity: Transaction) {
        try {
            val localTransaction =
                transactionDao.getTransactionById(serverEntity.transactionId).first()

            // Don't overwrite unsynced local changes
            if (localTransaction?.syncStatus == SyncStatus.PENDING_SYNC) {
                Log.d(
                    TAG,
                    "Skipping server transaction ${serverEntity.transactionId} due to pending local changes"
                )
                return
            }

            when {
                localTransaction == null -> {
                    Log.d(
                        TAG,
                        "Inserting new transaction from server: ${serverEntity.transactionId}"
                    )
                    transactionDao.insertTransaction(serverEntity.toEntity(SyncStatus.SYNCED))
                }

                serverEntity.updatedAt!! > (localTransaction.updatedAt ?: "") -> {
                    Log.d(
                        TAG,
                        "Updating existing transaction from server: ${serverEntity.transactionId}"
                    )
                    transactionDao.insertTransaction(serverEntity.toEntity(SyncStatus.SYNCED))
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

    private suspend fun fetchAndCacheTransactions(userId: Int): TransactionSyncResult {
        return try {
            Log.d(TAG, "Fetching fresh transactions from GoCardless")

            val response = apiService.getTransactionsByUserId(userId)
            val transactions = response.transactions
            val accountsNeedingReauth = response.accountsNeedingReauthentication

            // Update in-memory cache first
            TransactionCache.saveTransactions(transactions)
            Log.d(TAG, "Cached ${transactions.size} transactions in memory")

            // Process accounts needing reauthorization
            handleReauthAccounts(accountsNeedingReauth)

            // Selectively update local database:
            // 1. Only update transactions that are different from local version
            // 2. Don't overwrite local unsynced changes
            var updatedCount = 0
            transactions.forEach { transaction ->
                val localTransaction = transactionDao.getTransactionById(transaction.transactionId)
                    .first()

                if (shouldUpdateLocalTransaction(localTransaction, transaction)) {
                    transactionDao.insertTransaction(transaction.toEntity(SyncStatus.SYNCED))
                    updatedCount++
                }
            }

            Log.d(TAG, "Updated $updatedCount transactions in local database")
            TransactionSyncResult.Success(updatedCount)

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching and caching transactions", e)
            TransactionCache.setError("Failed to fetch transactions: ${e.message}")
            TransactionSyncResult.Error("Error caching GoCardless transactions: ${e.message}")
        }
    }

    private suspend fun handleReauthAccounts(accountsNeedingReauth: List<TransactionViewModel.AccountReauthState>) {
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

    private fun shouldUpdateLocalTransaction(local: TransactionEntity?, server: Transaction): Boolean {
        // Don't overwrite unsynced local changes
        if (local?.syncStatus == SyncStatus.PENDING_SYNC) {
            return false
        }

        // If no local version exists, we should update
        if (local == null) {
            return true
        }

        // Compare relevant fields to determine if update is needed
        return local.updatedAt != server.updatedAt ||
                local.amount != server.transactionAmount.amount ||
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