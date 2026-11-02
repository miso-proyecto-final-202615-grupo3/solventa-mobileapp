package com.solventa.app.feature.consent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.solventa.app.core.device.InstallationIdProvider
import com.solventa.app.data.consent.BiometricConsentRepository
import com.solventa.app.data.consent.ConsentDecision
import com.solventa.app.data.consent.ConsentResult
import com.solventa.app.data.consent.ConsentStatusResult
import com.solventa.app.data.session.SessionStore
import com.solventa.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConsentDestination { ACTIVATION, DECLINED, LOGIN }

enum class ConsentError { NETWORK, SERVICE_UNAVAILABLE }

data class BiometricConsentUiState(
    val loading: Boolean = true,
    val submitting: Boolean = false,
    val checked: Boolean = false,
    val termsVersion: String = "",
    val error: ConsentError? = null,
    val destination: ConsentDestination? = null,
)

class BiometricConsentViewModel(
    private val repository: BiometricConsentRepository,
    private val sessionStore: SessionStore,
    private val installationIdProvider: InstallationIdProvider,
    private val defaultTermsVersion: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BiometricConsentUiState(termsVersion = defaultTermsVersion))
    val uiState: StateFlow<BiometricConsentUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        val token = sessionStore.session.value?.accessToken
        if (token.isNullOrBlank()) {
            _uiState.update { it.copy(loading = false, destination = ConsentDestination.LOGIN) }
            return
        }
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.getStatus(token)) {
                is ConsentStatusResult.Loaded -> {
                    val status = result.status
                    // Si ya aceptó la versión vigente, no se vuelve a mostrar el texto.
                    val alreadyAccepted = status.granted && status.acceptedVersion == status.termsVersion
                    _uiState.update {
                        it.copy(
                            loading = false,
                            termsVersion = status.termsVersion,
                            destination = if (alreadyAccepted) ConsentDestination.ACTIVATION else null,
                        )
                    }
                }
                ConsentStatusResult.SessionExpired -> _uiState.update { it.copy(loading = false, destination = ConsentDestination.LOGIN) }
                ConsentStatusResult.NetworkError -> _uiState.update { it.copy(loading = false, error = ConsentError.NETWORK) }
                ConsentStatusResult.ServiceUnavailable -> _uiState.update { it.copy(loading = false, error = ConsentError.SERVICE_UNAVAILABLE) }
            }
        }
    }

    fun onCheckedChange(checked: Boolean) {
        _uiState.update { it.copy(checked = checked) }
    }

    fun onAccept() {
        if (!_uiState.value.checked) return
        record(ConsentDecision.ACCEPTED)
    }

    fun onReject() = record(ConsentDecision.REJECTED)

    private fun record(decision: ConsentDecision) {
        val state = _uiState.value
        if (state.submitting) return
        val token = sessionStore.session.value?.accessToken.orEmpty()
        _uiState.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (repository.record(token, decision, state.termsVersion, installationIdProvider.get())) {
                is ConsentResult.Recorded -> _uiState.update {
                    it.copy(
                        submitting = false,
                        destination = if (decision == ConsentDecision.ACCEPTED) ConsentDestination.ACTIVATION else ConsentDestination.DECLINED,
                    )
                }
                // Cambió el texto legal: se vuelve a pedir el estado y se muestra la versión nueva.
                ConsentResult.OutdatedTerms -> {
                    _uiState.update { it.copy(submitting = false, checked = false) }
                    load()
                }
                ConsentResult.SessionExpired -> _uiState.update { it.copy(submitting = false, destination = ConsentDestination.LOGIN) }
                ConsentResult.NetworkError -> _uiState.update { it.copy(submitting = false, error = ConsentError.NETWORK) }
                ConsentResult.ServiceUnavailable -> _uiState.update { it.copy(submitting = false, error = ConsentError.SERVICE_UNAVAILABLE) }
            }
        }
    }

    /** Salida cuando el error no se resuelve reintentando (p. ej. la cuenta no existe en el backend). */
    fun onBackToLogin() {
        _uiState.update { it.copy(destination = ConsentDestination.LOGIN) }
    }

    fun onDestinationHandled() {
        _uiState.update { it.copy(destination = null) }
    }

    companion object {
        fun factory(container: AppContainer, defaultTermsVersion: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BiometricConsentViewModel(
                    container.biometricConsentRepository,
                    container.sessionStore,
                    container.installationIdProvider,
                    defaultTermsVersion,
                )
            }
        }
    }
}
