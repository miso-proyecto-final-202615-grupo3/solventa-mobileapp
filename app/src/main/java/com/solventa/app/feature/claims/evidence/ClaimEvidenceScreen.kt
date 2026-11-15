package com.solventa.app.feature.claims.evidence

import android.Manifest
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solventa.app.R
import com.solventa.app.core.designsystem.component.SolventaPrimaryButton
import com.solventa.app.core.media.CaptureVideoWithLimit
import com.solventa.app.core.media.rememberThumbnail
import com.solventa.app.core.permissions.PermissionStatus
import com.solventa.app.core.permissions.openAppSettings
import com.solventa.app.core.permissions.rememberPermissionRequester
import com.solventa.app.data.claims.evidence.EvidenceKind
import com.solventa.app.data.claims.evidence.EvidenceProblem
import com.solventa.app.data.claims.evidence.EvidenceUploadState
import com.solventa.app.data.claims.evidence.LocalEvidence
import com.solventa.app.data.claims.evidence.RemoteEvidence
import java.io.File
import java.util.Locale

object ClaimEvidenceTestTags {
    const val TAKE_PHOTO = "evidence_take_photo"
    const val PICK_PHOTOS = "evidence_pick_photos"
    const val RECORD_VIDEO = "evidence_record_video"
    const val PICK_VIDEO = "evidence_pick_video"
    const val LOCATION = "evidence_location"
    const val ITEM_PREFIX = "evidence_item_"
    const val RETRY_PREFIX = "evidence_retry_"
    const val REMOVE_PREFIX = "evidence_remove_"
    const val FINISH = "evidence_finish"
    const val MESSAGE = "evidence_message"
    const val DONE = "evidence_done"
    const val DONE_HOME = "evidence_done_home"
    const val DONE_ADD_MORE = "evidence_done_add_more"
}

private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
private const val GALLERY_MAX_ITEMS = 5

