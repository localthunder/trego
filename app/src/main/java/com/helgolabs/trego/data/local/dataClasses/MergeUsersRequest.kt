package com.helgolabs.trego.data.local.dataClasses

import com.google.gson.annotations.SerializedName

data class MergeUsersRequest(
    @SerializedName("provisional_user_id")
    val provisionalUserId: Int,
    @SerializedName("target_user_id")
    val targetUserId: Int
)

