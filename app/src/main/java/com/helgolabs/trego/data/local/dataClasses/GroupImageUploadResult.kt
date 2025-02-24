package com.helgolabs.trego.data.local.dataClasses

data class GroupImageUploadResult(
    val localFileName: String,
    val serverPath: String?,
    val localPath: String?,
    val message: String?,
    val needsSync: Boolean = false
)