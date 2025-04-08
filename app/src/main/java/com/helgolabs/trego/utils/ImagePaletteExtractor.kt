package com.helgolabs.trego.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.size.Scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color as ComposeColor
import android.graphics.drawable.Drawable
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.android.material.color.utilities.CorePalette
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.Scheme
import com.google.android.material.color.utilities.Score

object ImagePaletteExtractor {

    data class GroupColorScheme(
        val lightScheme: ColorScheme,
        val darkScheme: ColorScheme,
        val extracted: Boolean = false
    )

    suspend fun extractColorsFromImage(context: Context, imagePath: String?): GroupColorScheme {
        if (imagePath == null) {
            Log.d("PaletteExtractor", "No image path provided")
            return createDefaultColorScheme()
        }

        return try {
            withContext(Dispatchers.IO) {
                val imageLoader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(imagePath)
                    .scale(Scale.FILL)
                    .size(300) // Optimize size for performance
                    .allowHardware(false) // Ensure software bitmap conversion
                    .build()

                val result = imageLoader.execute(request)

                if (result is SuccessResult) {
                    val image = result.image
                    val bitmap = (image as? BitmapDrawable)?.bitmap ?: image.toBitmap()
                    extractColorsFromBitmapUsingMaterial3(bitmap)
                } else {
                    Log.e("PaletteExtractor", "Failed to load image (not a SuccessResult)")
                    createDefaultColorScheme()
                }
            }
        } catch (e: Exception) {
            Log.e("PaletteExtractor", "Error extracting colors", e)
            createDefaultColorScheme()
        }
    }

    @SuppressLint("RestrictedApi")
    private fun extractColorsFromBitmapUsingMaterial3(bitmap: Bitmap): GroupColorScheme {
        try {
            // Extract the pixels from the bitmap
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

            // Use Material Color Utilities to generate a palette
            val sourceColors = QuantizerCelebi.quantize(pixels, 128)
            val seedColor = Score.score(sourceColors, 4).first()

            // Generate an entire harmonized palette
            val scheme = Scheme.light(seedColor)
            val darkScheme = Scheme.dark(seedColor)

            // Create the light scheme with proper Material 3 tones
            val lightColorScheme = lightColorScheme(
                // Primary colors
                primary = ComposeColor(scheme.primary),
                onPrimary = ComposeColor(scheme.onPrimary),
                primaryContainer = ComposeColor(scheme.primaryContainer),
                onPrimaryContainer = ComposeColor(scheme.onPrimaryContainer),

                // Secondary colors
                secondary = ComposeColor(scheme.secondary),
                onSecondary = ComposeColor(scheme.onSecondary),
                secondaryContainer = ComposeColor(scheme.secondaryContainer),
                onSecondaryContainer = ComposeColor(scheme.onSecondaryContainer),

                // Tertiary colors
                tertiary = ComposeColor(scheme.tertiary),
                onTertiary = ComposeColor(scheme.onTertiary),
                tertiaryContainer = ComposeColor(scheme.tertiaryContainer),
                onTertiaryContainer = ComposeColor(scheme.onTertiaryContainer),

                // Surface colors
                surface = ComposeColor(scheme.surface),
                onSurface = ComposeColor(scheme.onSurface),
                surfaceVariant = ComposeColor(scheme.surfaceVariant),
                onSurfaceVariant = ComposeColor(scheme.onSurfaceVariant),

                // Background
                background = ComposeColor(scheme.background),
                onBackground = ComposeColor(scheme.onBackground),

                // Other colors
                error = ComposeColor(scheme.error),
                onError = ComposeColor(scheme.onError),
                errorContainer = ComposeColor(scheme.errorContainer),
                onErrorContainer = ComposeColor(scheme.onErrorContainer),
                outline = ComposeColor(scheme.outline),
                outlineVariant = ComposeColor(scheme.outlineVariant),
                scrim = ComposeColor(scheme.scrim),
                inverseSurface = ComposeColor(scheme.inverseSurface),
                inverseOnSurface = ComposeColor(scheme.inverseOnSurface),
                inversePrimary = ComposeColor(scheme.inversePrimary)
            )

            // Create the dark scheme with proper Material 3 tones
            val darkColorScheme = darkColorScheme(
                // Primary colors
                primary = ComposeColor(darkScheme.primary),
                onPrimary = ComposeColor(darkScheme.onPrimary),
                primaryContainer = ComposeColor(darkScheme.primaryContainer),
                onPrimaryContainer = ComposeColor(darkScheme.onPrimaryContainer),

                // Secondary colors
                secondary = ComposeColor(darkScheme.secondary),
                onSecondary = ComposeColor(darkScheme.onSecondary),
                secondaryContainer = ComposeColor(darkScheme.secondaryContainer),
                onSecondaryContainer = ComposeColor(darkScheme.onSecondaryContainer),

                // Tertiary colors
                tertiary = ComposeColor(darkScheme.tertiary),
                onTertiary = ComposeColor(darkScheme.onTertiary),
                tertiaryContainer = ComposeColor(darkScheme.tertiaryContainer),
                onTertiaryContainer = ComposeColor(darkScheme.onTertiaryContainer),

                // Surface colors
                surface = ComposeColor(darkScheme.surface),
                onSurface = ComposeColor(darkScheme.onSurface),
                surfaceVariant = ComposeColor(darkScheme.surfaceVariant),
                onSurfaceVariant = ComposeColor(darkScheme.onSurfaceVariant),

                // Background
                background = ComposeColor(darkScheme.background),
                onBackground = ComposeColor(darkScheme.onBackground),

                // Other colors
                error = ComposeColor(darkScheme.error),
                onError = ComposeColor(darkScheme.onError),
                errorContainer = ComposeColor(darkScheme.errorContainer),
                onErrorContainer = ComposeColor(darkScheme.onErrorContainer),
                outline = ComposeColor(darkScheme.outline),
                outlineVariant = ComposeColor(darkScheme.outlineVariant),
                scrim = ComposeColor(darkScheme.scrim),
                inverseSurface = ComposeColor(darkScheme.inverseSurface),
                inverseOnSurface = ComposeColor(darkScheme.inverseOnSurface),
                inversePrimary = ComposeColor(darkScheme.inversePrimary)
            )

            Log.d("PaletteExtractor", "Successfully created Material 3 color schemes")
            return GroupColorScheme(lightColorScheme, darkColorScheme, true)

        } catch (e: Exception) {
            Log.e("PaletteExtractor", "Error processing bitmap with Material 3 utilities", e)
            return fallbackExtraction(bitmap)
        }
    }

