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
import com.solventa.app.core.device.AndroidDeviceInfoProvider
import com.solventa.app.core.device.DeviceInfoProvider
import com.solventa.app.core.device.InstallationIdProvider
import com.solventa.app.core.liveness.launcher.CaptureLauncherFactory
import com.solventa.app.core.liveness.launcher.OwnCaptureLauncher
import com.solventa.app.core.liveness.launcher.SdkDiditCaptureLauncher
import com.solventa.app.core.liveness.launcher.SimulatedDiditCaptureLauncher
import com.solventa.app.data.activation.ActivationRepository
import com.solventa.app.data.activation.MockActivationRepository
import com.solventa.app.data.activation.RemoteActivationRepository
import com.solventa.app.data.consent.BiometricConsentRepository
import com.solventa.app.data.consent.MockBiometricConsentRepository
import com.solventa.app.data.consent.RemoteBiometricConsentRepository
import com.solventa.app.core.device.SharedPreferencesInstallationIdProvider
import com.solventa.app.data.auth.AuthRepository
import com.solventa.app.data.auth.MockAuthRepository
import com.solventa.app.data.auth.RemoteAuthRepository
import com.solventa.app.data.auth.Session
import com.solventa.app.data.session.InMemorySessionStore
import com.solventa.app.data.session.SessionStore

/** Contenedor de dependencias manual; suficiente para el tamaño actual de la app. */
class AppContainer(context: Context) {
    val installationIdProvider: InstallationIdProvider = SharedPreferencesInstallationIdProvider(context)
    /** Backend simulado o BFF Móvil real, según la propiedad de Gradle `solventa.useMockBackend`. */
    val authRepository: AuthRepository =
        if (BuildConfig.USE_MOCK_BACKEND) {
            MockAuthRepository()
        } else {
            RemoteAuthRepository(baseUrl = BuildConfig.BFF_MOBILE_URL)
        }

    val sessionStore: SessionStore = InMemorySessionStore()

    val deviceInfoProvider: DeviceInfoProvider = AndroidDeviceInfoProvider(installationIdProvider)

    private val currentEmail: () -> String? = { sessionStore.session.value?.cliente?.correo }

    /** Activación (HU3.1.1): simulada o contra el BFF Móvil según `solventa.useMockBackend`. */
    val activationRepository: ActivationRepository =
        if (BuildConfig.USE_MOCK_BACKEND) {
            MockActivationRepository(currentEmail, captureType = BuildConfig.MOCK_CAPTURE_TYPE)
        } else {
            RemoteActivationRepository(
                baseUrl = BuildConfig.BFF_MOBILE_URL,
                demoEmail = BuildConfig.DEMO_ACTIVATION_EMAIL.takeIf { it.isNotBlank() },
            )
        }

    /**
     * Consentimiento biométrico simulado hasta que HU3.1.3 entregue el endpoint (excepción transitoria D5,
     * `solventa.useMockConsent`).
     */
    val biometricConsentRepository: BiometricConsentRepository =
        if (BuildConfig.USE_MOCK_CONSENT) {
            MockBiometricConsentRepository(currentEmail)
        } else {
            RemoteBiometricConsentRepository(BuildConfig.BFF_MOBILE_URL)
        }

    /** Estrategias de captura por tipo; Didit real solo con `solventa.diditSdkEnabled=true`. */
    val captureLauncherFactory = CaptureLauncherFactory(
        listOf(
            OwnCaptureLauncher,
            if (BuildConfig.DIDIT_SDK_ENABLED) SdkDiditCaptureLauncher(context) else SimulatedDiditCaptureLauncher(),
        ),
    )

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
