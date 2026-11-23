package com.solventa.app.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class CompressedPhoto(val file: File, val sizeBytes: Long, val width: Int, val height: Int)

sealed interface CompressionResult {
    data class Ok(val photo: CompressedPhoto) : CompressionResult
    data object Unreadable : CompressionResult
    data object TooLarge : CompressionResult
}

/** Cálculos puros del escalado (probados con JUnit, sin Android). */
object ImageScaling {
    /** Mayor potencia de 2 que deja el lado mayor decodificado ≥ [maxSide] (no se pierde nitidez). */
    fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        val longest = maxOf(width, height)
        while (longest / (sample * 2) >= maxSide) sample *= 2
        return sample
    }

    fun targetSize(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
        val longest = maxOf(width, height)
        if (longest <= maxSide) return width to height
        val scale = maxSide.toDouble() / longest
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }

    fun rotationDegrees(exifOrientation: Int): Int = when (exifOrientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}

/**
 * Recomprime toda foto a JPEG, lado mayor ≤ 2048 px y ≤ 5 MB (decisión A6). El JPEG nuevo no lleva EXIF:
 * no filtra la ubicación ni el modelo del teléfono; la ubicación se envía aparte y con permiso.
 */
class PhotoCompressor(
    private val maxSide: Int = 2048,
    private val maxBytes: Long = 5L * 1024 * 1024,
) {
    suspend fun compress(source: File, target: File): CompressionResult = withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext CompressionResult.Unreadable

        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageScaling.sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        }
        val decoded = BitmapFactory.decodeFile(source.absolutePath, options) ?: return@withContext CompressionResult.Unreadable
        val rotation = try {
            ImageScaling.rotationDegrees(
                ExifInterface(source.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL),
            )
        } catch (_: IOException) {
            0
        }
        val (width, height) = ImageScaling.targetSize(decoded.width, decoded.height, maxSide)
        val matrix = Matrix().apply {
            postScale(width.toFloat() / decoded.width, height.toFloat() / decoded.height)
            postRotate(rotation.toFloat())
        }
        val output = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (output !== decoded) decoded.recycle()
        try {
            for (quality in QUALITIES) {
                target.outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, quality, it) }
                if (target.length() <= maxBytes) {
                    return@withContext CompressionResult.Ok(CompressedPhoto(target, target.length(), output.width, output.height))
                }
            }
            target.delete()
            CompressionResult.TooLarge
        } catch (_: IOException) {
            target.delete()
            CompressionResult.Unreadable
        } finally {
            output.recycle()
        }
    }

    private companion object {
        val QUALITIES = listOf(85, 75, 65)
    }
}
