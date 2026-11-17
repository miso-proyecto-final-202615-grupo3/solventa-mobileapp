package com.solventa.app.feature.claims.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.solventa.app.data.claims.ClaimResult
import com.solventa.app.data.claims.ClaimStatus
import com.solventa.app.data.claims.ClaimsRepository
import com.solventa.app.data.claims.EligiblePolicy
import com.solventa.app.data.claims.PoliciesResult
import com.solventa.app.data.claims.ReportedClaim
import com.solventa.app.data.claims.evidence.DownloadResult
import com.solventa.app.data.claims.evidence.EvidenceRepository
import com.solventa.app.data.claims.evidence.RemoteEvidence
import com.solventa.app.data.session.SessionStore
import com.solventa.app.di.AppContainer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.util.UUID

enum class ClaimDetailMessage { LOAD_NETWORK, LOAD_SERVICE, NOT_FOUND, EVIDENCE_NOT_AVAILABLE, EVIDENCE_FAILED }

/** URL temporal de lectura de una evidencia (vence en minutos; solo vive en memoria). */
data class EvidenceUrl(val url: String, val contentType: String, val expiresAt: Instant)

data class ClaimDetailUiState(
    val loading: Boolean = true,
    val claim: ReportedClaim? = null,
    val policy: EligiblePolicy? = null,
    /** Mientras responde MS Pólizas el tipo y el producto se muestran como "…" (no con el código del tipo). */
    val policyLoading: Boolean = true,
    val message: ClaimDetailMessage? = null,
    /** URLs temporales por evidencia: alimentan las miniaturas y el visor. */
    val urls: Map<String, EvidenceUrl> = emptyMap(),
    /** Evidencia abierta en el visor de la app. */
    val viewing: RemoteEvidence? = null,
    /** Evidencia cuya URL se está renovando para el visor. */
    val openingEvidenceId: String? = null,
    val goToLogin: Boolean = false,
) {
    val typeName: String? get() = claim?.let { c -> policy?.claimTypes?.firstOrNull { it.code == c.typeCode }?.name }

    /** Solo un siniestro recién reportado admite más evidencias (mismo criterio que el backend). */
    val canAddEvidence: Boolean get() = claim?.status == ClaimStatus.REPORTED

    /** Solo se pueden ver las evidencias ya verificadas por el backend. */
    fun canOpen(evidence: RemoteEvidence) = evidence.state == UPLOADED

    val viewingUrl: EvidenceUrl? get() = viewing?.let { urls[it.id] }

    companion object {
        const val UPLOADED = "CARGADA"
    }
}

/**
 * HU4.2.1 · Detalle del siniestro: estado, póliza, ubicación y evidencias con miniatura y visor dentro de la app.
 * Las URL de S3 vencen en 5 minutos: se piden al cargar y se renuevan al abrir el visor si ya vencieron.
 */
