package com.helgolabs.trego.data.local.dataClasses

object PreferenceKeys {
    // Theme preferences
    const val THEME_MODE = "theme_mode"

    // Notification preferences
    const val NOTIFICATIONS_ENABLED = "notifications_enabled"

    // Enum class for theme mode values to further prevent typos
    object ThemeMode {
        const val LIGHT = "light"
        const val DARK = "dark"
        const val SYSTEM = "system" // Optional if you want to follow system settings
    }

    // Boolean values as strings (since preferences are stored as strings)
    object BooleanValue {
        const val TRUE = "true"
        const val FALSE = "false"
    }
}