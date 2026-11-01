package com.solventa.app.data.activation

import com.solventa.app.core.camera.CapturedFrame
import com.solventa.app.core.camera.CapturedFrames
import com.solventa.app.core.device.DeviceInfo
import com.solventa.app.core.liveness.FrameSlot
import com.solventa.app.core.liveness.LivenessChallenge
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class RemoteActivationRepositoryTest {
    private lateinit var server: MockWebServer
    private val dispatcher = StandardTestDispatcher()
    private val device = DeviceInfo("instalacion-1", model = "Pixel", osVersion = "15")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun repository(demoEmail: String? = null) =
        RemoteActivationRepository(server.url("/").toString(), ioDispatcher = dispatcher, demoEmail = demoEmail)

    private fun contract(name: String): String =
        checkNotNull(javaClass.classLoader!!.getResource("contracts/activacion/$name")).readText()

    private fun framesSession() = ActivationSession(
        "sesion-1", "2026-10-31T12:02:00Z", 3, CaptureType.Frames(LivenessChallenge.BLINK, "nonce-1"),
    )

    private fun frames(): CapturedFrames = CapturedFrames().apply {
        this[FrameSlot.START] = CapturedFrame(byteArrayOf(-1, -40, -1, 1), Instant.parse("2026-10-31T12:00:01Z"))
        this[FrameSlot.GESTURE] = CapturedFrame(byteArrayOf(-1, -40, -1, 2), Instant.parse("2026-10-31T12:00:02Z"))
        this[FrameSlot.END] = CapturedFrame(byteArrayOf(-1, -40, -1, 3), Instant.parse("2026-10-31T12:00:03Z"))
    }

    @Test
    fun `muestra canonica de inicio produce una sesion frames`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(201).setBody(contract("inicio-frames.json")))

        val result = repository().startSession("token-1", device, "corr-1")

        assertTrue(result is SessionResult.Started)
        val capture = (result as SessionResult.Started).session.capture as CaptureType.Frames
        assertEquals(LivenessChallenge.BLINK, capture.challenge)
        assertEquals("nonce-de-prueba", capture.nonce)
    }

    @Test
    fun `inicio envia token, correlacion y dispositivo sin correo`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(201).setBody(contract("inicio-frames.json")))

        repository().startSession("token-1", device, "corr-1")

        val request = server.takeRequest()
        assertEquals("Bearer token-1", request.getHeader("Authorization"))
        assertEquals("corr-1", request.getHeader("X-Correlation-Id"))
        assertNull(request.getHeader("X-Demo-Correo"))
        assertEquals(
            """{"installation_id":"instalacion-1","plataforma":"ANDROID","modelo":"Pixel","version_so":"15"}""",
            request.body.readUtf8(),
        )
    }

    @Test
    fun `el correo de demo solo viaja si el build lo configura`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(201).setBody(contract("inicio-frames.json")))

        repository(demoEmail = "ana@example.com").startSession("token-1", device, "corr-1")

        assertEquals("ana@example.com", server.takeRequest().getHeader("X-Demo-Correo"))
    }

    @Test
    fun `sesion sdk de didit`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"dispositivo_vinculado":false,"sesion_id":"s","expira_en":"x","intentos_restantes":2,
                "captura":{"tipo":"SDK","sdk":{"nombre":"DIDIT","session_token":"tok"}}}""",
            ),
        )

        val result = repository().startSession("token-1", device, "corr-1") as SessionResult.Started

        assertEquals(CaptureType.Sdk("DIDIT", "tok"), result.session.capture)
    }

    @Test
    fun `reconoce dispositivo ya vinculado`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"dispositivo_vinculado":true}"""))

        assertEquals(SessionResult.AlreadyLinked, repository().startSession("t", device, "c"))
    }

    @Test
    fun `gesto o tipo desconocido no se aceptan`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"sesion_id":"s","expira_en":"x","captura":{"tipo":"FRAMES","reto":{"gesto":"SONREIR","nonce":"n"}}}""",
            ),
        )

        assertEquals(SessionResult.ServiceUnavailable, repository().startSession("t", device, "c"))
    }

    @Test
    fun `mapea los errores de inicio`() = runTest(dispatcher) {
        val expected = mapOf(
            401 to SessionResult.SessionExpired,
            403 to SessionResult.ConsentRequired,
            404 to SessionResult.AccountNotFound,
            429 to SessionResult.AttemptsExhausted,
            503 to SessionResult.ServiceUnavailable,
        )
        expected.forEach { (code, result) ->
            server.enqueue(MockResponse().setResponseCode(code).setBody("""{"codigo":"X"}"""))
            assertEquals(result, repository().startSession("t", device, "c"))
        }
    }

    @Test
    fun `verificacion envia tres frames y metadata con nonce y marcas de tiempo`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(contract("verificacion-aprobada.json")))

        val result = repository().verify("token-1", framesSession(), CaptureOutcome.OwnFrames(frames()), "corr-2")

        assertTrue(result is VerificationResult.Approved)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("""{"nonce":"nonce-1","capturas":{"inicio":"2026-10-31T12:00:01Z","gesto":"2026-10-31T12:00:02Z","fin":"2026-10-31T12:00:03Z"}}"""))
        listOf("frame_inicio", "frame_gesto", "frame_fin").forEach { assertTrue(body.contains("name=\"$it\"")) }
        assertFalse(body.contains("correo"))
    }

    @Test
    fun `verificacion sdk envia solo el resultado del sdk`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(contract("verificacion-aprobada.json")))
        val session = framesSession().copy(capture = CaptureType.Sdk("DIDIT", "tok"))

        repository().verify("token-1", session, CaptureOutcome.SdkResult(SdkStatus.COMPLETED), "corr-2")

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("""{"resultado_sdk":"COMPLETED"}"""))
        assertFalse(body.contains("frame_inicio"))
    }

    @Test
    fun `rechazo informa intentos restantes y sin intentos se agota`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"resultado":"RECHAZADO","folio":"f","intento":1,"intentos_restantes":2,"dispositivo_vinculado":false,"motivo":"GESTO_NO_DETECTADO"}""",
        ))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"resultado":"RECHAZADO","folio":"f","intento":3,"intentos_restantes":0,"dispositivo_vinculado":false}""",
        ))

        assertEquals(
            VerificationResult.Rejected(1, 2, "GESTO_NO_DETECTADO"),
            repository().verify("t", framesSession(), CaptureOutcome.OwnFrames(frames()), "c"),
        )
        assertEquals(
            VerificationResult.AttemptsExhausted,
            repository().verify("t", framesSession(), CaptureOutcome.OwnFrames(frames()), "c"),
        )
    }

    @Test
    fun `mapea los errores de verificacion`() = runTest(dispatcher) {
        val expected = listOf(
            Triple(401, "{}", VerificationResult.SessionExpired),
            Triple(404, "{}", VerificationResult.InvalidSession),
            Triple(409, """{"codigo":"SESION_INVALIDA"}""", VerificationResult.InvalidSession),
            Triple(409, """{"codigo":"SESION_CANCELADA"}""", VerificationResult.Cancelled),
            Triple(413, "{}", VerificationResult.InvalidCapture),
            Triple(415, "{}", VerificationResult.InvalidCapture),
            Triple(422, "{}", VerificationResult.InvalidCapture),
            Triple(429, "{}", VerificationResult.AttemptsExhausted),
            Triple(503, "{}", VerificationResult.ServiceUnavailable),
        )
        expected.forEach { (code, body, result) ->
            server.enqueue(MockResponse().setResponseCode(code).setBody(body))
            assertEquals(result, repository().verify("t", framesSession(), CaptureOutcome.OwnFrames(frames()), "c"))
        }
    }

    @Test
    fun `cuerpo mal formado es servicio no disponible y desconexion es error de red`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("no-json"))
        assertEquals(
            VerificationResult.ServiceUnavailable,
            repository().verify("t", framesSession(), CaptureOutcome.OwnFrames(frames()), "c"),
        )

        val offline = repository()
        server.shutdown()
        assertEquals(SessionResult.NetworkError, offline.startSession("t", device, "c"))
    }
}
