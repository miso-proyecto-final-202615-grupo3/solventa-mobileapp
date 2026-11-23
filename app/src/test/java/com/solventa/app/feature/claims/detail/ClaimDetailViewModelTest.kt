package com.solventa.app.feature.claims.detail

import com.solventa.app.MainDispatcherRule
import com.solventa.app.core.biometric.testSession
import com.solventa.app.data.claims.ClaimDraft
import com.solventa.app.data.claims.ClaimResult
import com.solventa.app.data.claims.ClaimStatus
import com.solventa.app.data.claims.ClaimsPageResult
import com.solventa.app.data.claims.ClaimsRepository
import com.solventa.app.data.claims.MockClaimsRepository
import com.solventa.app.data.claims.PoliciesResult
import com.solventa.app.data.claims.ReportResult
import com.solventa.app.data.claims.ReportedClaim
import com.solventa.app.data.claims.evidence.DownloadResult
import com.solventa.app.data.claims.evidence.EvidenceKind
import com.solventa.app.data.claims.evidence.FakeEvidenceRepository
import com.solventa.app.data.claims.evidence.RemoteEvidence
import com.solventa.app.data.session.InMemorySessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ClaimDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispositivos = MockClaimsRepository.POLICIES[1]
    private val photo = RemoteEvidence("e1", EvidenceKind.PHOTO, "CARGADA", 2048, Instant.parse("2026-11-10T20:05:00Z"), null)
    private val video = RemoteEvidence("e3", EvidenceKind.VIDEO, "CARGADA", 9000, Instant.parse("2026-11-10T20:07:00Z"), null)
    private val pending = RemoteEvidence("e2", EvidenceKind.VIDEO, "PENDIENTE", null, Instant.parse("2026-11-10T20:06:00Z"), null)

    private fun claim(status: ClaimStatus = ClaimStatus.REPORTED, evidences: List<RemoteEvidence> = listOf(photo, pending, video)) =
        ReportedClaim(
            id = "c1", number = "SIN-2026-C41F0E2A", status = status, policyId = dispositivos.id,
            policyNumber = dispositivos.number, typeCode = "ROBO",
            occurredAt = Instant.parse("2026-11-10T19:30:00Z"), reportedAt = Instant.parse("2026-11-10T20:02:11Z"),
            evidences = evidences,
        )

    private class FakeClaims(var result: ClaimResult) : ClaimsRepository {
        override suspend fun eligiblePolicies(accessToken: String, correlationId: String) = PoliciesResult.Loaded(MockClaimsRepository.POLICIES)
        override suspend fun report(accessToken: String, draft: ClaimDraft, idempotencyKey: String, correlationId: String) = ReportResult.ServiceUnavailable
        override suspend fun claim(accessToken: String, claimId: String, correlationId: String) = result
        override suspend fun claims(accessToken: String, policyId: String?, page: Int, pageSize: Int, correlationId: String) =
            ClaimsPageResult.ServiceUnavailable
    }

    private val evidence = FakeEvidenceRepository()
    private val sessionStore = InMemorySessionStore().apply { save(testSession()) }

    /** El fake entrega URLs que vencen el 2030-01-01T00:05Z. */
    private fun viewModel(result: ClaimResult = ClaimResult.Found(claim()), now: String = "2030-01-01T00:00:00Z") =
        ClaimDetailViewModel("c1", FakeClaims(result), evidence, sessionStore, Clock.fixed(Instant.parse(now), ZoneOffset.UTC))

    @Test
    fun `carga el siniestro con su poliza y tipo, y las URLs de las miniaturas cargadas`() {
        val state = viewModel().uiState.value

        assertFalse(state.loading)
        assertEquals(dispositivos.productName, state.policy?.productName)
        assertEquals("Robo", state.typeName)
        assertTrue(state.canAddEvidence)
        // Solo las CARGADA piden URL; la pendiente no.
        assertEquals(setOf("e1", "e3"), evidence.downloaded.toSet())
        assertEquals("https://s3.test/e1?firma", state.urls["e1"]?.url)
        assertNull(state.urls["e2"])
    }

    @Test
    fun `si una miniatura falla las demas se muestran`() {
        evidence.downloads += DownloadResult.ServiceUnavailable

        val state = viewModel().uiState.value

        assertEquals(1, state.urls.size)
        assertNull(state.message)
    }

    @Test
    fun `abrir una evidencia usa la URL vigente en el visor sin pedir otra`() {
        val vm = viewModel()
        val requests = evidence.downloaded.size

        vm.openEvidence(photo)

        assertEquals(photo, vm.uiState.value.viewing)
        assertEquals("https://s3.test/e1?firma", vm.uiState.value.viewingUrl?.url)
        assertEquals(requests, evidence.downloaded.size)
        vm.closeViewer()
        assertNull(vm.uiState.value.viewing)
    }

    @Test
    fun `con la URL vencida pide una nueva antes de abrir el visor`() {
        val vm = viewModel(now = "2030-01-01T00:04:45Z") // vence a las 00:05, dentro del margen de 30 s

        vm.openEvidence(video)

        assertEquals(3, evidence.downloaded.size)
        assertEquals(video, vm.uiState.value.viewing)
    }

    @Test
    fun `una evidencia pendiente no abre el visor`() {
        val vm = viewModel()

        vm.openEvidence(pending)

        assertNull(vm.uiState.value.viewing)
        assertEquals(ClaimDetailMessage.EVIDENCE_NOT_AVAILABLE, vm.uiState.value.message)
    }

    @Test
    fun `errores al renovar la URL y al mostrar el archivo`() {
        val vm = viewModel(now = "2030-01-01T00:10:00Z")
        evidence.downloads += DownloadResult.NotAvailable
        evidence.downloads += DownloadResult.NetworkError

        vm.openEvidence(photo)
        assertEquals(ClaimDetailMessage.EVIDENCE_NOT_AVAILABLE, vm.uiState.value.message)
        vm.openEvidence(photo)
        assertEquals(ClaimDetailMessage.EVIDENCE_FAILED, vm.uiState.value.message)
        assertNull(vm.uiState.value.viewing)

        vm.openEvidence(photo)
        vm.viewerFailed()
        assertNull(vm.uiState.value.viewing)
        assertEquals(ClaimDetailMessage.EVIDENCE_FAILED, vm.uiState.value.message)
        vm.dismissMessage()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `estado distinto de reportado, errores al cargar y sesion vencida`() {
        assertFalse(viewModel(ClaimResult.Found(claim(ClaimStatus.IN_REVIEW))).uiState.value.canAddEvidence)
        assertEquals(ClaimDetailMessage.NOT_FOUND, viewModel(ClaimResult.NotFound).uiState.value.message)
        assertEquals(ClaimDetailMessage.LOAD_NETWORK, viewModel(ClaimResult.NetworkError).uiState.value.message)
        assertEquals(ClaimDetailMessage.LOAD_SERVICE, viewModel(ClaimResult.ServiceUnavailable).uiState.value.message)
        assertTrue(viewModel(ClaimResult.SessionExpired).uiState.value.goToLogin)

        evidence.downloads += DownloadResult.SessionExpired
        assertTrue(viewModel().uiState.value.goToLogin)
    }
}
