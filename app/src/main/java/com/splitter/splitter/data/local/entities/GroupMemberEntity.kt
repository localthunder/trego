package com.splitter.splitter.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "group_members")
data class GroupMemberEntity(
    @PrimaryKey(autoGenerate = true)
    @SerializedName("id")
    @ColumnInfo(name = "id")
    val id: Int,

    @SerializedName("group_id")
    @ColumnInfo(name = "group_id")
    val groupId: Int,

    @SerializedName("user_id")
    @ColumnInfo(name = "user_id")
    val userId: Int,

    @SerializedName("created_at")
    @ColumnInfo(name = "created_at")
    val createdAt: String,

    @SerializedName("updated_at")
    @ColumnInfo(name = "updated_at")
    val updatedAt: String,

    @SerializedName("removed_at")
    @ColumnInfo(name = "removed_at")
    val removedAt: String? // Nullable as it can be null initially
)