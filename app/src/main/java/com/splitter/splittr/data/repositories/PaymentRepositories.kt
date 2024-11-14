package com.splitter.splittr.data.repositories

import android.content.Context
import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.GroupDao
import com.splitter.splittr.data.local.dao.PaymentDao
import com.splitter.splittr.data.local.dao.PaymentSplitDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.local.entities.PaymentEntity
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.data.model.Payment
import com.splitter.splittr.data.model.PaymentSplit
import com.splitter.splittr.data.sync.SyncableRepository
import com.splitter.splittr.data.sync.managers.PaymentSyncManager
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.NetworkUtils
import com.splitter.splittr.utils.NetworkUtils.isOnline
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException

class PaymentRepository(
    private val paymentDao: PaymentDao,
    private val paymentSplitDao: PaymentSplitDao,
    private val groupDao: GroupDao,
    private val apiService: ApiService,
    private val dispatchers: CoroutineDispatchers,
    private val context: Context,
    private val syncMetadataDao: SyncMetadataDao,
    private val paymentSyncManager: PaymentSyncManager
) : SyncableRepository {

    override val entityType = "payments"
    override val syncPriority = 4

    private val _payments = MutableStateFlow<List<Payment>>(emptyList())
    val payments: StateFlow<List<Payment>> = _payments.asStateFlow()

    fun getPaymentById(paymentId: Int): Flow<Payment?> =
        paymentDao.getPaymentById(paymentId).map { it?.toModel() }

    fun getPaymentByTransactionId(transactionId: String): Flow<Payment?> =
        paymentDao.getPaymentByTransactionId(transactionId).map { it?.toModel() }

    fun getPaymentsByGroup(groupId: Int): Flow<List<PaymentEntity>> = flow {
        // Emit local data first
        val localPayments = paymentDao.getPaymentsByGroup(groupId).first()
        emit(localPayments)

        // Then try to fetch from API and update local database
        if (isOnline()) {
            try {
                val apiPayments = apiService.getPaymentsByGroup(groupId)
                val paymentEntities = apiPayments.map { it.toEntity(SyncStatus.SYNCED) }
                Log.d("PaymentRepository", "Converted API payments to entities: $paymentEntities")
                paymentDao.insertOrUpdatePayments(paymentEntities)

                // Emit updated data
                val updatedPayments = paymentDao.getPaymentsByGroup(groupId).first()
                if (updatedPayments != localPayments) {
                    emit(updatedPayments)
                }
            } catch (e: Exception) {
                Log.e("PaymentRepository", "Error fetching payments from API", e)
            }
        }
    }.flowOn(dispatchers.io)

    suspend fun createPayment(payment: Payment): Result<Payment> = withContext(dispatchers.io) {
        try {
            val localId = paymentDao.insertPayment(payment.toEntity(SyncStatus.PENDING_SYNC))
            val serverPayment = apiService.createPayment(payment)
            val updatedPayment = serverPayment.copy(id = localId.toInt())
            paymentDao.updatePayment(updatedPayment.toEntity(SyncStatus.SYNCED))
            Result.success(updatedPayment)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePayment(payment: Payment): Result<Payment> = withContext(dispatchers.io) {
        try {
            paymentDao.updatePayment(payment.toEntity(SyncStatus.PENDING_SYNC))
            val serverPayment = apiService.updatePayment(payment.id, payment)
            paymentDao.updatePayment(serverPayment.toEntity(SyncStatus.SYNCED))
            Result.success(serverPayment)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun createPaymentWithSplits(payment: Payment, splits: List<PaymentSplit>): Result<Payment> =
        withContext(dispatchers.io) {
            try {
                // Save locally first
                val localId = paymentDao.insertPayment(payment.toEntity(SyncStatus.PENDING_SYNC))
                val paymentWithId = payment.copy(id = localId.toInt())

                // Save splits locally with PENDING_SYNC status
                val localSplits = splits.map { split ->
                    val localSplitId = paymentSplitDao.insertPaymentSplit(
                        split.copy(paymentId = localId.toInt())
                            .toEntity(SyncStatus.PENDING_SYNC)
                    )
                    split.copy(id = localSplitId.toInt(), paymentId = localId.toInt())
                }

                // Try to sync immediately if online
                if (NetworkUtils.isOnline()) {
                    try {
                        // Sync payment
                        val serverPayment = apiService.createPayment(paymentWithId)
                        paymentDao.updatePayment(serverPayment.toEntity(SyncStatus.SYNCED))
                        paymentDao.updatePaymentSyncStatus(serverPayment.id, SyncStatus.SYNCED) // Added explicit sync status update

                        // Sync splits
                        localSplits.forEach { localSplit ->
                            val serverSplit = apiService.createPaymentSplit(serverPayment.id, localSplit)
                            paymentSplitDao.updatePaymentSplit(serverSplit.toEntity(SyncStatus.SYNCED))
                            paymentSplitDao.updatePaymentSplitSyncStatus(serverSplit.id, SyncStatus.SYNCED) // Added explicit sync status update
                        }

                        Result.success(serverPayment)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync with server", e)
                        // Mark as SYNC_FAILED instead of returning success
                        paymentDao.updatePaymentSyncStatus(paymentWithId.id, SyncStatus.SYNC_FAILED)
                        localSplits.forEach { split ->
                            paymentSplitDao.updatePaymentSplitSyncStatus(split.id, SyncStatus.SYNC_FAILED)
                        }
                        Result.failure(e)
                    }
                } else {
                    Result.success(paymentWithId)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun updatePaymentWithSplits(payment: Payment, splits: List<PaymentSplit>): Result<Payment> =
        withContext(dispatchers.io) {
            try {
                // Update payment with PENDING_SYNC status
                paymentDao.updatePayment(payment.toEntity(SyncStatus.PENDING_SYNC))
                paymentDao.updatePaymentSyncStatus(payment.id, SyncStatus.PENDING_SYNC)

                // Track local split IDs
                val updatedSplitIds = mutableListOf<Int>()

                // Update/insert splits with PENDING_SYNC status
                splits.forEach { split ->
                    val splitId = if (split.id == 0) {
                        val localId = paymentSplitDao.insertPaymentSplit(
                            split.copy(paymentId = payment.id)
                                .toEntity(SyncStatus.PENDING_SYNC)
                        )
                        localId.toInt()
                    } else {
                        paymentSplitDao.updatePaymentSplit(split.toEntity(SyncStatus.PENDING_SYNC))
                        paymentSplitDao.updatePaymentSplitSyncStatus(split.id, SyncStatus.PENDING_SYNC)
                        split.id
                    }
                    updatedSplitIds.add(splitId)
                }

                val updatedPayment = paymentDao.getPaymentById(payment.id).first()
                    ?: return@withContext Result.failure(Exception("Failed to retrieve updated payment"))

                if (isOnline()) {
                    try {
                        // Sync payment
                        val serverPayment = apiService.updatePayment(payment.id, updatedPayment.toModel())
                        paymentDao.updatePayment(serverPayment.toEntity(SyncStatus.SYNCED))
                        paymentDao.updatePaymentSyncStatus(serverPayment.id, SyncStatus.SYNCED)

                        // Sync splits
                        splits.zip(updatedSplitIds).forEach { (split, localId) ->
                            val serverSplit = if (split.id == 0) {
                                apiService.createPaymentSplit(serverPayment.id, split.copy(id = localId))
                            } else {
                                apiService.updatePaymentSplit(serverPayment.id, split.id, split)
                            }
                            paymentSplitDao.updatePaymentSplit(serverSplit.toEntity(SyncStatus.SYNCED))
                            paymentSplitDao.updatePaymentSplitSyncStatus(serverSplit.id, SyncStatus.SYNCED)
                        }

                        Result.success(serverPayment)
                    } catch (e: Exception) {
                        Log.e("PaymentRepository", "Error syncing with server", e)
                        // Mark everything as SYNC_FAILED
                        paymentDao.updatePaymentSyncStatus(payment.id, SyncStatus.SYNC_FAILED)
                        updatedSplitIds.forEach { splitId ->
                            paymentSplitDao.updatePaymentSplitSyncStatus(splitId, SyncStatus.SYNC_FAILED)
                        }
                        Result.failure(e)
                    }
                }

                Result.success(updatedPayment.toModel())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // Single split operations
    suspend fun createPaymentSplit(paymentSplit: PaymentSplit): Result<PaymentSplit> =
        withContext(dispatchers.io) {
            val localId = paymentSplitDao.insertPaymentSplit(paymentSplit.toEntity(SyncStatus.PENDING_SYNC))
            try {
                val localSplit = paymentSplit.copy(id = localId.toInt())

                val serverSplit = apiService.createPaymentSplit(paymentSplit.paymentId, localSplit)
                paymentSplitDao.updatePaymentSplit(serverSplit.toEntity(SyncStatus.SYNCED))
                paymentSplitDao.updatePaymentSplitSyncStatus(serverSplit.id, SyncStatus.SYNCED)

                Result.success(serverSplit)
            } catch (e: Exception) {
                // Mark as failed if server sync fails
                if (isOnline()) {
                    paymentSplitDao.updatePaymentSplitSyncStatus(localId.toInt(), SyncStatus.SYNC_FAILED)
                }
                Result.failure(e)
            }
        }

    suspend fun updatePaymentSplit(paymentSplit: PaymentSplit): Result<PaymentSplit> =
        withContext(dispatchers.io) {
            try {
                paymentSplitDao.updatePaymentSplit(paymentSplit.toEntity(SyncStatus.PENDING_SYNC))
                paymentSplitDao.updatePaymentSplitSyncStatus(paymentSplit.id, SyncStatus.PENDING_SYNC)

                val serverSplit = apiService.updatePaymentSplit(
                    paymentSplit.paymentId,
                    paymentSplit.id,
                    paymentSplit
                )
                paymentSplitDao.updatePaymentSplit(serverSplit.toEntity(SyncStatus.SYNCED))
                paymentSplitDao.updatePaymentSplitSyncStatus(serverSplit.id, SyncStatus.SYNCED)

                Result.success(serverSplit)
            } catch (e: Exception) {
                if (NetworkUtils.isOnline()) {
                    paymentSplitDao.updatePaymentSplitSyncStatus(paymentSplit.id, SyncStatus.SYNC_FAILED)
                }
                Result.failure(e)
            }
        }

    suspend fun archivePayment(paymentId: Int): Result<Unit> = withContext(dispatchers.io) {
        try {
            apiService.archivePayment(paymentId)
            paymentDao.archivePayment(paymentId, System.currentTimeMillis().toString())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restorePayment(paymentId: Int): Result<Unit> = withContext(dispatchers.io) {
        try {
            apiService.restorePayment(paymentId)
            paymentDao.restorePayment(paymentId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sync(): Unit = withContext(dispatchers.io) {
        try {
            Log.d(TAG, "Starting payment sync process")

            if (!NetworkUtils.isOnline()) {
                Log.e(TAG, "No network connection available")
                throw IOException("No network connection available")
            }

            paymentSyncManager.performSync()

            // Update sync metadata
            syncMetadataDao.update(entityType) {
                it.copy(
                    lastSyncTimestamp = System.currentTimeMillis(),
                    syncStatus = SyncStatus.SYNCED,
                    lastSyncResult = "Sync completed successfully"
                )
            }

            Log.d(TAG, "Payment sync completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during payment sync", e)
            syncMetadataDao.update(entityType) {
                it.copy(
                    syncStatus = SyncStatus.SYNC_FAILED,
                    lastSyncResult = "Sync failed: ${e.message}"
                )
            }
            throw e
        }
    }
    companion object {
        private const val TAG = "PaymentRepository"
    }
}