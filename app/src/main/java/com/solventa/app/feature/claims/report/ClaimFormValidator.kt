package com.solventa.app.feature.claims.report

import com.solventa.app.data.claims.EligiblePolicy
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class ClaimFieldError {
    POLICY_REQUIRED,
    TYPE_REQUIRED,
    DESCRIPTION_TOO_SHORT,
    DESCRIPTION_TOO_LONG,
    DATE_REQUIRED,
    TIME_REQUIRED,
    DATE_IN_FUTURE,
    DATE_OUT_OF_COVERAGE,
}

/**
 * Reglas del formulario de reporte (HU4.1.1). El backend vuelve a validar todo; aquí se evita
 * enviar algo que seguro va a fallar. [clock] trae la zona del teléfono: las fechas son locales.
 */
class ClaimFormValidator(private val clock: Clock) {

    fun validatePolicy(policyId: String?, policies: List<EligiblePolicy>): Set<ClaimFieldError> =
        if (policyId == null || policies.none { it.id == policyId }) setOf(ClaimFieldError.POLICY_REQUIRED) else emptySet()

    fun validateDetails(
        policy: EligiblePolicy,
        typeCode: String?,
        description: String,
        date: LocalDate?,
        time: LocalTime?,
    ): Set<ClaimFieldError> = buildSet {
        if (typeCode == null || policy.claimTypes.none { it.code == typeCode }) add(ClaimFieldError.TYPE_REQUIRED)
        val length = description.trim().length
        if (length < MIN_DESCRIPTION) add(ClaimFieldError.DESCRIPTION_TOO_SHORT)
        if (length > MAX_DESCRIPTION) add(ClaimFieldError.DESCRIPTION_TOO_LONG)
        if (date == null) {
            add(ClaimFieldError.DATE_REQUIRED)
        } else if (date < policy.validFrom || date > policy.validTo) {
            add(ClaimFieldError.DATE_OUT_OF_COVERAGE)
        }
        if (time == null) add(ClaimFieldError.TIME_REQUIRED)
        if (date != null && time != null && LocalDateTime.of(date, time).isAfter(LocalDateTime.now(clock))) {
            add(ClaimFieldError.DATE_IN_FUTURE)
        }
    }

    /** Fechas elegibles en el selector: desde el inicio de la vigencia hasta hoy o el fin de la vigencia. */
    fun selectableDates(policy: EligiblePolicy): ClosedRange<LocalDate> {
        val today = LocalDate.now(clock)
        val end = minOf(policy.validTo, today)
        return policy.validFrom..maxOf(end, policy.validFrom)
    }

    companion object {
        const val MIN_DESCRIPTION = 10
        const val MAX_DESCRIPTION = 500
    }
}
