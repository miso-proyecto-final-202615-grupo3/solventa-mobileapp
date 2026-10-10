package com.solventa.app.feature.biometric

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.solventa.app.core.biometric.AppLockManager
import com.solventa.app.core.biometric.BiometricKeyInvalidatedException
import com.solventa.app.core.biometric.BiometricLockoutPolicy
import com.solventa.app.core.biometric.BiometricPromptEvent
import com.solventa.app.core.biometric.BiometricSettings
import com.solventa.app.core.biometric.BiometricVault
import com.solventa.app.core.biometric.FailureOutcome
import com.solventa.app.data.session.SessionStore
import com.solventa.app.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher

sealed interface UnlockStatus {
    /** "Confirma que eres tú". */
    data object Ready : UnlockStatus
    data class Failed(val attempt: Int, val maxAttempts: Int) : UnlockStatus
    data class LockedOut(val remainingSeconds: Long) : UnlockStatus
    data object BiometricChanged : UnlockStatus
}

enum class UnlockExit { UNLOCKED, USE_PASSWORD }

data class UnlockUiState(
    val status: UnlockStatus = UnlockStatus.Ready,
    val promptCipher: Cipher? = null,
    val exit: UnlockExit? = null,
)

/** Bloqueo de la app al abrirla o al volver de segundo plano (HU3.1.2). */
class UnlockViewModel(
    private val vault: BiometricVault,
    private val settings: BiometricSettings,
    private val policy: BiometricLockoutPolicy,
    private val sessionStore: SessionStore,
    private val lockManager: AppLockManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    /** Se llama cada vez que aparece la pantalla de bloqueo. */
    fun onShown() {
        countdownJob?.cancel()
        _uiState.value = UnlockUiState()
        requestPrompt()
    }

    fun onRetry() = requestPrompt()

    fun onPromptLaunched() {
        _uiState.update { it.copy(promptCipher = null) }
    }

    fun onPromptEvent(event: BiometricPromptEvent) {
        when (event) {
            is BiometricPromptEvent.Succeeded -> onAuthenticated(event.cipher)
            BiometricPromptEvent.AttemptFailed -> when (val outcome = policy.registerFailure()) {
                is FailureOutcome.Retry ->
                    _uiState.update { it.copy(status = UnlockStatus.Failed(outcome.attempt, outcome.maxAttempts)) }
                FailureOutcome.LockedOut -> startLockoutCountdown()
            }
            BiometricPromptEvent.SystemLockout -> {
                policy.registerSystemLockout()
                startLockoutCountdown()
            }
            // Cancelar no cuenta como intento fallido: se queda en la pantalla con la opción de contraseña.
            BiometricPromptEvent.Canceled, is BiometricPromptEvent.Error -> Unit
        }
    }

    fun onUsePassword() {
        countdownJob?.cancel()
        sessionStore.clear()
        _uiState.update { it.copy(exit = UnlockExit.USE_PASSWORD) }
    }

    /**
     * La UI ya navegó según [UnlockUiState.exit]; recién ahora se retira el bloqueo,
     * para que la pantalla de abajo nunca quede visible antes de tiempo.
     */
    fun onExitHandled() {
        _uiState.update { it.copy(exit = null) }
        lockManager.unlock()
    }

    private fun requestPrompt() {
        if (policy.isLockedOut()) {
            startLockoutCountdown()
            return
        }
        try {
            val cipher = vault.cipherForUnsealing()
            _uiState.update { it.copy(promptCipher = cipher) }
        } catch (_: BiometricKeyInvalidatedException) {
            onBiometricChanged()
        }
    }

    private fun onAuthenticated(cipher: Cipher) {
        val session = runCatching { vault.unseal(cipher) }.getOrNull()
        if (session == null) {
            onUsePassword()
            return
        }
        policy.registerSuccess()
        sessionStore.save(session)
        _uiState.update { it.copy(exit = UnlockExit.UNLOCKED) }
    }

    /** Cambió la biometría del teléfono: se desactiva y se vuelve a ofrecer tras entrar con contraseña. */
    private fun onBiometricChanged() {
        vault.disable()
        settings.offerDeclined = false
        _uiState.update { it.copy(status = UnlockStatus.BiometricChanged) }
    }

    private fun startLockoutCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                val remainingMillis = policy.remainingLockoutMillis()
                if (remainingMillis <= 0) {
                    _uiState.update { it.copy(status = UnlockStatus.Ready) }
                    break
                }
                val seconds = (remainingMillis + 999) / 1000
                _uiState.update { it.copy(status = UnlockStatus.LockedOut(seconds)) }
                delay(1000)
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                UnlockViewModel(
                    vault = container.biometricVault,
                    settings = container.biometricSettings,
                    policy = container.biometricLockoutPolicy,
                    sessionStore = container.sessionStore,
                    lockManager = container.appLockManager,
                )
            }
        }
    }
}
