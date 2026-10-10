package com.solventa.app.feature.biometric

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solventa.app.R
import com.solventa.app.core.biometric.BiometricPromptTexts
import com.solventa.app.core.biometric.rememberBiometricPromptLauncher
import com.solventa.app.core.designsystem.component.SolventaPrimaryButton
import com.solventa.app.core.designsystem.component.SolventaSecondaryButton
import com.solventa.app.core.designsystem.theme.SolventaTheme

object UnlockTestTags {
    const val CONFIRM = "unlock_confirm"
    const val RETRY = "unlock_retry"
    const val USE_PASSWORD = "unlock_use_password"
    const val ATTEMPT_BADGE = "unlock_attempt_badge"
    const val COUNTDOWN = "unlock_countdown"
}

/** Se dibuja sobre la navegación mientras la app está bloqueada, para no perder el estado debajo. */
@Composable
fun UnlockRoute(
    viewModel: UnlockViewModel,
    onUnlocked: () -> Unit,
    onUsePassword: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val launcher = rememberBiometricPromptLauncher()
    val texts = BiometricPromptTexts(
        title = stringResource(R.string.biometric_prompt_unlock_title),
        subtitle = stringResource(R.string.biometric_prompt_unlock_subtitle),
        negativeButton = stringResource(R.string.biometric_use_password),
    )

    LaunchedEffect(Unit) { viewModel.onShown() }
    LaunchedEffect(uiState.promptCipher) {
        val cipher = uiState.promptCipher ?: return@LaunchedEffect
        viewModel.onPromptLaunched()
        launcher.launch(cipher, texts, closeOnFailedAttempt = true, onEvent = viewModel::onPromptEvent)
    }
    LaunchedEffect(uiState.exit) {
        when (uiState.exit) {
            UnlockExit.UNLOCKED -> onUnlocked()
            UnlockExit.USE_PASSWORD -> onUsePassword()
            null -> return@LaunchedEffect
        }
        viewModel.onExitHandled()
    }
    // Con la app bloqueada, "atrás" no debe revelar el contenido de abajo.
    BackHandler {}

    UnlockScreen(
        status = uiState.status,
        onRetry = viewModel::onRetry,
        onUsePassword = viewModel::onUsePassword,
    )
}

@Composable
fun UnlockScreen(
    status: UnlockStatus,
    onRetry: () -> Unit,
    onUsePassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        when (status) {
            UnlockStatus.Ready -> {
                BiometricBadge()
                Spacer(Modifier.height(16.dp))
                Title(R.string.unlock_title)
                Body(R.string.unlock_body)
                Spacer(Modifier.height(32.dp))
                SolventaPrimaryButton(
                    text = stringResource(R.string.unlock_confirm),
                    onClick = onRetry,
                    modifier = Modifier.testTag(UnlockTestTags.CONFIRM),
                )
                UsePasswordTextButton(onUsePassword)
            }

            is UnlockStatus.Failed -> {
                BiometricBadge(
                    tint = MaterialTheme.colorScheme.error,
                    background = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.unlock_failed_attempt, status.attempt, status.maxAttempts),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag(UnlockTestTags.ATTEMPT_BADGE),
                )
                Spacer(Modifier.height(8.dp))
                Title(R.string.unlock_failed_title)
                Body(R.string.unlock_failed_body)
                Spacer(Modifier.height(32.dp))
                SolventaPrimaryButton(
                    text = stringResource(R.string.unlock_retry),
                    onClick = onRetry,
                    modifier = Modifier.testTag(UnlockTestTags.RETRY),
                )
                Spacer(Modifier.height(12.dp))
                SolventaSecondaryButton(
                    text = stringResource(R.string.biometric_use_password),
                    onClick = onUsePassword,
                    modifier = Modifier.testTag(UnlockTestTags.USE_PASSWORD),
                )
            }

            is UnlockStatus.LockedOut -> {
                Title(R.string.unlock_locked_title)
                Body(R.string.unlock_locked_body)
                Spacer(Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                        .padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.unlock_locked_countdown_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatCountdown(status.remainingSeconds),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.testTag(UnlockTestTags.COUNTDOWN),
                    )
                }
                Spacer(Modifier.height(32.dp))
                SolventaPrimaryButton(
                    text = stringResource(R.string.unlock_enter_with_password),
                    onClick = onUsePassword,
                    modifier = Modifier.testTag(UnlockTestTags.USE_PASSWORD),
                )
            }

            UnlockStatus.BiometricChanged -> {
                BiometricBadge()
                Spacer(Modifier.height(16.dp))
                Title(R.string.unlock_changed_title)
                Body(R.string.unlock_changed_body)
                Spacer(Modifier.height(32.dp))
                SolventaPrimaryButton(
                    text = stringResource(R.string.unlock_enter_with_password),
                    onClick = onUsePassword,
                    modifier = Modifier.testTag(UnlockTestTags.USE_PASSWORD),
                )
            }
        }
    }
}

internal fun formatCountdown(totalSeconds: Long): String =
    "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)

@Composable
private fun Title(textRes: Int) {
    Text(stringResource(textRes), style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Body(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun UsePasswordTextButton(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag(UnlockTestTags.USE_PASSWORD),
    ) {
        Text(stringResource(R.string.biometric_use_password))
    }
}

@Preview(showBackground = true)
@Composable
private fun UnlockFailedPreview() {
    SolventaTheme { UnlockScreen(status = UnlockStatus.Failed(1, 3), onRetry = {}, onUsePassword = {}) }
}

@Preview(showBackground = true)
@Composable
private fun UnlockLockedOutPreview() {
    SolventaTheme { UnlockScreen(status = UnlockStatus.LockedOut(300), onRetry = {}, onUsePassword = {}) }
}
