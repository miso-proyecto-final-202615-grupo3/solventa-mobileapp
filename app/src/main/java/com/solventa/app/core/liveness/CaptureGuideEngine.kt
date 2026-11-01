package com.solventa.app.core.liveness

import kotlin.math.abs

/** Gesto pedido por el servidor; la app nunca lo elige. */
enum class LivenessChallenge {
    BLINK, TURN_LEFT, TURN_RIGHT;

    companion object {
        fun fromCode(code: String): LivenessChallenge? = entries.firstOrNull { it.name == code }
    }
}

enum class FrameSlot { START, GESTURE, END }

enum class FaceHint { NO_FACE, TOO_FAR, NOT_CENTERED, MULTIPLE_FACES }

enum class GuideFailure { TIMEOUT, FACE_LOST, MULTIPLE_FACES }

/** Lo que el detector ve en un fotograma; modelo propio para no depender de ML Kit. */
data class FaceSignals(
    val faceCount: Int,
    val leftEyeOpenProbability: Float? = null,
    val rightEyeOpenProbability: Float? = null,
    val headEulerY: Float = 0f,
    val faceBoxRatio: Float = 0f,
    val faceCentered: Boolean = false,
    val timestampMillis: Long,
)

sealed interface GuideState {
    data object WaitingForFace : GuideState
    data class AdjustFace(val hint: FaceHint) : GuideState
    data class HoldStill(val progress: Float) : GuideState
    data class ChallengeInProgress(val challenge: LivenessChallenge) : GuideState
    data object ReturnToCenter : GuideState
    data object Completed : GuideState
    data class Failed(val reason: GuideFailure) : GuideState
}

/** Resultado de procesar un fotograma: el estado para la UI y, si toca, qué captura tomar de ese fotograma. */
data class GuideUpdate(val state: GuideState, val captureSlot: FrameSlot? = null)

data class GuideConfig(
    val minFaceBoxRatio: Float = 0.08f,
    val stableMillis: Long = 500,
    val eyesOpenThreshold: Float = 0.8f,
    val eyesClosedThreshold: Float = 0.2f,
    val neutralYawDegrees: Float = 10f,
    val turnYawDegrees: Float = 25f,
    /**
     * Signo de `headEulerY` de ML Kit para "girar a la izquierda" con la cámara frontal.
     * ML Kit reporta positivo cuando el rostro gira hacia la derecha de la imagen; se debe confirmar
     * en un dispositivo real junto con el signo que usa MediaPipe en el backend.
     */
    val turnLeftSign: Int = 1,
    val returnMillis: Long = 300,
    val maxChallengeMillis: Long = 10_000,
    val faceLostMillis: Long = 1_000,
)

/**
 * Máquina de estados de la guía local (pura, sin Android). Pide tres capturas: rostro neutro (START),
 * pico del gesto (GESTURE) y rostro neutro otra vez (END). El veredicto es del backend; esto solo guía.
 * Los fallos locales no consumen intentos porque nunca llegan al servidor.
 */
