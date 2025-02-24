package com.helgolabs.trego.data.local.dataClasses

import com.helgolabs.trego.data.model.Group

data class GroupMemberWithGroupResponse(
    val id: Int,
    val group_id: Int,
    val user_id: Int,
    val created_at: String,
    val updated_at: String,
    val removed_at: String?,
    val group: Group? // The embedded group data from API
)