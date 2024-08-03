package com.splitter.splitter.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val transactionId: String,
    val userId: Int?,
    val description: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val accountId: String?,
    val currency: String?,
    val bookingDate: String?,
    val valueDate: String?,
    val bookingDateTime: String?,
    val amount: Double,
    val creditorName: String?,
    val creditorAccountBban: String?,
    val debtorName: String?,
    val remittanceInformationUnstructured: String?,
    val proprietaryBankTransactionCode: String?,
    val internalTransactionId: String?,
    val institutionName: String?,
    val institutionId: String?
)
