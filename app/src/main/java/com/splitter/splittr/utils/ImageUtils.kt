package com.splitter.splittr.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.IOException

object ImageUtils {

    private const val TAG = "ImageUtils"

    fun getImageDirectory(context: Context): File {
        val imageDir = File(context.getExternalFilesDir(null), "group_images")
        if (!imageDir.exists()) {
            if (imageDir.mkdirs()) {
                Log.d(TAG, "Image directory created: ${imageDir.absolutePath}")
            } else {
                Log.e(TAG, "Failed to create image directory: ${imageDir.absolutePath}")
            }
        } else {
            Log.d(TAG, "Image directory exists: ${imageDir.absolutePath}")
        }
        return imageDir
    }

    fun saveImage(context: Context, imageName: String, imageData: ByteArray) {
        val imageDir = getImageDirectory(context)
        val file = File(imageDir, imageName)
        try {
            file.writeBytes(imageData)
            Log.d(
                TAG,
                "Image saved successfully: ${file.absolutePath}, Size: ${file.length()} bytes"
            )
        } catch (e: IOException) {
            Log.e(TAG, "Error saving image: ${e.message}")
        }
    }

    fun getImageFile(context: Context, imageName: String): File {
        val imageDir = getImageDirectory(context)
        val file = File(imageDir, imageName)
        Log.d(TAG, "Retrieved image file: ${file.absolutePath}, Exists: ${file.exists()}")
        return file
    }

    fun loadImage(context: Context, imageName: String): ByteArray? {
        val file = getImageFile(context, imageName)
        return if (file.exists()) {
            try {
                val imageData = file.readBytes()
                Log.d(
                    TAG,
                    "Image loaded successfully: ${file.absolutePath}, Size: ${imageData.size} bytes"
                )
                imageData
            } catch (e: IOException) {
                Log.e(TAG, "Error loading image: ${e.message}")
                null
            }
        } else {
            Log.e(TAG, "Image file does not exist: ${file.absolutePath}")
            null
        }
    }

    fun uriToByteArray(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                Log.d(TAG, "URI converted to ByteArray: $uri, Size: ${bytes.size} bytes")
                bytes
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting URI to ByteArray: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}
