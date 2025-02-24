package com.helgolabs.trego.data.local.dataClasses

@JvmInline
value class SecurePassword private constructor(private val value: String) {
    companion object {
        fun create(password: String): SecurePassword = SecurePassword(password)
    }

    fun get(): String = value

    override fun toString(): String = "••••••••"
}

// Extension function to clear string contents
fun String.secure(): SecurePassword = SecurePassword.create(this)