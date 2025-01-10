package com.splitter.splittr.data.sync.managers

import android.content.Context
import android.util.Log
import com.splitter.splittr.MyApplication
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.BankAccountDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.local.dao.UserDao
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
    private val userDao: UserDao,
    private val apiService: ApiService,
    syncMetadataDao: SyncMetadataDao,
    dispatchers: CoroutineDispatchers,
    private val context: Context
) : OptimizedSyncManager<BankAccount>(syncMetadataDao, dispatchers) {

    override val entityType = "bank_accounts"
    override val batchSize = 20

    val myApplication = context.applicationContext as MyApplication

    override suspend fun getLocalChanges(): List<BankAccount> =
        bankAccountDao.getUnsyncedBankAccounts().first().mapNotNull { bankAccountEntity ->
            myApplication.entityServerConverter.convertBankAccountToServer(bankAccountEntity).getOrNull()
        }

    override suspend fun syncToServer(entity: BankAccount): Result<BankAccount> = try {
        val accountId = entity.accountId
        val existingAccount = bankAccountDao.getAccountById(accountId)

        // Mark as pending sync before server operation
        bankAccountDao.updateBankAccountSyncStatus(accountId, SyncStatus.PENDING_SYNC)

        // Get server result
        val serverResult = if (existingAccount == null) {
            Log.d(TAG, "Creating new bank account on server: $accountId")
            apiService.addAccount(entity)
        } else {
            Log.d(TAG, "Updating existing bank account on server: $accountId")
            apiService.updateAccount(accountId, entity).data  // Add .data here since we modified the API response
        }

        // Convert and save the result
        myApplication.entityServerConverter
            .convertBankAccountFromServer(serverResult, existingAccount)
            .fold(
                onSuccess = { localAccount ->
                    bankAccountDao.insertBankAccount(localAccount)
                    bankAccountDao.updateBankAccountSyncStatus(accountId, SyncStatus.SYNCED)
                    Result.success(serverResult)
                },
                onFailure = { error ->
                    bankAccountDao.updateBankAccountSyncStatus(accountId, SyncStatus.SYNC_FAILED)
                    Result.failure(error)
                }
            )
    } catch (e: Exception) {
        Log.e(TAG, "Error syncing bank account to server: ${entity.accountId}", e)
        bankAccountDao.updateBankAccountSyncStatus(entity.accountId, SyncStatus.SYNC_FAILED)
        Result.failure(e)
    }

    override suspend fun getServerChanges(since: Long): List<BankAccount> {
        val userId = getUserIdFromPreferences(context)
            ?: throw IllegalStateException("User ID not found")

        // Get the server ID from the local user ID
        val localUser = userDao.getUserByIdDirect(userId)
            ?: throw IllegalStateException("User not found in local database")

        val serverUserId = localUser.serverId
            ?: throw IllegalStateException("No server ID found for user $userId")

        Log.d(TAG, "Fetching bank accounts since $since for user $userId")
        return apiService.getAccountsSince(since, serverUserId)
    }

    override suspend fun applyServerChange(serverEntity: BankAccount) {
        try {
            Log.d(TAG, "Processing server account: ${serverEntity}")

            // Validate required fields
            if (serverEntity.accountId == null) {
                Log.e(TAG, "Server account has null accountId, skipping")
                return
            }

            // Add logging for important fields
            Log.d(TAG, """
            Account details:
            ID: ${serverEntity.accountId}
            User ID: ${serverEntity.userId}
            Institution ID: ${serverEntity.institutionId}
            Updated At: ${serverEntity.updatedAt}
            Currency: ${serverEntity.currency}
        """.trimIndent())

            // Get local entity
            val localEntity = bankAccountDao.getAccountById(serverEntity.accountId)

            // Convert server account to local entity
            try {
                val convertedAccount = myApplication.entityServerConverter
                    .convertBankAccountFromServer(serverEntity, localEntity)
                    .getOrThrow() // Use getOrThrow to see the actual conversion error if it fails

                if (localEntity == null) {
                    Log.d(TAG, "Inserting new bank account from server: ${serverEntity.accountId}")
                    bankAccountDao.insertBankAccount(convertedAccount)
                } else {
                    when {
                        DateUtils.isUpdateNeeded(
                            serverEntity.updatedAt,
                            localEntity.updatedAt,
                            "BankAccount-${serverEntity.accountId}"
                        ) -> {
                            Log.d(TAG, "Updating existing bank account: ${serverEntity.accountId}")
                            val needsReauth = localEntity.needsReauthentication ||
                                    convertedAccount.needsReauthentication
                            bankAccountDao.insertBankAccount(
                                convertedAccount.copy(needsReauthentication = needsReauth)
                            )
                        }
                        else -> {
                            Log.d(TAG, "Local bank account is up to date: ${localEntity.accountId}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error converting bank account", e)
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in applyServerChange", e)
            throw e
        }
    }

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