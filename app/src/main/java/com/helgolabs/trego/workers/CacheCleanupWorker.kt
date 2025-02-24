package com.helgolabs.trego.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.helgolabs.trego.MyApplication
import java.util.concurrent.TimeUnit

class CacheCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val TAG = "CacheCleanupWorker"

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting cache cleanup work")
        return try {
            val application = applicationContext as MyApplication
            val cacheManager = application.transactionCacheManager

            cacheManager.clearExpiredCache()
            Log.d(TAG, "Cache cleanup completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cache cleanup", e)
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "cache_cleanup_work"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<CacheCleanupWorker>(
                12, TimeUnit.HOURS, // Run twice a day
                1, TimeUnit.HOURS  // Flex period
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP, // Don't replace if already scheduled
                    workRequest
                )

            Log.d("CacheCleanupWorker", "Cache cleanup work scheduled")
        }

        fun cancelScheduledWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}