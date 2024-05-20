package com.splitter.splitter.model

import com.google.gson.annotations.SerializedName

data class BankAccount(
    @SerializedName("account_id") val accountId: String,
    @SerializedName("requisition_id") val requisitionId: String,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("iban") val iban: String?,
    @SerializedName("currency") val currency: String?,
    @SerializedName("owner_name") val ownerName: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("product") val product: String?,
    @SerializedName("cash_account_type") val cashAccountType: String?
)
