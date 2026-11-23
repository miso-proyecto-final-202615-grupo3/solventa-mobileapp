package com.solventa.app.feature.claims.report

import com.solventa.app.data.claims.MockClaimsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ClaimFormValidatorTest {
    private val zone = ZoneId.of("America/Bogota")
    private val clock = Clock.fixed(ZonedDateTime.of(2026, 11, 10, 15, 0, 0, 0, zone).toInstant(), zone)
    private val validator = ClaimFormValidator(clock)
    private val policy = MockClaimsRepository.POLICIES.first() // vigencia 2026-03-12 a 2027-03-12

    private fun details(
        type: String? = "GASTO_MEDICO",
        description: String = "Me atendieron en urgencias.",
        date: LocalDate? = LocalDate.of(2026, 11, 10),
        time: LocalTime? = LocalTime.of(10, 0),
    ) = validator.validateDetails(policy, type, description, date, time)

    @Test
    fun `datos completos no tienen errores`() {
        assertEquals(emptySet<ClaimFieldError>(), details())
    }

    @Test
    fun `la poliza debe estar en la lista`() {
        assertEquals(setOf(ClaimFieldError.POLICY_REQUIRED), validator.validatePolicy(null, MockClaimsRepository.POLICIES))
        assertEquals(setOf(ClaimFieldError.POLICY_REQUIRED), validator.validatePolicy("otra", MockClaimsRepository.POLICIES))
        assertEquals(emptySet<ClaimFieldError>(), validator.validatePolicy(policy.id, MockClaimsRepository.POLICIES))
    }

    @Test
    fun `el tipo debe ser de la poliza`() {
        assertTrue(ClaimFieldError.TYPE_REQUIRED in details(type = null))
        assertTrue(ClaimFieldError.TYPE_REQUIRED in details(type = "ROBO"))
    }

    @Test
    fun `limites de la descripcion sin contar espacios de los extremos`() {
        assertTrue(ClaimFieldError.DESCRIPTION_TOO_SHORT in details(description = "   123456789   "))
        assertTrue(ClaimFieldError.DESCRIPTION_TOO_SHORT !in details(description = "1234567890"))
        assertTrue(ClaimFieldError.DESCRIPTION_TOO_LONG !in details(description = "x".repeat(500)))
        assertTrue(ClaimFieldError.DESCRIPTION_TOO_LONG in details(description = "x".repeat(501)))
    }

    @Test
    fun `fecha y hora obligatorias`() {
        assertTrue(ClaimFieldError.DATE_REQUIRED in details(date = null))
        assertTrue(ClaimFieldError.TIME_REQUIRED in details(time = null))
    }

    @Test
    fun `no puede ser posterior a ahora en la zona del telefono`() {
        assertTrue(ClaimFieldError.DATE_IN_FUTURE in details(time = LocalTime.of(15, 1)))
        assertTrue(ClaimFieldError.DATE_IN_FUTURE !in details(time = LocalTime.of(15, 0)))
        assertTrue(ClaimFieldError.DATE_IN_FUTURE in details(date = LocalDate.of(2026, 11, 11)))
    }

    @Test
    fun `bordes de la vigencia son inclusivos`() {
        assertTrue(ClaimFieldError.DATE_OUT_OF_COVERAGE !in details(date = LocalDate.of(2026, 3, 12)))
        assertTrue(ClaimFieldError.DATE_OUT_OF_COVERAGE in details(date = LocalDate.of(2026, 3, 11)))
    }

    @Test
    fun `fechas elegibles terminan hoy si la vigencia sigue`() {
        assertEquals(LocalDate.of(2026, 3, 12)..LocalDate.of(2026, 11, 10), validator.selectableDates(policy))
    }

    @Test
    fun `fechas elegibles terminan con la vigencia si ya acabo`() {
        val vencida = policy.copy(validFrom = LocalDate.of(2025, 1, 1), validTo = LocalDate.of(2025, 12, 31))

        assertEquals(LocalDate.of(2025, 1, 1)..LocalDate.of(2025, 12, 31), validator.selectableDates(vencida))
    }
}
