package com.solventa.app.feature.claims.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solventa.app.R
import com.solventa.app.core.designsystem.component.SolventaPrimaryButton
import com.solventa.app.core.designsystem.component.SolventaSecondaryButton
import com.solventa.app.core.designsystem.theme.SolventaTheme
import com.solventa.app.data.claims.ClaimStatus
import com.solventa.app.data.claims.ReportedClaim
import java.time.Instant

object MyClaimsTestTags {
    const val LIST = "my_claims_list"
    const val ITEM_PREFIX = "my_claims_item_"
    const val FILTER_ALL = "my_claims_filter_all"
    const val FILTER_PREFIX = "my_claims_filter_"
    const val EMPTY = "my_claims_empty"
    const val ERROR = "my_claims_error"
    const val LOAD_MORE = "my_claims_load_more"
}

@Composable
fun MyClaimsRoute(
    viewModel: MyClaimsViewModel,
    onOpenClaim: (String) -> Unit,
    onReportClaim: () -> Unit,
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
    MyClaimsScreen(
        state = state,
        onSelectPolicy = viewModel::selectPolicy,
        onOpenClaim = onOpenClaim,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::load,
        onReportClaim = onReportClaim,
        onBack = onBack,
    )
}

@Composable
fun MyClaimsScreen(
    state: MyClaimsUiState,
    onSelectPolicy: (String?) -> Unit,
    onOpenClaim: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onReportClaim: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp)) {
        Text(
            stringResource(R.string.my_claims_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (state.policies.size > 1) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(
                    selected = state.selectedPolicyId == null,
                    onClick = { onSelectPolicy(null) },
                    label = { Text(stringResource(R.string.my_claims_filter_all)) },
                    modifier = Modifier.testTag(MyClaimsTestTags.FILTER_ALL),
                )
                state.policies.forEach { policy ->
                    FilterChip(
                        selected = state.selectedPolicyId == policy.id,
                        onClick = { onSelectPolicy(policy.id) },
                        label = { Text("${policy.productName} · ${policy.number}") },
                        modifier = Modifier.testTag(MyClaimsTestTags.FILTER_PREFIX + policy.id),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        when {
            state.loading -> CircularProgressIndicator()
            state.error != null && state.claims.isEmpty() -> {
                Text(
                    stringResource(errorRes(state.error)),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(MyClaimsTestTags.ERROR),
                )
                Spacer(Modifier.height(12.dp))
                SolventaPrimaryButton(text = stringResource(R.string.claim_retry), onClick = onRetry)
            }
            state.isEmpty -> {
                Text(
                    stringResource(if (state.selectedPolicyId == null) R.string.my_claims_empty else R.string.my_claims_empty_policy),
                    modifier = Modifier.testTag(MyClaimsTestTags.EMPTY),
                )
                Spacer(Modifier.height(16.dp))
                SolventaPrimaryButton(text = stringResource(R.string.home_report_claim), onClick = onReportClaim)
            }
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).testTag(MyClaimsTestTags.LIST),
            ) {
                item {
                    Text(
                        pluralStringResource(R.plurals.my_claims_count_plural, state.total, state.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.claims, key = { it.id }) { claim ->
                    ClaimItem(claim, state.typeName(claim) ?: if (state.policiesLoading) PENDING_NAME else null, state.policyOf(claim)?.productName) { onOpenClaim(claim.id) }
                }
                if (state.hasMore || state.loadingMore || state.error != null) {
                    item {
                        when {
                            state.loadingMore -> CircularProgressIndicator()
                            state.error != null -> Text(stringResource(errorRes(state.error)), color = MaterialTheme.colorScheme.error)
                            else -> Unit
                        }
                        if (!state.loadingMore) {
                            SolventaSecondaryButton(
                                text = stringResource(R.string.my_claims_load_more),
                                onClick = onLoadMore,
                                modifier = Modifier.testTag(MyClaimsTestTags.LOAD_MORE),
                            )
                        }
                    }
                }
            }
        }
        if (!state.loading && state.claims.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SolventaSecondaryButton(text = stringResource(R.string.claim_go_home), onClick = onBack)
        }
    }
}

@Composable
private fun ClaimItem(claim: ReportedClaim, typeName: String?, productName: String?, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(MyClaimsTestTags.ITEM_PREFIX + claim.id),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(claim.number, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusBadge(claim.status)
            }
            Spacer(Modifier.height(4.dp))
            Text(typeName ?: humanize(claim.typeCode), style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(productName, claim.policyNumber.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.my_claims_reported_on, formatDateTime(claim.reportedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun errorRes(error: MyClaimsError): Int = when (error) {
    MyClaimsError.NETWORK -> R.string.my_claims_error_network
    MyClaimsError.SERVICE -> R.string.my_claims_error_service
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7F4)
@Composable
private fun MyClaimsScreenPreview() {
    SolventaTheme {
        MyClaimsScreen(
            state = MyClaimsUiState(
                loading = false,
                claims = listOf(
                    ReportedClaim(
                        id = "1", number = "SIN-2026-C41F0E2A", status = ClaimStatus.REPORTED, policyId = "p",
                        policyNumber = "SLV-2026-08341", typeCode = "GASTO_MEDICO",
                        occurredAt = Instant.parse("2026-11-10T19:30:00Z"), reportedAt = Instant.parse("2026-11-10T20:02:11Z"),
                    ),
                ),
                total = 1,
            ),
            onSelectPolicy = {}, onOpenClaim = {}, onLoadMore = {}, onRetry = {}, onReportClaim = {}, onBack = {},
        )
    }
}
