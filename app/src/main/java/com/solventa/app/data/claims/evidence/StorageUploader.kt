package com.solventa.app.data.claims.evidence

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface StorageUploadResult {
    data object Uploaded : StorageUploadResult
    /** La política firmada venció o no se cumplió una condición: hay que pedir otra carga. */
    data object Expired : StorageUploadResult
    /** El almacenamiento no acepta el archivo (p. ej. demasiado grande): no se reintenta. */
    data object Refused : StorageUploadResult
    data object NetworkError : StorageUploadResult
}

/** Strategy: subir a S3/MinIO con la carga prefirmada o simular sin red. */
fun interface StorageUploader {
    suspend fun upload(ticket: UploadTicket, file: File, mime: String, onProgress: (Float) -> Unit): StorageUploadResult
}

/**
 * `POST` multipart a la URL firmada: todos los campos en el orden recibido y el archivo **al final**
 * (requisito de S3). El archivo se lee por bloques (un video no se carga en memoria). Sin `Authorization`:
 * la firma es la credencial.
 */
class PresignedPostUploader(
    private val client: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StorageUploader {
    override suspend fun upload(ticket: UploadTicket, file: File, mime: String, onProgress: (Float) -> Unit): StorageUploadResult =
        withContext(ioDispatcher) {
            if (!file.exists()) return@withContext StorageUploadResult.Refused
            val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                ticket.fields.forEach { (name, value) -> addFormDataPart(name, value) }
                addFormDataPart("file", file.name, ProgressFileBody(file, mime.toMediaType(), onProgress))
            }.build()
            try {
                client.newCall(Request.Builder().url(ticket.url).post(body).build()).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    when {
                        response.isSuccessful -> StorageUploadResult.Uploaded
                        response.code == 403 -> StorageUploadResult.Expired
                        response.code in 400..499 && "EntityTooLarge" in text -> StorageUploadResult.Refused
                        response.code in 400..499 -> StorageUploadResult.Refused
                        else -> StorageUploadResult.NetworkError
                    }
                }
            } catch (_: IOException) {
                StorageUploadResult.NetworkError
            }
        }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

/** Cuerpo que lee el archivo por bloques e informa el avance. */
internal class ProgressFileBody(
    private val file: File,
    private val mediaType: MediaType,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType = mediaType

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength().coerceAtLeast(1)
        var written = 0L
        file.source().use { source ->
            while (true) {
                val read = source.read(sink.buffer, CHUNK)
                if (read == -1L) break
                written += read
                sink.flush()
                onProgress(written.toFloat() / total)
            }
        }
    }

    private companion object {
        const val CHUNK = 64L * 1024
    }
}

/** Subida simulada para el backend simulado: avanza en 4 pasos sin red. */
class FakeStorageUploader(private val stepDelayMs: Long = 250) : StorageUploader {
    override suspend fun upload(ticket: UploadTicket, file: File, mime: String, onProgress: (Float) -> Unit): StorageUploadResult {
        repeat(4) {
            delay(stepDelayMs)
            onProgress((it + 1) / 4f)
        }
        return StorageUploadResult.Uploaded
    }
}
