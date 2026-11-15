package com.solventa.app.feature.claims.evidence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.solventa.app.R
import com.solventa.app.core.designsystem.component.SolventaPrimaryButton
import com.solventa.app.core.designsystem.component.SolventaSecondaryButton
import com.solventa.app.core.designsystem.theme.SolventaTheme
import com.solventa.app.data.claims.ClaimStatus
import com.solventa.app.data.claims.ReportedClaim
import com.solventa.app.data.claims.evidence.EvidenceKind
import java.time.Instant

/** Confirmación al terminar de adjuntar evidencias: resumen de lo enviado y cómo seguir. */
@Composable
fun ClaimEvidenceDoneScreen(
    state: ClaimEvidenceUiState,
    onAddMore: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val photos = state.sentCount(EvidenceKind.PHOTO)
    val videos = state.sentCount(EvidenceKind.VIDEO)
    val anySent = photos + videos > 0
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .testTag(ClaimEvidenceTestTags.DONE),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check_circle),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(if (anySent) R.string.evidence_done_title else R.string.evidence_done_title_empty),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        state.claim?.let {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.evidence_claim_number, it.number), style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(20.dp))
        if (anySent) {
            if (photos > 0) Text(pluralStringResource(R.plurals.evidence_done_photos, photos, photos))
            if (videos > 0) Text(pluralStringResource(R.plurals.evidence_done_videos, videos, videos))
        } else {
            Text(stringResource(R.string.evidence_done_none))
        }
        if (state.locationAttached) Text(stringResource(R.string.evidence_done_location))
        if (state.notSentCount > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                pluralStringResource(R.plurals.evidence_done_not_sent, state.notSentCount, state.notSentCount),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.evidence_done_next),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        SolventaPrimaryButton(
            text = stringResource(R.string.claim_go_home),
            onClick = onHome,
            modifier = Modifier.testTag(ClaimEvidenceTestTags.DONE_HOME),
        )
        Spacer(Modifier.height(12.dp))
        SolventaSecondaryButton(
            text = stringResource(R.string.evidence_done_add_more),
            onClick = onAddMore,
            modifier = Modifier.testTag(ClaimEvidenceTestTags.DONE_ADD_MORE),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7F4)
@Composable
private fun ClaimEvidenceDoneScreenPreview() {
    SolventaTheme {
        ClaimEvidenceDoneScreen(
            state = ClaimEvidenceUiState(
                loading = false,
                claim = ReportedClaim(
                    id = "c41f0e2a", number = "SIN-2026-C41F0E2A", status = ClaimStatus.REPORTED,
                    policyId = "p", policyNumber = "SLV-2026-08341", typeCode = "DANO_DISPOSITIVO",
                    occurredAt = Instant.parse("2026-11-10T19:30:00Z"), reportedAt = Instant.parse("2026-11-10T20:02:11Z"),
                ),
                completed = true,
            ),
            onAddMore = {},
            onHome = {},
        )
    }
}
