package com.helgolabs.trego.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.helgolabs.trego.MyApplication

class FetchTransactionsForAccountWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val requisitionId = inputData.getString("requisitionId") ?: return Result.failure()
        val userId = inputData.getInt("userId", -1)
        if (userId == -1) return Result.failure()

        try {
            val app = applicationContext as MyApplication
            val bankAccountRepository = app.bankAccountRepository
            val transactionRepository = app.transactionRepository

            // Fetch accounts and process them
            bankAccountRepository.getBankAccounts(requisitionId)
                .onSuccess { accounts ->
                    if (accounts.isNotEmpty()) {
                        // Process transactions
                        transactionRepository.fetchAccountTransactions(accounts.first().accountId, userId)
                    }
                }

            return Result.success()
        } catch (e: Exception) {
            Log.e("FetchAccountsWorker", "Error", e)
            return Result.retry()
        }
    }
}