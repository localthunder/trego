package com.helgolabs.trego.utils

enum class PasswordStrength {
    WEAK, MEDIUM, STRONG;

    companion object {
        fun calculate(password: String): PasswordStrength {
            var score = 0

            // Length check
            if (password.length >= 8) score++
            if (password.length >= 12) score++

            // Complexity checks
            if (password.any { it.isDigit() }) score++
            if (password.any { it.isLowerCase() }) score++
            if (password.any { it.isUpperCase() }) score++
            if (password.any { !it.isLetterOrDigit() }) score++

            // Common patterns check (decrease score for common patterns)
            if (password.contains(Regex("123|abc|qwerty|password", RegexOption.IGNORE_CASE))) score--

            return when {
                score <= 2 -> WEAK
                score <= 4 -> MEDIUM
                else -> STRONG
            }
        }
    }
}