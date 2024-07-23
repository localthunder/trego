package com.splitter.splitter.model

import com.google.gson.annotations.SerializedName

data class User(
    @SerializedName("userId") val userId: Int,
    val username: String,
    val email: String,
    @SerializedName("passwordHash") val passwordHash: String?,
    @SerializedName("googleId") val googleId: String?,
    @SerializedName("appleId") val appleId: String?,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String,
    @SerializedName("default_currency") val defaultCurrency: String
)
