package com.solventa.app.feature.policies

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solventa.app.R
import com.solventa.app.core.designsystem.component.SolventaPrimaryButton
import com.solventa.app.core.designsystem.component.SolventaSecondaryButton
import com.solventa.app.data.policies.Coverage
import com.solventa.app.data.policies.Policy
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PolicyDetailRoute(
    viewModel: PolicyDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    PolicyDetailScreen(
        state = state,
        onOpenCertificate = {
            scope.launch {
                val file = viewModel.prepareCertificate()
                if (file == null || !openPdf(context, file)) {
                    Toast.makeText(context, R.string.policy_certificate_open_failed, Toast.LENGTH_LONG).show()
                }
            }
        },
        onBack = onBack,
        modifier = modifier,
    )
}

/** Abre el PDF con el visor del sistema mediante el FileProvider de la app; `false` si no hay visor. */
private fun openPdf(context: android.content.Context, file: File): Boolean {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.evidencias", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** Coberturas y credencial digital de la póliza; todo sale de la copia local (HU3.2.1). */
@Composable
fun PolicyDetailScreen(
    state: PolicyDetailUiState,
    onOpenCertificate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        val policy = state.policy
        if (policy == null) {
            Text(stringResource(R.string.policy_not_found), style = MaterialTheme.typography.bodyLarge)
        } else {
            SyncStatus(offline = state.offline, syncing = false, failed = false, syncedAt = state.syncedAt)
            Spacer(Modifier.height(16.dp))
            CredentialCard(policy)
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.policy_coverages), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            policy.coverages.forEach { CoverageRow(it) }
            Spacer(Modifier.height(24.dp))
            if (state.certificateSaved) {
                SolventaPrimaryButton(text = stringResource(R.string.policy_open_certificate), onClick = onOpenCertificate)
                Text(
                    stringResource(R.string.policy_certificate_saved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Text(
                    stringResource(if (state.offline) R.string.policy_certificate_unavailable_offline else R.string.policy_certificate_pending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        SolventaSecondaryButton(text = stringResource(R.string.policies_back), onClick = onBack)
    }
}

/** Credencial digital: lo que el cliente necesita mostrar para identificar su póliza. */
@Composable
private fun CredentialCard(policy: Policy) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(stringResource(R.string.policy_credential), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(policy.productName ?: policy.branch, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.policies_number, policy.number), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.policies_valid_range, formatDate(policy.validFrom), formatDate(policy.validTo)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(statusLabel(policy.status), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(R.string.policy_premium, formatAmount(policy.totalPremium)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CoverageRow(coverage: Coverage) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(coverage.name ?: stringResource(R.string.policy_coverage_unnamed), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.policy_coverage_limit, formatAmount(coverage.insuredLimit)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.policy_coverage_deductible, formatAmount(coverage.deductible)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
