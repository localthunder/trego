package com.splitter.splittr.data.local.dataClasses

import com.splitter.splittr.data.model.GroupMember

data class GroupMemberResponse(
    val success: Boolean,
    val message: String,
    val data: GroupMember
)