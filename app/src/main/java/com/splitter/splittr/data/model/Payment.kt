package com.splitter.splittr.data.model

import com.google.gson.annotations.SerializedName
import com.splitter.splittr.utils.TimestampedEntity
import java.sql.Timestamp

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
    @SerializedName("updated_at") override val updatedAt: String,
    @SerializedName("split_mode") val splitMode: String,
    @SerializedName("payment_type") val paymentType: String,
    val currency: String?,
    @SerializedName("deleted_at") val deletedAt: Timestamp?,
    val institutionId: String?
) : TimestampedEntity
