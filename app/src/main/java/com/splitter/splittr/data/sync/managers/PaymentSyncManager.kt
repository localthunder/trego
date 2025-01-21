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
import com.splitter.splittr.data.local.dataClasses.PaymentWithSplitsAndLocalIds
import com.splitter.splittr.data.local.entities.PaymentEntity
import com.splitter.splittr.data.local.entities.PaymentSplitEntity
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
import com.splitter.splittr.utils.ServerIdUtil
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

            // Get local splits but don't convert them yet
            val localSplits = paymentSplitDao.getPaymentSplitsByPayment(paymentEntity.id).first()
            Log.d(TAG, "Found ${localSplits.size} splits for payment ${paymentEntity.id}")

            PaymentWithSplitsAndLocalIds(
                payment = serverPayment,
                splits = emptyList(),  // We'll convert splits after payment is created
                localPaymentId = paymentEntity.id,
                localSplitIds = localSplits.map { it.id }
            )
        }

    override suspend fun syncToServer(entity: PaymentWithSplitsAndLocalIds): Result<PaymentWithSplitsAndLocalIds> = try {
        Log.d(TAG, "Starting payment sync for payment ${entity.localPaymentId}")

        // First create/update the payment on server
        val serverPayment = apiService.createPayment(entity.payment)
        Log.d(TAG, "Payment created on server with ID: ${serverPayment.id}")

        // Update local payment and get server ID mapping
        paymentDao.runInTransaction {
            val localPayment = myApplication.entityServerConverter
                .convertPaymentFromServer(serverPayment)
                .getOrNull() ?: throw Exception("Failed to convert server payment")

            paymentDao.updatePaymentWithTimestamp(
                localPayment.copy(id = entity.localPaymentId),
                SyncStatus.SYNCED
            )

            ServerIdUtil.saveIdMapping(
                localId = entity.localPaymentId,
                serverId = serverPayment.id,
                entityType = "payments",
                context = context
            )
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
                    ?: throw Exception("No server ID found for user ${splitEntity.userId}")

                val updatedByServerId = ServerIdUtil.getServerId(splitEntity.updatedBy, "users", context)
                    ?: throw Exception("No server ID found for user ${splitEntity.userId}")

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

                // Update local split
                val updatedLocalSplit = myApplication.entityServerConverter
                    .convertPaymentSplitFromServer(serverSplit)
                    .getOrNull() ?: throw Exception("Failed to convert server split")

                paymentSplitDao.updatePaymentSplitWithTimestamp(
                    updatedLocalSplit.copy(
                        id = splitEntity.id,
                        userId = splitEntity.userId
                    ),
                    SyncStatus.SYNCED
                )

                ServerIdUtil.saveIdMapping(
                    localId = splitEntity.id,
                    serverId = serverSplit.id,
                    entityType = "payment_splits",
                    context = context
                )

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
                        // Use insertPayment for new payments
                        paymentDao.insertPayment(localPayment.copy(syncStatus = SyncStatus.SYNCED))
                    }
                    DateUtils.isUpdateNeeded(
                        serverEntity.payment.updatedAt,
                        existingPayment.updatedAt,
                        "Payment-${existingPayment.id}"
                    ) -> {
                        Log.d(TAG, "Updating existing payment: ${serverEntity.payment.id}")
                        // Use updatePaymentWithTimestamp for updates
                        paymentDao.updatePaymentWithTimestamp(
                            localPayment.copy(id = existingPayment.id),
                            SyncStatus.SYNCED
                        )
                    }
                }

                // Handle splits
                serverEntity.splits.forEach { serverSplit ->
                    val existingSplit = paymentSplitDao.getPaymentSplitByServerId(serverSplit.id)
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