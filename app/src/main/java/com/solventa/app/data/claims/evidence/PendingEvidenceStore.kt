package com.solventa.app.data.claims.evidence

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/** Política de reintentos: retroceso exponencial 2 s, 4 s, 8 s (táctica de disponibilidad). */
class RetryPolicy(val maxAttempts: Int = 3, private val baseDelayMs: Long = 2_000) {
    fun delayFor(attempt: Int): Long = baseDelayMs shl (attempt - 1).coerceIn(0, 10)

    fun isRetryable(problem: EvidenceProblem): Boolean =
        problem == EvidenceProblem.NETWORK || problem == EvidenceProblem.SERVICE_UNAVAILABLE
}

/**
 * Evidencias sin enviar, en `filesDir/evidencias/pendientes.json`. Si Android cierra la app, siguen ahí y
 * se ofrecen de nuevo desde Inicio. Escritura atómica: archivo temporal + renombrar.
 */
class PendingEvidenceStore(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val serializer = ListSerializer(LocalEvidence.serializer())

    suspend fun load(): List<LocalEvidence> = withContext(ioDispatcher) {
        try {
            if (!file.exists()) emptyList() else json.decodeFromString(serializer, file.readText())
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        } catch (_: IOException) {
            emptyList()
        }
    }

    suspend fun save(items: List<LocalEvidence>) = withContext(ioDispatcher) {
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(json.encodeToString(serializer, items.filter { it.pending }))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (_: IOException) {
            // Sin espacio: la cola sigue en memoria mientras la app esté abierta.
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
