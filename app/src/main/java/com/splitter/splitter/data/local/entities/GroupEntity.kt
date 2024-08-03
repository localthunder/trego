package com.splitter.splitter.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true)
    @SerializedName("id")
    @ColumnInfo(name = "id")
    val id: Int,

    @SerializedName("name")
    @ColumnInfo(name = "name")
    val name: String,

    @SerializedName("description")
    @ColumnInfo(name = "description")
    val description: String?,

    @SerializedName("group_img")
    @ColumnInfo(name = "group_img")
    val groupImg: String?,

    @SerializedName("created_at")
    @ColumnInfo(name = "created_at")
    val createdAt: String,

    @SerializedName("updated_at")
    @ColumnInfo(name = "updated_at")
    val updatedAt: String,

    @SerializedName("invite_link")
    @ColumnInfo(name = "invite_link")
    val inviteLink: String?
)
