package com.helgolabs.trego.data.local.entities

import androidx.room.*
import com.google.gson.annotations.SerializedName
import com.helgolabs.trego.data.sync.SyncStatus

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["server_id"], unique = true),
        Index("email", unique = true),
        Index("username")
    ]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "user_id") val userId: Int = 0,
    @SerializedName("server_id") @ColumnInfo(name = "server_id") val serverId: Int? = null,
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "email") val email: String = "",
    @ColumnInfo(name = "password_hash") val passwordHash: String? = null,
    @ColumnInfo(name = "google_id") val googleId: String? = null,
    @ColumnInfo(name = "apple_id") val appleId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "default_currency") val defaultCurrency: String = "GBP",
    @ColumnInfo(name = "last_login_date") val lastLoginDate: String? = null,
    @ColumnInfo(name = "sync_status") var syncStatus: SyncStatus = SyncStatus.PENDING_SYNC,
    @ColumnInfo(name = "is_provisional") val isProvisional: Boolean = false,
    @ColumnInfo(name = "invited_by") val invitedBy: Int? = null,
    @ColumnInfo(name = "invitation_email") val invitationEmail: String? = null,
    @ColumnInfo(name = "merged_into_user_id") val mergedIntoUserId: Int? = null,
    @ColumnInfo(name = "password_reset_token") val passwordResetToken: String? = null,
    @ColumnInfo(name = "password_reset_expires") val passwordResetExpires: String? = null,
    @ColumnInfo(name = "last_password_change") val lastPasswordChange: String? = null,
    )