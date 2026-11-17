package com.solventa.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

/** Pantalla provisional tras iniciar sesión, hasta construir la billetera de pólizas (HU3.2.x). */
@Composable
fun HomeScreen(
    clientName: String,
    deviceLinked: Boolean,
    showBiometricHint: Boolean,
    onActivateDevice: () -> Unit,
    onReportClaim: () -> Unit,
    onOpenSecurity: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    /** HU4.1.2: folio de un siniestro con evidencias sin enviar, si hay. */
    pendingEvidenceClaimNumber: String? = null,
    onOpenPendingEvidence: () -> Unit = {},
    /** HU4.2.1: "Mis siniestros". */
    onOpenMyClaims: () -> Unit = {},
) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = if (clientName.isBlank()) {
                stringResource(R.string.home_greeting_generic)
            } else {
                stringResource(R.string.home_greeting, clientName)
            },
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(if (deviceLinked) R.string.home_device_linked else R.string.home_pending_activation),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showBiometricHint) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.home_biometric_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                    .padding(16.dp),
            )
        }
        if (pendingEvidenceClaimNumber != null) {
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.home_pending_evidence, pendingEvidenceClaimNumber), style = MaterialTheme.typography.bodyMedium)
                SolventaSecondaryButton(text = stringResource(R.string.home_pending_evidence_action), onClick = onOpenPendingEvidence)
            }
        }
        Spacer(Modifier.height(32.dp))
        if (!deviceLinked) {
            SolventaPrimaryButton(text = stringResource(R.string.home_activate_device), onClick = onActivateDevice)
            Spacer(Modifier.height(12.dp))
        } else {
            // Entrada provisional hasta la barra "Pólizas · Siniestros · Perfil" de la billetera (HU3.2.x).
            SolventaPrimaryButton(text = stringResource(R.string.home_report_claim), onClick = onReportClaim)
            Spacer(Modifier.height(12.dp))
            SolventaPrimaryButton(text = stringResource(R.string.home_my_claims), onClick = onOpenMyClaims)
            Spacer(Modifier.height(12.dp))
        }
        SolventaPrimaryButton(
            text = stringResource(R.string.home_open_security),
            onClick = onOpenSecurity,
        )
        Spacer(Modifier.height(12.dp))
        SolventaSecondaryButton(
            text = stringResource(R.string.home_logout),
            onClick = onLogout,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7F4)
@Composable
private fun HomeScreenPreview() {
    SolventaTheme {
        HomeScreen(clientName = "Camila", deviceLinked = true, showBiometricHint = true, onActivateDevice = {}, onReportClaim = {}, onOpenSecurity = {}, onLogout = {})
    }
}
