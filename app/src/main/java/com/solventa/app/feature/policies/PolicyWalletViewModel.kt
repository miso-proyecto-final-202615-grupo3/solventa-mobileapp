package com.solventa.app.feature.policies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.solventa.app.data.policies.Policy
import com.solventa.app.data.policies.sync.SyncState
import com.solventa.app.data.policies.sync.WalletSync
import com.solventa.app.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

data class PolicyWalletUiState(
    val policies: List<Policy> = emptyList(),
    /** Hay una copia local (aunque no tenga pólizas). Sin copia y sin red se muestra el estado vacío explicativo. */
    val hasLocalCopy: Boolean = false,
    /** Momento de la última sincronización exitosa. */
    val syncedAt: Instant? = null,
    /** Sin conexión: lo que se ve viene del almacenamiento local (HU3.2.1). */
    val offline: Boolean = false,
    /** "Sincronizando…" (HU3.2.2); no impide consultar lo guardado. */
    val syncing: Boolean = false,
    val syncFailed: Boolean = false,
)

/** "Mis pólizas": siempre lee de la copia local; la sincronización corre aparte (HU3.2.1 y HU3.2.2). */
class PolicyWalletViewModel(private val walletSync: WalletSync) : ViewModel() {

    val uiState: StateFlow<PolicyWalletUiState> =
        combine(walletSync.snapshot, walletSync.state, walletSync.online) { snapshot, state, online ->
            PolicyWalletUiState(
                policies = snapshot?.policies.orEmpty(),
                hasLocalCopy = snapshot != null,
                syncedAt = snapshot?.syncedAt,
                offline = !online,
                syncing = state == SyncState.SYNCING,
                syncFailed = state == SyncState.FAILED && online,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PolicyWalletUiState())

    /** Acción manual "Actualizar". */
    fun refresh() {
        viewModelScope.launch { walletSync.sync() }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { PolicyWalletViewModel(container.walletSync) }
        }
    }
}
