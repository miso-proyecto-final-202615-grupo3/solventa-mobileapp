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

sealed interface ActivationVerificationResult {
    data object Approved : ActivationVerificationResult
    data class Rejected(val reason: String?, val remainingAttempts: Int?) : ActivationVerificationResult
    data object NetworkError : ActivationVerificationResult
    data object ServiceUnavailable : ActivationVerificationResult
}

interface ActivationRepository {
    suspend fun start(
        accessToken: String,
        installationId: String,
        platform: String,
        model: String,
        osVersion: String,
    ): ActivationStartResult

    suspend fun verify(accessToken: String, challenge: LivenessChallenge, frames: List<ByteArray>): ActivationVerificationResult
}
