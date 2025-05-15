package com.helgolabs.trego.data.model

import com.google.gson.annotations.SerializedName

data class DeviceToken(
    @SerializedName("id") val id: Int? = null,  // Server ID, null for new tokens
    @SerializedName("user_id") val userId: Int,
    @SerializedName("fcm_token") val fcmToken: String,
    @SerializedName("device_type") val deviceType: String = "android",
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)
