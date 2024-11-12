package com.splitter.splittr.data.model

import com.google.gson.annotations.SerializedName

data class GroupMember(
    val id: Int,
    @SerializedName("group_id") val groupId: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("removed_at") val removedAt: String? // Nullable as it can be null initially
)
