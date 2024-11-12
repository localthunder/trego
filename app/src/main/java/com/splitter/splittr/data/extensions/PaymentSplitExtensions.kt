package com.splitter.splittr.data.extensions

import com.splitter.splittr.data.local.entities.PaymentSplitEntity
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.data.model.PaymentSplit

fun PaymentSplit.toEntity(syncStatus: SyncStatus = SyncStatus.PENDING_SYNC): PaymentSplitEntity {
    return PaymentSplitEntity(
        id = this.id,
        serverId = this.id,
        paymentId = this.paymentId,
        userId = this.userId,
        amount = this.amount,
        createdBy = this.createdBy,
        updatedBy = this.updatedBy,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        currency = this.currency,
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
        currency = this.currency,
        deletedAt = this.deletedAt
    )
}