    private fun fallbackExtraction(bitmap: Bitmap): GroupColorScheme {
        try {
            val palette = Palette.from(bitmap).generate()

            // Extract colors from palette
            val vibrant = palette.vibrantSwatch
            val darkVibrant = palette.darkVibrantSwatch
            val lightVibrant = palette.lightVibrantSwatch
            val dominant = palette.dominantSwatch
            val muted = palette.mutedSwatch
            val darkMuted = palette.darkMutedSwatch

            // Primary color - prefer vibrant, fall back to dominant
            val primaryColor = vibrant?.rgb ?: dominant?.rgb ?: Color.BLUE
            val primaryContainer = lightVibrant?.rgb ?: vibrant?.rgb ?: dominant?.rgb ?: Color.LTGRAY

            // Secondary color - prefer muted, different from primary
            val secondaryColor = muted?.rgb ?: darkVibrant?.rgb ?: Color.DKGRAY
            val secondaryContainer = muted?.rgb ?: darkVibrant?.rgb ?: Color.GRAY

            // Tertiary color - try for a complementary color
            val tertiaryColor = darkMuted?.rgb ?: darkVibrant?.rgb ?: Color.DKGRAY
            val tertiaryContainer = darkMuted?.rgb ?: darkVibrant?.rgb ?: Color.GRAY

            // Text colors - improved for better accessibility
            val onPrimary = ensureContrastRatio(primaryColor, Color.WHITE, 4.5f)
            val onSecondary = ensureContrastRatio(secondaryColor, Color.WHITE, 4.5f)
            val onTertiary = ensureContrastRatio(tertiaryColor, Color.WHITE, 4.5f)
            val onPrimaryContainer = ensureContrastRatio(primaryContainer, Color.BLACK, 4.5f)
            val onSecondaryContainer = ensureContrastRatio(secondaryContainer, Color.BLACK, 4.5f)
            val onTertiaryContainer = ensureContrastRatio(tertiaryContainer, Color.BLACK, 4.5f)

            // Create light and dark schemes with improved contrast
            val lightScheme = lightColorScheme(
                primary = ComposeColor(primaryColor),
                onPrimary = ComposeColor(onPrimary),
                primaryContainer = ComposeColor(primaryContainer),
                onPrimaryContainer = ComposeColor(onPrimaryContainer),
                secondary = ComposeColor(secondaryColor),
                onSecondary = ComposeColor(onSecondary),
                secondaryContainer = ComposeColor(secondaryContainer),
                onSecondaryContainer = ComposeColor(onSecondaryContainer),
                tertiary = ComposeColor(tertiaryColor),
                onTertiary = ComposeColor(onTertiary),
                tertiaryContainer = ComposeColor(tertiaryContainer),
                onTertiaryContainer = ComposeColor(onTertiaryContainer)
            )

            // For dark scheme, invert some colors for better contrast
            val darkScheme = darkColorScheme(
                primary = ComposeColor(primaryColor),
                onPrimary = ComposeColor(onPrimary),
                primaryContainer = ComposeColor(adjustColorForDarkTheme(primaryContainer)),
                onPrimaryContainer = ComposeColor(ensureContrastRatio(adjustColorForDarkTheme(primaryContainer), Color.WHITE, 4.5f)),
                secondary = ComposeColor(secondaryColor),
                onSecondary = ComposeColor(onSecondary),
                secondaryContainer = ComposeColor(adjustColorForDarkTheme(secondaryContainer)),
                onSecondaryContainer = ComposeColor(ensureContrastRatio(adjustColorForDarkTheme(secondaryContainer), Color.WHITE, 4.5f)),
                tertiary = ComposeColor(tertiaryColor),
                onTertiary = ComposeColor(onTertiary),
                tertiaryContainer = ComposeColor(adjustColorForDarkTheme(tertiaryContainer)),
                onTertiaryContainer = ComposeColor(ensureContrastRatio(adjustColorForDarkTheme(tertiaryContainer), Color.WHITE, 4.5f))
            )

            return GroupColorScheme(lightScheme, darkScheme, true)
        } catch (e: Exception) {
            Log.e("PaletteExtractor", "Error in fallback extraction", e)
            return createDefaultColorScheme()
        }
    }

