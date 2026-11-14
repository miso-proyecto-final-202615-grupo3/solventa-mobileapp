package com.solventa.app.data.claims

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class MockClaimsRepositoryTest {
    private val repository = MockClaimsRepository(Clock.fixed(Instant.parse("2026-11-10T20:00:00Z"), ZoneOffset.UTC), latencyMs = 0)

    private fun draft(description: String = "Me atendieron en urgencias.", type: String = "GASTO_MEDICO") = ClaimDraft(
        policyId = MockClaimsRepository.POLICIES.first().id,
        typeCode = type,
        description = description,
        occurredAt = OffsetDateTime.of(2026, 11, 10, 9, 0, 0, 0, ZoneOffset.ofHours(-5)),
    )

    @Test
    fun `la misma clave devuelve el mismo siniestro`() = runTest {
        val first = repository.report("t", draft(), "k-1", "c") as ReportResult.Reported
        val second = repository.report("t", draft(), "k-1", "c") as ReportResult.Reported

        assertFalse(first.replayed)
        assertTrue(second.replayed)
        assertEquals(first.claim, second.claim)
        assertTrue(first.claim.number.startsWith("SIN-2026-"))
        assertEquals(ClaimResult.Found(first.claim), repository.claim("t", first.claim.id, "c"))
    }

    @Test
    fun `reglas de demostracion`() = runTest {
        assertEquals(ReportResult.NetworkError, repository.report("t", draft("Algo pasó #red"), "a", "c"))
        assertEquals(ReportResult.ServiceUnavailable, repository.report("t", draft("Algo pasó #503"), "b", "c"))
        assertEquals(ReportResult.PolicyNotActive, repository.report("t", draft("Algo pasó #vencida"), "d", "c"))
        assertEquals(ReportResult.TypeNotAllowed, repository.report("t", draft(type = "ROBO"), "e", "c"))
    }

    @Test
    fun `las polizas de demostracion excluyen los seguros de vida`() = runTest {
        val loaded = repository.eligiblePolicies("t", "c") as PoliciesResult.Loaded

        assertEquals(listOf("SEGURO_VIAJE", "PROTECCION_DISPOSITIVOS"), loaded.policies.map { it.productCode })
    }
}
