package com.helgolabs.trego.utils

import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import okhttp3.OkHttpClient
import java.io.File

/**
 * Utility class to properly set up Coil for image loading.
 */
object CoilSetup {
    private const val TAG = "CoilSetup"
    private var imageLoader: ImageLoader? = null

    /**
     * Get or create a properly configured ImageLoader instance.
     */
    fun getImageLoader(context: Context): ImageLoader {
        return imageLoader ?: synchronized(this) {
            imageLoader ?: createImageLoader(context).also { imageLoader = it }
        }
    }

    private fun createImageLoader(context: Context): ImageLoader {
        SecureLogger.d(TAG, "Creating custom ImageLoader")

        return ImageLoader.Builder(context)

            // Configure disk cache
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "image_cache"))
                    .maxSizeBytes(50 * 1024 * 1024) // 50MB
                    .build()
            }

            // Configure memory cache size (% of available memory)
            .memoryCachePolicy(CachePolicy.ENABLED)

            // Set crossfade duration for smoother image loading
            .crossfade(true)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            OkHttpClient()
                        }
                    )
                )
            }
            .build()
    }
}