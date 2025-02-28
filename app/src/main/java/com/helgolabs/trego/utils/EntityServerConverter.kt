package com.helgolabs.trego.utils

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import com.helgolabs.trego.data.extensions.toEntity
import com.helgolabs.trego.data.local.dataClasses.CurrencyConversionData
import com.helgolabs.trego.data.local.entities.BankAccountEntity
import com.helgolabs.trego.data.local.entities.CurrencyConversionEntity
import com.helgolabs.trego.data.local.entities.DeviceTokenEntity
import com.helgolabs.trego.data.local.entities.GroupDefaultSplitEntity
import com.helgolabs.trego.data.local.entities.GroupEntity
import com.helgolabs.trego.data.local.entities.GroupMemberEntity
import com.helgolabs.trego.data.local.entities.PaymentEntity
import com.helgolabs.trego.data.local.entities.PaymentSplitEntity
import com.helgolabs.trego.data.local.entities.RequisitionEntity
import com.helgolabs.trego.data.local.entities.TransactionEntity
import com.helgolabs.trego.data.local.entities.UserEntity
import com.helgolabs.trego.data.local.entities.UserGroupArchiveEntity
import com.helgolabs.trego.data.model.BankAccount
import com.helgolabs.trego.data.model.CreditorAccount
import com.helgolabs.trego.data.model.CurrencyConversion
import com.helgolabs.trego.data.model.DeviceToken
import com.helgolabs.trego.data.model.Group
import com.helgolabs.trego.data.model.GroupDefaultSplit
import com.helgolabs.trego.data.model.GroupMember
import com.helgolabs.trego.data.model.Payment
import com.helgolabs.trego.data.model.PaymentSplit
import com.helgolabs.trego.data.model.Requisition
import com.helgolabs.trego.data.model.Transaction
import com.helgolabs.trego.data.model.TransactionAmount
import com.helgolabs.trego.data.model.User
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.utils.ServerIdUtil.getLocalId

//Local Ids are used as foreign keys on the device, when something is passed to the server the local ids need to be
//substituted for the correct server ids. Entity Server Converter does this.
class EntityServerConverter(private val context: Context) {
    suspend fun convertPaymentToServer(payment: PaymentEntity): Result<Payment> {
        return try {
            Log.d("EntityServerConverter", "Starting payment conversion with payment: " +
                    "id=${payment.id}, serverId=${payment.serverId}, " +
                    "groupId=${payment.groupId}, paidByUserId=${payment.paidByUserId}, " +
                    "createdBy=${payment.createdBy}, updatedBy=${payment.updatedBy}")

            // Look up all required server IDs
            val serverGroupId = ServerIdUtil.getServerId(payment.groupId, "groups", context)
            Log.d("EntityServerConverter", "Got serverGroupId: $serverGroupId for local groupId: ${payment.groupId}")

            serverGroupId ?: return Result.failure(Exception("No server ID found for group ${payment.groupId}"))

            val serverPaidByUserId = ServerIdUtil.getServerId(payment.paidByUserId, "users", context)
            Log.d("EntityServerConverter", "Got serverPaidByUserId: $serverPaidByUserId for local userId: ${payment.paidByUserId}")

            serverPaidByUserId ?: return Result.failure(Exception("No server ID found for paid by user ${payment.paidByUserId}"))

            val serverCreatedById = ServerIdUtil.getServerId(payment.createdBy, "users", context)
            Log.d("EntityServerConverter", "Got serverCreatedById: $serverCreatedById for local createdBy: ${payment.createdBy}")

            serverCreatedById ?: return Result.failure(Exception("No server ID found for created by user ${payment.createdBy}"))

            val serverUpdatedById = ServerIdUtil.getServerId(payment.updatedBy, "users", context)
            Log.d("EntityServerConverter", "Got serverUpdatedById: $serverUpdatedById for local updatedBy: ${payment.updatedBy}")

            serverUpdatedById ?: return Result.failure(Exception("No server ID found for updated by user ${payment.updatedBy}"))

            // Create server model with all server IDs
            val serverPayment = Payment(
                id = payment.serverId ?: 0,
                groupId = serverGroupId,
                paidByUserId = serverPaidByUserId,
                transactionId = payment.transactionId,
                amount = payment.amount,
                description = payment.description,
                notes = payment.notes,
                paymentDate = payment.paymentDate,
                createdBy = serverCreatedById,
                updatedBy = serverUpdatedById,
                createdAt = payment.createdAt,
                updatedAt = payment.updatedAt,
                splitMode = payment.splitMode,
                paymentType = payment.paymentType,
                currency = payment.currency,
                deletedAt = payment.deletedAt,
                institutionId = payment.institutionId,
            )

            Log.d("EntityServerConverter", "Successfully created server payment model: " +
                    "id=${serverPayment.id}, groupId=${serverPayment.groupId}, " +
                    "paidByUserId=${serverPayment.paidByUserId}, " +
                    "createdBy=${serverPayment.createdBy}, updatedBy=${serverPayment.updatedBy}")

            Result.success(serverPayment)
        } catch (e: Exception) {
            Log.e("EntityServerConverter", "Error converting payment to server model", e)
            Log.e("EntityServerConverter", "Failed payment details: " +
                    "id=${payment.id}, serverId=${payment.serverId}, " +
                    "groupId=${payment.groupId}, paidByUserId=${payment.paidByUserId}, " +
                    "createdBy=${payment.createdBy}, updatedBy=${payment.updatedBy}")
            Result.failure(e)
        }
    }

