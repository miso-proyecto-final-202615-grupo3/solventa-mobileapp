package com.solventa.app.data.claims.evidence

import com.solventa.app.core.location.LocationFix
import com.solventa.app.core.location.LocationSource
import com.solventa.app.data.claims.ClaimResult
import com.solventa.app.data.claims.RemoteClaimsRepository
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class RemoteEvidenceRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val dispatcher = StandardTestDispatcher()
    private val capturedAt = OffsetDateTime.of(2026, 11, 10, 14, 35, 0, 0, ZoneOffset.ofHours(-5))

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun repository() = RemoteEvidenceRepository(server.url("/").toString(), ioDispatcher = dispatcher)

    private fun contract(name: String): String =
        checkNotNull(javaClass.classLoader!!.getResource("contracts/evidencias/$name")).readText()

    private fun error(codigo: String) = """{"codigo":"$codigo","mensaje":"x","correlation_id":"c"}"""

    @Test
    fun `solicitud con el cuerpo del contrato y la carga parseada`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(201).setBody(contract("solicitud-response.json")))

        val result = repository().requestUpload("t", "c41f", EvidenceKind.PHOTO, 845123, EvidenceOrigin.CAMERA, capturedAt, "key-1", "corr")

        val request = server.takeRequest()
        assertEquals("/api/v1/bff-mobile/siniestros/c41f/evidencias", request.path)
        assertEquals("Bearer t", request.getHeader("Authorization"))
        assertEquals("key-1", request.getHeader("Idempotency-Key"))
        assertEquals(
            Json.parseToJsonElement(contract("solicitud-request.json")).jsonObject,
            Json.parseToJsonElement(request.body.readUtf8()).jsonObject,
        )
        val ticket = (result as TicketResult.Issued).ticket
        assertEquals("5e0c2d8a-1f43-4b7e-9c0d-6a2b8e4f1c33", ticket.evidenceId)
        assertEquals("aws:kms", ticket.fields["x-amz-server-side-encryption"])
        assertEquals(Instant.parse("2026-11-10T20:10:00Z"), ticket.expiresAt)
        assertEquals(5L * 1024 * 1024, ticket.maxBytes)
    }

    @Test
    fun `errores de la solicitud`() = runTest(dispatcher) {
        val casos = listOf(
            Triple(409, contract("error-limite-evidencias.json"), TicketResult.Refused(EvidenceProblem.LIMIT_REACHED)),
            Triple(409, error("SINIESTRO_NO_ADMITE_EVIDENCIAS"), TicketResult.Refused(EvidenceProblem.CLAIM_CLOSED)),
            Triple(413, error("EVIDENCIA_DEMASIADO_GRANDE"), TicketResult.Refused(EvidenceProblem.TOO_LARGE)),
            Triple(415, error("FORMATO_NO_SOPORTADO"), TicketResult.Refused(EvidenceProblem.NOT_ALLOWED)),
            Triple(422, error("TIPO_EVIDENCIA_NO_PERMITIDO"), TicketResult.Refused(EvidenceProblem.NOT_ALLOWED)),
            Triple(422, error("EVIDENCIA_RECHAZADA"), TicketResult.Refused(EvidenceProblem.REJECTED_BY_SERVER)),
            Triple(409, error("IDEMPOTENCIA_CONFLICTO"), TicketResult.Refused(EvidenceProblem.REJECTED_BY_SERVER)),
            Triple(200, contract("solicitud-ya-cargada-response.json"), TicketResult.AlreadyUploaded),
            Triple(401, "", TicketResult.SessionExpired),
            Triple(503, error("SERVICIO_NO_DISPONIBLE"), TicketResult.ServiceUnavailable),
        )
        casos.forEach { (code, body, expected) ->
            server.enqueue(MockResponse().setResponseCode(code).setBody(body))
            assertEquals("$code", expected, repository().requestUpload("t", "c", EvidenceKind.PHOTO, 1, EvidenceOrigin.CAMERA, capturedAt, "k", "x"))
        }
    }

    @Test
    fun `confirmacion y sus errores`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(contract("confirmacion-response.json")))
        server.enqueue(MockResponse().setResponseCode(409).setBody(error("CARGA_NO_ENCONTRADA")))
        server.enqueue(MockResponse().setResponseCode(409).setBody(error("CARGA_EXPIRADA")))
        server.enqueue(MockResponse().setResponseCode(422).setBody(error("EVIDENCIA_RECHAZADA")))

        val repo = repository()
        assertEquals(ConfirmResult.Confirmed, repo.confirm("t", "c", "e", "x"))
        assertEquals("/api/v1/bff-mobile/siniestros/c/evidencias/e/confirmacion", server.takeRequest().path)
        assertEquals(ConfirmResult.NotUploadedYet, repo.confirm("t", "c", "e", "x"))
        assertEquals(ConfirmResult.Expired, repo.confirm("t", "c", "e", "x"))
        assertEquals(ConfirmResult.Rejected, repo.confirm("t", "c", "e", "x"))
        // Puerto cerrado: error de conexión real y determinista.
        assertEquals(ConfirmResult.NetworkError, RemoteEvidenceRepository(CLOSED_PORT, ioDispatcher = dispatcher).confirm("t", "c", "e", "x"))
    }

    @Test
    fun `ubicacion con el contrato`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(contract("ubicacion-response.json")))
        val fix = LocationFix(4.6533, -74.0836, 18.5f, Instant.parse("2026-11-10T19:36:10Z"), LocationSource.GPS)

        val result = repository().saveLocation("t", "c41f", fix, "corr")

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("GPS", body["fuente"]!!.jsonPrimitive.content)
        assertEquals(4.6533, body["latitud"]!!.jsonPrimitive.content.toDouble(), 1e-9)
        assertEquals(LocationSaveResult.Saved, result)
    }

    @Test
    fun `detalle del siniestro con ubicacion y evidencias`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(contract("detalle-con-evidencias.json")))
        val claims = RemoteClaimsRepository(server.url("/").toString(), ioDispatcher = dispatcher, languageTag = { "es" })

        val claim = (claims.claim("t", "c41f", "corr") as ClaimResult.Found).claim

        assertEquals(LocationSource.GPS, claim.location?.source)
        assertEquals(listOf(EvidenceKind.PHOTO, EvidenceKind.VIDEO), claim.evidences.map { it.kind })
        assertEquals("CARGADA", claim.evidences.first().state)
        assertNull(claim.evidences.last().uploadedAt)
    }

    @Test
    fun `la subida a s3 envia todos los campos y el archivo al final sin authorization`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(204))
        val file = tmp.newFile("foto.jpg").apply { writeBytes(ByteArray(1000) { 7 }) }
        val ticket = UploadTicket("e", server.url("/bucket").toString(), linkedMapOf("key" to "k", "policy" to "p", "x-amz-signature" to "s"),
            Instant.parse("2030-01-01T00:00:00Z"), 5_000)
        val progress = mutableListOf<Float>()

        val result = PresignedPostUploader(ioDispatcher = dispatcher).upload(ticket, file, "image/jpeg") { progress += it }

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertEquals(StorageUploadResult.Uploaded, result)
        assertNull(request.getHeader("Authorization"))
        val order = listOf("name=\"key\"", "name=\"policy\"", "name=\"x-amz-signature\"", "name=\"file\"").map(body::indexOf)
        assertTrue(order.all { it >= 0 } && order == order.sorted())
        assertEquals(1f, progress.last())
    }

    @Test
    fun `respuestas de s3`() = runTest(dispatcher) {
        val file = tmp.newFile("v.mp4").apply { writeBytes(ByteArray(10)) }
        val ticket = UploadTicket("e", server.url("/bucket").toString(), mapOf("key" to "k"), Instant.parse("2030-01-01T00:00:00Z"), 5_000)
        server.enqueue(MockResponse().setResponseCode(403).setBody("<Error><Code>AccessDenied</Code><Message>Policy expired</Message></Error>"))
        server.enqueue(MockResponse().setResponseCode(400).setBody("<Error><Code>EntityTooLarge</Code></Error>"))
        val uploader = PresignedPostUploader(ioDispatcher = dispatcher)

        assertEquals(StorageUploadResult.Expired, uploader.upload(ticket, file, "video/mp4") {})
        assertEquals(StorageUploadResult.Refused, uploader.upload(ticket, file, "video/mp4") {})
        assertEquals(StorageUploadResult.NetworkError, uploader.upload(ticket.copy(url = "${CLOSED_PORT}bucket"), file, "video/mp4") {})
    }

    private companion object {
        const val CLOSED_PORT = "http://127.0.0.1:1/"
    }
}
