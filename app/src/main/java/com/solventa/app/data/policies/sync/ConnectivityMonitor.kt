package com.solventa.app.data.policies.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado de la conexión a internet del teléfono (HU3.2.2). */
interface ConnectivityMonitor {
    val online: StateFlow<Boolean>
}

/**
 * Escucha la red predeterminada del sistema. "En línea" significa que Android la validó con acceso real a
 * internet (no basta con estar conectado a una red Wi-Fi sin salida).
 */
class AndroidConnectivityMonitor(context: Context) : ConnectivityMonitor {
    private val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _online = MutableStateFlow(currentlyOnline())
    override val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        try {
            manager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _online.value = isValidated(manager.getNetworkCapabilities(network))
                    }

                    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                        _online.value = isValidated(capabilities)
                    }

                    override fun onLost(network: Network) {
                        _online.value = currentlyOnline()
                    }
                },
            )
        } catch (_: SecurityException) {
            // Sin permiso de estado de red se asume en línea: cada sincronización fallida ya se reintenta.
            _online.value = true
        }
    }

    private fun currentlyOnline(): Boolean = try {
        isValidated(manager.getNetworkCapabilities(manager.activeNetwork))
    } catch (_: SecurityException) {
        true
    }

    private fun isValidated(capabilities: NetworkCapabilities?): Boolean =
        capabilities != null &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
