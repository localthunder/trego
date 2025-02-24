// file: PaymentExtensions.kt
package com.helgolabs.trego.data.extensions

import com.helgolabs.trego.data.local.entities.PaymentEntity
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.data.model.Payment
import java.time.Instant
import java.time.format.DateTimeFormatter

fun Payment.toEntity(syncStatus: SyncStatus = SyncStatus.PENDING_SYNC): PaymentEntity {
    return PaymentEntity(
        id = 0,
        serverId = if (this.id == 0) null else this.id,  // Server ID can be null for new splits
        groupId = this.groupId,
        paidByUserId = this.paidByUserId,
        transactionId = this.transactionId,
        amount = this.amount,
        description = this.description,
        notes = this.notes,
        paymentDate = this.paymentDate.toString(),
        createdBy = this.createdBy,
        updatedBy = this.updatedBy,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        splitMode = this.splitMode,
        institutionId = this.institutionId,
        paymentType = this.paymentType,
        currency = this.currency,
        deletedAt = this.deletedAt,
        syncStatus = syncStatus
    )
}

fun PaymentEntity.toModel(): Payment {
    return Payment(
        id = this.serverId ?: 0,
        groupId = this.groupId,
        paidByUserId = this.paidByUserId,
        transactionId = this.transactionId,
        amount = this.amount,
        description = this.description,
        notes = this.notes,
        paymentDate = this.paymentDate,
        createdBy = this.createdBy,
        updatedBy = this.updatedBy,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        splitMode = this.splitMode,
        institutionId = this.institutionId,
        paymentType = this.paymentType,
        currency = this.currency,
        deletedAt = this.deletedAt
    )
}