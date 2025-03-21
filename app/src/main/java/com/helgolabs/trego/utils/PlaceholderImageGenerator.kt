package com.helgolabs.trego.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.abs

/**
 * Generates visually appealing placeholder images for groups that don't have a custom image yet.
 * Images are deterministic based on the group ID and name to ensure consistency, unless regeneration is requested.
 */
object PlaceholderImageGenerator {

    private const val IMAGE_SIZE = 1024
    private const val PATTERN_COUNT = 6
    private const val PREFIX = "placeholder://"

    // Track which pattern was last used for a group ID
    private val lastPatternUsed = mutableMapOf<Int, Int>()

    /**
     * Checks if an image path is a placeholder image reference
     */
    fun isPlaceholderImage(path: String?): Boolean {
        if (path == null) return false

        // Check for placeholder reference format
        if (path.startsWith(PREFIX)) return true

        // Also check for local file path that contains placeholder pattern
        if (path.contains("/placeholder_images/placeholder_") && path.contains("_pattern_")) return true

        return false
    }

    /**
     * Extracts seed from a placeholder image path
     */
    fun extractSeedFromPath(path: String): Long? {
        if (path.isBlank()) return null

        return try {
            if (path.startsWith(PREFIX)) {
                // For placeholder reference format
                val seedStr = path.substringAfter("seed=").substringBefore("&")
                seedStr.toLongOrNull()
            } else if (path.contains("/placeholder_") && path.contains("_pattern_")) {
                // For local file path format
                val filename = path.substringAfterLast("/placeholder_").substringBefore("_pattern_")
                filename.toLongOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("PlaceholderImgGen", "Failed to extract seed from path: $path", e)
            null
        }
    }

    /**
     * Extracts pattern type from a placeholder image path
     */
    fun extractPatternTypeFromPath(path: String?): Int? {
        if (path == null || path.isBlank()) return null

        return try {
            if (path.startsWith(PREFIX)) {
                // First try with &pattern= format
                if (path.contains("&pattern=")) {
                    val patternStr = path.substringAfter("&pattern=").substringBefore("&")
                    return patternStr.toIntOrNull()
                }

                // Try with pattern= at the beginning of query params
                if (path.contains("pattern=")) {
                    val patternStr = path.substringAfter("pattern=").substringBefore("&")
                    return patternStr.toIntOrNull()
                }
            } else if (path.contains("_pattern_")) {
                // For local file path format
                val patternStr = path.substringAfter("_pattern_").substringBefore(".")
                return patternStr.toIntOrNull()
            }

            // If we can't find it, return null
            null
        } catch (e: Exception) {
            Log.e("PlaceholderImgGen", "Failed to extract pattern type: $path", e)
            null
        }
    }

    /**
     * Generates a placeholder image reference with seed info and pattern type
     */
    fun generatePlaceholderReference(groupId: Int, groupName: String, useTimestamp: Boolean = false, forcePattern: Int? = null): String {
        val seed = generateSeed(groupId, groupName, useTimestamp)

        // Determine pattern type
        val patternType = determinePatternType(groupId, forcePattern, seed)

        // Store this pattern as the last used for this group
        lastPatternUsed[groupId] = patternType

        return "$PREFIX" + "seed=$seed&pattern=$patternType&groupId=$groupId"
    }

    /**
     * Determines which pattern type to use, ensuring we don't use the same one twice in a row for regeneration
     */
    private fun determinePatternType(groupId: Int, forcePattern: Int?, seed: Long): Int {
        // If pattern is forced, use that
        if (forcePattern != null) {
            return forcePattern % PATTERN_COUNT
        }

        // Get the last pattern used for this group
        val lastPattern = lastPatternUsed[groupId]

        // If we're regenerating, avoid the last pattern used
        return if (lastPattern != null) {
            // Create a seed-based random but exclude the last pattern
            val random = java.util.Random(seed)
            var newPattern = abs(random.nextInt() % PATTERN_COUNT)

            // If we got the same pattern, pick the next one
            if (newPattern == lastPattern) {
                newPattern = (newPattern + 1) % PATTERN_COUNT
            }

            newPattern
        } else {
            // For first time, just use the seed to pick a pattern
            abs((seed % PATTERN_COUNT).toInt())
        }
    }

    /**
     * Generates or retrieves a placeholder image for a given placeholder path/reference
     *
     * @param context Application context
     * @param imagePath The placeholder image path/reference
     * @return The file path of the generated or retrieved image
     */
    fun getImageForPath(context: Context, imagePath: String): String {
        Log.d("PlaceholderImgGen", "Getting image for path: $imagePath")

        // First, check if the provided path is already a local file path
        if (imagePath.startsWith("/data/") || imagePath.contains("/files/placeholder_images/")) {
            Log.d("PlaceholderImgGen", "Already a local path, returning: $imagePath")
            return imagePath
        }

        if (!imagePath.startsWith(PREFIX)) {
            Log.e("PlaceholderImgGen", "Not a valid placeholder reference: $imagePath")
            return ""
        }

        // Extract seed and pattern
        val seed = extractSeedFromPath(imagePath)
        if (seed == null) {
            Log.e("PlaceholderImgGen", "Failed to extract seed from path: $imagePath")
            return ""
        }

        val patternType = extractPatternTypeFromPath(imagePath) ?: 0
        val cacheKey = "placeholder_${seed}_pattern_${patternType}.png"

        // Check if image already exists in cache
        val imageDir = getImageDirectory(context)
        val cachedFile = File(imageDir, cacheKey)

        if (cachedFile.exists()) {
            Log.d("PlaceholderImgGen", "Found cached placeholder: ${cachedFile.absolutePath}")
            return cachedFile.absolutePath
        }

        // Generate and cache the image
        try {
            Log.d("PlaceholderImgGen", "Generating new placeholder with seed $seed and pattern $patternType")
            val bitmap = createPlaceholderImage(seed, patternType)
            val savedPath = saveBitmap(context, bitmap, cacheKey)
            Log.d("PlaceholderImgGen", "Saved new placeholder at: $savedPath")
            return savedPath
        } catch (e: Exception) {
            Log.e("PlaceholderImgGen", "Error generating placeholder image", e)
            return ""
        }
    }

    /**
     * Generates a placeholder image for a group and saves it to the app's internal storage.
     *
     * @param context Application context
     * @param groupId The group ID
     * @param groupName The group name
     * @param useTimestamp If true, adds current timestamp to create unique image
     * @param forcePattern Force a specific pattern (0-5)
     * @return The file path of the generated image
     */
    fun generateForGroup(
        context: Context,
        groupId: Int,
        groupName: String,
        useTimestamp: Boolean = false,
        forcePattern: Int? = null
    ): String {
        // Create a reference first
        val reference = generatePlaceholderReference(groupId, groupName, useTimestamp, forcePattern)

        // Then get or create the image
        return getImageForPath(context, reference)
    }

    /**
     * Creates a seed from the group ID and name that will be used to generate a consistent image
     * Optionally includes timestamp for regeneration
     */
    private fun generateSeed(groupId: Int, groupName: String, useTimestamp: Boolean = false): Long {
        val timestamp = if (useTimestamp) System.currentTimeMillis() else 0L
        val combined = "$groupId:$groupName:$timestamp"

        Log.d("PlaceholderImgGen", "Generating seed from: $combined")

        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(combined.toByteArray())

        // Convert first 8 bytes to a long
        var seed = 0L
        for (i in 0 until 8.coerceAtMost(digest.size)) {
            seed = (seed shl 8) or (digest[i].toLong() and 0xFF)
        }
        return seed
    }

    /**
     * Creates the placeholder image with a visually appealing pattern
     */
    private fun createPlaceholderImage(seed: Long, patternType: Int = -1): Bitmap {
        val random = java.util.Random(seed)
        val bitmap = Bitmap.createBitmap(IMAGE_SIZE, IMAGE_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Choose a pattern type based on the seed if not specified
        val pattern = if (patternType in 0 until PATTERN_COUNT) {
            patternType
        } else {
            abs(random.nextInt() % PATTERN_COUNT)
        }

        Log.d("PlaceholderImgGen", "Creating pattern type: $pattern with seed: $seed")

        when (pattern) {
            0 -> drawGradientPattern(canvas, random)
            1 -> drawGeometricPattern(canvas, random)
            2 -> drawWavePattern(canvas, random)
            3 -> drawMosaicPattern(canvas, random)
            4 -> drawCirclePattern(canvas, random)
            5 -> drawStripePattern(canvas, random)
            else -> drawGradientPattern(canvas, random) // fallback
        }

        return bitmap
    }

    // Drawing functions remain the same...
    private fun drawGradientPattern(canvas: Canvas, random: java.util.Random) {
        // Generate two pastel colors for the gradient
        val color1 = getPastelColor(random)
        val color2 = getPastelColor(random)

        // Determine gradient direction
        val angle = random.nextFloat() * 360
        val radians = Math.toRadians(angle.toDouble())
        val dx = (Math.cos(radians) * IMAGE_SIZE).toFloat()
        val dy = (Math.sin(radians) * IMAGE_SIZE).toFloat()

        val paint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, dx, dy,
                color1, color2,
                Shader.TileMode.CLAMP
            )
        }

        canvas.drawRect(0f, 0f, IMAGE_SIZE.toFloat(), IMAGE_SIZE.toFloat(), paint)

        // Add some subtle shapes for visual interest
        val shapePaint = Paint().apply {
            color = Color.WHITE
            alpha = 20 + random.nextInt(30) // More subtle transparency for pastel look
            style = Paint.Style.FILL
        }

        val shapeCount = 3 + random.nextInt(5)
        repeat(shapeCount) {
            val size = 50 + random.nextInt(200)
            val x = random.nextInt(IMAGE_SIZE)
            val y = random.nextInt(IMAGE_SIZE)

            when (random.nextInt(3)) {
                0 -> canvas.drawCircle(x.toFloat(), y.toFloat(), size.toFloat(), shapePaint)
                1 -> canvas.drawRect(x.toFloat(), y.toFloat(), (x + size).toFloat(), (y + size).toFloat(), shapePaint)
                2 -> {
                    val path = Path()
                    path.moveTo(x.toFloat(), y.toFloat())
                    path.lineTo((x + size).toFloat(), y.toFloat())
                    path.lineTo((x + size/2).toFloat(), (y + size).toFloat())
                    path.close()
                    canvas.drawPath(path, shapePaint)
                }
            }
        }
    }

