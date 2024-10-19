package com.splitter.splittr.data.extensions

import com.splitter.splittr.data.local.entities.TransactionEntity
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.model.Transaction
import com.splitter.splittr.model.TransactionAmount
import com.splitter.splittr.model.CreditorAccount

fun Transaction.toEntity(syncStatus: SyncStatus = SyncStatus.PENDING_SYNC): TransactionEntity {
    return TransactionEntity(
        transactionId = this.transactionId,
        userId = this.userId,
        description = this.description,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        accountId = this.accountId,
        currency = this.currency,
        bookingDate = this.bookingDate,
        valueDate = this.valueDate,
        bookingDateTime = this.bookingDateTime,
        amount = this.transactionAmount.amount,
        creditorName = this.creditorName,
        creditorAccountBban = this.creditorAccount?.bban,
        debtorName = this.debtorName,
        remittanceInformationUnstructured = this.remittanceInformationUnstructured,
        proprietaryBankTransactionCode = this.proprietaryBankTransactionCode,
        internalTransactionId = this.internalTransactionId,
        institutionName = this.institutionName,
        institutionId = this.institutionId,
        syncStatus = syncStatus
    )
}

fun TransactionEntity.toModel(): Transaction {
    return Transaction(
        transactionId = this.transactionId,
        userId = this.userId,
        description = this.description,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        accountId = this.accountId,
        currency = this.currency,
        bookingDate = this.bookingDate,
        valueDate = this.valueDate,
        bookingDateTime = this.bookingDateTime,
        transactionAmount = TransactionAmount(
            amount = this.amount,
            currency = this.currency ?: ""
        ),
        creditorName = this.creditorName,
        creditorAccount = this.creditorAccountBban?.let { CreditorAccount(it) },
        debtorName = this.debtorName,
        remittanceInformationUnstructured = this.remittanceInformationUnstructured,
        proprietaryBankTransactionCode = this.proprietaryBankTransactionCode,
        internalTransactionId = this.internalTransactionId,
        institutionName = this.institutionName,
        institutionId = this.institutionId
    )
}