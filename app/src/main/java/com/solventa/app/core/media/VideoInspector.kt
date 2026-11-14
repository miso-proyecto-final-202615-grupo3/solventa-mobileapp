package com.solventa.app.core.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContract
import java.io.File

data class VideoInfo(val durationMs: Long, val sizeBytes: Long, val mimeType: String?)

enum class VideoCheck { OK, TOO_LONG, TOO_LARGE, UNSUPPORTED }

/** Reglas puras del video de evidencia (HU4.1.2): MP4, máximo 60 s y 50 MB. */
object VideoRules {
    const val MAX_DURATION_MS = 60_000L
    const val MAX_BYTES = 50L * 1024 * 1024
    const val MIME = "video/mp4"

    fun check(info: VideoInfo?): VideoCheck = when {
        info == null || info.mimeType != MIME -> VideoCheck.UNSUPPORTED
        // Tolerancia de 1 s: algunas cámaras cortan en 60,4 s.
        info.durationMs > MAX_DURATION_MS + 1_000 -> VideoCheck.TOO_LONG
        info.sizeBytes > MAX_BYTES -> VideoCheck.TOO_LARGE
        else -> VideoCheck.OK
    }
}

class VideoInspector {
    fun inspect(file: File): VideoInfo? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            VideoInfo(
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                sizeBytes = file.length(),
                mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
            )
        } catch (_: RuntimeException) {
            null
        } finally {
            retriever.release()
        }
    }
}

/**
 * Cámara del sistema para grabar video con límite de 60 s y 50 MB. Algunas cámaras ignoran los
 * límites: por eso [VideoRules] vuelve a validar.
 */
class CaptureVideoWithLimit : ActivityResultContract<Uri, Boolean>() {
    override fun createIntent(context: Context, input: Uri): Intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        .putExtra(MediaStore.EXTRA_OUTPUT, input)
        .putExtra(MediaStore.EXTRA_DURATION_LIMIT, (VideoRules.MAX_DURATION_MS / 1000).toInt())
        .putExtra(MediaStore.EXTRA_SIZE_LIMIT, VideoRules.MAX_BYTES)
        .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean = resultCode == Activity.RESULT_OK
}
