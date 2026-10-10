package com.solventa.app.feature.biometric

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solventa.app.R
import com.solventa.app.core.biometric.BiometricPromptTexts
import com.solventa.app.core.biometric.rememberBiometricPromptLauncher
import com.solventa.app.core.designsystem.theme.SolventaTheme

object SecuritySettingsTestTags {
    const val BIOMETRIC_SWITCH = "security_biometric_switch"
}

@Composable
fun SecuritySettingsRoute(
    viewModel: SecuritySettingsViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val launcher = rememberBiometricPromptLauncher()
    val texts = BiometricPromptTexts(
        title = stringResource(R.string.biometric_prompt_activate_title),
        subtitle = stringResource(R.string.biometric_prompt_activate_subtitle),
        negativeButton = stringResource(R.string.biometric_prompt_cancel),
    )

    // El usuario pudo configurar una huella en los ajustes del teléfono y volver.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(uiState.promptCipher) {
        val cipher = uiState.promptCipher ?: return@LaunchedEffect
        viewModel.onPromptLaunched()
        launcher.launch(cipher, texts, closeOnFailedAttempt = false, onEvent = viewModel::onPromptEvent)
    }

    SecuritySettingsScreen(uiState = uiState, onToggle = viewModel::onToggle, onBack = onBack)
}

@Composable
fun SecuritySettingsScreen(
    uiState: SecuritySettingsUiState,
    onToggle: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        TextButton(onClick = onBack) { Text("← " + stringResource(R.string.security_back)) }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.security_title), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.security_section),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                .padding(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.security_biometric_label), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        if (uiState.biometricAvailable || uiState.biometricEnabled) {
                            R.string.security_biometric_description
                        } else {
                            R.string.security_biometric_unavailable
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = uiState.biometricEnabled,
                onCheckedChange = onToggle,
                enabled = uiState.biometricAvailable || uiState.biometricEnabled,
                modifier = Modifier.testTag(SecuritySettingsTestTags.BIOMETRIC_SWITCH),
            )
        }
        if (uiState.activationFailed) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.biometric_offer_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7F4)
@Composable
private fun SecuritySettingsScreenPreview() {
    SolventaTheme {
        SecuritySettingsScreen(
            uiState = SecuritySettingsUiState(biometricEnabled = true, biometricAvailable = true),
            onToggle = {},
            onBack = {},
        )
    }
}
