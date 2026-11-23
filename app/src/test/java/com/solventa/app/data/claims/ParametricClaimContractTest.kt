package com.solventa.app.data.claims

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
import java.math.BigDecimal

/** HU4.2.3: el detalle de un siniestro paramétrico (contrato docs/contracts/parametricos) trae canal e indemnización. */
class ParametricClaimContractTest {
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

    private fun repository() = RemoteClaimsRepository(server.url("/").toString(), ioDispatcher = dispatcher, languageTag = { "es-CO" })

    private fun contract(path: String): String = checkNotNull(javaClass.classLoader!!.getResource("contracts/$path")).readText()

    @Test
    fun `detalle parametrico con compensacion`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(contract("parametricos/detalle-siniestro-parametrico.json")))

        val claim = (repository().claim("token", "5e8a1c2d-3f4b-4a6c-9d7e-8f9a0b1c2d3e", "c-1") as ClaimResult.Found).claim

        assertTrue(claim.isParametric)
        assertEquals(ClaimStatus.APPROVED, claim.status)
        assertEquals("LLUVIA_EXTREMA", claim.typeCode)
        assertEquals(Compensation(BigDecimal("300000.00"), "COP", CompensationStatus.PAYMENT_REQUESTED), claim.compensation)
    }

    @Test
    fun `siniestro reportado por el cliente sigue siendo movil y sin compensacion`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(contract("evidencias/detalle-con-evidencias.json")))

        val claim = (repository().claim("token", "c41f0e2a-9b7d-4e55-8a0c-2f6d1b3e9a77", "c-1") as ClaimResult.Found).claim

        assertFalse(claim.isParametric)
        assertNull(claim.compensation)
    }

    @Test
    fun `canal y estado de pago desconocidos no rompen el detalle`() = runTest(dispatcher) {
        val body = contract("parametricos/detalle-siniestro-parametrico.json")
            .replace("\"PARAMETRICO\"", "\"OTRO\"").replace("PAGO_SOLICITADO", "EN_TRAMITE")
        server.enqueue(MockResponse().setBody(body))

        val claim = (repository().claim("token", "x", "c-1") as ClaimResult.Found).claim

        assertEquals(ClaimChannel.MOBILE, claim.channel)
        assertNull(claim.compensation?.status)
    }
}
