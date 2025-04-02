package com.helgolabs.trego.data.model

import com.google.gson.annotations.SerializedName

data class UserPreference(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("key") val preferenceKey: String,
    @SerializedName("value") val preferenceValue: String,
    @SerializedName("updated_at") val updatedAt: String? = null
)