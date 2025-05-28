package com.helgolabs.trego.data.extensions

import com.helgolabs.trego.data.local.entities.PaymentSplitEntity
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.data.model.PaymentSplit

fun PaymentSplit.toEntity(syncStatus: SyncStatus = SyncStatus.PENDING_SYNC): PaymentSplitEntity {
    return PaymentSplitEntity(
        id = 0,
        serverId = if (this.id == 0) null else this.id,  // Server ID can be null for new splits
        paymentId = this.paymentId,
        userId = this.userId,
        amount = this.amount,
        createdBy = this.createdBy,
        updatedBy = this.updatedBy,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        currency = this.currency,
        percentage = this.percentage,
        deletedAt = this.deletedAt,
        syncStatus = syncStatus
    )
}

fun PaymentSplitEntity.toModel(): PaymentSplit {
    return PaymentSplit(
        id = this.serverId ?: 0,
        paymentId = this.paymentId,
        userId = this.userId,
        amount = this.amount,
        createdBy = this.createdBy,
        updatedBy = this.updatedBy,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        percentage = this.percentage,
        currency = this.currency,
        deletedAt = this.deletedAt
    )
}
