package com.solventa.app.data.activation

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteActivationRepositoryTest {
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

    @Test
    fun `las muestras de contrato de activacion estan disponibles para la app`() {
        val inicio = javaClass.classLoader!!.getResource("contracts/activacion/inicio-frames.json")
            ?.readText()
        val verificacion = javaClass.classLoader!!.getResource("contracts/activacion/verificacion-aprobada.json")
            ?.readText()

        assertTrue(inicio?.contains("\"tipo\": \"FRAMES\"") == true)
        assertTrue(verificacion?.contains("\"resultado\": \"APROBADO\"") == true)
    }

    @Test
    fun `inicia reto frames con nonce del bff`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{
                  "dispositivo_vinculado":false,
                  "sesion_id":"sesion-1",
                  "expira_en":"2026-10-25T12:02:00Z",
                  "captura":{"tipo":"FRAMES","reto":{"gesto":"BLINK","nonce":"reto-1"}},
                  "intentos_restantes":3
                }""".trimIndent(),
            ),
        )
        val repository = RemoteActivationRepository(server.url("/").toString(), ioDispatcher = dispatcher)

        val result = repository.start("token-1", "instalacion-1", "ANDROID", "Pixel", "15")

        assertTrue(result is ActivationStartResult.ChallengeReady)
        result as ActivationStartResult.ChallengeReady
        assertEquals("sesion-1", result.challenge.sessionId)
        assertEquals("BLINK", result.challenge.gesture)
        assertEquals("reto-1", result.challenge.nonce)
        assertEquals(
            """{"installation_id":"instalacion-1","plataforma":"ANDROID","modelo":"Pixel","version_so":"15"}""",
            server.takeRequest().body.readUtf8(),
        )
    }

    @Test
    fun `reconoce dispositivo ya vinculado`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"dispositivo_vinculado":true}"""))
        val repository = RemoteActivationRepository(server.url("/").toString(), ioDispatcher = dispatcher)

        val result = repository.start("token-1", "instalacion-1", "ANDROID", "Pixel", "15")

        assertEquals(ActivationStartResult.DeviceAlreadyLinked, result)
    }
}
