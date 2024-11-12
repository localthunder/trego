package com.splitter.splittr.data.repositories

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.BankAccountDao
import com.splitter.splittr.data.local.dao.RequisitionDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.data.model.BankAccount
import com.splitter.splittr.data.sync.SyncableRepository
import com.splitter.splittr.data.sync.managers.BankAccountSyncManager
import com.splitter.splittr.data.sync.managers.RequisitionSyncManager
import com.splitter.splittr.utils.ConflictResolution
import com.splitter.splittr.utils.ConflictResolver
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.NetworkUtils
import com.splitter.splittr.utils.SyncUtils
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class BankAccountRepository(
    private val bankAccountDao: BankAccountDao,
    private val requisitionDao: RequisitionDao,
    private val apiService: ApiService,
    private val dispatchers: CoroutineDispatchers,
    private val syncMetadataDao: SyncMetadataDao,
    private val context: Context,
    private val bankAccountSyncManager: BankAccountSyncManager,
    private val requisitionSyncManager: RequisitionSyncManager
) : SyncableRepository {

    override val entityType = "bank_accounts"
    override val syncPriority = 2

    // Handle conflicts between local and server data
    fun getUserAccounts(userId: Int): Flow<List<BankAccount>> = flow {
        // Always emit local data first
        emitAll(bankAccountDao.getUserAccounts(userId).map { entities ->
            entities.map { it.toModel() }
        })

        // Check if we should fetch fresh data
        if (NetworkUtils.isOnline()) {
            try {
                val serverAccounts = apiService.getUserAccounts(userId)
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
            accounts.forEach { account ->
                bankAccountDao.insertBankAccount(account.toEntity(SyncStatus.SYNCED))
            }
            Result.success(accounts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun addAccount(account: BankAccount): Result<BankAccount> = withContext(dispatchers.io) {
        try {
            // Save locally first (optimistic update)
            val entity = account.toEntity(SyncStatus.PENDING_SYNC)
            bankAccountDao.insertBankAccount(entity)

            // Try to sync if online
            if (NetworkUtils.isOnline()) {
                try {
                    val serverAccount = apiService.addAccount(account)
                    val resolution = ConflictResolver.resolve(account, serverAccount)
                    when (resolution) {
                        is ConflictResolution.ServerWins -> {
                            bankAccountDao.insertBankAccount(serverAccount.toEntity(SyncStatus.SYNCED))
                            Result.success(serverAccount)
                        }
                        is ConflictResolution.LocalWins -> {
                            Result.success(account)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync with server", e)
                    Result.success(account)
                }
            } else {
                Result.success(account)
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
            apiService.updateAccountAfterReauth(accountId, mapOf("newRequisitionId" to newRequisitionId))
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("InstitutionRepository", "Error updating account after reauth", e)
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