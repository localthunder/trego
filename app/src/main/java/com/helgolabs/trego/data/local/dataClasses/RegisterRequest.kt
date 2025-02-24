package com.helgolabs.trego.data.local.dataClasses

data class RegisterRequest(
    val email: String,
    val password: SecurePassword,
    val username: String
) {
    companion object {
        fun create(email: String, password: String, username: String): RegisterRequest =
            RegisterRequest(email, password.secure(), username)
    }
}