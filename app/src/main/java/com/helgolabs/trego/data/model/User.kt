package com.helgolabs.trego.data.model

import com.google.gson.annotations.SerializedName
import com.helgolabs.trego.utils.TimestampedEntity

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
    @SerializedName("lastLoginDate") val lastLoginDate: String?,
    @SerializedName("isProvisional") val isProvisional: Boolean,
    @SerializedName("invitedBy") val invitedBy: Int?,
    @SerializedName("invitationEmail") val invitationEmail: String?,
    @SerializedName("mergedIntoUserId") val mergedIntoUserId: Int?,
    @SerializedName("passwordResetToken") val passwordResetToken: String?,
    @SerializedName("passwordResetExpires") val passwordResetExpires: String?,
    @SerializedName("lastPasswordChange") val lastPasswordChange: String?
) : TimestampedEntity
