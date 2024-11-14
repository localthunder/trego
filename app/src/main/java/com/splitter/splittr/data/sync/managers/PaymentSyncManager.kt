package com.splitter.splittr.data.sync.managers

import android.content.Context
import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.PaymentDao
import com.splitter.splittr.data.local.dao.PaymentSplitDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.model.Group
import com.splitter.splittr.data.model.Payment
import com.splitter.splittr.data.model.PaymentSplit
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.GroupSyncManager
import com.splitter.splittr.data.sync.OptimizedSyncManager
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.utils.ConflictResolution
import com.splitter.splittr.utils.ConflictResolver
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.NetworkUtils
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class PaymentSyncManager(
    private val paymentDao: PaymentDao,
    private val paymentSplitDao: PaymentSplitDao,
    private val apiService: ApiService,
    syncMetadataDao: SyncMetadataDao,
    dispatchers: CoroutineDispatchers,
    private val context: Context
) : OptimizedSyncManager<Payment>(syncMetadataDao, dispatchers) {

    override val entityType = "payments"
    override val batchSize = 20

    override suspend fun getLocalChanges(): List<Payment> =
        paymentDao.getUnsyncedPayments().first().map { it.toModel() }

    override suspend fun syncToServer(entity: Payment): Result<Payment> = try {
        Log.d(TAG, "Syncing payment to server: ${entity.id}")

        val result = if (entity.id == 0) {
            Log.d(TAG, "Creating new payment on server")
            apiService.createPayment(entity)
        } else {
            Log.d(TAG, "Updating existing payment on server: ${entity.id}")
            apiService.updatePayment(entity.id, entity)
        }

        // Update local sync status after successful server sync
        paymentDao.updatePaymentSyncStatus(result.id, SyncStatus.SYNCED)

        // After successful payment sync, sync its splits
        val splits = paymentSplitDao.getPaymentSplitsByPayment(entity.id).first()
        syncPaymentSplits(result.id, splits.map { it.toModel() })

        Result.success(result)
    } catch (e: Exception) {
        Log.e(TAG, "Error syncing payment to server: ${entity.id}", e)
        // Update sync status to failed
        entity.id.let { paymentDao.updatePaymentSyncStatus(it, SyncStatus.SYNC_FAILED) }
        Result.failure(e)
    }

    override suspend fun getServerChanges(since: Long): List<Payment> {
        val userId = getUserIdFromPreferences(context)
            ?: throw IllegalStateException("User ID not found")

        try {
            Log.d(TAG, "Fetching payments since $since")
            val response = apiService.getPaymentsSince(since, userId)

            response.data.forEach { paymentWithSplits ->
                try {
                    // Update the payment
                    paymentDao.updatePayment(paymentWithSplits.payment.toEntity(SyncStatus.SYNCED))

                    // Update all splits for this payment
                    paymentWithSplits.splits.forEach { split ->
                        paymentSplitDao.updatePaymentSplit(split.toEntity(SyncStatus.SYNCED))
                    }

                    Log.d(TAG, "Successfully processed payment ${paymentWithSplits.payment.id} with ${paymentWithSplits.splits.size} splits")
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing payment ${paymentWithSplits.payment.id}", e)
                }
            }

            return response.data.mapNotNull { it.payment }

        } catch (e: Exception) {
            Log.e(TAG, "Error getting server changes", e)
            throw e
        }
    }

    private suspend fun syncPaymentSplits(paymentId: Int, splits: List<PaymentSplit>): Result<List<PaymentSplit>> = try {
        Log.d(TAG, "Syncing ${splits.size} splits for payment $paymentId")

        val results = splits.map { split ->
            try {
                val serverSplit = if (split.id == 0) {
                    Log.d(TAG, "Creating new split for payment $paymentId")
                    apiService.createPaymentSplit(paymentId, split)
                } else {
                    Log.d(TAG, "Updating split ${split.id} for payment $paymentId")
                    apiService.updatePaymentSplit(paymentId, split.id, split)
                }

                // Insert or update with SYNCED status
                val syncedEntity = serverSplit.toEntity(SyncStatus.SYNCED)
                if (split.id == 0) {
                    Log.d(TAG, "Inserting new synced split ${syncedEntity.id}")
                    paymentSplitDao.insertPaymentSplit(syncedEntity)
                } else {
                    Log.d(TAG, "Updating split ${syncedEntity.id} with sync status SYNCED")
                    paymentSplitDao.updatePaymentSplit(syncedEntity)
                    paymentSplitDao.updatePaymentSplitSyncStatus(syncedEntity.id, SyncStatus.SYNCED)
                }

                Log.d(TAG, "Successfully synced split ${serverSplit.id}")
                Result.success(serverSplit)
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing split ${split.id}", e)
                // Update sync status to failed
                if (split.id != 0) {
                    paymentSplitDao.updatePaymentSplitSyncStatus(split.id, SyncStatus.SYNC_FAILED)
                }
                Result.failure(e)
            }
        }

        if (results.all { it.isSuccess }) {
            Result.success(results.mapNotNull { it.getOrNull() })
        } else {
            Result.failure(Exception("Some splits failed to sync"))
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error syncing splits for payment $paymentId", e)
        Result.failure(e)
    }

    private suspend fun syncSplitsFromServer(paymentId: Int) {
        try {
            Log.d(TAG, "Fetching splits from server for payment $paymentId")
            val serverSplits = apiService.getPaymentSplitsByPayment(paymentId)

            serverSplits.forEach { serverSplit ->
                paymentSplitDao.runInTransaction {
                    val localSplit = paymentSplitDao.getPaymentSplitById(serverSplit.id)

                    when {
                        localSplit == null -> {
                            Log.d(TAG, "Inserting new split from server: ${serverSplit.id}")
                            paymentSplitDao.insertPaymentSplit(serverSplit.toEntity(SyncStatus.SYNCED))
                        }
                        serverSplit.updatedAt > localSplit.updatedAt -> {
                            Log.d(TAG, "Updating existing split from server: ${serverSplit.id}")
                            paymentSplitDao.updatePaymentSplit(serverSplit.toEntity(SyncStatus.SYNCED))
                            paymentSplitDao.updatePaymentSplitSyncStatus(serverSplit.id, SyncStatus.SYNCED)
                        }
                        else -> {
                            Log.d(TAG, "Local split ${localSplit.id} is up to date")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing splits from server for payment $paymentId", e)
            throw e
        }
    }

    // Handle server changes
    override suspend fun applyServerChange(serverEntity: Payment) {
        paymentDao.runInTransaction {
            val localEntity = paymentDao.getPaymentById(serverEntity.id).first()

            when {
                localEntity == null -> {
                    Log.d(TAG, "Inserting new payment from server: ${serverEntity.id}")
                    paymentDao.insertPayment(serverEntity.toEntity(SyncStatus.SYNCED))
                    // Sync splits for new payment
                    syncSplitsFromServer(serverEntity.id)
                }
                serverEntity.updatedAt > localEntity.updatedAt -> {
                    Log.d(TAG, "Updating existing payment from server: ${serverEntity.id}")
                    paymentDao.updatePayment(serverEntity.toEntity(SyncStatus.SYNCED))
                    paymentDao.updatePaymentSyncStatus(serverEntity.id, SyncStatus.SYNCED)
                    // Sync splits for updated payment
                    syncSplitsFromServer(serverEntity.id)
                }
                else -> {
                    Log.d(TAG, "Local payment ${serverEntity.id} is up to date")
                }
            }
        }
    }

    companion object {
        private const val TAG = "PaymentSyncManager"
    }
}