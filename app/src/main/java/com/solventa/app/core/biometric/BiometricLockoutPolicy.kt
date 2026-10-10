package com.solventa.app.core.biometric

sealed interface FailureOutcome {
    data class Retry(val attempt: Int, val maxAttempts: Int) : FailureOutcome
    data object LockedOut : FailureOutcome
}

/**
 * Política de HU3.1.2: tras 3 intentos fallidos consecutivos se bloquea la biometría
 * durante 5 minutos. El contador y el bloqueo se persisten, así que sobreviven al cierre
 * de la app, y se reinician tras un acceso biométrico exitoso.
 */
class BiometricLockoutPolicy(
    private val settings: BiometricSettings,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun remainingLockoutMillis(): Long = (settings.lockoutUntilMillis - clock()).coerceAtLeast(0)

    fun isLockedOut(): Boolean = remainingLockoutMillis() > 0

    fun registerFailure(): FailureOutcome {
        val attempt = settings.failedAttempts + 1
        if (attempt >= MAX_ATTEMPTS) {
            lockNow()
            return FailureOutcome.LockedOut
        }
        settings.failedAttempts = attempt
        return FailureOutcome.Retry(attempt, MAX_ATTEMPTS)
    }

    /** El sistema operativo aplicó su propio bloqueo; se respeta con la misma duración. */
    fun registerSystemLockout() = lockNow()

    fun registerSuccess() {
        settings.failedAttempts = 0
        settings.lockoutUntilMillis = 0
    }

    private fun lockNow() {
        settings.failedAttempts = 0
        settings.lockoutUntilMillis = clock() + LOCKOUT_MILLIS
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        const val LOCKOUT_MILLIS = 5 * 60 * 1000L
    }
}
