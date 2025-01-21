package com.splitter.splittr.data.model

import com.google.gson.annotations.SerializedName
import com.splitter.splittr.utils.TimestampedEntity

data class BankAccount(
    @SerializedName("accountId") val accountId: String,
    @SerializedName("requisitionId") val requisitionId: String,
    @SerializedName("userId") val userId: Int,
    @SerializedName("iban") val iban: String?,
    @SerializedName("institutionId") val institutionId: String,
    @SerializedName("currency") val currency: String?,
    @SerializedName("ownerName") val ownerName: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("product") val product: String?,
    @SerializedName("cashAccountType") val cashAccountType: String?,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") override val updatedAt: String,
    @SerializedName("needsReauthentication") val needsReauthentication: Boolean = false
) : TimestampedEntity