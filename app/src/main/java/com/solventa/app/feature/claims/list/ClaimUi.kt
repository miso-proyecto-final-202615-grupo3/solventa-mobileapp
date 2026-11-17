package com.solventa.app.feature.claims.list

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.solventa.app.R
import com.solventa.app.data.claims.ClaimStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Etiquetas y formatos compartidos por "Mis siniestros" y el detalle (HU4.2.1). */
fun statusRes(status: ClaimStatus): Int = when (status) {
    ClaimStatus.REPORTED -> R.string.claim_status_reported
    ClaimStatus.IN_REVIEW -> R.string.claim_status_in_review
    ClaimStatus.APPROVED -> R.string.claim_status_approved
    ClaimStatus.REJECTED -> R.string.claim_status_rejected
    ClaimStatus.PAID -> R.string.claim_status_paid
}

@Composable
fun StatusBadge(status: ClaimStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (background, content) = when (status) {
        ClaimStatus.REPORTED, ClaimStatus.IN_REVIEW -> colors.secondaryContainer to colors.onSecondaryContainer
        ClaimStatus.APPROVED, ClaimStatus.PAID -> colors.tertiaryContainer to colors.onTertiaryContainer
        ClaimStatus.REJECTED -> colors.errorContainer to colors.onErrorContainer
    }
    Surface(color = background, contentColor = content, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Text(
            stringResource(statusRes(status)),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

fun formatDateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    instant.atZone(zone).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))

fun formatDate(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    instant.atZone(zone).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

/** Se muestra mientras MS Pólizas responde, en lugar de un nombre armado desde el código. */
const val PENDING_NAME = "…"

/** Código de tipo legible solo si MS Pólizas no trajo el nombre (p. ej. una póliza que ya no está vigente). */
fun humanize(code: String): String = code.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
