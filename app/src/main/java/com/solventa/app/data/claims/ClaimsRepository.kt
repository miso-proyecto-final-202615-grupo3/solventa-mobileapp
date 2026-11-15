package com.solventa.app.data.claims

/**
 * Siniestros contra el BFF Móvil (HU4.1.1). Contrato canónico: sección 3 del plan del backend.
 * La app nunca envía el correo ni el `cliente_id`: el BFF los toma del token.
 */
interface ClaimsRepository {
    /** `GET /api/v1/bff-mobile/siniestros/polizas-elegibles`. */
    suspend fun eligiblePolicies(accessToken: String, correlationId: String): PoliciesResult

    /** `POST /api/v1/bff-mobile/siniestros` con `Idempotency-Key`: reintentar con la misma clave no duplica. */
    suspend fun report(accessToken: String, draft: ClaimDraft, idempotencyKey: String, correlationId: String): ReportResult

    /** `GET /api/v1/bff-mobile/siniestros/{id}`. */
    suspend fun claim(accessToken: String, claimId: String, correlationId: String): ClaimResult
}
