package com.solventa.app.data.claims

import com.solventa.app.core.location.LocationFix
import com.solventa.app.data.claims.evidence.RemoteEvidence
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

/** Cuántas fotos y videos admite un tipo de siniestro (tabla `tipo_siniestro` del backend). */
data class EvidenceRules(val maxPhotos: Int, val maxVideos: Int) {
    val allowsVideo: Boolean get() = maxVideos > 0
}

/** Tipo de siniestro de un producto; [name] llega del backend en el idioma pedido. */
data class ClaimType(val code: String, val name: String, val evidenceRules: EvidenceRules)

data class EligiblePolicy(
    val id: String,
    val number: String,
    val productCode: String,
    val productName: String,
    val validFrom: LocalDate,
    val validTo: LocalDate,
    val claimTypes: List<ClaimType>,
)

data class ClaimDraft(
    val policyId: String,
    val typeCode: String,
    val description: String,
    val occurredAt: OffsetDateTime,
)

enum class ClaimStatus(val code: String) {
    REPORTED("REPORTADO"),
    IN_REVIEW("EN_EVALUACION"),
    APPROVED("APROBADO"),
    REJECTED("RECHAZADO"),
    PAID("PAGADO"),
    ;

    companion object {
        fun fromCode(code: String): ClaimStatus? = entries.firstOrNull { it.code == code }
    }
}

data class ReportedClaim(
    val id: String,
    val number: String,
    val status: ClaimStatus,
    val policyId: String,
    val policyNumber: String,
    val typeCode: String,
    val occurredAt: Instant,
    val reportedAt: Instant,
    /** HU4.1.2: solo en el detalle (`GET /siniestros/{id}`). */
    val location: LocationFix? = null,
    val evidences: List<RemoteEvidence> = emptyList(),
    /** HU4.2.3: reportado por el cliente o creado automáticamente por un evento paramétrico. */
    val channel: ClaimChannel = ClaimChannel.MOBILE,
    /** HU4.2.3: solo en el detalle, cuando el siniestro tiene indemnización. */
    val compensation: Compensation? = null,
) {
    val isParametric: Boolean get() = channel == ClaimChannel.PARAMETRIC
}

enum class ClaimChannel(val code: String) {
    MOBILE("MOVIL"),
    PARAMETRIC("PARAMETRICO"),
    ;

    companion object {
        /** Un canal desconocido se trata como reporte del cliente (no cambia lo que se muestra). */
        fun fromCode(code: String?): ClaimChannel = entries.firstOrNull { it.code == code } ?: MOBILE
    }
}

enum class CompensationStatus(val code: String) {
    PAYMENT_REQUESTED("PAGO_SOLICITADO"),
    PAID("PAGADA"),
    ;

    companion object {
        fun fromCode(code: String): CompensationStatus? = entries.firstOrNull { it.code == code }
    }
}

/** HU4.2.3: compensación aprobada; [amount] es exacto (llega como texto decimal). */
data class Compensation(val amount: java.math.BigDecimal, val currency: String, val status: CompensationStatus?)

sealed interface PoliciesResult {
    data class Loaded(val policies: List<EligiblePolicy>) : PoliciesResult
    data object ClientNotRegistered : PoliciesResult
    data object SessionExpired : PoliciesResult
    data object NetworkError : PoliciesResult
    data object ServiceUnavailable : PoliciesResult
}

sealed interface ReportResult {
    /** [replayed] = el backend ya tenía este reporte (misma clave de idempotencia). */
    data class Reported(val claim: ReportedClaim, val replayed: Boolean) : ReportResult
    data object PolicyNotFound : ReportResult
    data object PolicyNotActive : ReportResult
    data object OutOfCoverage : ReportResult
    data object InvalidDate : ReportResult
    data object TypeNotAllowed : ReportResult
    data object InvalidData : ReportResult
    data object IdempotencyConflict : ReportResult
    data object ClientNotRegistered : ReportResult
    data object SessionExpired : ReportResult
    data object NetworkError : ReportResult
    data object ServiceUnavailable : ReportResult
}

sealed interface ClaimResult {
    data class Found(val claim: ReportedClaim) : ClaimResult
    data object NotFound : ClaimResult
    data object SessionExpired : ClaimResult
    data object NetworkError : ClaimResult
    data object ServiceUnavailable : ClaimResult
}

/** HU4.2.1: una página de "Mis siniestros", más reciente primero. */
data class ClaimsPage(val claims: List<ReportedClaim>, val page: Int, val pageSize: Int, val total: Int) {
    val hasMore: Boolean get() = page * pageSize < total
}

sealed interface ClaimsPageResult {
    data class Loaded(val page: ClaimsPage) : ClaimsPageResult
    data object SessionExpired : ClaimsPageResult
    data object NetworkError : ClaimsPageResult
    data object ServiceUnavailable : ClaimsPageResult
}
