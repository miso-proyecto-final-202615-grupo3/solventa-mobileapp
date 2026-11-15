package com.solventa.app.data.claims

import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Backend de siniestros simulado (mismas formas que el contrato), con los datos del prototipo móvil.
 *
 * Reglas de demostración según la descripción del reporte:
 * - contiene `#red` → falla de red; `#503` → servicio no disponible; `#vencida` → póliza no vigente.
 * La misma clave de idempotencia devuelve el mismo siniestro con `replayed = true`.
 */
class MockClaimsRepository(
    private val clock: Clock = Clock.systemUTC(),
    private val latencyMs: Long = 600,
) : ClaimsRepository {
    private val reported = mutableMapOf<String, ReportedClaim>()

    override suspend fun eligiblePolicies(accessToken: String, correlationId: String): PoliciesResult {
        delay(latencyMs)
        return PoliciesResult.Loaded(POLICIES)
    }

    override suspend fun report(
        accessToken: String,
        draft: ClaimDraft,
        idempotencyKey: String,
        correlationId: String,
    ): ReportResult {
        delay(latencyMs)
        when {
            NETWORK_TAG in draft.description -> return ReportResult.NetworkError
            UNAVAILABLE_TAG in draft.description -> return ReportResult.ServiceUnavailable
            EXPIRED_TAG in draft.description -> return ReportResult.PolicyNotActive
        }
        reported[idempotencyKey]?.let { return ReportResult.Reported(it, replayed = true) }
        val policy = POLICIES.firstOrNull { it.id == draft.policyId } ?: return ReportResult.PolicyNotFound
        if (policy.claimTypes.none { it.code == draft.typeCode }) return ReportResult.TypeNotAllowed
        val id = UUID.randomUUID()
        val now = Instant.now(clock)
        val claim = ReportedClaim(
            id = id.toString(),
            number = "SIN-${LocalDate.now(clock).year}-${id.toString().replace("-", "").take(8).uppercase()}",
            status = ClaimStatus.REPORTED,
            policyId = policy.id,
            policyNumber = policy.number,
            typeCode = draft.typeCode,
            occurredAt = draft.occurredAt.toInstant(),
            reportedAt = now,
        )
        reported[idempotencyKey] = claim
        return ReportResult.Reported(claim, replayed = false)
    }

    /** Reglas de evidencia del tipo del siniestro simulado (las usa el repositorio de evidencias simulado). */
    fun evidenceRulesFor(claimId: String): EvidenceRules {
        val claim = reported.values.firstOrNull { it.id == claimId } ?: return EvidenceRules(5, 1)
        return POLICIES.flatMap { it.claimTypes }.firstOrNull { it.code == claim.typeCode }?.evidenceRules ?: EvidenceRules(5, 1)
    }

    override suspend fun claim(accessToken: String, claimId: String, correlationId: String): ClaimResult {
        delay(latencyMs)
        return reported.values.firstOrNull { it.id == claimId }?.let { ClaimResult.Found(it) } ?: ClaimResult.NotFound
    }

    companion object {
        const val NETWORK_TAG = "#red"
        const val UNAVAILABLE_TAG = "#503"
        const val EXPIRED_TAG = "#vencida"

        val POLICIES = listOf(
            EligiblePolicy(
                id = "8d2b6c1e-4a7f-4f0e-9a51-0c6b1d9e2f10",
                number = "SLV-2026-08341",
                productCode = "SEGURO_VIAJE",
                productName = "Seguro de viaje",
                validFrom = LocalDate.of(2026, 3, 12),
                validTo = LocalDate.of(2027, 3, 12),
                claimTypes = listOf(
                    ClaimType("GASTO_MEDICO", "Gasto médico", EvidenceRules(5, 0)),
                    ClaimType("EQUIPAJE", "Pérdida o daño de equipaje", EvidenceRules(5, 1)),
                    ClaimType("CANCELACION_VIAJE", "Cancelación del viaje", EvidenceRules(3, 0)),
                ),
            ),
            EligiblePolicy(
                id = "5b1f9c2d-7e3a-4d6b-8f10-2c4e6a8b0d12",
                number = "SLV-2026-09120",
                productCode = "PROTECCION_DISPOSITIVOS",
                productName = "Protección de dispositivos",
                validFrom = LocalDate.of(2026, 6, 1),
                validTo = LocalDate.of(2027, 6, 1),
                claimTypes = listOf(
                    ClaimType("DANO_DISPOSITIVO", "Daño al dispositivo", EvidenceRules(5, 1)),
                    ClaimType("ROBO", "Robo", EvidenceRules(3, 1)),
                ),
            ),
        )
    }
}
