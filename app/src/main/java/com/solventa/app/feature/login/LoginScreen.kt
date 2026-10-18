package com.solventa.app.feature.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solventa.app.R
import com.solventa.app.core.designsystem.component.SolventaPrimaryButton
import com.solventa.app.core.designsystem.component.SolventaSecondaryButton
import com.solventa.app.core.designsystem.theme.SolventaTheme

object LoginTestTags {
    const val EMAIL = "login_email"
    const val PASSWORD = "login_password"
    const val TOGGLE_PASSWORD = "login_toggle_password"
    const val SUBMIT = "login_submit"
    const val FORM_ERROR = "login_form_error"
    const val BIOMETRIC_ENTRY = "login_biometric_entry"
}

@Composable
fun LoginRoute(
    viewModel: LoginViewModel,
    onLoggedIn: () -> Unit,
    onBiometricOffer: () -> Unit,
    onAccountNotFound: () -> Unit,
    biometricEntryAvailable: Boolean = false,
    onBiometricEntry: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.destination) {
        when (uiState.destination) {
            LoginDestination.HOME -> onLoggedIn()
            LoginDestination.BIOMETRIC_OFFER -> onBiometricOffer()
            LoginDestination.ACCOUNT_NOT_FOUND -> onAccountNotFound()
            null -> return@LaunchedEffect
        }
        viewModel.onDestinationHandled()
    }

    LoginScreen(
        uiState = uiState,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
        onSubmit = viewModel::onSubmit,
        showBiometricEntry = biometricEntryAvailable,
        onBiometricEntry = onBiometricEntry,
    )
}

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    showBiometricEntry: Boolean = false,
    onBiometricEntry: () -> Unit = {},
) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.brand_wordmark),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = uiState.email,
            onValueChange = onEmailChange,
            label = { Text(stringResource(R.string.login_email_label)) },
            singleLine = true,
            isError = uiState.emailError != null,
            supportingText = uiState.emailError?.let { { Text(stringResource(R.string.login_error_email_invalid)) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            enabled = !uiState.loading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LoginTestTags.EMAIL),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.login_password_label)) },
            singleLine = true,
            isError = uiState.passwordError != null,
            supportingText = uiState.passwordError?.let { { Text(stringResource(R.string.login_error_password_empty)) } },
            visualTransformation = if (uiState.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            enabled = !uiState.loading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LoginTestTags.PASSWORD),
        )
        TextButton(
            onClick = onTogglePasswordVisibility,
            modifier = Modifier.testTag(LoginTestTags.TOGGLE_PASSWORD),
        ) {
            Text(
                stringResource(
                    if (uiState.passwordVisible) R.string.login_hide_password else R.string.login_show_password,
                ),
            )
        }

        uiState.formError?.let { error ->
            Text(
                text = stringResource(
                    when (error) {
                        LoginFormError.INVALID_CREDENTIALS -> R.string.login_error_invalid_credentials
                        LoginFormError.NETWORK -> R.string.login_error_network
                        LoginFormError.SERVICE_UNAVAILABLE -> R.string.login_error_service_unavailable
                    },
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(LoginTestTags.FORM_ERROR),
            )
        }

        Spacer(Modifier.height(16.dp))
        SolventaPrimaryButton(
            text = stringResource(R.string.login_submit),
            onClick = onSubmit,
            loading = uiState.loading,
            modifier = Modifier.testTag(LoginTestTags.SUBMIT),
        )
        if (showBiometricEntry) {
            Spacer(Modifier.height(12.dp))
            SolventaSecondaryButton(
                text = stringResource(R.string.login_use_biometric),
                onClick = onBiometricEntry,
                modifier = Modifier.testTag(LoginTestTags.BIOMETRIC_ENTRY),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7F4)
@Composable
private fun LoginScreenPreview() {
    SolventaTheme {
        LoginScreen(
            uiState = LoginUiState(email = "camila.rojas@correo.com", formError = LoginFormError.INVALID_CREDENTIALS),
            onEmailChange = {},
            onPasswordChange = {},
            onTogglePasswordVisibility = {},
            onSubmit = {},
        )
    }
}
