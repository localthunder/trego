package com.splitter.splitter.model

import com.google.gson.annotations.SerializedName

data class Transaction(
    @SerializedName("transactionId") val transactionId: String,
    @SerializedName("userId") val userId: Int?,
    @SerializedName("description") val description: String?,
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("updatedAt") val updatedAt: String?,
    @SerializedName("accountId") val accountId: String?,
    @SerializedName("currency") val currency: String?,
    @SerializedName("bookingDate") val bookingDate: String?,
    @SerializedName("valueDate") val valueDate: String?,
    @SerializedName("bookingDateTime") val bookingDateTime: String?,
    @SerializedName("transactionAmount") val transactionAmount: TransactionAmount,
    @SerializedName("creditorName") val creditorName: String?,
    @SerializedName("creditorAccount") val creditorAccount: CreditorAccount?,
    @SerializedName("remittanceInformationUnstructured") val remittanceInformationUnstructured: String?,
    @SerializedName("proprietaryBankTransactionCode") val proprietaryBankTransactionCode: String?,
    @SerializedName("internalTransactionId") val internalTransactionId: String?
)

data class TransactionAmount(
    @SerializedName("amount") val amount: String,
    @SerializedName("currency") val currency: String
)

data class CreditorAccount(
    @SerializedName("bban") val bban: String?
)
