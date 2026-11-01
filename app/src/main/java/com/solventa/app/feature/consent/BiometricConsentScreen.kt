package com.solventa.app.feature.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solventa.app.R
import com.solventa.app.core.designsystem.component.SolventaPrimaryButton
import com.solventa.app.core.designsystem.component.SolventaSecondaryButton
import com.solventa.app.core.designsystem.theme.SolventaTheme

object BiometricConsentTestTags {
    const val CHECKBOX = "consent_checkbox"
    const val ACCEPT = "consent_accept"
    const val REJECT = "consent_reject"
    const val ERROR = "consent_error"
}

@Composable
fun BiometricConsentRoute(
    viewModel: BiometricConsentViewModel,
    onAccepted: () -> Unit,
    onDeclined: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.destination) {
        when (uiState.destination) {
            ConsentDestination.ACTIVATION -> onAccepted()
            ConsentDestination.DECLINED -> onDeclined()
            ConsentDestination.LOGIN -> onSessionExpired()
            null -> return@LaunchedEffect
        }
        viewModel.onDestinationHandled()
    }
    BiometricConsentScreen(
        uiState = uiState,
        onCheckedChange = viewModel::onCheckedChange,
        onAccept = viewModel::onAccept,
        onReject = viewModel::onReject,
        onRetry = viewModel::load,
    )
}

@Composable
fun BiometricConsentScreen(
    uiState: BiometricConsentUiState,
    onCheckedChange: (Boolean) -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.loading) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { CircularProgressIndicator() }
        return
    }
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.consent_section),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.consent_title), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.consent_body), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.consent_purpose),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.consent_version, uiState.termsVersion),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(BiometricConsentTestTags.CHECKBOX)
                .toggleable(value = uiState.checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        ) {
            Checkbox(checked = uiState.checked, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.consent_checkbox), style = MaterialTheme.typography.bodyMedium)
        }
        uiState.error?.let { error ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    if (error == ConsentError.NETWORK) R.string.consent_error_network else R.string.consent_error_service,
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .testTag(BiometricConsentTestTags.ERROR)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Spacer(Modifier.height(28.dp))
        SolventaPrimaryButton(
            text = stringResource(R.string.consent_accept),
            onClick = onAccept,
            enabled = uiState.checked,
            loading = uiState.submitting,
            modifier = Modifier.testTag(BiometricConsentTestTags.ACCEPT),
        )
        Spacer(Modifier.height(12.dp))
        SolventaSecondaryButton(
            text = stringResource(if (uiState.error != null && uiState.termsVersion.isBlank()) R.string.consent_retry else R.string.consent_reject),
            onClick = if (uiState.error != null && uiState.termsVersion.isBlank()) onRetry else onReject,
            modifier = Modifier.testTag(BiometricConsentTestTags.REJECT),
        )
    }
}

@Composable
fun ConsentDeclinedScreen(onBackToLogin: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(stringResource(R.string.consent_declined_title), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.consent_declined_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        SolventaPrimaryButton(text = stringResource(R.string.consent_declined_back), onClick = onBackToLogin)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7F4)
@Composable
private fun BiometricConsentScreenPreview() {
    SolventaTheme {
        BiometricConsentScreen(
            uiState = BiometricConsentUiState(loading = false, termsVersion = "1.0"),
            onCheckedChange = {}, onAccept = {}, onReject = {}, onRetry = {},
        )
    }
}
