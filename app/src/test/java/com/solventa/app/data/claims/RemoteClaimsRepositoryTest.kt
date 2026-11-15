package com.solventa.app.data.claims

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class RemoteClaimsRepositoryTest {
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

    private fun repository() = RemoteClaimsRepository(
        baseUrl = server.url("/").toString(),
        ioDispatcher = dispatcher,
        languageTag = { "es-CO" },
    )

    private fun contract(name: String): String =
        checkNotNull(javaClass.classLoader!!.getResource("contracts/siniestros/$name")).readText()

    private val draft = ClaimDraft(
        policyId = "8d2b6c1e-4a7f-4f0e-9a51-0c6b1d9e2f10",
        typeCode = "GASTO_MEDICO",
        description = "Me atendieron en urgencias por una intoxicación durante el viaje.",
        occurredAt = OffsetDateTime.of(2026, 11, 10, 14, 30, 0, 0, ZoneOffset.ofHours(-5)),
    )

    @Test
    fun `polizas elegibles del contrato con headers`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(contract("polizas-elegibles.json")))

        val result = repository().eligiblePolicies("token-1", "corr-1") as PoliciesResult.Loaded

        val request = server.takeRequest()
        assertEquals("/api/v1/bff-mobile/siniestros/polizas-elegibles", request.path)
        assertEquals("Bearer token-1", request.getHeader("Authorization"))
        assertEquals("corr-1", request.getHeader("X-Correlation-Id"))
        assertEquals("es-CO", request.getHeader("Accept-Language"))
        val policy = result.policies.single()
        assertEquals("SLV-2026-08341", policy.number)
        assertEquals(LocalDate.of(2027, 3, 12), policy.validTo)
        assertEquals(listOf("GASTO_MEDICO", "EQUIPAJE", "CANCELACION_VIAJE"), policy.claimTypes.map { it.code })
        assertEquals(EvidenceRules(5, 1), policy.claimTypes[1].evidenceRules)
        assertTrue(policy.claimTypes[1].evidenceRules.allowsVideo)
    }

    @Test
    fun `reporte del contrato con idempotencia y sin correo`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(201).setBody(contract("reporte-response.json")))

        val result = repository().report("token-1", draft, "clave-1", "corr-2") as ReportResult.Reported

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/bff-mobile/siniestros", request.path)
        assertEquals("clave-1", request.getHeader("Idempotency-Key"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        val expected = Json.parseToJsonElement(contract("reporte-request.json")).jsonObject
        assertEquals(expected, body)
        assertFalse("correo" in body || "cliente_id" in body)
        assertFalse(result.replayed)
        assertEquals("SIN-2026-C41F0E2A", result.claim.number)
        assertEquals(ClaimStatus.REPORTED, result.claim.status)
        assertEquals(Instant.parse("2026-11-10T19:30:00Z"), result.claim.occurredAt)
    }

    @Test
    fun `respuesta 200 es un reintento ya registrado`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(contract("reporte-response.json")))

        val result = repository().report("t", draft, "clave-1", "c") as ReportResult.Reported

        assertTrue(result.replayed)
    }

    @Test
    fun `mapeo de errores del reporte`() = runTest(dispatcher) {
        val casos = listOf(
            Triple(401, null, ReportResult.SessionExpired),
            Triple(404, "POLIZA_NO_ENCONTRADA", ReportResult.PolicyNotFound),
            Triple(404, "CLIENTE_NO_REGISTRADO", ReportResult.ClientNotRegistered),
            Triple(409, "POLIZA_NO_VIGENTE", ReportResult.PolicyNotActive),
            Triple(409, "FECHA_FUERA_DE_VIGENCIA", ReportResult.OutOfCoverage),
            Triple(409, "IDEMPOTENCIA_CONFLICTO", ReportResult.IdempotencyConflict),
            Triple(422, "FECHA_INVALIDA", ReportResult.InvalidDate),
            Triple(422, "TIPO_NO_PERMITIDO", ReportResult.TypeNotAllowed),
            Triple(422, null, ReportResult.InvalidData),
            Triple(400, "DATOS_INVALIDOS", ReportResult.InvalidData),
            Triple(503, "SERVICIO_NO_DISPONIBLE", ReportResult.ServiceUnavailable),
        )
        casos.forEach { (code, codigo, expected) ->
            val body = codigo?.let { """{"codigo":"$it","mensaje":"x","correlation_id":"c"}""" } ?: """{"detail":[]}"""
            server.enqueue(MockResponse().setResponseCode(code).setBody(body))
            assertEquals("$code $codigo", expected, repository().report("t", draft, "k", "c"))
        }
    }

    @Test
    fun `el error comun del contrato se mapea`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(409).setBody(contract("error-poliza-no-vigente.json")))

        assertEquals(ReportResult.PolicyNotActive, repository().report("t", draft, "k", "c"))
    }

    @Test
    fun `estado desconocido o cuerpo mal formado es servicio no disponible`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(201).setBody(contract("reporte-response.json").replace("REPORTADO", "OTRO")))
        server.enqueue(MockResponse().setResponseCode(201).setBody("{no es json"))

        assertEquals(ReportResult.ServiceUnavailable, repository().report("t", draft, "k", "c"))
        assertEquals(ReportResult.ServiceUnavailable, repository().report("t", draft, "k", "c"))
    }

    @Test
    fun `desconexion es error de red`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertEquals(ReportResult.NetworkError, repository().report("t", draft, "k", "c"))
        assertEquals(PoliciesResult.NetworkError, repository().eligiblePolicies("t", "c"))
    }

    @Test
    fun `errores al cargar polizas`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"codigo":"CLIENTE_NO_REGISTRADO"}"""))
        server.enqueue(MockResponse().setResponseCode(503))

        assertEquals(PoliciesResult.SessionExpired, repository().eligiblePolicies("t", "c"))
        assertEquals(PoliciesResult.ClientNotRegistered, repository().eligiblePolicies("t", "c"))
        assertEquals(PoliciesResult.ServiceUnavailable, repository().eligiblePolicies("t", "c"))
    }

    @Test
    fun `consulta de un siniestro`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(contract("reporte-response.json")))
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"codigo":"SINIESTRO_NO_ENCONTRADO"}"""))

        val found = repository().claim("t", "c41f0e2a-9b7d-4e55-8a0c-2f6d1b3e9a77", "c") as ClaimResult.Found

        assertEquals("/api/v1/bff-mobile/siniestros/c41f0e2a-9b7d-4e55-8a0c-2f6d1b3e9a77", server.takeRequest().path)
        assertEquals("SLV-2026-08341", found.claim.policyNumber)
        assertEquals(ClaimResult.NotFound, repository().claim("t", "otro", "c"))
    }

    @Test
    fun `el modo demo envia el correo solo como header`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(contract("polizas-elegibles.json")))
        val repo = RemoteClaimsRepository(server.url("/").toString(), ioDispatcher = dispatcher, demoEmail = "demo@example.com")

        repo.eligiblePolicies("t", "c")

        assertEquals("demo@example.com", server.takeRequest().getHeader("X-Demo-Correo"))
    }

    @Test
    fun `la fecha se envia con la zona del telefono`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(201).setBody(contract("reporte-response.json")))

        repository().report("t", draft, "k", "c")

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("2026-11-10T14:30:00-05:00", body["ocurrido_en"]!!.jsonPrimitive.content)
    }
}
