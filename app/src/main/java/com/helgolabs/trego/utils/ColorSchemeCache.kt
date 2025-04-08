package com.helgolabs.trego.utils

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.util.Log
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Persistently caches color schemes for groups to ensure consistent colors
 * across app restarts.
 */
@Stable
object ColorSchemeCache {
    private const val PREFS_NAME = "trego_color_schemes"
    private const val COLOR_SCHEMES_KEY = "color_schemes"
    private val cacheMutex = Mutex()

    // In-memory cache for quick access
    private var groupColorSchemes = mutableMapOf<Int, ColorSchemeData>()
    private var isInitialized = false

    // Data class that can be serialized to/from JSON
    data class ColorSchemeData(
        // Light theme colors
        val primaryLight: Int,
        val onPrimaryLight: Int,
        val primaryContainerLight: Int,
        val onPrimaryContainerLight: Int,
        val secondaryLight: Int,
        val onSecondaryLight: Int,
        val secondaryContainerLight: Int,
        val onSecondaryContainerLight: Int,
        val tertiaryLight: Int,
        val onTertiaryLight: Int,
        val tertiaryContainerLight: Int,
        val onTertiaryContainerLight: Int,
        val backgroundLight: Int,
        val onBackgroundLight: Int,
        val surfaceLight: Int,
        val onSurfaceLight: Int,
        val surfaceVariantLight: Int,
        val onSurfaceVariantLight: Int,

        // Dark theme colors
        val primaryDark: Int,
        val onPrimaryDark: Int,
        val primaryContainerDark: Int,
        val onPrimaryContainerDark: Int,
        val secondaryDark: Int,
        val onSecondaryDark: Int,
        val secondaryContainerDark: Int,
        val onSecondaryContainerDark: Int,
        val tertiaryDark: Int,
        val onTertiaryDark: Int,
        val tertiaryContainerDark: Int,
        val onTertiaryContainerDark: Int,
        val backgroundDark: Int,
        val onBackgroundDark: Int,
        val surfaceDark: Int,
        val onSurfaceDark: Int,
        val surfaceVariantDark: Int,
        val onSurfaceVariantDark: Int
    ) {
        // Convert to GroupColorScheme
        fun toGroupColorScheme(): ImagePaletteExtractor.GroupColorScheme {
            val lightScheme = lightColorScheme(
                primary = ComposeColor(primaryLight),
                onPrimary = ComposeColor(onPrimaryLight),
                primaryContainer = ComposeColor(primaryContainerLight),
                onPrimaryContainer = ComposeColor(onPrimaryContainerLight),
                secondary = ComposeColor(secondaryLight),
                onSecondary = ComposeColor(onSecondaryLight),
                secondaryContainer = ComposeColor(secondaryContainerLight),
                onSecondaryContainer = ComposeColor(onSecondaryContainerLight),
                tertiary = ComposeColor(tertiaryLight),
                onTertiary = ComposeColor(onTertiaryLight),
                tertiaryContainer = ComposeColor(tertiaryContainerLight),
                onTertiaryContainer = ComposeColor(onTertiaryContainerLight),
                background = ComposeColor(backgroundLight),
                onBackground = ComposeColor(onBackgroundLight),
                surface = ComposeColor(surfaceLight),
                onSurface = ComposeColor(onSurfaceLight),
                surfaceVariant = ComposeColor(surfaceVariantLight),
                onSurfaceVariant = ComposeColor(onSurfaceVariantLight)
            )

            val darkScheme = darkColorScheme(
                primary = ComposeColor(primaryDark),
                onPrimary = ComposeColor(onPrimaryDark),
                primaryContainer = ComposeColor(primaryContainerDark),
                onPrimaryContainer = ComposeColor(onPrimaryContainerDark),
                secondary = ComposeColor(secondaryDark),
                onSecondary = ComposeColor(onSecondaryDark),
                secondaryContainer = ComposeColor(secondaryContainerDark),
                onSecondaryContainer = ComposeColor(onSecondaryContainerDark),
                tertiary = ComposeColor(tertiaryDark),
                onTertiary = ComposeColor(onTertiaryDark),
                tertiaryContainer = ComposeColor(tertiaryContainerDark),
                onTertiaryContainer = ComposeColor(onTertiaryContainerDark),
                background = ComposeColor(backgroundDark),
                onBackground = ComposeColor(onBackgroundDark),
                surface = ComposeColor(surfaceDark),
                onSurface = ComposeColor(onSurfaceDark),
                surfaceVariant = ComposeColor(surfaceVariantDark),
                onSurfaceVariant = ComposeColor(onSurfaceVariantDark)
            )

            return ImagePaletteExtractor.GroupColorScheme(
                lightScheme = lightScheme,
                darkScheme = darkScheme,
                extracted = true
            )
        }

        companion object {
            // Convert from GroupColorScheme
            fun fromGroupColorScheme(scheme: ImagePaletteExtractor.GroupColorScheme): ColorSchemeData {
                val light = scheme.lightScheme
                val dark = scheme.darkScheme

                return ColorSchemeData(
                    // Light theme colors
                    primaryLight = light.primary.toArgb(),
                    onPrimaryLight = light.onPrimary.toArgb(),
                    primaryContainerLight = light.primaryContainer.toArgb(),
                    onPrimaryContainerLight = light.onPrimaryContainer.toArgb(),
                    secondaryLight = light.secondary.toArgb(),
                    onSecondaryLight = light.onSecondary.toArgb(),
                    secondaryContainerLight = light.secondaryContainer.toArgb(),
                    onSecondaryContainerLight = light.onSecondaryContainer.toArgb(),
                    tertiaryLight = light.tertiary.toArgb(),
                    onTertiaryLight = light.onTertiary.toArgb(),
                    tertiaryContainerLight = light.tertiaryContainer.toArgb(),
                    onTertiaryContainerLight = light.onTertiaryContainer.toArgb(),
                    backgroundLight = light.background.toArgb(),
                    onBackgroundLight = light.onBackground.toArgb(),
                    surfaceLight = light.surface.toArgb(),
                    onSurfaceLight = light.onSurface.toArgb(),
                    surfaceVariantLight = light.surfaceVariant.toArgb(),
                    onSurfaceVariantLight = light.onSurfaceVariant.toArgb(),

                    // Dark theme colors
                    primaryDark = dark.primary.toArgb(),
                    onPrimaryDark = dark.onPrimary.toArgb(),
                    primaryContainerDark = dark.primaryContainer.toArgb(),
                    onPrimaryContainerDark = dark.onPrimaryContainer.toArgb(),
                    secondaryDark = dark.secondary.toArgb(),
                    onSecondaryDark = dark.onSecondary.toArgb(),
                    secondaryContainerDark = dark.secondaryContainer.toArgb(),
                    onSecondaryContainerDark = dark.onSecondaryContainer.toArgb(),
                    tertiaryDark = dark.tertiary.toArgb(),
                    onTertiaryDark = dark.onTertiary.toArgb(),
                    tertiaryContainerDark = dark.tertiaryContainer.toArgb(),
                    onTertiaryContainerDark = dark.onTertiaryContainer.toArgb(),
                    backgroundDark = dark.background.toArgb(),
                    onBackgroundDark = dark.onBackground.toArgb(),
                    surfaceDark = dark.surface.toArgb(),
                    onSurfaceDark = dark.onSurface.toArgb(),
                    surfaceVariantDark = dark.surfaceVariant.toArgb(),
                    onSurfaceVariantDark = dark.onSurfaceVariant.toArgb()
                )
            }
        }
    }

