package com.helgolabs.trego.data.model

import com.google.gson.annotations.SerializedName
import com.helgolabs.trego.utils.TimestampedEntity

data class Requisition(
    @SerializedName("requisitionId") val requisitionId: String,
    @SerializedName("userId") val userId: Int,
    @SerializedName("institutionId") val institutionId: String?,
    @SerializedName("reference") val reference: String?,
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("updatedAt") val updatedAt: String?
)

