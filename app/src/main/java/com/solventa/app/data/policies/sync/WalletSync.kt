package com.solventa.app.data.policies.sync

import com.solventa.app.data.auth.AuthRepository
import com.solventa.app.data.auth.Session
import com.solventa.app.data.auth.SessionRefreshResult
import com.solventa.app.data.policies.CertificateResult
import com.solventa.app.data.policies.PolicyRepository
import com.solventa.app.data.policies.WalletFetchResult
import com.solventa.app.data.policies.WalletSnapshot
import com.solventa.app.data.policies.local.CertificateStore
import com.solventa.app.data.policies.local.WalletStore
import com.solventa.app.data.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Clock
import java.util.UUID

enum class SyncState {
    IDLE,

    /** Sincronizando: la lista local sigue consultable (HU3.2.2). */
    SYNCING,

    /** La última sincronización falló; se reintenta mientras haya conexión. La copia local no se toca. */
    FAILED,
}

enum class SyncOutcome { SYNCED, NO_SESSION, SESSION_EXPIRED, CLIENT_NOT_REGISTERED, FAILED, ALREADY_RUNNING }

/**
 * Mantiene al día la copia local de la billetera (HU3.2.1 y HU3.2.2).
 *
 * - Al haber sesión carga la copia guardada y, con conexión, sincroniza.
 * - Al recuperar la conexión sincroniza otra vez.
 * - La versión del servidor **reemplaza** la copia completa: una póliza que ya no viene desaparece del
 *   teléfono; una cancelada o renovada se actualiza.
 * - Si algo falla (red, servicio, disco) no se modifica ni la copia ni los certificados ya guardados, y se
 *   reintenta con retroceso mientras siga la conexión.
 */
