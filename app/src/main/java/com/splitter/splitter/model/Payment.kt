package com.splitter.splitter.model

import com.google.gson.annotations.SerializedName
import java.sql.Date

data class Payment(
    val id: Int,
    @SerializedName("group_id") val groupId: Int,
    @SerializedName("paid_by_user_id") val paidByUserId: Int,
    @SerializedName("transaction_id") val transactionId: String?,
    val amount: Double,
    val description: String?,
    val notes: String?,
    @SerializedName("payment_date") val paymentDate: String,
    @SerializedName("created_by") val createdBy: Int,
    @SerializedName("updated_by") val updatedBy: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)
