package com.helgolabs.trego.data.repositories

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.calculators.SplitCalculator
import com.helgolabs.trego.data.extensions.toEntity
import com.helgolabs.trego.data.extensions.toModel
import com.helgolabs.trego.data.local.dao.CurrencyConversionDao
import com.helgolabs.trego.data.local.dao.GroupDao
import com.helgolabs.trego.data.local.dao.GroupMemberDao
import com.helgolabs.trego.data.local.dao.PaymentDao
import com.helgolabs.trego.data.local.dao.PaymentSplitDao
import com.helgolabs.trego.data.local.dao.SyncMetadataDao
import com.helgolabs.trego.data.local.dao.TransactionDao
import com.helgolabs.trego.data.local.dataClasses.BatchConversionResult
import com.helgolabs.trego.data.local.dataClasses.ConversionAttempt
import com.helgolabs.trego.data.local.dataClasses.CurrencyConversionResult
import com.helgolabs.trego.data.local.dataClasses.PaymentEntityWithSplits
import com.helgolabs.trego.data.local.entities.CurrencyConversionEntity
import com.helgolabs.trego.data.local.entities.PaymentEntity
import com.helgolabs.trego.data.local.entities.PaymentSplitEntity
import com.helgolabs.trego.data.managers.CurrencyConversionManager
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.data.model.Payment
import com.helgolabs.trego.data.model.PaymentSplit
import com.helgolabs.trego.data.model.Transaction
import com.helgolabs.trego.data.model.TransactionAmount
import com.helgolabs.trego.data.network.ExchangeRateService
import com.helgolabs.trego.data.sync.SyncableRepository
import com.helgolabs.trego.data.sync.managers.CurrencyConversionSyncManager
import com.helgolabs.trego.data.sync.managers.PaymentSyncManager
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.NetworkUtils
import com.helgolabs.trego.utils.NetworkUtils.isOnline
import com.helgolabs.trego.utils.ServerIdUtil
import com.helgolabs.trego.utils.ServerIdUtil.getLocalId
import com.helgolabs.trego.utils.ServerIdUtil.getServerId
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.coroutineScope
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

