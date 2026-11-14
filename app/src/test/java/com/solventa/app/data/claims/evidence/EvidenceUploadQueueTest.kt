package com.solventa.app.data.claims.evidence

import com.solventa.app.core.biometric.testSession
import com.solventa.app.data.session.InMemorySessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class EvidenceUploadQueueTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val repository = FakeEvidenceRepository()
    private val uploader = FakeUploader()
    private val sessionStore = InMemorySessionStore().apply { save(testSession()) }
    private val deleted = mutableListOf<String?>()
    private val now = OffsetDateTime.of(2026, 11, 10, 14, 0, 0, 0, ZoneOffset.ofHours(-5))
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun cancelScopes() = scopes.forEach { it.cancel() }

    private fun TestScope.store() = PendingEvidenceStore(File(tmp.root, "pendientes.json"), StandardTestDispatcher(testScheduler))

    private fun TestScope.queue(store: PendingEvidenceStore = store()): EvidenceUploadQueue {
        var id = 0
        return EvidenceUploadQueue(
            repository = repository,
            uploader = uploader,
            sessionStore = sessionStore,
            store = store,
            deleteFile = { deleted += it; it?.let(::File)?.delete() },
            policy = RetryPolicy(maxAttempts = 3, baseDelayMs = 2_000),
            // Mismo planificador de la prueba (tiempo virtual); se cancela en @After.
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)).also { scopes += it },
            newId = { "local-${++id}" },
        )
    }

    private fun photo(name: String = "foto.jpg"): File = tmp.newFile(name).apply { writeBytes(ByteArray(100)) }

    private fun EvidenceUploadQueue.only() = items.value.single()

    @Test
    fun `camino feliz pide carga sube confirma y borra el archivo`() = runTest {
        val queue = queue()
        val file = photo()

        queue.enqueue("c-1", "SIN-1", file, EvidenceKind.PHOTO, EvidenceOrigin.CAMERA, now)
        advanceUntilIdle()

        val item = queue.only()
        assertEquals(EvidenceUploadState.UPLOADED, item.state)
        assertEquals("ev-1", item.remoteId)
        assertNull(item.filePath)
        assertEquals(listOf("ev-1"), repository.confirmed)
        assertFalse(file.exists())
    }

    @Test
    fun `una falla de red se reintenta con retroceso y luego funciona`() = runTest {
        repository.confirms += ConfirmResult.NetworkError
        val queue = queue()

        queue.enqueue("c-1", "SIN-1", photo(), EvidenceKind.PHOTO, EvidenceOrigin.CAMERA, now)
        advanceTimeBy(1_000)
        assertEquals(EvidenceUploadState.QUEUED, queue.only().state)
        advanceUntilIdle()

        assertEquals(EvidenceUploadState.UPLOADED, queue.only().state)
        assertEquals(listOf("ev-1", "ev-1"), repository.confirmed) // misma carga, sin pedir otra
        assertEquals(1, repository.requested.size)
    }

    @Test
    fun `tres fallas dejan FAILED y reintentar la retoma`() = runTest {
        repeat(3) { repository.confirms += ConfirmResult.ServiceUnavailable }
        val queue = queue()
        queue.enqueue("c-1", "SIN-1", photo(), EvidenceKind.PHOTO, EvidenceOrigin.CAMERA, now)
        advanceUntilIdle()
        assertEquals(EvidenceUploadState.FAILED, queue.only().state)
        assertEquals(EvidenceProblem.SERVICE_UNAVAILABLE, queue.only().problem)

        queue.retry("local-1")
        advanceUntilIdle()

        assertEquals(EvidenceUploadState.UPLOADED, queue.only().state)
    }

    @Test
    fun `una evidencia rechazada no detiene a las siguientes`() = runTest {
        repository.tickets += TicketResult.Refused(EvidenceProblem.LIMIT_REACHED)
        val queue = queue()

        queue.enqueue("c-1", "SIN-1", photo("a.jpg"), EvidenceKind.PHOTO, EvidenceOrigin.CAMERA, now)
        queue.enqueue("c-1", "SIN-1", photo("b.jpg"), EvidenceKind.PHOTO, EvidenceOrigin.GALLERY, now)
        advanceUntilIdle()

        val (first, second) = queue.items.value
        assertEquals(EvidenceUploadState.REJECTED to EvidenceProblem.LIMIT_REACHED, first.state to first.problem)
        assertEquals(EvidenceUploadState.UPLOADED, second.state)
    }

    @Test
    fun `carga vencida en el almacenamiento pide una nueva`() = runTest {
        uploader.results += StorageUploadResult.Expired
        val queue = queue()

        queue.enqueue("c-1", "SIN-1", photo(), EvidenceKind.PHOTO, EvidenceOrigin.CAMERA, now)
        advanceUntilIdle()

        assertEquals(2, repository.requested.size)
        assertEquals(EvidenceUploadState.UPLOADED, queue.only().state)
        assertEquals("ev-2", queue.only().remoteId)
    }

    @Test
    fun `no subido aun se vuelve a subir y rechazo del servidor no se reintenta`() = runTest {
        repository.confirms += ConfirmResult.NotUploadedYet
        repository.confirms += ConfirmResult.Rejected
        val queue = queue()

        queue.enqueue("c-1", "SIN-1", photo(), EvidenceKind.PHOTO, EvidenceOrigin.CAMERA, now)
        advanceUntilIdle()

        assertEquals(2, uploader.uploaded.size)
        assertEquals(EvidenceUploadState.REJECTED, queue.only().state)
        assertEquals(EvidenceProblem.REJECTED_BY_SERVER, queue.only().problem)
    }

    @Test
    fun `sin sesion espera y se reanuda al iniciar sesion`() = runTest {
        sessionStore.clear()
        val queue = queue()
        queue.start()
        queue.enqueue("c-1", "SIN-1", photo(), EvidenceKind.PHOTO, EvidenceOrigin.CAMERA, now)
        advanceUntilIdle()
        assertEquals(EvidenceUploadState.QUEUED, queue.only().state)

        sessionStore.save(testSession())
        advanceUntilIdle()

        assertEquals(EvidenceUploadState.UPLOADED, queue.only().state)
    }

    @Test
    fun `sesion vencida durante el envio queda FAILED con su motivo`() = runTest {
        repository.tickets += TicketResult.SessionExpired
        val queue = queue()

        queue.enqueue("c-1", "SIN-1", photo(), EvidenceKind.PHOTO, EvidenceOrigin.CAMERA, now)
        advanceUntilIdle()

        assertEquals(EvidenceUploadState.FAILED to EvidenceProblem.SESSION_EXPIRED, queue.only().state to queue.only().problem)
    }

    @Test
    fun `los pendientes se guardan y se restauran al iniciar`() = runTest {
        val store = store()
        repeat(3) { repository.confirms += ConfirmResult.NetworkError }
        val first = queue(store)
        val file = photo()
        first.enqueue("c-1", "SIN-1", file, EvidenceKind.PHOTO, EvidenceOrigin.CAMERA, now)
        advanceUntilIdle()
        assertEquals(EvidenceUploadState.FAILED, first.only().state)

        val restored = queue(store)
        var kept: Set<String>? = null
        restored.start(onLoaded = { kept = it })
        advanceUntilIdle()

        // Al iniciar sesión se reanuda lo que falló por red, y esta vez se confirma.
        assertEquals(EvidenceUploadState.UPLOADED, restored.only().state)
        assertEquals(setOf(file.absolutePath), kept)
    }

    @Test
    fun `al reanudar tras cerrar la app se pide la carga con la misma clave`() = runTest {
        val store = store()
        uploader.results += StorageUploadResult.NetworkError
        uploader.results += StorageUploadResult.NetworkError
        uploader.results += StorageUploadResult.NetworkError
        val first = queue(store)
        first.enqueue("c-1", "SIN-1", photo(), EvidenceKind.PHOTO, EvidenceOrigin.CAMERA, now)
        advanceUntilIdle()
        assertEquals(EvidenceUploadState.FAILED, first.only().state)

        // Proceso nuevo: la carga firmada (solo en memoria) se perdió y se vuelve a pedir.
        val restored = queue(store)
        restored.start()
        advanceUntilIdle()

        assertEquals(EvidenceUploadState.UPLOADED, restored.only().state)
        assertEquals(listOf("local-1", "local-1"), repository.keys)
    }

    @Test
    fun `si el servidor ya la tiene cargada se da por enviada`() = runTest {
        repository.tickets += TicketResult.AlreadyUploaded
        val queue = queue()
        val file = photo()

        queue.enqueue("c-1", "SIN-1", file, EvidenceKind.PHOTO, EvidenceOrigin.CAMERA, now)
        advanceUntilIdle()

        assertEquals(EvidenceUploadState.UPLOADED, queue.only().state)
        assertTrue(repository.confirmed.isEmpty())
        assertEquals(listOf<String?>(file.absolutePath), deleted)
    }

    @Test
    fun `la sesion vencida se reanuda sola al volver a iniciar sesion`() = runTest {
        repository.confirms += ConfirmResult.SessionExpired
        val queue = queue()
        queue.start()
        advanceUntilIdle() // la reanudación inicial de start() ya pasó
        queue.enqueue("c-1", "SIN-1", photo(), EvidenceKind.PHOTO, EvidenceOrigin.CAMERA, now)
        advanceUntilIdle()
        assertEquals(EvidenceProblem.SESSION_EXPIRED, queue.only().problem)

        sessionStore.save(testSession().copy(accessToken = "token-nuevo"))
        advanceUntilIdle()

        assertEquals(EvidenceUploadState.UPLOADED, queue.only().state)
        assertEquals(2, repository.confirmed.size)
    }

    @Test
    fun `quitar borra el archivo local`() = runTest {
        repository.tickets += TicketResult.Refused(EvidenceProblem.TOO_LARGE)
        val queue = queue()
        val file = photo()
        queue.enqueue("c-1", "SIN-1", file, EvidenceKind.PHOTO, EvidenceOrigin.CAMERA, now)
        advanceUntilIdle()

        queue.remove("local-1")

        assertTrue(queue.items.value.isEmpty())
        assertTrue(file.absolutePath in deleted)
    }

    @Test
    fun `archivo que ya no existe se rechaza`() = runTest {
        val queue = queue()
        val file = photo()
        file.delete()

        queue.enqueue("c-1", "SIN-1", file, EvidenceKind.PHOTO, EvidenceOrigin.CAMERA, now)
        advanceUntilIdle()

        assertEquals(EvidenceProblem.FILE_MISSING, queue.only().problem)
    }

    @Test
    fun `siniestros con pendientes`() = runTest {
        repeat(3) { repository.confirms += ConfirmResult.NetworkError }
        val queue = queue()
        queue.enqueue("c-1", "SIN-1", photo(), EvidenceKind.PHOTO, EvidenceOrigin.CAMERA, now)
        advanceUntilIdle()

        assertEquals(listOf("c-1" to "SIN-1"), queue.pendingClaims().first())
    }
}

class PendingEvidenceStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `guarda solo pendientes y tolera un archivo corrupto`() = runTest {
        val file = File(tmp.root, "pendientes.json")
        val store = PendingEvidenceStore(file, StandardTestDispatcher(testScheduler))
        val base = LocalEvidence("l1", "c", "SIN", EvidenceKind.PHOTO, EvidenceOrigin.CAMERA, "/x", 1, "2026-11-10T14:00-05:00")

        store.save(listOf(base, base.copy(localId = "l2", state = EvidenceUploadState.UPLOADED)))
        assertEquals(listOf("l1"), store.load().map { it.localId })

        file.writeText("{no es json")
        assertTrue(store.load().isEmpty())
    }
}
