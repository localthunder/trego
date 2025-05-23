package com.helgolabs.trego.data.local.dataClasses

data class ApiResponse<T>(
    val success: Boolean,
    val data: T,
    val message: String? = null
)