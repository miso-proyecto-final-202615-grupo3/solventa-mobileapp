package com.solventa.app.feature.claims.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.solventa.app.data.claims.ClaimDraft
import com.solventa.app.data.claims.ClaimsRepository
import com.solventa.app.data.claims.EligiblePolicy
import com.solventa.app.data.claims.PoliciesResult
import com.solventa.app.data.claims.ReportResult
import com.solventa.app.data.claims.ReportedClaim
import com.solventa.app.data.session.SessionStore
import com.solventa.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

enum class ReportStep { POLICY, DETAILS, REVIEW, SUBMITTING, DONE }

enum class ReportError {
    LOAD_FAILED,
    NETWORK,
    SERVICE_UNAVAILABLE,
    CLIENT_NOT_REGISTERED,
    POLICY_NOT_FOUND,
    POLICY_NOT_ACTIVE,
    OUT_OF_COVERAGE,
    INVALID_DATE,
    TYPE_NOT_ALLOWED,
    INVALID_DATA,
}

sealed interface ReportDestination {
    data class Evidence(val claimId: String) : ReportDestination
    data object Home : ReportDestination
    data object Login : ReportDestination
}

data class ClaimReportUiState(
    val step: ReportStep = ReportStep.POLICY,
    val loadingPolicies: Boolean = true,
    val policies: List<EligiblePolicy> = emptyList(),
    val selectedPolicyId: String? = null,
    val selectedTypeCode: String? = null,
    val description: String = "",
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val fieldErrors: Set<ClaimFieldError> = emptySet(),
    val error: ReportError? = null,
    val reported: ReportedClaim? = null,
    val destination: ReportDestination? = null,
) {
    val selectedPolicy: EligiblePolicy? get() = policies.firstOrNull { it.id == selectedPolicyId }
}

/**
 * Reporte de un siniestro en tres pasos: póliza → qué pasó → revisar y enviar (HU4.1.1).
 *
 * La clave de idempotencia se genera al llegar a "Revisar" y se reutiliza en cada reintento del mismo
 * borrador; si el usuario cambia algo, se genera otra. El formulario y la clave sobreviven a que
 * Android cierre el proceso ([SavedStateHandle]).
 */
