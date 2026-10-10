package com.solventa.app.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.solventa.app.data.auth.AuthRepository
import com.solventa.app.data.auth.LoginResult
import com.solventa.app.data.auth.Session
import com.solventa.app.data.session.SessionStore
import com.solventa.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LoginFieldError { EMAIL_INVALID, PASSWORD_EMPTY }

enum class LoginFormError { INVALID_CREDENTIALS, NETWORK }

/** Destino al que debe navegar la UI tras un intento de inicio de sesión. */
enum class LoginDestination { HOME, BIOMETRIC_OFFER, ACCOUNT_NOT_FOUND }

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val loading: Boolean = false,
    val emailError: LoginFieldError? = null,
    val passwordError: LoginFieldError? = null,
    val formError: LoginFormError? = null,
    val destination: LoginDestination? = null,
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val sessionStore: SessionStore,
    private val shouldOfferBiometric: (Session) -> Boolean = { false },
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, emailError = null, formError = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, formError = null) }
    }

    fun onTogglePasswordVisibility() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun onSubmit() {
        val state = _uiState.value
        if (state.loading) return
        val emailError = if (EMAIL_REGEX.matches(state.email.trim())) null else LoginFieldError.EMAIL_INVALID
        val passwordError = if (state.password.isEmpty()) LoginFieldError.PASSWORD_EMPTY else null
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        _uiState.update { it.copy(loading = true, formError = null) }
        viewModelScope.launch {
            when (val result = authRepository.login(state.email.trim(), state.password)) {
                is LoginResult.Success -> {
                    sessionStore.save(result.session)
                    val destination = if (shouldOfferBiometric(result.session)) {
                        LoginDestination.BIOMETRIC_OFFER
                    } else {
                        LoginDestination.HOME
                    }
                    _uiState.update { it.copy(loading = false, password = "", destination = destination) }
                }
                LoginResult.AccountNotFound ->
                    _uiState.update { it.copy(loading = false, destination = LoginDestination.ACCOUNT_NOT_FOUND) }
                LoginResult.InvalidCredentials ->
                    _uiState.update { it.copy(loading = false, formError = LoginFormError.INVALID_CREDENTIALS) }
                LoginResult.NetworkError ->
                    _uiState.update { it.copy(loading = false, formError = LoginFormError.NETWORK) }
            }
        }
    }

    fun onDestinationHandled() {
        _uiState.update { it.copy(destination = null) }
    }

    companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LoginViewModel(container.authRepository, container.sessionStore, container::shouldOfferBiometric)
            }
        }
    }
}
