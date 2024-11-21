package com.splitter.splittr.data.extensions

import com.splitter.splittr.data.local.entities.TransactionEntity
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.data.model.Transaction
import com.splitter.splittr.data.model.TransactionAmount
import com.splitter.splittr.data.model.CreditorAccount

/**
 * Converts a Transaction model to a TransactionEntity for database storage.
 * Extracts amount from transactionAmount if present (GoCardless data)
 * or uses direct amount property (local data).
 */
fun Transaction.toEntity(syncStatus: SyncStatus = SyncStatus.PENDING_SYNC): TransactionEntity {
    // For GoCardless transactions, amount is nested in transactionAmount
    val finalAmount = when {
        transactionAmount != null -> transactionAmount.amount
        else -> amount ?: 0.0 // Fallback for local transactions or missing amount
    }

    val finalCurrency = when {
        transactionAmount != null -> transactionAmount.currency
        else -> currency ?: ""
    }

    return TransactionEntity(
        transactionId = transactionId,
        serverId = null,
        userId = userId,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt,
        accountId = accountId,
        currency = finalCurrency,
        bookingDate = bookingDate,
        valueDate = valueDate,
        bookingDateTime = bookingDateTime,
        amount = finalAmount,
        creditorName = creditorName,
        creditorAccountBban = creditorAccount?.bban,
        debtorName = debtorName,
        remittanceInformationUnstructured = remittanceInformationUnstructured,
        proprietaryBankTransactionCode = proprietaryBankTransactionCode,
        internalTransactionId = internalTransactionId,
        institutionName = institutionName,
        institutionId = institutionId,
        syncStatus = syncStatus
    )
}

/**
 * Converts a TransactionEntity from the database back to a Transaction model.
 * Creates a transactionAmount object for API compatibility when needed.
 */
fun TransactionEntity.toModel(): Transaction {
    return Transaction(
        transactionId = transactionId,
        userId = userId,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt,
        accountId = accountId,
        amount = amount, // Keep direct amount for local use
        currency = currency, // Keep direct currency for local use
        bookingDate = bookingDate,
        valueDate = valueDate,
        bookingDateTime = bookingDateTime,
        // Create transactionAmount for API compatibility
        transactionAmount = TransactionAmount(
            amount = amount,
            currency = currency ?: "GBP"
        ),
        creditorName = creditorName,
        creditorAccount = creditorAccountBban?.let { CreditorAccount(bban = it) },
        debtorName = debtorName,
        remittanceInformationUnstructured = remittanceInformationUnstructured,
        proprietaryBankTransactionCode = proprietaryBankTransactionCode,
        internalTransactionId = internalTransactionId,
        institutionName = institutionName,
        institutionId = institutionId
    )
}

/**
 * Converts a server response to a Transaction model.
 * Handles both GoCardless response format and local server format.
 */
fun Map<String, Any?>.toTransaction(): Transaction {
    // Handle amount from either nested transactionAmount or direct amount property
    val amount = when {
        // Try to get amount from transactionAmount object first
        this["transactionAmount"] is Map<*, *> -> {
            val transAmount = this["transactionAmount"] as Map<*, *>
            (transAmount["amount"] as? Number)?.toDouble()
                ?: (transAmount["amount"] as? String)?.toDoubleOrNull()
        }
        // Fall back to direct amount property
        this["amount"] is Number -> (this["amount"] as Number).toDouble()
        this["amount"] is String -> (this["amount"] as String).toDoubleOrNull()
        else -> null
    } ?: 0.0

    val currency = when {
        this["transactionAmount"] is Map<*, *> -> {
            val transAmount = this["transactionAmount"] as Map<*, *>
            transAmount["currency"] as? String
        }
        else -> this["currency"] as? String
    } ?: ""

    return Transaction(
        transactionId = this["transactionId"] as? String ?: "",
        userId = (this["userId"] as? Number)?.toInt(),
        description = this["description"] as? String,
        createdAt = this["createdAt"] as? String,
        updatedAt = this["updatedAt"] as? String,
        accountId = this["accountId"] as? String,
        amount = amount, // Store direct amount for local use
        currency = currency, // Store direct currency for local use
        bookingDate = this["bookingDate"] as? String,
        valueDate = this["valueDate"] as? String,
        bookingDateTime = this["bookingDateTime"] as? String,
        transactionAmount = TransactionAmount(amount = amount, currency = currency),
        creditorName = this["creditorName"] as? String,
        creditorAccount = (this["creditorAccountBban"] as? String)?.let { CreditorAccount(it) },
        debtorName = this["debtorName"] as? String,
        remittanceInformationUnstructured = this["remittanceInformationUnstructured"] as? String,
        proprietaryBankTransactionCode = this["proprietaryBankTransactionCode"] as? String,
        internalTransactionId = this["internalTransactionId"] as? String,
        institutionName = this["institutionName"] as? String,
        institutionId = this["institutionId"] as? String
    )
}