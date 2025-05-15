package com.helgolabs.trego.data.local.dataClasses

import com.google.gson.annotations.SerializedName

data class InviteTokenResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("token")
    val token: String? = null,

    @SerializedName("userId")
    val userId: Int? = null,

    @SerializedName("expiresAt")
    val expiresAt: String? = null,

    @SerializedName("message")
    val message: String? = null
)