class ClaimDetailViewModel(
    private val claimId: String,
    private val claimsRepository: ClaimsRepository,
    private val evidenceRepository: EvidenceRepository,
    private val sessionStore: SessionStore,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(ClaimDetailUiState())
    val uiState: StateFlow<ClaimDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        val token = accessToken() ?: return goToLogin()
        _uiState.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            when (val result = claimsRepository.claim(token, claimId, correlationId())) {
                is ClaimResult.Found -> {
                    // El siniestro se muestra de inmediato; el nombre del producto y del tipo llegan después.
                    _uiState.update { it.copy(loading = false, claim = result.claim) }
                    if (_uiState.value.policy == null) loadPolicy(token, result.claim.policyId)
                    loadThumbnails(token, result.claim)
                }
                ClaimResult.SessionExpired -> goToLogin()
                ClaimResult.NotFound -> _uiState.update { it.copy(loading = false, message = ClaimDetailMessage.NOT_FOUND) }
                ClaimResult.NetworkError -> _uiState.update { it.copy(loading = false, message = ClaimDetailMessage.LOAD_NETWORK) }
                ClaimResult.ServiceUnavailable -> _uiState.update { it.copy(loading = false, message = ClaimDetailMessage.LOAD_SERVICE) }
            }
        }
    }

    private fun loadPolicy(token: String, policyId: String) {
        viewModelScope.launch {
            val policies = (claimsRepository.eligiblePolicies(token, correlationId()) as? PoliciesResult.Loaded)?.policies
            _uiState.update { it.copy(policyLoading = false, policy = policies?.firstOrNull { p -> p.id == policyId } ?: it.policy) }
        }
    }

    /** Una URL por evidencia cargada, en paralelo. Si alguna falla, su miniatura queda con el ícono. */
    private suspend fun loadThumbnails(token: String, claim: ReportedClaim) {
        val urls = claim.evidences.filter { it.state == ClaimDetailUiState.UPLOADED }
            .map { evidence -> viewModelScope.async { evidence.id to evidenceRepository.download(token, claimId, evidence.id, correlationId()) } }
            .awaitAll()
        if (urls.any { it.second == DownloadResult.SessionExpired }) return goToLogin()
        val ready = urls.mapNotNull { (id, result) -> (result as? DownloadResult.Ready)?.let { id to it.toUrl() } }.toMap()
        _uiState.update { it.copy(urls = it.urls + ready) }
    }

    /** Abre el visor de la app; si la URL ya venció (o nunca llegó), pide una nueva. */
    fun openEvidence(evidence: RemoteEvidence) {
        val state = _uiState.value
        if (state.openingEvidenceId != null) return
        if (!state.canOpen(evidence)) return _uiState.update { it.copy(message = ClaimDetailMessage.EVIDENCE_NOT_AVAILABLE) }
        val current = state.urls[evidence.id]
        if (current != null && current.expiresAt.isAfter(Instant.now(clock).plusSeconds(MARGIN_SECONDS))) {
            return _uiState.update { it.copy(viewing = evidence, message = null) }
        }
        val token = accessToken() ?: return goToLogin()
        _uiState.update { it.copy(openingEvidenceId = evidence.id, message = null) }
        viewModelScope.launch {
            val result = evidenceRepository.download(token, claimId, evidence.id, correlationId())
            _uiState.update { it.copy(openingEvidenceId = null) }
            when (result) {
                is DownloadResult.Ready -> _uiState.update { it.copy(urls = it.urls + (evidence.id to result.toUrl()), viewing = evidence) }
                DownloadResult.SessionExpired -> goToLogin()
                DownloadResult.NotAvailable, DownloadResult.NotFound ->
                    _uiState.update { it.copy(message = ClaimDetailMessage.EVIDENCE_NOT_AVAILABLE) }
                DownloadResult.NetworkError, DownloadResult.ServiceUnavailable ->
                    _uiState.update { it.copy(message = ClaimDetailMessage.EVIDENCE_FAILED) }
            }
        }
    }

    fun closeViewer() = _uiState.update { it.copy(viewing = null) }

    /** El visor no pudo mostrar el archivo (p. ej. se cortó la conexión). */
    fun viewerFailed() = _uiState.update { it.copy(viewing = null, message = ClaimDetailMessage.EVIDENCE_FAILED) }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    fun consumeLogin() = _uiState.update { it.copy(goToLogin = false) }

    private fun DownloadResult.Ready.toUrl() = EvidenceUrl(url, contentType, expiresAt)

    private fun goToLogin() = _uiState.update { it.copy(loading = false, viewing = null, goToLogin = true) }

    private fun accessToken(): String? = sessionStore.session.value?.accessToken?.takeIf { it.isNotBlank() }

    private fun correlationId() = UUID.randomUUID().toString()

    companion object {
        /** Margen para no abrir una URL a punto de vencer. */
        private const val MARGIN_SECONDS = 30L

        fun factory(container: AppContainer, claimId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ClaimDetailViewModel(claimId, container.claimsRepository, container.evidenceRepository, container.sessionStore)
            }
        }
    }
}
