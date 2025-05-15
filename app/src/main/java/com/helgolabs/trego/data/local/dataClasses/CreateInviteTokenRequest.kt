package com.helgolabs.trego.data.local.dataClasses

import com.google.gson.annotations.SerializedName

data class CreateInviteTokenRequest(
    @SerializedName("token")
    val token: String,

    @SerializedName("userId")
    val userId: Int
)