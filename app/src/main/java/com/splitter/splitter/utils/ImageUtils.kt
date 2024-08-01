package com.splitter.splitter.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.splitter.splitter.network.ApiService
import com.splitter.splitter.network.UploadResponsed
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
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

    fun uploadImage(
        context: Context,
        uri: Uri
    ): String? {
        val imageData = uriToByteArray(context, uri)
        return if (imageData != null) {
            val imageName = "image_${System.currentTimeMillis()}.jpg"
            saveImage(context, imageName, imageData)
            Log.d(TAG, "Image uploaded and saved with name: $imageName")
            imageName // Return the name of the saved image
        } else {
            Log.e(TAG, "Failed to convert URI to ByteArray")
            null
        }
    }

    fun uploadGroupImage(
        apiService: ApiService,
        context: Context,
        groupId: Int,
        imageUri: Uri,
        callback: (Boolean, String?, String?) -> Unit
    ) {
        // Convert Uri to ByteArray
        val imageData = uriToByteArray(context, imageUri)
        if (imageData == null) {
            callback(false, null, "Failed to convert URI to ByteArray")
            return
        }

        // Create RequestBody from ByteArray
        val requestFile = imageData.toRequestBody("image/jpeg".toMediaTypeOrNull())

        // MultipartBody.Part is used to send also the actual file name
        val body = MultipartBody.Part.createFormData("group_img", "image_${System.currentTimeMillis()}.jpg", requestFile)

        // Execute the request
        apiService.uploadGroupImage(groupId, body).enqueue(object : Callback<UploadResponsed> {
            override fun onResponse(
                call: Call<UploadResponsed>,
                response: Response<UploadResponsed>
            ) {
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    callback(true, responseBody?.imagePath, responseBody?.message)
                } else {
                    callback(false, null, response.message())
                }
            }

            override fun onFailure(call: Call<UploadResponsed>, t: Throwable) {
                callback(false, null, t.message)
            }
        })
    }
}
