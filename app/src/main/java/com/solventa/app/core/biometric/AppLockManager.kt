package com.solventa.app.core.biometric

import android.os.SystemClock
import com.solventa.app.data.session.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Decide cuándo pedir la biometría antes de mostrar la cuenta: al abrir la app
 * y al volver de segundo plano tras más de 60 segundos (HU3.1.2).
 */
class AppLockManager(
    private val vault: BiometricVault,
    private val sessionStore: SessionStore,
    private val elapsedClock: () -> Long = SystemClock::elapsedRealtime,
) {
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt: Long? = null

    fun onColdStart() {
        if (vault.isEnabled()) _locked.value = true
    }

    fun onAppBackgrounded() {
        backgroundedAt = elapsedClock()
    }

    fun onAppForegrounded() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        val hasSession = sessionStore.session.value != null
        if (elapsedClock() - since > BACKGROUND_TIMEOUT_MILLIS && vault.isEnabled() && hasSession) {
            _locked.value = true
        }
    }

    /** Pide la biometría a demanda, por ejemplo desde "Entrar con huella o rostro" en el login. */
    fun lock() {
        if (vault.isEnabled()) _locked.value = true
    }

    fun unlock() {
        _locked.value = false
    }

    companion object {
        const val BACKGROUND_TIMEOUT_MILLIS = 60_000L
    }
}
