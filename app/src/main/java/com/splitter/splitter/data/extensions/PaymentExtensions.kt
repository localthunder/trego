// file: PaymentExtensions.kt
package com.splitter.splitter.data.extensions

import com.splitter.splitter.data.local.entities.PaymentEntity
import com.splitter.splitter.model.Payment

fun Payment.toEntity(): PaymentEntity {
    return PaymentEntity(
        id = this.id,
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
        institutionName = this.institutionName,
        paymentType = this.paymentType,
        currency = this.currency,
        deletedAt = this.deletedAt
    )
}

fun PaymentEntity.toModel(): Payment {
    return Payment(
        id = this.id,
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
        institutionName = this.institutionName,
        paymentType = this.paymentType,
        currency = this.currency,
        deletedAt = this.deletedAt
    )
}
