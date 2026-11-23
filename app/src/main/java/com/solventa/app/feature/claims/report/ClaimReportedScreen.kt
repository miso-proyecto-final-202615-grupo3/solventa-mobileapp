package com.solventa.app.feature.claims.report

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.solventa.app.R
import com.solventa.app.core.designsystem.component.SolventaPrimaryButton
import com.solventa.app.core.designsystem.component.SolventaSecondaryButton
import com.solventa.app.core.designsystem.theme.SolventaTheme
import com.solventa.app.data.claims.ClaimStatus
import com.solventa.app.data.claims.ReportedClaim
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Confirmación del reporte con el folio que generó el backend (CA4 y CA5 de HU4.1.1). */
@Composable
fun ClaimReportedScreen(
    claim: ReportedClaim,
    onAddEvidence: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val canAskNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check_circle),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.claim_done_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.claim_done_number_label), style = MaterialTheme.typography.labelLarge)
        val numberA11y = stringResource(R.string.claim_done_number_a11y, claim.number.toList().joinToString(" "))
        SelectionContainer {
            Text(
                text = claim.number,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .testTag(ClaimReportTestTags.NUMBER)
                    .clearAndSetSemantics { contentDescription = numberA11y },
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.claim_done_policy, claim.policyNumber), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.claim_done_reported_at, formatInstant(claim.reportedAt)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.claim_done_next), style = MaterialTheme.typography.bodyMedium)
        if (canAskNotifications) {
            Spacer(Modifier.height(16.dp))
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.claim_notifications_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.claim_notifications_body), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    SolventaSecondaryButton(
                        text = stringResource(R.string.claim_notifications_action),
                        onClick = { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        SolventaPrimaryButton(
            text = stringResource(R.string.claim_add_evidence),
            onClick = onAddEvidence,
            modifier = Modifier.testTag(ClaimReportTestTags.ADD_EVIDENCE),
        )
        Spacer(Modifier.height(12.dp))
        SolventaSecondaryButton(text = stringResource(R.string.claim_go_home), onClick = onHome)
    }
}

private fun formatInstant(instant: Instant): String =
    instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))

@Preview(showBackground = true, backgroundColor = 0xFFF5F7F4)
@Composable
private fun ClaimReportedScreenPreview() {
    SolventaTheme {
        ClaimReportedScreen(
            claim = ReportedClaim(
                id = "c41f0e2a", number = "SIN-2026-C41F0E2A", status = ClaimStatus.REPORTED,
                policyId = "p", policyNumber = "SLV-2026-08341", typeCode = "GASTO_MEDICO",
                occurredAt = Instant.parse("2026-11-10T19:30:00Z"), reportedAt = Instant.parse("2026-11-10T20:02:11Z"),
            ),
            onAddEvidence = {},
            onHome = {},
        )
    }
}
