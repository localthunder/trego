package com.splitter.splittr.data.local.repositories

import android.content.Context
import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.GroupDao
import com.splitter.splittr.data.local.dao.PaymentDao
import com.splitter.splittr.data.local.dao.PaymentSplitDao
import com.splitter.splittr.data.local.entities.PaymentEntity
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.model.Payment
import com.splitter.splittr.model.PaymentSplit
import com.splitter.splittr.utils.CoroutineDispatchers
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

class PaymentRepository(
    private val paymentDao: PaymentDao,
    private val paymentSplitDao: PaymentSplitDao,
    private val groupDao: GroupDao,
    private val apiService: ApiService,
    private val dispatchers: CoroutineDispatchers,
    private val context: Context
) {

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


    suspend fun createPaymentWithSplits(payment: Payment, splits: List<PaymentSplit>): Result<Payment> = withContext(dispatchers.io) {
        try {
            val paymentId = paymentDao.insertPayment(payment.toEntity(SyncStatus.PENDING_SYNC))

            splits.forEach { split ->
                paymentSplitDao.insertPaymentSplit(split.copy(paymentId = paymentId.toInt()).toEntity(SyncStatus.PENDING_SYNC))
            }

            val localPayment = paymentDao.getPaymentById(paymentId.toInt()).first()

            if (localPayment == null) {
                return@withContext Result.failure(Exception("Failed to retrieve created payment"))
            }

            if (isOnline()) {
                try {
                    val serverPayment = apiService.createPayment(localPayment.toModel())
                    paymentDao.updatePayment(serverPayment.toEntity(SyncStatus.SYNCED))
                    paymentDao.updatePaymentSyncStatus(serverPayment.id, SyncStatus.SYNCED)

                    splits.forEach { split ->
                        val serverSplit = apiService.createPaymentSplit(serverPayment.id, split)
                        paymentSplitDao.updatePaymentSplit(serverSplit.toEntity(SyncStatus.SYNCED))
                    }
                } catch (e: Exception) {
                    Log.e("PaymentRepository", "Error syncing new payment with server", e)
                }
            }

            Result.success(localPayment.toModel())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePaymentWithSplits(payment: Payment, splits: List<PaymentSplit>): Result<Payment> = withContext(dispatchers.io) {
        try {
            paymentDao.updatePayment(payment.toEntity(SyncStatus.PENDING_SYNC))
            paymentDao.updatePaymentSyncStatus(payment.id, SyncStatus.PENDING_SYNC)

            splits.forEach { split ->
                if (split.id == 0) {
                    paymentSplitDao.insertPaymentSplit(split.copy(paymentId = payment.id).toEntity(SyncStatus.PENDING_SYNC))
                } else {
                    paymentSplitDao.updatePaymentSplit(split.toEntity(SyncStatus.PENDING_SYNC))
                }
            }

            val updatedPayment = paymentDao.getPaymentById(payment.id).first()

            if (updatedPayment == null) {
                return@withContext Result.failure(Exception("Failed to retrieve updated payment"))
            }

            if (isOnline()) {
                try {
                    val serverPayment = apiService.updatePayment(payment.id, updatedPayment.toModel())
                    paymentDao.updatePayment(serverPayment.toEntity(SyncStatus.SYNCED))
                    paymentDao.updatePaymentSyncStatus(serverPayment.id, SyncStatus.SYNCED)

                    splits.forEach { split ->
                        if (split.id == 0) {
                            val serverSplit = apiService.createPaymentSplit(serverPayment.id, split)
                            paymentSplitDao.updatePaymentSplit(serverSplit.toEntity(SyncStatus.SYNCED))
                        } else {
                            val serverSplit = apiService.updatePaymentSplit(serverPayment.id, split.id, split)
                            paymentSplitDao.updatePaymentSplit(serverSplit.toEntity(SyncStatus.SYNCED))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PaymentRepository", "Error syncing updated payment with server", e)
                }
            }

            Result.success(updatedPayment.toModel())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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

    suspend fun syncPayments() = withContext(dispatchers.io) {
        Log.d("PaymentRepository", "Starting syncPayments")
        val userId = getUserIdFromPreferences(context)
        Log.d("PaymentRepository", "User ID: $userId")

        if (userId == null) {
            Log.e("PaymentRepository", "User ID is null, cannot sync payments")
            return@withContext
        }

        Log.d("PaymentRepository", "Starting sync of local payments to server")
        val unsyncedPayments = paymentDao.getUnsyncedPayments().first()
        Log.d("PaymentRepository", "Number of unsynced payments: ${unsyncedPayments.size}")

        unsyncedPayments.forEach { paymentEntity ->
            try {
                Log.d("PaymentRepository", "Syncing payment: ${paymentEntity.id}")
                val payment = paymentEntity.toModel()
                val serverPayment = if (paymentEntity.serverId == null) {
                    Log.d("PaymentRepository", "Creating new payment on server")
                    apiService.createPayment(payment)
                } else {
                    Log.d("PaymentRepository", "Updating existing payment on server")
                    apiService.updatePayment(paymentEntity.serverId, payment)
                }
                Log.d("PaymentRepository", "Server response received for payment: ${serverPayment.id}")
                val updatedPaymentEntity = serverPayment.toEntity(SyncStatus.SYNCED)
                paymentDao.updatePayment(updatedPaymentEntity)
                Log.d("PaymentRepository", "Local database updated for payment: ${updatedPaymentEntity.id}")
            } catch (e: Exception) {
                paymentDao.updatePaymentSyncStatus(paymentEntity.id, SyncStatus.SYNC_FAILED)
                Log.e("PaymentRepository", "Error syncing payment ${paymentEntity.id}", e)
            }
            delay(100)
        }

        Log.d("PaymentRepository", "Starting fetch of payments from server with user id: $userId")
        try {
            val groups = apiService.getGroupsByUserId(userId)
            Log.d("PaymentRepository", "Number of user groups: ${groups.size}")

            groups.forEach { group ->
                try {
                    Log.d("PaymentRepository", "Fetching payments for group: ${group.id}")
                    val serverPayments = apiService.getPaymentsByGroup(group.id)
                    Log.d("PaymentRepository", "Number of payments fetched for group ${group.id}: ${serverPayments.size}")

                    serverPayments.forEach { serverPayment ->
                        Log.d("PaymentRepository", "Processing server payment: ${serverPayment.id}")
                        val paymentEntity = serverPayment.toEntity(SyncStatus.SYNCED)
                        paymentDao.insertOrUpdatePayment(paymentEntity)
                        Log.d("PaymentRepository", "Payment ${paymentEntity.id} inserted/updated in local database")
                    }
                    delay(100)
                } catch (e: Exception) {
                    Log.e("PaymentRepository", "Error fetching payments for group ${group.id}", e)
                }
            }
        } catch (e: Exception) {
            Log.e("PaymentRepository", "Error fetching groups for user $userId", e)
        }

        Log.d("PaymentRepository", "syncPayments completed")
    }
}