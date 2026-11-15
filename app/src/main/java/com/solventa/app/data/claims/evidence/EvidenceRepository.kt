package com.solventa.app.data.claims.evidence

import com.solventa.app.core.location.LocationFix
import java.time.OffsetDateTime

sealed interface TicketResult {
    data class Issued(val ticket: UploadTicket) : TicketResult
    /** Reintento de una evidencia que el backend ya tiene `CARGADA` (se perdió la respuesta de la confirmación). */
    data object AlreadyUploaded : TicketResult
    /** El backend no la acepta (límite, tamaño, tipo o siniestro cerrado): no se reintenta. */
    data class Refused(val problem: EvidenceProblem) : TicketResult
    data object SessionExpired : TicketResult
    data object NetworkError : TicketResult
    data object ServiceUnavailable : TicketResult
}

sealed interface ConfirmResult {
    data object Confirmed : ConfirmResult
    data object NotUploadedYet : ConfirmResult
    data object Expired : ConfirmResult
    data object Rejected : ConfirmResult
    data object SessionExpired : ConfirmResult
    data object NetworkError : ConfirmResult
    data object ServiceUnavailable : ConfirmResult
}

sealed interface LocationSaveResult {
    data object Saved : LocationSaveResult
    data object ClaimClosed : LocationSaveResult
    data object SessionExpired : LocationSaveResult
    data object NetworkError : LocationSaveResult
    data object ServiceUnavailable : LocationSaveResult
}

/** Evidencias y ubicación del siniestro contra el BFF Móvil (contrato canónico del plan de HU4.1.2 §3). */
interface EvidenceRepository {
    /**
     * `POST /siniestros/{id}/evidencias`: registra la evidencia y entrega la carga prefirmada.
     * [idempotencyKey] es fija por evidencia local: repetir la solicitud renueva la misma evidencia en el
     * backend en vez de ocupar otro cupo.
     */
    suspend fun requestUpload(
        accessToken: String,
        claimId: String,
        kind: EvidenceKind,
        sizeBytes: Long,
        origin: EvidenceOrigin,
        capturedAt: OffsetDateTime,
        idempotencyKey: String,
        correlationId: String,
    ): TicketResult

    /** `POST /siniestros/{id}/evidencias/{evidencia_id}/confirmacion` (idempotente). */
    suspend fun confirm(accessToken: String, claimId: String, evidenceId: String, correlationId: String): ConfirmResult

    /** `PUT /siniestros/{id}/ubicacion`. */
    suspend fun saveLocation(accessToken: String, claimId: String, fix: LocationFix, correlationId: String): LocationSaveResult
}
