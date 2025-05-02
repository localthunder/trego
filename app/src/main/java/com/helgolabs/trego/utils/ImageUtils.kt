package com.helgolabs.trego.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object ImageUtils {
    private const val TAG = "ImageUtils"
    private const val MAX_IMAGE_SIZE = 1024
    private const val JPEG_QUALITY = 85
//    private const val BASE_URL = "http://10.0.2.2:3000/"
    private const val BASE_URL = "http://192.168.68.62:3000/"
    private const val METADATA_SUFFIX = ".meta"
    private const val CACHE_SIZE_BYTES = 50L * 1024 * 1024 // 50MB cache limit
    private const val MAX_RETRY_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 1000L

    fun uriToByteArray(context: Context, uri: Uri): ByteArray? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            originalBitmap?.let { bitmap ->
                val resizedBitmap = resizeBitmap(bitmap)
                ByteArrayOutputStream().use { stream ->
                    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                    val result = stream.toByteArray()
                    if (bitmap != resizedBitmap) {
                        resizedBitmap.recycle()
                    }
                    bitmap.recycle()
                    result
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image: ${e.message}")
            null
        }
    }

    fun resizeBitmap(original: Bitmap): Bitmap {
        val width = original.width
        val height = original.height
        if (width <= MAX_IMAGE_SIZE && height <= MAX_IMAGE_SIZE) {
            return original
        }

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = MAX_IMAGE_SIZE
            newHeight = (MAX_IMAGE_SIZE / ratio).toInt()
        } else {
            newHeight = MAX_IMAGE_SIZE
            newWidth = (MAX_IMAGE_SIZE * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true)
    }

    fun getFullImageUrl(path: String?): String? {
        if (path == null) return null

        // Check if this is a placeholder reference
        if (PlaceholderImageGenerator.isPlaceholderImage(path)) {
            // Don't try to convert placeholder references to URLs
            Log.d(TAG, "Path is a placeholder reference: $path")
            return null
        }

        // Handle regular image paths as before
        val relativePath = path.split("uploads/").lastOrNull()?.let { "uploads/$it" }
            ?: path.split("group_images/").lastOrNull()?.let { "uploads/group_images/$it" }
            ?: path

        Log.d(TAG, "Converting path: $path to relative path: $relativePath")

        return when {
            relativePath.startsWith("http") -> relativePath
            else -> "$BASE_URL/$relativePath"
        }
    }

    // Directory management functions
    fun getImageDirectory(context: Context): File {
        val imageDir = File(context.filesDir, "group_images")
        if (!imageDir.exists() && !imageDir.mkdirs()) {
            throw IOException("Failed to create image directory")
        }
        return imageDir
    }

    fun getImageFile(context: Context, filename: String): File {
        return File(getImageDirectory(context), filename)
    }

    fun saveImage(context: Context, imageData: ByteArray): String {
        val filename = "group_image_${UUID.randomUUID()}.jpg"
        val file = File(getImageDirectory(context), filename)
        try {
            FileOutputStream(file).use { output ->
                output.write(imageData)
            }
            Log.d(TAG, "Image saved successfully: ${file.absolutePath}")
            return filename
        } catch (e: IOException) {
            Log.e(TAG, "Error saving image: ${e.message}")
            throw e
        }
    }

    fun getLocalImagePath(context: Context, serverPath: String): String {
        val fileName = serverPath.substringAfterLast("/")
        return File(getImageDirectory(context), fileName).absolutePath
    }

    fun imageExistsLocally(context: Context, serverPath: String): Boolean {
        val localPath = getLocalImagePath(context, serverPath)
        return File(localPath).exists()
    }

    private fun getMetadataFile(context: Context, serverPath: String): File {
        val fileName = "${serverPath.substringAfterLast("/")}_$METADATA_SUFFIX"
        return File(getImageDirectory(context), fileName)
    }

    fun saveImageMetadata(context: Context, serverPath: String, lastModified: String) {
        getMetadataFile(context, serverPath).writeText(lastModified)
    }

    fun getImageMetadata(context: Context, serverPath: String): String? {
        val file = getMetadataFile(context, serverPath)
        return if (file.exists()) file.readText() else null
    }

    // Enhanced getImageWithCaching with better error handling
    suspend fun getImageWithCaching(context: Context, serverPath: String, lastModified: String): String {
        try {
            // If image exists locally and is up to date, return local path
            val localPath = getLocalImagePath(context, serverPath)
            val localMetadata = getImageMetadata(context, serverPath)

            if (imageExistsLocally(context, serverPath)) {
                if (localMetadata == lastModified) {
                    return localPath
                } else {
                    // Delete outdated image and metadata
                    deleteLocalImage(context, serverPath)
                }
            }

            // Download and cache the image
            val fullUrl = getFullImageUrl(serverPath) ?: throw IOException("Invalid server path")
            val fileName = downloadAndSaveImage(context, fullUrl)
                ?: throw IOException("Failed to download image")

            saveImageMetadata(context, serverPath, lastModified)
            return getLocalImagePath(context, fileName)
        } catch (e: Exception) {
            Log.e(TAG, "Error in getImageWithCaching: ${e.message}")
            throw e
        }
    }

    // Enhanced download with retries
    suspend fun downloadAndSaveImage(context: Context, imageUrl: String): String? {
        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                val result = downloadAndSaveImageInternal(context, imageUrl)
                if (result != null) {
                    // Manage cache size after successful download
                    manageCacheSize(context)
                    return result
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download attempt ${attempt + 1} failed: ${e.message}")
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    delay(RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }
        return null
    }

    suspend fun downloadAndSaveImageInternal(context: Context, imageUrl: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(imageUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connect()

                val inputStream = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(inputStream)

                // Resize the bitmap if necessary
                val resizedBitmap = resizeBitmap(bitmap)

                val outputStream = ByteArrayOutputStream()
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
                val imageData = outputStream.toByteArray()

                val savedFileName = saveImage(context, imageData)

                if (resizedBitmap != bitmap) {
                    resizedBitmap.recycle()
                }
                bitmap.recycle()
                outputStream.close()
                inputStream.close()

                savedFileName
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading image: ${e.message}")
                null
            }
        }
    }

    suspend fun clearOldImages(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val imageDir = getImageDirectory(context)
                val currentTime = System.currentTimeMillis()
                val maxAge = 7 * 24 * 60 * 60 * 1000L // 7 days in milliseconds

                imageDir.listFiles()?.forEach { file ->
                    if (currentTime - file.lastModified() > maxAge) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing old images: ${e.message}")
            }
        }
    }

    // Add cache size management
    private suspend fun manageCacheSize(context: Context) = withContext(Dispatchers.IO) {
        try {
            val imageDir = getImageDirectory(context)
            var totalSize = 0L
            val files = mutableListOf<Pair<File, Long>>()

            // Calculate total size and collect file info
            imageDir.listFiles()?.forEach { file ->
                if (!file.name.endsWith(METADATA_SUFFIX)) {
                    val size = file.length()
                    totalSize += size
                    files.add(Pair(file, file.lastModified()))
                }
            }

            if (totalSize > CACHE_SIZE_BYTES) {
                // Sort by last modified (oldest first)
                files.sortBy { it.second }

                // Remove oldest files until we're under limit
                for (fileInfo in files) {
                    if (totalSize <= CACHE_SIZE_BYTES) break
                    val file = fileInfo.first
                    totalSize -= file.length()

                    // Delete both image and its metadata
                    file.delete()
                    getMetadataFile(context, file.name).delete()
                }
                Log.d(TAG, "Cache cleaned. New size: $totalSize bytes")
            } else {
                Log.d(TAG, "Cache size ($totalSize bytes) is within limit ($CACHE_SIZE_BYTES bytes)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error managing cache size", e)
        }
    }

    // Enhanced deleteLocalImage with metadata cleanup
    fun deleteLocalImage(context: Context, serverPath: String) {
        try {
            val localPath = getLocalImagePath(context, serverPath)
            val imageFile = File(localPath)
            val metadataFile = getMetadataFile(context, serverPath)

            var success = true
            if (imageFile.exists() && !imageFile.delete()) {
                success = false
                Log.e(TAG, "Failed to delete image file: $localPath")
            }
            if (metadataFile.exists() && !metadataFile.delete()) {
                success = false
                Log.e(TAG, "Failed to delete metadata file: ${metadataFile.path}")
            }

            if (!success) {
                throw IOException("Failed to delete some files")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting local image: ${e.message}")
            throw e
        }
    }
}