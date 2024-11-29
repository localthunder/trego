package com.splitter.splittr.data.sync.managers

import android.content.Context
import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.BankAccountDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.model.BankAccount
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.OptimizedSyncManager
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.utils.ConflictResolution
import com.splitter.splittr.utils.ConflictResolver
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.DateUtils
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first

class BankAccountSyncManager(
    private val bankAccountDao: BankAccountDao,
    private val apiService: ApiService,
    syncMetadataDao: SyncMetadataDao,
    dispatchers: CoroutineDispatchers,
    private val context: Context
) : OptimizedSyncManager<BankAccount>(syncMetadataDao, dispatchers) {

    override val entityType = "bank_accounts"
    override val batchSize = 20 // Process 20 accounts at a time

    override suspend fun getLocalChanges(): List<BankAccount> =
        bankAccountDao.getUnsyncedBankAccounts().first().map { it.toModel() }

    override suspend fun syncToServer(entity: BankAccount): Result<BankAccount> = try {
        val accountId = entity.accountId
        val existingAccount = bankAccountDao.getAccountById(accountId)

        // Mark as pending sync before server operation
        bankAccountDao.updateBankAccountSyncStatus(accountId, SyncStatus.PENDING_SYNC)

        val result = if (existingAccount == null) {
            Log.d(TAG, "Creating new bank account on server: $accountId")
            apiService.addAccount(entity)
        } else {
            Log.d(TAG, "Updating existing bank account on server: $accountId")
            apiService.updateAccount(accountId, entity)
        }

        // Update local entity with server result and SYNCED status
        bankAccountDao.updateBankAccountSyncStatus(accountId, SyncStatus.SYNCED)

        Result.success(result)
    } catch (e: Exception) {
        Log.e(TAG, "Error syncing bank account to server: ${entity.accountId}", e)
        bankAccountDao.updateBankAccountSyncStatus(entity.accountId, SyncStatus.SYNC_FAILED)
        Result.failure(e)
    }

    override suspend fun getServerChanges(since: Long): List<BankAccount> {
        val userId = getUserIdFromPreferences(context) ?: throw IllegalStateException("User ID not found")
        Log.d(TAG, "Fetching bank accounts since $since for user $userId")
        return apiService.getAccountsSince(since, userId)
    }

    override suspend fun applyServerChange(serverEntity: BankAccount) {
        Log.d(TAG, "Fetching account with ID: ${serverEntity.accountId}")
        Log.d(TAG, "Account details: $serverEntity")
        val localEntity = bankAccountDao.getAccountById(serverEntity.accountId)?.toModel()

        if (localEntity == null) {
            Log.d(TAG, "Inserting new bank account from server: ${serverEntity.accountId}")
            bankAccountDao.insertBankAccount(
                serverEntity
                    .copy(updatedAt = DateUtils.standardizeTimestamp(serverEntity.updatedAt))
                    .toEntity(SyncStatus.SYNCED)
            )
        } else {
            when {
                DateUtils.isUpdateNeeded(
                    serverEntity.updatedAt,
                    localEntity.updatedAt,
                    "BankAccount-${serverEntity.accountId}"
                ) -> {
                    Log.d(TAG, "Updating existing bank account from server: ${serverEntity.accountId}")
                    Log.d(TAG, "Server timestamp: ${serverEntity.updatedAt}")
                    Log.d(TAG, "Local timestamp: ${localEntity.updatedAt}")

                    // Preserve local reauth status if it's set to true
                    val needsReauth = localEntity.needsReauthentication || serverEntity.needsReauthentication

                    bankAccountDao.insertBankAccount(
                        serverEntity
                            .copy(updatedAt = DateUtils.standardizeTimestamp(serverEntity.updatedAt))
                            .toEntity(SyncStatus.SYNCED)
                            .copy(needsReauthentication = needsReauth)
                    )
                }
                else -> {
                    Log.d(TAG, "Keeping local bank account version: ${localEntity.accountId}")
                    Log.d(TAG, "Server timestamp: ${serverEntity.updatedAt}")
                    Log.d(TAG, "Local timestamp: ${localEntity.updatedAt}")
                    bankAccountDao.updateBankAccountSyncStatus(localEntity.accountId, SyncStatus.PENDING_SYNC)
                }
            }
        }
    }

    /**
     * Special method to handle reauthorization status updates
     */
    suspend fun syncReauthStatus(accountId: String, needsReauth: Boolean) {
        try {
            Log.d(TAG, "Syncing reauth status for account $accountId: $needsReauth")

            // Update local DB first
            bankAccountDao.updateNeedsReauthentication(accountId, needsReauth)

            // Then sync to server
            apiService.updateNeedsReauthentication(accountId, needsReauth)

            Log.d(TAG, "Successfully synced reauth status")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing reauth status for account $accountId", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "BankAccountSyncManager"
    }
}