@Composable
fun ClaimEvidenceRoute(
    viewModel: ClaimEvidenceViewModel,
    uriFor: (File) -> Uri,
    onHome: () -> Unit,
    onLogin: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.destination) {
        when (state.destination) {
            EvidenceDestination.Home -> onHome()
            EvidenceDestination.Login -> onLogin()
            null -> return@LaunchedEffect
        }
        viewModel.consumeDestination()
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        viewModel.onCaptured(EvidenceKind.PHOTO, ok)
    }
    val recordVideo = rememberLauncherForActivityResult(CaptureVideoWithLimit()) { ok ->
        viewModel.onCaptured(EvidenceKind.VIDEO, ok)
    }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(GALLERY_MAX_ITEMS)) { uris ->
        viewModel.onGalleryPicked(uris.map(Uri::toString))
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onGalleryPicked(listOf(uri.toString()))
    }
    val pendingCapture by rememberUpdatedState(state.pendingCapture)
    val requestCamera = rememberPermissionRequester(arrayOf(Manifest.permission.CAMERA)) { status ->
        viewModel.onCameraPermission(status)
        if (status == PermissionStatus.GRANTED) {
            when (pendingCapture) {
                CaptureRequest.PHOTO -> takePicture.launch(uriFor(viewModel.newCaptureTarget(EvidenceKind.PHOTO)))
                CaptureRequest.VIDEO -> recordVideo.launch(uriFor(viewModel.newCaptureTarget(EvidenceKind.VIDEO)))
                null -> Unit
            }
        }
    }
    val requestLocation = rememberPermissionRequester(LOCATION_PERMISSIONS, viewModel::onLocationPermission)

    LaunchedEffect(state.pendingCapture) {
        if (state.pendingCapture != null) requestCamera()
    }
    BackHandler { if (state.completed) viewModel.goHome() else viewModel.finish() }

    if (state.completed) {
        ClaimEvidenceDoneScreen(state = state, onAddMore = viewModel::addMore, onHome = viewModel::goHome)
        return
    }

    ClaimEvidenceScreen(
        state = state,
        onTakePhoto = { viewModel.requestCapture(CaptureRequest.PHOTO) },
        onPickPhotos = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onRecordVideo = { viewModel.requestCapture(CaptureRequest.VIDEO) },
        onPickVideo = { pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
        onAttachLocation = requestLocation,
        onOpenSettings = context::openAppSettings,
        onRetry = viewModel::retry,
        onRemove = viewModel::remove,
        onReload = viewModel::load,
        onDismissMessage = viewModel::dismissMessage,
        onFinish = viewModel::finish,
        onConfirmLeave = viewModel::confirmLeave,
        onDismissLeave = viewModel::dismissLeave,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClaimEvidenceScreen(
    state: ClaimEvidenceUiState,
    onTakePhoto: () -> Unit,
    onPickPhotos: () -> Unit,
    onRecordVideo: () -> Unit,
    onPickVideo: () -> Unit,
    onAttachLocation: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
    onReload: () -> Unit,
    onDismissMessage: () -> Unit,
    onFinish: () -> Unit,
    onConfirmLeave: () -> Unit,
    onDismissLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Text(
            stringResource(R.string.evidence_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        state.claim?.let {
            Text(stringResource(R.string.evidence_claim_number, it.number), style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(16.dp))
        if (state.loading) {
            CircularProgressIndicator()
            return@Column
        }
        if (state.claim == null) {
            MessageCard(EvidenceMessage.LOAD_FAILED, onDismissMessage, onOpenSettings)
            Spacer(Modifier.height(12.dp))
            SolventaPrimaryButton(text = stringResource(R.string.claim_retry), onClick = onReload)
            return@Column
        }
        state.message?.let {
            MessageCard(it, onDismissMessage, onOpenSettings)
            Spacer(Modifier.height(12.dp))
        }

        // Fotos
        SectionTitle(R.string.evidence_photos_title)
        Tiles(state, EvidenceKind.PHOTO, onRetry, onRemove)
        Text(
            pluralStringResource(R.plurals.evidence_photos_remaining, state.remainingPhotos, state.remainingPhotos),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onTakePhoto,
                enabled = state.remainingPhotos > 0 && !state.processing,
                modifier = Modifier.testTag(ClaimEvidenceTestTags.TAKE_PHOTO),
            ) { Text(stringResource(R.string.evidence_take_photo)) }
            OutlinedButton(
                onClick = onPickPhotos,
                enabled = state.remainingPhotos > 0 && !state.processing,
                modifier = Modifier.testTag(ClaimEvidenceTestTags.PICK_PHOTOS),
            ) { Text(stringResource(R.string.evidence_pick_photos)) }
        }

        // Video (solo si el tipo de siniestro lo admite)
        if (state.rules.allowsVideo) {
            Spacer(Modifier.height(20.dp))
            SectionTitle(R.string.evidence_video_title)
            Tiles(state, EvidenceKind.VIDEO, onRetry, onRemove)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRecordVideo,
                    enabled = state.remainingVideos > 0 && !state.processing,
                    modifier = Modifier.testTag(ClaimEvidenceTestTags.RECORD_VIDEO),
                ) { Text(stringResource(R.string.evidence_record_video)) }
                OutlinedButton(
                    onClick = onPickVideo,
                    enabled = state.remainingVideos > 0 && !state.processing,
                    modifier = Modifier.testTag(ClaimEvidenceTestTags.PICK_VIDEO),
                ) { Text(stringResource(R.string.evidence_pick_video)) }
            }
        }
        if (state.processing) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle(R.string.evidence_location_title)
        LocationCard(state.location, onAttachLocation, onOpenSettings)

        Spacer(Modifier.height(28.dp))
        SolventaPrimaryButton(
            text = stringResource(R.string.evidence_finish),
            onClick = onFinish,
            modifier = Modifier.testTag(ClaimEvidenceTestTags.FINISH),
        )
    }

    if (state.confirmLeaveWithPending) {
        AlertDialog(
            onDismissRequest = onDismissLeave,
            title = { Text(stringResource(R.string.evidence_leave_title)) },
            text = { Text(pluralStringResource(R.plurals.evidence_leave_body, state.pendingCount, state.pendingCount)) },
            confirmButton = { TextButton(onClick = onConfirmLeave) { Text(stringResource(R.string.evidence_leave_confirm)) } },
            dismissButton = { TextButton(onClick = onDismissLeave) { Text(stringResource(R.string.evidence_leave_stay)) } },
        )
    }
}

@Composable
private fun SectionTitle(res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tiles(state: ClaimEvidenceUiState, kind: EvidenceKind, onRetry: (String) -> Unit, onRemove: (String) -> Unit) {
    val remote = state.uploaded.filter { it.kind == kind }
    val local = state.local.filter { it.kind == kind }
    if (remote.isEmpty() && local.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        remote.forEachIndexed { index, item -> RemoteTile(item, index + 1) }
        local.forEachIndexed { index, item -> LocalTile(item, remote.size + index + 1, onRetry, onRemove) }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun RemoteTile(item: RemoteEvidence, number: Int) {
    val kind = stringResource(if (item.kind == EvidenceKind.VIDEO) R.string.evidence_kind_video else R.string.evidence_kind_photo)
    val status = stringResource(if (item.state == "CARGADA") R.string.evidence_state_uploaded else R.string.evidence_state_sending)
    TileBox(description = stringResource(R.string.evidence_tile_a11y, kind, number, status)) {
        Text(status, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(8.dp))
    }
}

@Composable
private fun LocalTile(item: LocalEvidence, number: Int, onRetry: (String) -> Unit, onRemove: (String) -> Unit) {
    val thumbnail by rememberThumbnail(item.filePath, item.kind == EvidenceKind.VIDEO)
    val kind = stringResource(if (item.kind == EvidenceKind.VIDEO) R.string.evidence_kind_video else R.string.evidence_kind_photo)
    val status = stringResource(stateRes(item))
    Column(Modifier.width(TILE_SIZE)) {
        TileBox(description = stringResource(R.string.evidence_tile_a11y, kind, number, status), tag = ClaimEvidenceTestTags.ITEM_PREFIX + item.localId) {
            thumbnail?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            when (item.state) {
                EvidenceUploadState.UPLOADING -> CircularProgressIndicator(progress = { item.progress }, modifier = Modifier.size(36.dp))
                EvidenceUploadState.QUEUED, EvidenceUploadState.REQUESTING, EvidenceUploadState.CONFIRMING ->
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                else -> Unit
            }
        }
        Text(status, style = MaterialTheme.typography.labelSmall, color = statusColor(item))
        item.problem?.takeIf { item.state == EvidenceUploadState.REJECTED || item.state == EvidenceUploadState.FAILED }?.let {
            Text(stringResource(problemRes(it)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        if (item.state == EvidenceUploadState.FAILED) {
            TextButton(onClick = { onRetry(item.localId) }, modifier = Modifier.testTag(ClaimEvidenceTestTags.RETRY_PREFIX + item.localId)) {
                Text(stringResource(R.string.evidence_retry))
            }
        }
        if (item.state == EvidenceUploadState.FAILED || item.state == EvidenceUploadState.REJECTED) {
            TextButton(onClick = { onRemove(item.localId) }, modifier = Modifier.testTag(ClaimEvidenceTestTags.REMOVE_PREFIX + item.localId)) {
                Text(stringResource(R.string.evidence_remove))
            }
        }
    }
}

@Composable
private fun TileBox(description: String, tag: String? = null, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(TILE_SIZE)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .semantics { contentDescription = description }
            .let { if (tag != null) it.testTag(tag) else it },
    ) { content() }
}

@Composable
private fun statusColor(item: LocalEvidence) = when (item.state) {
    EvidenceUploadState.FAILED, EvidenceUploadState.REJECTED -> MaterialTheme.colorScheme.error
    EvidenceUploadState.UPLOADED -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun LocationCard(location: LocationUi, onAttach: () -> Unit, onOpenSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ClaimEvidenceTestTags.LOCATION),
    ) {
        Column(Modifier.padding(16.dp)) {
            when (location) {
                LocationUi.Idle -> {
                    Text(stringResource(R.string.evidence_location_why), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onAttach) { Text(stringResource(R.string.evidence_location_attach)) }
                }
                LocationUi.Locating -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                    Text(stringResource(R.string.evidence_location_locating), modifier = Modifier.padding(start = 12.dp))
                }
                is LocationUi.Attached -> {
                    Text(stringResource(R.string.evidence_location_attached), style = MaterialTheme.typography.titleSmall)
                    Text(
                        location.address ?: String.format(Locale.ROOT, "%.5f, %.5f", location.fix.latitude, location.fix.longitude),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.evidence_location_accuracy, location.fix.accuracyMeters.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onAttach) { Text(stringResource(R.string.evidence_location_update)) }
                }
                LocationUi.Denied -> {
                    Text(stringResource(R.string.evidence_location_denied), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onAttach) { Text(stringResource(R.string.evidence_location_grant)) }
                }
                LocationUi.PermanentlyDenied -> {
                    Text(stringResource(R.string.evidence_location_denied), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.evidence_open_settings)) }
                }
                LocationUi.Disabled -> {
                    Text(stringResource(R.string.evidence_location_disabled), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onAttach) { Text(stringResource(R.string.claim_retry)) }
                }
                LocationUi.Unavailable, LocationUi.SaveFailed -> {
                    Text(stringResource(R.string.evidence_location_unavailable), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onAttach) { Text(stringResource(R.string.claim_retry)) }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(message: EvidenceMessage, onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ClaimEvidenceTestTags.MESSAGE),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(messageRes(message)), style = MaterialTheme.typography.bodyMedium)
            Row {
                if (message == EvidenceMessage.CAMERA_PERMANENTLY_DENIED) {
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.evidence_open_settings)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.claim_dialog_accept)) }
            }
        }
    }
}

