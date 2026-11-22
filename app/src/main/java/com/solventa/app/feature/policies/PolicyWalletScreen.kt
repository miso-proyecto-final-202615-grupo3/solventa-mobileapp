package com.solventa.app.feature.policies

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solventa.app.R
import com.solventa.app.core.designsystem.component.SolventaPrimaryButton
import com.solventa.app.core.designsystem.component.SolventaSecondaryButton
import com.solventa.app.core.designsystem.theme.SolventaTheme
import com.solventa.app.data.policies.Coverage
import com.solventa.app.data.policies.Policy
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun PolicyWalletRoute(
    viewModel: PolicyWalletViewModel,
    onOpenPolicy: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PolicyWalletScreen(state, onRefresh = viewModel::refresh, onOpenPolicy = onOpenPolicy, onBack = onBack, modifier = modifier)
}

/** "Mis pólizas": billetera que se consulta desde la copia local, con o sin conexión (HU3.2.1 y HU3.2.2). */
@Composable
fun PolicyWalletScreen(
    state: PolicyWalletUiState,
    onRefresh: () -> Unit,
    onOpenPolicy: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp)) {
        Text(stringResource(R.string.policies_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        SyncStatus(state.offline, state.syncing, state.syncFailed, state.syncedAt)
        Spacer(Modifier.height(16.dp))
        when {
            state.policies.isNotEmpty() -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(state.policies, key = { it.id }) { PolicyCard(it, onClick = { onOpenPolicy(it.id) }) }
            }
            else -> Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        when {
                            state.hasLocalCopy -> R.string.policies_empty
                            state.offline -> R.string.policies_empty_offline
                            state.syncing -> R.string.policies_loading
                            else -> R.string.policies_empty_unsynced
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SolventaPrimaryButton(
            text = stringResource(R.string.policies_refresh),
            onClick = onRefresh,
            enabled = !state.offline,
            loading = state.syncing,
        )
        Spacer(Modifier.height(12.dp))
        SolventaSecondaryButton(text = stringResource(R.string.policies_back), onClick = onBack)
    }
}

/** Estado de la información: modo sin conexión con la última sincronización, "Sincronizando…" o fallo con reintento. */
@Composable
internal fun SyncStatus(offline: Boolean, syncing: Boolean, failed: Boolean, syncedAt: Instant?, modifier: Modifier = Modifier) {
    val formattedDate = remember(syncedAt) { syncedAt?.let(::formatDateTime) }
    Column(modifier = modifier.fillMaxWidth()) {
        if (syncing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.policies_syncing), style = MaterialTheme.typography.bodyMedium)
        }
        if (offline) {
            Text(
                text = stringResource(R.string.policies_offline_banner),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (syncing) 8.dp else 0.dp)
                    .padding(vertical = 4.dp),
            )
        } else if (failed) {
            Text(stringResource(R.string.policies_sync_failed), style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = if (formattedDate != null) {
                stringResource(R.string.policies_last_sync, formattedDate)
            } else {
                stringResource(R.string.policies_never_synced)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PolicyCard(policy: Policy, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(policy.productName ?: policy.branch, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.policies_number, policy.number),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.policies_valid_range, formatDate(policy.validFrom), formatDate(policy.validTo)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(statusLabel(policy.status), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun statusLabel(status: String): String = when (status.uppercase(Locale.ROOT)) {
    "EMITIDA", "VIGENTE" -> stringResource(R.string.policy_status_active)
    "CANCELADA" -> stringResource(R.string.policy_status_canceled)
    "VENCIDA" -> stringResource(R.string.policy_status_expired)
    else -> status
}

internal fun formatDateTime(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault()).format(instant)

internal fun formatDate(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault()).format(instant)

/** Importe del contrato (texto decimal) con separadores de miles; si no se puede leer, se muestra tal cual. */
internal fun formatAmount(value: String): String = try {
    "$ " + NumberFormat.getIntegerInstance().format(BigDecimal(value).setScale(0, java.math.RoundingMode.HALF_UP))
} catch (_: NumberFormatException) {
    value
}

internal fun sampleCoverage() = Coverage("c1", "Muerte por cualquier causa", "200000000.00", "0.00")

@Preview(showBackground = true, backgroundColor = 0xFFF5F7F4)
@Composable
private fun PolicyWalletOfflinePreview() {
    SolventaTheme {
        PolicyWalletScreen(
            state = PolicyWalletUiState(
                policies = listOf(
                    Policy(
                        "1", "SLV-2026-08341", "EMITIDA", "VIDA", "X", "Seguro de vida hipotecario",
                        Instant.parse("2026-03-12T00:00:00Z"), Instant.parse("2027-03-12T00:00:00Z"), null, "120000.00",
                        listOf(sampleCoverage()),
                    ),
                ),
                hasLocalCopy = true,
                syncedAt = Instant.parse("2026-11-18T14:05:00Z"),
                offline = true,
            ),
            onRefresh = {}, onOpenPolicy = {}, onBack = {},
        )
    }
}