class CaptureGuideEngine(
    private val challenge: LivenessChallenge,
    private val config: GuideConfig = GuideConfig(),
) {
    private enum class Phase { SEARCHING, STABILIZING, GESTURE, RETURN, DONE, FAILED }

    private var phase = Phase.SEARCHING
    private var stableSince: Long? = null
    private var challengeStartedAt: Long? = null
    private var gestureAt: Long? = null
    private var neutralSince: Long? = null
    private var faceMissingSince: Long? = null
    private var terminal: GuideState? = null

    fun onFrame(signals: FaceSignals): GuideUpdate {
        terminal?.let { return GuideUpdate(it) }
        val now = signals.timestampMillis
        challengeStartedAt?.let { started ->
            if (now - started > config.maxChallengeMillis) return fail(GuideFailure.TIMEOUT)
        }
        if (signals.faceCount > 1) {
            return if (phase == Phase.GESTURE || phase == Phase.RETURN) {
                fail(GuideFailure.MULTIPLE_FACES)
            } else {
                resetStability()
                GuideUpdate(GuideState.AdjustFace(FaceHint.MULTIPLE_FACES))
            }
        }
        if (signals.faceCount == 0) {
            if (phase == Phase.GESTURE || phase == Phase.RETURN) {
                val since = faceMissingSince ?: now.also { faceMissingSince = it }
                if (now - since > config.faceLostMillis) return fail(GuideFailure.FACE_LOST)
                return GuideUpdate(GuideState.AdjustFace(FaceHint.NO_FACE))
            }
            resetStability()
            return GuideUpdate(GuideState.WaitingForFace)
        }
        faceMissingSince = null

        return when (phase) {
            Phase.SEARCHING, Phase.STABILIZING -> stabilize(signals, now)
            Phase.GESTURE -> awaitGesture(signals, now)
            Phase.RETURN -> awaitReturn(signals, now)
            Phase.DONE -> GuideUpdate(GuideState.Completed)
            Phase.FAILED -> GuideUpdate(terminal ?: GuideState.Failed(GuideFailure.TIMEOUT))
        }
    }

    private fun stabilize(signals: FaceSignals, now: Long): GuideUpdate {
        val hint = frameHint(signals)
        if (hint != null || !isNeutral(signals)) {
            resetStability()
            return GuideUpdate(GuideState.AdjustFace(hint ?: FaceHint.NOT_CENTERED))
        }
        val since = stableSince ?: now.also { stableSince = it; phase = Phase.STABILIZING }
        val elapsed = now - since
        if (elapsed < config.stableMillis) {
            return GuideUpdate(GuideState.HoldStill(elapsed.toFloat() / config.stableMillis))
        }
        phase = Phase.GESTURE
        challengeStartedAt = now
        return GuideUpdate(GuideState.ChallengeInProgress(challenge), captureSlot = FrameSlot.START)
    }

    private fun awaitGesture(signals: FaceSignals, now: Long): GuideUpdate {
        if (!isGesture(signals)) return GuideUpdate(GuideState.ChallengeInProgress(challenge))
        phase = Phase.RETURN
        gestureAt = now
        neutralSince = null
        return GuideUpdate(GuideState.ReturnToCenter, captureSlot = FrameSlot.GESTURE)
    }

    private fun awaitReturn(signals: FaceSignals, now: Long): GuideUpdate {
        if (!isNeutral(signals) || frameHint(signals) != null) {
            neutralSince = null
            return GuideUpdate(GuideState.ReturnToCenter)
        }
        val since = neutralSince ?: now.also { neutralSince = it }
        val sinceGesture = now - (gestureAt ?: now)
        if (now - since < config.returnMillis || sinceGesture < config.returnMillis) {
            return GuideUpdate(GuideState.ReturnToCenter)
        }
        phase = Phase.DONE
        terminal = GuideState.Completed
        return GuideUpdate(GuideState.Completed, captureSlot = FrameSlot.END)
    }

    private fun frameHint(signals: FaceSignals): FaceHint? = when {
        signals.faceBoxRatio < config.minFaceBoxRatio -> FaceHint.TOO_FAR
        !signals.faceCentered -> FaceHint.NOT_CENTERED
        else -> null
    }

    private fun isNeutral(signals: FaceSignals): Boolean {
        if (abs(signals.headEulerY) >= config.neutralYawDegrees) return false
        if (challenge != LivenessChallenge.BLINK) return true
        val left = signals.leftEyeOpenProbability ?: return false
        val right = signals.rightEyeOpenProbability ?: return false
        return left > config.eyesOpenThreshold && right > config.eyesOpenThreshold
    }

    private fun isGesture(signals: FaceSignals): Boolean = when (challenge) {
        LivenessChallenge.BLINK -> {
            val left = signals.leftEyeOpenProbability
            val right = signals.rightEyeOpenProbability
            left != null && right != null && left < config.eyesClosedThreshold && right < config.eyesClosedThreshold
        }
        LivenessChallenge.TURN_LEFT -> signals.headEulerY * config.turnLeftSign > config.turnYawDegrees
        LivenessChallenge.TURN_RIGHT -> -signals.headEulerY * config.turnLeftSign > config.turnYawDegrees
    }

    private fun resetStability() {
        stableSince = null
        if (phase == Phase.STABILIZING) phase = Phase.SEARCHING
    }

    private fun fail(reason: GuideFailure): GuideUpdate {
        phase = Phase.FAILED
        val state = GuideState.Failed(reason)
        terminal = state
        return GuideUpdate(state)
    }
}
