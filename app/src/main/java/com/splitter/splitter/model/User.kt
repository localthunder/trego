package com.splitter.splitter.model

import com.google.gson.annotations.SerializedName

data class User(
    @SerializedName("user_id") val userId: Int,
    val username: String,
    val email: String,
    @SerializedName("password_hash") val passwordHash: String?,
    @SerializedName("google_id") val googleId: String?,
    @SerializedName("apple_id") val appleId: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)
