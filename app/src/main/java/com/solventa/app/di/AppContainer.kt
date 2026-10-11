package com.solventa.app.di

import android.content.Context
import com.solventa.app.BuildConfig
import com.solventa.app.core.biometric.AndroidBiometricAvailabilityChecker
import com.solventa.app.core.biometric.AppLockManager
import com.solventa.app.core.biometric.BiometricKeyManager
import com.solventa.app.core.biometric.BiometricLockoutPolicy
import com.solventa.app.core.biometric.BiometricSettings
import com.solventa.app.core.biometric.BiometricVault
import com.solventa.app.core.biometric.KeystoreBiometricVault
import com.solventa.app.core.biometric.SharedPreferencesBiometricSettings
import com.solventa.app.data.auth.AuthRepository
import com.solventa.app.data.auth.MockAuthRepository
import com.solventa.app.data.auth.Session
import com.solventa.app.data.session.InMemorySessionStore
import com.solventa.app.data.session.SessionStore

/** Contenedor de dependencias manual; suficiente para el tamaño actual de la app. */
class AppContainer(context: Context) {
    val authRepository: AuthRepository =
        if (BuildConfig.USE_MOCK_BACKEND) {
            MockAuthRepository()
        } else {
            TODO("Implementación remota contra el BFF Móvil pendiente de contratos")
        }

    val sessionStore: SessionStore = InMemorySessionStore()

    val biometricSettings: BiometricSettings = SharedPreferencesBiometricSettings(context)

    val biometricVault: BiometricVault = KeystoreBiometricVault(
        keyManager = BiometricKeyManager(),
        settings = biometricSettings,
        availabilityChecker = AndroidBiometricAvailabilityChecker(context),
    )

    val biometricLockoutPolicy = BiometricLockoutPolicy(biometricSettings)

    val appLockManager = AppLockManager(biometricVault, sessionStore)

    /** Tras vincular el dispositivo se ofrece activar la biometría, salvo que ya esté activa o la haya rechazado. */
    fun shouldOfferBiometric(session: Session): Boolean =
        session.cliente.dispositivoVinculado && !biometricVault.isEnabled() && !biometricSettings.offerDeclined

    /**
     * Cerrar sesión solo limpia la sesión en memoria. El acceso biométrico queda configurado:
     * se desactiva desde Perfil > Seguridad, o al borrar los datos o desinstalar la app (HU3.1.2).
     */
    fun logout() {
        sessionStore.clear()
    }
}
