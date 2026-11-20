package com.solventa.app.feature.claims.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.solventa.app.data.claims.ClaimsPageResult
import com.solventa.app.data.claims.ClaimsRepository
import com.solventa.app.data.claims.EligiblePolicy
import com.solventa.app.data.claims.PoliciesResult
import com.solventa.app.data.claims.ReportedClaim
import com.solventa.app.data.session.SessionStore
import com.solventa.app.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class MyClaimsError { NETWORK, SERVICE }

data class MyClaimsUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    /** Pólizas del cliente para el filtro (también dan el nombre del producto y de los tipos). */
    val policies: List<EligiblePolicy> = emptyList(),
    /** Mientras responde MS Pólizas los tipos se muestran como "…" (no con su código). */
    val policiesLoading: Boolean = true,
    /** `null` = todas las pólizas. */
    val selectedPolicyId: String? = null,
    val claims: List<ReportedClaim> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val error: MyClaimsError? = null,
    val goToLogin: Boolean = false,
) {
    val isEmpty: Boolean get() = !loading && error == null && claims.isEmpty()

    fun policyOf(claim: ReportedClaim): EligiblePolicy? = policies.firstOrNull { it.id == claim.policyId }

    fun typeName(claim: ReportedClaim): String? =
        policyOf(claim)?.claimTypes?.firstOrNull { it.code == claim.typeCode }?.name
}

/** HU4.2.1 · "Mis siniestros": los siniestros del cliente, más reciente primero, con filtro por póliza. */
class MyClaimsViewModel(
    private val repository: ClaimsRepository,
    private val sessionStore: SessionStore,
    private val pageSize: Int = PAGE_SIZE,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MyClaimsUiState())
    val uiState: StateFlow<MyClaimsUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    /** Primera página (al entrar, al cambiar el filtro, al reintentar o al volver del detalle). */
    fun load() {
        val token = accessToken() ?: return goToLogin()
        loadJob?.cancel()
        _uiState.update { it.copy(loading = true, error = null) }
        // Las pólizas solo dan el filtro y los nombres: se piden aparte y no retrasan ni bloquean la lista.
        if (_uiState.value.policies.isEmpty()) loadPolicies(token)
        loadJob = viewModelScope.launch {
            when (val result = repository.claims(token, _uiState.value.selectedPolicyId, 1, pageSize, correlationId())) {
                is ClaimsPageResult.Loaded -> _uiState.update {
                    it.copy(
                        loading = false,
                        claims = result.page.claims,
                        total = result.page.total,
                        page = 1,
                        hasMore = result.page.hasMore,
                    )
                }
                ClaimsPageResult.SessionExpired -> goToLogin()
                ClaimsPageResult.NetworkError -> fail(MyClaimsError.NETWORK)
                ClaimsPageResult.ServiceUnavailable -> fail(MyClaimsError.SERVICE)
            }
        }
    }

    private fun loadPolicies(token: String) {
        viewModelScope.launch {
            val policies = (repository.eligiblePolicies(token, correlationId()) as? PoliciesResult.Loaded)?.policies
            _uiState.update { it.copy(policiesLoading = false, policies = policies ?: it.policies) }
        }
    }

    fun selectPolicy(policyId: String?) {
        if (policyId == _uiState.value.selectedPolicyId) return
        _uiState.update { it.copy(selectedPolicyId = policyId, claims = emptyList(), total = 0, hasMore = false) }
        load()
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore || !state.hasMore) return
        val token = accessToken() ?: return goToLogin()
        _uiState.update { it.copy(loadingMore = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.claims(token, state.selectedPolicyId, state.page + 1, pageSize, correlationId())) {
                is ClaimsPageResult.Loaded -> _uiState.update { current ->
                    val known = current.claims.map { it.id }.toSet()
                    current.copy(
                        loadingMore = false,
                        claims = current.claims + result.page.claims.filterNot { it.id in known },
                        total = result.page.total,
                        page = result.page.page,
                        hasMore = result.page.hasMore,
                    )
                }
                ClaimsPageResult.SessionExpired -> goToLogin()
                ClaimsPageResult.NetworkError -> _uiState.update { it.copy(loadingMore = false, error = MyClaimsError.NETWORK) }
                ClaimsPageResult.ServiceUnavailable -> _uiState.update { it.copy(loadingMore = false, error = MyClaimsError.SERVICE) }
            }
        }
    }

    fun consumeLogin() = _uiState.update { it.copy(goToLogin = false) }

    private fun fail(error: MyClaimsError) = _uiState.update { it.copy(loading = false, error = error) }

    private fun goToLogin() = _uiState.update { it.copy(loading = false, loadingMore = false, goToLogin = true) }

    private fun accessToken(): String? = sessionStore.session.value?.accessToken?.takeIf { it.isNotBlank() }

    private fun correlationId() = UUID.randomUUID().toString()

    companion object {
        const val PAGE_SIZE = 20

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { MyClaimsViewModel(container.claimsRepository, container.sessionStore) }
        }
    }
}