    private fun ensureContrastRatio(backgroundColor: Int, foregroundColor: Int, targetRatio: Float): Int {
        val backgroundLuminance = calculateLuminance(backgroundColor)
        val foregroundLuminance = calculateLuminance(foregroundColor)

        val currentRatio = calculateContrastRatio(backgroundLuminance, foregroundLuminance)

        if (currentRatio >= targetRatio) {
            return foregroundColor
        }

        // Adjust foreground color to meet contrast ratio
        val isBackgroundDark = backgroundLuminance < 0.5
        val targetColor = if (isBackgroundDark) Color.WHITE else Color.BLACK

        // Blend between current foreground and target color
        val blendFactor = 0.75f  // Start with 75% blend
        return blendColors(foregroundColor, targetColor, blendFactor)
    }

    private fun calculateLuminance(color: Int): Double {
        val red = Color.red(color) / 255.0
        val green = Color.green(color) / 255.0
        val blue = Color.blue(color) / 255.0

        val r = if (red <= 0.03928) red / 12.92 else Math.pow((red + 0.055) / 1.055, 2.4)
        val g = if (green <= 0.03928) green / 12.92 else Math.pow((green + 0.055) / 1.055, 2.4)
        val b = if (blue <= 0.03928) blue / 12.92 else Math.pow((blue + 0.055) / 1.055, 2.4)

        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun calculateContrastRatio(luminance1: Double, luminance2: Double): Float {
        val lighter = Math.max(luminance1, luminance2)
        val darker = Math.min(luminance1, luminance2)
        return ((lighter + 0.05) / (darker + 0.05)).toFloat()
    }

    private fun blendColors(color1: Int, color2: Int, factor: Float): Int {
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)

        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)

        val r = (r1 * (1 - factor) + r2 * factor).toInt().coerceIn(0, 255)
        val g = (g1 * (1 - factor) + g2 * factor).toInt().coerceIn(0, 255)
        val b = (b1 * (1 - factor) + b2 * factor).toInt().coerceIn(0, 255)

        return Color.rgb(r, g, b)
    }

    private fun adjustColorForDarkTheme(color: Int): Int {
        // For dark theme, we may want to darken some colors
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)

        // Reduce brightness for dark theme
        hsv[2] = hsv[2] * 0.7f
        // Slightly boost saturation
        hsv[1] = (hsv[1] * 1.2f).coerceAtMost(1.0f)

        return Color.HSVToColor(hsv)
    }

    private fun createDefaultColorScheme(): GroupColorScheme {
        // Return default Material color schemes
        return GroupColorScheme(
            lightScheme = lightColorScheme(),
            darkScheme = darkColorScheme(),
            extracted = false
        )
    }

    @SuppressLint("RestrictedApi")
    fun extractSeedColor(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        // ✅ Use QuantizerCelebi correctly
        val colorToCount: Map<Int, Int> = QuantizerCelebi.quantize(pixels, 128)

        // ✅ Use Score to rank colors
        val rankedColors = Score.score(colorToCount, 4)

        return rankedColors.firstOrNull() ?: 0xFF6200EE.toInt() // Default fallback color
    }

    @SuppressLint("RestrictedApi")
    fun generateMaterialColorScheme(seedColor: Int): CorePalette {
        return CorePalette.of(seedColor)
    }
}