private val TILE_SIZE = 96.dp

private fun stateRes(item: LocalEvidence): Int = when (item.state) {
    EvidenceUploadState.QUEUED, EvidenceUploadState.REQUESTING -> R.string.evidence_state_waiting
    EvidenceUploadState.UPLOADING, EvidenceUploadState.CONFIRMING -> R.string.evidence_state_sending
    EvidenceUploadState.UPLOADED -> R.string.evidence_state_uploaded
    EvidenceUploadState.FAILED -> R.string.evidence_state_failed
    EvidenceUploadState.REJECTED -> R.string.evidence_state_rejected
}

internal fun problemRes(problem: EvidenceProblem): Int = when (problem) {
    EvidenceProblem.NETWORK -> R.string.evidence_problem_network
    EvidenceProblem.SERVICE_UNAVAILABLE -> R.string.evidence_problem_service
    EvidenceProblem.SESSION_EXPIRED -> R.string.evidence_problem_session
    EvidenceProblem.LIMIT_REACHED -> R.string.evidence_problem_limit
    EvidenceProblem.TOO_LARGE -> R.string.evidence_problem_too_large
    EvidenceProblem.NOT_ALLOWED -> R.string.evidence_problem_not_allowed
    EvidenceProblem.REJECTED_BY_SERVER -> R.string.evidence_problem_rejected
    EvidenceProblem.CLAIM_CLOSED -> R.string.evidence_problem_claim_closed
    EvidenceProblem.FILE_MISSING -> R.string.evidence_problem_file_missing
}

internal fun messageRes(message: EvidenceMessage): Int = when (message) {
    EvidenceMessage.PHOTO_UNREADABLE -> R.string.evidence_msg_photo_unreadable
    EvidenceMessage.PHOTO_TOO_LARGE -> R.string.evidence_msg_photo_too_large
    EvidenceMessage.VIDEO_TOO_LONG -> R.string.evidence_msg_video_too_long
    EvidenceMessage.VIDEO_TOO_LARGE -> R.string.evidence_msg_video_too_large
    EvidenceMessage.VIDEO_UNSUPPORTED -> R.string.evidence_msg_video_unsupported
    EvidenceMessage.LIMIT_REACHED -> R.string.evidence_msg_limit
    EvidenceMessage.CAMERA_DENIED -> R.string.evidence_msg_camera_denied
    EvidenceMessage.CAMERA_PERMANENTLY_DENIED -> R.string.evidence_msg_camera_permanently_denied
    EvidenceMessage.LOAD_FAILED -> R.string.evidence_msg_load_failed
}