class ClaimReportViewModel(
    private val repository: ClaimsRepository,
    private val sessionStore: SessionStore,
    private val validator: ClaimFormValidator,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
    private val newKey: () -> String = { UUID.randomUUID().toString() },
    private val newCorrelationId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(restoredState())
    val uiState: StateFlow<ClaimReportUiState> = _uiState.asStateFlow()

    init {
        loadPolicies()
    }

    fun loadPolicies() {
        val token = accessToken() ?: return goToLogin()
        _uiState.update { it.copy(loadingPolicies = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.eligiblePolicies(token, newCorrelationId())) {
                is PoliciesResult.Loaded -> _uiState.update { state ->
                    val policies = result.policies
                    val selected = state.selectedPolicyId?.takeIf { id -> policies.any { it.id == id } }
                        ?: policies.singleOrNull()?.id
                    state.copy(
                        loadingPolicies = false,
                        policies = policies,
                        selectedPolicyId = selected,
                        // Si la póliza guardada ya no está, se vuelve a elegir.
                        step = if (selected == null && state.step != ReportStep.DONE) ReportStep.POLICY else state.step,
                    )
                }.also { persist() }
                PoliciesResult.SessionExpired -> goToLogin()
                PoliciesResult.ClientNotRegistered -> loadFailed(ReportError.CLIENT_NOT_REGISTERED)
                PoliciesResult.NetworkError -> loadFailed(ReportError.NETWORK)
                PoliciesResult.ServiceUnavailable -> loadFailed(ReportError.LOAD_FAILED)
            }
        }
    }

    fun selectPolicy(policyId: String) {
        _uiState.update { state ->
            val policy = state.policies.firstOrNull { it.id == policyId } ?: return@update state
            val keepType = state.selectedTypeCode?.takeIf { code -> policy.claimTypes.any { it.code == code } }
            state.copy(selectedPolicyId = policyId, selectedTypeCode = keepType, fieldErrors = emptySet())
        }
        persist()
    }

    fun selectType(code: String) {
        _uiState.update { it.copy(selectedTypeCode = code, fieldErrors = it.fieldErrors - ClaimFieldError.TYPE_REQUIRED) }
        persist()
    }

    fun onDescriptionChange(text: String) {
        _uiState.update {
            it.copy(
                description = text.take(ClaimFormValidator.MAX_DESCRIPTION),
                fieldErrors = it.fieldErrors - ClaimFieldError.DESCRIPTION_TOO_SHORT - ClaimFieldError.DESCRIPTION_TOO_LONG,
            )
        }
        persist()
    }

    fun onDateSelected(date: LocalDate) {
        _uiState.update { it.copy(date = date, fieldErrors = it.fieldErrors - DATE_ERRORS) }
        persist()
    }

    fun onTimeSelected(time: LocalTime) {
        _uiState.update { it.copy(time = time, fieldErrors = it.fieldErrors - DATE_ERRORS) }
        persist()
    }

    fun selectableDates(): ClosedRange<LocalDate>? = _uiState.value.selectedPolicy?.let(validator::selectableDates)

    fun continueFromPolicy() {
        val state = _uiState.value
        val errors = validator.validatePolicy(state.selectedPolicyId, state.policies)
        _uiState.update { it.copy(fieldErrors = errors, step = if (errors.isEmpty()) ReportStep.DETAILS else it.step) }
        persist()
    }

    fun continueFromDetails() {
        val state = _uiState.value
        val policy = state.selectedPolicy ?: return continueFromPolicy()
        val errors = validator.validateDetails(policy, state.selectedTypeCode, state.description, state.date, state.time)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = errors) }
            return
        }
        _uiState.update { it.copy(fieldErrors = emptySet(), error = null, step = ReportStep.REVIEW) }
        ensureIdempotencyKey()
        persist()
    }

    fun back(): Boolean {
        val previous = when (_uiState.value.step) {
            ReportStep.DETAILS -> ReportStep.POLICY
            ReportStep.REVIEW -> ReportStep.DETAILS
            else -> null
        } ?: return false
        _uiState.update { it.copy(step = previous, error = null, fieldErrors = emptySet()) }
        persist()
        return true
    }

    fun editPolicy() = jumpTo(ReportStep.POLICY)

    fun editDetails() = jumpTo(ReportStep.DETAILS)

    fun submit() {
        val state = _uiState.value
        if (state.step != ReportStep.REVIEW) return // un solo envío a la vez
        val draft = draftOf(state) ?: return jumpTo(ReportStep.DETAILS)
        val token = accessToken() ?: return goToLogin()
        val key = ensureIdempotencyKey()
        _uiState.update { it.copy(step = ReportStep.SUBMITTING, error = null) }
        viewModelScope.launch {
            val result = repository.report(token, draft, key, newCorrelationId())
            if (result is ReportResult.Reported) {
                _uiState.update { it.copy(step = ReportStep.DONE, reported = result.claim) }
                clearDraft()
                return@launch
            }
            if (result == ReportResult.SessionExpired) return@launch goToLogin()
            if (result == ReportResult.IdempotencyConflict) clearKey() // no debería pasar: el próximo intento usa otra clave
            _uiState.update { it.copy(step = ReportStep.REVIEW, error = errorOf(result)) }
        }
    }

    /** Reintenta lo último que falló. El envío reutiliza la misma clave: no duplica el siniestro. */
    fun retry() {
        if (_uiState.value.step == ReportStep.REVIEW) submit() else loadPolicies()
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    fun goToEvidence() {
        val claim = _uiState.value.reported ?: return
        _uiState.update { it.copy(destination = ReportDestination.Evidence(claim.id)) }
    }

    fun goHome() = _uiState.update { it.copy(destination = ReportDestination.Home) }

    fun consumeDestination() = _uiState.update { it.copy(destination = null) }

    private fun jumpTo(step: ReportStep) {
        if (_uiState.value.step != ReportStep.REVIEW) return
        _uiState.update { it.copy(step = step, error = null) }
        persist()
    }

    private fun draftOf(state: ClaimReportUiState): ClaimDraft? {
        val policyId = state.selectedPolicyId ?: return null
        val type = state.selectedTypeCode ?: return null
        val date = state.date ?: return null
        val time = state.time ?: return null
        return ClaimDraft(
            policyId = policyId,
            typeCode = type,
            description = state.description.trim(),
            occurredAt = LocalDateTime.of(date, time).atZone(clock.zone).toOffsetDateTime(),
        )
    }

    private fun fingerprint(state: ClaimReportUiState): String =
        listOf(state.selectedPolicyId, state.selectedTypeCode, state.description.trim(), state.date, state.time)
            .joinToString("|")

    /** La misma clave mientras el borrador no cambie; si cambió, una nueva. */
    private fun ensureIdempotencyKey(): String {
        val print = fingerprint(_uiState.value)
        val saved = savedState.get<String>(KEY_IDEMPOTENCY)
        if (saved != null && savedState.get<String>(KEY_FINGERPRINT) == print) return saved
        return newKey().also {
            savedState[KEY_IDEMPOTENCY] = it
            savedState[KEY_FINGERPRINT] = print
        }
    }

    private fun clearKey() {
        savedState.remove<String>(KEY_IDEMPOTENCY)
        savedState.remove<String>(KEY_FINGERPRINT)
    }

    private fun clearDraft() {
        clearKey()
        listOf(KEY_STEP, KEY_POLICY, KEY_TYPE, KEY_DESCRIPTION, KEY_DATE, KEY_TIME).forEach { savedState.remove<String>(it) }
    }

    private fun persist() {
        val state = _uiState.value
        if (state.step == ReportStep.DONE) return
        savedState[KEY_STEP] = (if (state.step == ReportStep.SUBMITTING) ReportStep.REVIEW else state.step).name
        savedState[KEY_POLICY] = state.selectedPolicyId
        savedState[KEY_TYPE] = state.selectedTypeCode
        savedState[KEY_DESCRIPTION] = state.description
        savedState[KEY_DATE] = state.date?.toString()
        savedState[KEY_TIME] = state.time?.toString()
    }

    private fun restoredState(): ClaimReportUiState = ClaimReportUiState(
        step = savedState.get<String>(KEY_STEP)?.let { runCatching { ReportStep.valueOf(it) }.getOrNull() } ?: ReportStep.POLICY,
        selectedPolicyId = savedState[KEY_POLICY],
        selectedTypeCode = savedState[KEY_TYPE],
        description = savedState[KEY_DESCRIPTION] ?: "",
        date = savedState.get<String>(KEY_DATE)?.let(LocalDate::parse),
        time = savedState.get<String>(KEY_TIME)?.let(LocalTime::parse),
    )

    private fun loadFailed(error: ReportError) = _uiState.update { it.copy(loadingPolicies = false, error = error) }

    private fun accessToken(): String? = sessionStore.session.value?.accessToken?.takeIf { it.isNotBlank() }

    private fun goToLogin() = _uiState.update { it.copy(loadingPolicies = false, destination = ReportDestination.Login) }

    private fun errorOf(result: ReportResult): ReportError = when (result) {
        ReportResult.NetworkError -> ReportError.NETWORK
        ReportResult.PolicyNotFound -> ReportError.POLICY_NOT_FOUND
        ReportResult.PolicyNotActive -> ReportError.POLICY_NOT_ACTIVE
        ReportResult.OutOfCoverage -> ReportError.OUT_OF_COVERAGE
        ReportResult.InvalidDate -> ReportError.INVALID_DATE
        ReportResult.TypeNotAllowed -> ReportError.TYPE_NOT_ALLOWED
        ReportResult.InvalidData -> ReportError.INVALID_DATA
        ReportResult.ClientNotRegistered -> ReportError.CLIENT_NOT_REGISTERED
        ReportResult.IdempotencyConflict, ReportResult.ServiceUnavailable, ReportResult.SessionExpired,
        is ReportResult.Reported -> ReportError.SERVICE_UNAVAILABLE
    }

    companion object {
        private val DATE_ERRORS = setOf(
            ClaimFieldError.DATE_REQUIRED,
            ClaimFieldError.TIME_REQUIRED,
            ClaimFieldError.DATE_IN_FUTURE,
            ClaimFieldError.DATE_OUT_OF_COVERAGE,
        )
        internal const val KEY_STEP = "claim_report_step"
        internal const val KEY_POLICY = "claim_report_policy"
        internal const val KEY_TYPE = "claim_report_type"
        internal const val KEY_DESCRIPTION = "claim_report_description"
        internal const val KEY_DATE = "claim_report_date"
        internal const val KEY_TIME = "claim_report_time"
        internal const val KEY_IDEMPOTENCY = "claim_report_idempotency_key"
        internal const val KEY_FINGERPRINT = "claim_report_fingerprint"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ClaimReportViewModel(
                    repository = container.claimsRepository,
                    sessionStore = container.sessionStore,
                    validator = ClaimFormValidator(container.clock),
                    clock = container.clock,
                    savedState = createSavedStateHandle(),
                )
            }
        }
    }
}
