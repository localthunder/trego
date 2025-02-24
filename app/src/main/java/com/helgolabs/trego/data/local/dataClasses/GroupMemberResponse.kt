package com.helgolabs.trego.data.local.dataClasses

import com.helgolabs.trego.data.model.GroupMember

data class GroupMemberResponse(
    val success: Boolean,
    val message: String,
    val data: GroupMember
)