package com.helgolabs.trego.ui.components

import android.app.Activity
import android.os.Build
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.helgolabs.trego.data.local.dataClasses.PreferenceKeys
import com.helgolabs.trego.ui.viewmodels.UserPreferencesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalTopAppBar(
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    userPreferencesViewModel: UserPreferencesViewModel? = null,
    systemInDarkTheme: Boolean = isSystemInDarkTheme(),
    isTransparent: Boolean = false
) {
    val activity = LocalContext.current as Activity

    // Determine if the app is in dark mode
    val isAppInDarkTheme = if (userPreferencesViewModel != null) {
        val themeMode by userPreferencesViewModel.themeMode.collectAsState()
        when (themeMode) {
            PreferenceKeys.ThemeMode.LIGHT -> false
            PreferenceKeys.ThemeMode.DARK -> true
            else -> systemInDarkTheme // System setting
        }
    } else {
        systemInDarkTheme
    }

    // Define colors dynamically
    val backgroundColor = if (isTransparent) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Update status bar appearance
    SideEffect {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        insetsController.isAppearanceLightStatusBars = !isAppInDarkTheme
    }

    // Transparent Top App Bar
    TopAppBar(
        title = { title() },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = backgroundColor,
            scrolledContainerColor = backgroundColor, // Ensure consistency on scroll
            titleContentColor = contentColor,
            actionIconContentColor = contentColor
        )
    )
}
