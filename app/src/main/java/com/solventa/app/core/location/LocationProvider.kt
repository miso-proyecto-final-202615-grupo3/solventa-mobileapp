package com.solventa.app.core.location

import java.time.Instant

enum class LocationSource(val api: String) { GPS("GPS"), NETWORK("RED"), LAST_KNOWN("ULTIMA_CONOCIDA") }

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAt: Instant,
    val source: LocationSource,
)

sealed interface LocationResult {
    data class Found(val fix: LocationFix) : LocationResult
    data object PermissionDenied : LocationResult
    /** La ubicación del sistema está apagada. */
    data object Disabled : LocationResult
    /** Sin Google Play Services o sin señal. */
    data object Unavailable : LocationResult
    data object Timeout : LocationResult
}

/** Adapter: la lógica de la app no depende de Google Play Services (se prueba con fakes). */
fun interface LocationProvider {
    suspend fun current(timeoutMs: Long): LocationResult
}

/** Dirección legible solo para mostrar ("Bogotá, Colombia"); `null` si no se puede. Opcional. */
fun interface AddressResolver {
    suspend fun resolve(fix: LocationFix): String?
}
