package com.solventa.app.feature.activation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.solventa.app.core.camera.CapturedFrame
import com.solventa.app.core.camera.CapturedFrames
import com.solventa.app.core.device.DeviceInfoProvider
import com.solventa.app.core.liveness.CaptureGuideEngine
import com.solventa.app.core.liveness.FaceSignals
import com.solventa.app.core.liveness.FrameSlot
import com.solventa.app.core.liveness.GuideState
import com.solventa.app.core.liveness.LivenessChallenge
import com.solventa.app.core.liveness.launcher.CaptureLauncherFactory
import com.solventa.app.data.activation.ActivationRepository
import com.solventa.app.data.activation.ActivationSession
import com.solventa.app.data.activation.CaptureOutcome
import com.solventa.app.data.activation.CaptureType
import com.solventa.app.data.activation.SdkStatus
import com.solventa.app.data.activation.SessionResult
import com.solventa.app.data.activation.VerificationResult
import com.solventa.app.data.auth.Session
import com.solventa.app.data.session.SessionStore
import com.solventa.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

enum class ActivationStep { INTRO, STARTING, CAPTURE, LAUNCHING_SDK, SUBMITTING, RESULT }

enum class ActivationOutcome {
    APPROVED, REJECTED, EXHAUSTED, ACCOUNT_NOT_FOUND, INVALID_CAPTURE, NETWORK, SERVICE_UNAVAILABLE, LOCAL_FAILURE,
}

enum class ActivationDestination { BIOMETRIC_OFFER, HOME, LOGIN, ACCOUNT_NOT_FOUND, CONSENT }

data class ActivationUiState(
    val step: ActivationStep = ActivationStep.INTRO,
    val challenge: LivenessChallenge? = null,
    val sdkToken: String? = null,
    val guide: GuideState = GuideState.WaitingForFace,
    val outcome: ActivationOutcome? = null,
    val remainingAttempts: Int? = null,
    val reason: String? = null,
    val destination: ActivationDestination? = null,
)

/**
 * Flujo de activación (HU3.1.1): sesión → captura (propia o SDK) → verificación → resultado.
 * El gesto lo decide el backend. Las capturas se sobrescriben con ceros al terminar, pase lo que pase.
 */
