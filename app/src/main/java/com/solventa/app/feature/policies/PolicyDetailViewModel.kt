package com.solventa.app.feature.policies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.solventa.app.data.policies.Policy
import com.solventa.app.data.policies.local.CertificateStore
import com.solventa.app.data.policies.sync.WalletSync
import com.solventa.app.di.AppContainer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant

data class PolicyDetailUiState(
    /** `null` si la póliza ya no está en la copia local. */
    val policy: Policy? = null,
    val syncedAt: Instant? = null,
    val offline: Boolean = false,
    /** El PDF del certificado está guardado cifrado en el teléfono. */
    val certificateSaved: Boolean = false,
)

/** Coberturas y credencial digital de una póliza, sin necesitar conexión (HU3.2.1). */
class PolicyDetailViewModel(
    private val policyId: String,
    walletSync: WalletSync,
    private val certificates: CertificateStore,
    private val cacheDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    val uiState: StateFlow<PolicyDetailUiState> =
        combine(walletSync.snapshot, walletSync.online, walletSync.certificatesVersion) { snapshot, online, _ ->
            PolicyDetailUiState(
                policy = snapshot?.policies?.firstOrNull { it.id == policyId },
                syncedAt = snapshot?.syncedAt,
                offline = !online,
                certificateSaved = certificates.has(policyId),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PolicyDetailUiState())

    /**
     * Descifra el certificado a un archivo temporal de la caché para abrirlo con el visor del sistema.
     * `null` si no hay certificado guardado o está dañado. Los temporales anteriores se borran.
     */
    suspend fun prepareCertificate(): File? = withContext(ioDispatcher) {
        val pdf = certificates.load(policyId) ?: return@withContext null
        val directory = File(cacheDir, CERTIFICATES_DIR).apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        try {
            File(directory, "certificado-$policyId.pdf").also { it.writeBytes(pdf) }
        } catch (_: IOException) {
            null
        }
    }

    /** Borra el PDF descifrado temporal al salir de la pantalla. */
    override fun onCleared() {
        File(cacheDir, CERTIFICATES_DIR).listFiles()?.forEach { it.delete() }
    }

    companion object {
        const val CERTIFICATES_DIR = "certificados"
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer, policyId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PolicyDetailViewModel(
                    policyId = policyId,
                    walletSync = container.walletSync,
                    certificates = container.certificateStore,
                    cacheDir = container.cacheDir,
                )
            }
        }
    }
}
