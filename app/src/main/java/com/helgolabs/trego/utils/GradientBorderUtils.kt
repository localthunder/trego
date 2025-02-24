package com.helgolabs.trego.utils

import android.graphics.Bitmap
import android.util.Log
import android.graphics.Color as AndroidColor
import androidx.palette.graphics.Palette

object GradientBorderUtils {

    fun getDominantColors(bitmap: Bitmap): List<Int> {
        val palette = Palette.from(bitmap).generate()
        val dominantSwatches = listOf(
            palette.vibrantSwatch,
            palette.darkVibrantSwatch,
            palette.lightVibrantSwatch,
            palette.mutedSwatch,
            palette.darkMutedSwatch,
            palette.lightMutedSwatch
        ).filterNotNull()

        if (dominantSwatches.isEmpty()) {
            Log.d("getDominantColors", "Palette failed, using average color")
            return listOf(getAverageColor(bitmap))
        }

        dominantSwatches.forEach { swatch ->
            Log.d("getDominantColors", "Swatch color: ${swatch.rgb}")
        }

        return dominantSwatches.map { it.rgb }
    }

    fun getAverageColor(bitmap: Bitmap): Int {
        val width = bitmap.width
        val height = bitmap.height
        val size = width * height
        var r = 0
        var g = 0
        var b = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                r += AndroidColor.red(pixel)
                g += AndroidColor.green(pixel)
                b += AndroidColor.blue(pixel)
            }
        }

        r /= size
        g /= size
        b /= size

        return AndroidColor.rgb(r, g, b)
    }

}