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
import com.solventa.app.data.claims.ClaimsRepository
import com.solventa.app.data.claims.MockClaimsRepository
import com.solventa.app.data.claims.RemoteClaimsRepository
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
import com.solventa.app.core.location.AddressResolver
import com.solventa.app.core.location.FusedLocationProvider
import com.solventa.app.core.location.GeocoderAddressResolver
import com.solventa.app.core.location.LocationProvider
import com.solventa.app.core.media.EvidenceFileStore
import com.solventa.app.core.media.PhotoCompressor
import com.solventa.app.core.media.VideoInspector
import com.solventa.app.data.claims.EvidenceRules
import com.solventa.app.data.claims.evidence.EvidenceRepository
import com.solventa.app.data.claims.evidence.EvidenceUploadQueue
import com.solventa.app.data.claims.evidence.FakeStorageUploader
import com.solventa.app.data.claims.evidence.MockEvidenceRepository
import com.solventa.app.data.claims.evidence.PendingEvidenceStore
import com.solventa.app.data.claims.evidence.PresignedPostUploader
import com.solventa.app.data.claims.evidence.RemoteEvidenceRepository
import com.solventa.app.data.claims.evidence.RetryPolicy
import com.solventa.app.data.claims.evidence.StorageUploader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import com.solventa.app.core.crypto.KeystoreLocalCipher
import com.solventa.app.core.crypto.LocalCipher
import com.solventa.app.data.policies.MockPolicyRepository
import com.solventa.app.data.policies.PolicyRepository
import com.solventa.app.data.policies.RemotePolicyRepository
import com.solventa.app.data.policies.local.CertificateStore
import com.solventa.app.data.policies.local.EncryptedFileCertificateStore
import com.solventa.app.data.policies.local.EncryptedFileWalletStore
import com.solventa.app.data.policies.local.WalletStore
import com.solventa.app.data.policies.sync.AndroidConnectivityMonitor
import com.solventa.app.data.policies.sync.ConnectivityMonitor
import com.solventa.app.data.policies.sync.WalletSync
import java.time.Clock

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
     * Consentimiento biométrico (HU3.1.3): real contra el BFF Móvil; simulado solo junto con el backend simulado
     * o si se pide con `solventa.useMockConsent=true`.
     */
    val biometricConsentRepository: BiometricConsentRepository =
        if (BuildConfig.USE_MOCK_CONSENT) {
            MockBiometricConsentRepository(currentEmail)
        } else {
            RemoteBiometricConsentRepository(BuildConfig.BFF_MOBILE_URL)
        }

    /** Siniestros (HU4.1.1): simulados con el backend simulado o con `solventa.useMockClaims=true`. */
    val claimsRepository: ClaimsRepository =
        if (BuildConfig.USE_MOCK_CLAIMS) {
            MockClaimsRepository()
        } else {
            RemoteClaimsRepository(
                baseUrl = BuildConfig.BFF_MOBILE_URL,
                demoEmail = BuildConfig.DEMO_ACTIVATION_EMAIL.takeIf { it.isNotBlank() },
            )
        }

    /** Reloj del sistema en la zona del teléfono: la fecha del siniestro es la local del cliente. */
    val clock: Clock = Clock.systemDefaultZone()

    // --- HU3.2.1 y HU3.2.2 · Billetera de pólizas sin conexión ---------------------------------

    /** Carpeta de caché de la app: ahí se descifra, de forma temporal, el certificado que se abre. */
    val cacheDir: File = context.cacheDir

    private val localCipher: LocalCipher = KeystoreLocalCipher()
    private val walletStore: WalletStore =
        EncryptedFileWalletStore(File(context.filesDir, "billetera/polizas.bin"), localCipher)
    val certificateStore: CertificateStore =
        EncryptedFileCertificateStore(File(context.filesDir, "billetera/certificados"), localCipher)

    val policyRepository: PolicyRepository =
        if (BuildConfig.USE_MOCK_BACKEND) {
            MockPolicyRepository()
        } else {
            RemotePolicyRepository(
                baseUrl = BuildConfig.BFF_MOBILE_URL,
                demoEmail = BuildConfig.DEMO_ACTIVATION_EMAIL.takeIf { it.isNotBlank() },
            )
        }

    val connectivityMonitor: ConnectivityMonitor = AndroidConnectivityMonitor(context)

    /** Vive con la app: sincroniza al haber sesión y al recuperar la conexión. */
    val walletSync = WalletSync(
        repository = policyRepository,
        store = walletStore,
        certificates = certificateStore,
        sessionStore = sessionStore,
        authRepository = authRepository,
        connectivity = connectivityMonitor,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    // --- HU4.1.2 · Evidencias y ubicación -----------------------------------------------------

    val evidenceFileStore = EvidenceFileStore(context)
    val photoCompressor = PhotoCompressor()
    val videoInspector = VideoInspector()
    val locationProvider: LocationProvider = FusedLocationProvider(context, clock)
    val addressResolver: AddressResolver = GeocoderAddressResolver(context)

    /** Evidencias: simuladas con los siniestros simulados; si no, contra el BFF y subida directa a S3. */
    val evidenceRepository: EvidenceRepository =
        if (BuildConfig.USE_MOCK_CLAIMS) {
            MockEvidenceRepository(rulesFor = { (claimsRepository as? MockClaimsRepository)?.evidenceRulesFor(it) ?: EvidenceRules(5, 1) })
        } else {
            RemoteEvidenceRepository(
                baseUrl = BuildConfig.BFF_MOBILE_URL,
                demoEmail = BuildConfig.DEMO_ACTIVATION_EMAIL.takeIf { it.isNotBlank() },
            )
        }

    private val storageUploader: StorageUploader =
        if (BuildConfig.USE_MOCK_CLAIMS) FakeStorageUploader() else PresignedPostUploader()

    /** Vive con la app: la subida sigue aunque el usuario cambie de pantalla (decisión A2). */
    val evidenceUploadQueue = EvidenceUploadQueue(
        repository = evidenceRepository,
        uploader = storageUploader,
        sessionStore = sessionStore,
        store = PendingEvidenceStore(File(evidenceFileStore.directory, EvidenceFileStore.PENDING_FILE)),
        deleteFile = evidenceFileStore::delete,
        policy = RetryPolicy(),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

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
