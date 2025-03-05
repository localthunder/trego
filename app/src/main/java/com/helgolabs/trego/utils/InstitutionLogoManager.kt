package com.helgolabs.trego.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

    private suspend fun downloadBitmap(url: String): Bitmap = withContext(Dispatchers.IO) {
        try {
            // Use Coil for image loading
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .build()

            val result = loader.execute(request)
            when (result) {
                is SuccessResult -> {
                    val drawable = result.image as? BitmapDrawable
                        ?: throw IllegalStateException("Failed to get bitmap from result")
                    drawable.bitmap
                }
                else -> throw IllegalStateException("Failed to load image from $url")
            }
        } catch (e: Exception) {
            Log.e("Institution Logo Manager", "Error downloading bitmap: ${e.message}", e)
            throw e
        }
    }

    fun clearCache() {
        logoCache.evictAll()
    }
}