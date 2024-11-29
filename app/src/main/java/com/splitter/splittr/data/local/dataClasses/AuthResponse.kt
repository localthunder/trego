package com.splitter.splittr.data.local.dataClasses

data class AuthResponse(
    val token: String?,
    val userId: Int,
    val success: Boolean = true,
    val message: String?,
    val refreshToken: String?
)