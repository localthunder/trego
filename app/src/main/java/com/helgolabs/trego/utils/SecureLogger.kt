package com.helgolabs.trego.utils

import android.content.Context
import android.util.Log

object SecureLogger {
    private var isDebug = false

    fun init(context: Context) {
        // Check if we're in debug mode using the application context
        isDebug = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    private val SENSITIVE_PATTERNS = listOf(
        Regex("token[\"']?\\s*[:=]\\s*[\"']?([^\"']+)[\"']?", RegexOption.IGNORE_CASE), // token patterns
        Regex("password[\"']?\\s*[:=]\\s*[\"']?([^\"']+)[\"']?", RegexOption.IGNORE_CASE), // password patterns
        Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}\\b"), // email patterns
        Regex("[0-9]{13,16}"), // credit card patterns - simplified
        // Add more patterns as needed
    )

    // Log level functions that automatically sanitize
    fun d(tag: String, message: String) {
        if (isDebug) {
            Log.d(tag, sanitizeLog(message))
        }
    }

    fun i(tag: String, message: String) {
        if (isDebug) {
            Log.i(tag, sanitizeLog(message))
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        // Always log errors but sanitize them
        if (throwable != null) {
            Log.e(tag, sanitizeLog(message), throwable)
        } else {
            Log.e(tag, sanitizeLog(message))
        }
    }

    fun v(tag: String, message: String) {
        if (isDebug) {
            Log.v(tag, sanitizeLog(message))
        }
    }

    fun w(tag: String, message: String) {
        if (isDebug) {
            Log.w(tag, sanitizeLog(message))
        }
    }

    private fun sanitizeLog(message: String): String {
        var sanitized = message
        for (pattern in SENSITIVE_PATTERNS) {
            sanitized = sanitized.replace(pattern) { matchResult ->
                val matched = matchResult.value
                val maskedLength = matched.length / 2
                if (matched.length <= 4) {
                    "****"
                } else {
                    matched.substring(0, maskedLength / 2) +
                            "*".repeat(matched.length - maskedLength) +
                            matched.substring(matched.length - maskedLength / 2)
                }
            }
        }
        return sanitized
    }
}