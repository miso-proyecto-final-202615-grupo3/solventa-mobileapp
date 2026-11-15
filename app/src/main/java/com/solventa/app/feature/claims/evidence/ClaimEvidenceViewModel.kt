package com.solventa.app.feature.claims.evidence

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.solventa.app.core.location.AddressResolver
import com.solventa.app.core.location.LocationFix
import com.solventa.app.core.location.LocationProvider
import com.solventa.app.core.location.LocationResult
import com.solventa.app.core.permissions.PermissionStatus
import com.solventa.app.data.claims.ClaimResult
import com.solventa.app.data.claims.ClaimsRepository
import com.solventa.app.data.claims.EvidenceRules
import com.solventa.app.data.claims.PoliciesResult
import com.solventa.app.data.claims.ReportedClaim
import com.solventa.app.data.claims.evidence.EvidenceKind
import com.solventa.app.data.claims.evidence.EvidenceOrigin
import com.solventa.app.data.claims.evidence.EvidenceRepository
import com.solventa.app.data.claims.evidence.EvidenceUploadQueue
import com.solventa.app.data.claims.evidence.EvidenceUploadState
import com.solventa.app.data.claims.evidence.LocalEvidence
import com.solventa.app.data.claims.evidence.LocationSaveResult
import com.solventa.app.data.claims.evidence.RemoteEvidence
import com.solventa.app.data.session.SessionStore
import com.solventa.app.di.AppContainer
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

sealed interface LocationUi {
    data object Idle : LocationUi
    data object Locating : LocationUi
    data class Attached(val fix: LocationFix, val address: String?) : LocationUi
    data object Denied : LocationUi
    data object PermanentlyDenied : LocationUi
    data object Disabled : LocationUi
    data object Unavailable : LocationUi
    data object SaveFailed : LocationUi
}

enum class EvidenceMessage {
    PHOTO_UNREADABLE,
    PHOTO_TOO_LARGE,
    VIDEO_TOO_LONG,
    VIDEO_TOO_LARGE,
    VIDEO_UNSUPPORTED,
    LIMIT_REACHED,
    CAMERA_DENIED,
    CAMERA_PERMANENTLY_DENIED,
    LOAD_FAILED,
}

sealed interface EvidenceDestination {
    data object Home : EvidenceDestination
    data object Login : EvidenceDestination
}

/** Lo que se pide al tocar un botón de captura, una vez concedido el permiso de cámara. */
enum class CaptureRequest { PHOTO, VIDEO }

data class ClaimEvidenceUiState(
    val loading: Boolean = true,
    val claim: ReportedClaim? = null,
    val rules: EvidenceRules = DEFAULT_RULES,
    /** Evidencias del servidor que no están en la cola local (p. ej. enviadas en otra sesión). */
    val uploaded: List<RemoteEvidence> = emptyList(),
    val local: List<LocalEvidence> = emptyList(),
    val location: LocationUi = LocationUi.Idle,
    val remainingPhotos: Int = DEFAULT_RULES.maxPhotos,
    val remainingVideos: Int = DEFAULT_RULES.maxVideos,
    val processing: Boolean = false,
    val message: EvidenceMessage? = null,
    val pendingCapture: CaptureRequest? = null,
    val confirmLeaveWithPending: Boolean = false,
    val destination: EvidenceDestination? = null,
    /** El usuario tocó "Terminar" sin envíos en curso: se muestra la confirmación con el resumen. */
    val completed: Boolean = false,
) {
    val pendingCount: Int get() = local.count { it.state in SENDING }

    /** Evidencias ya guardadas en el servidor (las de esta sesión y las enviadas antes). */
    fun sentCount(kind: EvidenceKind): Int =
        uploaded.count { it.kind == kind && it.state == "CARGADA" } +
            local.count { it.kind == kind && it.state == EvidenceUploadState.UPLOADED }

    /** Fallidas o rechazadas: se pueden reintentar o quitar desde "Agregar otra evidencia". */
    val notSentCount: Int get() = local.count { it.state == EvidenceUploadState.FAILED || it.state == EvidenceUploadState.REJECTED }

    val locationAttached: Boolean get() = location is LocationUi.Attached || claim?.location != null
}

internal val DEFAULT_RULES = EvidenceRules(5, 1)
internal val SENDING = setOf(
    EvidenceUploadState.QUEUED,
    EvidenceUploadState.REQUESTING,
    EvidenceUploadState.UPLOADING,
    EvidenceUploadState.CONFIRMING,
)

/**
 * Adjuntar fotos, videos y ubicación a un siniestro ya creado (HU4.1.2). La subida la hace la cola de la
 * aplicación ([EvidenceUploadQueue]); aquí se capturan, se preparan y se muestra el estado de cada una.
 */
