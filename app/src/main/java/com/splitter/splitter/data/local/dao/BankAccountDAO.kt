package com.splitter.splitter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.splitter.splitter.model.BankAccount

@Dao
interface BankAccountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bankAccount: BankAccount)

    @Query("SELECT * FROM accounts")
    suspend fun getAllBankAccounts(): List<BankAccount>

    @Query("SELECT * FROM accounts WHERE account_id = :accountId")
    suspend fun getBankAccountById(accountId: String): BankAccount?
}
