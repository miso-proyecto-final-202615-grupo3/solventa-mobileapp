package com.solventa.app.data.activation

data class LivenessChallenge(
    val sessionId: String,
    val expiresAt: String,
    val gesture: String,
    val nonce: String,
    val remainingAttempts: Int,
)

sealed interface ActivationStartResult {
    data class ChallengeReady(val challenge: LivenessChallenge) : ActivationStartResult
    data object DeviceAlreadyLinked : ActivationStartResult
    data object NetworkError : ActivationStartResult
    data object ServiceUnavailable : ActivationStartResult
}

interface ActivationRepository {
    suspend fun start(
        email: String,
        installationId: String,
        platform: String,
        model: String,
        osVersion: String,
    ): ActivationStartResult
}
