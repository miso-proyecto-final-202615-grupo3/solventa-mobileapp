package com.solventa.app.data.claims.evidence

import com.solventa.app.data.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Cola de envío de evidencias en el ámbito de la aplicación (decisión A2 de HU4.1.2): sigue aunque el
 * usuario cambie de pantalla y procesa una evidencia a la vez. Cada evidencia es independiente: si una
 * falla, se marca y la cola continúa con las demás (criterio "informar sin perder el resto del reporte").
 *
 * Por evidencia: pedir carga → subir directo al almacenamiento → confirmar. Errores de red o servicio
 * se reintentan con retroceso exponencial ([RetryPolicy]); los rechazos no.
 */
class EvidenceUploadQueue(
    private val repository: EvidenceRepository,
    private val uploader: StorageUploader,
    private val sessionStore: SessionStore,
    private val store: PendingEvidenceStore,
    private val deleteFile: (String?) -> Unit,
    private val policy: RetryPolicy,
    private val scope: CoroutineScope,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val _items = MutableStateFlow<List<LocalEvidence>>(emptyList())
    val items: StateFlow<List<LocalEvidence>> = _items.asStateFlow()

    private val mutex = Mutex()
    // Solo en memoria, nunca en disco (la URL firmada no se guarda). Si se pierde (app cerrada, respuesta perdida),
    // se pide de nuevo con la misma Idempotency-Key ([LocalEvidence.localId]) y el backend renueva la misma
    // evidencia: no se gasta otro cupo.
    private val tickets = mutableMapOf<String, UploadTicket>()

    /**
     * Carga los pendientes guardados y se reanuda sola cada vez que hay sesión. [onLoaded] recibe las rutas
     * de los archivos que siguen pendientes (para limpiar el resto sin borrar nada que falte enviar).
     */
    fun start(onLoaded: (Set<String>) -> Unit = {}) {
        scope.launch {
            val saved = store.load().map { item ->
                // Lo que quedó a medias al cerrar la app vuelve a la cola.
                if (item.state in IN_PROGRESS) item.copy(state = EvidenceUploadState.QUEUED) else item
            }
            _items.update { current -> saved + current.filter { c -> saved.none { it.localId == c.localId } } }
            onLoaded(_items.value.mapNotNull { it.filePath }.toSet())
            sessionStore.session.map { it?.accessToken }.filterNotNull().distinctUntilChanged().collect { resume() }
        }
    }

    fun itemsFor(claimId: String): Flow<List<LocalEvidence>> = items.map { list -> list.filter { it.claimId == claimId } }

    /** Siniestros con evidencias sin enviar: (id, folio). */
    fun pendingClaims(): Flow<List<Pair<String, String>>> = items.map { list ->
        list.filter { it.pending }.map { it.claimId to it.claimNumber }.distinct()
    }

    fun enqueue(
        claimId: String,
        claimNumber: String,
        file: File,
        kind: EvidenceKind,
        origin: EvidenceOrigin,
        capturedAt: OffsetDateTime,
    ): String {
        val item = LocalEvidence(
            localId = newId(),
            claimId = claimId,
            claimNumber = claimNumber,
            kind = kind,
            origin = origin,
            filePath = file.absolutePath,
            sizeBytes = file.length(),
            capturedAt = capturedAt.toString(),
        )
        _items.update { it + item }
        persist()
        process()
        return item.localId
    }

    /** "Reintentar": vuelve a la cola con el contador en cero. */
    fun retry(localId: String) {
        updateItem(localId) {
            if (it.state == EvidenceUploadState.FAILED) it.copy(state = EvidenceUploadState.QUEUED, attempts = 0, problem = null) else it
        }
        persist()
        process()
    }

    /** "Quitar": borra el archivo local. Lo ya confirmado en el servidor no se toca. */
    fun remove(localId: String) {
        val item = _items.value.firstOrNull { it.localId == localId } ?: return
        if (item.state in IN_PROGRESS) return
        deleteFile(item.filePath)
        tickets.remove(localId)
        _items.update { list -> list.filterNot { it.localId == localId } }
        persist()
    }

    /**
     * Reintenta lo que espera sesión, falló por red o quedó detenido porque la sesión venció (llamado al iniciar
     * sesión o desbloquear: ya hay token nuevo).
     */
    fun resume() {
        _items.update { list ->
            list.map {
                if (it.state == EvidenceUploadState.FAILED && resumable(it.problem)) {
                    it.copy(state = EvidenceUploadState.QUEUED, attempts = 0, problem = null)
                } else {
                    it
                }
            }
        }
        process()
    }

    private fun resumable(problem: EvidenceProblem?): Boolean =
        problem == null || problem == EvidenceProblem.SESSION_EXPIRED || policy.isRetryable(problem)

    private fun process() {
        scope.launch {
            mutex.withLock {
                while (true) {
                    val next = _items.value.firstOrNull { it.state == EvidenceUploadState.QUEUED } ?: break
                    if (accessToken() == null) break // sin sesión: espera a resume()
                    send(next.localId)
                }
            }
        }
    }

    private suspend fun send(localId: String) {
        while (true) {
            val item = current(localId) ?: return
            val outcome = attempt(item)
            if (outcome == null) return // terminó: UPLOADED, REJECTED o FAILED sin reintento
            val attempts = item.attempts + 1
            if (policy.isRetryable(outcome) && attempts < policy.maxAttempts) {
                updateItem(localId) { it.copy(attempts = attempts, problem = outcome, state = EvidenceUploadState.QUEUED) }
                persist()
                delay(policy.delayFor(attempts))
            } else {
                updateItem(localId) { it.copy(attempts = attempts, problem = outcome, state = EvidenceUploadState.FAILED) }
                persist()
                return
            }
        }
    }

    /** Un intento completo. Devuelve el problema si hay que decidir reintento, o `null` si quedó resuelto. */
    private suspend fun attempt(item: LocalEvidence): EvidenceProblem? {
        val token = accessToken() ?: return fail(item.localId, EvidenceProblem.SESSION_EXPIRED)
        val file = item.filePath?.let(::File)?.takeIf { it.exists() }
            ?: return reject(item.localId, EvidenceProblem.FILE_MISSING)
        val correlationId = UUID.randomUUID().toString()

        var ticket = tickets[item.localId]
        if (ticket == null) {
            setState(item.localId, EvidenceUploadState.REQUESTING)
            when (val result = repository.requestUpload(
                token, item.claimId, item.kind, file.length(), item.origin, OffsetDateTime.parse(item.capturedAt), item.localId, correlationId,
            )) {
                is TicketResult.Issued -> {
                    ticket = result.ticket
                    tickets[item.localId] = ticket
                    updateItem(item.localId) { it.copy(remoteId = result.ticket.evidenceId) }
                    persist()
                }
                TicketResult.AlreadyUploaded -> return markUploaded(item)
                is TicketResult.Refused -> return reject(item.localId, result.problem)
                TicketResult.SessionExpired -> return fail(item.localId, EvidenceProblem.SESSION_EXPIRED)
                TicketResult.NetworkError -> return EvidenceProblem.NETWORK
                TicketResult.ServiceUnavailable -> return EvidenceProblem.SERVICE_UNAVAILABLE
            }
        }

        setState(item.localId, EvidenceUploadState.UPLOADING)
        when (uploader.upload(ticket, file, item.kind.mime) { p -> updateItem(item.localId) { it.copy(progress = p) } }) {
            StorageUploadResult.Uploaded -> Unit
            StorageUploadResult.Expired -> {
                tickets.remove(item.localId) // el próximo intento pide otra carga
                return EvidenceProblem.SERVICE_UNAVAILABLE
            }
            StorageUploadResult.Refused -> return reject(item.localId, EvidenceProblem.TOO_LARGE)
            StorageUploadResult.NetworkError -> return EvidenceProblem.NETWORK
        }

        setState(item.localId, EvidenceUploadState.CONFIRMING)
        return when (repository.confirm(token, item.claimId, ticket.evidenceId, correlationId)) {
            ConfirmResult.Confirmed -> markUploaded(item)
            ConfirmResult.NotUploadedYet -> EvidenceProblem.NETWORK // se vuelve a subir con la misma carga
            ConfirmResult.Expired -> {
                tickets.remove(item.localId)
                EvidenceProblem.SERVICE_UNAVAILABLE
            }
            ConfirmResult.Rejected -> reject(item.localId, EvidenceProblem.REJECTED_BY_SERVER)
            ConfirmResult.SessionExpired -> fail(item.localId, EvidenceProblem.SESSION_EXPIRED)
            ConfirmResult.NetworkError -> EvidenceProblem.NETWORK
            ConfirmResult.ServiceUnavailable -> EvidenceProblem.SERVICE_UNAVAILABLE
        }
    }

    private fun markUploaded(item: LocalEvidence): EvidenceProblem? {
        tickets.remove(item.localId)
        deleteFile(item.filePath)
        updateItem(item.localId) { it.copy(state = EvidenceUploadState.UPLOADED, filePath = null, problem = null, progress = 1f) }
        persist()
        return null
    }

    private fun reject(localId: String, problem: EvidenceProblem): EvidenceProblem? {
        tickets.remove(localId)
        updateItem(localId) { it.copy(state = EvidenceUploadState.REJECTED, problem = problem) }
        persist()
        return null
    }

    private fun fail(localId: String, problem: EvidenceProblem): EvidenceProblem? {
        updateItem(localId) { it.copy(state = EvidenceUploadState.FAILED, problem = problem) }
        persist()
        return null
    }

    private fun setState(localId: String, state: EvidenceUploadState) = updateItem(localId) { it.copy(state = state, progress = 0f) }

    private fun current(localId: String): LocalEvidence? = _items.value.firstOrNull { it.localId == localId }

    private fun updateItem(localId: String, change: (LocalEvidence) -> LocalEvidence) {
        _items.update { list -> list.map { if (it.localId == localId) change(it) else it } }
    }

    private val persistMutex = Mutex()

    /** Guarda siempre el estado más reciente al tomar el turno: las escrituras no se pisan con datos viejos. */
    private fun persist() {
        scope.launch { persistMutex.withLock { store.save(_items.value) } }
    }

    private fun accessToken(): String? = sessionStore.session.value?.accessToken?.takeIf { it.isNotBlank() }

    private companion object {
        val IN_PROGRESS = setOf(EvidenceUploadState.REQUESTING, EvidenceUploadState.UPLOADING, EvidenceUploadState.CONFIRMING)
    }
}