    suspend fun convertPaymentFromServer(
        serverPayment: Payment,
        existingPayment: PaymentEntity? = null
    ): Result<PaymentEntity> {
        return try {
            Log.d(TAG, "Converting server payment: id=${serverPayment.id} to local entity")

            // Get local IDs
            val localPaidByUserId = getLocalId(serverPayment.paidByUserId, "users", context)
                ?: existingPayment?.paidByUserId
                ?: return Result.failure(Exception("Could not resolve local user ID for ${serverPayment.paidByUserId}"))

            val localCreatedByUserId = getLocalId(serverPayment.createdBy, "users", context)
                ?: existingPayment?.createdBy
                ?: return Result.failure(Exception("Could not resolve local user ID for ${serverPayment.createdBy}"))

            val localUpdatedByUserId = getLocalId(serverPayment.updatedBy, "users", context)
                ?: existingPayment?.updatedBy
                ?: return Result.failure(Exception("Could not resolve local user ID for ${serverPayment.updatedBy}"))

            val localGroupId = getLocalId(serverPayment.groupId, "groups", context)
                ?: existingPayment?.groupId
                ?: return Result.failure(Exception("Could not resolve local group ID for ${serverPayment.groupId}"))

            // Create the entity with all correct IDs from the start
            val entity = PaymentEntity(
                id = existingPayment?.id ?: 0,
                serverId = serverPayment.id,
                groupId = localGroupId,
                paidByUserId = localPaidByUserId,
                transactionId = serverPayment.transactionId,
                amount = serverPayment.amount,
                description = serverPayment.description,
                notes = serverPayment.notes,
                paymentDate = serverPayment.paymentDate,
                createdBy = localCreatedByUserId,
                updatedBy = localUpdatedByUserId,
                createdAt = serverPayment.createdAt,
                updatedAt = serverPayment.updatedAt,
                splitMode = serverPayment.splitMode,
                institutionId = serverPayment.institutionId,
                paymentType = serverPayment.paymentType,
                currency = serverPayment.currency,
                deletedAt = serverPayment.deletedAt,
                syncStatus = SyncStatus.SYNCED
            )

            Result.success(entity)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting server payment to local entity", e)
            Result.failure(e)
        }
    }

    fun convertGroupToServer(group: GroupEntity): Result<Group> {
        return try {

            Result.success(Group(
                id = group.serverId ?: 0,
                name = group.name,
                description = group.description,
                groupImg = group.groupImg,
                createdAt = group.createdAt,
                updatedAt = group.updatedAt,
                defaultCurrency = group.defaultCurrency,
                inviteLink = group.inviteLink,
                defaultSplitMode = group.defaultSplitMode
            ))
        } catch (e: Exception) {
            Log.e("EntityServerConverter", "Error converting group member to server model", e)
            Result.failure(e)
        }
    }