class WalletSync(
    private val repository: PolicyRepository,
    private val store: WalletStore,
    private val certificates: CertificateStore,
    private val sessionStore: SessionStore,
    private val authRepository: AuthRepository,
    private val connectivity: ConnectivityMonitor,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
    private val retryDelaysMillis: List<Long> = DEFAULT_RETRY_DELAYS_MILLIS,
    private val newCorrelationId: () -> String = { UUID.randomUUID().toString() },
) {
    private val _snapshot = MutableStateFlow<WalletSnapshot?>(null)

    /** La copia local del cliente con sesión, o `null` si todavía no hay. */
    val snapshot: StateFlow<WalletSnapshot?> = _snapshot.asStateFlow()

    private val _state = MutableStateFlow(SyncState.IDLE)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    val online: StateFlow<Boolean> get() = connectivity.online

    private val _certificatesVersion = MutableStateFlow(0)

    /** Cambia cada vez que termina de guardarse el lote de certificados, para que la UI relea cuáles hay. */
    val certificatesVersion: StateFlow<Int> = _certificatesVersion.asStateFlow()

    private val mutex = Mutex()
    private var retryJob: Job? = null

    /** Empieza a observar la sesión y la conexión. Se llama una vez al iniciar la app. */
    fun start() {
        scope.launch {
            combine(sessionStore.session, connectivity.online) { session, online -> session?.cliente?.id to online }
                .distinctUntilChanged()
                .collect { (clientId, online) ->
                    if (clientId == null) {
                        _snapshot.value = null
                        retryJob?.cancel()
                        _state.value = SyncState.IDLE
                    } else {
                        loadLocal(clientId)
                        if (online) sync()
                    }
                }
        }
    }

    private fun loadLocal(clientId: String) {
        if (_snapshot.value?.clientId == clientId) return
        _snapshot.value = store.load()?.takeIf { it.clientId == clientId }
    }

    /** Sincroniza ahora (la usa el botón "Actualizar" y la reconexión). Si falla, programa los reintentos. */
    suspend fun sync(): SyncOutcome {
        val outcome = attempt()
        when (outcome) {
            SyncOutcome.SYNCED -> retryJob?.cancel()
            SyncOutcome.FAILED -> if (retryJob?.isActive != true) scheduleRetry()
            else -> Unit
        }
        return outcome
    }

    private suspend fun attempt(): SyncOutcome {
        if (!mutex.tryLock()) return SyncOutcome.ALREADY_RUNNING
        try {
            val session = sessionStore.session.value ?: return SyncOutcome.NO_SESSION
            _state.value = SyncState.SYNCING
            val outcome = runSync(session)
            _state.value = if (outcome == SyncOutcome.SYNCED || outcome == SyncOutcome.CLIENT_NOT_REGISTERED) SyncState.IDLE else SyncState.FAILED
            return outcome
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun runSync(initial: Session): SyncOutcome {
        var session = initial
        var refreshed = false
        while (true) {
            when (val result = repository.fetchWallet(session.accessToken, newCorrelationId())) {
                is WalletFetchResult.Loaded -> return apply(session, result)
                WalletFetchResult.SessionExpired -> {
                    // Un solo intento de renovar la sesión: sin bucles si el refresh token también venció.
                    if (refreshed) return SyncOutcome.SESSION_EXPIRED
                    refreshed = true
                    session = when (val renewed = authRepository.refresh(session)) {
                        is SessionRefreshResult.Success -> renewed.session.also(sessionStore::save)
                        SessionRefreshResult.ReauthenticationRequired -> return SyncOutcome.SESSION_EXPIRED
                        SessionRefreshResult.NetworkError, SessionRefreshResult.ServiceUnavailable ->
                            return SyncOutcome.FAILED
                    }
                }
                // Sin pólizas que mostrar para quien aún no es cliente: no es un fallo reintentable.
                WalletFetchResult.ClientNotRegistered -> return SyncOutcome.CLIENT_NOT_REGISTERED
                WalletFetchResult.NetworkError, WalletFetchResult.ServiceUnavailable -> return SyncOutcome.FAILED
            }
        }
    }

    private suspend fun apply(session: Session, result: WalletFetchResult.Loaded): SyncOutcome {
        // Si la sesión cambió o se cerró mientras bajaban los datos, no se guarda nada de otro cliente.
        if (sessionStore.session.value?.cliente?.id != session.cliente.id) return SyncOutcome.NO_SESSION
        val snapshot = WalletSnapshot(session.cliente.id, result.policies, clock.instant())
        try {
            // Los certificados de otro cliente nunca se conservan.
            val onDisk = store.load()
            if (onDisk != null && onDisk.clientId != snapshot.clientId) certificates.clear()
            store.save(snapshot)
        } catch (_: IOException) {
            return SyncOutcome.FAILED
        }
        _snapshot.value = snapshot
        certificates.retainOnly(snapshot.policies.map { it.id }.toSet())
        downloadCertificates(session, snapshot)
        _certificatesVersion.value += 1
        return SyncOutcome.SYNCED
    }

    /** Mejor esfuerzo: un certificado que no baja no invalida la sincronización de las pólizas. */
    private suspend fun downloadCertificates(session: Session, snapshot: WalletSnapshot) {
        for (policy in snapshot.policies) {
            when (val result = repository.certificate(session.accessToken, policy.id, newCorrelationId())) {
                is CertificateResult.Loaded -> try {
                    certificates.save(policy.id, result.pdf)
                } catch (_: IOException) {
                    // Se vuelve a intentar en la próxima sincronización.
                }
                CertificateResult.NotAvailable -> Unit
                CertificateResult.SessionExpired, CertificateResult.NetworkError, CertificateResult.ServiceUnavailable -> return
            }
        }
    }

    private fun scheduleRetry() {
        retryJob = scope.launch {
            for (wait in retryDelaysMillis) {
                delay(wait)
                if (!connectivity.online.value) continue
                if (attempt() != SyncOutcome.FAILED) return@launch
            }
        }
    }

    companion object {
        val DEFAULT_RETRY_DELAYS_MILLIS = listOf(5_000L, 15_000L, 45_000L, 120_000L, 300_000L)
    }
}
