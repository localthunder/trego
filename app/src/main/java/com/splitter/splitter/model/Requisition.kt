package com.splitter.splitter.model

import com.google.gson.annotations.SerializedName

data class Requisition(
    @SerializedName("requisitionId") val requisitionId: String?,
    @SerializedName("userId") val userId: Int?,
    @SerializedName("institutionId") val institutionId: String?,
    @SerializedName("reference") val reference: String?,
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("updatedAt") val updatedAt: String?
)
