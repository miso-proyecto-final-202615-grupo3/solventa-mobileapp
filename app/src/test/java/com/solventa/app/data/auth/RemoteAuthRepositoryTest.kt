package com.solventa.app.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class RemoteAuthRepositoryTest {

    private val server = MockWebServer()
    private lateinit var repository: RemoteAuthRepository

    @Before
    fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build()
        repository = RemoteAuthRepository(
            baseUrl = server.url("/").toString(),
            client = client,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(code: Int, body: String) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    @Test
    fun `login exitoso envia las credenciales y arma la sesion`() = runTest {
        server.enqueue(
            json(
                200,
                """
                {"access_token":"access-123","refresh_token":"refresh-123","expires_in":3600,
                 "token_type":"Bearer","correo":"ana.perez@example.com","campo_nuevo":"ignorado"}
                """.trimIndent(),
            ),
        )

        val result = repository.login("ana.perez@example.com", "Secreta123")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals(RemoteAuthRepository.LOGIN_PATH, request.path)
        assertEquals(
            """{"correo":"ana.perez@example.com","password":"Secreta123"}""",
            request.body.readUtf8(),
        )
        assertTrue(result is LoginResult.Success)
        val session = (result as LoginResult.Success).session
        assertEquals("access-123", session.accessToken)
        assertEquals("refresh-123", session.refreshToken)
        assertEquals(3600L, session.expiresInSeconds)
        assertEquals("ana.perez@example.com", session.cliente.correo)
        assertTrue(session.cliente.dispositivoVinculado)
    }

    @Test
    fun `respuesta sin refresh token ni expiracion usa valores vacios`() = runTest {
        server.enqueue(json(200, """{"access_token":"access-123"}"""))

        val session = (repository.login("a@b.co", "x") as LoginResult.Success).session

        assertEquals("", session.refreshToken)
        assertEquals(0L, session.expiresInSeconds)
    }

    @Test
    fun `401 son credenciales invalidas`() = runTest {
        server.enqueue(json(401, """{"detail":"Credenciales inválidas."}"""))

        assertEquals(LoginResult.InvalidCredentials, repository.login("a@b.co", "mala"))
    }

    @Test
    fun `422 tambien se muestra como credenciales invalidas`() = runTest {
        server.enqueue(json(422, """{"detail":[]}"""))

        assertEquals(LoginResult.InvalidCredentials, repository.login("a@b.co", "x"))
    }

    @Test
    fun `503 del BFF es servicio no disponible`() = runTest {
        server.enqueue(json(503, """{"detail":"El servicio de identidad no está disponible."}"""))

        assertEquals(LoginResult.ServiceUnavailable, repository.login("a@b.co", "x"))
    }

    @Test
    fun `500 o respuesta que no es json es servicio no disponible`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("<html>Internal Server Error</html>"))

        assertEquals(LoginResult.ServiceUnavailable, repository.login("a@b.co", "x"))
    }

    @Test
    fun `respuesta exitosa con cuerpo invalido es servicio no disponible`() = runTest {
        server.enqueue(json(200, """{"no_es":"una sesion"}"""))

        assertEquals(LoginResult.ServiceUnavailable, repository.login("a@b.co", "x"))
    }

    @Test
    fun `sin conexion con el servidor es error de conexion`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertEquals(LoginResult.NetworkError, repository.login("a@b.co", "x"))
    }

    @Test
    fun `servidor que no responde a tiempo es error de conexion`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        assertEquals(LoginResult.NetworkError, repository.login("a@b.co", "x"))
    }

    @Test
    fun `la url base acepta barra final`() = runTest {
        server.enqueue(json(200, """{"access_token":"a"}"""))
        val repo = RemoteAuthRepository(
            baseUrl = server.url("/").toString().trimEnd('/') + "/",
            ioDispatcher = Dispatchers.Unconfined,
        )

        repo.login("a@b.co", "x")

        assertEquals(RemoteAuthRepository.LOGIN_PATH, server.takeRequest().path)
    }
}
