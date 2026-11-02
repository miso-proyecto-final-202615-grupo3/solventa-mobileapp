package com.solventa.app.data.consent

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class BiometricConsentRepositoryTest {
    private lateinit var server: MockWebServer
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun remote() = RemoteBiometricConsentRepository(
        server.url("/").toString(), ioDispatcher = dispatcher, clock = { Instant.parse("2026-10-31T12:00:00Z") },
    )

    @Test
    fun `remoto lee el estado con el contrato 4_3`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody("""{"otorgado":true,"version_vigente":"1.0","version_aceptada":"1.0"}"""))

        val result = remote().getStatus("token")

        assertEquals(ConsentStatusResult.Loaded(BiometricConsentStatus(true, "1.0", "1.0")), result)
        assertEquals("Bearer token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `remoto registra la decision con version, instalacion y fecha`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"decision":"RECHAZADO","version_terminos":"1.0","registrado_en":"2026-10-31T12:00:01Z"}""",
        ))

        val result = remote().record("token", ConsentDecision.REJECTED, "1.0", "instalacion-1")

        assertTrue(result is ConsentResult.Recorded)
        assertEquals(
            """{"decision":"RECHAZADO","version_terminos":"1.0","installation_id":"instalacion-1","decidido_en":"2026-10-31T12:00:00Z"}""",
            server.takeRequest().body.readUtf8(),
        )
    }

    private fun contract(name: String): String =
        checkNotNull(javaClass.classLoader!!.getResource("contracts/consentimiento-biometrico/$name")).readText()

    @Test
    fun `muestras canonicas del backend se leen sin errores`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(contract("estado-otorgado.json")))
        server.enqueue(MockResponse().setBody(contract("estado-sin-decision.json")))
        server.enqueue(MockResponse().setResponseCode(201).setBody(contract("registro-response.json")))

        assertEquals(ConsentStatusResult.Loaded(BiometricConsentStatus(true, "1.0", "1.0")), remote().getStatus("t"))
        assertEquals(ConsentStatusResult.Loaded(BiometricConsentStatus(false, "1.0", null)), remote().getStatus("t"))
        assertEquals(
            ConsentResult.Recorded(ConsentDecision.ACCEPTED, "2026-11-02T15:00:01Z", "1.0"),
            remote().record("t", ConsentDecision.ACCEPTED, "1.0", "5f0c7a1e-0000-4000-8000-000000000001"),
        )
    }

    @Test
    fun `la solicitud de registro coincide con la muestra canonica`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(201).setBody(contract("registro-response.json")))
        val canonical = RemoteBiometricConsentRepository(
            server.url("/").toString(), ioDispatcher = dispatcher, clock = { Instant.parse("2026-11-02T15:00:00Z") },
        )

        canonical.record("t", ConsentDecision.ACCEPTED, "1.0", "5f0c7a1e-0000-4000-8000-000000000001")

        val esperado = contract("registro-request.json").replace(Regex("\\s"), "")
        assertEquals(esperado, server.takeRequest().body.readUtf8())
    }

    @Test
    fun `remoto mapea 409 401 y 503`() = runTest(dispatcher) {
        listOf(409 to ConsentResult.OutdatedTerms, 401 to ConsentResult.SessionExpired, 503 to ConsentResult.ServiceUnavailable)
            .forEach { (code, expected) ->
                server.enqueue(MockResponse().setResponseCode(code))
                assertEquals(expected, remote().record("t", ConsentDecision.ACCEPTED, "1.0", "i"))
            }
    }

    @Test
    fun `simulado guarda la aceptacion y rechaza versiones viejas`() = runTest {
        val mock = MockBiometricConsentRepository({ "ana@correo.com" }, latencyMillis = 0)

        assertEquals(ConsentResult.OutdatedTerms, mock.record("t", ConsentDecision.ACCEPTED, "0.9", "i"))
        mock.record("t", ConsentDecision.ACCEPTED, "1.0", "i")

        assertEquals(
            ConsentStatusResult.Loaded(BiometricConsentStatus(true, "1.0", "1.0")),
            mock.getStatus("t"),
        )
    }

    @Test
    fun `simulado permite probar el servicio caido`() = runTest {
        val mock = MockBiometricConsentRepository({ MockBiometricConsentRepository.SERVICE_UNAVAILABLE_EMAIL }, latencyMillis = 0)

        assertEquals(ConsentStatusResult.ServiceUnavailable, mock.getStatus("t"))
    }
}
