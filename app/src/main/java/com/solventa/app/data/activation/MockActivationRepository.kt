package com.solventa.app.data.activation

import com.solventa.app.core.device.DeviceInfo
import com.solventa.app.core.liveness.LivenessChallenge
import kotlinx.coroutines.delay
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

/**
 * Backend simulado de la activación (mismas formas que el contrato canónico).
 *
 * Reglas de demostración, según el correo de la sesión:
 * - [REJECT_EMAIL]: la prueba siempre se rechaza; al tercer intento se agotan.
 * - [NO_CONSENT_EMAIL]: falta el consentimiento biométrico.
 * - cualquier otro: se aprueba (con FRAMES, solo si llegan las tres capturas).
 * El gesto lo elige el "servidor" simulado; [captureType] = "SDK" simula Didit.
 */
class MockActivationRepository(
    private val currentEmail: () -> String?,
    private val captureType: String = "FRAMES",
    private val latencyMillis: Long = 1200,
    private val random: Random = Random.Default,
) : ActivationRepository {

    private val rejectedByInstallation = mutableMapOf<String, Int>()
    private val linkedInstallations = mutableSetOf<String>()
    private val installationBySession = mutableMapOf<String, String>()

    override suspend fun startSession(accessToken: String, device: DeviceInfo, correlationId: String): SessionResult {
        delay(latencyMillis)
        val email = currentEmail()?.lowercase()
        if (email == NO_CONSENT_EMAIL) return SessionResult.ConsentRequired
        if (device.installationId in linkedInstallations) return SessionResult.AlreadyLinked
        val rejected = rejectedByInstallation[device.installationId] ?: 0
        if (rejected >= MAX_ATTEMPTS) return SessionResult.AttemptsExhausted
        val sessionId = UUID.randomUUID().toString()
        installationBySession[sessionId] = device.installationId
        val capture = if (captureType == "SDK") {
            CaptureType.Sdk("DIDIT", "mock-token-${UUID.randomUUID()}")
        } else {
            CaptureType.Frames(LivenessChallenge.entries.random(random), UUID.randomUUID().toString())
        }
        return SessionResult.Started(
            ActivationSession(sessionId, Instant.now().plusSeconds(120).toString(), MAX_ATTEMPTS - rejected, capture),
        )
    }

    override suspend fun verify(
        accessToken: String,
        session: ActivationSession,
        outcome: CaptureOutcome,
        correlationId: String,
    ): VerificationResult {
        delay(latencyMillis)
        val installationId = installationBySession.remove(session.sessionId) ?: return VerificationResult.InvalidSession
        if (outcome is CaptureOutcome.SdkResult && outcome.status == SdkStatus.CANCELLED) return VerificationResult.Cancelled
        if (outcome is CaptureOutcome.OwnFrames && !outcome.frames.isComplete()) return VerificationResult.InvalidCapture
        val rejected = rejectedByInstallation[installationId] ?: 0
        if (currentEmail()?.lowercase() == REJECT_EMAIL) {
            val total = rejected + 1
            rejectedByInstallation[installationId] = total
            return if (total >= MAX_ATTEMPTS) {
                VerificationResult.AttemptsExhausted
            } else {
                VerificationResult.Rejected(total, MAX_ATTEMPTS - total, "GESTO_NO_DETECTADO")
            }
        }
        linkedInstallations += installationId
        return VerificationResult.Approved("MOCK-${UUID.randomUUID().toString().take(8)}", Instant.now().toString())
    }

    companion object {
        const val REJECT_EMAIL = "rechazo.vida@correo.com"
        const val NO_CONSENT_EMAIL = "sin.consentimiento@correo.com"
        const val MAX_ATTEMPTS = 3
    }
}
