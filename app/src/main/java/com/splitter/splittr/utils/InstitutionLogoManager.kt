package com.splitter.splittr.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream

class InstitutionLogoManager(private val context: Context) {
    private val logoCache = LruCache<String, Bitmap>(100)
    private val downloadMutexes = mutableMapOf<String, Mutex>()
    private val mutexLock = Mutex()

    data class LogoInfo(
        val file: File,
        val bitmap: Bitmap,
        val dominantColors: List<Color>
    )

    suspend fun getOrFetchLogo(
        institutionId: String,
        logoUrl: String?
    ): LogoInfo? {
        // Get or create mutex for this institution
        val mutex = mutexLock.withLock {
            downloadMutexes.getOrPut(institutionId) { Mutex() }
        }

        return mutex.withLock {
            // Check cache first
            logoCache.get(institutionId)?.let { cachedBitmap ->
                return@withLock createLogoInfo(institutionId, cachedBitmap)
            }

            // Check if file exists
            val logoFile = File(context.filesDir, "${institutionId}.png")
            if (logoFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(logoFile.path)
                if (bitmap != null) {
                    logoCache.put(institutionId, bitmap)
                    return@withLock createLogoInfo(institutionId, bitmap)
                }
            }

            // Download if URL provided
            logoUrl?.let { url ->
                try {
                    val bitmap = downloadAndSaveLogo(url, logoFile)
                    logoCache.put(institutionId, bitmap)
                    return@withLock createLogoInfo(institutionId, bitmap)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    private fun createLogoInfo(institutionId: String, bitmap: Bitmap): LogoInfo {
        val colors = GradientBorderUtils.getDominantColors(bitmap)
            .map { Color(it) }
            .let { if (it.size < 2) listOf(it.first(), it.first().copy(alpha = 0.7f)) else it }

        return LogoInfo(
            file = File(context.filesDir, "${institutionId}.png"),
            bitmap = bitmap,
            dominantColors = colors
        )
    }

    private suspend fun downloadAndSaveLogo(url: String, outputFile: File): Bitmap {
        val bitmap = downloadBitmap(url)
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return bitmap
    }

    private suspend fun downloadBitmap(url: String): Bitmap {
        // Implement your existing bitmap download logic here
        TODO("Implement bitmap download")
    }

    fun clearCache() {
        logoCache.evictAll()
    }
}