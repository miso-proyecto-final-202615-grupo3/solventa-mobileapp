package com.solventa.app.feature.claims.evidence

import com.solventa.app.MainDispatcherRule
import com.solventa.app.core.biometric.testSession
import com.solventa.app.core.location.LocationFix
import com.solventa.app.core.location.LocationResult
import com.solventa.app.core.location.LocationSource
import com.solventa.app.core.permissions.PermissionStatus
import com.solventa.app.data.claims.ClaimDraft
import com.solventa.app.data.claims.ClaimResult
import com.solventa.app.data.claims.ClaimStatus
import com.solventa.app.data.claims.ClaimsRepository
import com.solventa.app.data.claims.MockClaimsRepository
import com.solventa.app.data.claims.PoliciesResult
import com.solventa.app.data.claims.ReportResult
import com.solventa.app.data.claims.ReportedClaim
import com.solventa.app.data.claims.evidence.EvidenceKind
import com.solventa.app.data.claims.evidence.EvidenceUploadQueue
import com.solventa.app.data.claims.evidence.EvidenceUploadState
import com.solventa.app.data.claims.evidence.FakeEvidenceRepository
import com.solventa.app.data.claims.evidence.FakeUploader
import com.solventa.app.data.claims.evidence.LocationSaveResult
import com.solventa.app.data.claims.evidence.PendingEvidenceStore
import com.solventa.app.data.claims.evidence.RemoteEvidence
import com.solventa.app.data.claims.evidence.RetryPolicy
import com.solventa.app.data.session.InMemorySessionStore
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ClaimEvidenceViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val travel = MockClaimsRepository.POLICIES.first()
    private val fix = LocationFix(4.65, -74.08, 12f, Instant.parse("2026-11-10T19:00:00Z"), LocationSource.GPS)

    private fun claim(type: String = "EQUIPAJE", evidences: List<RemoteEvidence> = emptyList(), location: LocationFix? = null) = ReportedClaim(
        id = "c-1", number = "SIN-2026-ABCDEF01", status = ClaimStatus.REPORTED, policyId = travel.id,
        policyNumber = travel.number, typeCode = type, occurredAt = Instant.parse("2026-11-10T14:00:00Z"),
        reportedAt = Instant.parse("2026-11-10T15:00:00Z"), location = location, evidences = evidences,
    )

    private inner class FakeClaims(var result: ClaimResult) : ClaimsRepository {
        /** Si se asigna, la consulta del siniestro espera hasta completarla (carga lenta tras un cierre del proceso). */
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun eligiblePolicies(accessToken: String, correlationId: String) = PoliciesResult.Loaded(MockClaimsRepository.POLICIES)
        override suspend fun report(accessToken: String, draft: ClaimDraft, idempotencyKey: String, correlationId: String) = ReportResult.ServiceUnavailable
        override suspend fun claim(accessToken: String, claimId: String, correlationId: String): ClaimResult {
            gate?.await()
            return result
        }
    }

    private inner class FakeMedia : EvidenceMedia {
        var nextPrepared: PreparedMedia? = null
        val discarded = mutableListOf<File?>()
        override fun newCaptureFile(kind: EvidenceKind): File = tmp.newFile()
        override suspend fun preparePhoto(raw: File) = nextPrepared ?: PreparedMedia.Ready(raw, EvidenceKind.PHOTO)
        override suspend fun prepareVideo(raw: File) = nextPrepared ?: PreparedMedia.Ready(raw, EvidenceKind.VIDEO)
        override suspend fun importFromGallery(uri: String) =
            nextPrepared ?: PreparedMedia.Ready(tmp.newFile().apply { writeBytes(ByteArray(10)) }, EvidenceKind.PHOTO)
        override fun discard(file: File?) {
            discarded += file
        }
    }

    private val sessionStore = InMemorySessionStore().apply { save(testSession()) }
    private val evidenceRepository = FakeEvidenceRepository()
    private val media = FakeMedia()
    private var locationResult: LocationResult = LocationResult.Found(fix)
    private var permission = false

    private fun queue() = EvidenceUploadQueue(
        repository = evidenceRepository,
        uploader = FakeUploader(),
        sessionStore = sessionStore,
        store = PendingEvidenceStore(File(tmp.root, "p.json"), UnconfinedTestDispatcher()),
        deleteFile = {},
        policy = RetryPolicy(baseDelayMs = 0),
        scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
    )

    private fun viewModel(
        claimResult: ClaimResult = ClaimResult.Found(claim()),
        queue: EvidenceUploadQueue = queue(),
        claims: FakeClaims = FakeClaims(claimResult),
        savedState: SavedStateHandle = SavedStateHandle(),
    ) = ClaimEvidenceViewModel(
        claimId = "c-1",
        claimsRepository = claims,
        evidenceRepository = evidenceRepository,
        queue = queue,
        locationProvider = { locationResult },
        addressResolver = { "Bogotá, Colombia" },
        media = media,
        sessionStore = sessionStore,
        clock = Clock.fixed(Instant.parse("2026-11-10T20:00:00Z"), ZoneOffset.ofHours(-5)),
        hasLocationPermission = { permission },
        savedState = savedState,
    )

    private fun ClaimEvidenceViewModel.capturePhoto() {
        requestCapture(CaptureRequest.PHOTO)
        onCameraPermission(PermissionStatus.GRANTED)
        newCaptureTarget(EvidenceKind.PHOTO).writeBytes(ByteArray(50))
        onCaptured(EvidenceKind.PHOTO, success = true)
    }

    @Test
    fun `carga el siniestro con las reglas de su tipo`() {
        val state = viewModel().uiState.value

        assertFalse(state.loading)
        assertEquals("SIN-2026-ABCDEF01", state.claim?.number)
        assertEquals(5, state.remainingPhotos)
        assertEquals(1, state.remainingVideos)
        assertTrue(state.rules.allowsVideo)
    }

    @Test
    fun `tipo sin video oculta el video`() {
        val state = viewModel(ClaimResult.Found(claim(type = "GASTO_MEDICO"))).uiState.value

        assertFalse(state.rules.allowsVideo)
        assertEquals(0, state.remainingVideos)
    }

    @Test
    fun `una foto tomada se envia y descuenta el cupo`() {
        val vm = viewModel()

        vm.capturePhoto()

        val state = vm.uiState.value
        assertEquals(EvidenceUploadState.UPLOADED, state.local.single().state)
        assertEquals(4, state.remainingPhotos)
        assertNull(state.pendingCapture)
    }

    @Test
    fun `las evidencias ya enviadas cuentan para el cupo`() {
        val sent = (1..5).map { RemoteEvidence("r$it", EvidenceKind.PHOTO, "CARGADA", 10, Instant.EPOCH, Instant.EPOCH) }
        val vm = viewModel(ClaimResult.Found(claim(evidences = sent)))

        vm.requestCapture(CaptureRequest.PHOTO)

        assertEquals(0, vm.uiState.value.remainingPhotos)
        assertEquals(EvidenceMessage.LIMIT_REACHED, vm.uiState.value.message)
        assertNull(vm.uiState.value.pendingCapture)
    }

    @Test
    fun `la foto sobrevive si Android cierra la app con la camara abierta`() {
        val savedState = SavedStateHandle()
        val before = viewModel(savedState = savedState)
        before.requestCapture(CaptureRequest.PHOTO)
        before.newCaptureTarget(EvidenceKind.PHOTO).writeBytes(ByteArray(50))

        // Proceso nuevo: otro ViewModel con el estado guardado, y el siniestro aún cargando cuando llega la foto.
        val claims = FakeClaims(ClaimResult.Found(claim())).apply { gate = CompletableDeferred() }
        val after = viewModel(claims = claims, savedState = savedState)
        after.onCaptured(EvidenceKind.PHOTO, success = true)
        assertTrue(after.uiState.value.local.isEmpty())

        claims.gate!!.complete(Unit)

        assertEquals(EvidenceUploadState.UPLOADED, after.uiState.value.local.single().state)
        assertEquals(4, after.uiState.value.remainingPhotos)
    }

    @Test
    fun `captura cancelada se descarta sin mensaje`() {
        val vm = viewModel()
        vm.requestCapture(CaptureRequest.PHOTO)
        val file = vm.newCaptureTarget(EvidenceKind.PHOTO)

        vm.onCaptured(EvidenceKind.PHOTO, success = false)

        assertTrue(file in media.discarded)
        assertNull(vm.uiState.value.message)
        assertTrue(vm.uiState.value.local.isEmpty())
    }

    @Test
    fun `foto ilegible o video largo muestran su mensaje`() {
        val vm = viewModel()
        media.nextPrepared = PreparedMedia.Failed(EvidenceMessage.VIDEO_TOO_LONG)

        vm.onGalleryPicked(listOf("content://video"))

        assertEquals(EvidenceMessage.VIDEO_TOO_LONG, vm.uiState.value.message)
        vm.dismissMessage()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `permiso de camara negado`() {
        val vm = viewModel()
        vm.requestCapture(CaptureRequest.PHOTO)

        vm.onCameraPermission(PermissionStatus.PERMANENTLY_DENIED)

        assertEquals(EvidenceMessage.CAMERA_PERMANENTLY_DENIED, vm.uiState.value.message)
        assertNull(vm.uiState.value.pendingCapture)
    }

    @Test
    fun `con permiso de ubicacion se adjunta sola al entrar`() {
        permission = true

        val state = viewModel().uiState.value

        assertEquals(LocationUi.Attached(fix, "Bogotá, Colombia"), state.location)
        assertEquals(listOf(fix), evidenceRepository.savedLocations)
    }

    @Test
    fun `sin permiso queda inactiva y los estados de error`() {
        val vm = viewModel()
        assertEquals(LocationUi.Idle, vm.uiState.value.location)

        vm.onLocationPermission(PermissionStatus.DENIED)
        assertEquals(LocationUi.Denied, vm.uiState.value.location)
        vm.onLocationPermission(PermissionStatus.PERMANENTLY_DENIED)
        assertEquals(LocationUi.PermanentlyDenied, vm.uiState.value.location)

        locationResult = LocationResult.Disabled
        vm.attachLocation()
        assertEquals(LocationUi.Disabled, vm.uiState.value.location)

        locationResult = LocationResult.Timeout
        vm.attachLocation()
        assertEquals(LocationUi.Unavailable, vm.uiState.value.location)

        locationResult = LocationResult.Found(fix)
        evidenceRepository.locationResult = LocationSaveResult.NetworkError
        vm.attachLocation()
        assertEquals(LocationUi.SaveFailed, vm.uiState.value.location)
    }

    @Test
    fun `ubicacion ya guardada no se vuelve a pedir`() {
        permission = true

        val state = viewModel(ClaimResult.Found(claim(location = fix))).uiState.value

        assertEquals(LocationUi.Attached(fix, null), state.location)
        assertTrue(evidenceRepository.savedLocations.isEmpty())
    }

    @Test
    fun `terminar con envios pendientes pide confirmacion`() {
        sessionStore.clear() // la cola espera sesión: la foto queda pendiente
        val queue = queue()
        sessionStore.save(testSession())
        val vm = viewModel(queue = queue)
        sessionStore.clear()
        vm.capturePhoto()

        vm.finish()
        assertTrue(vm.uiState.value.confirmLeaveWithPending)
        vm.confirmLeave()

        assertEquals(EvidenceDestination.Home, vm.uiState.value.destination)
    }

    @Test
    fun `terminar sin pendientes va al inicio`() {
        val vm = viewModel()

        vm.finish()

        assertEquals(EvidenceDestination.Home, vm.uiState.value.destination)
    }

    @Test
    fun `error al cargar y sesion vencida`() {
        assertEquals(EvidenceMessage.LOAD_FAILED, viewModel(ClaimResult.NetworkError).uiState.value.message)
        assertEquals(EvidenceDestination.Login, viewModel(ClaimResult.SessionExpired).uiState.value.destination)
    }
}
