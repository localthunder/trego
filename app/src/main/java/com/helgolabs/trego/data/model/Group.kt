package com.helgolabs.trego.data.model

import com.google.gson.annotations.SerializedName

data class Group(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String?,
    @SerializedName("group_img") val groupImg: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("invite_link") val inviteLink: String?,
    @SerializedName("default_currency") val defaultCurrency: String,
    @SerializedName("default_split_mode") val defaultSplitMode: String,
    )