    // Initialize cache from SharedPreferences
    suspend fun initialize(context: Context) = cacheMutex.withLock {
        if (isInitialized) return@withLock

        withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val json = prefs.getString(COLOR_SCHEMES_KEY, null)

                if (json != null) {
                    val type = object : TypeToken<Map<Int, ColorSchemeData>>() {}.type
                    groupColorSchemes = Gson().fromJson(json, type) ?: mutableMapOf()
                    Log.d("PersistentColorSchemeCache", "Loaded ${groupColorSchemes.size} color schemes from prefs")
                }
            } catch (e: Exception) {
                Log.e("PersistentColorSchemeCache", "Error loading color schemes", e)
                groupColorSchemes = mutableMapOf()
            }
        }

        isInitialized = true
    }

    // Save cache to SharedPreferences
    private suspend fun saveToPrefs(context: Context) = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = Gson().toJson(groupColorSchemes)

            prefs.edit {
                putString(COLOR_SCHEMES_KEY, json)
                apply()
            }

            Log.d("PersistentColorSchemeCache", "Saved ${groupColorSchemes.size} color schemes to prefs")
        } catch (e: Exception) {
            Log.e("PersistentColorSchemeCache", "Error saving color schemes", e)
        }
    }

    /**
     * Retrieve a cached color scheme or null if not found
     */
    suspend fun getColorScheme(context: Context, groupId: Int): ImagePaletteExtractor.GroupColorScheme? =
        cacheMutex.withLock {
            if (!isInitialized) {
                initialize(context)
            }

            val schemeData = groupColorSchemes[groupId]
            return@withLock schemeData?.toGroupColorScheme()
        }

    /**
     * Cache a color scheme for future use
     */
    suspend fun cacheColorScheme(context: Context, groupId: Int, scheme: ImagePaletteExtractor.GroupColorScheme) =
        cacheMutex.withLock {
            if (!isInitialized) {
                initialize(context)
            }

            val schemeData = ColorSchemeData.fromGroupColorScheme(scheme)
            groupColorSchemes[groupId] = schemeData

            // Save to persistent storage
            saveToPrefs(context)
        }

    /**
     * Remove a color scheme from the cache
     */
    suspend fun removeColorScheme(context: Context, groupId: Int) = cacheMutex.withLock {
        if (!isInitialized) {
            initialize(context)
        }

        if (groupColorSchemes.remove(groupId) != null) {
            // Only save if something was actually removed
            saveToPrefs(context)
        }
    }

    /**
     * Clear all cached color schemes
     */
    suspend fun clearCache(context: Context) = cacheMutex.withLock {
        groupColorSchemes.clear()
        saveToPrefs(context)
    }
}