package com.solventa.app.core.biometric

import android.content.Context
import androidx.core.content.edit

/** Sesión cifrada con la llave biométrica, codificada en Base64. */
data class SealedPayload(val ciphertext: String, val iv: String)

/**
 * Estado local del acceso biométrico. No contiene datos biométricos:
 * la verificación la hace el sistema operativo (criterio de HU3.1.2).
 */
interface BiometricSettings {
    var enabled: Boolean
    /** El usuario eligió "Ahora no" en la oferta de activación. */
    var offerDeclined: Boolean
    var failedAttempts: Int
    var lockoutUntilMillis: Long
    var sealedSession: SealedPayload?
}

class SharedPreferencesBiometricSettings(context: Context) : BiometricSettings {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    override var offerDeclined: Boolean
        get() = prefs.getBoolean(KEY_OFFER_DECLINED, false)
        set(value) = prefs.edit { putBoolean(KEY_OFFER_DECLINED, value) }

    override var failedAttempts: Int
        get() = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
        set(value) = prefs.edit { putInt(KEY_FAILED_ATTEMPTS, value) }

    override var lockoutUntilMillis: Long
        get() = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        set(value) = prefs.edit { putLong(KEY_LOCKOUT_UNTIL, value) }

    override var sealedSession: SealedPayload?
        get() {
            val ciphertext = prefs.getString(KEY_SESSION_CIPHERTEXT, null) ?: return null
            val iv = prefs.getString(KEY_SESSION_IV, null) ?: return null
            return SealedPayload(ciphertext, iv)
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove(KEY_SESSION_CIPHERTEXT)
                remove(KEY_SESSION_IV)
            } else {
                putString(KEY_SESSION_CIPHERTEXT, value.ciphertext)
                putString(KEY_SESSION_IV, value.iv)
            }
        }

    private companion object {
        const val PREFS_NAME = "solventa_biometric"
        const val KEY_ENABLED = "enabled"
        const val KEY_OFFER_DECLINED = "offer_declined"
        const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        const val KEY_LOCKOUT_UNTIL = "lockout_until"
        const val KEY_SESSION_CIPHERTEXT = "session_ciphertext"
        const val KEY_SESSION_IV = "session_iv"
    }
}