    suspend fun convertGroupFromServer(
        serverGroup: Group,
        existingGroup: GroupEntity? = null
    ): Result<GroupEntity> {
        return try {
            Result.success(GroupEntity(
                id = existingGroup?.id ?: 0,
                serverId = serverGroup.id,
                name = serverGroup.name,
                description = serverGroup.description,
                groupImg = serverGroup.groupImg,
                localImagePath = existingGroup?.localImagePath,
                imageLastModified = existingGroup?.imageLastModified,
                createdAt = serverGroup.createdAt,
                updatedAt = serverGroup.updatedAt,
                inviteLink = serverGroup.inviteLink,
                defaultCurrency = serverGroup.defaultCurrency,
                defaultSplitMode = serverGroup.defaultSplitMode,
                syncStatus = SyncStatus.SYNCED,
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error converting server group to local entity", e)
            Result.failure(e)
        }
    }

    suspend fun convertGroupMemberToServer(groupMember: GroupMemberEntity): Result<GroupMember> {
        return try {
            val serverGroupId = ServerIdUtil.getServerId(groupMember.groupId, "groups", context)
                ?: return Result.failure(Exception("No server ID found for group ${groupMember.groupId}"))

            val serverUserId = ServerIdUtil.getServerId(groupMember.userId, "users", context)
                ?: return Result.failure(Exception("No server ID found for user ${groupMember.userId}"))

            Result.success(GroupMember(
                id = groupMember.serverId ?: 0,
                groupId = serverGroupId,
                userId = serverUserId,
                createdAt = groupMember.createdAt,
                updatedAt = groupMember.updatedAt,
                removedAt = groupMember.removedAt
            ))
        } catch (e: Exception) {
            Log.e("EntityServerConverter", "Error converting group member to server model", e)
            Result.failure(e)
        }
    }

    suspend fun convertGroupMemberFromServer(
        serverMember: GroupMember,
        existingMember: GroupMemberEntity? = null
    ): Result<GroupMemberEntity> {
        return try {
            Log.d(TAG, "Converting server group member: id=${serverMember.id} to local entity")

            // Get local IDs
            val localGroupId = ServerIdUtil.getLocalId(serverMember.groupId, "groups", context)
                ?: existingMember?.groupId
                ?: return Result.failure(Exception("Could not resolve local group ID for ${serverMember.groupId}"))

            val localUserId = ServerIdUtil.getLocalId(serverMember.userId, "users", context)
                ?: existingMember?.userId
                ?: return Result.failure(Exception("Could not resolve local user ID for ${serverMember.userId}"))

            if (existingMember == null) {
                // This is a new entry - create with generated local ID
                Log.d(TAG, "Creating new local entity for server member ${serverMember.id}")
                Result.success(
                    GroupMemberEntity(
                        id = 0,  // Let Room generate a new local ID
                        serverId = serverMember.id,
                        groupId = localGroupId,
                        userId = localUserId,
                        createdAt = serverMember.createdAt,
                        updatedAt = serverMember.updatedAt,
                        removedAt = serverMember.removedAt,
                        syncStatus = SyncStatus.SYNCED
                    )
                )
            } else {
                // This is an update - preserve existing local ID
                Log.d(
                    TAG,
                    "Updating existing local entity ${existingMember.id} with server member ${serverMember.id}"
                )
                Result.success(
                    existingMember.copy(
                        serverId = serverMember.id,
                        groupId = localGroupId,
                        userId = localUserId,
                        createdAt = serverMember.createdAt,
                        updatedAt = serverMember.updatedAt,
                        removedAt = serverMember.removedAt,
                        syncStatus = SyncStatus.SYNCED
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting server group member to local entity", e)
            Result.failure(e)
        }
    }

    suspend fun convertBankAccountToServer(bankAccount: BankAccountEntity): Result<BankAccount> {
        return try {
            val serverUserId = ServerIdUtil.getServerId(bankAccount.userId, "users", context)
                ?: return Result.failure(Exception("No server ID found for user ${bankAccount.userId}"))

            Result.success(
                BankAccount(
                accountId = bankAccount.accountId,
                requisitionId = bankAccount.requisitionId,  // Usually already a server ID
                userId = serverUserId,
                iban = bankAccount.iban,
                institutionId = bankAccount.institutionId,
                currency = bankAccount.currency,
                ownerName = bankAccount.ownerName,
                name = bankAccount.name,
                product = bankAccount.product,
                cashAccountType = bankAccount.cashAccountType,
                createdAt = bankAccount.createdAt,
                updatedAt = bankAccount.updatedAt,
                needsReauthentication = bankAccount.needsReauthentication
            )
            )
        } catch (e: Exception) {
            Log.e("EntityServerConverter", "Error converting bank account to server model", e)
            Result.failure(e)
        }
    }

    suspend fun convertBankAccountFromServer(
        serverAccount: BankAccount,
        existingAccount: BankAccountEntity? = null
    ): Result<BankAccountEntity> {
        return try {
            // Get local user ID
            val localUserId = ServerIdUtil.getLocalId(serverAccount.userId, "users", context)
                ?: existingAccount?.userId
                ?: return Result.failure(Exception("Could not resolve local user ID for ${serverAccount.userId}"))

            Result.success(BankAccountEntity(
                accountId = serverAccount.accountId,
                serverId = serverAccount.accountId, // Bank accounts use accountId as both local and server ID
                requisitionId = serverAccount.requisitionId,
                userId = localUserId,
                iban = serverAccount.iban,
                institutionId = serverAccount.institutionId,
                currency = serverAccount.currency,
                ownerName = serverAccount.ownerName,
                name = serverAccount.name,
                product = serverAccount.product,
                cashAccountType = serverAccount.cashAccountType,
                createdAt = serverAccount.createdAt,
                updatedAt = serverAccount.updatedAt,
                syncStatus = SyncStatus.SYNCED,
                needsReauthentication = existingAccount?.needsReauthentication
                    ?: serverAccount.needsReauthentication,
                deletedAt = null
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error converting server bank account to local entity", e)
            Result.failure(e)
        }
    }

    suspend fun convertUserToServer(
        user: UserEntity,
        emailOverride: String? = null  // Add optional parameter
    ): Result<User> {
        return try {
            Result.success(
                User(
                    userId = user.serverId ?: 0,
                    username = user.username,
                    email = emailOverride ?: user.email,  // Use override if provided
                    passwordHash = null,
                    googleId = user.googleId,
                    appleId = user.appleId,
                    createdAt = user.createdAt,
                    updatedAt = user.updatedAt,
                    defaultCurrency = user.defaultCurrency,
                    lastLoginDate = user.lastLoginDate,
                    isProvisional = user.isProvisional,
                    invitedBy = user.invitedBy,
                    invitationEmail = user.invitationEmail,
                    mergedIntoUserId = user.mergedIntoUserId,
                    passwordResetExpires = user.passwordResetExpires,
                    passwordResetToken = user.passwordResetToken,
                    lastPasswordChange = user.lastPasswordChange
                )
            )
        } catch (e: Exception) {
            Log.e("EntityServerConverter", "Error converting user to server model", e)
            Result.failure(e)
        }
    }

    suspend fun convertUserFromServer(
        serverUser: User,
        existingUser: UserEntity? = null,
        isCurrentUser: Boolean = false
    ): Result<UserEntity> {
        return try {
            Result.success(UserEntity(
                userId = existingUser?.userId ?: 0,
                serverId = serverUser.userId,
                username = serverUser.username,
                email = serverUser.email,
                passwordHash = if (isCurrentUser) existingUser?.passwordHash else null,
                googleId = if (isCurrentUser) existingUser?.googleId else null,
                appleId = if (isCurrentUser) existingUser?.appleId else null,
                createdAt = serverUser.createdAt,
                updatedAt = serverUser.updatedAt,
                defaultCurrency = serverUser.defaultCurrency ?: "GBP",
                lastLoginDate = if (isCurrentUser) existingUser?.lastLoginDate else null,
                syncStatus = SyncStatus.SYNCED,
                isProvisional = serverUser.isProvisional,
                invitedBy = serverUser.invitedBy,
                invitationEmail = serverUser.invitationEmail,
                mergedIntoUserId = serverUser.mergedIntoUserId,
                passwordResetExpires = serverUser.passwordResetExpires,
                passwordResetToken = serverUser.passwordResetToken,
                lastPasswordChange = serverUser.lastPasswordChange
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error converting server user to local entity", e)
            Result.failure(e)
        }
    }

    suspend fun convertPaymentSplitToServer(paymentSplit: PaymentSplitEntity): Result<PaymentSplit> {
        return try {
            val serverPaymentId = ServerIdUtil.getServerId(paymentSplit.paymentId, "payments", context)
                ?: return Result.failure(Exception("No server ID found for payment ${paymentSplit.paymentId}"))

            val serverUserId = ServerIdUtil.getServerId(paymentSplit.userId, "users", context)
                ?: return Result.failure(Exception("No server ID found for user ${paymentSplit.userId}"))

            val serverCreatedById = ServerIdUtil.getServerId(paymentSplit.createdBy, "users", context)
                ?: return Result.failure(Exception("No server ID found for created by user ${paymentSplit.createdBy}"))

            val serverUpdatedById = ServerIdUtil.getServerId(paymentSplit.updatedBy, "users", context)
                ?: return Result.failure(Exception("No server ID found for updated by user ${paymentSplit.updatedBy}"))

            Result.success(
                PaymentSplit(
                    id = paymentSplit.serverId ?: 0,
                    paymentId = serverPaymentId,
                    userId = serverUserId,
                    amount = paymentSplit.amount,
                    currency = paymentSplit.currency,
                    createdBy = serverCreatedById,
                    updatedBy = serverUpdatedById,
                    createdAt = paymentSplit.createdAt,
                    updatedAt = paymentSplit.updatedAt,
                    percentage = paymentSplit.percentage,
                    deletedAt = paymentSplit.deletedAt
            )
            )
        } catch (e: Exception) {
            Log.e("EntityServerConverter", "Error converting payment split to server model", e)
            Result.failure(e)
        }
    }

    suspend fun convertPaymentSplitFromServer(
        serverSplit: PaymentSplit,
        existingSplit: PaymentSplitEntity? = null
    ): Result<PaymentSplitEntity> {
        return try {
            Log.d(TAG, "Converting server payment split: id=${serverSplit.id} to local entity with user ID ${serverSplit.userId}")

            // Modified ID lookups - specifically looking up by server IDs
            val localUserId = ServerIdUtil.getLocalId(serverSplit.userId, "users", context)
                ?: existingSplit?.userId
                ?: run {
                    Log.e(TAG, "Failed to resolve local user ID for server ID ${serverSplit.userId}")
                    return Result.failure(Exception("Could not resolve local user ID for ${serverSplit.userId}"))
                }

            val localPaymentId = ServerIdUtil.getLocalId(serverSplit.paymentId, "payments", context)
                ?: existingSplit?.paymentId
                ?: run {
                    Log.e(TAG, "Failed to resolve local payment ID for server ID ${serverSplit.paymentId}")
                    return Result.failure(Exception("Could not resolve local payment ID for ${serverSplit.paymentId}"))
                }

            // Similarly for created/updated by IDs
            val localCreatedByUserId = ServerIdUtil.getLocalId(serverSplit.createdBy, "users", context)
                ?: existingSplit?.createdBy
                ?: run {
                    Log.e(TAG, "Failed to resolve local created by ID for server ID ${serverSplit.createdBy}")
                    return Result.failure(Exception("Could not resolve local user ID for ${serverSplit.createdBy}"))
                }

            val localUpdatedByUserId = ServerIdUtil.getLocalId(serverSplit.updatedBy, "users", context)
                ?: existingSplit?.updatedBy
                ?: run {
                    Log.e(TAG, "Failed to resolve local updated by ID for server ID ${serverSplit.updatedBy}")
                    return Result.failure(Exception("Could not resolve local user ID for ${serverSplit.updatedBy}"))
                }

            Log.d(TAG, "Successfully converted IDs: server user ${serverSplit.userId} -> local $localUserId")

            Result.success(PaymentSplitEntity(
                id = existingSplit?.id ?: 0,
                serverId = serverSplit.id,
                paymentId = localPaymentId,
                userId = localUserId,
                amount = serverSplit.amount,
                currency = serverSplit.currency,
                createdBy = localCreatedByUserId,
                updatedBy = localUpdatedByUserId,
                createdAt = serverSplit.createdAt,
                updatedAt = serverSplit.updatedAt,
                percentage = serverSplit.percentage,
                deletedAt = serverSplit.deletedAt,
                syncStatus = SyncStatus.SYNCED
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error converting server payment split to local entity", e)
            Result.failure(e)
        }
    }

    suspend fun convertRequisitionToServer(requisition: RequisitionEntity): Result<Requisition> {
        return try {
            val serverUserId = ServerIdUtil.getServerId(requisition.userId, "users", context)
                ?: return Result.failure(Exception("No server ID found for user ${requisition.userId}"))

            Result.success(
                Requisition(
                    requisitionId = requisition.requisitionId,
                    userId = serverUserId,
                    institutionId = requisition.institutionId,
                    reference = requisition.reference,
                    createdAt = requisition.createdAt,
                    updatedAt = requisition.updatedAt
                )
            )
        } catch (e: Exception) {
            Log.e("EntityServerConverter", "Error converting requisition to server model", e)
            Result.failure(e)
        }
    }

    suspend fun convertRequisitionFromServer(
        serverRequisition: Requisition,
        existingRequisition: RequisitionEntity? = null
    ): Result<RequisitionEntity> {
        return try {
            // Get local user ID
            val localUserId = ServerIdUtil.getLocalId(serverRequisition.userId, "users", context)
                ?: existingRequisition?.userId
                ?: return Result.failure(Exception("Could not resolve local user ID for ${serverRequisition.userId}"))

            Result.success(RequisitionEntity(
                requisitionId = serverRequisition.requisitionId,
                userId = localUserId,
                institutionId = serverRequisition.institutionId,
                reference = serverRequisition.reference,
                createdAt = serverRequisition.createdAt,
                updatedAt = serverRequisition.updatedAt,
                syncStatus = SyncStatus.SYNCED
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error converting server requisition to local entity", e)
            Result.failure(e)
        }
    }

    suspend fun convertTransactionToServer(transaction: TransactionEntity): Result<Transaction> {
        return try {
            val serverUserId = transaction.userId?.let { ServerIdUtil.getServerId(it, "users", context) }
                ?: return Result.failure(Exception("No server ID found for user ${transaction.userId}"))

            Result.success(
                Transaction(
                    transactionId = transaction.transactionId,
                    userId = serverUserId,
                    description = transaction.description,
                    createdAt = transaction.createdAt,
                    updatedAt = transaction.updatedAt,
                    accountId = transaction.accountId,
                    amount = transaction.amount,
                    currency = transaction.currency,
                    bookingDate = transaction.bookingDate,
                    valueDate = transaction.valueDate,
                    bookingDateTime = transaction.bookingDateTime,
                    transactionAmount = TransactionAmount(
                        amount = transaction.amount,
                        currency = transaction.currency ?: "GBP"
                    ),
                    creditorAccount = CreditorAccount(
                        transaction.creditorAccountBban,
                    ),
                    creditorName = transaction.creditorName,
                    debtorName = transaction.debtorName,
                    remittanceInformationUnstructured = transaction.remittanceInformationUnstructured,
                    proprietaryBankTransactionCode = transaction.proprietaryBankTransactionCode,
                    internalTransactionId = transaction.internalTransactionId,
                    institutionName = transaction.institutionName,
                    institutionId = transaction.institutionId,
                    transactionStatus = transaction.transactionStatus
                    )
            )
        } catch (e: Exception) {
            Log.e("EntityServerConverter", "Error converting requisition to server model", e)
            Result.failure(e)
        }
    }

    suspend fun convertTransactionFromServer(
        serverTransaction: Transaction,
        existingTransaction: TransactionEntity? = null
    ): Result<TransactionEntity> {
        return try {
            // Get local user ID
            val localUserId = serverTransaction.userId?.let {
                ServerIdUtil.getLocalId(it, "users", context)
                    ?: existingTransaction?.userId
            } ?: return Result.failure(Exception("Could not resolve local user ID"))

            Result.success(TransactionEntity(
                transactionId = serverTransaction.transactionId,
                userId = localUserId,
                description = serverTransaction.description,
                createdAt = serverTransaction.createdAt,
                updatedAt = serverTransaction.updatedAt,
                accountId = serverTransaction.accountId,
                amount = serverTransaction.getEffectiveAmount(),
                currency = serverTransaction.getEffectiveCurrency(),
                bookingDate = serverTransaction.bookingDate,
                valueDate = serverTransaction.valueDate,
                bookingDateTime = serverTransaction.bookingDateTime,
                creditorName = serverTransaction.creditorName,
                creditorAccountBban = serverTransaction.creditorAccount?.bban,
                debtorName = serverTransaction.debtorName,
                remittanceInformationUnstructured = serverTransaction.remittanceInformationUnstructured,
                proprietaryBankTransactionCode = serverTransaction.proprietaryBankTransactionCode,
                internalTransactionId = serverTransaction.internalTransactionId,
                institutionName = serverTransaction.institutionName,
                institutionId = serverTransaction.institutionId,
                syncStatus = SyncStatus.SYNCED,
                transactionStatus = serverTransaction.transactionStatus

            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error converting server transaction to local entity", e)
            Result.failure(e)
        }
    }

    suspend fun convertUserGroupArchiveToServer(archive: UserGroupArchiveEntity): Result<Map<String, Any>> {
        return try {
            val serverUserId = ServerIdUtil.getServerId(archive.userId, "users", context)
                ?: return Result.failure(Exception("No server ID found for user ${archive.userId}"))

            val serverGroupId = ServerIdUtil.getServerId(archive.groupId, "groups", context)
                ?: return Result.failure(Exception("No server ID found for group ${archive.groupId}"))

            // Return as Map since there's no specific server model class, just the DB table
            Result.success(mapOf(
                "user_id" to serverUserId,
                "group_id" to serverGroupId,
                "archived_at" to archive.archivedAt
            ))
        } catch (e: Exception) {
            Log.e("EntityServerConverter", "Error converting user group archive to server model", e)
            Result.failure(e)
        }
    }

    suspend fun convertUserGroupArchiveFromServer(
        serverArchive: Map<String, Any>,
        existingArchive: UserGroupArchiveEntity? = null
    ): Result<UserGroupArchiveEntity> {
        return try {
            // Handle both Int and Double values for IDs
            val serverUserId = when (val userId = serverArchive["user_id"]) {
                is Int -> userId
                is Double -> userId.toInt()
                else -> return Result.failure(Exception("Server archive missing or invalid user_id"))
            }

            val serverGroupId = when (val groupId = serverArchive["group_id"]) {
                is Int -> groupId
                is Double -> groupId.toInt()
                else -> return Result.failure(Exception("Server archive missing or invalid group_id"))
            }

            val localUserId = ServerIdUtil.getLocalId(serverUserId, "users", context)
                ?: existingArchive?.userId
                ?: return Result.failure(Exception("Could not resolve local user ID for $serverUserId"))

            val localGroupId = ServerIdUtil.getLocalId(serverGroupId, "groups", context)
                ?: existingArchive?.groupId
                ?: return Result.failure(Exception("Could not resolve local group ID for $serverGroupId"))

            Result.success(
                UserGroupArchiveEntity(
                    userId = localUserId,
                    groupId = localGroupId,
                    archivedAt = serverArchive["archived_at"] as String,
                    syncStatus = SyncStatus.SYNCED
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error converting server user group archive to local entity", e)
            Result.failure(e)
        }
    }

    suspend fun convertCurrencyConversionToServer(
        conversion: CurrencyConversionEntity
    ): Result<CurrencyConversion> {
        return try {
            val serverPaymentId = ServerIdUtil.getServerId(conversion.paymentId, "payments", context)
                ?: return Result.failure(Exception("No server ID found for payment ${conversion.paymentId}"))

            val serverCreatedById = ServerIdUtil.getServerId(conversion.createdBy, "users", context)
                ?: return Result.failure(Exception("No server ID found for user ${conversion.createdBy}"))

            val serverUpdatedById = ServerIdUtil.getServerId(conversion.updatedBy, "users", context)
                ?: return Result.failure(Exception("No server ID found for user ${conversion.updatedBy}"))

            Result.success(CurrencyConversion(
                id = conversion.serverId ?: 0,
                paymentId = serverPaymentId,
                originalCurrency = conversion.originalCurrency,
                originalAmount = conversion.originalAmount,
                finalCurrency = conversion.finalCurrency,
                finalAmount = conversion.finalAmount,
                exchangeRate = conversion.exchangeRate,
                source = conversion.source,
                createdBy = serverCreatedById,
                updatedBy = serverUpdatedById,
                createdAt = conversion.createdAt,
                updatedAt = conversion.updatedAt
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun convertCurrencyConversionFromServer(
        serverData: CurrencyConversionData,
        existingConversion: CurrencyConversionEntity? = null
    ): Result<CurrencyConversionEntity> {
        return try {

            val serverConversion = serverData.currencyConversion

            val localPaymentId = getLocalId(serverConversion.paymentId, "payments", context)
            Log.d(TAG, "Resolved local payment ID: $localPaymentId (existing: ${existingConversion?.paymentId})")

            if (localPaymentId == null && existingConversion?.paymentId == null) {
                Log.e(TAG, "Failed to resolve local payment ID for server payment ${serverConversion.paymentId}")
                return Result.failure(Exception("Could not resolve local payment ID"))
            }

            val localCreatedById = getLocalId(serverConversion.createdBy, "users", context)
            Log.d(TAG, "Resolved local created by ID: $localCreatedById (existing: ${existingConversion?.createdBy})")

            if (localCreatedById == null && existingConversion?.createdBy == null) {
                Log.e(TAG, "Failed to resolve local user ID for created by ${serverConversion.createdBy}")
                return Result.failure(Exception("Could not resolve local user ID"))
            }

            val localUpdatedById = getLocalId(serverConversion.updatedBy, "users", context)
            Log.d(TAG, "Resolved local updated by ID: $localUpdatedById (existing: ${existingConversion?.updatedBy})")

            if (localUpdatedById == null && existingConversion?.updatedBy == null) {
                Log.e(TAG, "Failed to resolve local user ID for updated by ${serverConversion.updatedBy}")
                return Result.failure(Exception("Could not resolve local user ID"))
            }

            val finalLocalPaymentId = localPaymentId ?: existingConversion?.paymentId!!
            val finalLocalCreatedById = localCreatedById ?: existingConversion?.createdBy!!
            val finalLocalUpdatedById = localUpdatedById ?: existingConversion?.updatedBy!!


            val localEntity = CurrencyConversionEntity(
                id = existingConversion?.id ?: 0,
                serverId = serverConversion.id,
                paymentId = finalLocalPaymentId,
                originalCurrency = serverConversion.originalCurrency,
                originalAmount = serverConversion.originalAmount,
                finalCurrency = serverConversion.finalCurrency,
                finalAmount = serverConversion.finalAmount,
                exchangeRate = serverConversion.exchangeRate,
                source = serverConversion.source,
                createdBy = finalLocalCreatedById,
                updatedBy = finalLocalUpdatedById,
                createdAt = serverConversion.createdAt,
                updatedAt = serverConversion.updatedAt,
                syncStatus = SyncStatus.SYNCED
            )

            Result.success(localEntity)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting currency conversion from server", e)
            Result.failure(e)
        }
    }
    suspend fun convertDeviceTokenToServer(deviceToken: DeviceTokenEntity): Result<DeviceToken> {
        return try {
            val serverUserId = ServerIdUtil.getServerId(deviceToken.userId, "users", context)
                ?: return Result.failure(Exception("No server ID found for user ${deviceToken.userId}"))

            Result.success(DeviceToken(
                tokenId = deviceToken.tokenId,
                userId = serverUserId,
                fcmToken = deviceToken.fcmToken,
                deviceType = deviceToken.deviceType,
                createdAt = deviceToken.createdAt,
                updatedAt = deviceToken.updatedAt
            ))
        } catch (e: Exception) {
            Log.e("EntityServerConverter", "Error converting device token to server model", e)
            Result.failure(e)
        }
    }

    suspend fun convertDeviceTokenFromServer(
        serverToken: DeviceToken,
        existingToken: DeviceTokenEntity? = null
    ): Result<DeviceTokenEntity> {
        return try {
            // Get local user ID
            val localUserId = getLocalId(serverToken.userId, "users", context)
                ?: existingToken?.userId
                ?: return Result.failure(Exception("Could not resolve local user ID for ${serverToken.userId}"))

            Result.success(DeviceTokenEntity(
                tokenId = serverToken.tokenId,
                userId = localUserId,
                fcmToken = serverToken.fcmToken,
                deviceType = serverToken.deviceType,
                createdAt = serverToken.createdAt,
                updatedAt = serverToken.updatedAt,
                syncStatus = SyncStatus.SYNCED
            ))
        } catch (e: Exception) {
            Log.e("EntityServerConverter", "Error converting server device token to local entity", e)
            Result.failure(e)
        }
    }

    suspend fun convertGroupDefaultSplitToServer(defaultSplit: GroupDefaultSplitEntity): Result<GroupDefaultSplit> {
        return try {
            val serverGroupId = ServerIdUtil.getServerId(defaultSplit.groupId, "groups", context)
                ?: return Result.failure(Exception("No server ID found for group ${defaultSplit.groupId}"))

            val serverUserId = ServerIdUtil.getServerId(defaultSplit.userId, "users", context)
                ?: return Result.failure(Exception("No server ID found for user ${defaultSplit.userId}"))

            Result.success(GroupDefaultSplit(
                id = defaultSplit.serverId ?: 0,
                groupId = serverGroupId,
                userId = serverUserId,
                percentage = defaultSplit.percentage,
                createdAt = defaultSplit.createdAt,
                updatedAt = defaultSplit.updatedAt
            ))
        } catch (e: Exception) {
            Log.e("EntityServerConverter", "Error converting group default split to server model", e)
            Result.failure(e)
        }
    }

    suspend fun convertGroupDefaultSplitFromServer(
        serverSplit: GroupDefaultSplit,
        existingSplit: GroupDefaultSplitEntity? = null
    ): Result<GroupDefaultSplitEntity> {
        return try {
            // Get local group ID
            val localGroupId = getLocalId(serverSplit.groupId, "groups", context)
                ?: existingSplit?.groupId
                ?: return Result.failure(Exception("Could not resolve local group ID for ${serverSplit.groupId}"))

            // Get local user ID
            val localUserId = ServerIdUtil.getLocalId(serverSplit.userId, "users", context)
                ?: existingSplit?.userId
                ?: return Result.failure(Exception("Could not resolve local user ID for ${serverSplit.userId}"))

            Result.success(GroupDefaultSplitEntity(
                id = existingSplit?.id ?: 0,
                serverId = serverSplit.id,
                groupId = localGroupId,
                userId = localUserId,
                percentage = serverSplit.percentage,
                createdAt = serverSplit.createdAt,
                updatedAt = serverSplit.updatedAt,
                removedAt = null,
                syncStatus = SyncStatus.SYNCED
            ))
        } catch (e: Exception) {
            Log.e("EntityServerConverter", "Error converting server group default split to local entity", e)
            Result.failure(e)
        }
    }
}