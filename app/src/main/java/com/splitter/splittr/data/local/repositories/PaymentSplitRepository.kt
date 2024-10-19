package com.splitter.splittr.data.local.repositories

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.GroupDao
import com.splitter.splittr.data.local.dao.PaymentDao
import com.splitter.splittr.data.local.dao.PaymentSplitDao
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.model.PaymentSplit
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.NetworkUtils
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PaymentSplitRepository(
    private val paymentSplitDao: PaymentSplitDao,
    private val paymentDao: PaymentDao,
    private val groupDao: GroupDao,
    private val apiService: ApiService,
    private val dispatchers: CoroutineDispatchers,
    private val context: Context
) {
    fun getPaymentSplitsByPayment(paymentId: Int): Flow<List<PaymentSplit>> = flow {
        // Emit data from local database first
        emitAll(paymentSplitDao.getPaymentSplitsByPayment(paymentId).map { entities ->
            entities.map { entity -> entity.toModel() }
        })

        // Then fetch from API
        try {
            val remotePaymentSplits = apiService.getPaymentSplitsByPayment(paymentId)
            // Update local database with new data
            paymentSplitDao.insertAllPaymentSplits(remotePaymentSplits.map { it.toEntity() })
            // Emit new data
            emit(remotePaymentSplits)
        } catch (e: Exception) {
            // Log error or handle it as needed
            Log.e("PaymentSplitRepository", "Error fetching payment splits from API", e)
        }
    }.flowOn(dispatchers.io)
    suspend fun createPaymentSplit(paymentSplit: PaymentSplit): Result<PaymentSplit> = withContext(dispatchers.io) {
        try {
            val localId = paymentSplitDao.insertPaymentSplit(paymentSplit.toEntity(SyncStatus.PENDING_SYNC))
            val serverSplit = apiService.createPaymentSplit(paymentSplit.paymentId, paymentSplit)
            val updatedSplit = serverSplit.copy(id = localId.toInt())
            paymentSplitDao.updatePaymentSplit(updatedSplit.toEntity(SyncStatus.SYNCED))
            Result.success(updatedSplit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePaymentSplit(paymentSplit: PaymentSplit): Result<PaymentSplit> = withContext(dispatchers.io) {
        try {
            paymentSplitDao.updatePaymentSplit(paymentSplit.toEntity(SyncStatus.PENDING_SYNC))
            val serverSplit = apiService.updatePaymentSplit(paymentSplit.paymentId, paymentSplit.id, paymentSplit)
            paymentSplitDao.updatePaymentSplit(serverSplit.toEntity(SyncStatus.SYNCED))
            Result.success(serverSplit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePaymentSplits(splits: List<PaymentSplit>): Result<List<PaymentSplit>> = withContext(dispatchers.io) {
        try {
            // First, update each split locally with a PENDING_SYNC status
            splits.forEach { split ->
                paymentSplitDao.updatePaymentSplit(split.toEntity(SyncStatus.PENDING_SYNC))
            }

            // Then, sync each split with the server and update them in the local DB with a SYNCED status
            val syncedSplits = splits.map { split ->
                val serverSplit = apiService.updatePaymentSplit(split.paymentId, split.id, split)
                paymentSplitDao.updatePaymentSplit(serverSplit.toEntity(SyncStatus.SYNCED))
                serverSplit
            }

            Result.success(syncedSplits)
        } catch (e: Exception) {
            // If any exception occurs, return failure
            Result.failure(e)
        }
    }

    suspend fun syncPaymentSplits() = withContext(dispatchers.io) {
        Log.d(TAG, "Starting syncPaymentSplits")
        val userId = getUserIdFromPreferences(context)
        Log.d(TAG, "User ID: $userId")

        if (NetworkUtils.isOnline()) {
            Log.d(TAG, "Network is online, proceeding with sync")

            try {
                // 1. Sync local unsaved changes to the server
                Log.d(TAG, "Syncing local unsaved changes")
                val unsyncedSplits = paymentSplitDao.getUnsyncedPaymentSplits().first() // Use first() instead of collect
                Log.d(TAG, "Found ${unsyncedSplits.size} unsynced splits")

                unsyncedSplits.forEach { splitEntity ->
                    try {
                        val split = splitEntity.toModel()
                        val serverSplit = if (splitEntity.serverId == null) {
                            Log.d(TAG, "Creating new split on server: ${split.id}")
                            apiService.createPaymentSplit(split.paymentId, split)
                        } else {
                            Log.d(TAG, "Updating existing split on server: ${split.id}")
                            apiService.updatePaymentSplit(split.paymentId, split.id, split)
                        }
                        paymentSplitDao.updatePaymentSplit(serverSplit.toEntity(SyncStatus.SYNCED))
                        Log.d(TAG, "Successfully synced split: ${serverSplit.id}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync payment split ${splitEntity.id}", e)
                        paymentSplitDao.updatePaymentSplitSyncStatus(splitEntity.id, SyncStatus.SYNC_FAILED.name)
                    }
                }

                // 2. Fetch payments and their splits from the server
                Log.d(TAG, "Fetching payments and splits from server")
                val groups = userId?.let { groupDao.getGroupsByUserId(it).first() }
                Log.d(TAG, "Found ${groups?.size ?: 0} groups for user")

                groups?.forEach { group ->
                    try {
                        Log.d(TAG, "Fetching payments for group: ${group.id}")
                        val serverPayments = apiService.getPaymentsByGroup(group.id)
                        Log.d(TAG, "Found ${serverPayments.size} payments for group ${group.id}")

                        serverPayments.forEach { serverPayment ->
                            Log.d(TAG, "Fetching splits for payment: ${serverPayment.id}")
                            val serverSplits = apiService.getPaymentSplitsByPayment(serverPayment.id)
                            Log.d(TAG, "Found ${serverSplits.size} splits for payment ${serverPayment.id}")

                            serverSplits.forEach { serverSplit ->
                                Log.d(TAG, "Processing server split: ${serverSplit.id}")
                                val localSplit = paymentSplitDao.getPaymentSplitsById(serverSplit.id).firstOrNull()
                                if (localSplit == null) {
                                    Log.d(TAG, "Inserting new split: ${serverSplit.id}")
                                    paymentSplitDao.insertPaymentSplit(serverSplit.toEntity(SyncStatus.SYNCED))
                                } else {
                                    Log.d(TAG, "Updating existing split: ${serverSplit.id}")
                                    paymentSplitDao.updatePaymentSplit(serverSplit.toEntity(SyncStatus.SYNCED))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching payments and splits for group ${group.id}", e)
                    }
                }

                Log.d(TAG, "Completed syncPaymentSplits successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync payment splits", e)
            }
        } else {
            Log.e(TAG, "No internet connection available for syncing payment splits")
        }
    }
}