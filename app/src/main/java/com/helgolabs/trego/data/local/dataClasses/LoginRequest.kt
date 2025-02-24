package com.helgolabs.trego.data.local.dataClasses

data class LoginRequest(
    val email: String,
    val password: SecurePassword
) {
    companion object {
        fun create(email: String, password: String): LoginRequest =
            LoginRequest(email, password.secure())
    }
}