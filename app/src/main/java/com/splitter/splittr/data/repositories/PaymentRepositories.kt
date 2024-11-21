package com.splitter.splittr.data.repositories

import android.content.Context
import android.util.Log
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.converters.LocalIdGenerator
import com.splitter.splittr.data.local.dao.GroupDao
import com.splitter.splittr.data.local.dao.GroupMemberDao
import com.splitter.splittr.data.local.dao.PaymentDao
import com.splitter.splittr.data.local.dao.PaymentSplitDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.local.dao.TransactionDao
import com.splitter.splittr.data.local.entities.PaymentEntity
import com.splitter.splittr.data.local.entities.PaymentSplitEntity
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.data.model.Payment
import com.splitter.splittr.data.model.PaymentSplit
import com.splitter.splittr.data.model.Transaction
import com.splitter.splittr.data.sync.SyncableRepository
import com.splitter.splittr.data.sync.managers.PaymentSyncManager
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.DateUtils
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
    private val groupMemberDao: GroupMemberDao,
    private val transactionDao: TransactionDao,
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

    suspend fun createPaymentFromTransaction(
        transaction: Transaction,
        payment: Payment,
        splits: List<PaymentSplit>
    ): Result<Payment> = withContext(dispatchers.io) {
        try {
            // Get userId and validate it exists
            val userId = getUserIdFromPreferences(context)
                ?: return@withContext Result.failure(IllegalStateException("User ID not found"))

            // Create a complete transaction object with all required fields
            val completeTransaction = transaction.copy(
                userId = userId,
                currency = transaction.transactionAmount.currency // Ensure currency is set from transactionAmount
            )

            Log.d(TAG, "Creating transaction with userId: $userId")

            // First save transaction locally
            transactionDao.insertTransaction(completeTransaction.toEntity(SyncStatus.PENDING_SYNC))

            // Then create transaction on server
            val serverTransaction = try {
                apiService.createTransaction(completeTransaction)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create transaction on server", e)
                // Clean up local transaction on server failure
                transactionDao.updateTransactionSyncStatus(
                    completeTransaction.transactionId,
                    SyncStatus.SYNC_FAILED
                )
                throw e
            }

            // Update local transaction with server response and SYNCED status
            transactionDao.insertTransaction(serverTransaction.toEntity(SyncStatus.SYNCED))

            // Small delay to ensure transaction is fully persisted
            delay(100)

            // Create the payment using existing function with the confirmed transaction ID
            createPaymentWithSplits(
                payment = payment.copy(
                    transactionId = serverTransaction.transactionId,
                    // Ensure payment has same user ID and currency
                    paidByUserId = userId,
                    currency = serverTransaction.transactionAmount.currency
                ),
                splits = splits
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating payment from transaction", e)
            Result.failure(e)
        }
    }


    suspend fun createPaymentWithSplits(payment: Payment, splits: List<PaymentSplit>): Result<Payment> =
        withContext(dispatchers.io) {
            Log.d(TAG, "Starting createPaymentWithSplits")
            Log.d(TAG, "Payment: $payment")
            Log.d(TAG, "Splits: ${splits.map { "Split(id=${it.id}, paymentId=${it.paymentId}, userId=${it.userId}, amount=${it.amount})" }}")

            try {
                val member = groupMemberDao.getGroupMemberByUserId(payment.paidByUserId).first()
                Log.d(TAG, "Member check result: ${member != null}, removedAt: ${member?.removedAt}")

                if (member == null || member.removedAt != null) {
                    return@withContext Result.failure(IllegalStateException("User is not a member of this group"))
                }

                val currentTime = DateUtils.getCurrentTimestamp()

                if (NetworkUtils.isOnline()) {
                    Log.d(TAG, "Device is online, creating payment on server")
                    try {
                        // Create payment on server
                        val serverPayment = apiService.createPayment(payment)
                        Log.d(TAG, "Server payment created successfully: id=${serverPayment.id}")

                        // Create splits on server
                        val serverSplits = splits.map { split ->
                            Log.d(TAG, "Creating server split: userId=${split.userId}, amount=${split.amount}")
                            val splitWithCorrectPaymentId = split.copy(paymentId = serverPayment.id)
                            val serverSplit = apiService.createPaymentSplit(serverPayment.id, splitWithCorrectPaymentId)
                            Log.d(TAG, "Server split created: id=${serverSplit.id}, paymentId=${serverSplit.paymentId}")
                            serverSplit
                        }

                        // Save to local DB
                        paymentDao.runInTransaction {
                            try {
                                // Save payment
                                val localPayment = serverPayment.toEntity(SyncStatus.SYNCED)
                                Log.d(TAG, "Inserting local payment: id=${localPayment.id}, serverId=${localPayment.serverId}")
                                paymentDao.insertPayment(localPayment)

                                // Save splits
                                serverSplits.forEach { serverSplit ->
                                    val localSplit = serverSplit.toEntity(SyncStatus.SYNCED)
                                    Log.d(TAG, "Attempting to insert local split: " +
                                            "id=${localSplit.id}, " +
                                            "serverId=${localSplit.serverId}, " +
                                            "paymentId=${localSplit.paymentId}, " +
                                            "userId=${localSplit.userId}, " +
                                            "amount=${localSplit.amount}")

                                    try {
                                        val splitId = paymentSplitDao.insertPaymentSplit(localSplit)
                                        Log.d(TAG, "Successfully inserted split with returned id: $splitId")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error inserting split: ${e.message}")
                                        Log.e(TAG, "Split that failed: $localSplit", e)
                                        throw e // Re-throw to trigger transaction rollback
                                    }
                                }

                                Log.d(TAG, "All splits inserted successfully")

                                // Update group timestamp
                                groupDao.updateGroupTimestamp(payment.groupId, currentTime)
                                Log.d(TAG, "Updated group ${payment.groupId} timestamp")
                            } catch (e: Exception) {
                                Log.e(TAG, "Transaction failed", e)
                                throw e
                            }
                        }

                        Log.d(TAG, "Entire operation completed successfully")
                        return@withContext Result.success(serverPayment)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during server operation", e)
                        throw e
                    }
                } else {
                    Log.d(TAG, "Device is offline, creating local-only records")
                    // Local-only creation with logging
                    val localPaymentId = LocalIdGenerator.nextId()
                    Log.d(TAG, "Generated local payment ID: $localPaymentId")

                    val localPayment = payment.copy(id = localPaymentId).toEntity(SyncStatus.PENDING_SYNC)
                    val localSplits = splits.map { split ->
                        val localSplitId = LocalIdGenerator.nextId()
                        Log.d(TAG, "Generated local split ID: $localSplitId for payment: $localPaymentId")
                        split.copy(
                            id = localSplitId,
                            paymentId = localPaymentId
                        ).toEntity(SyncStatus.PENDING_SYNC)
                    }

                    paymentDao.runInTransaction {
                        try {
                            paymentDao.insertPayment(localPayment)
                            Log.d(TAG, "Local payment inserted: $localPaymentId")

                            localSplits.forEach { splitEntity ->
                                try {
                                    val splitId = paymentSplitDao.insertPaymentSplit(splitEntity)
                                    Log.d(TAG, "Local split inserted: original=${splitEntity.id}, returned=$splitId")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error inserting local split", e)
                                    Log.e(TAG, "Failed split entity: $splitEntity")
                                    throw e
                                }
                            }

                            groupDao.updateGroupTimestamp(payment.groupId, currentTime)
                            Log.d(TAG, "Local operations completed successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Local transaction failed", e)
                            throw e
                        }
                    }

                    return@withContext Result.success(localPayment.toModel())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Operation failed", e)
                return@withContext Result.failure(e)
            }
        }

    // Helper function for local-only creation
    private suspend fun createLocalPaymentWithSplits(
        payment: Payment,
        splits: List<PaymentSplit>,
        timestamp: String
    ): Result<Payment> {
        try {
            // Generate local IDs
            val localPaymentId = LocalIdGenerator.nextId()
            val localPayment = payment.copy(id = localPaymentId).toEntity(SyncStatus.PENDING_SYNC)

            // Create local splits with correct payment ID reference
            val localSplits = splits.map { split ->
                split.copy(
                    id = LocalIdGenerator.nextId(),
                    paymentId = localPaymentId
                ).toEntity(SyncStatus.PENDING_SYNC)
            }

            // Save everything in a single transaction
            paymentDao.runInTransaction {
                // Save payment
                paymentDao.insertPayment(localPayment)
                Log.d(TAG, "Inserted payment locally with id: $localPaymentId")

                // Save splits
                localSplits.forEach { splitEntity ->
                    paymentSplitDao.insertPaymentSplit(splitEntity)
                    Log.d(TAG, "Inserted split locally with id: ${splitEntity.id}")
                }

                // Update group timestamp
                groupDao.updateGroupTimestamp(payment.groupId, timestamp)
                Log.d(TAG, "Updated group ${payment.groupId} timestamp to $timestamp")
            }

            return Result.success(localPayment.toModel())
        } catch (e: Exception) {
            Log.e(TAG, "Error creating local payment", e)
            return Result.failure(e)
        }
    }

    suspend fun updatePaymentWithSplits(payment: Payment, splits: List<PaymentSplit>): Result<Payment> =
        withContext(dispatchers.io) {
            try {
                // Update payment with PENDING_SYNC status
                paymentDao.updatePayment(payment.toEntity(SyncStatus.PENDING_SYNC))
                paymentDao.updatePaymentSyncStatus(payment.id, SyncStatus.PENDING_SYNC)

                // Update group's updatedAt timestamp
                val currentTime = System.currentTimeMillis().toString()
                groupDao.updateGroupTimestamp(payment.groupId, currentTime)
                Log.d(TAG, "Updated group ${payment.groupId} timestamp to $currentTime")

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
    suspend fun createPaymentSplit(paymentSplit: PaymentSplit): Result<PaymentSplit> = withContext(dispatchers.io) {
        val localId = paymentSplitDao.insertPaymentSplit(paymentSplit.toEntity(SyncStatus.PENDING_SYNC))
        val localSplit = paymentSplit.copy(id = localId.toInt())
        try {
            if (NetworkUtils.isOnline()) {
                val serverSplit = apiService.createPaymentSplit(paymentSplit.paymentId, localSplit)

                // Update both split and parent payment in a transaction
                paymentSplitDao.runInTransaction {
                    // Update the split
                    paymentSplitDao.updatePaymentSplit(serverSplit.toEntity(SyncStatus.SYNCED))
                    paymentSplitDao.updatePaymentSplitSyncStatus(serverSplit.id, SyncStatus.SYNCED)

                    // Update parent payment's timestamp
                    val currentTimestamp = DateUtils.getCurrentTimestamp()
                    val payment = paymentDao.getPaymentById(paymentSplit.paymentId).first()
                    payment?.let {
                        paymentDao.updatePayment(
                            it.copy(updatedAt = currentTimestamp, syncStatus = SyncStatus.PENDING_SYNC)
                        )
                    }
                }

                Result.success(serverSplit)
            } else {
                Result.success(localSplit)
            }
        } catch (e: Exception) {
            // Mark as failed if server sync fails
            if (NetworkUtils.isOnline()) {
                paymentSplitDao.updatePaymentSplitSyncStatus(localSplit.id, SyncStatus.SYNC_FAILED)
            }
            Result.failure(e)
        }
    }

    suspend fun updatePaymentSplit(paymentSplit: PaymentSplit): Result<PaymentSplit> = withContext(dispatchers.io) {
        try {
            // Update split with pending sync status
            paymentSplitDao.updatePaymentSplit(paymentSplit.toEntity(SyncStatus.PENDING_SYNC))
            paymentSplitDao.updatePaymentSplitSyncStatus(paymentSplit.id, SyncStatus.PENDING_SYNC)

            if (NetworkUtils.isOnline()) {
                val serverSplit = apiService.updatePaymentSplit(
                    paymentSplit.paymentId,
                    paymentSplit.id,
                    paymentSplit
                )

                // Update both split and parent payment in a transaction
                paymentSplitDao.runInTransaction {
                    // Update the split
                    paymentSplitDao.updatePaymentSplit(serverSplit.toEntity(SyncStatus.SYNCED))
                    paymentSplitDao.updatePaymentSplitSyncStatus(serverSplit.id, SyncStatus.SYNCED)

                    // Update parent payment's timestamp
                    val currentTimestamp = DateUtils.getCurrentTimestamp()
                    val payment = paymentDao.getPaymentById(paymentSplit.paymentId).first()
                    payment?.let {
                        paymentDao.updatePayment(
                            it.copy(updatedAt = currentTimestamp, syncStatus = SyncStatus.PENDING_SYNC)
                        )
                    }
                }

                Result.success(serverSplit)
            } else {
                Result.success(paymentSplit)
            }
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
            paymentDao.archivePayment(paymentId, DateUtils.getCurrentTimestamp())
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