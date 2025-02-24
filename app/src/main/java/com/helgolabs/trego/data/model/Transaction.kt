package com.helgolabs.trego.data.model

import com.google.gson.annotations.SerializedName
import com.helgolabs.trego.data.local.dataClasses.TransactionStatus

data class Transaction(
    @SerializedName("transactionId") val transactionId: String,
    @SerializedName("userId") val userId: Int?,
    @SerializedName("description") val description: String?,
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("updatedAt") val updatedAt: String?,
    @SerializedName("accountId") val accountId: String?,
    @SerializedName("amount") val amount: Double? = null,
    @SerializedName("currency") val currency: String?,
    @SerializedName("bookingDate") val bookingDate: String?,
    @SerializedName("valueDate") val valueDate: String?,
    @SerializedName("bookingDateTime") val bookingDateTime: String?,
    @SerializedName("transactionAmount") val transactionAmount: TransactionAmount? = null,
    @SerializedName("creditorName") val creditorName: String?,
    @SerializedName("creditorAccount") val creditorAccount: CreditorAccount?,
    @SerializedName("debtorName") val debtorName: String?,
    @SerializedName("remittanceInformationUnstructured") val remittanceInformationUnstructured: String?,
    @SerializedName("proprietaryBankTransactionCode") val proprietaryBankTransactionCode: String?,
    @SerializedName("internalTransactionId") val internalTransactionId: String?,
    @SerializedName("institutionName") val institutionName: String?,
    @SerializedName("institutionId") val institutionId: String?,
    @SerializedName("transactionStatus") val transactionStatus: TransactionStatus? = TransactionStatus.BOOKED
) {
    // Helper function to safely get amount
    fun getEffectiveAmount(): Double {
        return amount ?: transactionAmount?.amount ?: 0.0
    }

    // Helper function to safely get currency
    fun getEffectiveCurrency(): String {
        return currency ?: transactionAmount?.currency ?: "GBP"
    }
}

data class TransactionAmount(
    @SerializedName("amount") val amount: Double,
    @SerializedName("currency") val currency: String
)

data class CreditorAccount(
    @SerializedName("bban") val bban: String?
)
