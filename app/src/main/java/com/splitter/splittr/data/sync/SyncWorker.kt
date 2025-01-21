package com.splitter.splittr.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.splitter.splittr.data.repositories.*
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val userRepository: UserRepository,
    private val paymentRepository: PaymentRepository,
    private val groupRepository: GroupRepository,
    private val bankAccountRepository: BankAccountRepository,
    private val paymentSplitRepository: PaymentSplitRepository,
    private val institutionRepository: InstitutionRepository,
    private val requisitionRepository: RequisitionRepository,
    private val transactionRepository: TransactionRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = coroutineScope {

        try {
            Log.d(TAG, "Starting sync process")

            syncRepository("Users", userRepository::sync)
            syncRepository("Groups", groupRepository::sync)
            syncRepository("Requisitions", requisitionRepository::sync)
            syncRepository("Bank Accounts", bankAccountRepository::sync)
            syncRepository("Transactions", transactionRepository::sync)
            syncRepository("Payments", paymentRepository::sync)

            Log.d(TAG, "Sync process completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync process failed", e)
            Result.retry()
        }
    }

    private suspend fun syncRepository(name: String, syncFunction: suspend () -> Unit) {
        try {
            Log.d(TAG, "Starting sync for $name")
            syncFunction()
            Log.d(TAG, "Completed sync for $name")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing $name", e)
            // We're not throwing the exception here to allow other syncs to proceed
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val SYNC_WORK_NAME = "action_triggered_sync"

        fun requestSync(context: Context) {
            val workManager = WorkManager.getInstance(context)

            // Cancel any existing sync work
            workManager.cancelUniqueWork(SYNC_WORK_NAME)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniqueWork(
                SYNC_WORK_NAME,
                ExistingWorkPolicy.REPLACE, // This will replace any existing work
                syncRequest
            )
            Log.d(TAG, "New sync requested")
        }
    }
}

class SyncWorkerFactory(
    private val userRepository: UserRepository,
    private val paymentRepository: PaymentRepository,
    private val groupRepository: GroupRepository,
    private val bankAccountRepository: BankAccountRepository,
    private val paymentSplitRepository: PaymentSplitRepository,
    private val institutionRepository: InstitutionRepository,
    private val requisitionRepository: RequisitionRepository,
    private val transactionRepository: TransactionRepository
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            SyncWorker::class.java.name ->
                SyncWorker(
                    appContext,
                    workerParameters,
                    userRepository,
                    paymentRepository,
                    groupRepository,
                    bankAccountRepository,
                    paymentSplitRepository,
                    institutionRepository,
                    requisitionRepository,
                    transactionRepository
                )
            else -> null
        }
    }
}