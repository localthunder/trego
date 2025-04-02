package com.helgolabs.trego.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.helgolabs.trego.data.repositories.*
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
    private val transactionRepository: TransactionRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = coroutineScope {
        try {
            Log.d(TAG, "Starting sync process")

            // Launch transaction fetch & cache concurrently with other syncs
            val transactionFetchJob = launch {
                try {
                    Log.d(TAG, "Starting transaction fetch and cache")
                    val userId = getUserIdFromPreferences(applicationContext)
                        ?: throw IllegalStateException("User ID not found")

                    // Only run the fetch and cache part
                    (transactionRepository as? TransactionRepository)?.let { repo ->
                        repo.transactionSyncManager.fetchAndCacheTransactions(userId)
                    }
                    Log.d(TAG, "Completed transaction fetch and cache")
                } catch (e: Exception) {
                    Log.e(TAG, "Error in transaction fetch and cache", e)
                    // Don't fail the whole sync process if fetch fails
                }
            }

            // Run other syncs in required order
            try {
                // Core data syncs first
                syncRepository("Users", userRepository::sync)
                syncRepository("Groups", groupRepository::sync)
                syncRepository("User Preferences", userPreferencesRepository::sync)

                // Account-related syncs
                syncRepository("Requisitions", requisitionRepository::sync)
                syncRepository("Bank Accounts", bankAccountRepository::sync)

                // Transaction and Payment syncs
                syncRepository("Transactions", transactionRepository::sync)
                syncRepository("Payments", paymentRepository::sync)
            } catch (e: Exception) {
                Log.e(TAG, "Error in ordered sync process", e)
                throw e
            }

            // Wait for transaction fetch to complete, but don't fail if it errored
            transactionFetchJob.join()

            Log.d(TAG, "Sync process completed")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in sync process", e)
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
    private val transactionRepository: TransactionRepository,
    private val userPreferencesRepository: UserPreferencesRepository
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
                    transactionRepository,
                    userPreferencesRepository
                )
            else -> null
        }
    }
}