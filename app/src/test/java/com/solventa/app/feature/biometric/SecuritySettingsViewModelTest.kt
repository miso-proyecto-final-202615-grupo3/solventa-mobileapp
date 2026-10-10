package com.solventa.app.feature.biometric

import com.solventa.app.core.biometric.BiometricAvailability
import com.solventa.app.core.biometric.BiometricPromptEvent
import com.solventa.app.core.biometric.FakeBiometricVault
import com.solventa.app.core.biometric.testSession
import com.solventa.app.data.session.InMemorySessionStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Cipher

class SecuritySettingsViewModelTest {

    private val vault = FakeBiometricVault()
    private val sessionStore = InMemorySessionStore().apply { save(testSession()) }
    private val cipher: Cipher = Cipher.getInstance("AES/GCM/NoPadding")

    @Test
    fun `activar desde el interruptor pide biometria y la activa`() {
        val viewModel = SecuritySettingsViewModel(vault, sessionStore)

        viewModel.onToggle(true)
        viewModel.onPromptLaunched()
        viewModel.onPromptEvent(BiometricPromptEvent.Succeeded(cipher))

        assertTrue(viewModel.uiState.value.biometricEnabled)
    }

    @Test
    fun `desactivar borra la llave y la sesion cifrada`() {
        vault.seal(cipher, testSession())
        val viewModel = SecuritySettingsViewModel(vault, sessionStore)

        viewModel.onToggle(false)

        assertFalse(viewModel.uiState.value.biometricEnabled)
        assertTrue(vault.disableCalls > 0)
    }

    @Test
    fun `sin biometria configurada el interruptor no hace nada`() {
        vault.availability = BiometricAvailability.NONE_ENROLLED
        val viewModel = SecuritySettingsViewModel(vault, sessionStore)

        viewModel.onToggle(true)

        assertNull(viewModel.uiState.value.promptCipher)
        assertFalse(viewModel.uiState.value.biometricEnabled)
    }

    @Test
    fun `refrescar detecta que el usuario configuro una huella`() {
        vault.availability = BiometricAvailability.NONE_ENROLLED
        val viewModel = SecuritySettingsViewModel(vault, sessionStore)

        vault.availability = BiometricAvailability.AVAILABLE
        viewModel.refresh()

        assertTrue(viewModel.uiState.value.biometricAvailable)
    }
}