class ActivationViewModel(
    private val repository: ActivationRepository,
    private val sessionStore: SessionStore,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val launchers: CaptureLauncherFactory,
    private val shouldOfferBiometric: (Session) -> Boolean,
    private val logout: () -> Unit,
    private val guideEngineFactory: (LivenessChallenge) -> CaptureGuideEngine = { CaptureGuideEngine(it) },
    private val correlationIds: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivationUiState())
    val uiState: StateFlow<ActivationUiState> = _uiState.asStateFlow()

    private var session: ActivationSession? = null
    private var engine: CaptureGuideEngine? = null
    private var frames = CapturedFrames()
    private val lock = Any()

    /** Crea siempre una sesión nueva: una sesión FRAMES nunca se reutiliza (D11). */
    fun onStart() {
        val state = _uiState.value
        if (state.step == ActivationStep.STARTING || state.step == ActivationStep.SUBMITTING) return
        val token = accessToken() ?: return expireSession()
        discardFrames()
        _uiState.update { it.copy(step = ActivationStep.STARTING, outcome = null, reason = null) }
        viewModelScope.launch {
            when (val result = repository.startSession(token, deviceInfoProvider.get(), correlationIds())) {
                is SessionResult.Started -> begin(result.session)
                SessionResult.AlreadyLinked -> markLinked()
                SessionResult.AttemptsExhausted -> showResult(ActivationOutcome.EXHAUSTED, remaining = 0)
                SessionResult.ConsentRequired -> navigate(ActivationDestination.CONSENT)
                SessionResult.AccountNotFound -> navigate(ActivationDestination.ACCOUNT_NOT_FOUND)
                SessionResult.SessionExpired -> expireSession()
                SessionResult.NetworkError -> showResult(ActivationOutcome.NETWORK)
                SessionResult.ServiceUnavailable -> showResult(ActivationOutcome.SERVICE_UNAVAILABLE)
            }
        }
    }

    private fun begin(newSession: ActivationSession) {
        val launcher = launchers.forType(newSession.capture)
        if (launcher == null) {
            showResult(ActivationOutcome.SERVICE_UNAVAILABLE)
            return
        }
        session = newSession
        when (val capture = newSession.capture) {
            is CaptureType.Frames -> {
                synchronized(lock) {
                    engine = guideEngineFactory(capture.challenge)
                    frames = CapturedFrames()
                }
                _uiState.update {
                    it.copy(
                        step = ActivationStep.CAPTURE,
                        challenge = capture.challenge,
                        guide = GuideState.WaitingForFace,
                        remainingAttempts = newSession.remainingAttempts,
                    )
                }
            }
            is CaptureType.Sdk -> _uiState.update {
                it.copy(
                    step = ActivationStep.LAUNCHING_SDK,
                    sdkToken = capture.sessionToken,
                    remainingAttempts = newSession.remainingAttempts,
                )
            }
        }
    }

    /** Llamado desde el hilo del analizador; devuelve la captura a tomar de este fotograma. */
    fun onFaceSignals(signals: FaceSignals): FrameSlot? = synchronized(lock) {
        if (_uiState.value.step != ActivationStep.CAPTURE) return null
        val update = engine?.onFrame(signals) ?: return null
        _uiState.update { it.copy(guide = update.state) }
        if (update.state is GuideState.Failed) {
            discardFrames()
            showResult(ActivationOutcome.LOCAL_FAILURE)
        }
        update.captureSlot
    }

    /** Llamado desde el analizador con el JPEG del fotograma pedido. */
    fun onFrameCaptured(slot: FrameSlot, jpeg: ByteArray, capturedAtMillis: Long) {
        val ready = synchronized(lock) {
            if (_uiState.value.step != ActivationStep.CAPTURE) {
                jpeg.fill(0)
                return
            }
            frames[slot] = CapturedFrame(jpeg, Instant.ofEpochMilli(capturedAtMillis))
            frames.isComplete()
        }
        if (ready) submit(CaptureOutcomeSource.FRAMES)
    }

    fun onSdkResult(status: SdkStatus) {
        if (_uiState.value.step != ActivationStep.LAUNCHING_SDK) return
        if (status == SdkStatus.CANCELLED) {
            submit(CaptureOutcomeSource.SDK_CANCELLED)
        } else {
            submit(if (status == SdkStatus.COMPLETED) CaptureOutcomeSource.SDK_COMPLETED else CaptureOutcomeSource.SDK_FAILED)
        }
    }

    private enum class CaptureOutcomeSource { FRAMES, SDK_COMPLETED, SDK_CANCELLED, SDK_FAILED }

    private fun submit(source: CaptureOutcomeSource) {
        val current = session ?: return
        val token = accessToken() ?: return expireSession()
        val outcome = when (source) {
            CaptureOutcomeSource.FRAMES -> CaptureOutcome.OwnFrames(synchronized(lock) { frames })
            CaptureOutcomeSource.SDK_COMPLETED -> CaptureOutcome.SdkResult(SdkStatus.COMPLETED)
            CaptureOutcomeSource.SDK_CANCELLED -> CaptureOutcome.SdkResult(SdkStatus.CANCELLED)
            CaptureOutcomeSource.SDK_FAILED -> CaptureOutcome.SdkResult(SdkStatus.FAILED)
        }
        // Sin doble envío: solo se envía desde CAPTURE o LAUNCHING_SDK.
        var accepted = false
        _uiState.update {
            if (it.step == ActivationStep.CAPTURE || it.step == ActivationStep.LAUNCHING_SDK) {
                accepted = true
                it.copy(step = ActivationStep.SUBMITTING)
            } else {
                it
            }
        }
        if (!accepted) return
        viewModelScope.launch {
            val result = try {
                repository.verify(token, current, outcome, correlationIds())
            } finally {
                discardFrames()
            }
            handleVerification(result)
        }
    }

    private fun handleVerification(result: VerificationResult) {
        when (result) {
            is VerificationResult.Approved -> markLinked()
            is VerificationResult.Rejected -> showResult(ActivationOutcome.REJECTED, result.remainingAttempts, result.reason)
            VerificationResult.AttemptsExhausted -> showResult(ActivationOutcome.EXHAUSTED, remaining = 0)
            VerificationResult.InvalidSession -> showResult(ActivationOutcome.SERVICE_UNAVAILABLE)
            VerificationResult.Cancelled -> _uiState.update { it.copy(step = ActivationStep.INTRO) }
            VerificationResult.InvalidCapture -> showResult(ActivationOutcome.INVALID_CAPTURE)
            VerificationResult.SessionExpired -> expireSession()
            VerificationResult.NetworkError -> showResult(ActivationOutcome.NETWORK)
            VerificationResult.ServiceUnavailable -> showResult(ActivationOutcome.SERVICE_UNAVAILABLE)
        }
    }

    /** "Reintentar": siempre con una sesión y un gesto nuevos; no consume intentos por sí mismo. */
    fun onRetry() = onStart()

    fun onCancel() {
        discardFrames()
        session = null
        _uiState.update { ActivationUiState(remainingAttempts = it.remainingAttempts) }
    }

    fun onDestinationHandled() {
        _uiState.update { it.copy(destination = null) }
    }

    /** Aprobado o instalación ya vinculada: la sesión queda vinculada; `estadoKyc` no se toca (HU1.3.3). */
    private fun markLinked() {
        val current = sessionStore.session.value
        if (current == null) {
            expireSession()
            return
        }
        val linked = current.copy(cliente = current.cliente.copy(dispositivoVinculado = true))
        sessionStore.save(linked)
        _uiState.update {
            it.copy(
                step = ActivationStep.RESULT,
                outcome = ActivationOutcome.APPROVED,
                destination = if (shouldOfferBiometric(linked)) ActivationDestination.BIOMETRIC_OFFER else ActivationDestination.HOME,
            )
        }
    }

    private fun showResult(outcome: ActivationOutcome, remaining: Int? = null, reason: String? = null) {
        _uiState.update {
            it.copy(
                step = ActivationStep.RESULT,
                outcome = outcome,
                remainingAttempts = remaining ?: it.remainingAttempts,
                reason = reason,
            )
        }
    }

    private fun navigate(destination: ActivationDestination) {
        _uiState.update { it.copy(step = ActivationStep.INTRO, destination = destination) }
    }

    private fun expireSession() {
        discardFrames()
        logout()
        _uiState.update { it.copy(step = ActivationStep.INTRO, destination = ActivationDestination.LOGIN) }
    }

    private fun accessToken(): String? = sessionStore.session.value?.accessToken?.takeIf { it.isNotBlank() }

    private fun discardFrames() = synchronized(lock) { frames.wipe() }

    override fun onCleared() {
        discardFrames()
        super.onCleared()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ActivationViewModel(
                    repository = container.activationRepository,
                    sessionStore = container.sessionStore,
                    deviceInfoProvider = container.deviceInfoProvider,
                    launchers = container.captureLauncherFactory,
                    shouldOfferBiometric = container::shouldOfferBiometric,
                    logout = container::logout,
                )
            }
        }
    }
}
