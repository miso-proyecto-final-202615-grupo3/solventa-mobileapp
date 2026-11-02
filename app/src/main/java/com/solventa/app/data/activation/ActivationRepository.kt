package com.solventa.app.data.activation

import com.solventa.app.core.camera.CapturedFrames
import com.solventa.app.core.device.DeviceInfo
import com.solventa.app.core.liveness.LivenessChallenge

/**
 * Cómo capturar, según la sesión del backend (contrato canónico, plan del backend §3).
 * La app no conoce al proveedor que decide: solo recibe el tipo de captura.
 */
sealed interface CaptureType {
    data class Frames(val challenge: LivenessChallenge, val nonce: String) : CaptureType
    data class Sdk(val sdkName: String, val sessionToken: String) : CaptureType
}

data class ActivationSession(
    val sessionId: String,
    val expiresAt: String,
    val remainingAttempts: Int,
    val capture: CaptureType,
)

enum class SdkStatus { COMPLETED, CANCELLED, FAILED }

/** Lo que entrega una estrategia de captura para verificar. */
sealed interface CaptureOutcome {
    data class OwnFrames(val frames: CapturedFrames) : CaptureOutcome
    data class SdkResult(val status: SdkStatus) : CaptureOutcome
}

sealed interface SessionResult {
    data class Started(val session: ActivationSession) : SessionResult
    data object AlreadyLinked : SessionResult
    data object AttemptsExhausted : SessionResult
    data object ConsentRequired : SessionResult
    data object AccountNotFound : SessionResult
    data object SessionExpired : SessionResult
    data object NetworkError : SessionResult
    data object ServiceUnavailable : SessionResult
}

sealed interface VerificationResult {
    data class Approved(val folio: String?, val linkedAt: String?) : VerificationResult
    data class Rejected(val attempt: Int, val remainingAttempts: Int, val reason: String?) : VerificationResult
    data object AttemptsExhausted : VerificationResult

    /** 404 / 409: la sesión ya no sirve; hay que crear otra (no consume intento). */
    data object InvalidSession : VerificationResult

    /** 409 SESION_CANCELADA: la persona canceló el SDK. */
    data object Cancelled : VerificationResult
    data object InvalidCapture : VerificationResult
    data object SessionExpired : VerificationResult
    data object NetworkError : VerificationResult

    /** 503: no consume intento. */
    data object ServiceUnavailable : VerificationResult
}

interface ActivationRepository {
    /** `POST /api/v1/bff-mobile/activacion/sesiones`. */
    suspend fun startSession(accessToken: String, device: DeviceInfo, correlationId: String): SessionResult

    /** `POST …/sesiones/{id}/verificacion`. No borra las capturas: lo hace quien llama. */
    suspend fun verify(
        accessToken: String,
        session: ActivationSession,
        outcome: CaptureOutcome,
        correlationId: String,
    ): VerificationResult
}
