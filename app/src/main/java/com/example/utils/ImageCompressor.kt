package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class CompressionResult(
    val file: File,
    val fileSizeBytes: Long,
    val fileSizeKb: Float,
    val width: Int,
    val height: Int,
    val qualityUsed: Int,
    val format: String,
    val isUnder50Kb: Boolean
)

object ImageCompressor {

    /**
     * Reads a Bitmap from Uri, correcting orientation based on EXIF metadata.
     */
    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            // Read EXIF orientation
            val exifStream = context.contentResolver.openInputStream(uri)
            val orientation = exifStream?.let {
                val exif = ExifInterface(it)
                it.close()
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            rotateBitmapIfRequired(originalBitmap, orientation)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun rotateBitmapIfRequired(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }

    /**
     * Enhances document legibility by boosting contrast and sharpening text/signatures.
     */
    fun enhanceDocumentBitmap(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Contrast and brightness boost for handwriting and printed sheets
        val contrast = 1.35f
        val brightness = 15f
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    /**
     * Compresses bitmap strictly under the target size (default 49.0 KB to ensure < 50KB).
     * Maintains maximum resolution and sharpness.
     */
    fun compressToTargetSize(
        source: Bitmap,
        targetFile: File,
        targetMaxKb: Int = 50,
        format: String = "JPG",
        enhanceForDocument: Boolean = false
    ): CompressionResult {
        // Safe target bytes: 49.0 KB = 50,176 bytes to ensure strict portal compliance
        val targetBytes = ((targetMaxKb - 1).coerceAtLeast(20) * 1024)

        var processedBitmap = if (enhanceForDocument) {
            enhanceDocumentBitmap(source)
        } else {
            source
        }

        val isPng = format.equals("PNG", ignoreCase = true)
        val compressFormat = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG

        // Initial max dimension
        var maxDimension = if (isPng) 900 else 1280
        var currentBitmap = resizeToMaxDimension(processedBitmap, maxDimension)

        var quality = if (isPng) 100 else 85
        var outputBytes = ByteArrayOutputStream()

        var attempts = 0
        val maxAttempts = 12

        while (attempts < maxAttempts) {
            outputBytes.reset()
            currentBitmap.compress(compressFormat, quality, outputBytes)
            val currentSize = outputBytes.size()

            if (currentSize <= targetBytes) {
                // If it's already well under target and JPG, we are done
                break
            }

            // Exceeds target! Need reduction
            attempts++
            if (isPng) {
                // PNG ignores quality param in Android, so reduce dimensions
                maxDimension = (maxDimension * 0.85f).roundToInt().coerceAtLeast(320)
                val resized = resizeToMaxDimension(processedBitmap, maxDimension)
                if (currentBitmap != processedBitmap && currentBitmap != resized) {
                    currentBitmap.recycle()
                }
                currentBitmap = resized
            } else {
                // For JPG: reduce quality first down to 35, then reduce dimensions
                if (quality > 40) {
                    quality -= 12
                } else {
                    maxDimension = (maxDimension * 0.82f).roundToInt().coerceAtLeast(400)
                    val resized = resizeToMaxDimension(processedBitmap, maxDimension)
                    if (currentBitmap != processedBitmap && currentBitmap != resized) {
                        currentBitmap.recycle()
                    }
                    currentBitmap = resized
                    quality = 65
                }
            }
        }

        // Final safety check: if still above targetBytes, force downscale
        while (outputBytes.size() > targetBytes && maxDimension > 250) {
            maxDimension = (maxDimension * 0.8f).roundToInt()
            val resized = resizeToMaxDimension(processedBitmap, maxDimension)
            if (currentBitmap != processedBitmap && currentBitmap != resized) {
                currentBitmap.recycle()
            }
            currentBitmap = resized
            outputBytes.reset()
            currentBitmap.compress(compressFormat, if (isPng) 100 else 45, outputBytes)
        }

        // Write to destination file
        targetFile.parentFile?.mkdirs()
        FileOutputStream(targetFile).use { fos ->
            outputBytes.writeTo(fos)
            fos.flush()
        }

        val finalSizeBytes = targetFile.length()
        val finalSizeKb = finalSizeBytes / 1024f
        val finalWidth = currentBitmap.width
        val finalHeight = currentBitmap.height

        if (currentBitmap != processedBitmap && currentBitmap != source) {
            currentBitmap.recycle()
        }
        if (processedBitmap != source) {
            processedBitmap.recycle()
        }

        return CompressionResult(
            file = targetFile,
            fileSizeBytes = finalSizeBytes,
            fileSizeKb = finalSizeKb,
            width = finalWidth,
            height = finalHeight,
            qualityUsed = quality,
            format = if (isPng) "PNG" else "JPG",
            isUnder50Kb = finalSizeKb < 50.0f
        )
    }

    private fun resizeToMaxDimension(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxCurrent = max(width, height)

        if (maxCurrent <= maxDim) return bitmap

        val scale = maxDim.toFloat() / maxCurrent
        val newWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val newHeight = (height * scale).roundToInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
