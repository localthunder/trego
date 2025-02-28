package com.helgolabs.trego.data.model

import com.google.gson.annotations.SerializedName

data class GroupDefaultSplit(
    @SerializedName("id") val id: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("group_id") val groupId: Int,
    @SerializedName("percentage") val percentage: Double? = null,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
)