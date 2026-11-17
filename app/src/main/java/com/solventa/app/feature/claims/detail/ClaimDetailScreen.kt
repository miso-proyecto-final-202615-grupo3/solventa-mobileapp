package com.solventa.app.feature.claims.detail

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.solventa.app.R
import com.solventa.app.core.designsystem.component.SolventaPrimaryButton
import com.solventa.app.core.designsystem.component.SolventaSecondaryButton
import com.solventa.app.core.designsystem.theme.SolventaTheme
import com.solventa.app.data.claims.ClaimStatus
import com.solventa.app.data.claims.ReportedClaim
import com.solventa.app.data.claims.evidence.EvidenceKind
import com.solventa.app.data.claims.evidence.RemoteEvidence
import com.solventa.app.feature.claims.list.StatusBadge
import com.solventa.app.feature.claims.list.formatDateTime
import com.solventa.app.feature.claims.list.PENDING_NAME
import com.solventa.app.feature.claims.list.humanize
import java.time.Instant

object ClaimDetailTestTags {
    const val NUMBER = "claim_detail_number"
    const val STATUS = "claim_detail_status"
    const val EVIDENCE_PREFIX = "claim_detail_evidence_"
    const val ADD_EVIDENCE = "claim_detail_add_evidence"
    const val MESSAGE = "claim_detail_message"
    const val VIEWER = "claim_detail_viewer"
    const val VIEWER_CLOSE = "claim_detail_viewer_close"
}

