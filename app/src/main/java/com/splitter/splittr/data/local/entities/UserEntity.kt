package com.splitter.splittr.data.local.entities

import androidx.room.*
import com.google.gson.annotations.SerializedName
import com.splitter.splittr.data.local.converters.LocalIdGenerator
import com.splitter.splittr.data.sync.SyncStatus

@Entity(
    tableName = "users",
    indices = [
        Index("server_id"),
        Index("email", unique = true),
        Index("username", unique = true)
    ]
)
data class UserEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: Int = LocalIdGenerator.nextId(),
    @SerializedName("server_id") @ColumnInfo(name = "server_id") val serverId: Int? = 0,
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "email") val email: String,
    @ColumnInfo(name = "password_hash") val passwordHash: String?,
    @ColumnInfo(name = "google_id") val googleId: String?,
    @ColumnInfo(name = "apple_id") val appleId: String?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "default_currency") val defaultCurrency: String = "GBP",
    @ColumnInfo(name = "last_login_date") val lastLoginDate: String?,
    @ColumnInfo(name = "sync_status") var syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
)