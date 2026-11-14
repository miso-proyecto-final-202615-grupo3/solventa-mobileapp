package com.solventa.app.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.solventa.app.core.permissions.isAnyGranted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Ubicación actual con Fused Location Provider: alta precisión, timeout y, si no responde, la última
 * conocida con antigüedad ≤ 2 min. Acepta permiso aproximado (la precisión se envía tal cual).
 */
class FusedLocationProvider(private val context: Context, private val clock: Clock) : LocationProvider {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission") // se verifica con isAnyGranted
    override suspend fun current(timeoutMs: Long): LocationResult {
        if (!context.isAnyGranted(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            return LocationResult.PermissionDenied
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!LocationManagerCompat.isLocationEnabled(manager)) return LocationResult.Disabled
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            return LocationResult.Unavailable
        }
        val tokenSource = CancellationTokenSource()
        val fresh = withTimeoutOrNull(timeoutMs) {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token).await()
        }
        if (fresh == null) tokenSource.cancel()
        fresh?.let { return LocationResult.Found(it.toFix(sourceOf(it))) }

        val last = client.lastLocation.await()
        val age = last?.let { Duration.between(Instant.ofEpochMilli(it.time), Instant.now(clock)) }
        return if (last != null && age != null && age <= MAX_LAST_KNOWN_AGE) {
            LocationResult.Found(last.toFix(LocationSource.LAST_KNOWN))
        } else {
            LocationResult.Timeout
        }
    }

    private fun sourceOf(location: Location): LocationSource =
        if (location.provider == LocationManager.GPS_PROVIDER || location.accuracy <= GPS_ACCURACY_METERS) {
            LocationSource.GPS
        } else {
            LocationSource.NETWORK
        }

    private fun Location.toFix(source: LocationSource) = LocationFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy.coerceAtLeast(1f),
        capturedAt = Instant.ofEpochMilli(time),
        source = source,
    )

    private companion object {
        val MAX_LAST_KNOWN_AGE: Duration = Duration.ofMinutes(2)
        const val GPS_ACCURACY_METERS = 30f
    }
}

private suspend fun <T> Task<T>.await(): T? = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resume(null) }
    addOnCanceledListener { continuation.resume(null) }
}

/** "Bogotá, Colombia" con el Geocoder del sistema; `null` si no hay red o servicio. */
class GeocoderAddressResolver(private val context: Context) : AddressResolver {
    override suspend fun resolve(fix: LocationFix): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(fix.latitude, fix.longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<android.location.Address>) {
                        continuation.resume(addresses.firstOrNull()?.let(::format))
                    }

                    override fun onError(errorMessage: String?) {
                        continuation.resume(null)
                    }
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(fix.latitude, fix.longitude, 1)?.firstOrNull()?.let(::format)
                } catch (_: IOException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
        }
    }

    private fun format(address: android.location.Address): String? =
        listOfNotNull(address.locality ?: address.subAdminArea, address.countryName)
            .joinToString(", ")
            .ifBlank { null }
}
