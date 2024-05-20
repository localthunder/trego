package com.splitter.splitter.model

import com.google.gson.annotations.SerializedName

data class PaymentSplit(
    val id: Int,
    @SerializedName("payment_id") val paymentId: Int,
    @SerializedName("user_id") val userId: Int,
    val amount: Double,
    @SerializedName("created_by") val createdBy: Int,
    @SerializedName("updated_by") val updatedBy: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)
