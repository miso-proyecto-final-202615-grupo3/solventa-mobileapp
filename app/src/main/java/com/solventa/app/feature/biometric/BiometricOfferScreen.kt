package com.solventa.app.feature.biometric

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solventa.app.R
import com.solventa.app.core.biometric.BiometricPromptTexts
import com.solventa.app.core.biometric.rememberBiometricPromptLauncher
import com.solventa.app.core.designsystem.component.SolventaPrimaryButton
import com.solventa.app.core.designsystem.component.SolventaSecondaryButton
import com.solventa.app.core.designsystem.theme.SolventaTheme

object BiometricOfferTestTags {
    const val ACTIVATE = "biometric_offer_activate"
    const val NOT_NOW = "biometric_offer_not_now"
    const val USE_PASSWORD = "biometric_offer_use_password"
}

@Composable
fun BiometricOfferRoute(
    viewModel: BiometricOfferViewModel,
    onFinished: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val launcher = rememberBiometricPromptLauncher()
    val texts = BiometricPromptTexts(
        title = stringResource(R.string.biometric_prompt_activate_title),
        subtitle = stringResource(R.string.biometric_prompt_activate_subtitle),
        negativeButton = stringResource(R.string.biometric_prompt_cancel),
    )

    LaunchedEffect(uiState.promptCipher) {
        val cipher = uiState.promptCipher ?: return@LaunchedEffect
        viewModel.onPromptLaunched()
        launcher.launch(cipher, texts, closeOnFailedAttempt = false, onEvent = viewModel::onPromptEvent)
    }
    LaunchedEffect(uiState.finished) {
        if (uiState.finished) onFinished()
    }

    BiometricOfferScreen(
        uiState = uiState,
        onActivate = viewModel::onActivate,
        onDecline = viewModel::onDecline,
    )
}

@Composable
fun BiometricOfferScreen(
    uiState: BiometricOfferUiState,
    onActivate: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        BiometricBadge()
        Spacer(Modifier.height(16.dp))
        if (uiState.biometricAvailable) {
            Text(
                text = stringResource(R.string.biometric_offer_section),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.biometric_offer_title), style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.biometric_offer_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (uiState.activationFailed) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.biometric_offer_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(32.dp))
            SolventaPrimaryButton(
                text = stringResource(R.string.biometric_offer_activate),
                onClick = onActivate,
                modifier = Modifier.testTag(BiometricOfferTestTags.ACTIVATE),
            )
            Spacer(Modifier.height(12.dp))
            SolventaSecondaryButton(
                text = stringResource(R.string.biometric_offer_not_now),
                onClick = onDecline,
                modifier = Modifier.testTag(BiometricOfferTestTags.NOT_NOW),
            )
        } else {
            Text(stringResource(R.string.biometric_unavailable_title), style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.biometric_unavailable_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            SolventaPrimaryButton(
                text = stringResource(R.string.biometric_use_password),
                onClick = onDecline,
                modifier = Modifier.testTag(BiometricOfferTestTags.USE_PASSWORD),
            )
        }
    }
}

@Composable
internal fun BiometricBadge(
    tint: Color = MaterialTheme.colorScheme.primary,
    background: Color = MaterialTheme.colorScheme.surface,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .background(background, MaterialTheme.shapes.small),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_biometric),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7F4)
@Composable
private fun BiometricOfferScreenPreview() {
    SolventaTheme {
        BiometricOfferScreen(uiState = BiometricOfferUiState(biometricAvailable = true), onActivate = {}, onDecline = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7F4)
@Composable
private fun BiometricUnavailableScreenPreview() {
    SolventaTheme {
        BiometricOfferScreen(uiState = BiometricOfferUiState(biometricAvailable = false), onActivate = {}, onDecline = {})
    }
}
