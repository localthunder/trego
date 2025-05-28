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
import com.helgolabs.trego.data.local.dao.UserDao
import com.helgolabs.trego.data.local.dataClasses.BatchConversionResult
import com.helgolabs.trego.data.local.dataClasses.BatchNotificationRequest
import com.helgolabs.trego.data.local.dataClasses.ConversionAttempt
import com.helgolabs.trego.data.local.dataClasses.CurrencyConversionResult
import com.helgolabs.trego.data.local.dataClasses.ExchangeRateInfo
import com.helgolabs.trego.data.local.dataClasses.PaymentEntityWithSplits
import com.helgolabs.trego.data.local.dataClasses.PaymentWithSplits
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
import com.helgolabs.trego.utils.NetworkUtils.hasNetworkCapabilities
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
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
    private val userDao: UserDao,
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
    val paymentSplitRepository = myApplication.syncManagerProvider.providePaymentSplitRepository()

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
            val entityByLocalId = paymentDao.getPaymentById(paymentId).firstOrNull()
            if (entityByLocalId != null) {
                Log.d(TAG, "Found payment by local ID: $entityByLocalId")
                emit(entityByLocalId)
                return@flow  // Exit after emitting
            }

            // If not found by local ID, try server ID
            val entityByServerId = paymentDao.getPaymentByServerId(paymentId)
            if (entityByServerId != null) {
                Log.d(TAG, "Found payment by server ID: $entityByServerId")
                emit(entityByServerId)
            } else {
                emit(null)
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

        if (hasNetworkCapabilities()) {
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
            if (hasNetworkCapabilities()) {
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
            if (hasNetworkCapabilities()) {
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
            if (hasNetworkCapabilities()) {
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
                split.copy(
                    amount = calculateSplitAmount(split.amount, adjustedPaymentType),
                    percentage = split.percentage
                )
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
        Log.d(TAG, "=== CREATE PAYMENT WITH SPLITS DEBUG START ===")
        Log.d(TAG, "Payment details - id: ${payment.id}, groupId: ${payment.groupId}, amount: ${payment.amount}")
        Log.d(TAG, "Payment splitMode: ${payment.splitMode}")
        Log.d(TAG, "Incoming splits: ${splits.size}")
        splits.forEachIndexed { index, split ->
            Log.d(TAG, "  Input split[$index]: userId=${split.userId}, amount=${split.amount}, percentage=${split.percentage}")
        }
        Log.d(TAG, "Is online: ${hasNetworkCapabilities()}")
        Log.d(TAG, "Network state: ${NetworkUtils.hasNetworkCapabilities()}")

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
            Log.d(TAG, "Final amount after calculation: $finalAmount (original: ${payment.amount})")

            // Adjust the splits based on payment type
            Log.d(TAG, "Adjusting splits based on payment type: ${payment.paymentType}")
            val adjustedSplits = splits.map { split ->
                val adjustedAmount = calculateSplitAmount(split.amount, payment.paymentType)
                val adjustedSplit = split.copy(
                    amount = adjustedAmount,
                    percentage = split.percentage  // PRESERVE THE PERCENTAGE
                )
                Log.d(TAG, "  Adjusted split: userId=${adjustedSplit.userId}, amount=${adjustedSplit.amount} (was ${split.amount}), percentage=${adjustedSplit.percentage}")
                adjustedSplit
            }

            // Create payment with adjusted amount
            val paymentToCreate = payment.copy(amount = finalAmount)

            // Create initial local payment with PENDING_SYNC status
            val localPayment = paymentToCreate.copy(syncStatus = SyncStatus.PENDING_SYNC)
            val localPaymentId = paymentDao.insertPayment(localPayment)
            Log.d(TAG, "Local payment created with ID: $localPaymentId")

            // Create initial local splits with PENDING_SYNC status
            Log.d(TAG, "Creating initial split entities for local insertion")
            val initialSplitEntities = adjustedSplits.map { split ->
                val initialSplit = split.copy(
                    id = 0,
                    paymentId = localPaymentId.toInt(),
                    syncStatus = SyncStatus.PENDING_SYNC
                )
                Log.d(TAG, "  Initial split entity: userId=${initialSplit.userId}, amount=${initialSplit.amount}, percentage=${initialSplit.percentage}, paymentId=${initialSplit.paymentId}")
                initialSplit
            }

            // Insert local splits and capture their IDs
            val splitsWithLocalIds = paymentDao.runInTransaction {
                initialSplitEntities.map { splitEntity ->
                    Log.d(TAG, "About to insert split: userId=${splitEntity.userId}, amount=${splitEntity.amount}, percentage=${splitEntity.percentage}")

                    val splitId = paymentSplitDao.insertPaymentSplit(splitEntity).toInt()
                    Log.d(TAG, "Inserted split with ID: $splitId")

                    // Verify what was actually saved
                    val savedSplit = paymentSplitDao.getPaymentSplitById(splitId)
                    Log.d(TAG, "Verification - saved split: userId=${savedSplit.userId}, amount=${savedSplit.amount}, percentage=${savedSplit.percentage}")

                    val splitWithId = splitEntity.copy(id = splitId)
                    Log.d(TAG, "Split with local ID: userId=${splitWithId.userId}, amount=${splitWithId.amount}, percentage=${splitWithId.percentage}, id=${splitWithId.id}")
                    splitWithId
                }.also {
                    groupDao.updateGroupTimestamp(payment.groupId, currentTime)
                }
            }

            Log.d(TAG, "Splits with local IDs (${splitsWithLocalIds.size}):")
            splitsWithLocalIds.forEachIndexed { index, split ->
                Log.d(TAG, "  Split[$index] with ID: userId=${split.userId}, amount=${split.amount}, percentage=${split.percentage}, id=${split.id}")
            }

            val createdPayment = localPayment.copy(id = localPaymentId.toInt())

            // If online, sync with server
            if (hasNetworkCapabilities()) {
                try {
                    Log.d(TAG, "Device is online, attempting server sync")

                    // Convert to server model
                    val paymentServerModel = myApplication.entityServerConverter
                        .convertPaymentToServer(createdPayment)
                        .getOrThrow()

                    Log.d(TAG, "Converted payment to server model: $paymentServerModel")

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
                    Log.d(TAG, "Creating ${splitsWithLocalIds.size} splits on server")
                    splitsWithLocalIds.forEach { localSplit ->
                        try {
                            val localSplitId = localSplit.id
                            Log.d(TAG, "Processing split with local ID: $localSplitId")
                            Log.d(TAG, "  Local split before server conversion: userId=${localSplit.userId}, amount=${localSplit.amount}, percentage=${localSplit.percentage}")

                            val splitServerModel = myApplication.entityServerConverter
                                .convertPaymentSplitToServer(localSplit)
                                .getOrThrow()

                            Log.d(TAG, "  Converted to server model: $splitServerModel")

                            val serverSplit = apiService.createPaymentSplit(serverPayment.id, splitServerModel)
                            Log.d(TAG, "  Created server split: ${serverSplit.id} for local split: $localSplitId")
                            Log.d(TAG, "  Server split details: $serverSplit")

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

                            Log.d(TAG, "  Updated split for local save: userId=${updatedSplit.userId}, amount=${updatedSplit.amount}, percentage=${updatedSplit.percentage}")

                            // Update the local split using the original local ID
                            paymentSplitDao.updatePaymentSplitDirect(updatedSplit)
                            Log.d(TAG, "  Updated local split $localSplitId with server ID ${serverSplit.id}")

                            // Verify what was actually updated
                            val verificationSplit = paymentSplitDao.getPaymentSplitById(localSplitId)
                            Log.d(TAG, "  Verification after update: userId=${verificationSplit.userId}, amount=${verificationSplit.amount}, percentage=${verificationSplit.percentage}")

                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing split", e)
                            throw e
                        }
                    }

                    Log.d(TAG, "=== CREATE PAYMENT WITH SPLITS DEBUG END (SUCCESS) ===")
                    return@withContext Result.success(updatedPaymentResult)
                } catch (e: Exception) {
                    Log.e(TAG, "Server sync failed", e)
                    // Return local version if server sync fails
                    Log.d(TAG, "=== CREATE PAYMENT WITH SPLITS DEBUG END (SERVER SYNC FAILED) ===")
                    return@withContext Result.success(createdPayment)
                }
            }

            // Return local version if offline
            Log.d(TAG, "=== CREATE PAYMENT WITH SPLITS DEBUG END (OFFLINE) ===")
            Result.success(createdPayment)
        } catch (e: Exception) {
            Log.e(TAG, "Operation failed", e)
            Log.d(TAG, "=== CREATE PAYMENT WITH SPLITS DEBUG END (FAILED) ===")
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
                        syncStatus = SyncStatus.PENDING_SYNC,
                        percentage = split.percentage
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

                if (hasNetworkCapabilities()) {
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

    suspend fun updatePaymentWithSplitsSkipNotification(payment: PaymentEntity, splits: List<PaymentSplitEntity>): Result<PaymentEntity> =
        withContext(dispatchers.io) {
            try {
                // Calculate the final amount based on payment type
                val finalAmount = calculateFinalAmount(payment.amount, payment.paymentType)
                val adjustedPayment = payment.copy(amount = finalAmount)

                // Adjust the splits based on payment type
                val adjustedSplits = splits.map { split ->
                    split.copy(
                        amount = calculateSplitAmount(split.amount, payment.paymentType),
                        syncStatus = SyncStatus.PENDING_SYNC,
                        percentage = split.percentage
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

                if (hasNetworkCapabilities()) {
                    try {
                        // Convert to server model
                        val paymentServerModel = myApplication.entityServerConverter
                            .convertPaymentToServer(adjustedPayment)
                            .getOrThrow()

                        // **KEY CHANGE: Add special header to skip expense notifications**
                        val headers = mapOf(
                            "x-skip-expense-notification" to "true",
                            "x-currency-conversion-undo" to "true"
                        )

                        // Update payment on server
                        val serverPayment = apiService.updatePayment(
                            paymentServerModel.id,
                            paymentServerModel,
                            headers
                        )

                        Log.d(TAG, "Server payment updated with skip notification flag: id=${serverPayment.id}")

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
                Log.e(TAG, "Failed to update payment with splits (skip notification)", e)
                Result.failure(e)
            }
        }

    // Single split operations
    suspend fun createPaymentSplit(paymentSplit: PaymentSplitEntity): Result<PaymentSplitEntity> = withContext(dispatchers.io) {
        val localId = paymentSplitDao.insertPaymentSplit(paymentSplit.copy(syncStatus = SyncStatus.PENDING_SYNC))
        val localSplit = paymentSplit.copy(id = localId.toInt())

        try {
            if (hasNetworkCapabilities()) {
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
            if (NetworkUtils.hasNetworkCapabilities()) {
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

            if (hasNetworkCapabilities()) {
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
            if (NetworkUtils.hasNetworkCapabilities()) {
                paymentSplitDao.updatePaymentSplitSyncStatus(paymentSplit.id, SyncStatus.SYNC_FAILED)
            }
            Result.failure(e)
        }
    }

    suspend fun archivePayment(paymentId: Int): Result<Unit> = withContext(dispatchers.io) {
        try {
            Log.d("PaymentRepository", "Starting archive process for payment ID: $paymentId")

            // Archive locally first
            val timestamp = DateUtils.getCurrentTimestamp()
            Log.d("PaymentRepository", "Archiving payment locally with timestamp: $timestamp")
            paymentDao.archivePayment(paymentId, timestamp)
            Log.d("PaymentRepository", "Payment $paymentId archived locally successfully")

            // Check basic network connectivity (not server health)
            val hasNetwork = NetworkUtils.hasNetworkCapabilities()
            Log.d("PaymentRepository", "Has network capabilities: $hasNetwork")

            // Attempt to sync with server if we have network
            if (hasNetwork) {
                try {
                    Log.d("PaymentRepository", "Getting server ID for payment $paymentId")
                    val serverPaymentId = getServerId(paymentId, "payments", context)
                    Log.d("PaymentRepository", "Server payment ID: $serverPaymentId")

                    if (serverPaymentId == null || serverPaymentId == 0) {
                        Log.w("PaymentRepository", "No server ID found for payment $paymentId, cannot sync to server")
                        paymentDao.updatePaymentSyncStatus(paymentId, SyncStatus.LOCAL_ONLY)
                        Log.d("PaymentRepository", "Payment $paymentId marked as LOCAL_ONLY due to missing server ID")
                        return@withContext Result.success(Unit)
                    }

                    Log.d("PaymentRepository", "Calling server archive endpoint for server payment ID: $serverPaymentId")
                    apiService.archivePayment(serverPaymentId)
                    Log.d("PaymentRepository", "Server archive call successful for payment $serverPaymentId")

                    // Mark as synced since we successfully called the archive endpoint
                    paymentDao.updatePaymentSyncStatus(paymentId, SyncStatus.SYNCED)
                    Log.d("PaymentRepository", "Payment $paymentId marked as SYNCED")

                } catch (e: Exception) {
                    Log.e("PaymentRepository", "Failed to sync payment archive with server for payment $paymentId", e)
                    Log.e("PaymentRepository", "Exception type: ${e::class.simpleName}")
                    Log.e("PaymentRepository", "Exception message: ${e.message}")

                    // Mark as LOCAL_ONLY instead of PENDING_SYNC to prevent background sync
                    paymentDao.updatePaymentSyncStatus(paymentId, SyncStatus.LOCAL_ONLY)
                    Log.d("PaymentRepository", "Payment $paymentId marked as LOCAL_ONLY due to server sync failure")
                }
            } else {
                Log.d("PaymentRepository", "No network capabilities, marking payment $paymentId as LOCAL_ONLY")
                paymentDao.updatePaymentSyncStatus(paymentId, SyncStatus.LOCAL_ONLY)
                Log.d("PaymentRepository", "Payment $paymentId marked as LOCAL_ONLY due to no network")
            }

            Log.d("PaymentRepository", "Archive operation completed successfully for payment $paymentId")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e("PaymentRepository", "Failed to archive payment $paymentId", e)
            Log.e("PaymentRepository", "Local archive exception type: ${e::class.simpleName}")
            Log.e("PaymentRepository", "Local archive exception message: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun restorePayment(paymentId: Int): Result<Unit> = withContext(dispatchers.io) {
        try {
            // Restore locally first
            paymentDao.restorePayment(paymentId)
            paymentDao.updatePaymentSyncStatus(paymentId, SyncStatus.PENDING_SYNC)

            // Attempt server sync if online
            if (hasNetworkCapabilities()) {
                try {
                    val serverPaymentId = getServerId(paymentId, "payments", context) ?: 0
                    apiService.restorePayment(serverPaymentId)
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

    suspend fun getPaymentWithSplits(paymentId: Int): PaymentEntityWithSplits? {
        try {
            Log.d("PaymentRepo", "Getting payment with ID: $paymentId")

            // Get payment
            val payment = try {
                getPaymentById(paymentId).first()
            } catch (e: Exception) {
                Log.e("PaymentRepo", "Error fetching payment", e)
                null
            }

            if (payment == null) {
                Log.d("PaymentRepo", "Payment not found with ID: $paymentId")
                return null
            }

            Log.d("PaymentRepo", "Found payment: $payment")

            // Get splits
            val splits = try {
                paymentSplitRepository.getPaymentSplitsByPayment(paymentId).first()
            } catch (e: Exception) {
                Log.e("PaymentRepo", "Error fetching splits", e)
                emptyList()
            }

            Log.d("PaymentRepo", "Found ${splits.size} splits for payment $paymentId: $splits")
            return PaymentEntityWithSplits(payment, splits)
        } catch (e: Exception) {
            Log.e("PaymentRepo", "Error in getPaymentWithSplits", e)
            return null
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

    suspend fun getExchangeRateInfo(
        fromCurrency: String,
        toCurrency: String,
        paymentDate: String?
    ): Result<ExchangeRateInfo> {
        return try {
            // Convert String date to LocalDate if provided
            val localDate = paymentDate?.let { dateStr ->
                try {
                    val parsedDate = java.time.LocalDate.parse(
                        dateStr.substring(0, 10),
                        java.time.format.DateTimeFormatter.ISO_DATE
                    )
                    parsedDate
                } catch (e: Exception) {
                    null
                }
            }

            // Use your existing ExchangeRateService to get the rate
            val exchangeRateService = ExchangeRateService()
            val rateResult = exchangeRateService.getExchangeRate(
                fromCurrency = fromCurrency,
                toCurrency = toCurrency,
                date = localDate
            )

            rateResult.map { rate ->
                // Date is either the requested date or today's date if it was null/auto-fetched
                val dateStr = localDate?.toString() ?: DateUtils.getCurrentDate()
                ExchangeRateInfo(rate, dateStr)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun convertCurrency(
        amount: Double,
        fromCurrency: String,
        toCurrency: String,
        paymentId: Int? = null,
        userId: Int? = null,
        paymentDate: String? = null,
        customExchangeRate: Double? = null
    ): Result<CurrencyConversionResult> = withContext(dispatchers.io) {
        try {
            Log.d(TAG, "Starting currency conversion with parameters:")
            Log.d(TAG, "Amount: $amount, From: $fromCurrency, To: $toCurrency")
            Log.d(TAG, "Payment date: $paymentDate, Payment ID: $paymentId, User ID: $userId")

            // Get exchange rate either from custom input or service
            val (rateResult, rateSource) = if (customExchangeRate != null) {
                Log.d(TAG, "Using custom exchange rate: $customExchangeRate")
                Pair(customExchangeRate, "user_$userId")
            } else {
                val exchangeRateService = ExchangeRateService()

                // Convert String date to LocalDate if provided
                val localDate = paymentDate?.let { dateStr ->
                    try {
                        // Use your DateUtils to parse the date string
                        val parsedDate = java.time.LocalDate.parse(
                            dateStr.substring(0, 10),
                            java.time.format.DateTimeFormatter.ISO_DATE
                        )
                        Log.d(TAG, "Successfully parsed payment date: $dateStr to LocalDate: $parsedDate")
                        parsedDate
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing payment date: $dateStr", e)
                        Log.w(TAG, "Falling back to current date for exchange rate lookup")
                        null // Fall back to current date if parsing fails
                    }
                }

                Log.d(TAG, "Requesting exchange rate for date: ${localDate ?: "Current date (no date provided)"}")

                val rate = exchangeRateService.getExchangeRate(
                    fromCurrency = fromCurrency,
                    toCurrency = toCurrency,
                    date = localDate
                ).getOrNull() ?: return@withContext Result.failure(
                    Exception("Could not get exchange rate")
                )

                Log.d(TAG, "Retrieved exchange rate: $rate for date: ${localDate ?: "Current date"}")
                Pair(rate, "ECB/ExchangeRatesAPI")
            }

            val convertedAmount = amount * rateResult
            Log.d(TAG, "Conversion result: $amount $fromCurrency → $convertedAmount $toCurrency (rate: $rateResult)")

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

            // Use a special method that skips expense notifications
            val result = updatePaymentWithSplitsSkipNotification(updatedPayment, newSplits)

            result.onSuccess {
                // Mark conversions as deleted locally
                currencyConversionDao.markPaymentConversionsAsDeleted(
                    paymentId = paymentId,
                    timestamp = currentTime,
                    syncStatus = SyncStatus.PENDING_SYNC
                )

                // Try to delete on server immediately if online
                if (hasNetworkCapabilities()) {
                    try {
                        conversion.serverId?.let { serverConversionId ->
                            Log.d(TAG, "Deleting currency conversion on server: $serverConversionId")
                            apiService.deleteCurrencyConversion(serverConversionId)

                            // Update sync status to LOCALLY_DELETED since server deletion succeeded
                            currencyConversionDao.markPaymentConversionsAsDeleted(
                                paymentId = paymentId,
                                timestamp = currentTime,
                                syncStatus = SyncStatus.LOCALLY_DELETED
                            )

                            Log.d(TAG, "Successfully deleted currency conversion on server")
                        } ?: run {
                            Log.d(TAG, "No server ID for conversion, marking as LOCAL_ONLY")
                            // If no server ID, mark as LOCAL_ONLY since nothing to delete on server
                            currencyConversionDao.markPaymentConversionsAsDeleted(
                                paymentId = paymentId,
                                timestamp = currentTime,
                                syncStatus = SyncStatus.LOCAL_ONLY
                            )
                        }
                    } catch (serverError: Exception) {
                        Log.e(TAG, "Failed to delete currency conversion on server, will retry via sync", serverError)
                        // Keep PENDING_SYNC status so the sync manager will retry later
                    }
                } else {
                    Log.d(TAG, "No network connection, will sync deletion later")
                    // Keep PENDING_SYNC status for when network is available
                }
            }

            result
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
            Log.d(TAG, "=== BATCH CONVERSION START ===")

            val group = groupDao.getGroupByIdSync(groupId)
                ?: return@withContext Result.failure(Exception("Group not found"))

            val targetCurrency = group.defaultCurrency
                ?: return@withContext Result.failure(Exception("Group has no default currency"))

            Log.d(TAG, "Target currency: $targetCurrency")

            val payments = paymentDao.getNonArchivedPaymentsByGroup(groupId)
            val paymentsToConvert = payments.filter { payment ->
                payment.currency != null && payment.currency != targetCurrency
            }

            Log.d(TAG, "Found ${paymentsToConvert.size} payments to convert:")
            paymentsToConvert.forEach { payment ->
                Log.d(TAG, "  Payment ${payment.id}: ${payment.currency} -> $targetCurrency (amount: ${payment.amount})")
            }

            if (paymentsToConvert.isEmpty()) {
                return@withContext Result.success(BatchConversionResult(0, emptyList(), emptyList()))
            }

            val successfulConversions = mutableListOf<ConversionAttempt>()
            val failedConversions = mutableListOf<ConversionAttempt>()

            // Process each payment with detailed logging
            paymentsToConvert.forEachIndexed { index, payment ->
                try {
                    val fromCurrency = payment.currency ?: "GBP"
                    Log.d(TAG, "[$index/${paymentsToConvert.size}] Processing payment ${payment.id}: $fromCurrency -> $targetCurrency")

                    // Get exchange rate
                    val localDate = try {
                        payment.paymentDate?.let { dateStr ->
                            java.time.LocalDate.parse(
                                dateStr.substring(0, 10),
                                java.time.format.DateTimeFormatter.ISO_DATE
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing payment date for ${payment.id}", e)
                        null
                    }

                    val exchangeRateService = ExchangeRateService()
                    val rate = exchangeRateService.getExchangeRate(
                        fromCurrency = fromCurrency,
                        toCurrency = targetCurrency,
                        date = localDate
                    ).getOrNull()

                    if (rate == null) {
                        Log.e(TAG, "❌ Could not get exchange rate for payment ${payment.id}")
                        failedConversions.add(
                            ConversionAttempt(
                                paymentId = payment.id,
                                fromCurrency = fromCurrency,
                                toCurrency = targetCurrency,
                                originalAmount = payment.amount,
                                error = "Could not get exchange rate"
                            )
                        )
                        return@forEachIndexed
                    }

                    Log.d(TAG, "Got exchange rate for ${payment.id}: $rate")

                    // Perform conversion with skipNotification = true (for batch)
                    val skipIndividualNotification = paymentsToConvert.size > 1

                    val result = currencyConversionManager.performConversion(
                        paymentId = payment.id,
                        fromCurrency = fromCurrency,
                        toCurrency = targetCurrency,
                        amount = payment.amount,
                        exchangeRate = rate,
                        userId = userId,
                        source = "ECB/ExchangeRatesAPI",
                        skipNotification = skipIndividualNotification
                    )

                    result.fold(
                        onSuccess = { conversion ->
                            Log.d(TAG, "✅ Successfully converted payment ${payment.id}")
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
                            Log.e(TAG, "❌ Failed to convert payment ${payment.id}: ${error.message}")
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
                    Log.e(TAG, "❌ Exception converting payment ${payment.id}", e)
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

            Log.d(TAG, "Batch conversion completed: ${successfulConversions.size} successful, ${failedConversions.size} failed")

            // Send batch notification if we have multiple conversions and at least one success
            if (paymentsToConvert.size > 1 && successfulConversions.isNotEmpty()) {
                Log.d(TAG, "Sending batch notification for ${successfulConversions.size} successful conversions")
                try {
                    sendBatchConversionNotification(
                        groupId = groupId,
                        userId = userId,
                        targetCurrency = targetCurrency,
                        successfulConversions = successfulConversions.size,
                        totalAttempted = paymentsToConvert.size
                    )
                    Log.d(TAG, "✅ Batch notification sent successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to send batch notification", e)
                }
            } else if (paymentsToConvert.size == 1) {
                Log.d(TAG, "Single payment conversion - individual notification was sent")
            } else {
                Log.d(TAG, "No successful conversions - no notification sent")
            }

            Log.d(TAG, "=== BATCH CONVERSION END ===")

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

    private suspend fun sendBatchConversionNotification(
        groupId: Int,
        userId: Int,
        targetCurrency: String,
        successfulConversions: Int,
        totalAttempted: Int
    ) {
        Log.d(TAG, "=== SENDING BATCH NOTIFICATION ===")

        try {
            if (!NetworkUtils.hasNetworkCapabilities()) {
                Log.w(TAG, "No network - skipping batch notification")
                return
            }

            val group = groupDao.getGroupByIdSync(groupId) ?: run {
                Log.e(TAG, "Group not found: $groupId")
                return
            }

            val user = userDao.getUserByIdSync(userId) ?: run {
                Log.e(TAG, "User not found: $userId")
                return
            }

            // Use the data class instead of Map
            val request = BatchNotificationRequest(
                groupId = group.serverId ?: groupId,
                userId = user.serverId ?: userId,
                targetCurrency = targetCurrency,
                successfulConversions = successfulConversions,
                totalAttempted = totalAttempted,
                userName = user.username,
                groupName = group.name
            )

            Log.d(TAG, "Sending notification request: $request")

            val response = apiService.sendBatchCurrencyConversionNotification(request)

            if (response.success) {
                Log.d(TAG, "✅ Batch notification sent to ${response.notificationsSent} members")
            } else {
                Log.e(TAG, "❌ Batch notification failed: ${response.error}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception sending batch notification", e)
        }

        Log.d(TAG, "=== BATCH NOTIFICATION END ===")
    }

    fun getAddedTransactionIds(groupId: Int): Flow<List<String>> {
        return paymentDao.getAddedTransactionIds(groupId)
            .catch { e ->
                Log.e("PaymentRepository", "Error fetching added transaction IDs", e)
                emit(emptyList())
            }
            .flowOn(dispatchers.io)
    }

    override suspend fun sync(): Unit = withContext(dispatchers.io) {
        try {
            Log.d(TAG, "Starting payment sync process")

            if (!NetworkUtils.hasNetworkCapabilities()) {
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