import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.File
import java.io.FileOutputStream

suspend fun downloadAndSaveImage(context: Context, imageUrl: String, filename: String): File? {
    val loader = ImageLoader(context)
    val request = ImageRequest.Builder(context)
        .data(imageUrl)
        .build()

    val result = (loader.execute(request) as? SuccessResult)?.drawable
    val bitmap = (result as? Drawable)?.toBitmap()

    return bitmap?.let {
        val file = File(context.filesDir, filename)
        val outputStream = FileOutputStream(file)
        it.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
        file
    }
}
