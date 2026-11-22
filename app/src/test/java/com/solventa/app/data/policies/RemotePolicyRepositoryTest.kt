package com.solventa.app.data.policies

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class RemotePolicyRepositoryTest {
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

    private fun repository() = RemotePolicyRepository(server.url("/").toString(), ioDispatcher = dispatcher)

    private fun contract() =
        checkNotNull(javaClass.classLoader!!.getResource("contracts/polizas/billetera.json")).readText()

    @Test
    fun `billetera del contrato con headers y cliente del token`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(contract()))

        val result = repository().fetchWallet("token-1", "corr-1") as WalletFetchResult.Loaded

        val request = server.takeRequest()
        assertEquals("/api/v1/bff-mobile/polizas", request.path)
        assertEquals("Bearer token-1", request.getHeader("Authorization"))
        assertEquals("corr-1", request.getHeader("X-Correlation-Id"))
        val policy = result.policies.single()
        assertEquals("SLV-2026-08341", policy.number)
        assertEquals("EMITIDA", policy.status)
        assertEquals("Seguro de vida hipotecario", policy.productName)
        assertEquals(Instant.parse("2027-03-12T00:00:00Z"), policy.validTo)
        assertEquals("120000.00", policy.totalPremium)
        assertEquals(listOf("Muerte por cualquier causa", null), policy.coverages.map { it.name })
        assertEquals("200000000.00", policy.coverages.first().insuredLimit)
    }

    @Test
    fun `acepta importes numericos ademas de texto`() = runTest(dispatcher) {
        val body = contract().replace("\"120000.00\"", "120000.5").replace("\"0.00\"", "0")
        server.enqueue(MockResponse().setBody(body))

        val policy = (repository().fetchWallet("t", "c") as WalletFetchResult.Loaded).policies.single()

        assertEquals("120000.5", policy.totalPremium)
        assertEquals("0", policy.coverages.first().deductible)
    }

    @Test
    fun `producto nulo se acepta`() = runTest(dispatcher) {
        val body = contract().replace(Regex("\"producto\": \\{[^}]*\\}"), "\"producto\": null")
        server.enqueue(MockResponse().setBody(body))

        val policy = (repository().fetchWallet("t", "c") as WalletFetchResult.Loaded).policies.single()

        assertNull(policy.productName)
        assertNull(policy.productCode)
    }

    @Test
    fun `lista vacia`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody("""{"polizas":[],"sincronizado_en":"2026-11-18T14:05:00Z"}"""))

        assertEquals(WalletFetchResult.Loaded(emptyList()), repository().fetchWallet("t", "c"))
    }

    @Test
    fun `errores del servidor se traducen`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"codigo":"NO_AUTENTICADO"}"""))
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"codigo":"CLIENTE_NO_REGISTRADO"}"""))
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"codigo":"SERVICIO_NO_DISPONIBLE"}"""))
        server.enqueue(MockResponse().setResponseCode(500).setBody("<html>"))

        val repository = repository()

        assertEquals(WalletFetchResult.SessionExpired, repository.fetchWallet("t", "c"))
        assertEquals(WalletFetchResult.ClientNotRegistered, repository.fetchWallet("t", "c"))
        assertEquals(WalletFetchResult.ServiceUnavailable, repository.fetchWallet("t", "c"))
        assertEquals(WalletFetchResult.ServiceUnavailable, repository.fetchWallet("t", "c"))
    }

    @Test
    fun `cuerpo mal formado es servicio no disponible`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody("""{"polizas":[{"poliza_id":"x"}]}"""))
        server.enqueue(MockResponse().setBody("no es json"))

        val repository = repository()

        assertEquals(WalletFetchResult.ServiceUnavailable, repository.fetchWallet("t", "c"))
        assertEquals(WalletFetchResult.ServiceUnavailable, repository.fetchWallet("t", "c"))
    }

    @Test
    fun `sin red es error de red`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertEquals(WalletFetchResult.NetworkError, repository().fetchWallet("t", "c"))
    }

    @Test
    fun `descarga el certificado en pdf`() = runTest(dispatcher) {
        val pdf = "%PDF-1.4 certificado".toByteArray()
        server.enqueue(MockResponse().setHeader("Content-Type", "application/pdf").setBody(Buffer().write(pdf)))

        val result = repository().certificate("token-1", "pol-1", "corr-1")

        assertEquals(CertificateResult.Loaded(pdf), result)
        val request = server.takeRequest()
        assertEquals("/api/v1/bff-mobile/polizas/pol-1/certificado/descarga", request.path)
        assertEquals("Bearer token-1", request.getHeader("Authorization"))
    }

    @Test
    fun `errores del certificado`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(409))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(200))

        val repository = repository()

        assertEquals(CertificateResult.SessionExpired, repository.certificate("t", "p", "c"))
        assertEquals(CertificateResult.NotAvailable, repository.certificate("t", "p", "c"))
        assertEquals(CertificateResult.NotAvailable, repository.certificate("t", "p", "c"))
        assertEquals(CertificateResult.ServiceUnavailable, repository.certificate("t", "p", "c"))
        // 200 sin contenido no es un certificado válido.
        assertTrue(repository.certificate("t", "p", "c") is CertificateResult.ServiceUnavailable)
    }
}
