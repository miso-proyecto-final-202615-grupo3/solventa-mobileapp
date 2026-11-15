package com.solventa.app.feature.claims.evidence

import android.net.Uri
import com.solventa.app.core.media.CompressionResult
import com.solventa.app.core.media.EvidenceFileStore
import com.solventa.app.core.media.PhotoCompressor
import com.solventa.app.core.media.VideoCheck
import com.solventa.app.core.media.VideoInspector
import com.solventa.app.core.media.VideoRules
import com.solventa.app.data.claims.evidence.EvidenceKind
import java.io.File

sealed interface PreparedMedia {
    data class Ready(val file: File, val kind: EvidenceKind) : PreparedMedia
    data class Failed(val message: EvidenceMessage) : PreparedMedia
}

/**
 * Lo que el ViewModel necesita de Android para preparar una evidencia: archivos privados, compresión de
 * fotos, validación de videos y copia desde el selector. Detrás de una interfaz para probarlo con JUnit.
 */
interface EvidenceMedia {
    fun newCaptureFile(kind: EvidenceKind): File
    suspend fun preparePhoto(raw: File): PreparedMedia
    suspend fun prepareVideo(raw: File): PreparedMedia
    /** [uri] como texto: el ViewModel no depende de `android.net.Uri`. */
    suspend fun importFromGallery(uri: String): PreparedMedia
    fun discard(file: File?)
}

class AndroidEvidenceMedia(
    private val fileStore: EvidenceFileStore,
    private val compressor: PhotoCompressor,
    private val videoInspector: VideoInspector,
) : EvidenceMedia {
    override fun newCaptureFile(kind: EvidenceKind): File = fileStore.newFile(kind.extension)

    /** Toda foto se recomprime a JPEG sin EXIF; el original se borra. */
    override suspend fun preparePhoto(raw: File): PreparedMedia {
        val target = fileStore.newFile(EvidenceKind.PHOTO.extension)
        val result = compressor.compress(raw, target)
        raw.delete()
        return when (result) {
            is CompressionResult.Ok -> PreparedMedia.Ready(result.photo.file, EvidenceKind.PHOTO)
            CompressionResult.Unreadable -> PreparedMedia.Failed(EvidenceMessage.PHOTO_UNREADABLE)
            CompressionResult.TooLarge -> PreparedMedia.Failed(EvidenceMessage.PHOTO_TOO_LARGE)
        }
    }

    override suspend fun prepareVideo(raw: File): PreparedMedia {
        val message = when (VideoRules.check(videoInspector.inspect(raw))) {
            VideoCheck.OK -> return PreparedMedia.Ready(raw, EvidenceKind.VIDEO)
            VideoCheck.TOO_LONG -> EvidenceMessage.VIDEO_TOO_LONG
            VideoCheck.TOO_LARGE -> EvidenceMessage.VIDEO_TOO_LARGE
            VideoCheck.UNSUPPORTED -> EvidenceMessage.VIDEO_UNSUPPORTED
        }
        raw.delete()
        return PreparedMedia.Failed(message)
    }

    override suspend fun importFromGallery(uri: String): PreparedMedia {
        val parsed = Uri.parse(uri)
        val isVideo = fileStore.mimeType(parsed)?.startsWith("video/") == true
        val kind = if (isVideo) EvidenceKind.VIDEO else EvidenceKind.PHOTO
        val copy = fileStore.copyFrom(parsed, kind.extension)
            ?: return PreparedMedia.Failed(if (isVideo) EvidenceMessage.VIDEO_UNSUPPORTED else EvidenceMessage.PHOTO_UNREADABLE)
        return if (isVideo) prepareVideo(copy) else preparePhoto(copy)
    }

    override fun discard(file: File?) {
        file?.delete()
    }
}
