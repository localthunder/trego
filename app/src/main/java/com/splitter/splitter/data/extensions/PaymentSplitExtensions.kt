package com.splitter.splitter.data.extensions

import com.splitter.splitter.data.local.entities.PaymentSplitEntity
import com.splitter.splitter.model.PaymentSplit

fun PaymentSplit.toEntity(): PaymentSplitEntity {
    return PaymentSplitEntity(
        id = this.id,
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

fun PaymentSplitEntity.toModel(): PaymentSplit {
    return PaymentSplit(
        id = this.id,
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
