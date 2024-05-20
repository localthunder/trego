package com.splitter.splitter.model

import com.google.gson.annotations.SerializedName

data class Transaction(
    @SerializedName("transactionId") val transactionId: String,
    @SerializedName("bookingDate") val bookingDate: String,
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
