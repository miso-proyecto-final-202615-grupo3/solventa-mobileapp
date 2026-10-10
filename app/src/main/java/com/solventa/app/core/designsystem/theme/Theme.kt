package com.solventa.app.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val SolventaColorScheme = lightColorScheme(
    primary = Cobalto,
    onPrimary = Papel,
    secondary = VerdeCobertura,
    onSecondary = Papel,
    tertiary = AmbarInstantaneo,
    onTertiary = Tinta,
    error = CoralAlerta,
    onError = Superficie,
    background = Papel,
    onBackground = Tinta,
    surface = Superficie,
    onSurface = Tinta,
    onSurfaceVariant = TintaSecundaria,
    outline = Borde,
)

// "Nada de esquinas muy redondeadas: precisión antes que suavidad."
private val SolventaShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun SolventaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SolventaColorScheme,
        typography = SolventaTypography,
        shapes = SolventaShapes,
        content = content,
    )
}
