package com.splitter.splitter.model

import com.google.gson.annotations.SerializedName

data class Group(
    val id: Int,
    val name: String,
    val description: String?,
    @SerializedName("group_img") val groupImg: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)