@Composable
fun ClaimDetailRoute(
    viewModel: ClaimDetailViewModel,
    onAddEvidence: (String) -> Unit,
    onBack: () -> Unit,
    onLogin: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.goToLogin) {
        if (state.goToLogin) {
            viewModel.consumeLogin()
            onLogin()
        }
    }
    ClaimDetailScreen(
        state = state,
        onOpenEvidence = viewModel::openEvidence,
        onAddEvidence = { state.claim?.let { onAddEvidence(it.id) } },
        onRetry = viewModel::load,
        onDismissMessage = viewModel::dismissMessage,
        onBack = onBack,
    )
    val viewing = state.viewing
    val url = state.viewingUrl
    if (viewing != null && url != null) {
        EvidenceViewer(evidence = viewing, url = url.url, onClose = viewModel::closeViewer, onError = viewModel::viewerFailed)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClaimDetailScreen(
    state: ClaimDetailUiState,
    onOpenEvidence: (RemoteEvidence) -> Unit,
    onAddEvidence: () -> Unit,
    onRetry: () -> Unit,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Text(
            stringResource(R.string.claim_detail_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(16.dp))
        val claim = state.claim
        if (state.loading) {
            CircularProgressIndicator()
            return@Column
        }
        state.message?.let { message ->
            Text(
                stringResource(messageRes(message)),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(ClaimDetailTestTags.MESSAGE),
            )
            if (claim != null) TextButton(onClick = onDismissMessage) { Text(stringResource(R.string.claim_detail_dismiss)) }
            Spacer(Modifier.height(8.dp))
        }
        if (claim == null) {
            if (state.message != ClaimDetailMessage.NOT_FOUND) {
                SolventaPrimaryButton(text = stringResource(R.string.claim_retry), onClick = onRetry)
                Spacer(Modifier.height(12.dp))
            }
            SolventaSecondaryButton(text = stringResource(R.string.claim_detail_back), onClick = onBack)
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                claim.number,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).testTag(ClaimDetailTestTags.NUMBER),
            )
            StatusBadge(claim.status, Modifier.testTag(ClaimDetailTestTags.STATUS))
        }
        Spacer(Modifier.height(12.dp))
        Field(R.string.claim_detail_type, state.typeName ?: if (state.policyLoading) PENDING_NAME else humanize(claim.typeCode))
        Field(
            R.string.claim_detail_policy,
            listOfNotNull(state.policy?.productName, claim.policyNumber.takeIf { it.isNotBlank() }).joinToString(" · "),
        )
        Field(R.string.claim_detail_occurred, formatDateTime(claim.occurredAt))
        Field(R.string.claim_detail_reported, formatDateTime(claim.reportedAt))
        Field(
            R.string.claim_detail_location,
            claim.location?.let { stringResource(R.string.claim_detail_location_value, it.accuracyMeters.toInt()) }
                ?: stringResource(R.string.claim_detail_location_none),
        )

        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.claim_detail_evidence_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (claim.evidences.isEmpty()) {
            Text(stringResource(R.string.claim_detail_evidence_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var photo = 0
                var video = 0
                claim.evidences.forEach { evidence ->
                    val number = if (evidence.kind == EvidenceKind.VIDEO) ++video else ++photo
                    Thumbnail(
                        evidence = evidence,
                        number = number,
                        url = state.urls[evidence.id]?.url,
                        canOpen = state.canOpen(evidence),
                        opening = state.openingEvidenceId == evidence.id,
                        onOpen = { onOpenEvidence(evidence) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.claim_detail_evidence_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(28.dp))
        if (state.canAddEvidence) {
            SolventaPrimaryButton(
                text = stringResource(R.string.claim_detail_add_evidence),
                onClick = onAddEvidence,
                modifier = Modifier.testTag(ClaimDetailTestTags.ADD_EVIDENCE),
            )
            Spacer(Modifier.height(12.dp))
        }
        SolventaSecondaryButton(text = stringResource(R.string.claim_detail_back), onClick = onBack)
    }
}

@Composable
private fun Field(label: Int, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Miniatura: la foto desde su URL temporal; los videos y las no cargadas muestran un ícono y su estado. */
@Composable
private fun Thumbnail(
    evidence: RemoteEvidence,
    number: Int,
    url: String?,
    canOpen: Boolean,
    opening: Boolean,
    onOpen: () -> Unit,
) {
    val kindLabel = stringResource(if (evidence.kind == EvidenceKind.VIDEO) R.string.evidence_kind_video else R.string.evidence_kind_photo)
    val stateLabel = stringResource(evidenceStateRes(evidence.state))
    val description = stringResource(R.string.evidence_tile_a11y, kindLabel, number, stateLabel)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(96.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(if (canOpen) Modifier.clickable(onClick = onOpen) else Modifier)
                .semantics { contentDescription = description }
                .testTag(ClaimDetailTestTags.EVIDENCE_PREFIX + evidence.id),
        ) {
            if (evidence.kind == EvidenceKind.PHOTO && url != null) {
                AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            when {
                opening -> CircularProgressIndicator(Modifier.size(28.dp))
                evidence.kind == EvidenceKind.VIDEO -> Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                )
                url == null && canOpen -> Text(kindLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
        Text("$kindLabel $number", style = MaterialTheme.typography.labelMedium)
        if (!canOpen) Text(stateLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Visor a pantalla completa dentro de la app: la foto con Coil y el video con el reproductor de Android. */
@Composable
private fun EvidenceViewer(evidence: RemoteEvidence, url: String, onClose: () -> Unit, onError: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().background(Color.Black).testTag(ClaimDetailTestTags.VIEWER),
        ) {
            if (evidence.kind == EvidenceKind.VIDEO) {
                AndroidView(
                    factory = { context ->
                        VideoView(context).apply {
                            setMediaController(MediaController(context).also { it.setAnchorView(this) })
                            setOnPreparedListener { start() }
                            setOnErrorListener { _, _, _ ->
                                onError()
                                true
                            }
                            setVideoURI(Uri.parse(url))
                        }
                    },
                    onRelease = { it.stopPlayback() },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                AsyncImage(
                    model = url,
                    contentDescription = stringResource(R.string.evidence_kind_photo),
                    contentScale = ContentScale.Fit,
                    onError = { onError() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).testTag(ClaimDetailTestTags.VIEWER_CLOSE),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.claim_detail_viewer_close), tint = Color.White)
            }
        }
    }
}

private fun evidenceStateRes(state: String): Int = when (state) {
    ClaimDetailUiState.UPLOADED -> R.string.evidence_state_uploaded
    "RECHAZADA" -> R.string.evidence_state_rejected
    else -> R.string.evidence_state_waiting
}

private fun messageRes(message: ClaimDetailMessage): Int = when (message) {
    ClaimDetailMessage.LOAD_NETWORK -> R.string.my_claims_error_network
    ClaimDetailMessage.LOAD_SERVICE -> R.string.my_claims_error_service
    ClaimDetailMessage.NOT_FOUND -> R.string.claim_detail_msg_not_found
    ClaimDetailMessage.EVIDENCE_NOT_AVAILABLE -> R.string.claim_detail_msg_not_available
    ClaimDetailMessage.EVIDENCE_FAILED -> R.string.claim_detail_msg_failed
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7F4)
@Composable
private fun ClaimDetailScreenPreview() {
    SolventaTheme {
        ClaimDetailScreen(
            state = ClaimDetailUiState(
                loading = false,
                claim = ReportedClaim(
                    id = "1", number = "SIN-2026-C41F0E2A", status = ClaimStatus.REPORTED, policyId = "p",
                    policyNumber = "SLV-2026-08341", typeCode = "DANO_DISPOSITIVO",
                    occurredAt = Instant.parse("2026-11-10T19:30:00Z"), reportedAt = Instant.parse("2026-11-10T20:02:11Z"),
                    evidences = listOf(
                        RemoteEvidence("e1", EvidenceKind.PHOTO, "CARGADA", 2048, Instant.parse("2026-11-10T20:05:00Z"), null),
                        RemoteEvidence("e2", EvidenceKind.VIDEO, "PENDIENTE", null, Instant.parse("2026-11-10T20:06:00Z"), null),
                    ),
                ),
            ),
            onOpenEvidence = {}, onAddEvidence = {}, onRetry = {}, onDismissMessage = {}, onBack = {},
        )
    }
}
