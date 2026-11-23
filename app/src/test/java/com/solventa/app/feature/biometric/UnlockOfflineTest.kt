package com.solventa.app.feature.biometric

import com.solventa.app.MainDispatcherRule
import com.solventa.app.core.biometric.AppLockManager
import com.solventa.app.core.biometric.BiometricLockoutPolicy
import com.solventa.app.core.biometric.BiometricPromptEvent
import com.solventa.app.core.biometric.FakeBiometricVault
import com.solventa.app.core.biometric.FakeClock
import com.solventa.app.core.biometric.InMemoryBiometricSettings
import com.solventa.app.core.biometric.testSession
import com.solventa.app.data.auth.AuthRepository
import com.solventa.app.data.auth.LoginResult
import com.solventa.app.data.auth.Session
import com.solventa.app.data.auth.SessionRefreshResult
import com.solventa.app.data.session.InMemorySessionStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import javax.crypto.Cipher

/** HU3.2.1: sin conexión la biometría confirma la identidad y se entra con la sesión guardada. */
@OptIn(ExperimentalCoroutinesApi::class)
class UnlockOfflineTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val session = testSession()
    private val vault = FakeBiometricVault()
    private val settings = InMemoryBiometricSettings()
    private val clock = FakeClock()
    private val sessionStore = InMemorySessionStore()
    private val lockManager = AppLockManager(vault, sessionStore, clock)
    private var refreshResult: SessionRefreshResult = SessionRefreshResult.NetworkError
    private val authRepository = object : AuthRepository {
        override suspend fun login(email: String, password: String, installationId: String?) = LoginResult.InvalidCredentials
        override suspend fun refresh(session: Session) = refreshResult
    }
    private val viewModel = UnlockViewModel(vault, settings, BiometricLockoutPolicy(settings, clock), sessionStore, lockManager, authRepository)
    private val cipher: Cipher = Cipher.getInstance("AES/GCM/NoPadding")

    private fun authenticate() {
        vault.seal(cipher, session)
        lockManager.onColdStart()
        viewModel.onShown()
        viewModel.onPromptEvent(BiometricPromptEvent.Succeeded(cipher))
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `sin conexion entra con la sesion guardada`() {
        refreshResult = SessionRefreshResult.NetworkError

        authenticate()

        assertEquals(UnlockExit.UNLOCKED, viewModel.uiState.value.exit)
        assertEquals(session, sessionStore.session.value)
    }

    @Test
    fun `si el servicio falla con conexion sigue sin entrar`() {
        refreshResult = SessionRefreshResult.ServiceUnavailable

        authenticate()

        assertEquals(UnlockStatus.RefreshUnavailable, viewModel.uiState.value.status)
        assertNull(viewModel.uiState.value.exit)
        assertNull(sessionStore.session.value)
    }

    @Test
    fun `si el refresh token vencio exige contrasena aunque se haya autenticado`() {
        refreshResult = SessionRefreshResult.ReauthenticationRequired

        authenticate()

        assertEquals(UnlockExit.USE_PASSWORD, viewModel.uiState.value.exit)
        assertNull(sessionStore.session.value)
    }
}
