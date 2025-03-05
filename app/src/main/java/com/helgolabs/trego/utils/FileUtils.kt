import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Scale
import com.helgolabs.trego.utils.CoilSetup
import java.io.File
import java.io.FileOutputStream

private const val TAG = "FileUtils"

fun isLogoSaved(context: Context, institutionId: String): Boolean {
    val logoFile = File(context.filesDir, "$institutionId.png")
    val exists = logoFile.exists() && logoFile.length() > 0
    Log.d(TAG, "isLogoSaved check for $institutionId: $exists (path: ${logoFile.absolutePath}, size: ${if (logoFile.exists()) logoFile.length() else 0} bytes)")
    return exists
}

/**
 * Downloads an image from URL and saves it locally
 * Specifically handles Coil3 BitmapImage type
 */
suspend fun downloadAndSaveImage(context: Context, imageUrl: String, filename: String): File? {
    Log.d(TAG, "Starting download for $filename from URL: $imageUrl")

    try {
        // Check if file already exists
        val file = File(context.filesDir, filename)
        if (file.exists() && file.length() > 0) {
            Log.d(TAG, "File already exists: ${file.absolutePath}, size: ${file.length()} bytes")
            return file
        }

        // Create a special request that specifically requests a bitmap
        val loader = CoilSetup.getImageLoader(context)
        Log.d(TAG, "Created image loader for $filename")

        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .scale(Scale.FILL)
            .size(512, 512)  // Specify a reasonable size
            .build()

        val result = loader.execute(request)
        Log.d(TAG, "Request executed, result type: ${result::class.java.simpleName}")

        if (result is SuccessResult) {
            // Get the image object
            val image = result.image
            Log.d(TAG, "Got image of type: ${image::class.java.simpleName}")

            // Handle Coil3's BitmapImage type
            val bitmap = when (image) {
                // For Coil3 BitmapImage
                is coil3.BitmapImage -> {
                    Log.d(TAG, "Converting Coil3 BitmapImage to Android Bitmap")
                    try {
                        // Extract the bitmap using reflection if necessary
                        val bitmapField = image.javaClass.getDeclaredField("bitmap")
                        bitmapField.isAccessible = true
                        bitmapField.get(image) as? Bitmap
                    } catch (e: Exception) {
                        Log.e(TAG, "Error extracting bitmap from BitmapImage", e)
                        null
                    }
                }
                // For Android BitmapDrawable
                is android.graphics.drawable.BitmapDrawable -> {
                    Log.d(TAG, "Image is Android BitmapDrawable")
                    image.bitmap
                }
                // Try other methods if the above fails
                else -> {
                    Log.d(TAG, "Using alternative bitmap creation approach")
                    try {
                        // Try a direct conversion to Bitmap
                        val width = 512
                        val height = 512
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bitmap)

                        // Draw the image to the canvas
                        image.draw(canvas)
                        bitmap
                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating bitmap from drawable", e)
                        null
                    }
                }
            }

            if (bitmap == null) {
                Log.e(TAG, "Failed to extract bitmap for $filename")
                return null
            }

            Log.d(TAG, "Successfully extracted bitmap: ${bitmap.width}x${bitmap.height}")

            // Save the bitmap to a file
            try {
                file.parentFile?.mkdirs()

                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }

                Log.d(TAG, "Saved bitmap to file: ${file.absolutePath}, size: ${file.length()} bytes")
                return file
            } catch (e: Exception) {
                Log.e(TAG, "Error saving bitmap to file", e)
                // Clean up any partial file
                if (file.exists()) {
                    file.delete()
                }
                return null
            }
        } else {
            Log.e(TAG, "Failed to download image: ${result::class.java.simpleName}")
            return null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Exception during image download", e)
        return null
    }
}