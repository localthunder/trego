package com.splitter.splittr.data.sync.managers

import android.content.Context
import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.BankAccountDao
import com.splitter.splittr.data.local.dao.TransactionDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.model.Transaction
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.OptimizedSyncManager
import com.splitter.splittr.data.sync.SyncStatus
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
        Result.success(result)
    } catch (e: Exception) {
        Log.e(TAG, "Error syncing transaction to server: ${entity.transactionId}", e)
        Result.failure(e)
    }

    override suspend fun getServerChanges(since: Long): List<Transaction> {
        // Not used - we handle server changes differently for transactions
        return emptyList()
    }

    override suspend fun applyServerChange(serverEntity: Transaction) {
        // Not used - we handle server changes differently for transactions
        throw UnsupportedOperationException("Direct server changes not supported for transactions")
    }

    /**
     * Special sync method that handles both local sync and GoCardless transaction caching
     */
    suspend fun performFullSync(): TransactionSyncResult {
        if (!NetworkUtils.isOnline()) {
            return TransactionSyncResult.Error("No network connection available")
        }

        val userId = getUserIdFromPreferences(context) ?: return TransactionSyncResult.Error(
            "User ID not found"
        )

        return try {
            // First sync local saved transactions
            val localSyncResult = syncLocalTransactions(userId)

            // Then fetch and cache fresh transactions from GoCardless
            val cachingResult = cacheGoCardlessTransactions(userId)

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

    private suspend fun cacheGoCardlessTransactions(userId: Int): TransactionSyncResult {
        try {
            val response = apiService.getTransactionsByUserId(userId)
            val transactions = response.transactions
            val accountsNeedingReauth = response.accountsNeedingReauthentication

            Log.d(TAG, "Caching ${transactions.size} transactions from GoCardless")

            // Save all transactions to local DB with SYNCED status
            transactions.forEach { transaction ->
                transactionDao.insertTransaction(transaction.toEntity(SyncStatus.SYNCED))
            }

            // Update TransactionCache
            TransactionCache.saveTransactions(transactions)

            // Handle reauth accounts if needed
            accountsNeedingReauth.forEach { account ->
                Log.d(TAG, "Account ${account.accountId} needs reauthorization")
                bankAccountDao.updateNeedsReauthentication(account.accountId, true)
            }

            return TransactionSyncResult.Success(transactions.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error caching GoCardless transactions", e)
            TransactionCache.setError("Failed to fetch transactions: ${e.message}")
            return TransactionSyncResult.Error("Error caching GoCardless transactions: ${e.message}")
        }
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