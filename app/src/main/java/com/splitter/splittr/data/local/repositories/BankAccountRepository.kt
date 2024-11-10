package com.splitter.splittr.data.local.repositories

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.BankAccountDao
import com.splitter.splittr.data.local.dao.RequisitionDao
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.model.BankAccount
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.NetworkUtils
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
    private val context: Context
) {
    // Handle conflicts between local and server data
    private suspend fun handleConflict(local: BankAccount, server: BankAccount) {
        // If server version is newer, update local
        if (server.updatedAt > local.updatedAt) {
            bankAccountDao.insertBankAccount(server.toEntity(SyncStatus.SYNCED))
        } else {
            // If local version is newer, keep local changes and mark for sync
            bankAccountDao.updateBankAccountSyncStatus(local.accountId, SyncStatus.PENDING_SYNC)
        }
    }


    fun getUserAccounts(userId: Int): Flow<List<BankAccount>> = flow {
        // Always emit local data first
        emitAll(bankAccountDao.getUserAccounts(userId).map { entities ->
            entities.map { it.toModel() }
        })

        // Check if we should fetch fresh data
        if (NetworkUtils.isOnline() && SyncUtils.isDataStale("user_accounts_$userId")) {
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
                SyncUtils.updateLastSyncTimestamp("user_accounts_$userId")

                // Re-emit updated data
                emitAll(bankAccountDao.getUserAccounts(userId).map { entities ->
                    entities.map { it.toModel() }
                })
            } catch (e: Exception) {
                Log.e("BankAccountRepository", "Error fetching accounts from server", e)
            }
        }
    }.flowOn(dispatchers.io)

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
                    val serverEntity = serverAccount.toEntity(SyncStatus.SYNCED)
                    bankAccountDao.insertBankAccount(serverEntity)
                    return@withContext Result.success(serverAccount)
                } catch (e: Exception) {
                    Log.e("BankAccountRepository", "Failed to sync with server", e)
                    // Keep local changes and mark for later sync
                    return@withContext Result.success(account)
                }
            }
            Result.success(account)
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

    suspend fun syncBankAccounts(forceRefresh: Boolean = false) = withContext(dispatchers.io) {
        val userId = getUserIdFromPreferences(context) ?: return@withContext

        if (!NetworkUtils.isOnline()) {
            Log.d("BankAccountRepository", "No network connection, skipping sync")
            return@withContext
        }

        if (!forceRefresh && !SyncUtils.isDataStale("user_accounts_$userId")) {
            Log.d("BankAccountRepository", "Data is fresh, skipping sync")
            return@withContext
        }

        try {
            // 1. First sync local pending changes
            syncLocalChanges(userId)

            // 2. Then fetch and merge server changes
            syncServerChanges(userId)

            // Update sync timestamp
            SyncUtils.updateLastSyncTimestamp("user_accounts_$userId")
        } catch (e: Exception) {
            Log.e("BankAccountRepository", "Error during sync", e)
        }
    }

    private suspend fun syncLocalChanges(userId: Int) {
        try {
            // Sync pending requisitions
            requisitionDao.getUnsyncedRequisitions().first().forEach { requisitionEntity ->
                try {
                    val serverRequisition = apiService.addRequisition(requisitionEntity.toModel())
                    requisitionDao.updateRequisition(serverRequisition.toEntity(SyncStatus.SYNCED))
                } catch (e: Exception) {
                    Log.e("BankAccountRepository", "Failed to sync requisition ${requisitionEntity.requisitionId}", e)
                    requisitionDao.updateRequisitionSyncStatus(requisitionEntity.requisitionId, SyncStatus.SYNC_FAILED)
                }
            }

            // Sync pending bank accounts
            bankAccountDao.getUnsyncedBankAccounts().first().forEach { accountEntity ->
                try {
                    val serverAccount = apiService.addAccount(accountEntity.toModel())
                    bankAccountDao.insertBankAccount(serverAccount.toEntity(SyncStatus.SYNCED))
                } catch (e: Exception) {
                    Log.e("BankAccountRepository", "Failed to sync account ${accountEntity.accountId}", e)
                    bankAccountDao.updateBankAccountSyncStatus(accountEntity.accountId, SyncStatus.SYNC_FAILED)
                }
            }
        } catch (e: Exception) {
            Log.e("BankAccountRepository", "Error syncing local changes", e)
        }
    }

    private suspend fun syncServerChanges(userId: Int) {
        try {
            // Fetch and merge bank accounts
            val serverAccounts = apiService.getUserAccounts(userId)
            serverAccounts.forEach { serverAccount ->
                try {
                    val localAccount = bankAccountDao.getAccountById(serverAccount.accountId)?.toModel()
                    if (localAccount != null) {
                        handleConflict(localAccount, serverAccount)
                    } else {
                        bankAccountDao.insertBankAccount(serverAccount.toEntity(SyncStatus.SYNCED))
                    }
                } catch (e: Exception) {
                    Log.e("BankAccountRepository", "Error processing server account ${serverAccount.accountId}", e)
                }
            }

            // Fetch and merge requisitions
            val serverRequisitions = apiService.getRequisitionsByUserId(userId)
            serverRequisitions.forEach { serverRequisition ->
                try {
                    val localRequisition = requisitionDao.getRequisitionById(serverRequisition.requisitionId)
                    if (localRequisition != null) {
                        // Apply similar conflict resolution logic for requisitions
                        if (serverRequisition.updatedAt!! > localRequisition.updatedAt.toString()) {
                            requisitionDao.updateRequisition(serverRequisition.toEntity(SyncStatus.SYNCED))
                        }
                    } else {
                        requisitionDao.insert(serverRequisition.toEntity(SyncStatus.SYNCED))
                    }
                } catch (e: Exception) {
                    Log.e("BankAccountRepository", "Error processing server requisition ${serverRequisition.requisitionId}", e)
                }
            }
        } catch (e: Exception) {
            Log.e("BankAccountRepository", "Error syncing server changes", e)
        }
    }
}