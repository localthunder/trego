package com.splitter.splittr.data.local.dao

import androidx.room.*
import com.splitter.splittr.data.local.entities.BankAccountEntity
import com.splitter.splittr.data.sync.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BankAccountDao {
    @Query("SELECT * FROM accounts WHERE user_id = :userId")
    fun getUserAccounts(userId: Int): Flow<List<BankAccountEntity>>

    @Query("SELECT * FROM accounts WHERE requisition_id = :requisitionId")
    fun getBankAccountsByRequisition(requisitionId: String): Flow<List<BankAccountEntity>>

    @Query("SELECT * FROM accounts WHERE account_id = :accountId")
    suspend fun getAccountById(accountId: String): BankAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBankAccount(account: BankAccountEntity)

    @Query("SELECT * FROM accounts WHERE sync_status != 'SYNCED'")
    fun getUnsyncedBankAccounts(): Flow<List<BankAccountEntity>>

    @Query("UPDATE accounts SET sync_status = :status, updated_at = CURRENT_TIMESTAMP WHERE account_id = :accountId")
    suspend fun updateBankAccountSyncStatus(accountId: String, status: SyncStatus)

    @Query("UPDATE accounts SET needsReauthentication = :needsReauthentication, updated_at = CURRENT_TIMESTAMP WHERE account_id = :accountId")
    suspend fun updateNeedsReauthentication(accountId: String, needsReauthentication: Boolean)

    @Query("SELECT * FROM accounts WHERE needsReauthentication = 1")
    fun getAccountsNeedingReauthentication(): Flow<List<BankAccountEntity>>

    @Query("SELECT * FROM accounts WHERE needsReauthentication = 1 AND user_id = :userId ")
    fun getAccountsNeedingReauthByUserId(userId: Int): Flow<List<BankAccountEntity>>

    @Query("SELECT needsReauthentication FROM accounts WHERE account_id = :accountId")
    fun getNeedsReauthentication(accountId: String): Flow<Boolean>
}