package com.splitter.splittr.data.repositories

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import com.splitter.splittr.MyApplication
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.GroupDao
import com.splitter.splittr.data.local.dao.GroupMemberDao
import com.splitter.splittr.data.local.dao.PaymentDao
import com.splitter.splittr.data.local.dao.PaymentSplitDao
import com.splitter.splittr.data.local.dao.SyncMetadataDao
import com.splitter.splittr.data.local.dao.TransactionDao
import com.splitter.splittr.data.local.entities.PaymentEntity
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.data.model.Payment
import com.splitter.splittr.data.model.PaymentSplit
import com.splitter.splittr.data.model.Transaction
import com.splitter.splittr.data.model.TransactionAmount
import com.splitter.splittr.data.sync.SyncableRepository
import com.splitter.splittr.data.sync.managers.PaymentSyncManager
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.DateUtils
import com.splitter.splittr.utils.NetworkUtils
import com.splitter.splittr.utils.NetworkUtils.isOnline
import com.splitter.splittr.utils.ServerIdUtil
import com.splitter.splittr.utils.ServerIdUtil.getLocalId
import com.splitter.splittr.utils.ServerIdUtil.getServerId
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

    val myApplication = context.applicationContext as MyApplication

    private val _payments = MutableStateFlow<List<Payment>>(emptyList())
    val payments: StateFlow<List<Payment>> = _payments.asStateFlow()

    fun getPaymentById(paymentId: Int): Flow<Payment?> =
        paymentDao.getPaymentById(paymentId).map { it?.toModel() }

    fun getPaymentByTransactionId(transactionId: String): Flow<Payment?> =
        paymentDao.getPaymentByTransactionId(transactionId).map { it?.toModel() }

    fun getPaymentsByGroup(groupId: Int): Flow<List<PaymentEntity>> = flow {
        Log.d(TAG, "Getting payments for group $groupId")
        // Emit local data first
        val localPayments = paymentDao.getPaymentsByGroup(groupId).first()
        emit(localPayments)

        // Then try to fetch from API and update local database
        if (isOnline()) {
            try {
                val serverGroupId = getServerId(groupId, "groups", context) ?: return@flow
                Log.d(TAG, "Fetching payments from server for group ID: $serverGroupId")

                // Fetch payments
                val apiPayments = apiService.getPaymentsByGroup(serverGroupId)

                // Process in a transaction to maintain consistency
                paymentDao.runInTransaction {
                    apiPayments.forEach { serverPayment ->
                        // Convert and save payment
                        val paymentEntity = myApplication.entityServerConverter
                            .convertPaymentFromServer(serverPayment)
                            .getOrNull() ?: return@forEach

                        paymentDao.insertOrUpdatePayment(paymentEntity)

                        // Fetch and save splits for this payment
                        val serverSplits = apiService.getPaymentSplitsByPayment(serverPayment.id)
                        serverSplits.forEach { serverSplit ->
                            val splitEntity = myApplication.entityServerConverter
                                .convertPaymentSplitFromServer(serverSplit)
                                .getOrNull() ?: return@forEach

                            paymentSplitDao.insertOrUpdatePaymentSplit(splitEntity)
                        }
                    }
                }

                // Emit updated data if changed
                val updatedPayments = paymentDao.getPaymentsByGroup(groupId).first()
                if (updatedPayments != localPayments) {
                    emit(updatedPayments)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching or processing payments from API", e)
            }
        }
    }.flowOn(dispatchers.io)

    suspend fun createPayment(payment: Payment): Result<Payment> = withContext(dispatchers.io) {
        try {
            // First save locally with PENDING_SYNC status
            val localPayment = payment.toEntity(SyncStatus.PENDING_SYNC)
            val localId = paymentDao.insertPayment(localPayment)

            // Attempt to sync with server if online
            if (isOnline()) {
                try {
                    // Pass the saved local entity to the converter
                    val serverModel = myApplication.entityServerConverter
                        .convertPaymentToServer(localPayment)
                        .getOrElse { return@withContext Result.failure(it) }

                    val serverPayment = apiService.createPayment(serverModel)

                    // Update local payment with server data while preserving local ID
                    val syncedPayment = serverPayment.copy(
                        id = localId.toInt(),
                        updatedAt = DateUtils.getCurrentTimestamp()
                    ).toEntity(SyncStatus.SYNCED)
                    paymentDao.updatePayment(syncedPayment)

                    Result.success(syncedPayment.toModel())
                } catch (e: Exception) {
                    Log.e("PaymentRepository", "Failed to sync payment with server", e)
                    // Return the local payment even if server sync fails
                    Result.success(localPayment.toModel())
                }
            } else {
                // When offline, return the local payment
                Result.success(localPayment.toModel())
            }
        } catch (e: Exception) {
            Log.e("PaymentRepository", "Failed to create payment", e)
            Result.failure(e)
        }
    }

    suspend fun updatePayment(payment: Payment): Result<Payment> = withContext(dispatchers.io) {
        try {
            // First get existing payment to preserve any local-only data
            val existingPayment = paymentDao.getPaymentById(payment.id).first()

            // Update locally with PENDING_SYNC status
            val localPayment = payment.copy(
                updatedAt = DateUtils.getCurrentTimestamp()
            ).toEntity(SyncStatus.PENDING_SYNC)

            // Preserve any local-only fields from existing payment
            val updatedLocalPayment = existingPayment?.let { existing ->
                localPayment.copy(serverId = existing.serverId) } ?: localPayment

            paymentDao.updatePayment(updatedLocalPayment)

            // Attempt to sync with server if online
            if (isOnline()) {
                try {
                    val serverPayment = apiService.updatePayment(payment.id, payment)

                    // Merge server response with local data
                    val syncedPayment = serverPayment.copy(
                        id = payment.id,
                        updatedAt = DateUtils.getCurrentTimestamp()).toEntity(SyncStatus.SYNCED)

                    paymentDao.updatePayment(syncedPayment)
                    Result.success(syncedPayment.toModel())
                } catch (e: Exception) {
                    Log.e("PaymentRepository", "Failed to sync payment update with server", e)
                    // Return the local payment even if server sync fails
                    Result.success(updatedLocalPayment.toModel())
                }
            } else {
                // When offline, return the local payment
                Result.success(updatedLocalPayment.toModel())
            }
        } catch (e: Exception) {
            Log.e("PaymentRepository", "Failed to update payment", e)
            Result.failure(e)
        }
    }

    suspend fun createPaymentFromTransaction(
        transaction: Transaction,
        payment: Payment,
        splits: List<PaymentSplit>
    ): Result<Payment> = withContext(dispatchers.io) {
        try {
            val userId = getUserIdFromPreferences(context)
                ?: return@withContext Result.failure(IllegalStateException("User ID not found"))

            // Create a complete transaction object with all required fields
            val completeTransaction = transaction.copy(
                userId = userId,
                transactionAmount = TransactionAmount(
                    amount = transaction.getEffectiveAmount(),
                    currency = transaction.getEffectiveCurrency()
                )
            )

            Log.d(TAG, "Creating transaction with userId: $userId")

            // Convert to server model
            val serverTransaction = myApplication.entityServerConverter
                .convertTransactionToServer(completeTransaction.toEntity(SyncStatus.PENDING_SYNC))
                .getOrNull() ?: throw Exception("Failed to convert transaction to server model")

            // First save transaction locally
            transactionDao.insertTransaction(completeTransaction.toEntity(SyncStatus.PENDING_SYNC))

            // Then create transaction on server
            val createdServerTransaction = try {
                apiService.createTransaction(serverTransaction)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create transaction on server", e)
                transactionDao.updateTransactionSyncStatus(
                    completeTransaction.transactionId,
                    SyncStatus.SYNC_FAILED
                )
                throw e
            }

            // Update local transaction with server response and SYNCED status
            transactionDao.insertTransaction(createdServerTransaction.toEntity(SyncStatus.SYNCED))

            delay(100)

            // Create the payment using existing function with the confirmed transaction ID
            createPaymentWithSplits(
                payment = payment.copy(
                    transactionId = createdServerTransaction.transactionId,
                    paidByUserId = userId,
                    currency = createdServerTransaction.transactionAmount?.currency
                        ?: createdServerTransaction.currency
                        ?: "GBP"
                ),
                splits = splits
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating payment from transaction", e)
            Result.failure(e)
        }
    }

    suspend fun createPaymentWithSplits(
        payment: Payment,
        splits: List<PaymentSplit>
    ): Result<Payment> = withContext(dispatchers.io) {
        Log.d(TAG, "Starting createPaymentWithSplits")
        Log.d(TAG, "Payment: $payment")
        Log.d(TAG, "Splits: ${splits.map { "Split(id=${it.id}, paymentId=${it.paymentId}, userId=${it.userId}, amount=${it.amount})" }}")

        try {
            // Validate membership first
            val member = groupMemberDao.getGroupMemberByUserId(payment.paidByUserId).first()
            Log.d(TAG, "Member check result: ${member != null}, removedAt: ${member?.removedAt}")

            if (member == null || member.removedAt != null) {
                return@withContext Result.failure(IllegalStateException("User is not a member of this group"))
            }

            val currentTime = DateUtils.getCurrentTimestamp()

            // Step 1: Create local payment first
            val localPayment = payment.toEntity(SyncStatus.PENDING_SYNC)
            val paymentId = paymentDao.insertPayment(localPayment)
            Log.d(TAG, "Local payment created with ID: $paymentId")

            // Step 2: Create all splits using the new payment ID
            val localSplits = splits.map { split ->
                split.copy(
                    id = 0,
                    paymentId = paymentId.toInt()
                ).toEntity(SyncStatus.PENDING_SYNC)
            }

            // Step 3: Insert splits and update group timestamp in a transaction
            val createdPayment = paymentDao.runInTransaction {
                try {
                    localSplits.forEach { splitEntity ->
                        val splitId = paymentSplitDao.insertPaymentSplit(splitEntity)
                        Log.d(TAG, "Local split created with ID: $splitId")
                    }

                    // Update group timestamp
                    groupDao.updateGroupTimestamp(payment.groupId, currentTime)
                    Log.d(TAG, "Updated group ${payment.groupId} timestamp")

                    // Return the created payment with its generated ID
                    localPayment.copy(id = paymentId.toInt())
                } catch (e: Exception) {
                    Log.e(TAG, "Local transaction failed", e)
                    throw e
                }
            }

            // Step 4: Try to sync with server if online
            if (isOnline()) {
                try {
                    Log.d(TAG, "Attempting server sync")

                    // Convert to server model first
                    val paymentServerModel = myApplication.entityServerConverter
                        .convertPaymentToServer(createdPayment)
                        .getOrElse {
                            Log.e(TAG, "Failed to convert payment to server model", it)
                            throw it
                        }
                    Log.d(TAG, "Converted to server model: ${paymentServerModel}")

                    // Create payment on server with converted IDs
                    val serverPayment = apiService.createPayment(paymentServerModel)
                    Log.d(TAG, "Server payment created successfully: id=${serverPayment.id}")

                    // Convert splits to server models
                    val serverSplits = localSplits.map { split ->
                        Log.d(TAG, "Converting split to server model: userId=${split.userId}")
                        val splitServerModel = myApplication.entityServerConverter
                            .convertPaymentSplitToServer(split)
                            .getOrElse {
                                Log.e(TAG, "Failed to convert split to server model", it)
                                throw it
                            }
                        Log.d(TAG, "Creating server split with converted IDs")
                        val serverSplit = apiService.createPaymentSplit(serverPayment.id, splitServerModel)
                        Log.d(TAG, "Server split created: id=${serverSplit.id}")
                        serverSplit
                    }

                    // Update local records with server IDs
                    paymentDao.runInTransaction {
                        try {
                            // Convert server payment back to local entity
                            val updatedPayment = myApplication.entityServerConverter.convertPaymentFromServer(serverPayment, createdPayment)
                                .getOrElse {
                                    Log.e(TAG, "Failed to convert server payment back to local entity", it)
                                    throw it
                                }
                            paymentDao.updatePayment(updatedPayment)

                            // Update all splits with server IDs
                            serverSplits.forEach { serverSplit ->
                                val existingSplit = localSplits.find { it.paymentId == updatedPayment.id }
                                val updatedSplit = myApplication.entityServerConverter.convertPaymentSplitFromServer(serverSplit, existingSplit)
                                    .getOrElse {
                                        Log.e(TAG, "Failed to convert server split back to local entity", it)
                                        throw it
                                    }
                                paymentSplitDao.updatePaymentSplit(updatedSplit)
                            }

                            Log.d(TAG, "Local records updated with server IDs")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to update local records with server IDs", e)
                            // Don't throw - we still have valid local data
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Server sync failed", e)
                    // Don't throw - we still have valid local data
                }
            }

            // Always return the local result
            Result.success(createdPayment.toModel())
        } catch (e: Exception) {
            Log.e(TAG, "Operation failed", e)
            Result.failure(e)
        }
    }

//    suspend fun createPaymentWithSplits(
//        payment: Payment,
//        splits: List<PaymentSplit>
//    ): Result<Payment> = withContext(dispatchers.io) {
//        Log.d(TAG, "Starting createPaymentWithSplits")
//        Log.d(TAG, "Payment: $payment")
//        Log.d(TAG, "Splits: ${splits.map { "Split(id=${it.id}, paymentId=${it.paymentId}, userId=${it.userId}, amount=${it.amount})" }}")
//
//        val idGenerator = IDGenerator.getInstance(context)
//
//        try {
//            val member = groupMemberDao.getGroupMemberByUserId(payment.paidByUserId).first()
//            Log.d(TAG, "Member check result: ${member != null}, removedAt: ${member?.removedAt}")
//
//            if (member == null || member.removedAt != null) {
//                return@withContext Result.failure(IllegalStateException("User is not a member of this group"))
//            }
//
//            val currentTime = DateUtils.getCurrentTimestamp()
//
//            if (isOnline()) {
//                Log.d(TAG, "Device is online, creating payment on server")
//                try {
//                    // Create payment on server and handle locally
//                    val serverPayment = apiService.createPayment(payment)
//                    Log.d(TAG, "Server payment created successfully: id=${serverPayment.id}")
//
//                    // Create splits on server
//                    val serverSplits = splits.map { split ->
//                        Log.d(TAG, "Creating server split: userId=${split.userId}, amount=${split.amount}")
//                        val splitWithCorrectPaymentId = split.copy(paymentId = serverPayment.id)
//                        val serverSplit = apiService.createPaymentSplit(serverPayment.id, splitWithCorrectPaymentId)
//                        Log.d(TAG, "Server split created: id=${serverSplit.id}, paymentId=${serverSplit.paymentId}")
//                        serverSplit
//                    }
//
//                    // Save to local DB
//                    paymentDao.runInTransaction {
//                        try {
//                            // Save payment
//                            val localPayment = serverPayment.toEntity(SyncStatus.SYNCED)
//                            Log.d(TAG, "Inserting local payment: id=${localPayment.id}, serverId=${localPayment.serverId}")
//                            paymentDao.insertPayment(localPayment)
//
//                            // Save splits
//                            serverSplits.forEach { serverSplit ->
//                                val localSplit = serverSplit.toEntity(SyncStatus.SYNCED)
//                                Log.d(TAG, "Attempting to insert local split: " +
//                                        "id=${localSplit.id}, " +
//                                        "serverId=${localSplit.serverId}, " +
//                                        "paymentId=${localSplit.paymentId}, " +
//                                        "userId=${localSplit.userId}, " +
//                                        "amount=${localSplit.amount}")
//
//                                try {
//                                    val splitId = paymentSplitDao.insertPaymentSplit(localSplit)
//                                    Log.d(TAG, "Successfully inserted split with returned id: $splitId")
//                                } catch (e: Exception) {
//                                    Log.e(TAG, "Error inserting split: ${e.message}")
//                                    Log.e(TAG, "Split that failed: $localSplit", e)
//                                    throw e // Re-throw to trigger transaction rollback
//                                }
//                            }
//
//                            Log.d(TAG, "All splits inserted successfully")
//
//                            // Update group timestamp
//                            groupDao.updateGroupTimestamp(payment.groupId, currentTime)
//                            Log.d(TAG, "Updated group ${payment.groupId} timestamp")
//                        } catch (e: Exception) {
//                            Log.e(TAG, "Transaction failed", e)
//                            throw e
//                        }
//                    }
//
//                    Log.d(TAG, "Entire operation completed successfully")
//                    return@withContext Result.success(serverPayment)
//                } catch (e: Exception) {
//                    Log.e(TAG, "Error during server operation", e)
//                    throw e
//                }
//            } else {
//                Log.d(TAG, "Device is offline, creating local-only records")
//
//                // Generate local payment ID
//                idGenerator.nextId().fold(
//                    onSuccess = { localPaymentId ->
//                        Log.d(TAG, "Generated local payment ID: $localPaymentId")
//
//                        val localPayment = payment.copy(id = localPaymentId).toEntity(SyncStatus.PENDING_SYNC)
//
//                        // Generate local split IDs and create entities
//                        val localSplitsResults = splits.map { split ->
//                            idGenerator.nextId().map { localSplitId ->
//                                Log.d(TAG, "Generated local split ID: $localSplitId for payment: $localPaymentId")
//                                split.copy(
//                                    id = localSplitId,
//                                    paymentId = localPaymentId
//                                ).toEntity(SyncStatus.PENDING_SYNC)
//                            }
//                        }
//
//                        // Check if all split IDs were generated successfully
//                        val allSplitsSuccess = localSplitsResults.all { it.isSuccess }
//                        if (!allSplitsSuccess) {
//                            val error = localSplitsResults.first { it.isFailure }.exceptionOrNull()
//                                ?: Exception("Failed to generate split IDs")
//                            return@withContext Result.failure(error)
//                        }
//
//                        val localSplits = localSplitsResults.map { it.getOrNull()!! }
//
//                        paymentDao.runInTransaction {
//                            try {
//                                paymentDao.insertPayment(localPayment)
//                                Log.d(TAG, "Local payment inserted: $localPaymentId")
//
//                                localSplits.forEach { splitEntity ->
//                                    try {
//                                        val splitId = paymentSplitDao.insertPaymentSplit(splitEntity)
//                                        Log.d(TAG, "Local split inserted: original=${splitEntity.id}, returned=$splitId")
//                                    } catch (e: Exception) {
//                                        Log.e(TAG, "Error inserting local split", e)
//                                        Log.e(TAG, "Failed split entity: $splitEntity")
//                                        throw e
//                                    }
//                                }
//
//                                groupDao.updateGroupTimestamp(payment.groupId, currentTime)
//                                Log.d(TAG, "Local operations completed successfully")
//
//                                Result.success(localPayment.toModel())
//                            } catch (e: Exception) {
//                                Log.e(TAG, "Local transaction failed", e)
//                                throw e
//                            }
//                        }
//                    },
//                    onFailure = { error ->
//                        Log.e(TAG, "Failed to generate payment ID", error)
//                        Result.failure(error)
//                    }
//                )
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "Operation failed", e)
//            Result.failure(e)
//        }
//    }

//    // Helper function for local-only creation
//    private suspend fun createLocalPaymentWithSplits(
//        payment: Payment,
//        splits: List<PaymentSplit>,
//        timestamp: String
//    ): Result<Payment> {
//        try {
//            // Generate local IDs
//            val localPaymentId = LocalIdGenerator.nextId()
//            val localPayment = payment.copy(id = localPaymentId).toEntity(SyncStatus.PENDING_SYNC)
//
//            // Create local splits with correct payment ID reference
//            val localSplits = splits.map { split ->
//                split.copy(
//                    id = LocalIdGenerator.nextId(),
//                    paymentId = localPaymentId
//                ).toEntity(SyncStatus.PENDING_SYNC)
//            }
//
//            // Save everything in a single transaction
//            paymentDao.runInTransaction {
//                // Save payment
//                paymentDao.insertPayment(localPayment)
//                Log.d(TAG, "Inserted payment locally with id: $localPaymentId")
//
//                // Save splits
//                localSplits.forEach { splitEntity ->
//                    paymentSplitDao.insertPaymentSplit(splitEntity)
//                    Log.d(TAG, "Inserted split locally with id: ${splitEntity.id}")
//                }
//
//                // Update group timestamp
//                groupDao.updateGroupTimestamp(payment.groupId, timestamp)
//                Log.d(TAG, "Updated group ${payment.groupId} timestamp to $timestamp")
//            }
//
//            return Result.success(localPayment.toModel())
//        } catch (e: Exception) {
//            Log.e(TAG, "Error creating local payment", e)
//            return Result.failure(e)
//        }
//    }

    suspend fun updatePaymentWithSplits(payment: Payment, splits: List<PaymentSplit>): Result<Payment> =
        withContext(dispatchers.io) {
            try {
                // Update payment with PENDING_SYNC status
                paymentDao.updatePayment(payment.toEntity(SyncStatus.PENDING_SYNC))
                paymentDao.updatePaymentSyncStatus(payment.id, SyncStatus.PENDING_SYNC)

                // Update group's updatedAt timestamp
                val currentTime = DateUtils.getCurrentTimestamp()
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
            if (isOnline()) {
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

            if (isOnline()) {
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
            // Archive locally first
            val timestamp = DateUtils.getCurrentTimestamp()
            paymentDao.archivePayment(paymentId, timestamp)

            // Also update the payment's sync status
            paymentDao.updatePaymentSyncStatus(paymentId, SyncStatus.PENDING_SYNC)

            // Attempt to sync with server if online
            if (isOnline()) {
                try {
                    apiService.archivePayment(paymentId)

                    // Update sync status to indicate successful sync
                    paymentDao.updatePaymentSyncStatus(paymentId, SyncStatus.SYNCED)
                } catch (e: Exception) {
                    Log.e("PaymentRepository", "Failed to sync payment archive with server", e)
                    // Don't fail the operation if server sync fails
                    // The background sync can handle it later
                }
            }

            // Return success regardless of server sync status
            // since we've successfully archived locally
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("PaymentRepository", "Failed to archive payment", e)
            // Only return failure if local archiving failed
            Result.failure(e)
        }
    }

    suspend fun restorePayment(paymentId: Int): Result<Unit> = withContext(dispatchers.io) {
        try {
            // Restore locally first
            paymentDao.restorePayment(paymentId)
            paymentDao.updatePaymentSyncStatus(paymentId, SyncStatus.PENDING_SYNC)

            // Attempt server sync if online
            if (NetworkUtils.isOnline()) {
                try {
                    apiService.restorePayment(paymentId)
                    paymentDao.updatePaymentSyncStatus(paymentId, SyncStatus.SYNCED)
                } catch (e: Exception) {
                    Log.e("PaymentRepository", "Failed to sync payment restoration", e)
                    // Continue since local restore succeeded
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("PaymentRepository", "Failed to restore payment", e)
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