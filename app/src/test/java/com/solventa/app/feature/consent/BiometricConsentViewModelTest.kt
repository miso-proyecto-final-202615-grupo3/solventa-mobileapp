package com.solventa.app.feature.consent

import com.solventa.app.MainDispatcherRule
import com.solventa.app.core.biometric.testSession
import com.solventa.app.data.consent.BiometricConsentRepository
import com.solventa.app.data.consent.BiometricConsentStatus
import com.solventa.app.data.consent.ConsentDecision
import com.solventa.app.data.consent.ConsentResult
import com.solventa.app.data.consent.ConsentStatusResult
import com.solventa.app.data.session.InMemorySessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class BiometricConsentViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeRepository : BiometricConsentRepository {
        var status: ConsentStatusResult = ConsentStatusResult.Loaded(BiometricConsentStatus(false, "1.0", null))
        var recordResult: ((ConsentDecision) -> ConsentResult) = { ConsentResult.Recorded(it, "2026-10-31T12:00:00Z", "1.0") }
        val recorded = mutableListOf<Pair<ConsentDecision, String>>()

        override suspend fun getStatus(accessToken: String) = status

        override suspend fun record(
            accessToken: String,
            decision: ConsentDecision,
            termsVersion: String,
            installationId: String,
        ): ConsentResult {
            recorded += decision to termsVersion
            return recordResult(decision)
        }
    }

    private val repository = FakeRepository()
    private val sessionStore = InMemorySessionStore().apply { save(testSession(linked = false)) }

    private fun viewModel() = BiometricConsentViewModel(repository, sessionStore, { "instalacion-1" }, "1.0")

    @Test
    fun `si ya acepto la version vigente salta a la activacion`() {
        repository.status = ConsentStatusResult.Loaded(BiometricConsentStatus(true, "1.0", "1.0"))

        assertEquals(ConsentDestination.ACTIVATION, viewModel().uiState.value.destination)
    }

    @Test
    fun `si acepto una version anterior vuelve a mostrar el texto`() {
        repository.status = ConsentStatusResult.Loaded(BiometricConsentStatus(true, "2.0", "1.0"))

        val state = viewModel().uiState.value

        assertNull(state.destination)
        assertEquals("2.0", state.termsVersion)
    }

    @Test
    fun `aceptar exige el checkbox`() {
        val viewModel = viewModel()

        viewModel.onAccept()
        assertEquals(0, repository.recorded.size)

        viewModel.onCheckedChange(true)
        viewModel.onAccept()

        assertEquals(listOf(ConsentDecision.ACCEPTED to "1.0"), repository.recorded)
        assertEquals(ConsentDestination.ACTIVATION, viewModel.uiState.value.destination)
    }

    @Test
    fun `el rechazo tambien se registra`() {
        val viewModel = viewModel()

        viewModel.onReject()

        assertEquals(listOf(ConsentDecision.REJECTED to "1.0"), repository.recorded)
        assertEquals(ConsentDestination.DECLINED, viewModel.uiState.value.destination)
    }

    @Test
    fun `texto desactualizado recarga el estado y exige marcar de nuevo`() {
        repository.recordResult = { ConsentResult.OutdatedTerms }
        val viewModel = viewModel()
        viewModel.onCheckedChange(true)

        viewModel.onAccept()

        assertEquals(false, viewModel.uiState.value.checked)
        assertNull(viewModel.uiState.value.destination)
    }

    @Test
    fun `errores de red se muestran sin navegar`() {
        repository.status = ConsentStatusResult.NetworkError

        assertEquals(ConsentError.NETWORK, viewModel().uiState.value.error)
    }

    @Test
    fun `sin sesion va al login`() {
        sessionStore.clear()

        assertEquals(ConsentDestination.LOGIN, viewModel().uiState.value.destination)
    }
}
