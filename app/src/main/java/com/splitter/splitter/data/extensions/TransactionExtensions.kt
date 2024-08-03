package com.splitter.splitter.data.extensions

import com.splitter.splitter.data.local.entities.TransactionEntity
import com.splitter.splitter.model.Transaction

fun Transaction.toEntity(): TransactionEntity {
    return TransactionEntity(
        transactionId = transactionId,
        userId = userId,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt,
        accountId = accountId,
        currency = currency,
        bookingDate = bookingDate,
        valueDate = valueDate,
        bookingDateTime = bookingDateTime,
        amount = transactionAmount.amount,
        creditorName = creditorName,
        creditorAccountBban = creditorAccount?.bban,
        debtorName = debtorName,
        remittanceInformationUnstructured = remittanceInformationUnstructured,
        proprietaryBankTransactionCode = proprietaryBankTransactionCode,
        internalTransactionId = internalTransactionId,
        institutionName = institutionName,
        institutionId = institutionId
    )
}


