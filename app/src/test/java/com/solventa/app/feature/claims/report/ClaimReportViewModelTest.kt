package com.solventa.app.feature.claims.report

import androidx.lifecycle.SavedStateHandle
import com.solventa.app.MainDispatcherRule
import com.solventa.app.core.biometric.testSession
import com.solventa.app.data.claims.ClaimDraft
import com.solventa.app.data.claims.ClaimResult
import com.solventa.app.data.claims.ClaimsPageResult
import com.solventa.app.data.claims.ClaimStatus
import com.solventa.app.data.claims.ClaimsRepository
import com.solventa.app.data.claims.MockClaimsRepository
import com.solventa.app.data.claims.PoliciesResult
import com.solventa.app.data.claims.ReportResult
import com.solventa.app.data.claims.ReportedClaim
import com.solventa.app.data.session.InMemorySessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ClaimReportViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zone = ZoneId.of("America/Bogota")
    private val clock = Clock.fixed(ZonedDateTime.of(2026, 11, 10, 15, 0, 0, 0, zone).toInstant(), zone)
    private val travel = MockClaimsRepository.POLICIES[0]
    private val devices = MockClaimsRepository.POLICIES[1]

    private class FakeRepository : ClaimsRepository {
        var policies: PoliciesResult = PoliciesResult.Loaded(MockClaimsRepository.POLICIES)
        val results = ArrayDeque<ReportResult>()
        val reports = mutableListOf<Pair<ClaimDraft, String>>()

        override suspend fun eligiblePolicies(accessToken: String, correlationId: String) = policies

        override suspend fun report(accessToken: String, draft: ClaimDraft, idempotencyKey: String, correlationId: String): ReportResult {
            reports += draft to idempotencyKey
            return results.removeFirstOrNull() ?: ReportResult.Reported(claim(), replayed = false)
        }

        override suspend fun claim(accessToken: String, claimId: String, correlationId: String) = ClaimResult.NotFound
        override suspend fun claims(accessToken: String, policyId: String?, page: Int, pageSize: Int, correlationId: String) =
            ClaimsPageResult.ServiceUnavailable
    }

    private val repository = FakeRepository()
    private val sessionStore = InMemorySessionStore().apply { save(testSession()) }
    private var keys = 0

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) = ClaimReportViewModel(
        repository = repository,
        sessionStore = sessionStore,
        validator = ClaimFormValidator(clock),
        clock = clock,
        savedState = savedState,
        newKey = { "clave-${++keys}" },
        newCorrelationId = { "corr" },
    )

    private fun ClaimReportViewModel.fillValidDetails() {
        selectPolicy(travel.id)
        continueFromPolicy()
        selectType("GASTO_MEDICO")
        onDescriptionChange("Me atendieron en urgencias.")
        onDateSelected(LocalDate.of(2026, 11, 10))
        onTimeSelected(LocalTime.of(9, 30))
        continueFromDetails()
    }

    @Test
    fun `carga las polizas y no preselecciona si hay varias`() {
        val state = viewModel().uiState.value

        assertEquals(2, state.policies.size)
        assertNull(state.selectedPolicyId)
        assertEquals(ReportStep.POLICY, state.step)
    }

    @Test
    fun `con una sola poliza la preselecciona`() {
        repository.policies = PoliciesResult.Loaded(listOf(devices))

        assertEquals(devices.id, viewModel().uiState.value.selectedPolicyId)
    }

    @Test
    fun `sin polizas queda la lista vacia sin error`() {
        repository.policies = PoliciesResult.Loaded(emptyList())

        val state = viewModel().uiState.value

        assertTrue(state.policies.isEmpty())
        assertNull(state.error)
    }

    @Test
    fun `falla de red al cargar y reintento`() {
        repository.policies = PoliciesResult.NetworkError
        val vm = viewModel()
        assertEquals(ReportError.NETWORK, vm.uiState.value.error)

        repository.policies = PoliciesResult.Loaded(MockClaimsRepository.POLICIES)
        vm.retry()

        assertNull(vm.uiState.value.error)
        assertEquals(2, vm.uiState.value.policies.size)
    }

    @Test
    fun `sin sesion o sesion vencida va al login`() {
        repository.policies = PoliciesResult.SessionExpired
        assertEquals(ReportDestination.Login, viewModel().uiState.value.destination)

        sessionStore.clear()
        assertEquals(ReportDestination.Login, viewModel().uiState.value.destination)
    }

    @Test
    fun `no avanza sin poliza ni con errores en los detalles`() {
        val vm = viewModel()
        vm.continueFromPolicy()
        assertEquals(setOf(ClaimFieldError.POLICY_REQUIRED), vm.uiState.value.fieldErrors)

        vm.selectPolicy(travel.id)
        vm.continueFromPolicy()
        vm.continueFromDetails()

        assertEquals(ReportStep.DETAILS, vm.uiState.value.step)
        assertTrue(ClaimFieldError.TYPE_REQUIRED in vm.uiState.value.fieldErrors)
    }

    @Test
    fun `cambiar a una poliza sin ese tipo limpia el tipo`() {
        val vm = viewModel()
        vm.selectPolicy(travel.id)
        vm.selectType("GASTO_MEDICO")

        vm.selectPolicy(devices.id)

        assertNull(vm.uiState.value.selectedTypeCode)
    }

    @Test
    fun `envio exitoso con la fecha en la zona del telefono`() {
        val vm = viewModel()
        vm.fillValidDetails()
        assertEquals(ReportStep.REVIEW, vm.uiState.value.step)

        vm.submit()

        val (draft, key) = repository.reports.single()
        assertEquals(OffsetDateTime.of(2026, 11, 10, 9, 30, 0, 0, ZoneOffset.ofHours(-5)), draft.occurredAt)
        assertEquals("Me atendieron en urgencias.", draft.description)
        assertEquals("clave-1", key)
        assertEquals(ReportStep.DONE, vm.uiState.value.step)
        assertEquals("SIN-2026-C41F0E2A", vm.uiState.value.reported?.number)
    }

    @Test
    fun `reintento tras falla de red usa la misma clave`() {
        repository.results += ReportResult.NetworkError
        val vm = viewModel()
        vm.fillValidDetails()

        vm.submit()
        assertEquals(ReportError.NETWORK, vm.uiState.value.error)
        assertEquals(ReportStep.REVIEW, vm.uiState.value.step)
        vm.retry()

        assertEquals(listOf("clave-1", "clave-1"), repository.reports.map { it.second })
        assertEquals(ReportStep.DONE, vm.uiState.value.step)
    }

    @Test
    fun `editar tras un error genera otra clave`() {
        repository.results += ReportResult.ServiceUnavailable
        val vm = viewModel()
        vm.fillValidDetails()
        vm.submit()

        vm.editDetails()
        vm.onDescriptionChange("Me atendieron en urgencias del hospital.")
        vm.continueFromDetails()
        vm.submit()

        assertEquals(listOf("clave-1", "clave-2"), repository.reports.map { it.second })
    }

    @Test
    fun `submit fuera de revisar se ignora`() {
        val vm = viewModel()
        vm.submit()

        assertTrue(repository.reports.isEmpty())
    }

    @Test
    fun `cada error del backend se muestra en revisar`() {
        val casos = mapOf(
            ReportResult.PolicyNotActive to ReportError.POLICY_NOT_ACTIVE,
            ReportResult.OutOfCoverage to ReportError.OUT_OF_COVERAGE,
            ReportResult.InvalidDate to ReportError.INVALID_DATE,
            ReportResult.TypeNotAllowed to ReportError.TYPE_NOT_ALLOWED,
            ReportResult.InvalidData to ReportError.INVALID_DATA,
            ReportResult.PolicyNotFound to ReportError.POLICY_NOT_FOUND,
            ReportResult.ServiceUnavailable to ReportError.SERVICE_UNAVAILABLE,
            ReportResult.IdempotencyConflict to ReportError.SERVICE_UNAVAILABLE,
        )
        casos.forEach { (result, error) ->
            repository.results += result
            val vm = viewModel()
            vm.fillValidDetails()
            vm.submit()
            assertEquals("$result", error, vm.uiState.value.error)
        }
    }

    @Test
    fun `sesion vencida al enviar va al login`() {
        repository.results += ReportResult.SessionExpired
        val vm = viewModel()
        vm.fillValidDetails()

        vm.submit()

        assertEquals(ReportDestination.Login, vm.uiState.value.destination)
    }

    @Test
    fun `restaura el borrador y la clave tras morir el proceso`() {
        val saved = SavedStateHandle()
        repository.results += ReportResult.NetworkError
        viewModel(saved).apply {
            fillValidDetails()
            submit()
        }

        val restored = viewModel(saved)
        restored.retry()

        assertEquals(ReportStep.DONE, restored.uiState.value.step)
        assertEquals(listOf("clave-1", "clave-1"), repository.reports.map { it.second })
    }

    @Test
    fun `atras vuelve un paso y en el primero no hace nada`() {
        val vm = viewModel()
        assertEquals(false, vm.back())
        vm.fillValidDetails()

        assertEquals(true, vm.back())
        assertEquals(ReportStep.DETAILS, vm.uiState.value.step)
    }

    @Test
    fun `adjuntar evidencia navega con el id del siniestro`() {
        val vm = viewModel()
        vm.fillValidDetails()
        vm.submit()

        vm.goToEvidence()

        assertEquals(ReportDestination.Evidence("c41f0e2a"), vm.uiState.value.destination)
        vm.consumeDestination()
        assertNull(vm.uiState.value.destination)
    }

    private companion object {
        fun claim() = ReportedClaim(
            id = "c41f0e2a",
            number = "SIN-2026-C41F0E2A",
            status = ClaimStatus.REPORTED,
            policyId = "8d2b6c1e-4a7f-4f0e-9a51-0c6b1d9e2f10",
            policyNumber = "SLV-2026-08341",
            typeCode = "GASTO_MEDICO",
            occurredAt = Instant.parse("2026-11-10T14:30:00Z"),
            reportedAt = Instant.parse("2026-11-10T20:00:00Z"),
        )
    }
}
