package com.helgolabs.trego.data.extensions

import com.google.gson.Gson
import com.helgolabs.trego.data.local.dataClasses.TransactionStatus
import com.helgolabs.trego.data.local.entities.CachedTransactionEntity
import com.helgolabs.trego.data.local.entities.TransactionEntity
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.data.model.Transaction
import com.helgolabs.trego.data.model.TransactionAmount
import com.helgolabs.trego.data.model.CreditorAccount
import com.helgolabs.trego.utils.DateUtils

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
        createdAt = createdAt ?: DateUtils.getCurrentTimestamp(),  // Ensure timestamps
        updatedAt = updatedAt ?: DateUtils.getCurrentTimestamp(),  // are not null
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
        syncStatus = syncStatus,
        transactionStatus = transactionStatus ?: TransactionStatus.BOOKED  // Ensure status is not null
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
        institutionId = institutionId,
        transactionStatus = transactionStatus
    )
}

///**
// * Converts a server response to a Transaction model.
// * Handles both GoCardless response format and local server format.
// */
//fun Map<String, Any?>.toTransaction(): Transaction {
//    // Handle amount from either nested transactionAmount or direct amount property
//    val amount = when {
//        this["transactionAmount"] is Map<*, *> -> {
//            val transAmount = this["transactionAmount"] as Map<*, *>
//            (transAmount["amount"] as? Number)?.toDouble()
//                ?: (transAmount["amount"] as? String)?.toDoubleOrNull()
//        }
//        this["amount"] is Number -> (this["amount"] as Number).toDouble()
//        this["amount"] is String -> (this["amount"] as String).toDoubleOrNull()
//        else -> null
//    } ?: 0.0
//
//    val currency = when {
//        this["transactionAmount"] is Map<*, *> -> {
//            val transAmount = this["transactionAmount"] as Map<*, *>
//            transAmount["currency"] as? String
//        }
//        else -> this["currency"] as? String
//    } ?: ""
//
//    // Handle transaction status
//    val status = when (this["transactionStatus"] as? String?.uppercase()) {
//        "PENDING" -> TransactionStatus.PENDING
//        "BOOKED" -> TransactionStatus.BOOKED
//        else -> TransactionStatus.BOOKED // Default to BOOKED if not specified
//    }
//
//    return Transaction(
//        transactionId = this["transactionId"] as? String ?: "",
//        userId = (this["userId"] as? Number)?.toInt(),
//        description = this["description"] as? String,
//        createdAt = this["createdAt"] as? String ?: DateUtils.getCurrentTimestamp(),
//        updatedAt = this["updatedAt"] as? String ?: DateUtils.getCurrentTimestamp(),
//        accountId = this["accountId"] as? String,
//        amount = amount,
//        currency = currency,
//        bookingDate = this["bookingDate"] as? String,
//        valueDate = this["valueDate"] as? String,
//        bookingDateTime = this["bookingDateTime"] as? String,
//        transactionAmount = TransactionAmount(amount = amount, currency = currency),
//        creditorName = this["creditorName"] as? String,
//        creditorAccount = (this["creditorAccountBban"] as? String)?.let { CreditorAccount(it) },
//        debtorName = this["debtorName"] as? String,
//        remittanceInformationUnstructured = this["remittanceInformationUnstructured"] as? String,
//        proprietaryBankTransactionCode = this["proprietaryBankTransactionCode"] as? String,
//        internalTransactionId = this["internalTransactionId"] as? String,
//        institutionName = this["institutionName"] as? String,
//        institutionId = this["institutionId"] as? String,
//        transactionStatus = status  // Add the status
//    )
//}

private val gson = Gson()

fun Transaction.toJson(): String {
    return gson.toJson(this)
}

fun String.fromJson(): Transaction {
    return gson.fromJson(this, Transaction::class.java)
}

// Optional extension to make it easier to convert back from cached entities
fun CachedTransactionEntity.toTransaction(): Transaction {
    return this.transactionData.fromJson()
}