package com.solventa.app.feature.login

import com.solventa.app.MainDispatcherRule
import com.solventa.app.data.auth.AuthRepository
import com.solventa.app.data.auth.ClienteResumen
import com.solventa.app.data.auth.EstadoKyc
import com.solventa.app.data.auth.LoginResult
import com.solventa.app.data.auth.Session
import com.solventa.app.data.session.InMemorySessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class StubAuthRepository(var result: LoginResult) : AuthRepository {
        var calls = 0
        override suspend fun login(email: String, password: String): LoginResult {
            calls++
            return result
        }
    }

    private val session = Session(
        accessToken = "a",
        refreshToken = "r",
        expiresInSeconds = 3600,
        cliente = ClienteResumen("c-1", "Camila", EstadoKyc.PENDIENTE, dispositivoVinculado = false),
    )

    private val repository = StubAuthRepository(LoginResult.Success(session))
    private val sessionStore = InMemorySessionStore()
    private val viewModel = LoginViewModel(repository, sessionStore)

    private fun fillValidForm() {
        viewModel.onEmailChange("camila.rojas@correo.com")
        viewModel.onPasswordChange("secreta")
    }

    @Test
    fun `correo invalido muestra error y no llama al backend`() {
        viewModel.onEmailChange("camila")
        viewModel.onPasswordChange("secreta")

        viewModel.onSubmit()

        assertEquals(LoginFieldError.EMAIL_INVALID, viewModel.uiState.value.emailError)
        assertEquals(0, repository.calls)
    }

    @Test
    fun `contrasena vacia muestra error`() {
        viewModel.onEmailChange("camila.rojas@correo.com")

        viewModel.onSubmit()

        assertEquals(LoginFieldError.PASSWORD_EMPTY, viewModel.uiState.value.passwordError)
        assertEquals(0, repository.calls)
    }

    @Test
    fun `login exitoso guarda la sesion, limpia la contrasena y navega a inicio`() {
        fillValidForm()

        viewModel.onSubmit()

        val state = viewModel.uiState.value
        assertEquals(LoginDestination.HOME, state.destination)
        assertEquals("", state.password)
        assertFalse(state.loading)
        assertEquals(session, sessionStore.session.value)
    }

    @Test
    fun `login exitoso con oferta biometrica pendiente navega a la oferta`() {
        val viewModel = LoginViewModel(repository, sessionStore, shouldOfferBiometric = { true })
        viewModel.onEmailChange("camila.rojas@correo.com")
        viewModel.onPasswordChange("secreta")

        viewModel.onSubmit()

        assertEquals(LoginDestination.BIOMETRIC_OFFER, viewModel.uiState.value.destination)
    }

    @Test
    fun `cuenta inexistente navega a cuenta no encontrada sin guardar sesion`() {
        repository.result = LoginResult.AccountNotFound
        fillValidForm()

        viewModel.onSubmit()

        assertEquals(LoginDestination.ACCOUNT_NOT_FOUND, viewModel.uiState.value.destination)
        assertNull(sessionStore.session.value)
    }

    @Test
    fun `credenciales invalidas muestran error de formulario`() {
        repository.result = LoginResult.InvalidCredentials
        fillValidForm()

        viewModel.onSubmit()

        assertEquals(LoginFormError.INVALID_CREDENTIALS, viewModel.uiState.value.formError)
        assertNull(viewModel.uiState.value.destination)
    }

    @Test
    fun `falla de red muestra error de conexion`() {
        repository.result = LoginResult.NetworkError
        fillValidForm()

        viewModel.onSubmit()

        assertEquals(LoginFormError.NETWORK, viewModel.uiState.value.formError)
    }

    @Test
    fun `editar un campo limpia el error de formulario`() {
        repository.result = LoginResult.InvalidCredentials
        fillValidForm()
        viewModel.onSubmit()

        viewModel.onPasswordChange("otra")

        assertNull(viewModel.uiState.value.formError)
    }

    @Test
    fun `alternar visibilidad de contrasena`() {
        viewModel.onTogglePasswordVisibility()
        assertTrue(viewModel.uiState.value.passwordVisible)
        viewModel.onTogglePasswordVisibility()
        assertFalse(viewModel.uiState.value.passwordVisible)
    }

    @Test
    fun `destino manejado se limpia`() {
        fillValidForm()
        viewModel.onSubmit()

        viewModel.onDestinationHandled()

        assertNull(viewModel.uiState.value.destination)
    }
}
