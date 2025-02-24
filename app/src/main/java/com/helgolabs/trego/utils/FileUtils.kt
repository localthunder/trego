import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.ImageLoader
import java.io.File
import java.io.FileOutputStream

fun isLogoSaved(context: Context, institutionId: String): Boolean {
    val logoFile = File(context.filesDir, "$institutionId.png")
    return logoFile.exists()
}

suspend fun downloadAndSaveImage(context: Context, imageUrl: String, filename: String): File? {
    val loader = ImageLoader(context)
    val request = ImageRequest.Builder(context)
        .data(imageUrl)
        .build()

    val result = loader.execute(request)
    val bitmap = when (result) {
        is SuccessResult -> (result.image as? BitmapDrawable)?.bitmap
        else -> null
    }


    return bitmap?.let {
        val file = File(context.filesDir, filename)
        val outputStream = FileOutputStream(file)
        it.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
        file
    }
}
