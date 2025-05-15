package com.helgolabs.trego.data.sync.managers

import android.content.Context
import android.util.Log
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.extensions.toEntity
import com.helgolabs.trego.data.extensions.toModel
import com.helgolabs.trego.data.local.dao.PaymentDao
import com.helgolabs.trego.data.local.dao.PaymentSplitDao
import com.helgolabs.trego.data.local.dao.SyncMetadataDao
import com.helgolabs.trego.data.local.dao.UserDao
import com.helgolabs.trego.data.local.dataClasses.PaymentWithSplits
import com.helgolabs.trego.data.local.dataClasses.PaymentWithSplitsAndLocalIds
import com.helgolabs.trego.data.local.entities.PaymentEntity
import com.helgolabs.trego.data.local.entities.PaymentSplitEntity
import com.helgolabs.trego.data.model.Group
import com.helgolabs.trego.data.model.Payment
import com.helgolabs.trego.data.model.PaymentSplit
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.sync.GroupSyncManager
import com.helgolabs.trego.data.sync.OptimizedSyncManager
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.utils.ConflictResolution
import com.helgolabs.trego.utils.ConflictResolver
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.NetworkUtils
import com.helgolabs.trego.utils.ServerIdUtil
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
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
) : OptimizedSyncManager<PaymentWithSplitsAndLocalIds, PaymentWithSplits>(syncMetadataDao, dispatchers) {

    override val entityType = "payments"
    override val batchSize = 20

    val myApplication = context.applicationContext as MyApplication

    override suspend fun getLocalChanges(): List<PaymentWithSplitsAndLocalIds> =
        paymentDao.getUnsyncedPayments().first().mapNotNull { paymentEntity ->
            Log.d(TAG, """
            Processing payment ${paymentEntity.id} for sync:
            Server ID: ${paymentEntity.serverId}
            Sync Status: ${paymentEntity.syncStatus}
        """.trimIndent())

            // Convert payment to server format
            val serverPayment = myApplication.entityServerConverter
                .convertPaymentToServer(paymentEntity)
                .onFailure {
                    Log.e(TAG, "Failed to convert payment to server format", it)
                }
                .getOrNull() ?: return@mapNotNull null

            // Get splits and filter out locally deleted splits that were never synced
            val localSplits = paymentSplitDao.getPaymentSplitsByPayment(paymentEntity.id)
                .first()
                .filterNot { split ->
                    split.deletedAt != null && split.serverId == null
                }

            // Get deleted splits that were previously synced
            val deletedSplits = paymentSplitDao.getDeletedSplitsByPayment(paymentEntity.id).first()

            Log.d(TAG, """
            Found for payment ${paymentEntity.id}:
            - Active splits: ${localSplits.size}
            - Deleted splits: ${deletedSplits.size}
        """.trimIndent())

            PaymentWithSplitsAndLocalIds(
                payment = serverPayment,
                splits = emptyList(), // We'll convert splits after payment is created
                localPaymentId = paymentEntity.id,
                localSplitIds = localSplits.map { it.id },
                deletedSplitIds = deletedSplits.mapNotNull { it.serverId }
            )
        }

    override suspend fun syncToServer(entity: PaymentWithSplitsAndLocalIds): Result<PaymentWithSplitsAndLocalIds> = try {
        Log.d(TAG, "Starting payment sync for payment ${entity.localPaymentId}")

        // First create/update the payment on server
        val serverPayment = apiService.createPayment(entity.payment)
        Log.d(TAG, "Payment created on server with ID: ${serverPayment.id}")

        // Handle split deletions before creating new splits
        entity.deletedSplitIds.forEach { serverSplitId ->
            try {
                apiService.deletePaymentSplit(serverPayment.id, serverSplitId)
                Log.d(TAG, "Deleted split $serverSplitId from server")

                // Update the sync status of the local split to LOCALLY_DELETED
                paymentSplitDao.runInTransaction {
                    paymentSplitDao.getPaymentSplitByServerId(serverSplitId)?.let { localSplit ->
                        paymentSplitDao.updatePaymentSplitSyncStatus(
                            localSplit.id,
                            SyncStatus.LOCALLY_DELETED
                        )
                        Log.d(TAG, "Updated split ${localSplit.id} status to LOCALLY_DELETED")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete split $serverSplitId from server", e)
            }
        }

        // Update local payment and get server ID mapping
        paymentDao.runInTransaction {
            // First, save the ID mapping
            ServerIdUtil.saveIdMapping(
                localId = entity.localPaymentId,
                serverId = serverPayment.id,
                entityType = "payments",
                context = context
            )

            // Get the existing payment to preserve local data
            val existingPayment = paymentDao.getPaymentById(entity.localPaymentId).firstOrNull()
                ?: throw Exception("Could not find payment with ID ${entity.localPaymentId}")

            // Update the payment with the server ID and sync status
            val updatedPayment = existingPayment.copy(
                serverId = serverPayment.id,
                syncStatus = SyncStatus.SYNCED,
                updatedAt = DateUtils.getCurrentTimestamp()
            )

            // Perform the update
            paymentDao.updatePaymentDirect(updatedPayment)

            // Verify the update
            val verifiedPayment = paymentDao.getPaymentById(entity.localPaymentId).firstOrNull()
            Log.d(TAG, "Payment ${entity.localPaymentId} sync status after update: ${verifiedPayment?.syncStatus}")
        }

        // Now convert and create splits using the new server payment ID
        val localSplits = paymentSplitDao.getPaymentSplitsByPayment(entity.localPaymentId).first()
        val convertedSplits = localSplits.mapIndexedNotNull { index, splitEntity ->
            try {
                Log.d(TAG, "Converting split ${index + 1}/${localSplits.size}")

                // Get server user ID
                val serverUserId = ServerIdUtil.getServerId(splitEntity.userId, "users", context)
                    ?: throw Exception("No server ID found for user ${splitEntity.userId}")

                val createdByServerId = ServerIdUtil.getServerId(splitEntity.createdBy, "users", context)
                    ?: throw Exception("No server ID found for user ${splitEntity.createdBy}")

                val updatedByServerId = ServerIdUtil.getServerId(splitEntity.updatedBy, "users", context)
                    ?: throw Exception("No server ID found for user ${splitEntity.updatedBy}")

                // Create base split model
                val splitToCreate = PaymentSplit(
                    id = 0,
                    paymentId = serverPayment.id,
                    userId = serverUserId,
                    amount = splitEntity.amount,
                    createdBy = createdByServerId,
                    updatedBy = updatedByServerId,
                    createdAt = splitEntity.createdAt,
                    updatedAt = splitEntity.updatedAt,
                    currency = splitEntity.currency,
                    deletedAt = splitEntity.deletedAt
                )

                // Create split on server
                val serverSplit = apiService.createPaymentSplit(serverPayment.id, splitToCreate)
                Log.d(TAG, "Created split on server with ID: ${serverSplit.id}")

                // Update local split with server ID and sync status
                paymentSplitDao.runInTransaction {
                    // Save ID mapping
                    ServerIdUtil.saveIdMapping(
                        localId = splitEntity.id,
                        serverId = serverSplit.id,
                        entityType = "payment_splits",
                        context = context
                    )

                    // Update the split with server ID and sync status
                    val updatedSplit = splitEntity.copy(
                        serverId = serverSplit.id,
                        syncStatus = SyncStatus.SYNCED,
                        updatedAt = DateUtils.getCurrentTimestamp()
                    )

                    paymentSplitDao.updatePaymentSplitDirect(updatedSplit)

                    // Verify the update
                    val verifiedSplit = paymentSplitDao.getPaymentSplitById(splitEntity.id)
                    Log.d(TAG, "Split ${splitEntity.id} sync status after update: ${verifiedSplit?.syncStatus}")
                }

                serverSplit
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process split ${splitEntity.id}", e)
                null
            }
        }

        Log.d(TAG, """
        Sync completed:
        - Payment ID: ${entity.localPaymentId} -> ${serverPayment.id}
        - Splits synced: ${convertedSplits.size}/${localSplits.size}
    """.trimIndent())

        Result.success(entity.copy(splits = convertedSplits))
    } catch (e: Exception) {
        Log.e(TAG, "Error syncing payment to server", e)
        e.printStackTrace()
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
            try {
                // First try to find existing payment by server ID
                val existingPayment = paymentDao.getPaymentByServerId(serverEntity.payment.id)
                val localPayment = myApplication.entityServerConverter
                    .convertPaymentFromServer(serverEntity.payment)
                    .getOrNull() ?: throw Exception("Failed to convert server payment")

                when {
                    existingPayment == null -> {
                        Log.d(TAG, "Inserting new payment from server: ${serverEntity.payment.id}")
                        paymentDao.insertPayment(localPayment.copy(syncStatus = SyncStatus.SYNCED))
                    }
                    DateUtils.isUpdateNeeded(
                        serverEntity.payment.updatedAt,
                        existingPayment.updatedAt,
                        "Payment-${existingPayment.id}"
                    ) -> {
                        Log.d(TAG, "Updating existing payment: ${serverEntity.payment.id}")
                        paymentDao.updatePaymentWithTimestamp(
                            localPayment.copy(id = existingPayment.id),
                            SyncStatus.SYNCED
                        )
                    }
                }

                // Handle splits - only process if the split hasn't been marked as deleted locally
                serverEntity.splits.forEach { serverSplit ->
                    val existingSplit = paymentSplitDao.getPaymentSplitByServerId(serverSplit.id)

                    // Only process if split doesn't exist or isn't deleted locally
                    if (existingSplit?.deletedAt == null) {
                        val convertedSplit = myApplication.entityServerConverter
                            .convertPaymentSplitFromServer(serverSplit)
                            .getOrNull() ?: throw Exception("Failed to convert server split")

                        if (existingSplit == null) {
                            // Insert new split
                            paymentSplitDao.insertPaymentSplit(convertedSplit.copy(
                                syncStatus = SyncStatus.SYNCED
                            ))
                        } else if (DateUtils.isUpdateNeeded(serverSplit.updatedAt, existingSplit.updatedAt)) {
                            // Update existing split using withTimestamp method
                            paymentSplitDao.updatePaymentSplitWithTimestamp(
                                convertedSplit.copy(id = existingSplit.id),
                                SyncStatus.SYNCED
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying server changes", e)
                throw e
            }
        }
    }

    private data class EntityCacheItem(
        val payment: PaymentEntity,
        val splits: List<PaymentSplitEntity>
    )

    companion object {
        private const val TAG = "PaymentSyncManager"
        private val entityCache = mutableMapOf<Int, EntityCacheItem>()
    }
}