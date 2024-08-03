package com.splitter.splitter.data.local.repositories

import com.splitter.splitter.data.local.dao.BankAccountDao
import com.splitter.splitter.model.BankAccount

class BankAccountRepository(private val bankAccountDao: BankAccountDao) {

    suspend fun insert(bankAccount: BankAccount) {
        bankAccountDao.insert(bankAccount)
    }

    suspend fun getAllBankAccounts(): List<BankAccount> {
        return bankAccountDao.getAllBankAccounts()
    }

    suspend fun getBankAccountById(accountId: String): BankAccount? {
        return bankAccountDao.getBankAccountById(accountId)
    }
}
