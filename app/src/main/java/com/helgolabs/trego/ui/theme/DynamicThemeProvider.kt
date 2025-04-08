package com.helgolabs.trego.ui.theme

import android.R.attr
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.helgolabs.trego.utils.ImagePaletteExtractor
import com.helgolabs.trego.utils.ImagePaletteExtractor.extractSeedColor
import com.helgolabs.trego.utils.ImagePaletteExtractor.generateMaterialColorScheme
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.hct.Hct
import com.materialkolor.ktx.rememberThemeColor


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
    content: @Composable () -> Unit
) {

    Log.d("ThemeDebug", "AnimatedDynamicThemeProvider called for group $groupId with scheme: ${dynamicColorScheme != null}")

    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity
    val isDarkTheme = isSystemInDarkTheme()

    key(groupId) {


        // Configure system UI
        DisposableEffect(view) {
            if (activity != null) {
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                val shouldUseDarkIcons = dynamicColorScheme?.let { !isDarkTheme } ?: !isDarkTheme
                insetsController.isAppearanceLightStatusBars = shouldUseDarkIcons
            }

            onDispose {
                if (activity != null) {
                    WindowCompat.setDecorFitsSystemWindows(activity.window, true)
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

//// Helper function to access dynamic color scheme
//@Composable
//fun useDynamicColorScheme(): ImagePaletteExtractor.GroupColorScheme? {
//    return LocalDynamicColorScheme.current
//}
//
//// In your Material3Theme.kt or similar file
//@Composable
//fun GroupDetailsTheme(
//    groupImageBitmap: Bitmap?,
//    content: @Composable () -> Unit
//) {
//    val context = LocalContext.current
//    val isDarkTheme = isSystemInDarkTheme()
//
//    // Extract colors using your custom implementation
//    val customColorScheme = remember(groupImageBitmap) {
//        if (groupImageBitmap != null) {
//            ImagePaletteExtractor.extractColorsFromBitmap(groupImageBitmap)
//        } else {
//            null
//        }
//    }
//
//    // Choose the color scheme
//    val colorScheme = when {
//        // Use custom extraction if available
//        customColorScheme != null && customColorScheme.extracted -> {
//            if (isDarkTheme) customColorScheme.darkScheme else customColorScheme.lightScheme
//        }
//        // Fall back to system dynamic colors on Android 12+
//        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
//            if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
//        }
//        // Fallback to default
//        else -> {
//            if (isDarkTheme) darkColorScheme() else lightColorScheme()
//        }
//    }
//
//    MaterialTheme(
//        colorScheme = colorScheme,
//        typography = MaterialTheme.typography,
//        content = content
//    )
//}
//
//@SuppressLint("RestrictedApi")
//@Composable
//fun CustomDynamicTheme(bitmap: Bitmap, content: @Composable () -> Unit) {
//    val seedColor = remember(bitmap) { extractSeedColor(bitmap) }
//    val palette = remember(seedColor) { generateMaterialColorScheme(seedColor) }
//
//    val lightScheme = lightColorScheme(
//        primary = Color(palette.a1.tone(50)),
//        secondary = Color(palette.a2.tone(50)),
//        tertiary = Color(palette.a3.tone(50)),
//        background = Color(palette.n1.tone(90)),
//        surface = Color(palette.n2.tone(98))
//    )
//
//    val darkScheme = darkColorScheme(
//        primary = Color(palette.a1.tone(80)),
//        secondary = Color(palette.a2.tone(80)),
//        tertiary = Color(palette.a3.tone(80)),
//        background = Color(palette.n1.tone(20)),
//        surface = Color(palette.n2.tone(10))
//    )
//
//    val isDark = isSystemInDarkTheme()
//    MaterialTheme(
//        colorScheme = if (isDark) darkScheme else lightScheme,
//        typography = Typography(),
//        content = content
//    )
//}
//
//@Composable
//fun MaterialDynamicTheme(
//    bitmap: Bitmap,
//    isDarkTheme: Boolean = isSystemInDarkTheme(),
//    content: @Composable () -> Unit
//) {
//    val context = LocalContext.current
//    val view = LocalView.current
//    val activity = context.findActivity()
//    val imageBitmap = bitmap.asImageBitmap()
//    val seedColor = rememberThemeColor(imageBitmap, fallback = MaterialTheme.colorScheme.primary)
//
//    // Generate MaterialKolor scheme directly from bitmap
//    val customColorScheme = remember(bitmap, isDarkTheme) {
//        bitmap.let {
//            try {
//                // Generate dynamic color scheme directly
//                dynamicColorScheme(
//                    primary = seedColor,
//                    isDark = isDarkTheme,
//                    style = PaletteStyle.TonalSpot,
//                    isAmoled = false
//                )
//            } catch (e: Exception) {
//                Log.e("MaterialDynamicTheme", "Error generating MaterialKolor scheme", e)
//                null
//            }
//        }
//    }
//
//    // Handle system UI
//    DisposableEffect(view, isDarkTheme) {
//        if (activity != null) {
//            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
//            val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
//            insetsController.isAppearanceLightStatusBars = !isDarkTheme
//        }
//
//        onDispose {
//            if (activity != null) {
//                WindowCompat.setDecorFitsSystemWindows(activity.window, true)
//            }
//        }
//    }
//
//    // Choose color scheme
//    val colorScheme = when {
//        // Use our material-color-utilities based scheme if available
//        customColorScheme != null -> {
//            customColorScheme
//        }
//        // Use system dynamic colors on Android 12+
//        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
//            if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
//        }
//        // Fall back to default Material 3 colors
//        else -> {
//            if (isDarkTheme) darkColorScheme() else lightColorScheme()
//        }
//    }
//
//    // Apply the theme
//    MaterialTheme(
//        colorScheme = colorScheme,
//        typography = MaterialTheme.typography,
//        content = content
//    )
//}
//
///**
// * Find the Activity from a Context
// */
//private fun Context.findActivity(): Activity? {
//    var currentContext = this
//    while (currentContext is ContextWrapper) {
//        if (currentContext is Activity) {
//            return currentContext
//        }
//        currentContext = currentContext.baseContext
//    }
//    return null
//}