class PaymentRepository(
    private val paymentDao: PaymentDao,
    private val paymentSplitDao: PaymentSplitDao,
    private val groupDao: GroupDao,
    private val groupMemberDao: GroupMemberDao,
    private val transactionDao: TransactionDao,
    private val currencyConversionDao: CurrencyConversionDao,
    private val apiService: ApiService,
    private val dispatchers: CoroutineDispatchers,
    private val context: Context,
    private val syncMetadataDao: SyncMetadataDao,
    private val paymentSyncManager: PaymentSyncManager,
    private val currencyConversionSyncManager: CurrencyConversionSyncManager,
    private val splitCalculator: SplitCalculator
) : SyncableRepository {

    override val entityType = "payments"
    override val syncPriority = 4

    val myApplication = context.applicationContext as MyApplication

    val currencyConversionManager = myApplication.syncManagerProvider.currencyConversionManager

    private val _payments = MutableStateFlow<List<PaymentEntity>>(emptyList())
    val payments: StateFlow<List<PaymentEntity>> = _payments.asStateFlow()

    // Helper function to calculate final amount
    private fun calculateFinalAmount(amount: Double, paymentType: String): Double {
        val absAmount = abs(amount)
        return if (paymentType == "received") absAmount else -absAmount
    }

    // Helper function to calculate split amounts
    private fun calculateSplitAmount(amount: Double, paymentType: String): Double {
        val absAmount = abs(amount)
        return if (paymentType == "received") absAmount else -absAmount
    }

    fun getPaymentById(paymentId: Int): Flow<PaymentEntity?> = flow {
        Log.d(TAG, "Getting payment with ID: $paymentId")

        try {
            // First try local ID
            paymentDao.getPaymentById(paymentId).collect { entity ->
                if (entity != null) {
                    Log.d(TAG, "Found payment by local ID: $entity")
                    emit(entity)
                    return@collect
                }

                // If not found by local ID, try server ID
                val entityByServerId = paymentDao.getPaymentByServerId(paymentId)
                if (entityByServerId != null) {
                    Log.d(TAG, "Found payment by server ID: $entityByServerId")
                    emit(entityByServerId)
                } else {
                    emit(null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting payment", e)
            emit(null)
        }
    }.flowOn(dispatchers.io)

    fun getPaymentByTransactionId(transactionId: String): Flow<Payment?> =
        paymentDao.getPaymentByTransactionId(transactionId).map { it?.toModel() }

    fun getPaymentsByGroup(groupId: Int): Flow<List<PaymentEntity>> = flow {
        Log.d(TAG, "Getting payments for group $groupId")

        // Emit local data first
        val localPayments = paymentDao.getPaymentsByGroup(groupId).first()
        emit(localPayments)

        // Create a map of local payments by server ID for quick lookup
        val localPaymentMap = localPayments.associateBy { it.serverId }

        if (isOnline()) {
            try {
                val serverGroupId = getServerId(groupId, "groups", context) ?: return@flow
                Log.d(TAG, "Fetching payments from server for group ID: $serverGroupId")

                val apiPayments = apiService.getPaymentsByGroup(serverGroupId)
                var hasChanges = false

                paymentDao.runInTransaction {
                    apiPayments.forEach { serverPayment ->
                        val localPayment = localPaymentMap[serverPayment.id]

                        // Check if we need to update this payment
                        if (shouldUpdatePayment(localPayment, serverPayment)) {
                            val paymentEntity = myApplication.entityServerConverter
                                .convertPaymentFromServer(serverPayment)
                                .getOrNull() ?: return@forEach

                            paymentDao.insertOrUpdatePayment(paymentEntity)
                            hasChanges = true

                            // Only fetch and update splits if the payment was updated
                            val serverSplits = apiService.getPaymentSplitsByPayment(serverPayment.id)
                            val localSplits = paymentSplitDao.getPaymentSplitsByPayment(paymentEntity.id).first()
                            val localSplitsMap = localSplits.associateBy { it.serverId }

                            serverSplits.forEach { serverSplit ->
                                val localSplit = localSplitsMap[serverSplit.id]
                                if (shouldUpdateSplit(localSplit, serverSplit)) {
                                    val splitEntity = myApplication.entityServerConverter
                                        .convertPaymentSplitFromServer(serverSplit)
                                        .getOrNull() ?: return@forEach

                                    paymentSplitDao.insertOrUpdatePaymentSplit(splitEntity)
                                    hasChanges = true
                                }
                            }
                        }
                    }
                }

                // Only emit if there were actual changes
                if (hasChanges) {
                    val updatedPayments = paymentDao.getPaymentsByGroup(groupId).first()
                    emit(updatedPayments)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching or processing payments from API", e)
            }
        }
    }.flowOn(dispatchers.io)

    private fun shouldUpdatePayment(localPayment: PaymentEntity?, serverPayment: Payment): Boolean {
        if (localPayment == null) return true  // New payment

        return DateUtils.isUpdateNeeded(
            serverTimestamp = serverPayment.updatedAt,
            localTimestamp = localPayment.updatedAt,
            entityId = "Payment-${serverPayment.id}"
        )
    }

    private fun shouldUpdateSplit(localSplit: PaymentSplitEntity?, serverSplit: PaymentSplit): Boolean {
        if (localSplit == null) return true  // New split

        return DateUtils.isUpdateNeeded(
            serverTimestamp = serverSplit.updatedAt,
            localTimestamp = localSplit.updatedAt,
            entityId = "PaymentSplit-${serverSplit.id}"
        )
    }

    suspend fun createPayment(payment: PaymentEntity): Result<PaymentEntity> = withContext(dispatchers.io) {
        try {
            // First save locally with PENDING_SYNC status
            val localPayment = payment.copy(syncStatus = SyncStatus.PENDING_SYNC)
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

                    Result.success(syncedPayment)
                } catch (e: Exception) {
                    Log.e("PaymentRepository", "Failed to sync payment with server", e)
                    // Return the local payment even if server sync fails
                    Result.success(localPayment)
                }
            } else {
                // When offline, return the local payment
                Result.success(localPayment)
            }
        } catch (e: Exception) {
            Log.e("PaymentRepository", "Failed to create payment", e)
            Result.failure(e)
        }
    }

    suspend fun updatePayment(payment: PaymentEntity): Result<PaymentEntity> = withContext(dispatchers.io) {
        try {
            // First get existing payment to preserve any local-only data
            val existingPayment = paymentDao.getPaymentById(payment.id).first()

            // Update locally with PENDING_SYNC status
            val localPayment = payment.copy(
                updatedAt = DateUtils.getCurrentTimestamp(),
                syncStatus = SyncStatus.PENDING_SYNC
            )

            // Preserve any local-only fields from existing payment
            val updatedLocalPayment = existingPayment?.let { existing ->
                localPayment.copy(serverId = existing.serverId) } ?: localPayment

            paymentDao.updatePayment(updatedLocalPayment)

            // Attempt to sync with server if online
            if (isOnline()) {
                try {
                    val serverPaymentModel = payment.toModel()
                    val serverPayment = apiService.updatePayment(serverPaymentModel.id, serverPaymentModel)

                    // Merge server response with local data
                    val syncedPayment = serverPayment.copy(
                        id = payment.id,
                        updatedAt = DateUtils.getCurrentTimestamp()).toEntity(SyncStatus.SYNCED)

                    paymentDao.updatePayment(syncedPayment)
                    Result.success(syncedPayment)
                } catch (e: Exception) {
                    Log.e("PaymentRepository", "Failed to sync payment update with server", e)
                    // Return the local payment even if server sync fails
                    Result.success(updatedLocalPayment)
                }
            } else {
                // When offline, return the local payment
                Result.success(updatedLocalPayment)
            }
        } catch (e: Exception) {
            Log.e("PaymentRepository", "Failed to update payment", e)
            Result.failure(e)
        }
    }

    suspend fun createPaymentFromTransaction(
        transaction: Transaction,
        payment: PaymentEntity,
        splits: List<PaymentSplitEntity>
    ): Result<PaymentEntity> = withContext(dispatchers.io) {
        try {
            val userId = getUserIdFromPreferences(context)
                ?: return@withContext Result.failure(IllegalStateException("User ID not found"))

            // Determine payment type based on transaction amount
            val effectiveAmount = transaction.getEffectiveAmount()
            val adjustedPaymentType = if (effectiveAmount > 0) "received" else payment.paymentType

            // Create a complete transaction object with all required fields
            val completeTransaction = transaction.copy(
                userId = userId,
                transactionAmount = TransactionAmount(
                    amount = transaction.getEffectiveAmount(),
                    currency = transaction.getEffectiveCurrency()
                )
            )

            Log.d(TAG, "Creating transaction with userId: $userId")

            // Always save locally first with PENDING_SYNC status
            val localTransaction = completeTransaction.toEntity(SyncStatus.PENDING_SYNC)
            transactionDao.insertTransaction(localTransaction)

            // If online, attempt server sync
            if (NetworkUtils.isOnline()) {
                try {
                    // Convert to server model
                    val serverTransaction = myApplication.entityServerConverter
                        .convertTransactionToServer(localTransaction)
                        .getOrNull() ?: throw Exception("Failed to convert transaction to server model")

                    // Create transaction on server
                    val createdServerTransaction = apiService.createTransaction(serverTransaction)

                    // Update local transaction with server response and SYNCED status
                    transactionDao.insertTransaction(createdServerTransaction.toEntity(SyncStatus.SYNCED))

                    delay(100) // Keep the existing delay for API rate limiting

                    // Create payment with the server transaction ID
                    return@withContext createPaymentWithSplits(
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
                    Log.e(TAG, "Server sync failed, proceeding with local transaction", e)
                    // Fall through to offline handling
                }
            }

            // Offline handling or if server sync failed
            Log.d(TAG, "Device offline or sync failed, creating local payment")

            // Calculate the final amount based on payment type
            val finalAmount = calculateFinalAmount(payment.amount, payment.paymentType)

            // Adjust the payment with the calculated amount
            val adjustedPayment = payment.copy(
                amount = finalAmount,
                paymentType = adjustedPaymentType,
                transactionId = transaction.transactionId,
                paidByUserId = userId,
                currency = transaction.getEffectiveCurrency()
            )

            // Adjust the splits based on payment type
            val adjustedSplits = splits.map { split ->
                split.copy(amount = calculateSplitAmount(split.amount, adjustedPaymentType))
            }

            return@withContext createPaymentWithSplits(adjustedPayment, adjustedSplits).also { result ->
                result.getOrNull()?.id?.let { paymentId ->
                    // Update sync status to PENDING_SYNC since we're offline
                    paymentDao.updatePaymentSyncStatus(paymentId, SyncStatus.PENDING_SYNC)
                    paymentSplitDao.updatePaymentSplitsSyncStatus(paymentId, SyncStatus.PENDING_SYNC)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating payment from transaction", e)
            Result.failure(e)
        }
    }


    suspend fun createPaymentWithSplits(
        payment: PaymentEntity,
        splits: List<PaymentSplitEntity>
    ): Result<PaymentEntity> = withContext(dispatchers.io) {
        Log.d(TAG, "Starting createPaymentWithSplits")
        Log.d(TAG, "Payment details - id: ${payment.id}, groupId: ${payment.groupId}, amount: ${payment.amount}")
        Log.d(TAG, "Incoming splits: ${splits.size}")

        try {
            // Validate memberships (existing validation code remains the same)
            val allUserIds = splits.map { it.userId }.toSet() + setOf(payment.paidByUserId)
            val memberships = groupMemberDao.getGroupMembersByUserIds(payment.groupId, allUserIds.toList()).first()

            val invalidMembers = allUserIds.filter { userId ->
                val membership = memberships.find { it.userId == userId }
                membership == null || membership.removedAt != null
            }

            if (invalidMembers.isNotEmpty()) {
                return@withContext Result.failure(IllegalStateException("Some users are not valid members of this group"))
            }

            val currentTime = DateUtils.getCurrentTimestamp()

            // Calculate the final amount based on payment type
            val finalAmount = calculateFinalAmount(payment.amount, payment.paymentType)

            // Adjust the splits based on payment type
            val adjustedSplits = splits.map { split ->
                split.copy(amount = calculateSplitAmount(split.amount, payment.paymentType))
            }

            // Create payment with adjusted amount
            val paymentToCreate = payment.copy(amount = finalAmount)

            // Create initial local payment with PENDING_SYNC status
            val localPayment = paymentToCreate.copy(syncStatus = SyncStatus.PENDING_SYNC)
            val localPaymentId = paymentDao.insertPayment(localPayment)
            Log.d(TAG, "Local payment created with ID: $localPaymentId")

            // Create initial local splits with PENDING_SYNC status
            val initialSplitEntities = adjustedSplits.map { split ->
                split.copy(
                    id = 0,
                    paymentId = localPaymentId.toInt()
                ).copy(syncStatus = SyncStatus.PENDING_SYNC)
            }

            // Insert local splits and capture their IDs
            val splitsWithLocalIds = paymentDao.runInTransaction {
                initialSplitEntities.map { splitEntity ->
                    val splitId = paymentSplitDao.insertPaymentSplit(splitEntity).toInt()
                    Log.d(TAG, "Created local split with ID: $splitId")
                    splitEntity.copy(id = splitId)
                }.also {
                    groupDao.updateGroupTimestamp(payment.groupId, currentTime)
                }
            }

            val createdPayment = localPayment.copy(id = localPaymentId.toInt())

            // If online, sync with server
            if (isOnline()) {
                try {
                    // Convert to server model
                    val paymentServerModel = myApplication.entityServerConverter
                        .convertPaymentToServer(createdPayment)
                        .getOrThrow()

                    // Create payment on server
                    val serverPayment = apiService.createPayment(paymentServerModel)
                    Log.d(TAG, "Server payment created successfully: id=${serverPayment.id}")

                    // Convert server payment back to local entity
                    val updatedPaymentResult = myApplication.entityServerConverter
                        .convertPaymentFromServer(serverPayment)
                        .getOrThrow()
                        .copy(
                            id = localPaymentId.toInt(),  // Preserve our local ID
                            syncStatus = SyncStatus.SYNCED
                        )

                    // Update the payment with server ID
                    paymentDao.runInTransaction {
                        paymentDao.updatePaymentDirect(updatedPaymentResult)
                        Log.d(TAG, "Updated local payment $localPaymentId with server ID ${serverPayment.id}")
                    }

                    // Now create the splits on server using the updated payment
                    splitsWithLocalIds.map { localSplit ->
                        try {
                            val localSplitId = localSplit.id
                            Log.d(TAG, "Processing split with local ID: $localSplitId")

                            val splitServerModel = myApplication.entityServerConverter
                                .convertPaymentSplitToServer(localSplit)
                                .getOrThrow()

                            val serverSplit = apiService.createPaymentSplit(serverPayment.id, splitServerModel)
                            Log.d(TAG, "Created server split: ${serverSplit.id} for local split: $localSplitId")

                            // Convert back to local entity, preserving the local ID
                            val updatedSplit = myApplication.entityServerConverter
                                .convertPaymentSplitFromServer(serverSplit)
                                .getOrThrow()
                                .copy(
                                    id = localSplitId,  // Preserve the original local ID
                                    paymentId = localPaymentId.toInt(),
                                    syncStatus = SyncStatus.SYNCED,
                                    serverId = serverSplit.id  // Make sure to set the server ID
                                )

                            // Update the local split using the original local ID
                            paymentSplitDao.updatePaymentSplitDirect(updatedSplit)
                            Log.d(TAG, "Updated local split $localSplitId with server ID ${serverSplit.id}")

                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing split", e)
                            throw e
                        }
                    }

                    return@withContext Result.success(updatedPaymentResult)
                } catch (e: Exception) {
                    Log.e(TAG, "Server sync failed", e)
                    // Return local version if server sync fails
                    return@withContext Result.success(createdPayment)
                }
            }

            // Return local version if offline
            Result.success(createdPayment)
        } catch (e: Exception) {
            Log.e(TAG, "Operation failed", e)
            Result.failure(e)
        }
    }

    suspend fun updatePaymentWithSplits(payment: PaymentEntity, splits: List<PaymentSplitEntity>): Result<PaymentEntity> =
        withContext(dispatchers.io) {
            try {
                // Calculate the final amount based on payment type
                val finalAmount = calculateFinalAmount(payment.amount, payment.paymentType)
                val adjustedPayment = payment.copy(amount = finalAmount)

                // Adjust the splits based on payment type
                val adjustedSplits = splits.map { split ->
                    split.copy(
                        amount = calculateSplitAmount(split.amount, payment.paymentType),
                        syncStatus = SyncStatus.PENDING_SYNC
                    )
                }

                // When creating new splits, save the IDs returned from insertion
                val splitIds = mutableMapOf<PaymentSplitEntity, Int>()

                // First update local data
                paymentDao.runInTransaction {
                    // Update payment with PENDING_SYNC status
                    paymentDao.updatePayment(adjustedPayment.copy(syncStatus = SyncStatus.PENDING_SYNC))

                    // Delete ALL existing splits for this payment
                    paymentSplitDao.markAllSplitsAsDeletedByPayment(payment.id)

                    // Insert new splits with PENDING_SYNC status
                    adjustedSplits.forEach { split ->
                        val id = paymentSplitDao.insertPaymentSplit(
                            split.copy(
                                paymentId = payment.id,
                                syncStatus = SyncStatus.PENDING_SYNC
                            )
                        ).toInt()
                        splitIds[split] = id
                    }

                    // Update group's timestamp
                    val currentTime = DateUtils.getCurrentTimestamp()
                    groupDao.updateGroupTimestamp(payment.groupId, currentTime)
                }

                if (isOnline()) {
                    try {
                        // Convert to server model
                        val paymentServerModel = myApplication.entityServerConverter
                            .convertPaymentToServer(adjustedPayment)
                            .getOrThrow()

                        // Update payment on server
                        val serverPayment = apiService.updatePayment(paymentServerModel.id, paymentServerModel)
                        Log.d(TAG, "Server payment updated successfully: id=${serverPayment.id}")

                        // Convert server payment back to local entity
                        val updatedPaymentResult = myApplication.entityServerConverter
                            .convertPaymentFromServer(serverPayment)
                            .getOrThrow()
                            .copy(
                                id = payment.id,  // Preserve our local ID
                                syncStatus = SyncStatus.SYNCED
                            )

                        // Update the payment with server response
                        paymentDao.runInTransaction {
                            paymentDao.updatePaymentDirect(updatedPaymentResult)
                            Log.d(TAG, "Updated local payment ${payment.id} with server data")
                        }

                        // Delete all splits on server
                        payment.serverId?.let { serverPaymentId ->
                            apiService.deleteAllSplitsForPayment(serverPaymentId)

                            // Update sync status of deleted splits to LOCALLY_DELETED
                            paymentSplitDao.runInTransaction {
                                paymentSplitDao.updateDeletedSplitsSyncStatus(
                                    paymentId = payment.id,
                                    syncStatus = SyncStatus.LOCALLY_DELETED
                                )
                            }
                        }

                        // Create new splits on server
                        adjustedSplits.map { localSplit ->
                            try {
                                val localSplitId = splitIds[localSplit] ?: throw Exception("Missing local ID for split")
                                Log.d(TAG, "Processing split with local ID: $localSplitId")

                                val splitServerModel = myApplication.entityServerConverter
                                    .convertPaymentSplitToServer(localSplit)
                                    .getOrThrow()

                                val serverSplit = apiService.createPaymentSplit(serverPayment.id, splitServerModel)
                                Log.d(TAG, "Created server split: ${serverSplit.id} for local split: $localSplitId")

                                // Convert back to local entity, preserving the local ID
                                val updatedSplit = myApplication.entityServerConverter
                                    .convertPaymentSplitFromServer(serverSplit)
                                    .getOrThrow()
                                    .copy(
                                        id = localSplitId,
                                        paymentId = payment.id,
                                        syncStatus = SyncStatus.SYNCED,
                                        serverId = serverSplit.id
                                    )

                                // Update the local split using the original local ID
                                paymentSplitDao.updatePaymentSplitDirect(updatedSplit)
                                Log.d(TAG, "Updated local split $localSplitId with server ID ${serverSplit.id}")

                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing split", e)
                                throw e
                            }
                        }

                        Result.success(updatedPaymentResult)
                    } catch (e: Exception) {
                        Log.e(TAG, "Server sync failed", e)
                        // Return local version if server sync fails
                        Result.success(adjustedPayment)
                    }
                } else {
                    Result.success(adjustedPayment)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update payment with splits", e)
                Result.failure(e)
            }
        }

    // Single split operations
    suspend fun createPaymentSplit(paymentSplit: PaymentSplitEntity): Result<PaymentSplitEntity> = withContext(dispatchers.io) {
        val localId = paymentSplitDao.insertPaymentSplit(paymentSplit.copy(syncStatus = SyncStatus.PENDING_SYNC))
        val localSplit = paymentSplit.copy(id = localId.toInt())

        try {
            if (isOnline()) {
                // Convert to server model for API
                val serverSplitModel = myApplication.entityServerConverter
                    .convertPaymentSplitToServer(localSplit)
                    .getOrThrow()

                val serverSplit = apiService.createPaymentSplit(serverSplitModel.paymentId, serverSplitModel)

                // Convert server response back to entity
                val updatedEntity = myApplication.entityServerConverter
                    .convertPaymentSplitFromServer(serverSplit)
                    .getOrThrow()
                    .copy(
                        id = localId.toInt(),  // Preserve local ID
                        syncStatus = SyncStatus.SYNCED
                    )

                // Update both split and parent payment in a transaction
                paymentSplitDao.runInTransaction {
                    // Update the split
                    paymentSplitDao.updatePaymentSplitDirect(updatedEntity)

                    // Update parent payment's timestamp
                    val currentTimestamp = DateUtils.getCurrentTimestamp()
                    val payment = paymentDao.getPaymentById(paymentSplit.paymentId).first()
                    payment?.let {
                        paymentDao.updatePayment(
                            it.copy(updatedAt = currentTimestamp, syncStatus = SyncStatus.PENDING_SYNC)
                        )
                    }
                }

                Result.success(updatedEntity)
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

    suspend fun updatePaymentSplit(paymentSplit: PaymentSplitEntity): Result<PaymentSplitEntity> = withContext(dispatchers.io) {
        try {
            // Update split with pending sync status
            val updatedSplit = paymentSplit.copy(syncStatus = SyncStatus.PENDING_SYNC)
            paymentSplitDao.updatePaymentSplitDirect(updatedSplit)

            if (isOnline()) {
                // Convert to server model for API
                val serverModel = myApplication.entityServerConverter
                    .convertPaymentSplitToServer(updatedSplit)
                    .getOrThrow()

                val serverSplit = apiService.updatePaymentSplit(
                    serverModel.paymentId,
                    serverModel.id,
                    serverModel
                )

                // Convert server response back to entity
                val syncedEntity = myApplication.entityServerConverter
                    .convertPaymentSplitFromServer(serverSplit)
                    .getOrThrow()
                    .copy(
                        id = paymentSplit.id,  // Preserve local ID
                        syncStatus = SyncStatus.SYNCED
                    )

                // Update both split and parent payment in a transaction
                paymentSplitDao.runInTransaction {
                    // Update the split
                    paymentSplitDao.updatePaymentSplitDirect(syncedEntity)

                    // Update parent payment's timestamp
                    val currentTimestamp = DateUtils.getCurrentTimestamp()
                    val payment = paymentDao.getPaymentById(paymentSplit.paymentId).first()
                    payment?.let {
                        paymentDao.updatePayment(
                            it.copy(updatedAt = currentTimestamp, syncStatus = SyncStatus.PENDING_SYNC)
                        )
                    }
                }

                Result.success(syncedEntity)
            } else {
                Result.success(updatedSplit)
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

    fun getGroupPayments(groupId: Int): Flow<List<PaymentEntityWithSplits>> = flow {
        try {
            // First emission - get current local data
            emit(getLocalPayments(groupId))

            // Check if we need to sync
            if (shouldSyncGroupPayments(groupId)) {
                try {
                    // Request sync through the sync manager
                    paymentSyncManager.performSync()

                    // After sync completes, emit fresh local data
                    emit(getLocalPayments(groupId))
                } catch (e: Exception) {
                    Log.e(TAG, "Error during sync", e)
                    // Don't throw here - we already emitted initial data
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getGroupPayments", e)
            throw e
        }
    }.flowOn(dispatchers.io)

    private suspend fun getLocalPayments(groupId: Int): List<PaymentEntityWithSplits> {
        Log.d(TAG, "Getting local payments for group $groupId")
        return withContext(dispatchers.io) {
            val paymentEntities = paymentDao.getNonArchivedPaymentsByGroup(groupId)
            Log.d(TAG, "Found ${paymentEntities.size} non-archived payments")

            paymentEntities.map { paymentEntity ->
                val splits = paymentSplitDao.getNonArchivedSplitsByPayment(paymentEntity.id)
                Log.d(TAG, "Payment ${paymentEntity.id} has ${splits.size} splits")

                PaymentEntityWithSplits(paymentEntity, splits)
            }.also { results ->
                Log.d(TAG, """
                Final payment results:
                Total payments: ${results.size}
                Payment details:
                ${results.joinToString("\n") { payment ->
                    """
                    - ID: ${payment.payment.id}
                    Amount: ${payment.payment.amount}
                    Type: ${payment.payment.paymentType}
                    Currency: ${payment.payment.currency}
                    Splits: ${payment.splits.size}
                    """.trimIndent()
                }}
            """.trimIndent())
            }
        }
    }

    private suspend fun shouldSyncGroupPayments(groupId: Int): Boolean {
        // Check sync metadata to determine if sync is needed
        val metadata = syncMetadataDao.getMetadata("payments_group_$groupId") ?: return true

        val timeSinceLastSync = System.currentTimeMillis() - metadata.lastSyncTimestamp
        return timeSinceLastSync > SYNC_INTERVAL || metadata.syncStatus != SyncStatus.SYNCED
    }

    suspend fun convertCurrency(
        amount: Double,
        fromCurrency: String,
        toCurrency: String,
        paymentId: Int? = null,
        userId: Int? = null,
        customExchangeRate: Double? = null
    ): Result<CurrencyConversionResult> = withContext(dispatchers.io) {
        try {
            // Get exchange rate either from custom input or service
            val (rateResult, rateSource) = if (customExchangeRate != null) {
                Pair(customExchangeRate, "user_$userId")
            } else {
                val exchangeRateService = ExchangeRateService()
                val rate = exchangeRateService.getExchangeRate(
                    fromCurrency = fromCurrency,
                    toCurrency = toCurrency
                ).getOrNull() ?: return@withContext Result.failure(
                    Exception("Could not get exchange rate")
                )
                Pair(rate, "ECB/ExchangeRatesAPI")
            }

            val convertedAmount = amount * rateResult

            // If paymentId and userId are provided, handle the conversion
            if (paymentId != null && userId != null) {
                // Use the CurrencyConversionManager to handle all conversion-related updates
                currencyConversionManager.performConversion(
                    paymentId = paymentId,
                    fromCurrency = fromCurrency,
                    toCurrency = toCurrency,
                    amount = amount,
                    exchangeRate = rateResult,
                    userId = userId,
                    source = rateSource
                ).getOrThrow()
            }

            Result.success(
                CurrencyConversionResult(
                    originalAmount = amount,
                    originalCurrency = fromCurrency,
                    convertedAmount = convertedAmount,
                    targetCurrency = toCurrency,
                    exchangeRate = rateResult
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error converting currency", e)
            Result.failure(e)
        }
    }

    suspend fun getConversionCountForPayment(paymentId: Int): Int {
        return currencyConversionDao.getConversionCountForPayment(paymentId)
    }

    suspend fun getLatestConversionForPayment(paymentId: Int): CurrencyConversionEntity? {
        return currencyConversionDao.getLatestConversionForPayment(paymentId)
    }

    suspend fun undoCurrencyConversion(paymentId: Int): Result<PaymentEntity> = withContext(dispatchers.io) {
        try {
            val conversion = currencyConversionDao.getLatestConversionForPayment(paymentId)
                ?: return@withContext Result.failure(Exception("No conversion found for payment"))

            val payment = paymentDao.getPaymentById(paymentId).first()
                ?: return@withContext Result.failure(Exception("Payment not found"))
            val splits = paymentSplitDao.getPaymentSplitsByPayment(paymentId).first()

            val updatedPayment = payment.copy(
                currency = conversion.originalCurrency,
                amount = conversion.originalAmount
            )

            val currentTime = DateUtils.getCurrentTimestamp()

            // Use split calculator for the conversion back
            val newSplits = splitCalculator.calculateSplits(
                payment = updatedPayment,
                splits = splits,
                targetAmount = conversion.originalAmount.toBigDecimal().setScale(2, RoundingMode.HALF_UP),
                targetCurrency = conversion.originalCurrency,
                userId = payment.updatedBy,
                currentTime = currentTime
            )

            updatePaymentWithSplits(updatedPayment, newSplits)
                .onSuccess {
                    currencyConversionDao.markPaymentConversionsAsDeleted(
                        paymentId = paymentId,
                        timestamp = currentTime,
                        syncStatus = SyncStatus.PENDING_SYNC
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error undoing currency conversion", e)
            Result.failure(e)
        }
    }

    suspend fun batchConvertGroupCurrencies(
        groupId: Int,
        userId: Int
    ): Result<BatchConversionResult> = withContext(dispatchers.io) {
        try {
            // Get group details and validate
            val group = groupDao.getGroupByIdSync(groupId)
                ?: return@withContext Result.failure(Exception("Group not found"))

            val targetCurrency = group.defaultCurrency
                ?: return@withContext Result.failure(Exception("Group has no default currency"))

            // Get all non-archived payments for the group
            val payments = paymentDao.getNonArchivedPaymentsByGroup(groupId)

            // Filter payments that need conversion (different currency than group default)
            val paymentsToConvert = payments.filter { payment ->
                payment.currency != null && payment.currency != targetCurrency
            }

            if (paymentsToConvert.isEmpty()) {
                return@withContext Result.success(BatchConversionResult(0, emptyList(), emptyList()))
            }

            // Track results
            val successfulConversions = mutableListOf<ConversionAttempt>()
            val failedConversions = mutableListOf<ConversionAttempt>()

            // Process each payment
            paymentsToConvert.forEach { payment ->
                try {
                    val fromCurrency = payment.currency ?: "GBP"

                    // Get exchange rate from service
                    val exchangeRateService = ExchangeRateService()
                    val rate = exchangeRateService.getExchangeRate(
                        fromCurrency = fromCurrency,
                        toCurrency = targetCurrency
                    ).getOrNull()

                    if (rate == null) {
                        failedConversions.add(
                            ConversionAttempt(
                                paymentId = payment.id,
                                fromCurrency = fromCurrency,
                                toCurrency = targetCurrency,
                                originalAmount = payment.amount,
                                error = "Could not get exchange rate"
                            )
                        )
                        return@forEach
                    }

                    // Use CurrencyConversionManager to perform the conversion
                    val result = currencyConversionManager.performConversion(
                        paymentId = payment.id,
                        fromCurrency = fromCurrency,
                        toCurrency = targetCurrency,
                        amount = payment.amount,
                        exchangeRate = rate,
                        userId = userId,
                        source = "ECB/ExchangeRatesAPI"
                    )

                    result.fold(
                        onSuccess = { conversion ->
                            successfulConversions.add(
                                ConversionAttempt(
                                    paymentId = payment.id,
                                    fromCurrency = fromCurrency,
                                    toCurrency = targetCurrency,
                                    originalAmount = payment.amount,
                                    convertedAmount = conversion.finalAmount,
                                    exchangeRate = conversion.exchangeRate
                                )
                            )
                        },
                        onFailure = { error ->
                            failedConversions.add(
                                ConversionAttempt(
                                    paymentId = payment.id,
                                    fromCurrency = fromCurrency,
                                    toCurrency = targetCurrency,
                                    originalAmount = payment.amount,
                                    error = error.message ?: "Unknown error"
                                )
                            )
                        }
                    )
                } catch (e: Exception) {
                    failedConversions.add(
                        ConversionAttempt(
                            paymentId = payment.id,
                            fromCurrency = payment.currency ?: "GBP",
                            toCurrency = targetCurrency,
                            originalAmount = payment.amount,
                            error = e.message ?: "Unknown error"
                        )
                    )
                }
            }

            Result.success(
                BatchConversionResult(
                    totalPayments = paymentsToConvert.size,
                    successfulConversions = successfulConversions,
                    failedConversions = failedConversions
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in batch currency conversion", e)
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

            // Run payment sync first
            paymentSyncManager.performSync()

            // Then run currency conversion sync
            currencyConversionSyncManager.performSync()

            // Update sync metadata
            syncMetadataDao.update(entityType) {
                it.copy(
                    lastSyncTimestamp = System.currentTimeMillis(),
                    syncStatus = SyncStatus.SYNCED,
                    lastSyncResult = "Sync completed successfully"
                )
            }

            Log.d(TAG, "Payment and currency conversion sync completed successfully")
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
        private const val SYNC_INTERVAL = 5 * 60 * 1000 // 5 minutes
    }
}