    private fun drawGeometricPattern(canvas: Canvas, random: java.util.Random) {
        // Background color - soft pastel
        val bgColor = getPastelColor(random)
        canvas.drawColor(bgColor)

        val paint = Paint().apply {
            style = Paint.Style.FILL
        }

        // Draw a grid of shapes
        val gridSize = 4 + random.nextInt(4)
        val cellSize = IMAGE_SIZE / gridSize

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                if ((row + col) % 2 == 0) {
                    paint.color = getPastelColor(random)
                    paint.alpha = 180 + random.nextInt(50) // Slightly transparent for softer appearance

                    val x = col * cellSize
                    val y = row * cellSize

                    when (random.nextInt(3)) {
                        0 -> canvas.drawRect(x.toFloat(), y.toFloat(),
                            (x + cellSize).toFloat(), (y + cellSize).toFloat(), paint)
                        1 -> {
                            val path = Path()
                            path.moveTo(x.toFloat(), y.toFloat())
                            path.lineTo((x + cellSize).toFloat(), y.toFloat())
                            path.lineTo((x + cellSize/2).toFloat(), (y + cellSize).toFloat())
                            path.close()
                            canvas.drawPath(path, paint)
                        }
                        2 -> canvas.drawCircle((x + cellSize/2).toFloat(),
                            (y + cellSize/2).toFloat(),
                            (cellSize/2).toFloat(), paint)
                    }
                }
            }
        }
    }

    private fun drawWavePattern(canvas: Canvas, random: java.util.Random) {
        // Background
        val bgColor = getPastelColor(random)
        canvas.drawColor(bgColor)

        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f + random.nextFloat() * 10f // Thinner strokes
            color = getPastelColor(random)
        }

        val waveCount = 5 + random.nextInt(8)
        val amplitude = 15 + random.nextInt(40) // Smaller amplitude for subtler waves
        val frequency = 0.01f + random.nextFloat() * 0.02f
        val phaseShift = random.nextFloat() * 10f

        for (wave in 0 until waveCount) {
            val path = Path()

            // Vary each wave slightly
            val currentAmplitude = amplitude + random.nextInt(15) - 7
            val yOffset = random.nextInt(IMAGE_SIZE)

            paint.color = getPastelColor(random)
            paint.alpha = 60 + random.nextInt(100) // Lower alpha for pastels

            path.moveTo(0f, yOffset.toFloat())

            for (x in 0 until IMAGE_SIZE step 2) {
                val y = yOffset +
                        (Math.sin((x * frequency) + phaseShift + (wave * 0.5)) * currentAmplitude).toFloat()
                path.lineTo(x.toFloat(), y)
            }

            canvas.drawPath(path, paint)
        }
    }

    private fun drawMosaicPattern(canvas: Canvas, random: java.util.Random) {
        val tileSize = 20 + random.nextInt(60)
        val rows = IMAGE_SIZE / tileSize
        val cols = IMAGE_SIZE / tileSize

        val paint = Paint().apply {
            style = Paint.Style.FILL
        }

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                paint.color = getPastelColor(random)
                paint.alpha = 150 + random.nextInt(105) // Vary transparency for softer look

                val x = col * tileSize
                val y = row * tileSize

                canvas.drawRect(
                    x.toFloat(),
                    y.toFloat(),
                    (x + tileSize).toFloat(),
                    (y + tileSize).toFloat(),
                    paint
                )
            }
        }
    }

    private fun drawCirclePattern(canvas: Canvas, random: java.util.Random) {
        val bgColor = getPastelColor(random)
        canvas.drawColor(bgColor)

        val paint = Paint().apply {
            style = Paint.Style.FILL
        }

        val circleCount = 8 + random.nextInt(12) // Fewer circles for a cleaner look

        for (i in 0 until circleCount) {
            paint.color = getPastelColor(random)
            paint.alpha = 60 + random.nextInt(100) // More transparency for overlapping circles

            val radius = 20 + random.nextInt(120)
            val x = random.nextInt(IMAGE_SIZE)
            val y = random.nextInt(IMAGE_SIZE)

            canvas.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), paint)
        }
    }

    private fun drawStripePattern(canvas: Canvas, random: java.util.Random) {
        val bgColor = getPastelColor(random)
        canvas.drawColor(bgColor)

        val paint = Paint().apply {
            style = Paint.Style.FILL
        }

        val stripeCount = 5 + random.nextInt(12)
        val stripeWidth = IMAGE_SIZE / stripeCount

        val isHorizontal = random.nextBoolean()

        for (i in 0 until stripeCount) {
            if (i % 2 == 0) {
                paint.color = getPastelColor(random)
                paint.alpha = 130 + random.nextInt(70) // Semi-transparent for softer edges

                if (isHorizontal) {
                    val y = i * stripeWidth
                    canvas.drawRect(
                        0f,
                        y.toFloat(),
                        IMAGE_SIZE.toFloat(),
                        (y + stripeWidth).toFloat(),
                        paint
                    )
                } else {
                    val x = i * stripeWidth
                    canvas.drawRect(
                        x.toFloat(),
                        0f,
                        (x + stripeWidth).toFloat(),
                        IMAGE_SIZE.toFloat(),
                        paint
                    )
                }
            }
        }
    }

    /**
     * Generates a pastel color with soft, muted tones.
     *
     * @param random Random number generator
     * @return Pastel color as an ARGB integer
     */
    private fun getPastelColor(random: java.util.Random): Int {
        // Pastel hues - full spectrum
        val hue = random.nextFloat() * 360

        // Lower saturation for pastel effect (0.3-0.5)
        val saturation = 0.3f + random.nextFloat() * 0.2f

        // Higher brightness for pastel effect (0.85-0.98)
        val lightness = 0.85f + random.nextFloat() * 0.13f

        // Convert HSL to HSV
        // For HSL->HSV, we need to adjust the saturation and value
        // S_hsv = S_hsl * (1 - L_hsl/2) when L_hsl ≤ 0.5
        // S_hsv = S_hsl * (1 - (1-L_hsl)/2) when L_hsl > 0.5
        val adjustedSaturation = if (lightness <= 0.5) {
            saturation * (1 - lightness/2)
        } else {
            saturation * (1 - (1-lightness)/2)
        }

        // V_hsv = L_hsl + S_hsl * min(L_hsl, 1-L_hsl)
        val value = lightness + saturation * Math.min(lightness, 1f-lightness)

        val hsv = floatArrayOf(hue, adjustedSaturation, value)
        return Color.HSVToColor(hsv)
    }

    /**
     * Gets the image directory for placeholder images
     */
    private fun getImageDirectory(context: Context): File {
        val imageDir = File(context.filesDir, "placeholder_images")
        if (!imageDir.exists()) {
            imageDir.mkdirs()
        }
        return imageDir
    }

    /**
     * Saves a bitmap to the application's internal storage
     */
    private fun saveBitmap(context: Context, bitmap: Bitmap, filename: String): String {
        val imagesDir = getImageDirectory(context)
        val file = File(imagesDir, filename)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        return file.absolutePath
    }
}