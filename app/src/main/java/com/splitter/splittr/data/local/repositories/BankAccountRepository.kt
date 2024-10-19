package com.splitter.splittr.data.local.repositories

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
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class BankAccountRepository(
    private val bankAccountDao: BankAccountDao,
    private val requisitionDao: RequisitionDao,
    private val apiService: ApiService,
    private val dispatchers: CoroutineDispatchers,
    private val context: Context
) {
    fun getUserAccounts(userId: Int) = bankAccountDao.getUserAccounts(userId)

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

    suspend fun addAccount(account: BankAccount): Result<Unit> = withContext(dispatchers.io) {
        try {
            bankAccountDao.insertBankAccount(account.toEntity(SyncStatus.PENDING_SYNC))
            apiService.addAccount(account)
            bankAccountDao.updateBankAccountSyncStatus(account.accountId, SyncStatus.SYNCED)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncBankAccounts() = withContext(dispatchers.io) {
        val userId = getUserIdFromPreferences(context)

        // 1. Sync local requisitions to the server
        requisitionDao.getAllRequisitions().forEach { requisitionEntity ->
            try {
                apiService.addRequisition(requisitionEntity.toModel())
                requisitionDao.updateRequisitionSyncStatus(requisitionEntity.requisitionId, "SYNCED")
            } catch (e: Exception) {
                requisitionDao.updateRequisitionSyncStatus(requisitionEntity.requisitionId, "SYNC_FAILED")
                Log.e("RequisitionRepository", "Failed to sync requisition ${requisitionEntity.requisitionId}", e)
            }
        }

        // 2. Sync local bank accounts to the server
        if (userId != null) {
            bankAccountDao.getUserAccounts(userId).first().forEach { accountEntity ->
                try {
                    apiService.addAccount(accountEntity.toModel())
                    bankAccountDao.updateBankAccountSyncStatus(accountEntity.accountId, SyncStatus.SYNCED)
                } catch (e: Exception) {
                    bankAccountDao.updateBankAccountSyncStatus(accountEntity.accountId, SyncStatus.SYNC_FAILED)
                    Log.e("BankAccountRepository", "Failed to sync account ${accountEntity.accountId}", e)
                }
            }
        }

        // 3. Fetch latest bank accounts from the server
        try {
            val serverAccounts = userId?.let { apiService.getUserAccounts(it) }
            serverAccounts?.forEach { serverAccount ->
                val localAccount = bankAccountDao.getUserAccounts(serverAccount.userId).first()
                if (localAccount == null) {
                    bankAccountDao.insertBankAccount(serverAccount.toEntity(SyncStatus.SYNCED))
                } else {
                    bankAccountDao.insertBankAccount(serverAccount.toEntity(SyncStatus.SYNCED))
                }
            }
        } catch (e: Exception) {
            Log.e("BankAccountRepository", "Failed to fetch accounts from server", e)
        }

        // 4. Fetch latest requisitions from the server
        try {
            val serverRequisitions = userId?.let { apiService.getRequisitionsByUserId(it) }
            serverRequisitions?.forEach { serverRequisition ->
                val localRequisition = requisitionDao.getRequisitionById(serverRequisition.requisitionId)
                if (localRequisition == null) {
                    requisitionDao.insert(serverRequisition.toEntity(SyncStatus.SYNCED))
                } else {
                    requisitionDao.updateRequisition(serverRequisition.toEntity(SyncStatus.SYNCED))
                }
            }
        } catch (e: Exception) {
            Log.e("RequisitionRepository", "Failed to fetch requisitions from server", e)
        }
    }
}