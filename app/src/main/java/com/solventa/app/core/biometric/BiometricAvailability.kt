package com.solventa.app.core.biometric

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG

enum class BiometricAvailability {
    AVAILABLE,

    /** Hay sensor, pero el usuario no tiene huella ni rostro configurados en el sistema. */
    NONE_ENROLLED,

    /** El teléfono no tiene sensor biométrico fuerte o no está disponible. */
    UNAVAILABLE,
}

fun interface BiometricAvailabilityChecker {
    fun check(): BiometricAvailability
}

class AndroidBiometricAvailabilityChecker(private val context: Context) : BiometricAvailabilityChecker {
    override fun check(): BiometricAvailability =
        when (BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            else -> BiometricAvailability.UNAVAILABLE
        }
}
