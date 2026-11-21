package com.solventa.app.feature.claims.list

import com.solventa.app.MainDispatcherRule
import com.solventa.app.core.biometric.testSession
import com.solventa.app.data.claims.ClaimDraft
import com.solventa.app.data.claims.ClaimResult
import com.solventa.app.data.claims.ClaimStatus
import com.solventa.app.data.claims.ClaimsPage
import com.solventa.app.data.claims.ClaimsPageResult
import com.solventa.app.data.claims.ClaimsRepository
import com.solventa.app.data.claims.MockClaimsRepository
import com.solventa.app.data.claims.PoliciesResult
import com.solventa.app.data.claims.ReportResult
import com.solventa.app.data.claims.ReportedClaim
import com.solventa.app.data.session.InMemorySessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class MyClaimsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viaje = MockClaimsRepository.POLICIES[0]
    private val dispositivos = MockClaimsRepository.POLICIES[1]

    private fun claim(n: Int, policyId: String = viaje.id, type: String = "GASTO_MEDICO") = ReportedClaim(
        id = "c$n", number = "SIN-2026-0000000$n", status = ClaimStatus.REPORTED, policyId = policyId,
        policyNumber = "SLV-$n", typeCode = type,
        occurredAt = Instant.parse("2026-11-10T19:30:00Z"), reportedAt = Instant.parse("2026-11-1${n}T20:00:00Z"),
    )

    /** Pagina sobre [all] igual que el backend y registra cada consulta. */
    private class FakeClaims(var all: List<ReportedClaim>) : ClaimsRepository {
        val queries = mutableListOf<Triple<String?, Int, Int>>()
        val results = ArrayDeque<ClaimsPageResult>()
        override suspend fun eligiblePolicies(accessToken: String, correlationId: String) = PoliciesResult.Loaded(MockClaimsRepository.POLICIES)
        override suspend fun report(accessToken: String, draft: ClaimDraft, idempotencyKey: String, correlationId: String) = ReportResult.ServiceUnavailable
        override suspend fun claim(accessToken: String, claimId: String, correlationId: String) = ClaimResult.NotFound
        override suspend fun claims(accessToken: String, policyId: String?, page: Int, pageSize: Int, correlationId: String): ClaimsPageResult {
            queries += Triple(policyId, page, pageSize)
            results.removeFirstOrNull()?.let { return it }
            val filtered = all.filter { policyId == null || it.policyId == policyId }
            return ClaimsPageResult.Loaded(ClaimsPage(filtered.drop((page - 1) * pageSize).take(pageSize), page, pageSize, filtered.size))
        }
    }

    private val sessionStore = InMemorySessionStore().apply { save(testSession()) }

    @Test
    fun `carga la primera pagina con las polizas para el filtro y los nombres`() {
        val repo = FakeClaims(listOf(claim(1), claim(2, dispositivos.id, "ROBO")))

        val state = MyClaimsViewModel(repo, sessionStore).uiState.value

        assertFalse(state.loading)
        assertEquals(listOf("c1", "c2"), state.claims.map { it.id })
        assertEquals(2, state.total)
        assertEquals(MockClaimsRepository.POLICIES.size, state.policies.size)
        assertEquals("Gasto médico", state.typeName(state.claims[0]))
        assertEquals(dispositivos.productName, state.policyOf(state.claims[1])?.productName)
        assertEquals(listOf(Triple(null, 1, 20)), repo.queries)
    }

    @Test
    fun `al volver a la pantalla se recarga con el filtro y muestra el estado nuevo`() {
        val repo = FakeClaims(listOf(claim(1), claim(2, dispositivos.id, "ROBO")))
        val viewModel = MyClaimsViewModel(repo, sessionStore)
        viewModel.selectPolicy(viaje.id)

        repo.all = listOf(claim(1).copy(status = ClaimStatus.IN_REVIEW), claim(2, dispositivos.id, "ROBO"))
        viewModel.refresh()

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals(ClaimStatus.IN_REVIEW, state.claims.single().status)
        assertEquals(Triple(viaje.id, 1, 20), repo.queries.last())
    }

    @Test
    fun `filtrar por poliza vuelve a la primera pagina con poliza_id`() {
        val repo = FakeClaims(listOf(claim(1), claim(2, dispositivos.id, "ROBO")))
        val vm = MyClaimsViewModel(repo, sessionStore)

        vm.selectPolicy(dispositivos.id)

        assertEquals(dispositivos.id, vm.uiState.value.selectedPolicyId)
        assertEquals(listOf("c2"), vm.uiState.value.claims.map { it.id })
        assertEquals(Triple(dispositivos.id, 1, 20), repo.queries.last())
        vm.selectPolicy(null)
        assertEquals(2, vm.uiState.value.claims.size)
    }

    @Test
    fun `ver mas agrega la pagina siguiente sin duplicados`() {
        val repo = FakeClaims((1..5).map { claim(it) })
        val vm = MyClaimsViewModel(repo, sessionStore, pageSize = 2)
        assertTrue(vm.uiState.value.hasMore)

        vm.loadMore()
        vm.loadMore()
        vm.loadMore() // ya no hay más: no consulta

        assertEquals((1..5).map { "c$it" }, vm.uiState.value.claims.map { it.id })
        assertFalse(vm.uiState.value.hasMore)
        assertEquals(listOf(1, 2, 3), repo.queries.map { it.second })
    }

    @Test
    fun `sin siniestros muestra el estado vacio`() {
        val state = MyClaimsViewModel(FakeClaims(emptyList()), sessionStore).uiState.value

        assertTrue(state.isEmpty)
    }

    @Test
    fun `errores y reintento`() {
        val repo = FakeClaims(listOf(claim(1))).apply { results += ClaimsPageResult.NetworkError }
        val vm = MyClaimsViewModel(repo, sessionStore)
        assertEquals(MyClaimsError.NETWORK, vm.uiState.value.error)
        assertFalse(vm.uiState.value.isEmpty)

        vm.load()

        assertNull(vm.uiState.value.error)
        assertEquals(1, vm.uiState.value.claims.size)
    }

    @Test
    fun `error al cargar mas conserva lo que ya se ve`() {
        val repo = FakeClaims((1..3).map { claim(it) })
        val vm = MyClaimsViewModel(repo, sessionStore, pageSize = 2)
        repo.results += ClaimsPageResult.ServiceUnavailable

        vm.loadMore()

        assertEquals(2, vm.uiState.value.claims.size)
        assertEquals(MyClaimsError.SERVICE, vm.uiState.value.error)
    }

    @Test
    fun `sesion vencida o sin sesion va al login`() {
        val expired = FakeClaims(emptyList()).apply { results += ClaimsPageResult.SessionExpired }
        assertTrue(MyClaimsViewModel(expired, sessionStore).uiState.value.goToLogin)

        val noSession = MyClaimsViewModel(FakeClaims(emptyList()), InMemorySessionStore())
        assertTrue(noSession.uiState.value.goToLogin)
        noSession.consumeLogin()
        assertFalse(noSession.uiState.value.goToLogin)
    }
}
