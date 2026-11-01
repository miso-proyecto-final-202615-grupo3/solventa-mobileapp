package com.solventa.app.core.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Convierte un fotograma de CameraX a JPEG en memoria: rotado, ≤ 1280 px y ≤ 512 KB. */
object FrameEncoder {
    const val MAX_BYTES = 524_288
    private const val MAX_SIDE = 1280

    fun encode(imageProxy: ImageProxy): ByteArray? {
        val source = imageProxy.toBitmap()
        val rotated = rotateAndScale(source, imageProxy.imageInfo.rotationDegrees)
        try {
            return compress(rotated, 85)?.takeIf { it.size <= MAX_BYTES } ?: compress(rotated, 70)?.takeIf { it.size <= MAX_BYTES }
        } finally {
            if (rotated !== source) rotated.recycle()
            source.recycle()
        }
    }

    private fun rotateAndScale(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        val scale = MAX_SIDE.toFloat() / max(bitmap.width, bitmap.height)
        if (rotationDegrees == 0 && scale >= 1f) return bitmap
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotationDegrees != 0) postRotate(rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray? = ByteArrayOutputStream().use { output ->
        if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) output.toByteArray() else null
    }
}
