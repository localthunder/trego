package com.helgolabs.trego.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ImageOrientationFixer {
    private const val TAG = "ImageOrientationFixer"

    /**
     * Process the camera image to fix any orientation issues
     * @param context Application context
     * @param uri Uri of the image to process
     * @return ByteArray of the properly oriented image or null if processing failed
     */
    suspend fun processImageFromCamera(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Processing camera image from URI: $uri")

            // Open input streams - one for EXIF data, one for bitmap
            val inputStream = context.contentResolver.openInputStream(uri)
            val exifStream = context.contentResolver.openInputStream(uri)

            if (inputStream == null || exifStream == null) {
                Log.e(TAG, "Failed to open input streams")
                return@withContext null
            }

            // Get orientation from EXIF data
            val orientation = getExifOrientation(exifStream)
            Log.d(TAG, "Image EXIF orientation: $orientation")

            // Decode the bitmap
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            exifStream.close()

            if (originalBitmap == null) {
                Log.e(TAG, "Failed to decode bitmap")
                return@withContext null
            }

            // Rotate the bitmap if needed
            val rotatedBitmap = if (orientation != ExifInterface.ORIENTATION_NORMAL) {
                rotateBitmap(originalBitmap, orientation)
            } else {
                originalBitmap
            }

            // Resize the bitmap for efficiency
            val resizedBitmap = ImageUtils.resizeBitmap(rotatedBitmap)

            // Convert to byte array
            val byteArrayOutputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()

            // Clean up
            byteArrayOutputStream.close()
            if (rotatedBitmap != originalBitmap) {
                originalBitmap.recycle()
            }
            if (resizedBitmap != rotatedBitmap) {
                rotatedBitmap.recycle()
            }

            byteArray
        } catch (e: Exception) {
            Log.e(TAG, "Error processing camera image", e)
            null
        }
    }

    /**
     * Save a properly oriented copy of the image file
     * @param context Application context
     * @param uri Uri of the image to process
     * @return Uri of the corrected image, or null if processing failed
     */
    suspend fun processAndSaveImage(context: Context, uri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            val processedImageBytes = processImageFromCamera(context, uri)
            if (processedImageBytes == null) {
                Log.e(TAG, "Failed to process image")
                return@withContext null
            }

            // Create a new file in the same directory as the original
            val outputDir = context.cacheDir
            val outputFile = File.createTempFile("corrected_", ".jpg", outputDir)

            // Save the processed image
            FileOutputStream(outputFile).use { output ->
                output.write(processedImageBytes)
            }

            // Create and return a content URI for the new file
            val newUri = Uri.fromFile(outputFile)
            Log.d(TAG, "Created corrected image at: $newUri")

            newUri
        } catch (e: Exception) {
            Log.e(TAG, "Error saving corrected image", e)
            null
        }
    }

    /**
     * Get the orientation from the image's EXIF data
     * @param inputStream InputStream of the image
     * @return EXIF orientation constant
     */
    private fun getExifOrientation(inputStream: InputStream): Int {
        return try {
            val exif = ExifInterface(inputStream)
            exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error reading EXIF data", e)
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    /**
     * Rotate a bitmap according to the EXIF orientation tag
     * @param bitmap Bitmap to rotate
     * @param orientation EXIF orientation constant
     * @return Rotated bitmap, or the original if no rotation was needed
     */
    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()

        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.preScale(-1f, 1f)
                matrix.postRotate(270f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.preScale(-1f, 1f)
                matrix.postRotate(90f)
            }
            else -> return bitmap
        }

        return try {
            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            // Don't recycle the original bitmap here since the caller will handle that
            rotatedBitmap
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory when rotating bitmap", e)
            bitmap
        }
    }
}