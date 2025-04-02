package com.helgolabs.trego.utils

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.core.view.WindowCompat
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility class to analyze image brightness and set status bar icons accordingly
 */
object StatusBarHelper {
    private const val TAG = "StatusBarHelper"

    /**
     * Analyzes the top portion of an image to determine if status bar icons should be light or dark
     * @param bitmap The image bitmap to analyze
     * @param activity The activity whose status bar will be updated
     */
    suspend fun updateStatusBarBasedOnImage(bitmap: Bitmap?, activity: Activity) {
        if (bitmap == null) {
            // Default to dark icons (for light backgrounds) if no image
            setStatusBarIconsLight(activity, false)
            return
        }

        try {
            val isDarkImage = withContext(Dispatchers.Default) {
                analyzeTopImagePortion(bitmap)
            }

            // Set light icons for dark image tops, dark icons for light image tops
            setStatusBarIconsLight(activity, isDarkImage)

        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing image", e)
            // Default to dark icons if analysis fails
            setStatusBarIconsLight(activity, false)
        }
    }

    /**
     * Analyzes the top 20% of the image to determine if it's predominantly dark
     * @return true if the top portion is dark, false if it's light
     */
    private fun analyzeTopImagePortion(bitmap: Bitmap): Boolean {
        // Extract the top 20% of the image
        val topHeight = (bitmap.height * 0.2).toInt()
        val topSection = Bitmap.createBitmap(
            bitmap,
            0, 0,
            bitmap.width,
            topHeight.coerceAtMost(bitmap.height)
        )

        // Use Palette to extract dominant colors
        val palette = Palette.from(topSection).generate()

        // Get dominant swatch or default to a medium swatch
        val dominantSwatch = palette.dominantSwatch
            ?: palette.vibrantSwatch
            ?: palette.mutedSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.lightMutedSwatch
            ?: palette.darkVibrantSwatch
            ?: palette.darkMutedSwatch

        if (dominantSwatch != null) {
            // Calculate luminance (brightness) of the dominant color
            val color = dominantSwatch.rgb
            val red = Color.red(color) / 255.0
            val green = Color.green(color) / 255.0
            val blue = Color.blue(color) / 255.0

            // Calculate perceived brightness using standard formula
            val luminance = 0.299 * red + 0.587 * green + 0.114 * blue

            // Return true if the image is dark (luminance < 0.5)
            return luminance < 0.5
        }

        // If we couldn't determine, calculate average pixel brightness
        return calculateAveragePixelBrightness(topSection) < 0.5
    }

    /**
     * Calculate average brightness of pixels in the image
     */
    private fun calculateAveragePixelBrightness(bitmap: Bitmap): Double {
        var pixelCount = 0
        var brightnesSum = 0.0

        // Sample pixels (for very large images, we can sample every few pixels)
        val sampleEvery = if (bitmap.width * bitmap.height > 50000) 3 else 1

        for (x in 0 until bitmap.width step sampleEvery) {
            for (y in 0 until bitmap.height step sampleEvery) {
                val pixel = bitmap.getPixel(x, y)
                val red = Color.red(pixel) / 255.0
                val green = Color.green(pixel) / 255.0
                val blue = Color.blue(pixel) / 255.0

                // Calculate perceived brightness
                val luminance = 0.299 * red + 0.587 * green + 0.114 * blue
                brightnesSum += luminance
                pixelCount++
            }
        }

        return if (pixelCount > 0) brightnesSum / pixelCount else 0.5
    }

    /**
     * Set the status bar icons to light or dark
     * @param activity The activity to update
     * @param isLight Whether to use light icons (for dark backgrounds)
     */
    private fun setStatusBarIconsLight(activity: Activity, isLight: Boolean) {
        val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        // Light system bars = dark icons, so we use !isLight
        insetsController.isAppearanceLightStatusBars = !isLight
    }
}