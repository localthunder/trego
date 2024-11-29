package com.splitter.splittr.data.sync.managers

import android.content.Context
import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.PaymentDao
import com.splitter.splittr.data.local.dao.PaymentSplitDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.local.dataClasses.PaymentWithSplits
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
) : OptimizedSyncManager<PaymentWithSplits>(syncMetadataDao, dispatchers) {

    override val entityType = "payments"
    override val batchSize = 20

    override suspend fun getLocalChanges(): List<PaymentWithSplits> =
        paymentDao.getUnsyncedPayments().first().map { payment ->
            PaymentWithSplits(
                payment = payment.toModel(),
                splits = paymentSplitDao.getPaymentSplitsByPayment(payment.id).first().map { it.toModel() }
            )
        }

    override suspend fun syncToServer(entity: PaymentWithSplits): Result<PaymentWithSplits> = try {
        val result = if (entity.payment.id == 0) {
            apiService.createPayment(entity.payment)
        } else {
            apiService.updatePayment(entity.payment.id, entity.payment)
        }

        paymentDao.runInTransaction {
            val entityToUpdate = result.toEntity(SyncStatus.SYNCED)
            if (entityToUpdate.id == 0) {
                paymentDao.insertPayment(entityToUpdate)
            } else {
                paymentDao.updatePayment(entityToUpdate)
                paymentDao.updatePaymentSyncStatus(entityToUpdate.id, SyncStatus.SYNCED)
            }

            entity.splits.forEach { split ->
                val serverSplit = apiService.createPaymentSplit(entityToUpdate.id, split)
                paymentSplitDao.insertPaymentSplit(serverSplit.toEntity(SyncStatus.SYNCED))
            }
        }

        Result.success(PaymentWithSplits(result, entity.splits))
    } catch (e: Exception) {
        Log.e(TAG, "Error syncing payment to server", e)
        entity.payment.id.let { paymentDao.updatePaymentSyncStatus(it, SyncStatus.SYNC_FAILED) }
        Result.failure(e)
    }

    override suspend fun getServerChanges(since: Long): List<PaymentWithSplits> {
        val userId = getUserIdFromPreferences(context)
            ?: throw IllegalStateException("User ID not found")
        return apiService.getPaymentsSince(since, userId).data
    }

    override suspend fun applyServerChange(serverEntity: PaymentWithSplits) {
        paymentDao.runInTransaction {
            val localEntity = paymentDao.getPaymentById(serverEntity.payment.id).first()
            val timestamp = DateUtils.standardizeTimestamp(serverEntity.payment.updatedAt)

            when {
                localEntity == null -> paymentDao.insertPayment(
                    serverEntity.payment.copy(updatedAt = timestamp).toEntity(SyncStatus.SYNCED)
                )
                DateUtils.isUpdateNeeded(serverEntity.payment.updatedAt, localEntity.updatedAt,
                    "Payment-${serverEntity.payment.id}") -> {
                    paymentDao.updatePayment(
                        serverEntity.payment.copy(updatedAt = timestamp).toEntity(SyncStatus.SYNCED)
                    )
                    paymentDao.updatePaymentSyncStatus(serverEntity.payment.id, SyncStatus.SYNCED)
                }
            }

            serverEntity.splits.forEach { split ->
                val localSplit = paymentSplitDao.getPaymentSplitById(split.id)
                if (localSplit == null) {
                    paymentSplitDao.insertPaymentSplit(split.toEntity(SyncStatus.SYNCED))
                } else if (DateUtils.isUpdateNeeded(split.updatedAt, localSplit.updatedAt)) {
                    paymentSplitDao.updatePaymentSplit(split.toEntity(SyncStatus.SYNCED))
                }
            }
        }
    }

    companion object {
        private const val TAG = "PaymentSyncManager"
    }
}