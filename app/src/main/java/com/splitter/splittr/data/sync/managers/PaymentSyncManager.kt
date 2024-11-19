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
import com.splitter.splittr.utils.DateUtils
import com.splitter.splittr.utils.NetworkUtils
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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

        // Wrap in transaction
        paymentDao.runInTransaction {
            // Update local sync status after successful server sync
            paymentDao.updatePaymentSyncStatus(result.id, SyncStatus.SYNCED)
            paymentDao.updatePayment(result.toEntity(SyncStatus.SYNCED))
        }

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
        val userId = getUserIdFromPreferences(context) ?:
        throw IllegalStateException("User ID not found")

        try {
            Log.d(TAG, "Fetching payments since $since")
            val response = apiService.getPaymentsSince(since, userId)

            // Process in batches to avoid transaction timeout
            response.data.chunked(batchSize).forEach { batch ->
                paymentDao.runInTransaction {
                    batch.forEach { paymentWithSplits ->
                        try {
                            // Insert/Update payment
                            val payment = paymentWithSplits.payment.toEntity(SyncStatus.SYNCED)
                            paymentDao.insertOrUpdatePayment(payment)

                            // Insert/Update all splits
                            paymentWithSplits.splits.forEach { split ->
                                paymentSplitDao.insertPaymentSplit(
                                    split.toEntity(SyncStatus.SYNCED)
                                )
                            }

                            Log.d(TAG, "Successfully processed payment ${paymentWithSplits.payment.id} with ${paymentWithSplits.splits.size} splits")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing payment ${paymentWithSplits.payment.id}", e)
                            throw e  // Rollback transaction on error
                        }
                    }
                }
            }

            return response.data.mapNotNull { it.payment }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting server changes", e)
            throw e
        }
    }

    override suspend fun applyServerChange(serverEntity: Payment) {
        paymentDao.runInTransaction {
            try {
                val localEntity = paymentDao.getPaymentById(serverEntity.id).first()
                val standardizedServerTimestamp = DateUtils.standardizeTimestamp(serverEntity.updatedAt)

                when {
                    localEntity == null -> {
                        Log.d(TAG, "Inserting new payment from server: ${serverEntity.id}")
                        paymentDao.insertPayment(
                            serverEntity
                                .copy(updatedAt = standardizedServerTimestamp)
                                .toEntity(SyncStatus.SYNCED)
                        )
                    }
                    DateUtils.isUpdateNeeded(
                        serverEntity.updatedAt,
                        localEntity.updatedAt,
                        "Payment-${serverEntity.id}-Group-${serverEntity.groupId}"
                    ) -> {
                        Log.d(TAG, "Updating existing payment from server: ${serverEntity.id}")
                        paymentDao.updatePayment(
                            serverEntity
                                .copy(updatedAt = standardizedServerTimestamp)
                                .toEntity(SyncStatus.SYNCED)
                        )
                        paymentDao.updatePaymentSyncStatus(serverEntity.id, SyncStatus.SYNCED)
                    }
                    else -> {
                        Log.d(TAG, "Local payment ${serverEntity.id} is up to date")
                    }
                }

                // Always sync splits for the payment
                syncSplitsFromServer(serverEntity.id)
            } catch (e: Exception) {
                Log.e(TAG, "Error applying server changes for payment ${serverEntity.id}", e)
                throw e  // Rollback transaction
            }
        }
    }

    private suspend fun syncSplitsFromServer(paymentId: Int) {
        try {
            Log.d(TAG, "Fetching splits from server for payment $paymentId")
            val serverSplits = apiService.getPaymentSplitsByPayment(paymentId)

            paymentSplitDao.runInTransaction {
                serverSplits.forEach { serverSplit ->
                    val localSplit = paymentSplitDao.getPaymentSplitById(serverSplit.id)

                    when {
                        localSplit == null -> {
                            Log.d(TAG, "Inserting new split from server: ${serverSplit.id}")
                            paymentSplitDao.insertPaymentSplit(
                                serverSplit.toEntity(SyncStatus.SYNCED)
                            )
                        }
                        serverSplit.updatedAt > localSplit.updatedAt -> {
                            Log.d(TAG, "Updating existing split from server: ${serverSplit.id}")
                            paymentSplitDao.updatePaymentSplit(
                                serverSplit.toEntity(SyncStatus.SYNCED)
                            )
                            paymentSplitDao.updatePaymentSplitSyncStatus(
                                serverSplit.id,
                                SyncStatus.SYNCED
                            )
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

    private suspend fun syncPaymentSplits(paymentId: Int, splits: List<PaymentSplit>): Result<List<PaymentSplit>> =
        withContext(dispatchers.io) {
            try {
                Log.d(TAG, "Syncing ${splits.size} splits for payment $paymentId")

                // Process all splits within a single transaction
                paymentSplitDao.runInTransaction {
                    splits.map { split ->
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
                            serverSplit
                        } catch (e: Exception) {
                            Log.e(TAG, "Error syncing split ${split.id}", e)
                            // Update sync status to failed
                            if (split.id != 0) {
                                paymentSplitDao.updatePaymentSplitSyncStatus(split.id, SyncStatus.SYNC_FAILED)
                            }
                            throw e // Rolls back the transaction
                        }
                    }
                }.let { syncedSplits ->
                    Result.success(syncedSplits)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing splits for payment $paymentId", e)
                Result.failure(e)
            }
        }

    companion object {
        private const val TAG = "PaymentSyncManager"
    }
}