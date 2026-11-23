package com.solventa.app.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Miniatura de una evidencia local, decodificada en IO y reducida; sin librerías de imágenes. */
@Composable
fun rememberThumbnail(path: String?, isVideo: Boolean, sizePx: Int = 256): State<ImageBitmap?> =
    produceState<ImageBitmap?>(initialValue = null, path) {
        value = path?.let { withContext(Dispatchers.IO) { decode(it, isVideo, sizePx) } }
    }

private fun decode(path: String, isVideo: Boolean, sizePx: Int): ImageBitmap? = try {
    val bitmap: Bitmap? = if (isVideo) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            retriever.getFrameAtTime(0)
        } finally {
            retriever.release()
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = ImageScaling.sampleSize(bounds.outWidth, bounds.outHeight, sizePx) },
        )
    }
    bitmap?.asImageBitmap()
} catch (_: RuntimeException) {
    null
}
