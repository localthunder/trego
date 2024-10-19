package com.splitter.splittr.model

import com.google.gson.annotations.SerializedName

data class BankAccount(
    @SerializedName("account_id") val accountId: String,
    @SerializedName("requisition_id") val requisitionId: String,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("iban") val iban: String?,
    @SerializedName("institution_id") val institutionId: String,
    @SerializedName("currency") val currency: String?,
    @SerializedName("owner_name") val ownerName: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("product") val product: String?,
    @SerializedName("cash_account_type") val cashAccountType: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    val needsReauthentication: Boolean = false


)
