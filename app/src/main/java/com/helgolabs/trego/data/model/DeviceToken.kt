package com.helgolabs.trego.data.model

import com.google.gson.annotations.SerializedName

data class DeviceToken(
    @SerializedName("token_id") val tokenId: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("fcm_token") val fcmToken: String,
    @SerializedName("device_type") val deviceType: String = "android",
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)
