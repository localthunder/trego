package com.splitter.splittr.data.model

import com.google.gson.annotations.SerializedName
import com.splitter.splittr.utils.TimestampedEntity

data class User(
    @SerializedName("userId") val userId: Int,
    val username: String,
    val email: String,
    @SerializedName("passwordHash") val passwordHash: String?,
    @SerializedName("googleId") val googleId: String?,
    @SerializedName("appleId") val appleId: String?,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") override val updatedAt: String,
    @SerializedName("defaultCurrency") val defaultCurrency: String?,
    @SerializedName("lastLoginDate") val lastLoginDate: Long?
) : TimestampedEntity
