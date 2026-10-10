package com.solventa.app.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.solventa.app.R
import com.solventa.app.core.designsystem.component.SolventaPrimaryButton
import com.solventa.app.core.designsystem.component.SolventaSecondaryButton
import com.solventa.app.core.designsystem.theme.SolventaTheme

/** La app móvil no crea cuentas: el registro se hace en el canal web (precondición de HU3.1.1). */
@Composable
fun AccountNotFoundScreen(
    onGoToWebSignup: () -> Unit,
    onTryAnotherEmail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.account_not_found_title),
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.account_not_found_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        SolventaPrimaryButton(
            text = stringResource(R.string.account_not_found_go_web),
            onClick = onGoToWebSignup,
        )
        Spacer(Modifier.height(12.dp))
        SolventaSecondaryButton(
            text = stringResource(R.string.account_not_found_retry),
            onClick = onTryAnotherEmail,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7F4)
@Composable
private fun AccountNotFoundScreenPreview() {
    SolventaTheme {
        AccountNotFoundScreen(onGoToWebSignup = {}, onTryAnotherEmail = {})
    }
}
