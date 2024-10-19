package com.splitter.splittr.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.splitter.splittr.MyApplication
import com.splitter.splittr.utils.AppCoroutineDispatchers

class GroupSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val application = applicationContext as MyApplication
        val database = application.database
        val groupDao = database.groupDao()
        val groupMemberDao = database.groupMemberDao()
        val apiService = application.apiService
        val dispatchers = AppCoroutineDispatchers()

        // Use the existing GroupRepository from the application
        val repository = application.groupRepository

        return try {
            repository.syncGroups()
            Result.success()
        } catch (e: Exception) {
            // Log.e("GroupSyncWorker", "Sync failed", e)
            Result.retry()
        }
    }
}