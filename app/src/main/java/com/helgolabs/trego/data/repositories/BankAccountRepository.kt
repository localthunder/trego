package com.helgolabs.trego.data.repositories

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.extensions.toEntity
import com.helgolabs.trego.data.extensions.toModel
import com.helgolabs.trego.data.local.dao.BankAccountDao
import com.helgolabs.trego.data.local.dao.RequisitionDao
import com.helgolabs.trego.data.local.dao.SyncMetadataDao
import com.helgolabs.trego.data.local.entities.BankAccountEntity
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.data.model.BankAccount
import com.helgolabs.trego.data.sync.SyncableRepository
import com.helgolabs.trego.data.sync.managers.BankAccountSyncManager
import com.helgolabs.trego.data.sync.managers.RequisitionSyncManager
import com.helgolabs.trego.utils.ConflictResolution
import com.helgolabs.trego.utils.ConflictResolver
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.NetworkUtils
import com.helgolabs.trego.utils.ServerIdUtil.getServerId
import com.helgolabs.trego.utils.SyncUtils
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.Call
import java.util.concurrent.ConcurrentHashMap

class BankAccountRepository(
    private val bankAccountDao: BankAccountDao,
    private val requisitionDao: RequisitionDao,
    private val apiService: ApiService,
    private val dispatchers: CoroutineDispatchers,
    private val syncMetadataDao: SyncMetadataDao,
    private val context: Context,
    private val bankAccountSyncManager: BankAccountSyncManager,
    private val requisitionSyncManager: RequisitionSyncManager,
) : SyncableRepository {

    override val entityType = "bank_accounts"
    override val syncPriority = 2

    // Add this flag to track if we need to ignore call cancellations
    private val callsInProgress = ConcurrentHashMap<String, Call>()

    val myApplication = context.applicationContext as MyApplication
    val transactionRepository = myApplication.transactionRepository

    // Handle conflicts between local and server data
    fun getUserAccounts(userId: Int): Flow<List<BankAccount>> = flow {
        // Always emit local data first
        emitAll(bankAccountDao.getUserAccounts(userId).map { entities ->
            entities.map { it.toModel() }
        })

        // Check if we should fetch fresh data
        if (NetworkUtils.isOnline()) {
            try {
                val serverAccounts = apiService.getMyBankAccounts()
                serverAccounts.forEach { serverAccount ->
                    val localAccount = bankAccountDao.getAccountById(serverAccount.accountId)?.toModel()
                    if (localAccount != null) {
                        handleConflict(localAccount, serverAccount)
                    } else {
                        bankAccountDao.insertBankAccount(serverAccount.toEntity(SyncStatus.SYNCED))
                    }
                }

                // Re-emit updated data
                emitAll(bankAccountDao.getUserAccounts(userId).map { entities ->
                    entities.map { it.toModel() }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching accounts from server", e)
            }
        }
    }.flowOn(dispatchers.io)

    private suspend fun handleConflict(local: BankAccount, server: BankAccount) {
        when (val resolution = ConflictResolver.resolve(local, server)) {
            is ConflictResolution.ServerWins -> {
                Log.d(TAG, "Conflict resolved in favor of server version for account ${server.accountId}")
                // Preserve local reauth status if it's true
                val localEntity = bankAccountDao.getAccountById(server.accountId)
                val needsReauth = localEntity?.needsReauthentication == true

                bankAccountDao.insertBankAccount(server.toEntity(SyncStatus.SYNCED).copy(
                    needsReauthentication = needsReauth || server.needsReauthentication
                ))
            }
            is ConflictResolution.LocalWins -> {
                Log.d(TAG, "Conflict resolved in favor of local version for account ${local.accountId}")
                bankAccountDao.updateBankAccountSyncStatus(local.accountId, SyncStatus.PENDING_SYNC)
            }
        }
    }

    suspend fun getBankAccounts(requisitionId: String): Result<List<BankAccount>> = withContext(dispatchers.io) {
        try {
            val accounts = apiService.getBankAccounts(requisitionId)
            val timestamp = DateUtils.getCurrentTimestamp()

            // First add timestamps to the model objects
            val accountsWithTimestamps = accounts.map { account ->
                account.copy(
                    createdAt = timestamp,
                    updatedAt = timestamp
                )
            }

            // Then convert to entities
            val convertedAccounts = accountsWithTimestamps.mapNotNull { account ->
                myApplication.entityServerConverter.convertBankAccountFromServer(account).getOrNull()?.let { convertedAccount ->
                    convertedAccount.copy(
                        syncStatus = SyncStatus.PENDING_SYNC
                    )
                }
            }

            // Save to local DB
            convertedAccounts.forEach { account ->
                bankAccountDao.insertBankAccount(account)
            }

            try {
                accountsWithTimestamps.forEach { account ->
                    apiService.addAccount(account)
                }
            } catch (e: Exception) {
                Log.e("BankAccountRepository", "Error sending accounts to server", e)
            }

            Result.success(convertedAccounts.map { it.toModel() })
        } catch (e: Exception) {
            Log.e("BankAccountRepository", "Error fetching accounts", e)
            Result.failure(e)
        }
    }

    suspend fun getAccountById(accountId: String): BankAccount? = withContext(dispatchers.io) {
        try {
            val account = bankAccountDao.getAccountById(accountId)
            return@withContext account?.toModel()
        } catch (e: Exception) {
            Log.e("BankAccountRepository", "Error checking if account exists", e)
            return@withContext null
        }
    }

    suspend fun addAccount(account: BankAccountEntity): Result<BankAccountEntity> = withContext(dispatchers.io) {
        try {
            // Save locally first (optimistic update)
            val entity = account.copy(syncStatus = SyncStatus.PENDING_SYNC)
            bankAccountDao.insertBankAccount(entity)

            // Try to sync if online
            if (NetworkUtils.isOnline()) {
                try {
                    val serverUserId = getServerId(account.userId, "users", context)
                    val serverAccountRequest = account.copy(userId = serverUserId ?: account.userId)
                    val serverAccount = apiService.addAccount(serverAccountRequest.toModel())
                    val resolution = ConflictResolver.resolve(account.toModel(), serverAccount)
                    val result = when (resolution) {
                        is ConflictResolution.ServerWins -> {
                            bankAccountDao.insertBankAccount(serverAccount.toEntity(SyncStatus.SYNCED))
                            Result.success(serverAccount.toEntity(syncStatus = SyncStatus.SYNCED))
                        }
                        is ConflictResolution.LocalWins -> {
                            Result.success(account)
                        }
                    }

                    // Fetch transactions after account is synced
                    transactionRepository.fetchAccountTransactions(account.accountId, account.userId)

                    result
                } catch (e: Exception) { Log.e(TAG, "Failed to sync with server", e)
                    Result.success(account) } } else { Result.success(account)
                    }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateNeedsReauthentication(accountId: String, needsReauthentication: Boolean): Result<Unit> = withContext(dispatchers.io) {
        try {
            bankAccountDao.updateNeedsReauthentication(accountId, needsReauthentication)
            apiService.updateNeedsReauthentication(accountId, needsReauthentication)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getAccountsNeedingReauthentication(): Flow<List<BankAccount>> =
        bankAccountDao.getAccountsNeedingReauthentication()
            .map { entities -> entities.map { it.toModel() } }
            .flowOn(dispatchers.io)

    suspend fun getNeedsReauthentication(accountId: String): Result<Flow<Boolean>> = withContext(dispatchers.io) {
        try {
            val status = bankAccountDao.getNeedsReauthentication(accountId)
            Result.success(status)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAccountAfterReauth(accountId: String, newRequisitionId: String) = withContext(dispatchers.io) {
        try {
            bankAccountDao.updateNeedsReauthentication(accountId, false)
            apiService.updateNeedsReauthentication(accountId, false)
            apiService.updateAccountAfterReauth(accountId, mapOf("newRequisitionId" to newRequisitionId))
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("InstitutionRepository", "Error updating account after reauth", e)
            Result.failure(e)
        }
    }

    suspend fun deleteBankAccount(accountId: String): Result<Unit> = withContext(dispatchers.io) {
        try {
            Log.d(TAG, "Starting bank account deletion process for account: $accountId")

            // Step 1: Perform soft delete locally first
            bankAccountDao.locallyDeleteBankAccount(accountId)

            // Step 2: Mark as pending deletion in sync status
            bankAccountDao.updateBankAccountSyncStatus(accountId, SyncStatus.LOCALLY_DELETED)

            // Step 3: Attempt server deletion if online
            if (NetworkUtils.isOnline()) {
                try {
                    Log.d(TAG, "Attempting server deletion for account: $accountId")
                    val response = apiService.deleteBankAccount(accountId)

                    if (response.isSuccessful) {
                        Log.d(TAG, "Server deletion successful, removing local record")
                        // On successful server deletion, remove local record completely
                        bankAccountDao.deleteBankAccount(accountId)
                    } else {
                        Log.e(TAG, "Server deletion failed with status: ${response.code()}")
                        // Keep the local soft delete and sync status for retry
                        return@withContext Result.failure(Exception("Server deletion failed: ${response.code()}"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during server deletion", e)
                    // Keep the local soft delete and sync status for retry
                    return@withContext Result.failure(e)
                }
            } else {
                Log.d(TAG, "Offline - keeping account marked for deletion for later sync")
                // When offline, keep the account marked as LOCALLY_DELETED for later sync
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error in deleteBankAccount", e)
            Result.failure(e)
        }
    }

    override suspend fun sync(): Unit = withContext(dispatchers.io) {
        Log.d(TAG, "Starting bank account sync process")

        try {
            // First sync requisitions as bank accounts depend on them
            requisitionSyncManager.performSync()

            // Then sync bank accounts
            bankAccountSyncManager.performSync()

            Log.d(TAG, "Completed bank account sync process")
        } catch (e: Exception) {
            Log.e(TAG, "Error during sync", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "BankAccountRepository"
    }
}