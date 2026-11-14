package com.solventa.app.core.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Evidencias en almacenamiento privado (`filesDir/evidencias`), nunca en la galería (decisión A3 de HU4.1.2).
 * La cámara del sistema recibe solo una URI temporal del [FileProvider]; los archivos se borran al confirmarse.
 */
class EvidenceFileStore(private val context: Context) {
    val directory: File get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    fun newFile(extension: String): File = File(directory, "${UUID.randomUUID()}.$extension")

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.evidencias", file)

    /** Copia a privado un archivo elegido en el selector del sistema. */
    suspend fun copyFrom(uri: Uri, extension: String): File? = withContext(Dispatchers.IO) {
        val target = newFile(extension)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            target
        } catch (_: IOException) {
            target.delete()
            null
        } catch (_: SecurityException) {
            target.delete()
            null
        }
    }

    fun mimeType(uri: Uri): String? = context.contentResolver.getType(uri)

    fun delete(path: String?) {
        if (path != null) File(path).takeIf { it.parentFile == directory }?.delete()
    }

    /** Al arrancar: borra lo que no esté pendiente de envío (capturas canceladas o restos de un cierre). */
    fun purgeExcept(paths: Set<String>) {
        directory.listFiles()?.filter { it.isFile && it.absolutePath !in paths && it.name != PENDING_FILE }?.forEach { it.delete() }
    }

    companion object {
        const val DIRECTORY = "evidencias"
        const val PENDING_FILE = "pendientes.json"
    }
}
