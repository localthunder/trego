package com.helgolabs.trego.ui.theme

import android.app.Activity
import android.os.Build
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.dataClasses.PreferenceKeys
import com.helgolabs.trego.ui.viewmodels.UserPreferencesViewModel
import com.helgolabs.trego.utils.ImagePaletteExtractor


// Create composition local for dynamic color scheme
val LocalDynamicColorScheme = compositionLocalOf<ImagePaletteExtractor.GroupColorScheme?> { null }

/**
 * Theme provider that can dynamically apply color schemes based on group images.
 * Uses our custom implementation with extracted colors from the group image.
 */
@Composable
fun AnimatedDynamicThemeProvider(
    groupId: Int?,
    dynamicColorScheme: ImagePaletteExtractor.GroupColorScheme?,
    themeMode: String = PreferenceKeys.ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity

    // Get userPreferencesViewModel and themeMode
    val viewModelStore = LocalViewModelStoreOwner.current?.viewModelStore ?: ViewModelStore()
    val factory = (context.applicationContext as MyApplication).viewModelFactory
    val userPreferencesViewModel: UserPreferencesViewModel = ViewModelProvider(viewModelStore, factory)
        .get(UserPreferencesViewModel::class.java)

    // Determine dark theme based on user preference
    val isDarkTheme = when (themeMode) {
        PreferenceKeys.ThemeMode.LIGHT -> false
        PreferenceKeys.ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    }

    key(groupId) {
        // Configure system UI - NOTE: We need to make sure this consistently applies
        // across all screens
        if (activity != null) {
            // Apply the status bar configuration
            SideEffect {
                Log.d("StatusBarDebug", "DynamicTheme: Setting status bar for group $groupId, isDarkTheme=$isDarkTheme")
                val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                val currentState = insetsController.isAppearanceLightStatusBars

                // In dark theme, we want light status bar icons (white)
                // In light theme, we want dark status bar icons (black)
                // isAppearanceLightStatusBars = true means dark icons, false means light icons
                val targetState = !isDarkTheme

                Log.d("StatusBarDebug", "DynamicTheme: current=$currentState, target=$targetState, " +
                        "isDarkTheme=$isDarkTheme, groupId=$groupId")

                if (currentState != targetState) {
                    Log.d("StatusBarDebug", "DynamicTheme: CHANGING status bar icons from " +
                            "${if (currentState) "DARK" else "LIGHT"} to ${if (targetState) "DARK" else "LIGHT"}")
                    insetsController.isAppearanceLightStatusBars = targetState

                    // Force update by setting window flags directly
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Log.d("StatusBarDebug", "DynamicTheme: Using direct Window decor flags as backup")
                        val window = activity.window
                        val decorView = window.decorView
                        var flags = decorView.systemUiVisibility

                        if (targetState) {
                            // For light status bar (dark icons)
                            flags = flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        } else {
                            // For dark status bar (light icons)
                            flags = flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                        }

                        decorView.systemUiVisibility = flags
                    }
                } else {
                    Log.d("StatusBarDebug", "DynamicTheme: No change needed for status bar icons")
                }
            }
        }

        // Determine which color scheme to use
        val targetColorScheme = remember(dynamicColorScheme, isDarkTheme, groupId) {
            when {
                // Use our custom extracted colors if available
                dynamicColorScheme != null && dynamicColorScheme.extracted -> {
                    if (isDarkTheme) dynamicColorScheme.darkScheme else dynamicColorScheme.lightScheme
                }
                // Fall back to system dynamic colors on Android S+
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                // Fall back to default Material theme
                else -> {
                    if (isDarkTheme) darkColorScheme() else lightColorScheme()
                }
            }
        }

        // Keep track of the previously used color scheme for animations
        val previousColorScheme = remember { mutableStateOf(targetColorScheme) }
        val animationSpec = spring<Color>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )

        // Animate primary colors
        val primary = animateColorAsState(
            targetValue = targetColorScheme.primary,
            animationSpec = animationSpec,
            label = "primary",
        )
        val onPrimary = animateColorAsState(
            targetValue = targetColorScheme.onPrimary,
            animationSpec = animationSpec,
            label = "onPrimary"
        )
        val primaryContainer = animateColorAsState(
            targetValue = targetColorScheme.primaryContainer,
            animationSpec = animationSpec,
            label = "primaryContainer"
        )
        val onPrimaryContainer = animateColorAsState(
            targetValue = targetColorScheme.onPrimaryContainer,
            animationSpec = animationSpec,
            label = "onPrimaryContainer"
        )

        // Animate secondary colors
        val secondary = animateColorAsState(
            targetValue = targetColorScheme.secondary,
            animationSpec = animationSpec,
            label = "secondary"
        )
        val onSecondary = animateColorAsState(
            targetValue = targetColorScheme.onSecondary,
            animationSpec = animationSpec,
            label = "onSecondary"
        )
        val secondaryContainer = animateColorAsState(
            targetValue = targetColorScheme.secondaryContainer,
            animationSpec = animationSpec,
            label = "secondaryContainer"
        )
        val onSecondaryContainer = animateColorAsState(
            targetValue = targetColorScheme.onSecondaryContainer,
            animationSpec = animationSpec,
            label = "onSecondaryContainer"
        )

        // Animate tertiary colors
        val tertiary = animateColorAsState(
            targetValue = targetColorScheme.tertiary,
            animationSpec = animationSpec,
            label = "tertiary"
        )
        val onTertiary = animateColorAsState(
            targetValue = targetColorScheme.onTertiary,
            animationSpec = animationSpec,
            label = "onTertiary"
        )
        val tertiaryContainer = animateColorAsState(
            targetValue = targetColorScheme.tertiaryContainer,
            animationSpec = animationSpec,
            label = "tertiaryContainer"
        )
        val onTertiaryContainer = animateColorAsState(
            targetValue = targetColorScheme.onTertiaryContainer,
            animationSpec = animationSpec,
            label = "onTertiaryContainer"
        )

        // Animate background colors
        val background = animateColorAsState(
            targetValue = targetColorScheme.background,
            animationSpec = animationSpec,
            label = "background"
        )
        val onBackground = animateColorAsState(
            targetValue = targetColorScheme.onBackground,
            animationSpec = animationSpec,
            label = "onBackground"
        )
        val surface = animateColorAsState(
            targetValue = targetColorScheme.surface,
            animationSpec = animationSpec,
            label = "surface"
        )
        val onSurface = animateColorAsState(
            targetValue = targetColorScheme.onSurface,
            animationSpec = animationSpec,
            label = "onSurface"
        )
        val surfaceVariant = animateColorAsState(
            targetValue = targetColorScheme.surfaceVariant,
            animationSpec = animationSpec,
            label = "surfaceVariant"
        )
        val onSurfaceVariant = animateColorAsState(
            targetValue = targetColorScheme.onSurfaceVariant,
            animationSpec = animationSpec,
            label = "onSurfaceVariant"
        )

        // Create animated color scheme
        val animatedColorScheme = targetColorScheme.copy(
            primary = primary.value,
            onPrimary = onPrimary.value,
            primaryContainer = primaryContainer.value,
            onPrimaryContainer = onPrimaryContainer.value,
            secondary = secondary.value,
            onSecondary = onSecondary.value,
            secondaryContainer = secondaryContainer.value,
            onSecondaryContainer = onSecondaryContainer.value,
            tertiary = tertiary.value,
            onTertiary = onTertiary.value,
            tertiaryContainer = tertiaryContainer.value,
            onTertiaryContainer = onTertiaryContainer.value,
            background = background.value,
            onBackground = onBackground.value,
            surface = surface.value,
            onSurface = onSurface.value,
            surfaceVariant = surfaceVariant.value,
            onSurfaceVariant = onSurfaceVariant.value
        )

        // Update previous color scheme for next animation
        SideEffect {
            if (targetColorScheme != previousColorScheme.value) {
                previousColorScheme.value = targetColorScheme
            }
        }

        // Provide the animated color scheme
        CompositionLocalProvider(LocalDynamicColorScheme provides dynamicColorScheme) {
            MaterialTheme(
                colorScheme = animatedColorScheme,
                typography = MaterialTheme.typography,
                content = content
            )
        }
    }
}