package com.solventa.app.data.push

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemotePushRepositoryTest {
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

    private fun repository() = RemotePushRepository(server.url("/").toString(), ioDispatcher = dispatcher)

    private fun contract(name: String): String =
        checkNotNull(javaClass.classLoader!!.getResource("contracts/notificaciones/$name")).readText()

    @Test
    fun `registra con el cuerpo del contrato y el token de sesion`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(204))
        val sample = Json.parseToJsonElement(contract("registro-push-request.json")).jsonObject
        val installationId = sample["installation_id"]!!.jsonPrimitive.content
        val token = sample["token"]!!.jsonPrimitive.content

        val result = repository().register("access-1", installationId, token)

        val request = server.takeRequest()
        assertEquals(PushResult.Registered, result)
        assertEquals("PUT", request.method)
        assertEquals(RemotePushRepository.PATH, request.path)
        assertEquals("Bearer access-1", request.getHeader("Authorization"))
        assertTrue(request.getHeader("X-Correlation-Id")!!.isNotBlank())
        assertEquals(sample, Json.parseToJsonElement(request.body.readUtf8()).jsonObject)
        assertFalse(token in request.path!!) // el token nunca va en la URL
    }

    @Test
    fun `borra con installation_id en la consulta`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(204))

        assertEquals(PushResult.Registered, repository().unregister("access-1", "inst-9"))

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("inst-9", request.requestUrl!!.queryParameter("installation_id"))
    }

    @Test
    fun `traduce los errores`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(404).setBody(contract("error-dispositivo-no-vinculado.json")))
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(503))
        val repo = repository()

        assertEquals(PushResult.DeviceNotLinked, repo.register("a", "i", "t"))
        assertEquals(PushResult.SessionExpired, repo.register("a", "i", "t"))
        assertEquals(PushResult.ServiceUnavailable, repo.register("a", "i", "t"))
    }

    @Test
    fun `sin conexion devuelve error de red`() = runTest(dispatcher) {
        val repo = repository()
        server.shutdown()

        assertEquals(PushResult.NetworkError, repo.register("a", "i", "t"))
    }
}
