package com.helgolabs.trego.data.local.dataClasses

data class UserGroupListItem(
    val id: Int,
    val name: String,
    val description: String?,
    val groupImg: String?,
    val isArchived: Boolean = false
)