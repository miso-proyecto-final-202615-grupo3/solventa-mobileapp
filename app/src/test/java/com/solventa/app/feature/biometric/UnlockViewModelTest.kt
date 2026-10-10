package com.solventa.app.feature.biometric

import com.solventa.app.MainDispatcherRule
import com.solventa.app.core.biometric.AppLockManager
import com.solventa.app.core.biometric.BiometricLockoutPolicy
import com.solventa.app.core.biometric.BiometricPromptEvent
import com.solventa.app.core.biometric.FakeBiometricVault
import com.solventa.app.core.biometric.FakeClock
import com.solventa.app.core.biometric.InMemoryBiometricSettings
import com.solventa.app.core.biometric.testSession
import com.solventa.app.data.session.InMemorySessionStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.crypto.Cipher

@OptIn(ExperimentalCoroutinesApi::class)
class UnlockViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val session = testSession()
    private val vault = FakeBiometricVault()
    private val settings = InMemoryBiometricSettings()
    private val clock = FakeClock()
    private val policy = BiometricLockoutPolicy(settings, clock)
    private val sessionStore = InMemorySessionStore()
    private val lockManager = AppLockManager(vault, sessionStore, clock)
    private val viewModel = UnlockViewModel(vault, settings, policy, sessionStore, lockManager)

    private val cipher: Cipher = Cipher.getInstance("AES/GCM/NoPadding")

    @Before
    fun setUp() {
        vault.seal(cipher, session)
        lockManager.onColdStart()
    }

    private fun failAttempts(count: Int) = repeat(count) {
        viewModel.onPromptEvent(BiometricPromptEvent.AttemptFailed)
    }

    @Test
    fun `al mostrarse pide la biometria automaticamente`() {
        viewModel.onShown()

        assertEquals(UnlockStatus.Ready, viewModel.uiState.value.status)
        assertNotNull(viewModel.uiState.value.promptCipher)
    }

    @Test
    fun `autenticacion exitosa recupera la sesion y desbloquea al manejar la salida`() {
        viewModel.onShown()

        viewModel.onPromptEvent(BiometricPromptEvent.Succeeded(cipher))

        assertEquals(UnlockExit.UNLOCKED, viewModel.uiState.value.exit)
        assertEquals(session, sessionStore.session.value)
        assertTrue("sigue bloqueada hasta que la UI maneje la salida", lockManager.locked.value)
        viewModel.onExitHandled()
        assertFalse(lockManager.locked.value)
    }

    @Test
    fun `un fallo muestra el intento 1 de 3`() {
        viewModel.onShown()

        failAttempts(1)

        assertEquals(UnlockStatus.Failed(1, 3), viewModel.uiState.value.status)
    }

    @Test
    fun `tres fallos bloquean la biometria 5 minutos`() {
        viewModel.onShown()

        failAttempts(3)

        assertEquals(UnlockStatus.LockedOut(300), viewModel.uiState.value.status)
        assertTrue(policy.isLockedOut())
    }

    @Test
    fun `cancelar el aviso no cuenta como intento fallido`() {
        viewModel.onShown()

        viewModel.onPromptEvent(BiometricPromptEvent.Canceled)
        failAttempts(1)

        assertEquals(UnlockStatus.Failed(1, 3), viewModel.uiState.value.status)
    }

    @Test
    fun `un acceso exitoso reinicia el contador de fallos`() {
        viewModel.onShown()
        failAttempts(2)

        viewModel.onPromptEvent(BiometricPromptEvent.Succeeded(cipher))

        assertEquals(0, settings.failedAttempts)
    }

    @Test
    fun `si esta bloqueada al abrir no pide biometria y muestra la cuenta regresiva`() {
        repeat(3) { policy.registerFailure() }
        clock.now += 60_000

        viewModel.onShown()

        assertNull(viewModel.uiState.value.promptCipher)
        assertEquals(UnlockStatus.LockedOut(240), viewModel.uiState.value.status)
    }

    @Test
    fun `al terminar el bloqueo vuelve a estar listo`() {
        viewModel.onShown()
        failAttempts(3)

        clock.now += BiometricLockoutPolicy.LOCKOUT_MILLIS
        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(1_001)

        assertEquals(UnlockStatus.Ready, viewModel.uiState.value.status)
    }

    @Test
    fun `bloqueo del sistema operativo muestra la cuenta regresiva`() {
        viewModel.onShown()

        viewModel.onPromptEvent(BiometricPromptEvent.SystemLockout)

        assertEquals(UnlockStatus.LockedOut(300), viewModel.uiState.value.status)
    }

    @Test
    fun `cambio de biometria desactiva el acceso y lo vuelve a ofrecer`() {
        settings.offerDeclined = true
        vault.keyInvalidated = true

        viewModel.onShown()

        assertEquals(UnlockStatus.BiometricChanged, viewModel.uiState.value.status)
        assertEquals(1, vault.disableCalls)
        assertFalse(settings.offerDeclined)
    }

    @Test
    fun `usar contrasena cierra la sesion y sale al login`() {
        sessionStore.save(session)
        viewModel.onShown()

        viewModel.onUsePassword()

        assertEquals(UnlockExit.USE_PASSWORD, viewModel.uiState.value.exit)
        assertNull(sessionStore.session.value)
        viewModel.onExitHandled()
        assertFalse(lockManager.locked.value)
        assertTrue("la biometria sigue activa para el proximo ingreso", vault.isEnabled())
    }

    @Test
    fun `reintentar tras un fallo vuelve a pedir la biometria`() {
        viewModel.onShown()
        viewModel.onPromptLaunched()
        failAttempts(1)

        viewModel.onRetry()

        assertNotNull(viewModel.uiState.value.promptCipher)
    }
}
