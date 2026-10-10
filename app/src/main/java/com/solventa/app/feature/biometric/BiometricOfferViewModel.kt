package com.solventa.app.feature.biometric

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.solventa.app.core.biometric.BiometricAvailability
import com.solventa.app.core.biometric.BiometricPromptEvent
import com.solventa.app.core.biometric.BiometricSettings
import com.solventa.app.core.biometric.BiometricVault
import com.solventa.app.data.session.SessionStore
import com.solventa.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.crypto.Cipher

data class BiometricOfferUiState(
    val biometricAvailable: Boolean,
    val activationFailed: Boolean = false,
    /** Cipher pendiente de entregar al aviso biométrico del sistema. */
    val promptCipher: Cipher? = null,
    val finished: Boolean = false,
)

/** Oferta de activar huella o rostro tras vincular el dispositivo (HU3.1.2). */
class BiometricOfferViewModel(
    private val vault: BiometricVault,
    private val settings: BiometricSettings,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BiometricOfferUiState(biometricAvailable = vault.availability() == BiometricAvailability.AVAILABLE),
    )
    val uiState: StateFlow<BiometricOfferUiState> = _uiState.asStateFlow()

    fun onActivate() {
        val cipher = runCatching { vault.cipherForSealing() }.getOrElse {
            _uiState.update { it.copy(activationFailed = true) }
            return
        }
        _uiState.update { it.copy(activationFailed = false, promptCipher = cipher) }
    }

    fun onPromptLaunched() {
        _uiState.update { it.copy(promptCipher = null) }
    }

    fun onPromptEvent(event: BiometricPromptEvent) {
        when (event) {
            is BiometricPromptEvent.Succeeded -> {
                val session = sessionStore.session.value
                if (session == null) {
                    _uiState.update { it.copy(finished = true) }
                    return
                }
                runCatching { vault.seal(event.cipher, session) }
                    .onSuccess { _uiState.update { it.copy(finished = true) } }
                    .onFailure { _uiState.update { it.copy(activationFailed = true) } }
            }
            // Durante la activación el aviso del sistema permite reintentar por sí mismo.
            BiometricPromptEvent.AttemptFailed, BiometricPromptEvent.Canceled -> Unit
            BiometricPromptEvent.SystemLockout, is BiometricPromptEvent.Error ->
                _uiState.update { it.copy(activationFailed = true) }
        }
    }

    /** "Ahora no" o "Usar contraseña": no se vuelve a ofrecer; queda disponible en Perfil > Seguridad. */
    fun onDecline() {
        settings.offerDeclined = true
        _uiState.update { it.copy(finished = true) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BiometricOfferViewModel(container.biometricVault, container.biometricSettings, container.sessionStore)
            }
        }
    }
}
