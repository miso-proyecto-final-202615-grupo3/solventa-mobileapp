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
import com.solventa.app.data.claims.CompensationStatus
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
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

/** HU4.2.3: los tipos paramétricos no están en el catálogo de reporte manual; su nombre viene de la app. */
fun parametricTypeRes(code: String): Int? = when (code) {
    "LLUVIA_EXTREMA" -> R.string.claim_type_heavy_rain
    "RETRASO_VUELO" -> R.string.claim_type_flight_delay
    else -> null
}

fun compensationStatusRes(status: CompensationStatus?): Int = when (status) {
    CompensationStatus.PAYMENT_REQUESTED -> R.string.compensation_status_requested
    CompensationStatus.PAID -> R.string.compensation_status_paid
    null -> R.string.compensation_status_unknown
}

/**
 * Monto en la moneda del contrato; sin decimales si el valor es entero (p. ej. `$ 300.000`).
 * Los pesos se muestran con las convenciones de Colombia aunque el teléfono esté en otra región (p. ej. es-US).
 */
fun formatMoney(amount: BigDecimal, currencyCode: String, locale: Locale = moneyLocale(currencyCode)): String {
    val format = NumberFormat.getCurrencyInstance(locale)
    runCatching { Currency.getInstance(currencyCode) }.getOrNull()?.let { format.currency = it }
    val whole = amount.stripTrailingZeros().scale() <= 0
    format.minimumFractionDigits = if (whole) 0 else 2
    format.maximumFractionDigits = if (whole) 0 else 2
    return format.format(amount)
}

fun moneyLocale(currencyCode: String, base: Locale = Locale.getDefault()): Locale =
    if (currencyCode == "COP" && base.country != "CO") Locale(base.language, "CO") else base

/** Marca de los siniestros creados automáticamente por un evento cubierto (HU4.2.3). */
@Composable
fun AutomaticBadge(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.claim_automatic),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
