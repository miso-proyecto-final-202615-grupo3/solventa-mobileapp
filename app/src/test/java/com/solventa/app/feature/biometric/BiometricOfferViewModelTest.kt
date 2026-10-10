package com.solventa.app.feature.biometric

import com.solventa.app.core.biometric.BiometricAvailability
import com.solventa.app.core.biometric.BiometricPromptEvent
import com.solventa.app.core.biometric.FakeBiometricVault
import com.solventa.app.core.biometric.InMemoryBiometricSettings
import com.solventa.app.core.biometric.testSession
import com.solventa.app.data.session.InMemorySessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Cipher

class BiometricOfferViewModelTest {

    private val session = testSession()
    private val vault = FakeBiometricVault()
    private val settings = InMemoryBiometricSettings()
    private val sessionStore = InMemorySessionStore().also { it.save(session) }
    private val cipher: Cipher = Cipher.getInstance("AES/GCM/NoPadding")

    private fun viewModel() = BiometricOfferViewModel(vault, settings, sessionStore)

    @Test
    fun `con biometria configurada muestra la oferta`() {
        assertTrue(viewModel().uiState.value.biometricAvailable)
    }

    @Test
    fun `sin huella ni rostro configurados muestra el aviso y ofrece contrasena`() {
        vault.availability = BiometricAvailability.NONE_ENROLLED

        assertFalse(viewModel().uiState.value.biometricAvailable)
    }

    @Test
    fun `sin hardware biometrico tambien se detecta`() {
        vault.availability = BiometricAvailability.UNAVAILABLE

        assertFalse(viewModel().uiState.value.biometricAvailable)
    }

    @Test
    fun `activar pide el aviso del sistema y al confirmar guarda la sesion cifrada`() {
        val viewModel = viewModel()

        viewModel.onActivate()
        assertNotNull(viewModel.uiState.value.promptCipher)
        viewModel.onPromptLaunched()
        viewModel.onPromptEvent(BiometricPromptEvent.Succeeded(cipher))

        assertTrue(vault.isEnabled())
        assertEquals(session, vault.sealed)
        assertTrue(viewModel.uiState.value.finished)
    }

    @Test
    fun `cancelar el aviso no activa ni termina`() {
        val viewModel = viewModel()
        viewModel.onActivate()

        viewModel.onPromptEvent(BiometricPromptEvent.Canceled)

        assertFalse(vault.isEnabled())
        assertFalse(viewModel.uiState.value.finished)
    }

    @Test
    fun `ahora no registra el rechazo y continua con contrasena`() {
        val viewModel = viewModel()

        viewModel.onDecline()

        assertTrue(settings.offerDeclined)
        assertFalse(vault.isEnabled())
        assertTrue(viewModel.uiState.value.finished)
    }

    @Test
    fun `si el Keystore falla muestra error sin bloquear al usuario`() {
        vault.failOnSealingCipher = true
        val viewModel = viewModel()

        viewModel.onActivate()

        assertTrue(viewModel.uiState.value.activationFailed)
        assertFalse(viewModel.uiState.value.finished)
    }
}
