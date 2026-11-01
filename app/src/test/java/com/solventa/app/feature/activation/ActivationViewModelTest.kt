package com.solventa.app.feature.activation

import androidx.activity.ComponentActivity
import com.solventa.app.MainDispatcherRule
import com.solventa.app.core.biometric.testSession
import com.solventa.app.core.device.DeviceInfo
import com.solventa.app.core.liveness.FaceSignals
import com.solventa.app.core.liveness.FrameSlot
import com.solventa.app.core.liveness.GuideState
import com.solventa.app.core.liveness.LivenessChallenge
import com.solventa.app.core.liveness.launcher.CaptureLauncherFactory
import com.solventa.app.core.liveness.launcher.DiditCaptureLauncher
import com.solventa.app.core.liveness.launcher.OwnCaptureLauncher
import com.solventa.app.data.activation.ActivationRepository
import com.solventa.app.data.activation.ActivationSession
import com.solventa.app.data.activation.CaptureOutcome
import com.solventa.app.data.activation.CaptureType
import com.solventa.app.data.activation.SdkStatus
import com.solventa.app.data.activation.SessionResult
import com.solventa.app.data.activation.VerificationResult
import com.solventa.app.data.auth.EstadoKyc
import com.solventa.app.data.session.InMemorySessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ActivationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeRepository : ActivationRepository {
        val sessions = ArrayDeque<SessionResult>()
        var verification: VerificationResult = VerificationResult.Approved("folio", "2026-10-31T12:00:00Z")
        var verifyError: Throwable? = null
        var verifyCalls = 0
        var startCalls = 0
        var lastOutcome: CaptureOutcome? = null
        var framesSeenComplete = false

        override suspend fun startSession(accessToken: String, device: DeviceInfo, correlationId: String): SessionResult {
            startCalls++
            return sessions.removeFirstOrNull() ?: framesStarted("s-$startCalls", LivenessChallenge.BLINK)
        }

        override suspend fun verify(
            accessToken: String,
            session: ActivationSession,
            outcome: CaptureOutcome,
            correlationId: String,
        ): VerificationResult {
            verifyCalls++
            lastOutcome = outcome
            framesSeenComplete = (outcome as? CaptureOutcome.OwnFrames)?.frames?.isComplete() == true
            verifyError?.let { throw it }
            return verification
        }
    }

    private val sdkLauncher = object : DiditCaptureLauncher {
        override suspend fun launch(activity: ComponentActivity, sessionToken: String) = SdkStatus.COMPLETED
    }

    private val repository = FakeRepository()
    private val sessionStore = InMemorySessionStore().apply { save(testSession(linked = false)) }
    private var loggedOut = false
    private var offerBiometric = true

    private val viewModel = ActivationViewModel(
        repository = repository,
        sessionStore = sessionStore,
        deviceInfoProvider = { DeviceInfo("instalacion-1", model = "Pixel", osVersion = "15") },
        launchers = CaptureLauncherFactory(listOf(OwnCaptureLauncher, sdkLauncher)),
        shouldOfferBiometric = { offerBiometric },
        logout = { loggedOut = true; sessionStore.clear() },
        correlationIds = { "corr" },
    )

    private fun neutral(t: Long, eyes: Float = 0.95f) = FaceSignals(
        faceCount = 1, leftEyeOpenProbability = eyes, rightEyeOpenProbability = eyes,
        faceBoxRatio = 0.2f, faceCentered = true, timestampMillis = t,
    )

    /** Simula al analizador: entrega señales y, si la guía lo pide, el JPEG de ese fotograma. */
    private fun feed(signals: FaceSignals) {
        val slot = viewModel.onFaceSignals(signals) ?: return
        viewModel.onFrameCaptured(slot, byteArrayOf(-1, -40, -1, signals.timestampMillis.toByte()), 1_000 + signals.timestampMillis)
    }

    private fun completeBlink() {
        feed(neutral(0))
        feed(neutral(600))
        feed(neutral(800, eyes = 0.05f))
        feed(neutral(900))
        feed(neutral(1_300))
    }

    @Test
    fun `usa el gesto que decide el backend`() {
        repository.sessions += framesStarted("s-1", LivenessChallenge.TURN_LEFT)

        viewModel.onStart()

        assertEquals(ActivationStep.CAPTURE, viewModel.uiState.value.step)
        assertEquals(LivenessChallenge.TURN_LEFT, viewModel.uiState.value.challenge)
    }

    @Test
    fun `flujo frames aprobado vincula la sesion y ofrece biometria`() {
        viewModel.onStart()

        completeBlink()

        assertEquals(1, repository.verifyCalls)
        assertTrue(repository.framesSeenComplete)
        val state = viewModel.uiState.value
        assertEquals(ActivationOutcome.APPROVED, state.outcome)
        assertEquals(ActivationDestination.BIOMETRIC_OFFER, state.destination)
        val cliente = sessionStore.session.value!!.cliente
        assertTrue(cliente.dispositivoVinculado)
        assertEquals(EstadoKyc.VERIFICADO, cliente.estadoKyc)
    }

    @Test
    fun `sin oferta biometrica pendiente va a inicio`() {
        offerBiometric = false
        viewModel.onStart()

        completeBlink()

        assertEquals(ActivationDestination.HOME, viewModel.uiState.value.destination)
    }

    @Test
    fun `las capturas se borran despues de enviar, con exito o con error`() {
        viewModel.onStart()
        completeBlink()
        val sent = (repository.lastOutcome as CaptureOutcome.OwnFrames).frames
        assertFalse(sent.isComplete())

        repository.verifyError = IllegalStateException("fallo inesperado")
        viewModel.onRetry()
        runCatching { completeBlink() }
        val failed = (repository.lastOutcome as CaptureOutcome.OwnFrames).frames
        assertFalse(failed.isComplete())
    }

    @Test
    fun `rechazo muestra intentos restantes y reintentar crea otra sesion`() {
        repository.verification = VerificationResult.Rejected(1, 2, "GESTO_NO_DETECTADO")
        viewModel.onStart()
        completeBlink()

        assertEquals(ActivationOutcome.REJECTED, viewModel.uiState.value.outcome)
        assertEquals(2, viewModel.uiState.value.remainingAttempts)

        viewModel.onRetry()
        assertEquals(2, repository.startCalls)
        assertEquals(ActivationStep.CAPTURE, viewModel.uiState.value.step)
    }

    @Test
    fun `503 en la verificacion frames pide una sesion nueva al reintentar`() {
        repository.verification = VerificationResult.ServiceUnavailable
        viewModel.onStart()
        completeBlink()
        assertEquals(ActivationOutcome.SERVICE_UNAVAILABLE, viewModel.uiState.value.outcome)

        viewModel.onRetry()

        assertEquals(2, repository.startCalls)
    }

    @Test
    fun `intentos agotados`() {
        repository.sessions += SessionResult.AttemptsExhausted

        viewModel.onStart()

        assertEquals(ActivationOutcome.EXHAUSTED, viewModel.uiState.value.outcome)
    }

    @Test
    fun `ya vinculado pasa directo`() {
        repository.sessions += SessionResult.AlreadyLinked

        viewModel.onStart()

        assertEquals(ActivationOutcome.APPROVED, viewModel.uiState.value.outcome)
        assertTrue(sessionStore.session.value!!.cliente.dispositivoVinculado)
    }

    @Test
    fun `consentimiento requerido vuelve al consentimiento`() {
        repository.sessions += SessionResult.ConsentRequired

        viewModel.onStart()

        assertEquals(ActivationDestination.CONSENT, viewModel.uiState.value.destination)
    }

    @Test
    fun `sesion vencida cierra sesion y va al login`() {
        repository.sessions += SessionResult.SessionExpired

        viewModel.onStart()

        assertTrue(loggedOut)
        assertEquals(ActivationDestination.LOGIN, viewModel.uiState.value.destination)
    }

    @Test
    fun `falla local de la guia no llama al backend`() {
        viewModel.onStart()
        feed(neutral(0))
        feed(neutral(600))
        feed(FaceSignals(faceCount = 2, timestampMillis = 700))

        assertEquals(0, repository.verifyCalls)
        assertEquals(ActivationOutcome.LOCAL_FAILURE, viewModel.uiState.value.outcome)
    }

    @Test
    fun `sin doble envio`() {
        viewModel.onStart()
        completeBlink()

        viewModel.onFrameCaptured(FrameSlot.END, byteArrayOf(1), 5_000)
        assertNull(viewModel.onFaceSignals(neutral(6_000)))

        assertEquals(1, repository.verifyCalls)
    }

    @Test
    fun `captura sdk se verifica con el resultado del lanzador`() {
        repository.sessions += SessionResult.Started(
            ActivationSession("s-sdk", "x", 3, CaptureType.Sdk("DIDIT", "tok")),
        )
        viewModel.onStart()
        assertEquals(ActivationStep.LAUNCHING_SDK, viewModel.uiState.value.step)
        assertEquals("tok", viewModel.uiState.value.sdkToken)

        viewModel.onSdkResult(SdkStatus.COMPLETED)

        assertEquals(CaptureOutcome.SdkResult(SdkStatus.COMPLETED), repository.lastOutcome)
        assertEquals(ActivationOutcome.APPROVED, viewModel.uiState.value.outcome)
    }

    @Test
    fun `tipo de captura sin estrategia no se intenta`() {
        repository.sessions += SessionResult.Started(
            ActivationSession("s-x", "x", 3, CaptureType.Sdk("OTRO", "tok")),
        )

        viewModel.onStart()

        assertEquals(ActivationOutcome.SERVICE_UNAVAILABLE, viewModel.uiState.value.outcome)
    }

    @Test
    fun `cancelar descarta capturas parciales`() {
        viewModel.onStart()
        feed(neutral(0))
        feed(neutral(600))

        viewModel.onCancel()

        assertEquals(ActivationStep.INTRO, viewModel.uiState.value.step)
        assertEquals(GuideState.WaitingForFace, viewModel.uiState.value.guide)
    }

    private companion object {
        fun framesStarted(id: String, challenge: LivenessChallenge) = SessionResult.Started(
            ActivationSession(id, "2026-10-31T12:02:00Z", 3, CaptureType.Frames(challenge, "nonce-$id")),
        )
    }
}