class ClaimEvidenceViewModel(
    private val claimId: String,
    private val claimsRepository: ClaimsRepository,
    private val evidenceRepository: EvidenceRepository,
    private val queue: EvidenceUploadQueue,
    private val locationProvider: LocationProvider,
    private val addressResolver: AddressResolver?,
    private val media: EvidenceMedia,
    private val sessionStore: SessionStore,
    private val clock: Clock,
    private val hasLocationPermission: () -> Boolean,
    private val locationTimeoutMs: Long = 10_000,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(ClaimEvidenceUiState())
    val uiState: StateFlow<ClaimEvidenceUiState> = _uiState.asStateFlow()

    // Archivo donde escribe la cámara del sistema. Va en SavedStateHandle: si Android cierra la app mientras la
    // cámara está abierta, al volver el resultado llega a un ViewModel nuevo que debe encontrar el archivo.
    private var captureFile: File?
        get() = savedState.get<String>(KEY_CAPTURE_FILE)?.let(::File)
        set(value) {
            savedState[KEY_CAPTURE_FILE] = value?.absolutePath
        }

    init {
        viewModelScope.launch {
            queue.itemsFor(claimId).collect { local -> _uiState.update { recompute(it.copy(local = local)) } }
        }
        load()
    }

    fun load() {
        val token = accessToken() ?: return goToLogin()
        _uiState.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            val claimCall = async { claimsRepository.claim(token, claimId, correlationId()) }
            val policiesCall = async { claimsRepository.eligiblePolicies(token, correlationId()) }
            when (val result = claimCall.await()) {
                is ClaimResult.Found -> {
                    val claim = result.claim
                    val rules = (policiesCall.await() as? PoliciesResult.Loaded)?.policies
                        ?.firstOrNull { it.id == claim.policyId }
                        ?.claimTypes?.firstOrNull { it.code == claim.typeCode }
                        ?.evidenceRules ?: DEFAULT_RULES
                    _uiState.update {
                        recompute(
                            it.copy(
                                loading = false,
                                claim = claim,
                                rules = rules,
                                uploaded = claim.evidences,
                                location = claim.location?.let { fix -> LocationUi.Attached(fix, null) } ?: it.location,
                            ),
                        )
                    }
                    if (claim.location == null && hasLocationPermission()) attachLocation()
                }
                ClaimResult.SessionExpired -> goToLogin()
                ClaimResult.NotFound, ClaimResult.NetworkError, ClaimResult.ServiceUnavailable ->
                    _uiState.update { it.copy(loading = false, message = EvidenceMessage.LOAD_FAILED) }
            }
        }
    }

    // --- Captura -----------------------------------------------------------------------------------

    /** El usuario tocó "Tomar foto" o "Grabar video": la pantalla pide el permiso de cámara. */
    fun requestCapture(request: CaptureRequest) {
        val state = _uiState.value
        val remaining = if (request == CaptureRequest.PHOTO) state.remainingPhotos else state.remainingVideos
        if (remaining <= 0) return _uiState.update { it.copy(message = EvidenceMessage.LIMIT_REACHED) }
        _uiState.update { it.copy(pendingCapture = request) }
    }

    fun onCameraPermission(status: PermissionStatus) {
        when (status) {
            PermissionStatus.GRANTED -> Unit // la pantalla abre la cámara con newCaptureTarget()
            PermissionStatus.DENIED -> _uiState.update { it.copy(pendingCapture = null, message = EvidenceMessage.CAMERA_DENIED) }
            PermissionStatus.PERMANENTLY_DENIED ->
                _uiState.update { it.copy(pendingCapture = null, message = EvidenceMessage.CAMERA_PERMANENTLY_DENIED) }
        }
    }

    /** Archivo privado donde la cámara del sistema escribe la foto o el video. */
    fun newCaptureTarget(kind: EvidenceKind): File = media.newCaptureFile(kind).also { captureFile = it }

    fun onCaptured(kind: EvidenceKind, success: Boolean) {
        val file = captureFile
        captureFile = null
        _uiState.update { it.copy(pendingCapture = null) }
        if (file == null || !success || file.length() == 0L) {
            media.discard(file) // captura cancelada: sin mensaje
            return
        }
        prepare(EvidenceOrigin.CAMERA) {
            if (kind == EvidenceKind.PHOTO) media.preparePhoto(file) else media.prepareVideo(file)
        }
    }

    fun onGalleryPicked(uris: List<String>) {
        uris.forEach { uri -> prepare(EvidenceOrigin.GALLERY) { media.importFromGallery(uri) } }
    }

    private fun prepare(origin: EvidenceOrigin, block: suspend () -> PreparedMedia) {
        _uiState.update { it.copy(processing = true) }
        viewModelScope.launch {
            // Tras un cierre del proceso, la foto llega antes de que termine de cargar el siniestro: se espera.
            val claim = _uiState.first { !it.loading }.claim
            if (claim == null) {
                _uiState.update { it.copy(processing = false) }
                return@launch // no se pudo cargar: ya se muestra LOAD_FAILED
            }
            when (val prepared = block()) {
                is PreparedMedia.Ready -> {
                    val state = _uiState.value
                    val remaining = if (prepared.kind == EvidenceKind.PHOTO) state.remainingPhotos else state.remainingVideos
                    if (remaining <= 0) {
                        media.discard(prepared.file)
                        _uiState.update { it.copy(message = EvidenceMessage.LIMIT_REACHED) }
                    } else {
                        queue.enqueue(claim.id, claim.number, prepared.file, prepared.kind, origin, OffsetDateTime.now(clock))
                    }
                }
                is PreparedMedia.Failed -> _uiState.update { it.copy(message = prepared.message) }
            }
            _uiState.update { it.copy(processing = false) }
        }
    }

    fun retry(localId: String) = queue.retry(localId)

    fun remove(localId: String) = queue.remove(localId)

    // --- Ubicación ---------------------------------------------------------------------------------

    fun onLocationPermission(status: PermissionStatus) {
        when (status) {
            PermissionStatus.GRANTED -> attachLocation()
            PermissionStatus.DENIED -> _uiState.update { it.copy(location = LocationUi.Denied) }
            PermissionStatus.PERMANENTLY_DENIED -> _uiState.update { it.copy(location = LocationUi.PermanentlyDenied) }
        }
    }

    fun attachLocation() {
        val claim = _uiState.value.claim ?: return
        if (_uiState.value.location == LocationUi.Locating) return
        _uiState.update { it.copy(location = LocationUi.Locating) }
        viewModelScope.launch {
            val fix = when (val result = locationProvider.current(locationTimeoutMs)) {
                is LocationResult.Found -> result.fix
                LocationResult.PermissionDenied -> return@launch _uiState.update { it.copy(location = LocationUi.Denied) }
                LocationResult.Disabled -> return@launch _uiState.update { it.copy(location = LocationUi.Disabled) }
                LocationResult.Unavailable, LocationResult.Timeout ->
                    return@launch _uiState.update { it.copy(location = LocationUi.Unavailable) }
            }
            val token = accessToken() ?: return@launch goToLogin()
            when (evidenceRepository.saveLocation(token, claim.id, fix, correlationId())) {
                LocationSaveResult.Saved -> {
                    _uiState.update { it.copy(location = LocationUi.Attached(fix, null)) }
                    val address = addressResolver?.resolve(fix)
                    if (address != null) _uiState.update { it.copy(location = LocationUi.Attached(fix, address)) }
                }
                LocationSaveResult.SessionExpired -> goToLogin()
                LocationSaveResult.ClaimClosed, LocationSaveResult.NetworkError, LocationSaveResult.ServiceUnavailable ->
                    _uiState.update { it.copy(location = LocationUi.SaveFailed) }
            }
        }
    }

    // --- Salida ------------------------------------------------------------------------------------

    fun finish() {
        if (_uiState.value.pendingCount > 0) {
            _uiState.update { it.copy(confirmLeaveWithPending = true) }
        } else {
            _uiState.update { it.copy(completed = true) }
        }
    }

    /** Desde la confirmación: vuelve a la pantalla de evidencias del mismo siniestro. */
    fun addMore() = _uiState.update { it.copy(completed = false) }

    fun goHome() = _uiState.update { it.copy(destination = EvidenceDestination.Home) }

    fun confirmLeave() = _uiState.update { it.copy(confirmLeaveWithPending = false, destination = EvidenceDestination.Home) }

    fun dismissLeave() = _uiState.update { it.copy(confirmLeaveWithPending = false) }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    fun consumeDestination() = _uiState.update { it.copy(destination = null) }

    /** Cupos: enviadas en el servidor + locales que no fueron rechazadas (el backend tiene la última palabra). */
    private fun recompute(state: ClaimEvidenceUiState): ClaimEvidenceUiState {
        val localRemoteIds = state.local.mapNotNull { it.remoteId }.toSet()
        val uploaded = (state.claim?.evidences ?: state.uploaded).filter {
            it.id !in localRemoteIds && it.state != "RECHAZADA"
        }
        fun used(kind: EvidenceKind) = uploaded.count { it.kind == kind } +
            state.local.count { it.kind == kind && it.state != EvidenceUploadState.REJECTED }
        return state.copy(
            uploaded = uploaded,
            remainingPhotos = (state.rules.maxPhotos - used(EvidenceKind.PHOTO)).coerceAtLeast(0),
            remainingVideos = (state.rules.maxVideos - used(EvidenceKind.VIDEO)).coerceAtLeast(0),
        )
    }

    private fun accessToken(): String? = sessionStore.session.value?.accessToken?.takeIf { it.isNotBlank() }

    private fun goToLogin() = _uiState.update { it.copy(loading = false, destination = EvidenceDestination.Login) }

    private fun correlationId() = UUID.randomUUID().toString()

    companion object {
        private const val KEY_CAPTURE_FILE = "evidence.captureFile"

        fun factory(container: AppContainer, claimId: String, hasLocationPermission: () -> Boolean): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ClaimEvidenceViewModel(
                        claimId = claimId,
                        claimsRepository = container.claimsRepository,
                        evidenceRepository = container.evidenceRepository,
                        queue = container.evidenceUploadQueue,
                        locationProvider = container.locationProvider,
                        addressResolver = container.addressResolver,
                        media = AndroidEvidenceMedia(container.evidenceFileStore, container.photoCompressor, container.videoInspector),
                        sessionStore = container.sessionStore,
                        clock = container.clock,
                        hasLocationPermission = hasLocationPermission,
                        savedState = createSavedStateHandle(),
                    )
                }
            }
    }
}
