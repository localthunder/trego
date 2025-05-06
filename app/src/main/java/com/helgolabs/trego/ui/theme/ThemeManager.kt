package com.helgolabs.trego.ui.theme

import androidx.compose.runtime.compositionLocalOf
import com.helgolabs.trego.data.local.dataClasses.PreferenceKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ThemeManager {
    private val _themeMode = MutableStateFlow(PreferenceKeys.ThemeMode.SYSTEM)
    val themeMode: StateFlow<String> = _themeMode

    // Counter to force recomposition when theme changes
    private val _themeChangeCounter = MutableStateFlow(0)
    val themeChangeCounter: StateFlow<Int> = _themeChangeCounter

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        // Increment counter to trigger recomposition
        _themeChangeCounter.value++
    }
}

// Create a composition local
val LocalThemeMode = compositionLocalOf { PreferenceKeys.ThemeMode.SYSTEM }
val LocalThemeChangeCounter = compositionLocalOf { 0 }