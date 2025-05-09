package com.helgolabs.trego.data.sync.managers

import android.content.Context
import android.util.Log
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.exceptions.AccountOwnershipException
import com.helgolabs.trego.data.extensions.toEntity
import com.helgolabs.trego.data.extensions.toModel
import com.helgolabs.trego.data.local.dao.BankAccountDao
import com.helgolabs.trego.data.local.dao.SyncMetadataDao
import com.helgolabs.trego.data.local.dao.UserDao
import com.helgolabs.trego.data.model.BankAccount
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.sync.OptimizedSyncManager
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.utils.ConflictResolution
import com.helgolabs.trego.utils.ConflictResolver
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

class BankAccountSyncManager(
    private val bankAccountDao: BankAccountDao,
    private val userDao: UserDao,
    private val apiService: ApiService,
    syncMetadataDao: SyncMetadataDao,
    dispatchers: CoroutineDispatchers,
    private val context: Context
) : OptimizedSyncManager<BankAccount, BankAccount>(syncMetadataDao, dispatchers) {

    override val entityType = "bank_accounts"
    override val batchSize = 20

    val myApplication = context.applicationContext as MyApplication

    override suspend fun getLocalChanges(): List<BankAccount> =
        bankAccountDao.getUnsyncedBankAccounts().first().mapNotNull { bankAccountEntity ->
            myApplication.entityServerConverter.convertBankAccountToServer(bankAccountEntity).getOrNull()
        }

    override suspend fun syncToServer(entity: BankAccount): Result<BankAccount> {
        return try {
            val accountId = entity.accountId
            val existingAccount = bankAccountDao.getAccountById(accountId)

            // Mark as pending sync before server operation
            bankAccountDao.updateBankAccountSyncStatus(accountId, SyncStatus.PENDING_SYNC)

            try {
                if (existingAccount?.syncStatus == SyncStatus.LOCALLY_DELETED) {
                    // Handle deletion sync
                    val response = apiService.deleteBankAccount(accountId)
                    if (response.isSuccessful) {
                        bankAccountDao.deleteBankAccount(accountId)
                        Result.success(entity)
                    } else {
                        Result.failure(Exception("Server deletion failed: ${response.code()}"))
                    }
                } else {
                    // Get server result
                    val serverResult = if (existingAccount == null) {
                        Log.d(TAG, "Creating new bank account on server: $accountId")
                        apiService.addAccount(entity)
                    } else {
                        Log.d(TAG, "Updating existing bank account on server: $accountId")
                        apiService.updateAccount(
                            accountId,
                            entity
                        ).data  // Add .data here since we modified the API response
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
                                bankAccountDao.updateBankAccountSyncStatus(
                                    accountId,
                                    SyncStatus.SYNC_FAILED
                                )
                                Result.failure(error)
                            }
                        )
                }
            } catch (e: Exception) {
                // Check for 403 Forbidden (account owned by another user)
                if (e is HttpException && e.code() == 403) {
                    Log.w(TAG, "Account belongs to another user: $accountId")

                    // Mark the account with a special status
                    bankAccountDao.updateBankAccountSyncStatus(accountId, SyncStatus.CONFLICT)

                    // Return a more specific error
                    return Result.failure(AccountOwnershipException("This account is already registered to another user"))
                }

                // Handle other errors
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing bank account to server: ${entity.accountId}", e)
            bankAccountDao.updateBankAccountSyncStatus(entity.accountId, SyncStatus.SYNC_FAILED)
            Result.failure(e)
        }
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
        return apiService.getAccountsSince(since, serverUserId).also { accounts ->
            Log.d(TAG, "Received ${accounts.size} accounts from server")
        }
    }

    override suspend fun applyServerChange(serverEntity: BankAccount) {
        try {

            // Validate required fields
            if (serverEntity.accountId == null) {
                Log.e(TAG, "Server account has null accountId. Raw server entity: $serverEntity")
                return
            }

            // Get local entity
            val localEntity = bankAccountDao.getAccountById(serverEntity.accountId)

            // Log conversion attempt
            Log.d(TAG, "Attempting to convert server account to local entity...")

            // Convert server account to local entity with detailed error handling
            val convertedAccount = try {
                myApplication.entityServerConverter
                    .convertBankAccountFromServer(serverEntity, localEntity)
                    .getOrElse { error ->
                        Log.e(TAG, """
                        Failed to convert bank account:
                        Error: ${error.message}
                        Server Entity: $serverEntity
                        Local Entity: $localEntity
                    """.trimIndent())
                        throw error
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during conversion", e)
                throw e
            }

            // Log successful conversion
            Log.d(TAG, "Successfully converted account: $convertedAccount")

            // Rest of your existing logic...
            when {
                localEntity == null -> {
                    Log.d(TAG, "Inserting new bank account from server: ${serverEntity.accountId}")
                    bankAccountDao.insertBankAccount(convertedAccount)
                }
                // ... rest of your when block
            }
        } catch (e: Exception) {
            Log.e(TAG, """
            Error in applyServerChange:
            Error: ${e.message}
            Stack trace: ${e.stackTrace.joinToString("\n")}
        """.trimIndent())
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