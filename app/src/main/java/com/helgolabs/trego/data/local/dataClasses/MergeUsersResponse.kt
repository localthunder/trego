package com.helgolabs.trego.data.local.dataClasses

import com.google.gson.annotations.SerializedName

data class MergeUsersResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String?,
    @SerializedName("merged_user_id")
    val mergedUserId: Int,
    @SerializedName("target_user_id")
    val targetUserId: Int,
    @SerializedName("affected_groups")
    val affectedGroups: List<Int>? = null,
    @SerializedName("affected_payments")
    val affectedPayments: List<Int>? = null
)