package com.splitter.splittr.model

import com.google.gson.annotations.SerializedName

data class User(
    @SerializedName("userId") val userId: Int,
    val username: String,
    val email: String,
    @SerializedName("passwordHash") val passwordHash: String?,
    @SerializedName("googleId") val googleId: String?,
    @SerializedName("appleId") val appleId: String?,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("updatedAt") val updatedAt: Long,
    @SerializedName("defaultCurrency") val defaultCurrency: String?,
    @SerializedName("lastLoginDate") val lastLoginDate: Long?
)
