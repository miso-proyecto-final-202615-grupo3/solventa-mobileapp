package com.solventa.app.data.push

import com.solventa.app.core.device.InstallationIdProvider
import com.solventa.app.core.push.PushTokenSource
import com.solventa.app.data.auth.Session
import com.solventa.app.data.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Registra el token push de **esta** instalación para la sesión actual (HU4.2.2). Solo si el teléfono está
 * vinculado; el backend decide los destinos (CA3). Reintenta por red con 2, 4 y 8 s y no repite el mismo
 * token en la misma sesión. Al cerrar sesión lo borra.
 */
class PushRegistrar(
    private val repository: PushRepository,
    private val sessions: SessionStore,
    private val installationIdProvider: InstallationIdProvider,
    private val scope: CoroutineScope,
    private val tokenSource: PushTokenSource,
    private val retryDelaysMs: List<Long> = listOf(2_000, 4_000, 8_000),
) {
    private val mutex = Mutex()
    private var registered: Pair<String, String>? = null // (accessToken, token)

    fun register(token: String? = null): Job = scope.launch {
        mutex.withLock {
            val session = sessions.session.value ?: return@withLock
            if (!session.cliente.dispositivoVinculado) return@withLock
            val resolved = token ?: tokenSource.currentToken() ?: return@withLock
            if (registered == session.accessToken to resolved) return@withLock
            for (attempt in 0..retryDelaysMs.size) {
                when (repository.register(session.accessToken, installationIdProvider.get(), resolved)) {
                    PushResult.Registered -> {
                        registered = session.accessToken to resolved
                        return@withLock
                    }
                    PushResult.NetworkError, PushResult.ServiceUnavailable ->
                        retryDelaysMs.getOrNull(attempt)?.let { delay(it) } ?: return@withLock
                    // Sin vincular o sesión vencida: no tiene sentido reintentar ahora.
                    PushResult.DeviceNotLinked, PushResult.SessionExpired -> return@withLock
                }
            }
        }
    }

    fun unregister(sessionAtLogout: Session? = sessions.session.value): Job = scope.launch {
        mutex.withLock {
            registered = null
            val session = sessionAtLogout ?: return@withLock
            repository.unregister(session.accessToken, installationIdProvider.get())
        }
    }
}
