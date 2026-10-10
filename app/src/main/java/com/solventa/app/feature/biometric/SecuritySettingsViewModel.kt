package com.solventa.app.feature.biometric

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.solventa.app.core.biometric.BiometricAvailability
import com.solventa.app.core.biometric.BiometricPromptEvent
import com.solventa.app.core.biometric.BiometricVault
import com.solventa.app.data.session.SessionStore
import com.solventa.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.crypto.Cipher

data class SecuritySettingsUiState(
    val biometricEnabled: Boolean,
    val biometricAvailable: Boolean,
    val activationFailed: Boolean = false,
    val promptCipher: Cipher? = null,
)

/** Perfil > Seguridad: activar o desactivar el acceso biométrico en cualquier momento. */
class SecuritySettingsViewModel(
    private val vault: BiometricVault,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(currentState())
    val uiState: StateFlow<SecuritySettingsUiState> = _uiState.asStateFlow()

    fun refresh() {
        _uiState.value = currentState()
    }

    fun onToggle(enabled: Boolean) {
        if (!enabled) {
            vault.disable()
            refresh()
            return
        }
        if (!_uiState.value.biometricAvailable) return
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
                val session = sessionStore.session.value ?: return
                runCatching { vault.seal(event.cipher, session) }
                    .onSuccess { refresh() }
                    .onFailure { _uiState.update { it.copy(activationFailed = true) } }
            }
            BiometricPromptEvent.AttemptFailed, BiometricPromptEvent.Canceled -> Unit
            BiometricPromptEvent.SystemLockout, is BiometricPromptEvent.Error ->
                _uiState.update { it.copy(activationFailed = true) }
        }
    }

    private fun currentState() = SecuritySettingsUiState(
        biometricEnabled = vault.isEnabled(),
        biometricAvailable = vault.availability() == BiometricAvailability.AVAILABLE,
    )

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SecuritySettingsViewModel(container.biometricVault, container.sessionStore) }
        }
    }
}
