package com.splitter.splittr.data.sync.managers

import android.content.Context
import android.util.Log
import com.splitter.splittr.MyApplication
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.PaymentDao
import com.splitter.splittr.data.local.dao.PaymentSplitDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.local.dao.UserDao
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
    private val userDao: UserDao,
    private val apiService: ApiService,
    syncMetadataDao: SyncMetadataDao,
    dispatchers: CoroutineDispatchers,
    private val context: Context
) : OptimizedSyncManager<PaymentWithSplits>(syncMetadataDao, dispatchers) {

    override val entityType = "payments"
    override val batchSize = 20

    val myApplication = context.applicationContext as MyApplication

    override suspend fun getLocalChanges(): List<PaymentWithSplits> =
        paymentDao.getUnsyncedPayments().first().mapNotNull { payment ->
            // Convert payment to server format
            val serverPayment = myApplication.entityServerConverter.convertPaymentToServer(payment).getOrNull() ?: return@mapNotNull null

            // Get and convert splits
            val splits = paymentSplitDao.getPaymentSplitsByPayment(payment.id).first()
            val serverSplits = splits.mapNotNull { split ->
                myApplication.entityServerConverter.convertPaymentSplitToServer(split).getOrNull()
            }

            PaymentWithSplits(serverPayment, serverSplits)
        }

    override suspend fun syncToServer(entity: PaymentWithSplits): Result<PaymentWithSplits> = try {
        // Entity is already in server format from getLocalChanges()
        val result = if (entity.payment.id == 0) {
            apiService.createPayment(entity.payment)
        } else {
            apiService.updatePayment(entity.payment.id, entity.payment)
        }

        paymentDao.runInTransaction {
            // Convert server response back to local entity
            val localPayment = myApplication.entityServerConverter.convertPaymentFromServer(result).getOrNull()
                ?: throw Exception("Failed to convert server payment to local entity")

            if (localPayment.id == 0) {
                paymentDao.insertPayment(localPayment)
            } else {
                paymentDao.updatePayment(localPayment)
                paymentDao.updatePaymentSyncStatus(localPayment.id, SyncStatus.SYNCED)
            }

            entity.splits.forEach { split ->
                val serverSplit = apiService.createPaymentSplit(result.id, split)
                // Convert server split back to local entity
                val localSplit = myApplication.entityServerConverter.convertPaymentSplitFromServer(serverSplit).getOrNull()
                    ?: throw Exception("Failed to convert server split to local entity")
                paymentSplitDao.insertPaymentSplit(localSplit)
            }
        }

        Result.success(PaymentWithSplits(result, entity.splits))
    } catch (e: Exception) {
        Log.e(TAG, "Error syncing payment to server", e)
        entity.payment.id.let {
            paymentDao.updatePaymentSyncStatus(it, SyncStatus.SYNC_FAILED)
        }
        Result.failure(e)
    }

    override suspend fun getServerChanges(since: Long): List<PaymentWithSplits> {
        val userId = getUserIdFromPreferences(context)
            ?: throw IllegalStateException("User ID not found")

        // Get the server ID from the local user ID
        val localUser = userDao.getUserByIdDirect(userId)
            ?: throw IllegalStateException("User not found in local database")

        val serverUserId = localUser.serverId
            ?: throw IllegalStateException("No server ID found for user $userId")

        return apiService.getPaymentsSince(since, serverUserId).data
    }

    override suspend fun applyServerChange(serverEntity: PaymentWithSplits) {
        paymentDao.runInTransaction {
            // Convert server payment to local entity
            val localPayment = myApplication.entityServerConverter.convertPaymentFromServer(
                serverEntity.payment,
                paymentDao.getPaymentById(serverEntity.payment.id).first()
            ).getOrNull() ?: throw Exception("Failed to convert server payment")

            when {
                localPayment.id == 0 -> paymentDao.insertPayment(localPayment)
                DateUtils.isUpdateNeeded(
                    serverEntity.payment.updatedAt,
                    localPayment.updatedAt,
                    "Payment-${localPayment.id}"
                ) -> {
                    paymentDao.updatePayment(localPayment)
                    paymentDao.updatePaymentSyncStatus(localPayment.id, SyncStatus.SYNCED)
                }
            }

            // Handle splits similarly
            serverEntity.splits.forEach { serverSplit ->
                val localSplit = myApplication.entityServerConverter.convertPaymentSplitFromServer(
                    serverSplit,
                    paymentSplitDao.getPaymentSplitById(serverSplit.id)
                ).getOrNull() ?: throw Exception("Failed to convert server split")

                if (localSplit.id == 0) {
                    paymentSplitDao.insertPaymentSplit(localSplit)
                } else if (DateUtils.isUpdateNeeded(serverSplit.updatedAt, localSplit.updatedAt)) {
                    paymentSplitDao.updatePaymentSplit(localSplit)
                }
            }
        }
    }

    companion object {
        private const val TAG = "PaymentSyncManager"
    }
}